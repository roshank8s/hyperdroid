package com.hyperdroid.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hyperdroid.model.VMConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface VMDao {
    @Query("SELECT * FROM virtual_machines ORDER BY createdAt DESC")
    fun getAllVMs(): Flow<List<VMConfig>>

    @Query("SELECT * FROM virtual_machines ORDER BY createdAt DESC")
    suspend fun getAllVMsOnce(): List<VMConfig>

    @Query("SELECT * FROM virtual_machines WHERE id = :id")
    suspend fun getVMById(id: String): VMConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVM(vm: VMConfig)

    @Update
    suspend fun updateVM(vm: VMConfig)

    @Delete
    suspend fun deleteVM(vm: VMConfig)

    @Query("SELECT COUNT(*) FROM virtual_machines")
    fun getVMCount(): Flow<Int>
}
