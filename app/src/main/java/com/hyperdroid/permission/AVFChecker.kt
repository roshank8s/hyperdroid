package com.hyperdroid.permission

import android.content.Context
import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AVFChecker @Inject constructor(
    private val context: Context
) {
    data class AVFStatus(
        val isSupported: Boolean,
        val hasKVM: Boolean,
        val hasSystemFeature: Boolean,
        val hasVirtualMachineManager: Boolean,
        val supportsProtectedVM: Boolean,
        val supportsNonProtectedVM: Boolean,
        val failureReason: String? = null
    )

    fun checkAVFSupport(): AVFStatus {
        val hasFeature = context.packageManager
            .hasSystemFeature("android.software.virtualization_framework")

        val hasKVM = try {
            File("/dev/kvm").exists()
        } catch (_: SecurityException) {
            false
        }

        var hasVMM = false
        var supportsProtected = false
        var supportsNonProtected = false
        try {
            val vmmClass = Class.forName("android.system.virtualmachine.VirtualMachineManager")
            val vmm = context.getSystemService(vmmClass)
            if (vmm != null) {
                hasVMM = true
                try {
                    val getCapabilities = vmmClass.getMethod("getCapabilities")
                    val capabilities = getCapabilities.invoke(vmm) as Int
                    supportsProtected = (capabilities and 1) != 0
                    supportsNonProtected = (capabilities and 2) != 0
                } catch (_: Exception) {
                    // getCapabilities not available on this API level
                }
            }
        } catch (_: ClassNotFoundException) {
            // VirtualMachineManager not available
        } catch (_: Exception) {
            // Other reflection errors
        }

        val isSupported = hasFeature || hasKVM || hasVMM
        val failureReason = when {
            Build.VERSION.SDK_INT < 33 ->
                "Android 13+ required (current API: ${Build.VERSION.SDK_INT})"
            !isSupported ->
                "This device does not support Android Virtualization Framework"
            else -> null
        }

        return AVFStatus(
            isSupported = isSupported,
            hasKVM = hasKVM,
            hasSystemFeature = hasFeature,
            hasVirtualMachineManager = hasVMM,
            supportsProtectedVM = supportsProtected,
            supportsNonProtectedVM = supportsNonProtected,
            failureReason = failureReason
        )
    }
}
