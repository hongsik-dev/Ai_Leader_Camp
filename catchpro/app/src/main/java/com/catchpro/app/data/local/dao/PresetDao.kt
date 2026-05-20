package com.catchpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.catchpro.app.data.local.entity.PresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY isEnabled DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Query("SELECT COUNT(*) FROM presets")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preset: PresetEntity): Long

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE presets SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: Long, enabled: Boolean)
}
