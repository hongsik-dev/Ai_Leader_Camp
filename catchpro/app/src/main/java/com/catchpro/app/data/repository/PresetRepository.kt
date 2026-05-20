package com.catchpro.app.data.repository

import com.catchpro.app.data.local.dao.PresetDao
import com.catchpro.app.data.local.entity.PresetEntity
import com.catchpro.app.data.model.PresetDraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PresetRepository @Inject constructor(
    private val presetDao: PresetDao,
) {
    val presets: Flow<List<PresetEntity>> = presetDao.observeAll()
    val presetCount: Flow<Int> = presetDao.observeCount()

    suspend fun savePreset(draft: PresetDraft) {
        presetDao.upsert(
            PresetEntity(
                name = draft.name,
                minPrice = draft.minPrice,
                maxDetourMinutes = draft.maxDetourMinutes,
                originRegions = draft.originRegions,
                destinationRegions = draft.destinationRegions,
                vehicleTypes = draft.vehicleTypes,
                paymentModes = draft.paymentModes,
                excludeKeywords = draft.excludeKeywords,
                requiresRoundTrip = draft.requiresRoundTrip,
                isEnabled = draft.isEnabled,
            ),
        )
    }

    suspend fun deletePreset(id: Long) {
        presetDao.deleteById(id)
    }

    suspend fun setPresetEnabled(id: Long, enabled: Boolean) {
        presetDao.updateEnabled(id, enabled)
    }
}
