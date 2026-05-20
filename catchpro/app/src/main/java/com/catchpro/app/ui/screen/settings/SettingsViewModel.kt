package com.catchpro.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catchpro.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val clientBlacklistText: String = "",
    val alertsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val voiceAlertsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val primaryOrderListAutoEntryEnabled: Boolean = false,
    val secondaryOrderListAutoEntryEnabled: Boolean = false,
    val orderListAutoEntryMaxChecksText: String = "30",
    val orderTrackingModeEnabled: Boolean = false,
    val kakaoRestApiKey: String = "",
    val historyRetentionDays: Int = 14,
    val observationPackageFilters: String = "",
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { settings ->
            SettingsUiState(
                clientBlacklistText = settings.clientBlacklistText,
                alertsEnabled = settings.alertsEnabled,
                vibrationEnabled = settings.vibrationEnabled,
                voiceAlertsEnabled = settings.voiceAlertsEnabled,
                keepScreenOn = settings.keepScreenOn,
                primaryOrderListAutoEntryEnabled = settings.primaryOrderListAutoEntryEnabled,
                secondaryOrderListAutoEntryEnabled = settings.secondaryOrderListAutoEntryEnabled,
                orderListAutoEntryMaxChecksText = settings.orderListAutoEntryMaxChecksText,
                orderTrackingModeEnabled = settings.orderTrackingModeEnabled,
                kakaoRestApiKey = settings.kakaoRestApiKey,
                historyRetentionDays = settings.historyRetentionDays,
                observationPackageFilters = settings.observationPackageFilters,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAlertsEnabled(enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVibrationEnabled(enabled)
        }
    }

    fun setVoiceAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setVoiceAlertsEnabled(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(enabled)
        }
    }

    fun setPrimaryOrderListAutoEntryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrimaryOrderListAutoEntryEnabled(enabled)
        }
    }

    fun setSecondaryOrderListAutoEntryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSecondaryOrderListAutoEntryEnabled(enabled)
        }
    }

    fun setOrderListAutoEntryMaxChecksText(value: String) {
        viewModelScope.launch {
            settingsRepository.setOrderListAutoEntryMaxChecksText(value)
        }
    }

    fun setClientBlacklistText(value: String) {
        viewModelScope.launch {
            settingsRepository.setClientBlacklistText(value)
        }
    }

    fun setKakaoRestApiKey(value: String) {
        viewModelScope.launch {
            settingsRepository.setKakaoRestApiKey(value)
        }
    }

    fun setHistoryRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.setHistoryRetentionDays(days)
        }
    }

    fun setObservationPackageFilters(value: String) {
        viewModelScope.launch {
            settingsRepository.setObservationPackageFilters(value)
        }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        return SettingsViewModel(settingsRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
