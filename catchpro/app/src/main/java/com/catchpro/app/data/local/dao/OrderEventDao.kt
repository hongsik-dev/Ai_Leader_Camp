package com.catchpro.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.catchpro.app.data.local.entity.OrderEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderEventDao {
    @Query("SELECT * FROM order_events ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OrderEventEntity>>

    @Query(
        """
        SELECT * FROM order_events
        WHERE status IN (:statuses)
        ORDER BY createdAtMillis DESC
        LIMIT :limit
        """,
    )
    fun observeRecentByStatuses(
        statuses: List<String>,
        limit: Int,
    ): Flow<List<OrderEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: OrderEventEntity): Long

    @Query("DELETE FROM order_events WHERE createdAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM order_events")
    suspend fun deleteAll()
}
