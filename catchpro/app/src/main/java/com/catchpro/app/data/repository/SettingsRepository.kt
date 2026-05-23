package com.catchpro.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.model.AppSettings
import com.catchpro.app.data.sync.RouteAddressSyncPayload
import com.catchpro.app.feature.CatchProFeatureGate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val ManualRouteAddressSlotCount = 6

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        val normalizedRouteAddresses = preferences[TmapManualRouteAddressesTextKey]
            .orEmpty()
            .normalizeManualRouteAddressesText()
        val autoConfirmAvailable = CatchProFeatureGate.autoConfirmAvailable(context)
        val autoDetailAvailable = CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(context)
        val cloudSyncAvailable = CatchProFeatureGate.routeAddressCloudSyncAvailable(context)
        val naviOptimizationAvailable = CatchProFeatureGate.naviOptimizationAvailable(context)
        val personalEdition = BuildConfig.IS_PERSONAL_EDITION
        AppSettings(
            clientBlacklistText = (preferences[ClientBlacklistTextKey] ?: DefaultClientBlacklistText)
                .withDefaultClientBlacklistEntries(),
            alertsEnabled = preferences[AlertsEnabledKey] ?: true,
            vibrationEnabled = preferences[VibrationEnabledKey] ?: true,
            voiceAlertsEnabled = preferences[VoiceAlertsEnabledKey] ?: true,
            keepScreenOn = preferences[KeepScreenOnKey] ?: false,
            autoConfirmFeatureAvailable = autoConfirmAvailable,
            autoDetailConfirmFeatureAvailable = autoDetailAvailable,
            routeAddressCloudSyncFeatureAvailable = cloudSyncAvailable,
            naviOptimizationFeatureAvailable = naviOptimizationAvailable,
            primaryOrderListAutoEntryEnabled =
                autoDetailAvailable && (preferences[PrimaryOrderListAutoEntryEnabledKey] ?: false),
            secondaryOrderListAutoEntryEnabled =
                autoDetailAvailable && (preferences[SecondaryOrderListAutoEntryEnabledKey] ?: false),
            orderListAutoEntryMaxChecksText = (preferences[OrderListAutoEntryMaxChecksTextKey]
                ?: DefaultOrderListAutoEntryMaxChecksText)
                .normalizeOrderListAutoEntryMaxChecksText(),
            kakaoRestApiKey = preferences[KakaoRestApiKeyKey]?.takeIf { it.isNotBlank() } ?: BuildConfig.KAKAO_REST_API_KEY,
            historyRetentionDays = preferences[HistoryRetentionDaysKey] ?: 14,
            observationPackageFilters = preferences[ObservationPackageFiltersKey]
                ?.takeIf { it.isNotBlank() }
                ?: DefaultObservationPackageFilters,
            activeDriveDestinationText = preferences[ActiveDriveDestinationTextKey]
                ?.takeIf(String::isOperationalDestinationAddress)
                .orEmpty(),
            tmapManualRouteAddressesText = normalizedRouteAddresses,
            routeAddressCloudSyncEnabled =
                cloudSyncAvailable && (preferences[RouteAddressCloudSyncEnabledKey] ?: true),
            routeAddressCloudSyncRoomCode = preferences[RouteAddressCloudSyncRoomCodeKey]
                ?.sanitizeRouteAddressCloudSyncRoomCode()
                ?.takeIf { it.length == RouteAddressCloudSyncRoomCodeLength }
                ?: DefaultRouteAddressCloudSyncRoomCode,
            routeAddressCloudSyncServerUrl = preferences[RouteAddressCloudSyncServerUrlKey]
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: DefaultRouteAddressCloudSyncServerUrl,
            primaryAutoConfirmEnabled = autoConfirmAvailable && (preferences[AutoConfirmEnabledKey] ?: false),
            primaryDestinationKeywords = preferences[AutoConfirmDestinationKeywordsKey].orEmpty(),
            primaryAutoConfirmExcludedKeywordsText = (preferences[PrimaryAutoConfirmExcludedKeywordsTextKey]
                ?: DefaultPrimaryAutoConfirmExcludedKeywordsText)
                .withDefaultKeywordEntries(DefaultPrimaryAutoConfirmExcludedKeywordEntries),
            trackingAutoConfirmExcludedKeywordsText = (preferences[TrackingAutoConfirmExcludedKeywordsTextKey]
                ?: DefaultTrackingAutoConfirmExcludedKeywordsText)
                .withDefaultKeywordEntries(DefaultTrackingAutoConfirmExcludedKeywordEntries),
            autoConfirmExcludedKeywordsText = (preferences[AutoConfirmExcludedKeywordsTextKey]
                ?: DefaultAutoConfirmExcludedKeywordsText)
                .withDefaultKeywordEntries(DefaultAutoConfirmExcludedKeywordEntries),
            primaryMinimumPriceText = preferences[AutoConfirmMinimumPriceTextKey].orEmpty(),
            primaryMaximumPickupDistanceKmText = preferences[AutoConfirmMaximumPickupDistanceKmTextKey].orEmpty(),
            primaryMaximumRouteDistanceKmText = preferences[AutoConfirmMaximumRouteDistanceKmTextKey].orEmpty(),
            primaryLongDistanceRuleEnabled = preferences[PrimaryLongDistanceRuleEnabledKey] ?: true,
            primaryLongDistanceThresholdKmText = preferences[PrimaryLongDistanceThresholdKmTextKey]
                ?: DefaultPrimaryLongDistanceThresholdKmText,
            primaryLongDistanceMinimumPriceText = preferences[PrimaryLongDistanceMinimumPriceTextKey]
                ?: DefaultPrimaryLongDistanceMinimumPriceText,
            secondaryAutoConfirmEnabled =
                personalEdition && (preferences[SecondaryAutoConfirmEnabledKey] ?: false),
            secondaryMaximumPickupDistanceKmText = preferences[SecondaryAutoConfirmMaximumPickupDistanceKmTextKey]
                ?: preferences[AutoConfirmMaximumPickupDistanceKmTextKey].orEmpty(),
            secondaryDestinationRadiusKmText = preferences[AutoConfirmDestinationRadiusKmTextKey].orEmpty(),
            orderTrackingModeEnabled = personalEdition &&
                (preferences[OrderTrackingModeEnabledKey]
                    ?: (preferences[SecondaryAutoConfirmEnabledKey] ?: false)),
            orderTrackingMaximumRouteDistanceKmText = preferences[OrderTrackingMaximumRouteDistanceKmTextKey].orEmpty(),
            orderTrackingMaxConfirmCountText = preferences[OrderTrackingMaxConfirmCountTextKey]
                ?: DefaultOrderTrackingMaxConfirmCountText,
            onboardingCompleted = preferences[OnboardingCompletedKey] ?: false,
        )
    }

    suspend fun setAlertsEnabled(enabled: Boolean) = updateBoolean(AlertsEnabledKey, enabled)

    suspend fun setVibrationEnabled(enabled: Boolean) = updateBoolean(VibrationEnabledKey, enabled)

    suspend fun setVoiceAlertsEnabled(enabled: Boolean) = updateBoolean(VoiceAlertsEnabledKey, enabled)

    suspend fun setKeepScreenOn(enabled: Boolean) = updateBoolean(KeepScreenOnKey, enabled)

    suspend fun setPrimaryOrderListAutoEntryEnabled(enabled: Boolean) =
        updateBoolean(
            PrimaryOrderListAutoEntryEnabledKey,
            enabled && CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(context),
        )

    suspend fun setSecondaryOrderListAutoEntryEnabled(enabled: Boolean) =
        updateBoolean(
            SecondaryOrderListAutoEntryEnabledKey,
            enabled && CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(context),
        )

    suspend fun setOrderListAutoEntryMaxChecksText(value: String) {
        dataStore.edit { preferences ->
            preferences[OrderListAutoEntryMaxChecksTextKey] = value.normalizeOrderListAutoEntryMaxChecksText()
        }
    }

    suspend fun setClientBlacklistText(value: String) {
        dataStore.edit { preferences ->
            preferences[ClientBlacklistTextKey] = value.trim().withDefaultClientBlacklistEntries()
        }
    }

    suspend fun setKakaoRestApiKey(value: String) {
        dataStore.edit { preferences ->
            preferences[KakaoRestApiKeyKey] = value.trim()
        }
    }

    suspend fun setHistoryRetentionDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[HistoryRetentionDaysKey] = days
        }
    }

    suspend fun setObservationPackageFilters(value: String) {
        dataStore.edit { preferences ->
            preferences[ObservationPackageFiltersKey] = value.trim()
        }
    }

    suspend fun setPrimaryAutoConfirmEnabled(enabled: Boolean) =
        updateBoolean(AutoConfirmEnabledKey, enabled && CatchProFeatureGate.autoConfirmAvailable(context))

    suspend fun setActiveDriveDestinationText(value: String) {
        val address = value.trim()
        if (address.isNotBlank() && !address.isOperationalDestinationAddress()) return
        dataStore.edit { preferences ->
            preferences[ActiveDriveDestinationTextKey] = address
        }
    }

    suspend fun setTmapManualRouteAddressesText(value: String) {
        val normalized = value.normalizeManualRouteAddressesText()
        dataStore.edit { preferences ->
            val slots = normalized.manualRouteAddressSlots()
            val now = System.currentTimeMillis()
            val tombstones = preferences[RouteAddressCompletedTombstonesTextKey]
                .orEmpty()
                .routeAddressCompletionTombstones(now)
                .toMutableList()
            slots.forEachIndexed { index, address ->
                if (address.isNotBlank()) {
                    tombstones.removeAll { it.matches(index, address) }
                }
            }
            preferences[TmapManualRouteAddressesTextKey] = normalized
            preferences[RouteAddressCompletedTombstonesTextKey] = tombstones.toRouteAddressCompletionTombstoneText()
        }
    }

    suspend fun completeTmapManualRouteAddressSlot(index: Int) {
        if (index !in 0 until ManualRouteAddressSlotCount) return
        dataStore.edit { preferences ->
            val slots = preferences[TmapManualRouteAddressesTextKey]
                .orEmpty()
                .manualRouteAddressSlots()
                .toMutableList()
            val completedAddress = slots.getOrNull(index).orEmpty()
            slots[index] = ""
            val now = System.currentTimeMillis()
            val tombstones = preferences[RouteAddressCompletedTombstonesTextKey]
                .orEmpty()
                .routeAddressCompletionTombstones(now)
                .toMutableList()
                .apply { addOrRefreshRouteAddressCompletion(index, completedAddress, now) }
            preferences[TmapManualRouteAddressesTextKey] = slots
                .joinToString("\n")
                .normalizeManualRouteAddressesText()
            preferences[RouteAddressCompletedTombstonesTextKey] = tombstones.toRouteAddressCompletionTombstoneText()

            val activeDestination = preferences[ActiveDriveDestinationTextKey].orEmpty()
            if (
                completedAddress.isNotBlank() &&
                activeDestination.normalizeRouteAddressKey() == completedAddress.normalizeRouteAddressKey()
            ) {
                preferences[ActiveDriveDestinationTextKey] = ""
            }
        }
    }

    suspend fun importRouteAddressSyncText(value: String): Boolean {
        val slots = RouteAddressSyncPayload.decode(value)
            ?.takeIf { it.any(String::isNotBlank) }
            ?: return false
        val normalized = slots.joinToString("\n").normalizeManualRouteAddressesText()
        dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            val tombstones = preferences[RouteAddressCompletedTombstonesTextKey]
                .orEmpty()
                .routeAddressCompletionTombstones(now)
                .toMutableList()
            normalized.manualRouteAddressSlots().forEachIndexed { index, address ->
                if (address.isNotBlank()) {
                    tombstones.removeAll { it.matches(index, address) }
                }
            }
            preferences[TmapManualRouteAddressesTextKey] = normalized
            preferences[RouteAddressCompletedTombstonesTextKey] = tombstones.toRouteAddressCompletionTombstoneText()
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun applyRouteAddressCloudSync(
        addresses: List<String>,
        activeDriveDestination: String,
        remoteUpdatedAtMillis: Long = 0L,
    ) {
        dataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            val currentSlots = preferences[TmapManualRouteAddressesTextKey]
                .orEmpty()
                .manualRouteAddressSlots()
            val incomingSlots = addresses
                .joinToString("\n")
                .manualRouteAddressSlots()
                .toMutableList()
            val tombstones = preferences[RouteAddressCompletedTombstonesTextKey]
                .orEmpty()
                .routeAddressCompletionTombstones(now)
                .toMutableList()

            incomingSlots.indices.forEach { index ->
                val currentAddress = currentSlots.getOrNull(index).orEmpty()
                val incomingAddress = incomingSlots[index]
                if (currentAddress.isNotBlank() && incomingAddress.isBlank()) {
                    tombstones.addOrRefreshRouteAddressCompletion(index, currentAddress, now)
                }
            }
            incomingSlots.indices.forEach { index ->
                val incomingAddress = incomingSlots[index]
                if (
                    incomingAddress.isNotBlank() &&
                    tombstones.any { it.matches(index, incomingAddress) }
                ) {
                    incomingSlots[index] = ""
                }
            }

            val normalized = incomingSlots.joinToString("\n").normalizeManualRouteAddressesText()
            val trackingDestination = activeDriveDestination
                .takeIf(String::isOperationalDestinationAddress)
                ?.takeUnless { destination ->
                    tombstones.any { it.matchesAddress(destination) } ||
                        incomingSlots.none { it.normalizeRouteAddressKey() == destination.normalizeRouteAddressKey() }
                }
            preferences[TmapManualRouteAddressesTextKey] = normalized
            preferences[ActiveDriveDestinationTextKey] = trackingDestination.orEmpty()
            preferences[RouteAddressCompletedTombstonesTextKey] = tombstones.toRouteAddressCompletionTombstoneText()
        }
    }

    suspend fun setRouteAddressCloudSyncEnabled(enabled: Boolean) =
        updateBoolean(RouteAddressCloudSyncEnabledKey, enabled && CatchProFeatureGate.routeAddressCloudSyncAvailable(context))

    suspend fun setRouteAddressCloudSyncRoomCode(value: String) {
        dataStore.edit { preferences ->
            preferences[RouteAddressCloudSyncRoomCodeKey] =
                value.sanitizeRouteAddressCloudSyncRoomCode()
        }
    }

    suspend fun saveDetectedNavigationDestinationAddress(
        value: String,
        slotIndex: Int? = null,
        updateActiveDriveDestination: Boolean = slotIndex == null,
    ) {
        val address = value.trim()
        if (!address.isOperationalDestinationAddress()) return

        dataStore.edit { preferences ->
            val slots = preferences[TmapManualRouteAddressesTextKey]
                .orEmpty()
                .manualRouteAddressSlots()
                .toMutableList()
            val targetIndex = slotIndex
                ?.takeIf { it in 0 until ManualRouteAddressSlotCount }
                ?: slots.indexOfFirst { it.isBlank() }
                    .takeIf { it >= 0 }
                ?: ManualRouteAddressSlotCount - 1
            slots[targetIndex] = address
            val now = System.currentTimeMillis()
            val tombstones = preferences[RouteAddressCompletedTombstonesTextKey]
                .orEmpty()
                .routeAddressCompletionTombstones(now)
                .toMutableList()
                .apply { removeAll { it.matches(targetIndex, address) } }
            val normalized = slots.joinToString("\n").normalizeManualRouteAddressesText()
            preferences[TmapManualRouteAddressesTextKey] = normalized
            preferences[RouteAddressCompletedTombstonesTextKey] = tombstones.toRouteAddressCompletionTombstoneText()
            if (updateActiveDriveDestination && address.isOperationalDestinationAddress()) {
                preferences[ActiveDriveDestinationTextKey] = address
            }
        }
    }

    suspend fun clearTmapManualRouteOrderSlot(
        orderSlotIndex: Int,
        clearActiveDriveDestination: Boolean = orderSlotIndex == 0,
    ) {
        if (orderSlotIndex !in 0 until ManualRouteAddressSlotCount / 2) return
        dataStore.edit { preferences ->
            val slots = preferences[TmapManualRouteAddressesTextKey]
                .orEmpty()
                .manualRouteAddressSlots()
                .toMutableList()
            val pickupSlotIndex = orderSlotIndex * 2
            val dropoffSlotIndex = pickupSlotIndex + 1
            slots[pickupSlotIndex] = ""
            slots[dropoffSlotIndex] = ""
            preferences[TmapManualRouteAddressesTextKey] = slots.joinToString("\n")
            if (clearActiveDriveDestination) {
                preferences[ActiveDriveDestinationTextKey] = ""
            }
        }
    }

    suspend fun setPrimaryDestinationKeywords(value: String) {
        dataStore.edit { preferences ->
            preferences[AutoConfirmDestinationKeywordsKey] = value.trim()
        }
    }

    suspend fun setAutoConfirmExcludedKeywordsText(value: String) {
        dataStore.edit { preferences ->
            preferences[AutoConfirmExcludedKeywordsTextKey] = value.normalizeKeywordLines()
        }
    }

    suspend fun setPrimaryAutoConfirmExcludedKeywordsText(value: String) {
        dataStore.edit { preferences ->
            preferences[PrimaryAutoConfirmExcludedKeywordsTextKey] = value.normalizeKeywordLines()
        }
    }

    suspend fun setTrackingAutoConfirmExcludedKeywordsText(value: String) {
        dataStore.edit { preferences ->
            preferences[TrackingAutoConfirmExcludedKeywordsTextKey] = value.normalizeKeywordLines()
        }
    }

    suspend fun setPrimaryMinimumPriceText(value: String) {
        dataStore.edit { preferences ->
            preferences[AutoConfirmMinimumPriceTextKey] = value.trim()
        }
    }

    suspend fun setPrimaryMaximumPickupDistanceKmText(value: String) {
        dataStore.edit { preferences ->
            preferences[AutoConfirmMaximumPickupDistanceKmTextKey] = value.trim()
        }
    }

    suspend fun setPrimaryMaximumRouteDistanceKmText(value: String) {
        dataStore.edit { preferences ->
            preferences[AutoConfirmMaximumRouteDistanceKmTextKey] = value.trim()
        }
    }

    suspend fun setPrimaryLongDistanceRuleEnabled(enabled: Boolean) =
        updateBoolean(PrimaryLongDistanceRuleEnabledKey, enabled)

    suspend fun setPrimaryLongDistanceThresholdKmText(value: String) {
        dataStore.edit { preferences ->
            preferences[PrimaryLongDistanceThresholdKmTextKey] = value.trim()
        }
    }

    suspend fun setPrimaryLongDistanceMinimumPriceText(value: String) {
        dataStore.edit { preferences ->
            preferences[PrimaryLongDistanceMinimumPriceTextKey] = value.trim()
        }
    }

    suspend fun setSecondaryAutoConfirmEnabled(enabled: Boolean) =
        updateBoolean(SecondaryAutoConfirmEnabledKey, enabled && BuildConfig.IS_PERSONAL_EDITION)

    suspend fun setSecondaryMaximumPickupDistanceKmText(value: String) {
        dataStore.edit { preferences ->
            preferences[SecondaryAutoConfirmMaximumPickupDistanceKmTextKey] = value.trim()
        }
    }

    suspend fun setSecondaryDestinationRadiusKmText(value: String) {
        dataStore.edit { preferences ->
            preferences[AutoConfirmDestinationRadiusKmTextKey] = value.trim()
        }
    }

    suspend fun setOrderTrackingModeEnabled(enabled: Boolean) =
        updateBoolean(OrderTrackingModeEnabledKey, enabled && BuildConfig.IS_PERSONAL_EDITION)

    suspend fun setOrderTrackingMaximumRouteDistanceKmText(value: String) {
        dataStore.edit { preferences ->
            preferences[OrderTrackingMaximumRouteDistanceKmTextKey] = value.trim()
        }
    }

    suspend fun setOrderTrackingMaxConfirmCountText(value: String) {
        dataStore.edit { preferences ->
            preferences[OrderTrackingMaxConfirmCountTextKey] = value.trim()
        }
    }

    private suspend fun updateBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    private fun String.withDefaultClientBlacklistEntries(): String {
        val entries = split('\n', ',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toMutableList()
        DefaultClientBlacklistEntries.forEach { defaultEntry ->
            val alreadyExists = entries.any { entry ->
                entry.normalizeClientBlacklistKey() == defaultEntry.normalizeClientBlacklistKey()
            }
            if (!alreadyExists) entries += defaultEntry
        }
        return entries.distinctBy { it.normalizeClientBlacklistKey() }
            .joinToString("\n")
    }

    private fun String.normalizeClientBlacklistKey(): String =
        lowercase(Locale.KOREAN)
            .replace(Regex("""[\s\-_:：/().]+"""), "")
            .trim()

    private fun String.normalizeOrderListAutoEntryMaxChecksText(): String {
        val fallback = DefaultOrderListAutoEntryMaxChecksText.toInt()
        val parsed = trim().toIntOrNull() ?: fallback
        return parsed
            .coerceIn(
                MinimumOrderListAutoEntryMaxChecks,
                MaximumOrderListAutoEntryMaxChecks,
            )
            .toString()
    }

    private fun String.withDefaultKeywordEntries(defaultEntries: List<String>): String {
        val entries = keywordEntries().toMutableList()
        val existingKeys = entries.map { it.normalizeKeywordKey() }.toMutableSet()
        defaultEntries.forEach { entry ->
            if (existingKeys.add(entry.normalizeKeywordKey())) {
                entries += entry
            }
        }
        return entries.joinToString(", ")
    }

    private fun String.normalizeKeywordKey(): String =
        lowercase(Locale.KOREAN)
            .replace(Regex("""[\s/\\\-_.()·:：]+"""), "")
            .trim()

    private companion object {
        val AlertsEnabledKey = booleanPreferencesKey("alerts_enabled")
        val VibrationEnabledKey = booleanPreferencesKey("vibration_enabled")
        val VoiceAlertsEnabledKey = booleanPreferencesKey("voice_alerts_enabled")
        val KeepScreenOnKey = booleanPreferencesKey("keep_screen_on")
        val PrimaryOrderListAutoEntryEnabledKey = booleanPreferencesKey("primary_order_list_auto_entry_enabled")
        val SecondaryOrderListAutoEntryEnabledKey = booleanPreferencesKey("secondary_order_list_auto_entry_enabled")
        val OrderListAutoEntryMaxChecksTextKey = stringPreferencesKey("order_list_auto_entry_max_checks_text")
        const val DefaultOrderListAutoEntryMaxChecksText = "30"
        private const val MinimumOrderListAutoEntryMaxChecks = 1
        private const val MaximumOrderListAutoEntryMaxChecks = 30
        val ClientBlacklistTextKey = stringPreferencesKey("client_blacklist_text")
        val DefaultClientBlacklistEntries = listOf(
            "오마이퀵서비스-1566-5912",
            "오산드림퀵",
        )
        val DefaultClientBlacklistText = DefaultClientBlacklistEntries.joinToString("\n")
        val KakaoRestApiKeyKey = stringPreferencesKey("kakao_rest_api_key")
        val OnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
        val HistoryRetentionDaysKey = intPreferencesKey("history_retention_days")
        val ObservationPackageFiltersKey = stringPreferencesKey("observation_package_filters")
        const val DefaultObservationPackageFilters = "insung"
        val AutoConfirmEnabledKey = booleanPreferencesKey("auto_confirm_enabled")
        val ActiveDriveDestinationTextKey = stringPreferencesKey("active_drive_destination_text")
        val TmapManualRouteAddressesTextKey = stringPreferencesKey("tmap_manual_route_addresses_text")
        val RouteAddressCompletedTombstonesTextKey = stringPreferencesKey("route_address_completed_tombstones_text")
        val RouteAddressCloudSyncEnabledKey = booleanPreferencesKey("route_address_cloud_sync_enabled")
        val RouteAddressCloudSyncRoomCodeKey = stringPreferencesKey("route_address_cloud_sync_room_code")
        val RouteAddressCloudSyncServerUrlKey = stringPreferencesKey("route_address_cloud_sync_server_url")
        const val RouteAddressCloudSyncRoomCodeLength = 6
        const val DefaultRouteAddressCloudSyncRoomCode = "250501"
        const val DefaultRouteAddressCloudSyncServerUrl = "ws://43.200.8.165/catchpro-sync"
        val AutoConfirmDestinationKeywordsKey = stringPreferencesKey("auto_confirm_destination_keywords")
        val PrimaryAutoConfirmExcludedKeywordsTextKey =
            stringPreferencesKey("primary_auto_confirm_excluded_keywords_text")
        val TrackingAutoConfirmExcludedKeywordsTextKey =
            stringPreferencesKey("tracking_auto_confirm_excluded_keywords_text")
        val AutoConfirmExcludedKeywordsTextKey = stringPreferencesKey("auto_confirm_excluded_keywords_text")
        val DefaultPrimaryAutoConfirmExcludedKeywordEntries = listOf(
            "사다주기",
            "물건사다",
            "물건사다주기",
            "사서전달",
            "AS",
            "AS센터",
            "AS방문",
            "에이에스",
            "에이에스센터",
            "방문후",
            "방문하고",
            "대기",
            "대기시간",
            "대기비",
            "법원",
            "집행",
            "증인",
            "심부름",
            "시간예약",
            "시간정해진",
            "예약",
            "왕복",
            "복귀",
        )
        val DefaultTrackingAutoConfirmExcludedKeywordEntries = listOf(
            "핸드폰",
            "휴대폰",
            "모바일",
            "폰",
        ) + DefaultPrimaryAutoConfirmExcludedKeywordEntries
        val DefaultPrimaryAutoConfirmExcludedKeywordsText =
            DefaultPrimaryAutoConfirmExcludedKeywordEntries.joinToString(", ")
        val DefaultTrackingAutoConfirmExcludedKeywordsText =
            DefaultTrackingAutoConfirmExcludedKeywordEntries.joinToString(", ")
        val DefaultAutoConfirmExcludedKeywordEntries = listOf(
            "핸드폰",
            "휴대폰",
            "모바일",
            "폰",
            "사다주기",
            "물건사다",
            "물건사다주기",
            "사서전달",
            "AS",
            "AS센터",
            "AS방문",
            "에이에스",
            "에이에스센터",
            "방문후",
            "방문하고",
            "대기",
            "대기시간",
            "대기비",
            "법원",
            "집행",
            "증인",
            "심부름",
            "시간예약",
            "시간정해진",
            "예약",
            "왕복",
            "복귀",
        )
        val DefaultAutoConfirmExcludedKeywordsText =
            DefaultAutoConfirmExcludedKeywordEntries.joinToString(", ")
        val AutoConfirmMinimumPriceTextKey = stringPreferencesKey("auto_confirm_minimum_price_text")
        val AutoConfirmMaximumPickupDistanceKmTextKey = stringPreferencesKey("auto_confirm_maximum_pickup_distance_km_text")
        val AutoConfirmMaximumRouteDistanceKmTextKey =
            stringPreferencesKey("auto_confirm_maximum_route_distance_km_text")
        val PrimaryLongDistanceRuleEnabledKey = booleanPreferencesKey("primary_long_distance_rule_enabled")
        val PrimaryLongDistanceThresholdKmTextKey = stringPreferencesKey("primary_long_distance_threshold_km_text")
        val PrimaryLongDistanceMinimumPriceTextKey = stringPreferencesKey("primary_long_distance_minimum_price_text")
        const val DefaultPrimaryLongDistanceThresholdKmText = "40"
        const val DefaultPrimaryLongDistanceMinimumPriceText = "42000"
        val SecondaryAutoConfirmEnabledKey = booleanPreferencesKey("secondary_auto_confirm_enabled")
        val SecondaryAutoConfirmMaximumPickupDistanceKmTextKey =
            stringPreferencesKey("secondary_auto_confirm_maximum_pickup_distance_km_text")
        val AutoConfirmDestinationRadiusKmTextKey = stringPreferencesKey("auto_confirm_destination_radius_km_text")
        val OrderTrackingModeEnabledKey = booleanPreferencesKey("order_tracking_mode_enabled")
        val OrderTrackingMaximumRouteDistanceKmTextKey =
            stringPreferencesKey("order_tracking_maximum_route_distance_km_text")
        val OrderTrackingMaxConfirmCountTextKey = stringPreferencesKey("order_tracking_max_confirm_count_text")
        const val DefaultOrderTrackingMaxConfirmCountText = "2"
    }
}

