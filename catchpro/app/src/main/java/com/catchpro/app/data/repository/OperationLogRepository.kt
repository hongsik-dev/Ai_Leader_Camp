package com.catchpro.app.data.repository

import com.catchpro.app.data.local.dao.OperationLogDao
import com.catchpro.app.data.local.entity.OperationLogEntity
import com.catchpro.app.data.model.OperationLogDraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class OperationLogRepository @Inject constructor(
    private val operationLogDao: OperationLogDao,
) {
    fun recentLogs(limit: Int = 200): Flow<List<OperationLogEntity>> = operationLogDao.observeRecent(limit)

    suspend fun log(draft: OperationLogDraft) {
        if (draft.eventType !in HotPathEventTypes) return

        operationLogDao.insert(
            OperationLogEntity(
                eventType = draft.eventType,
                status = draft.status,
                orderSignature = draft.orderSignature,
                mode = draft.mode,
                source = draft.source,
                region = draft.region,
                clientText = draft.clientText,
                orderTitle = draft.orderTitle,
                originSummary = draft.originSummary,
                destinationSummary = draft.destinationSummary,
                requesterLocation = draft.requesterLocation,
                pickupAddress = draft.pickupAddress,
                dropoffAddress = draft.dropoffAddress,
                detailNote = draft.detailNote,
                price = draft.price,
                currentToPickupDistanceKm = draft.currentToPickupDistanceKm,
                pickupToDropoffStraightKm = draft.pickupToDropoffStraightKm,
                estimatedPickupToDropoffRoadKm = draft.estimatedPickupToDropoffRoadKm,
                pickupRoadDistanceKm = draft.pickupRoadDistanceKm,
                destinationMatchDistanceKm = draft.destinationMatchDistanceKm,
                farePerStraightKm = draft.farePerStraightKm,
                farePerEstimatedRoadKm = draft.farePerEstimatedRoadKm,
                shouldConfirm = draft.shouldConfirm,
                confirmed = draft.confirmed,
                manualInputRequired = draft.manualInputRequired,
                manualReviewRequired = draft.manualReviewRequired,
                reason = draft.reason,
                clickDiagnostic = draft.clickDiagnostic,
                screenSummary = draft.screenSummary,
                rawContext = draft.rawContext,
            ),
        )
        operationLogDao.trimToRecent(MaxRows)
    }

    suspend fun clearAll() {
        operationLogDao.deleteAll()
    }

    suspend fun deleteOlderThan(cutoffMillis: Long) {
        operationLogDao.deleteOlderThan(cutoffMillis)
    }

    private companion object {
        const val MaxRows = 50_000
        val HotPathEventTypes = setOf(
            "auto_entry_click",
            "auto_entry_click_failed",
            "auto_entry_click_retry",
            "auto_entry_detail_not_open",
            "auto_detail_decision",
            "auto_entry_detail_skipped",
            "manual_input_required",
            "order_confirm_duplicate_suppressed",
            "order_confirm_click_attempted",
            "order_confirm_click_failed",
            "order_confirm_rejected_by_insung",
            "order_confirm_unverified",
            "order_confirmed",
            "order_alert_delivery_failed",
            "ROUTE_ADDRESS_CLOUD_SYNC",
        )
    }
}
