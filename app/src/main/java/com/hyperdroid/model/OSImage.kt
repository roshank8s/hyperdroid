package com.hyperdroid.model

data class OSImage(
    val id: String,
    val name: String,
    val osType: OSType,
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val kernelFileName: String,
    val initrdFileName: String,
    val rootfsFileName: String
)