private data class RouteAddressCompletionTombstone(
    val slotIndex: Int,
    val completedAtMillis: Long,
    val addressKey: String,
) {
    fun matches(
        index: Int,
        address: String,
    ): Boolean = slotIndex == index && matchesAddress(address)

    fun matchesAddress(address: String): Boolean =
        addressKey.isNotBlank() && address.normalizeRouteAddressKey() == addressKey
}

private fun String.routeAddressCompletionTombstones(nowMillis: Long): List<RouteAddressCompletionTombstone> =
    lineSequence()
        .mapNotNull { line ->
            val parts = line.split('\t')
            val slotIndex = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val completedAtMillis = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val addressKey = parts.getOrNull(2).orEmpty()
            RouteAddressCompletionTombstone(
                slotIndex = slotIndex,
                completedAtMillis = completedAtMillis,
                addressKey = addressKey,
            )
        }
        .filter { tombstone ->
            tombstone.slotIndex in 0 until ManualRouteAddressSlotCount &&
                tombstone.addressKey.isNotBlank() &&
                nowMillis - tombstone.completedAtMillis <= RouteAddressCompletionTombstoneTtlMillis
        }
        .distinctBy { "${it.slotIndex}:${it.addressKey}" }
        .toList()

private fun MutableList<RouteAddressCompletionTombstone>.addOrRefreshRouteAddressCompletion(
    slotIndex: Int,
    address: String,
    nowMillis: Long,
) {
    val addressKey = address.normalizeRouteAddressKey()
    if (slotIndex !in 0 until ManualRouteAddressSlotCount || addressKey.isBlank()) return
    removeAll { it.slotIndex == slotIndex && it.addressKey == addressKey }
    add(
        RouteAddressCompletionTombstone(
            slotIndex = slotIndex,
            completedAtMillis = nowMillis,
            addressKey = addressKey,
        ),
    )
}

