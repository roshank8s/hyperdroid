package com.hyperdroid.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VMServiceManager @Inject constructor(
    private val context: Context
) {
    fun notifyVMStarted(vmId: String, vmName: String) {
        val intent = Intent(context, VMService::class.java).apply {
            action = VMService.ACTION_START_VM
            putExtra(VMService.EXTRA_VM_ID, vmId)
            putExtra(VMService.EXTRA_VM_NAME, vmName)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun notifyVMStopped(vmName: String) {
        val intent = Intent(context, VMService::class.java).apply {
            action = VMService.ACTION_STOP_VM
            putExtra(VMService.EXTRA_VM_NAME, vmName)
        }
        context.startService(intent)
    }

    fun stopAll() {
        val intent = Intent(context, VMService::class.java).apply {
            action = VMService.ACTION_STOP_ALL
        }
        context.startService(intent)
    }
}
