package com.hyperdroid.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "virtual_machines")
data class VMConfig(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val osType: OSType = OSType.DEBIAN,
    val cpuCores: Int = 2,
    val memoryMB: Int = 2048,
    val diskSizeGB: Int = 16,
    val enableNetworking: Boolean = true,
    val status: VMStatus = VMStatus.STOPPED,
    val createdAt: Long = System.currentTimeMillis(),
    val lastStartedAt: Long? = null,
    val imagePath: String? = null,
    val kernelPath: String? = null
)