private fun List<RouteAddressCompletionTombstone>.toRouteAddressCompletionTombstoneText(): String =
    joinToString("\n") { tombstone ->
        "${tombstone.slotIndex}\t${tombstone.completedAtMillis}\t${tombstone.addressKey}"
    }

private const val RouteAddressCompletionTombstoneTtlMillis = 30 * 60 * 1000L

internal fun String.normalizeManualRouteAddressesText(): String =
    manualRouteAddressSlots()
        .joinToString("\n")

internal fun String.manualRouteAddressSlots(): List<String> =
    split('\n')
        .map { line ->
            line.trim()
        }
        .take(ManualRouteAddressSlotCount)
        .let { slots ->
            slots + List((ManualRouteAddressSlotCount - slots.size).coerceAtLeast(0)) { "" }
        }

private fun String.manualRouteAddressLines(): List<String> =
    manualRouteAddressSlots()
        .filter(String::isNotBlank)

private fun List<String>.firstOperationalRouteAddress(): String? =
    firstOrNull(String::isOperationalDestinationAddress)

private fun String.normalizeKeywordLines(): String =
    keywordEntries()
        .distinctBy {
            it.lowercase(Locale.KOREAN)
                .replace(Regex("""[\s/\\\-_.()·:：]+"""), "")
        }
        .joinToString(", ")

