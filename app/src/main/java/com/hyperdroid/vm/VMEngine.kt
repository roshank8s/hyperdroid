package com.hyperdroid.vm

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.hyperdroid.data.VMRepository
import com.hyperdroid.model.OSType
import com.hyperdroid.model.VMConfig
import com.hyperdroid.model.VMStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VMEngine @Inject constructor(
    private val context: Context,
    private val vmRepository: VMRepository
) {
    companion object {
        private const val TAG = "VMEngine"

        private const val VMM_CLASS = "android.system.virtualmachine.VirtualMachineManager"
        private const val VM_CONFIG_CLASS = "android.system.virtualmachine.VirtualMachineConfig"
        private const val VM_CONFIG_BUILDER_CLASS = "android.system.virtualmachine.VirtualMachineConfig\$Builder"
        private const val VM_CLASS = "android.system.virtualmachine.VirtualMachine"
        private const val VM_CALLBACK_CLASS = "android.system.virtualmachine.VirtualMachineCallback"
        private const val CUSTOM_IMAGE_CONFIG_CLASS = "android.system.virtualmachine.VirtualMachineCustomImageConfig"
        private const val CUSTOM_IMAGE_CONFIG_BUILDER_CLASS = "android.system.virtualmachine.VirtualMachineCustomImageConfig\$Builder"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    // Running VM instances (id -> reflected VirtualMachine object)
    private val runningVMs = ConcurrentHashMap<String, Any>()

    // VM name-to-id mapping (AVF uses string names, we use UUIDs)
    private val vmNameMap = ConcurrentHashMap<String, String>()

    private val _vmStatuses = MutableStateFlow<Map<String, VMStatus>>(emptyMap())
    val vmStatuses: StateFlow<Map<String, VMStatus>> = _vmStatuses.asStateFlow()

    // Last error per VM (shown in UI)
    private val _lastErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastErrors: StateFlow<Map<String, String>> = _lastErrors.asStateFlow()

    // Cached reflection references
    private val vmmClass: Class<*>? = tryLoadClass(VMM_CLASS)
    private val vmConfigClass: Class<*>? = tryLoadClass(VM_CONFIG_CLASS)
    private val vmConfigBuilderClass: Class<*>? = tryLoadClass(VM_CONFIG_BUILDER_CLASS)
    private val vmClass: Class<*>? = tryLoadClass(VM_CLASS)
    private val vmCallbackClass: Class<*>? = tryLoadClass(VM_CALLBACK_CLASS)
    private val customImageConfigClass: Class<*>? = tryLoadClass(CUSTOM_IMAGE_CONFIG_CLASS)
    private val customImageConfigBuilderClass: Class<*>? = tryLoadClass(CUSTOM_IMAGE_CONFIG_BUILDER_CLASS)

    fun isAvailable(): Boolean {
        if (vmmClass == null || vmClass == null || vmConfigBuilderClass == null) {
            Log.w(TAG, "AVF classes not available on this device")
            return false
        }
        return try {
            val vmm = getVMM()
            if (vmm != null) {
                logSupportedOSList(vmm)
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "VirtualMachineManager not available", e)
            false
        }
    }

    fun getSupportedOSList(): List<String> {
        val vmm = getVMM() ?: return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val result = reflectCall(vmm, "getSupportedOSList")
            when (result) {
                is List<*> -> result.filterIsInstance<String>()
                is Array<*> -> result.filterIsInstance<String>()
                else -> {
                    Log.w(TAG, "getSupportedOSList returned unexpected type: ${result?.javaClass}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSupportedOSList not available: ${e.message}")
            emptyList()
        }
    }

    private fun logSupportedOSList(vmm: Any) {
        try {
            val osList = getSupportedOSList()
            Log.i(TAG, "Supported OS list: $osList")
        } catch (e: Exception) {
            Log.d(TAG, "Could not query supported OS list: ${e.message}")
        }
    }

    fun createAndStartVM(config: VMConfig): Result<Unit> {
        _lastErrors.update { it - config.id }

        return try {
            val vmm = getVMM() ?: return failWithError(config.id, "VirtualMachineManager not available")

            val avfConfig = buildVMConfig(config)
                ?: return failWithError(config.id,
                    if (config.imagePath == null && !getSupportedOSList().contains("debian"))
                        "No bootable disk image selected. This device requires an EFI-bootable raw disk image (.img)"
                    else
                        "Failed to build VM config — check OS image and permissions"
                )

            val vmName = sanitizeVMName(config.name)

            // Delete any stale AVF VM with the same name to ensure fresh config
            try {
                reflectCall(vmm, "delete", arrayOf(String::class.java), vmName)
                Log.d(TAG, "Deleted stale AVF VM: $vmName")
            } catch (_: Exception) { }

            val vm = reflectCall(vmm, "create", arrayOf(String::class.java, vmConfigClass!!), vmName, avfConfig)
                ?: return failWithError(config.id, "Failed to create VM instance")

            vmNameMap[config.id] = vmName

            setupCallback(vm, config.id)

            reflectCall(vm, "run")

            runningVMs[config.id] = vm

            // For custom image VMs, onPayloadReady never fires (it's microdroid-specific).
            // Set RUNNING immediately after run() succeeds, then poll status as backup.
            updateStatus(config.id, VMStatus.RUNNING)
            startStatusPolling(config.id, vm)

            Log.i(TAG, "VM '${config.name}' running")
            Result.success(Unit)
        } catch (e: Exception) {
            val rootCause = extractRootCause(e)
            Log.e(TAG, "Failed to create/start VM '${config.name}'", e)
            failWithError(config.id, rootCause)
        }
    }

    fun stopVM(id: String): Result<Unit> {
        return try {
            val vm = runningVMs[id]
                ?: return Result.failure(IllegalStateException("VM not found in running VMs"))

            try {
                reflectCall(vm, "close")
            } catch (e: Exception) {
                try {
                    reflectCall(vm, "stop")
                } catch (e2: Exception) {
                    Log.w(TAG, "Neither close() nor stop() worked", e2)
                }
            }

            runningVMs.remove(id)
            updateStatus(id, VMStatus.STOPPED)

            Log.i(TAG, "VM stopped: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop VM: $id", e)
            Result.failure(e)
        }
    }

    fun deleteVM(id: String): Result<Unit> {
        return try {
            if (runningVMs.containsKey(id)) {
                stopVM(id)
            }

            val vmm = getVMM() ?: return Result.failure(
                IllegalStateException("VirtualMachineManager not available")
            )

            val vmName = vmNameMap[id] ?: return Result.success(Unit)

            try {
                reflectCall(vmm, "delete", arrayOf(String::class.java), vmName)
            } catch (e: Exception) {
                Log.w(TAG, "AVF delete failed (VM may not exist in AVF): ${e.message}")
            }

            vmNameMap.remove(id)
            runningVMs.remove(id)
            _vmStatuses.update { it - id }
            _lastErrors.update { it - id }

            Log.i(TAG, "VM deleted: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete VM: $id", e)
            Result.failure(e)
        }
    }

    fun getConsoleOutput(id: String): ParcelFileDescriptor? {
        val vm = runningVMs[id] ?: return null
        return try {
            reflectCall(vm, "getConsoleOutput") as? ParcelFileDescriptor
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get console output for VM: $id", e)
            null
        }
    }

    fun getConsoleInput(id: String): ParcelFileDescriptor? {
        val vm = runningVMs[id] ?: return null
        return try {
            reflectCall(vm, "getConsoleInput") as? ParcelFileDescriptor
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get console input for VM: $id", e)
            null
        }
    }

    /**
     * Discover the VM's IP address from the AVF tap interface ARP table.
     * Returns null if the VM hasn't been assigned an IP yet.
     */
    fun discoverVMIp(): String? {
        try {
            // Find the AVF tap interface and its host IP
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            var hostIp: String? = null

            for (iface in interfaces) {
                if (iface.name == "avf_tap_fixed") {
                    for (addr in iface.inetAddresses) {
                        if (addr is java.net.Inet4Address) {
                            hostIp = addr.hostAddress
                            break
                        }
                    }
                    break
                }
            }

            if (hostIp == null) {
                Log.d(TAG, "avf_tap_fixed interface not found")
                return null
            }

            val prefix = hostIp.substringBeforeLast(".")
            val hostOctet = hostIp.substringAfterLast(".").toIntOrNull() ?: return null
            Log.d(TAG, "AVF host IP: $hostIp, scanning $prefix.0/24 for SSH")

            // Parallel scan: try TCP connect to port 22 on all IPs in /24
            val result = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val latch = java.util.concurrent.CountDownLatch(253)
            val executor = java.util.concurrent.Executors.newFixedThreadPool(32)

            for (octet in 1..254) {
                if (octet == hostOctet) { latch.countDown(); continue }
                val candidateIp = "$prefix.$octet"
                executor.submit {
                    try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(candidateIp, 22), 150)
                        socket.close()
                        result.compareAndSet(null, candidateIp)
                        Log.i(TAG, "Discovered VM IP: $candidateIp (SSH open)")
                    } catch (_: Exception) { }
                    finally { latch.countDown() }
                }
            }

            // Wait max 3 seconds for scan to complete
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
            executor.shutdownNow()

            return result.get()
        } catch (e: Exception) {
            Log.d(TAG, "VM IP discovery failed: ${e.message}")
            return null
        }
    }

    fun getVMStatus(id: String): VMStatus {
        return _vmStatuses.value[id] ?: VMStatus.STOPPED
    }

    fun getLastError(id: String): String? {
        return _lastErrors.value[id]
    }

    fun clearError(id: String) {
        _lastErrors.update { it - id }
    }

    // --- Private helpers ---

    private fun getVMM(): Any? {
        val cls = vmmClass ?: return null
        return context.getSystemService(cls)
    }

    private fun buildVMConfig(config: VMConfig): Any? {
        val builderClass = vmConfigBuilderClass ?: return null

        return try {
            val builder = builderClass.getConstructor(Context::class.java).newInstance(context)

            // Set memory
            tryReflectCall(builder, "setMemoryBytes", arrayOf(Long::class.java),
                config.memoryMB.toLong() * 1024L * 1024L)

            // Enable debug for console access
            try {
                val debugLevelFull = vmConfigClass?.getField("DEBUG_LEVEL_FULL")?.getInt(null) ?: 1
                tryReflectCall(builder, "setDebugLevel", arrayOf(Int::class.java), debugLevelFull)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set debug level", e)
            }

            // Required: setProtectedVm must be called explicitly before build()
            tryReflectCall(builder, "setProtectedVm", arrayOf(Boolean::class.java), false)

            // Console access settings
            tryReflectCall(builder, "setConnectVmConsole", arrayOf(Boolean::class.java), true)
            tryReflectCall(builder, "setVmOutputCaptured", arrayOf(Boolean::class.java), true)
            tryReflectCall(builder, "setConsoleInputDevice", arrayOf(String::class.java), "ttyS0")

            // CPU topology: try MATCH_HOST first
            try {
                val matchHost = vmConfigClass?.getField("CPU_TOPOLOGY_MATCH_HOST")?.getInt(null)
                if (matchHost != null) {
                    tryReflectCall(builder, "setCpuTopology", arrayOf(Int::class.java), matchHost)
                }
            } catch (_: Exception) {
                tryReflectCall(builder, "setNumCpus", arrayOf(Int::class.java), config.cpuCores)
            }

            // Strategy 1: setOs("debian") — Android 16+ named OS
            if (config.osType.isDebianBased) {
                val supported = getSupportedOSList()
                Log.i(TAG, "Supported OS list: $supported")

                if (supported.contains("debian")) {
                    Log.i(TAG, "Trying setOs('debian')...")
                    val setOsResult = tryReflectCall(builder, "setOs", arrayOf(String::class.java), "debian")
                    if (setOsResult != null) {
                        return try {
                            val built = reflectCall(builder, "build")
                            Log.i(TAG, "Using setOs('debian')")
                            built
                        } catch (e: Exception) {
                            Log.w(TAG, "setOs('debian') build failed: ${extractRootCause(e)}")
                            null
                        }
                    }
                }
                Log.w(TAG, "setOs('debian') not available on this device")
            }

            // Strategy 2: Custom image config (user-provided disk image)
            // On this device, U-Boot is auto-provided if no bootloader/kernel is set
            if (config.imagePath != null) {
                val customConfig = buildCustomImageConfig(config)
                if (customConfig != null && customImageConfigClass != null) {
                    val setResult = tryReflectCall(
                        builder, "setCustomImageConfig",
                        arrayOf(customImageConfigClass), customConfig
                    )
                    if (setResult != null) {
                        return try {
                            val built = reflectCall(builder, "build")
                            Log.i(TAG, "Using custom image config with disk: ${config.imagePath}")
                            built
                        } catch (e: Exception) {
                            Log.w(TAG, "Custom image config build failed: ${extractRootCause(e)}")
                            null
                        }
                    }
                }
            }

            Log.e(TAG, "No VM config strategy succeeded. imagePath=${config.imagePath}, osType=${config.osType}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build VirtualMachineConfig: ${extractRootCause(e)}", e)
            null
        }
    }

    private fun buildCustomImageConfig(config: VMConfig): Any? {
        val builderClass = customImageConfigBuilderClass ?: return null

        return try {
            val builder = builderClass.getConstructor().newInstance()

            // Set VM name
            tryReflectCall(builder, "setName", arrayOf(String::class.java), sanitizeVMName(config.name))

            // DON'T set bootloader or kernel — let the system auto-provide u-boot.bin
            // from /apex/com.android.virt/etc/u-boot.bin
            Log.i(TAG, "Using system-provided U-Boot bootloader (no explicit bootloader/kernel)")

            // Set kernel if explicitly provided by user
            config.kernelPath?.let { path ->
                Log.i(TAG, "Setting explicit kernel: $path")
                tryReflectCall(builder, "setKernelPath", arrayOf(String::class.java), path)
            }

            // Add user-provided image as a disk via Disk.RWDisk(path)
            config.imagePath?.let { path ->
                Log.i(TAG, "Adding disk image: $path")
                val added = tryAddDisk(builder, path, writable = true)
                if (!added) {
                    Log.w(TAG, "Failed to add disk via any method")
                }
            }

            // Add cloud-init cidata ISO as read-only second disk (enables SSH + root password)
            val cidataPath = java.io.File(context.filesDir, "vm_images/cidata.iso")
            if (cidataPath.exists()) {
                Log.i(TAG, "Adding cidata disk: ${cidataPath.absolutePath}")
                tryAddDisk(builder, cidataPath.absolutePath, writable = false)
            }

            // Enable networking
            tryReflectCall(builder, "useNetwork", arrayOf(Boolean::class.java), config.enableNetworking)

            val result = reflectCall(builder, "build")
            Log.i(TAG, "CustomImageConfig built successfully")
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to build custom image config: ${extractRootCause(e)}", e)
            null
        }
    }

    private fun tryAddDisk(builder: Any, path: String, writable: Boolean = true): Boolean {
        try {
            val diskClass = Class.forName("$CUSTOM_IMAGE_CONFIG_CLASS\$Disk")
            val methodName = if (writable) "RWDisk" else "RODisk"
            val diskMethod = diskClass.getMethod(methodName, String::class.java)
            val disk = diskMethod.invoke(null, path)
            val addDiskMethod = builder.javaClass.getMethod("addDisk", diskClass)
            addDiskMethod.invoke(builder, disk)
            Log.i(TAG, "Added disk via Disk.$methodName('$path')")
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Disk factory not available: ${e.message}")
        }

        // Fallback: setBootloaderPath (only for writable/main disk)
        if (writable) {
            try {
                reflectCall(builder, "setBootloaderPath", arrayOf(String::class.java), path)
                Log.i(TAG, "Set image as bootloader: $path")
                return true
            } catch (e: Exception) {
                Log.d(TAG, "setBootloaderPath not available: ${e.message}")
            }
        }

        return false
    }

    private fun startStatusPolling(vmId: String, vm: Any) {
        scope.launch {
            while (runningVMs.containsKey(vmId)) {
                delay(3000)
                try {
                    val status = reflectCall(vm, "getStatus") as? Int
                    // STATUS_STOPPED = 0, STATUS_RUNNING = 1, STATUS_DELETED = 2
                    if (status != null && status != 1) {
                        Log.i(TAG, "VM $vmId polled status=$status (not running), updating to STOPPED")
                        updateStatus(vmId, VMStatus.STOPPED)
                        runningVMs.remove(vmId)
                        break
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Status poll failed for VM $vmId: ${e.message}")
                }
            }
        }
    }

    private fun setupCallback(vm: Any, vmId: String) {
        val callbackCls = vmCallbackClass ?: return

        val proxy = Proxy.newProxyInstance(
            callbackCls.classLoader,
            arrayOf(callbackCls)
        ) { _, method, args ->
            when (method.name) {
                "onPayloadReady" -> {
                    Log.i(TAG, "VM $vmId: payload ready")
                    updateStatus(vmId, VMStatus.RUNNING)
                }
                "onPayloadStarted" -> {
                    Log.i(TAG, "VM $vmId: payload started")
                    updateStatus(vmId, VMStatus.STARTING)
                }
                "onPayloadFinished" -> {
                    val exitCode = args?.getOrNull(0) as? Int ?: -1
                    Log.i(TAG, "VM $vmId: payload finished (exit code: $exitCode)")
                    updateStatus(vmId, VMStatus.STOPPED)
                    runningVMs.remove(vmId)
                }
                "onError" -> {
                    val errorCode = args?.getOrNull(0) as? Int ?: -1
                    val message = args?.getOrNull(1) as? String ?: "Unknown error"
                    Log.e(TAG, "VM $vmId: error $errorCode - $message")
                    _lastErrors.update { it + (vmId to "Error $errorCode: $message") }
                    updateStatus(vmId, VMStatus.ERROR)
                    runningVMs.remove(vmId)
                }
                "onStopped" -> {
                    Log.i(TAG, "VM $vmId: stopped")
                    updateStatus(vmId, VMStatus.STOPPED)
                    runningVMs.remove(vmId)
                }
                "hashCode" -> vmId.hashCode()
                "equals" -> args?.getOrNull(0) === args?.getOrNull(0)
                "toString" -> "VMCallback[$vmId]"
            }
            null
        }

        try {
            val setCallbackMethod = vmClass?.getMethod(
                "setCallback",
                java.util.concurrent.Executor::class.java,
                callbackCls
            )
            setCallbackMethod?.invoke(vm, callbackExecutor, proxy)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set callback for VM $vmId", e)
        }
    }

    private fun updateStatus(vmId: String, status: VMStatus) {
        _vmStatuses.update { it + (vmId to status) }
        scope.launch {
            try {
                vmRepository.getVMById(vmId)?.let { vm ->
                    vmRepository.updateVM(vm.copy(status = status))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist VM status", e)
            }
        }
    }

    private fun failWithError(vmId: String, message: String): Result<Unit> {
        _lastErrors.update { it + (vmId to message) }
        updateStatus(vmId, VMStatus.ERROR)
        return Result.failure(RuntimeException(message))
    }

    private fun extractRootCause(e: Exception): String {
        var cause: Throwable? = e
        while (cause?.cause != null && cause.cause !== cause) {
            cause = cause.cause
        }
        return cause?.message ?: e.message ?: "Unknown error"
    }

    private fun sanitizeVMName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
    }

    // --- Reflection utilities ---

    private fun tryLoadClass(className: String): Class<*>? {
        return try {
            Class.forName(className)
        } catch (_: ClassNotFoundException) {
            Log.d(TAG, "Class not found: $className")
            null
        }
    }

    private fun reflectCall(obj: Any, methodName: String, paramTypes: Array<Class<*>>? = null, vararg args: Any?): Any? {
        val method: Method = if (paramTypes != null) {
            obj.javaClass.getMethod(methodName, *paramTypes)
        } else {
            obj.javaClass.getMethod(methodName)
        }
        return method.invoke(obj, *args)
    }

    private fun tryReflectCall(obj: Any, methodName: String, paramTypes: Array<Class<*>>? = null, vararg args: Any?): Any? {
        return try {
            reflectCall(obj, methodName, paramTypes, *args)
        } catch (e: Exception) {
            Log.d(TAG, "Optional method $methodName not available: ${e.message}")
            null
        }
    }
}
