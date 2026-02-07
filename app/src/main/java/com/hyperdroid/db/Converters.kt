package com.hyperdroid.db

import androidx.room.TypeConverter
import com.hyperdroid.model.OSType
import com.hyperdroid.model.VMStatus

class Converters {
    @TypeConverter
    fun fromOSType(value: OSType): String = value.name

    @TypeConverter
    fun toOSType(value: String): OSType = OSType.valueOf(value)

    @TypeConverter
    fun fromVMStatus(value: VMStatus): String = value.name

    @TypeConverter
    fun toVMStatus(value: String): VMStatus = VMStatus.valueOf(value)
}