private fun String.keywordEntries(): List<String> =
    split('\n', ',', '·')
        .map(String::trim)
        .filter(String::isNotBlank)

private fun String.sanitizeRouteAddressCloudSyncRoomCode(): String =
    filter(Char::isDigit)
        .take(6)

private fun String.normalizeRouteAddressKey(): String =
    lowercase(Locale.KOREAN)
        .replace(Regex("""[\s/(),._\-·]+"""), "")
        .trim()

internal fun String.isOperationalDestinationAddress(): Boolean {
    val normalized = trim()
        .replace(Regex("""\s+"""), " ")
    if (normalized.length !in 8..120) return false
    if (NavigationUiNoiseRegex.containsMatchIn(normalized)) return false
    if (normalized.length < 8) return false
    val hasCityOrDistrict = Regex("(시|군|구)").containsMatchIn(normalized)
    if (!hasCityOrDistrict) return false

    val words = normalized.split(Regex("""\s+""")).filter(String::isNotBlank)
    val administrativeWordRegex = Regex(""".*(특별시|광역시|특별자치시|특별자치도|도|시|군|구|동|읍|면|리|가)$""")
    val townWordRegex = Regex(""".*(동|읍|면|리|가)$""")
    val hasDetailedDongAddress = words.indices.any { index ->
        townWordRegex.matches(words[index]) &&
            words.drop(index + 1).any { detail ->
                detail.length >= 2 && !administrativeWordRegex.matches(detail)
            }
    }
    val hasRoadDetail = Regex("""[가-힣A-Za-z0-9]+(로|길)\s*\d+""").containsMatchIn(normalized)
    return hasDetailedDongAddress || hasRoadDetail
}

private val NavigationUiNoiseRegex = Regex(
    """(안내\s*시작|자동차|대중교통|도보|자전거|닫기|경유지|추가|전환|더보기|시간\s*설정|로드뷰|레이어|설정\s*버튼|우회전|좌회전|직진|meter|주변장소탐색|경로\s*새로고침|음성검색|택시비|700원|[0-9]+원~)""",
    RegexOption.IGNORE_CASE,
)
