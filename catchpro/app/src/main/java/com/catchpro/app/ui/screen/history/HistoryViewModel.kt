package com.catchpro.app.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catchpro.app.data.local.entity.OrderEventEntity
import com.catchpro.app.data.model.TrackingFailureStatuses
import com.catchpro.app.data.repository.OrderEventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val events: List<OrderEventEntity> = emptyList(),
    val confirmedCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val hiddenInternalCount: Int = 0,
)

class HistoryViewModel(
    private val orderEventRepository: OrderEventRepository,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = orderEventRepository
        .recentEventsByStatuses(TrackingFailureStatuses.Visible)
        .map { events ->
            HistoryUiState(
                events = events,
                confirmedCount = 0,
                skippedCount = events.count { it.status in TrackingFailureStatuses.Skipped },
                failedCount = events.count { it.status in TrackingFailureStatuses.Failed },
                hiddenInternalCount = 0,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun clearHistory() {
        viewModelScope.launch {
            orderEventRepository.clearAll()
        }
    }

    companion object {
        const val StatusConfirmed = "confirmed"
        const val StatusSkipped = "skipped"
        const val StatusFailed = "failed"
        val ConfirmedStatuses = setOf(
            StatusConfirmed,
            "primary-auto-confirmed",
            "secondary-auto-confirmed",
            "tracked-additional-auto-confirmed",
            "primary-manual-confirmed",
            "secondary-manual-confirmed",
            "tracked-additional-manual-confirmed",
        )
        val SkippedStatuses = setOf(
            StatusSkipped,
            "manual-skipped",
            "auto-cancelled",
            "tracked-additional-cancelled",
            "tracked-additional-rejected",
            "order-tracking-auto-entry-blocked",
        )
        val FailedStatuses = TrackingFailureStatuses.Failed + setOf(
            StatusFailed,
            "manual-confirm-failed",
            "manual-input-required",
        )
        val InternalStatuses = setOf(
            "primary-list-destination-match",
            "primary-detail-mismatch",
            "order-list-auto-entry",
            "order-list-auto-entry-failed",
            "order-list-auto-entry-detail-not-open",
            "order-list-auto-entry-skipped",
            "order-list-auto-entry-list-excluded",
            "pickup-button-clicked",
            "pickup-complete-prompt-detected",
            "pickup-completed-confirmed",
            "dropoff-signature-button-clicked",
            "dropoff-send-action-clicked",
            "dropoff-complete-prompt-detected",
            "dropoff-completed-confirmed",
            "tmap-arrival-detected",
            "order-tracking-ended",
        )

        fun factory(orderEventRepository: OrderEventRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                        return HistoryViewModel(orderEventRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
