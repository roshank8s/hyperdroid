package com.hyperdroid.util

import android.os.Build

object DeviceUtils {
    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun getAndroidVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun getProcessorInfo(): String = Build.HARDWARE

    fun isARM64(): Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
}
