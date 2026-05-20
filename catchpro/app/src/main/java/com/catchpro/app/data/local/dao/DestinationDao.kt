package com.catchpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.catchpro.app.data.local.entity.DestinationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Query("SELECT * FROM destinations ORDER BY isDefault DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<DestinationEntity>>

    @Query("SELECT * FROM destinations WHERE isDefault = 1 ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeDefaultDestination(): Flow<DestinationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(destination: DestinationEntity): Long

    @Query("DELETE FROM destinations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE destinations SET isDefault = 0")
    suspend fun clearDefaultSelection()

    @Query("UPDATE destinations SET isDefault = :isDefault WHERE id = :id")
    suspend fun updateDefaultFlag(id: Long, isDefault: Boolean)

    @Transaction
    suspend fun replaceDefault(id: Long) {
        clearDefaultSelection()
        updateDefaultFlag(id = id, isDefault = true)
    }
}
