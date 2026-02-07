package com.hyperdroid.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuHelper @Inject constructor(
    private val context: Context
) {
    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _isBinderAlive.value = true
        checkShizukuPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isBinderAlive.value = false
        _permissionGranted.value = false
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            _permissionGranted.value = grantResult == PackageManager.PERMISSION_GRANTED
        }

    fun initialize() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        if (Shizuku.pingBinder()) {
            _isBinderAlive.value = true
            checkShizukuPermission()
        }
    }

    fun cleanup() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun checkShizukuPermission(): Boolean {
        val granted = try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Exception) {
            false
        }
        _permissionGranted.value = granted
        return granted
    }

    fun requestShizukuPermission() {
        try {
            if (!Shizuku.isPreV11() && Shizuku.getVersion() >= 11) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (_: Exception) {
            // Shizuku not available
        }
    }

    fun grantVMPermission(): Result<Unit> {
        val permissions = listOf(
            "android.permission.MANAGE_VIRTUAL_MACHINE",
            "android.permission.USE_CUSTOM_VIRTUAL_MACHINE"
        )
        val errors = mutableListOf<String>()

        for (permission in permissions) {
            val result = grantSinglePermission(permission)
            if (result.isFailure) {
                errors.add("$permission: ${result.exceptionOrNull()?.message}")
            }
        }

        // At minimum, MANAGE_VIRTUAL_MACHINE must succeed
        return if (errors.size < permissions.size) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException("Grant failures:\n${errors.joinToString("\n")}"))
        }
    }

    private fun grantSinglePermission(permission: String): Result<Unit> {
        // Strategy 1: IPermissionManager (Android 12+, API 31+)
        try {
            return grantViaPermissionManager(permission)
        } catch (_: Exception) { }

        // Strategy 2: IPackageManager (older Android)
        try {
            return grantViaIPackageManager(permission)
        } catch (_: Exception) { }

        // Strategy 3: Shell command
        try {
            return grantViaShell(permission)
        } catch (_: Exception) { }

        return Result.failure(RuntimeException("All grant methods failed for $permission"))
    }

    private fun grantViaPermissionManager(permission: String): Result<Unit> {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
        val rawBinder = getServiceMethod.invoke(null, "permissionmgr") as IBinder

        val iPermStub = Class.forName("android.permission.IPermissionManager\$Stub")
        val asInterfaceMethod = iPermStub.getMethod("asInterface", IBinder::class.java)
        val iPerm = asInterfaceMethod.invoke(null, ShizukuBinderWrapper(rawBinder))

        val iPermClass = Class.forName("android.permission.IPermissionManager")
        val grantMethod = iPermClass.getMethod(
            "grantRuntimePermission",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        )

        grantMethod.invoke(iPerm, context.packageName, permission, 0)
        return Result.success(Unit)
    }

    private fun grantViaIPackageManager(permission: String): Result<Unit> {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
        val rawBinder = getServiceMethod.invoke(null, "package") as IBinder

        val iPmStub = Class.forName("android.content.pm.IPackageManager\$Stub")
        val asInterfaceMethod = iPmStub.getMethod("asInterface", IBinder::class.java)
        val iPm = asInterfaceMethod.invoke(null, ShizukuBinderWrapper(rawBinder))

        val iPmClass = Class.forName("android.content.pm.IPackageManager")
        val grantMethod = iPmClass.getMethod(
            "grantRuntimePermission",
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType
        )

        grantMethod.invoke(iPm, context.packageName, permission, 0)
        return Result.success(Unit)
    }

    private fun grantViaShell(permission: String): Result<Unit> {
        val cmd = arrayOf("pm", "grant", context.packageName, permission)

        for (methodName in listOf("newProcess", "newProcess2")) {
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    methodName,
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, cmd, null, null) as Process
                val exitCode = process.waitFor()
                if (exitCode == 0) return Result.success(Unit)
                val error = process.errorStream.bufferedReader().readText()
                throw RuntimeException("pm grant exit $exitCode: $error")
            } catch (_: NoSuchMethodException) {
                continue
            }
        }

        throw RuntimeException("No shell execution method available in this Shizuku version")
    }

    companion object {
        const val SHIZUKU_REQUEST_CODE = 1001
    }
}
