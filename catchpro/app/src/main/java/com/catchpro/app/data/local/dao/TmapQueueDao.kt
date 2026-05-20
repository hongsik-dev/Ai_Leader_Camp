package com.catchpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.catchpro.app.data.local.entity.TmapQueueEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TmapQueueDao {
    @Query(
        """
        SELECT * FROM tmap_queue_entries
        WHERE queueStatus NOT IN ('completed', 'cancelled')
        AND sourceType IN ('primary', 'secondary')
        ORDER BY updatedAtMillis DESC, createdAtMillis DESC
        """,
    )
    fun observeAll(): Flow<List<TmapQueueEntryEntity>>

    @Query("SELECT * FROM tmap_queue_entries WHERE orderSignature = :signature LIMIT 1")
    suspend fun findBySignature(signature: String): TmapQueueEntryEntity?

    @Query(
        """
        SELECT * FROM tmap_queue_entries
        WHERE queueStatus NOT IN ('completed', 'cancelled')
        AND sourceType IN ('primary', 'secondary')
        """,
    )
    suspend fun findActiveEntries(): List<TmapQueueEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TmapQueueEntryEntity): Long

    @Query(
        """
        UPDATE tmap_queue_entries
        SET manualPickupAddress = :address,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    suspend fun updateManualPickupAddress(
        id: Long,
        address: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE tmap_queue_entries
        SET manualDropoffAddress = :address,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    suspend fun updateManualDropoffAddress(
        id: Long,
        address: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE tmap_queue_entries
        SET queueStatus = :status,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        updatedAtMillis: Long,
    )

    @Query(
        """
        UPDATE tmap_queue_entries
        SET queueStatus = :status,
            updatedAtMillis = :updatedAtMillis
        WHERE orderSignature = :signature
        """,
    )
    suspend fun updateStatusBySignature(
        signature: String,
        status: String,
        updatedAtMillis: Long,
    )

    @Query("DELETE FROM tmap_queue_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
