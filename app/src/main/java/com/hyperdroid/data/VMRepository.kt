package com.hyperdroid.data

import com.hyperdroid.db.VMDao
import com.hyperdroid.model.VMConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VMRepository @Inject constructor(
    private val vmDao: VMDao
) {
    fun getAllVMs(): Flow<List<VMConfig>> = vmDao.getAllVMs()

    suspend fun getAllVMsOnce(): List<VMConfig> = vmDao.getAllVMsOnce()

    fun getVMCount(): Flow<Int> = vmDao.getVMCount()

    suspend fun getVMById(id: String): VMConfig? = vmDao.getVMById(id)

    suspend fun insertVM(vm: VMConfig) = vmDao.insertVM(vm)

    suspend fun updateVM(vm: VMConfig) = vmDao.updateVM(vm)

    suspend fun deleteVM(vm: VMConfig) = vmDao.deleteVM(vm)
}
