package com.catchpro.app.ui.screen.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catchpro.app.data.local.entity.OrderEventEntity
import com.catchpro.app.data.model.TrackingFailureStatuses
import com.catchpro.app.data.repository.OrderEventRepository
import com.catchpro.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PresetsUiState(
    val trackingModeEnabled: Boolean = false,
    val activeDriveDestinationText: String = "",
    val maximumPickupDistanceKmText: String = "",
    val maximumRouteDistanceKmText: String = "",
    val maxConfirmCountText: String = "2",
    val trackingExcludedKeywordsText: String =
        "핸드폰, 휴대폰, 모바일, 폰, 사다주기, 물건사다, 물건사다주기, 사서전달, AS, AS센터, AS방문, 에이에스, 에이에스센터, 방문후, 방문하고, 대기, 대기시간, 대기비, 법원, 집행, 증인, 심부름, 시간예약, 시간정해진, 예약, 왕복, 복귀",
    val recentTrackingFailures: List<OrderEventEntity> = emptyList(),
)

class PresetsViewModel(
    private val settingsRepository: SettingsRepository,
    private val orderEventRepository: OrderEventRepository,
) : ViewModel() {
    val uiState: StateFlow<PresetsUiState> = combine(
        settingsRepository.settings,
        orderEventRepository.recentEventsByStatuses(
            statuses = TrackingFailureStatuses.Visible,
            limit = RecentTrackingFailureLimit,
        ),
    ) { settings, recentTrackingFailures ->
            PresetsUiState(
                trackingModeEnabled = settings.orderTrackingModeEnabled,
                activeDriveDestinationText = settings.activeDriveDestinationText,
                maximumPickupDistanceKmText = settings.secondaryMaximumPickupDistanceKmText,
                maximumRouteDistanceKmText = settings.orderTrackingMaximumRouteDistanceKmText,
                maxConfirmCountText = settings.orderTrackingMaxConfirmCountText,
                trackingExcludedKeywordsText = settings.trackingAutoConfirmExcludedKeywordsText,
                recentTrackingFailures = recentTrackingFailures,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PresetsUiState(),
        )

    fun setTrackingModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOrderTrackingModeEnabled(enabled)
        }
    }

    fun saveTrackingConditions(
        maximumPickupDistanceKmText: String,
        maximumRouteDistanceKmText: String,
        maxConfirmCountText: String,
    ) {
        viewModelScope.launch {
            settingsRepository.setSecondaryMaximumPickupDistanceKmText(maximumPickupDistanceKmText)
            settingsRepository.setOrderTrackingMaximumRouteDistanceKmText(maximumRouteDistanceKmText)
            settingsRepository.setOrderTrackingMaxConfirmCountText(maxConfirmCountText)
        }
    }

    fun saveTrackingExcludedKeywords(excludedKeywordsText: String) {
        viewModelScope.launch {
            settingsRepository.setTrackingAutoConfirmExcludedKeywordsText(excludedKeywordsText)
        }
    }

    fun clearActiveDriveDestination() {
        viewModelScope.launch {
            settingsRepository.setActiveDriveDestinationText("")
        }
    }

    fun saveActiveDriveDestination(value: String) {
        viewModelScope.launch {
            settingsRepository.setActiveDriveDestinationText(value)
        }
    }

    companion object {
        private const val RecentTrackingFailureLimit = 3

        fun factory(
            settingsRepository: SettingsRepository,
            orderEventRepository: OrderEventRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(PresetsViewModel::class.java)) {
                        return PresetsViewModel(
                            settingsRepository = settingsRepository,
                            orderEventRepository = orderEventRepository,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
