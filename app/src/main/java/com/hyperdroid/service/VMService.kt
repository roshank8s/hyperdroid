package com.hyperdroid.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hyperdroid.MainActivity
import com.hyperdroid.vm.VMEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VMService : Service() {

    companion object {
        const val ACTION_START_VM = "com.hyperdroid.action.START_VM"
        const val ACTION_STOP_VM = "com.hyperdroid.action.STOP_VM"
        const val ACTION_STOP_ALL = "com.hyperdroid.action.STOP_ALL"
        const val EXTRA_VM_ID = "extra_vm_id"
        const val EXTRA_VM_NAME = "extra_vm_name"

        private const val CHANNEL_ID = "vm_running"
        private const val NOTIFICATION_ID = 1001
    }

    @Inject lateinit var vmEngine: VMEngine

    private val runningVMNames = mutableSetOf<String>()
    private val binder = VMServiceBinder()

    inner class VMServiceBinder : Binder() {
        fun getService(): VMService = this@VMService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_VM -> {
                val vmName = intent.getStringExtra(EXTRA_VM_NAME) ?: "VM"
                runningVMNames.add(vmName)
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_STOP_VM -> {
                val vmName = intent.getStringExtra(EXTRA_VM_NAME)
                vmName?.let { runningVMNames.remove(it) }
                if (runningVMNames.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification()
                }
            }
            ACTION_STOP_ALL -> {
                runningVMNames.clear()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Running Virtual Machines",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when virtual machines are running"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopAllIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VMService::class.java).apply { action = ACTION_STOP_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val count = runningVMNames.size
        val title = if (count == 1) "1 VM running" else "$count VMs running"
        val text = runningVMNames.joinToString(", ")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop All", stopAllIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
