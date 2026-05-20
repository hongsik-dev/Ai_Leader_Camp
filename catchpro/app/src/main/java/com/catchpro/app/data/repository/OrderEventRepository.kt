package com.catchpro.app.data.repository

import com.catchpro.app.data.local.dao.OrderEventDao
import com.catchpro.app.data.local.entity.OrderEventEntity
import com.catchpro.app.data.model.OrderEventDraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class OrderEventRepository @Inject constructor(
    private val orderEventDao: OrderEventDao,
) {
    fun recentEvents(limit: Int = 30): Flow<List<OrderEventEntity>> = orderEventDao.observeRecent(limit)

    fun recentEventsByStatuses(
        statuses: Set<String>,
        limit: Int = 200,
    ): Flow<List<OrderEventEntity>> = orderEventDao.observeRecentByStatuses(
        statuses = statuses.toList(),
        limit = limit,
    )

    suspend fun logEvent(draft: OrderEventDraft) {
        orderEventDao.insert(
            OrderEventEntity(
                presetId = draft.presetId,
                orderTitle = draft.orderTitle,
                originSummary = draft.originSummary,
                destinationSummary = draft.destinationSummary,
                price = draft.price,
                status = draft.status,
                failureReason = draft.failureReason,
            ),
        )
    }

    suspend fun clearAll() {
        orderEventDao.deleteAll()
    }

    suspend fun deleteOlderThan(cutoffMillis: Long) {
        orderEventDao.deleteOlderThan(cutoffMillis)
    }
}
