package com.hyperdroid.permission

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.hyperdroid.model.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManager @Inject constructor(
    private val context: Context,
    private val avfChecker: AVFChecker,
    private val shizukuHelper: ShizukuHelper
) {
    private val _status = MutableStateFlow(PermissionStatus.AVF_NOT_SUPPORTED)
    val status: StateFlow<PermissionStatus> = _status

    private var cachedAvfStatus: AVFChecker.AVFStatus? = null

    fun getAVFStatus(): AVFChecker.AVFStatus {
        if (cachedAvfStatus == null) {
            cachedAvfStatus = avfChecker.checkAVFSupport()
        }
        return cachedAvfStatus!!
    }

    fun refreshStatus(): PermissionStatus {
        val avfStatus = avfChecker.checkAVFSupport()
        cachedAvfStatus = avfStatus

        val newStatus = when {
            !avfStatus.isSupported -> PermissionStatus.AVF_NOT_SUPPORTED
            !isDeveloperModeEnabled() -> PermissionStatus.NEEDS_DEVELOPER_MODE
            !isWirelessDebuggingEnabled() -> PermissionStatus.NEEDS_WIRELESS_DEBUG
            !shizukuHelper.isShizukuInstalled() -> PermissionStatus.NEEDS_SHIZUKU
            !shizukuHelper.isShizukuRunning() -> PermissionStatus.NEEDS_SHIZUKU_START
            !hasVMPermission() -> PermissionStatus.NEEDS_VM_PERMISSION
            else -> PermissionStatus.GRANTED
        }
        _status.value = newStatus
        return newStatus
    }

    private fun isDeveloperModeEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (_: Exception) {
            false
        }
    }

    private fun isWirelessDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                "adb_wifi_enabled",
                0
            ) == 1
        } catch (_: Exception) {
            false
        }
    }

    private fun hasVMPermission(): Boolean {
        return context.checkSelfPermission("android.permission.MANAGE_VIRTUAL_MACHINE") ==
            PackageManager.PERMISSION_GRANTED
    }
}
