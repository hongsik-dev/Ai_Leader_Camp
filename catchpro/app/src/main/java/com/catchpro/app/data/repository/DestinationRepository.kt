package com.catchpro.app.data.repository

import com.catchpro.app.data.local.dao.DestinationDao
import com.catchpro.app.data.local.entity.DestinationEntity
import com.catchpro.app.data.model.DestinationDraft
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DestinationRepository @Inject constructor(
    private val destinationDao: DestinationDao,
) {
    val destinations: Flow<List<DestinationEntity>> = destinationDao.observeAll()
    val defaultDestination: Flow<DestinationEntity?> = destinationDao.observeDefaultDestination()

    suspend fun saveDestination(draft: DestinationDraft) {
        val destinationId = destinationDao.upsert(
            DestinationEntity(
                label = draft.label,
                address = draft.address,
                zone = draft.zone,
                note = draft.note,
                isDefault = draft.isDefault,
            ),
        )

        if (draft.isDefault) {
            destinationDao.replaceDefault(destinationId)
        }
    }

    suspend fun deleteDestination(id: Long) {
        destinationDao.deleteById(id)
    }

    suspend fun setDefaultDestination(id: Long) {
        destinationDao.replaceDefault(id)
    }
}
