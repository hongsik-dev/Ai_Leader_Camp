package com.catchpro.app.ui.screen.destinations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.feature.CatchProEdition
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DestinationsUiState(
    val enabled: Boolean = false,
    val orderListAutoEntryEnabled: Boolean = false,
    val orderListAutoEntryMaxChecksText: String = "30",
    val destinationKeywords: String = "",
    val minimumPriceText: String = "",
    val autoConfirmFeatureAvailable: Boolean = false,
    val autoDetailFeatureAvailable: Boolean = false,
    val editionLabel: String = CatchProEdition.label,
)

class DestinationsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<DestinationsUiState> = settingsRepository.settings
        .map { settings ->
            DestinationsUiState(
                enabled = settings.primaryAutoConfirmEnabled,
                orderListAutoEntryEnabled = settings.primaryOrderListAutoEntryEnabled,
                orderListAutoEntryMaxChecksText = settings.orderListAutoEntryMaxChecksText,
                destinationKeywords = settings.primaryDestinationKeywords,
                minimumPriceText = settings.primaryMinimumPriceText,
                autoConfirmFeatureAvailable = settings.autoConfirmFeatureAvailable,
                autoDetailFeatureAvailable = settings.autoDetailConfirmFeatureAvailable,
                editionLabel = CatchProEdition.label,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DestinationsUiState(),
        )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrimaryAutoConfirmEnabled(enabled)
        }
    }

    fun setOrderListAutoEntryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPrimaryOrderListAutoEntryEnabled(enabled)
        }
    }

    fun setOrderListAutoEntryMaxChecksText(value: String) {
        viewModelScope.launch {
            settingsRepository.setOrderListAutoEntryMaxChecksText(value)
        }
    }

    fun savePrimaryRules(
        destinationKeywords: String,
        minimumPriceText: String,
    ) {
        viewModelScope.launch {
            if (uiState.value.autoConfirmFeatureAvailable) {
                settingsRepository.setPrimaryAutoConfirmEnabled(true)
            }
            settingsRepository.setPrimaryDestinationKeywords(destinationKeywords)
            settingsRepository.setPrimaryMinimumPriceText(minimumPriceText)
        }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(DestinationsViewModel::class.java)) {
                        return DestinationsViewModel(settingsRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
