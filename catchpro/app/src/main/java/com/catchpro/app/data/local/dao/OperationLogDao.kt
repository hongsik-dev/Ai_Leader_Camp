package com.catchpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.catchpro.app.data.local.entity.OperationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationLogDao {
    @Query(
        """
        SELECT * FROM operation_logs
        ORDER BY createdAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<OperationLogEntity>>

    @Insert
    suspend fun insert(log: OperationLogEntity): Long

    @Query(
        """
        DELETE FROM operation_logs
        WHERE id NOT IN (
            SELECT id FROM operation_logs
            ORDER BY createdAtMillis DESC
            LIMIT :maxRows
        )
        """,
    )
    suspend fun trimToRecent(maxRows: Int)

    @Query("DELETE FROM operation_logs WHERE createdAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM operation_logs")
    suspend fun deleteAll()
}
