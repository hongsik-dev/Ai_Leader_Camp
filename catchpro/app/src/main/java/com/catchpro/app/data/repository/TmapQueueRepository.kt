package com.catchpro.app.data.repository

import com.catchpro.app.data.local.dao.TmapQueueDao
import com.catchpro.app.data.local.entity.TmapQueueEntryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

private val CompletedQueueStatuses = setOf("completed")
private val QueueSourceTypes = setOf("primary", "secondary")
private val AddressDistanceMarkerRegex = Regex("""\(?\s*\d+(?:\.\d+)?\s*km\s*\)?""", RegexOption.IGNORE_CASE)
private val AddressNormalizeRegex = Regex("""[\s/(),._-]+""")

@Singleton
class TmapQueueRepository @Inject constructor(
    private val tmapQueueDao: TmapQueueDao,
) {
    fun queueEntries(): Flow<List<TmapQueueEntryEntity>> = tmapQueueDao.observeAll()

    suspend fun enqueueConfirmedOrder(
        orderSignature: String,
        sourceType: String,
        orderTitle: String,
        pickupAddress: String?,
        dropoffAddress: String?,
    ) {
        if (sourceType !in QueueSourceTypes) return

        val now = System.currentTimeMillis()
        val cleanedPickupAddress = pickupAddress.cleanQueueAddress()
        val cleanedDropoffAddress = dropoffAddress.cleanQueueAddress()
        val existingBySignature = tmapQueueDao.findBySignature(orderSignature)
        if (existingBySignature?.queueStatus?.let(CompletedQueueStatuses::contains) == true) return

        val existing = existingBySignature ?: tmapQueueDao.findActiveEntries()
            .firstOrNull { entry ->
                entry.matchesQueueRoute(
                    orderTitle = orderTitle,
                    pickupAddress = cleanedPickupAddress,
                    dropoffAddress = cleanedDropoffAddress,
                )
            }

        tmapQueueDao.insert(
            TmapQueueEntryEntity(
                id = existing?.id ?: 0,
                orderSignature = orderSignature,
                sourceType = sourceType,
                orderTitle = orderTitle,
                pickupAddress = selectBetterAddress(cleanedPickupAddress, existing?.pickupAddress),
                dropoffAddress = selectBetterAddress(cleanedDropoffAddress, existing?.dropoffAddress),
                manualPickupAddress = existing?.manualPickupAddress,
                manualDropoffAddress = existing?.manualDropoffAddress,
                queueStatus = existing
                    ?.queueStatus
                    ?.takeUnless { it == "cancelled" }
                    ?: "queued",
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun updateManualPickupAddress(
        id: Long,
        address: String,
    ) {
        tmapQueueDao.updateManualPickupAddress(
            id = id,
            address = address.trim(),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun updateManualDropoffAddress(
        id: Long,
        address: String,
    ) {
        tmapQueueDao.updateManualDropoffAddress(
            id = id,
            address = address.trim(),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun markPickupLinked(id: Long) {
        tmapQueueDao.updateStatus(
            id = id,
            status = "pickup-linked",
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun markDropoffLinked(id: Long) {
        tmapQueueDao.updateStatus(
            id = id,
            status = "dropoff-linked",
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun markCompleted(id: Long) {
        tmapQueueDao.updateStatus(
            id = id,
            status = "completed",
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun markCancelled(orderSignature: String) {
        tmapQueueDao.updateStatusBySignature(
            signature = orderSignature,
            status = "cancelled",
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    suspend fun removeEntry(id: Long) {
        tmapQueueDao.deleteById(id)
    }

    private fun TmapQueueEntryEntity.matchesQueueRoute(
        orderTitle: String,
        pickupAddress: String?,
        dropoffAddress: String?,
    ): Boolean {
        val sameDropoff = isSimilarQueueAddress(
            first = dropoffAddress,
            second = this.dropoffAddress ?: manualDropoffAddress,
        )
        if (!sameDropoff) return false

        val samePickup = isSimilarQueueAddress(
            first = pickupAddress,
            second = this.pickupAddress ?: manualPickupAddress,
        )
        val sameTitle = isSimilarQueueAddress(orderTitle, this.orderTitle)
        return samePickup || sameTitle
    }

    private fun String?.cleanQueueAddress(): String? {
        return this
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun selectBetterAddress(
        candidate: String?,
        existing: String?,
    ): String? {
        if (candidate.isNullOrBlank()) return existing.cleanQueueAddress()
        if (existing.isNullOrBlank()) return candidate

        val normalizedCandidate = candidate.normalizeQueueAddress()
        val normalizedExisting = existing.normalizeQueueAddress()
        if (normalizedCandidate.isBlank()) return existing
        if (normalizedExisting.isBlank()) return candidate
        if (normalizedCandidate == normalizedExisting) return candidate
        if (normalizedCandidate.contains(normalizedExisting)) return candidate
        if (normalizedExisting.contains(normalizedCandidate)) return existing
        return if (normalizedCandidate.length >= normalizedExisting.length) candidate else existing
    }

    private fun isSimilarQueueAddress(
        first: String?,
        second: String?,
    ): Boolean {
        val normalizedFirst = first.normalizeQueueAddress()
        val normalizedSecond = second.normalizeQueueAddress()
        if (normalizedFirst.length < 5 || normalizedSecond.length < 5) return false
        if (normalizedFirst == normalizedSecond) return true

        val shorter = listOf(normalizedFirst, normalizedSecond).minBy { it.length }
        val longer = listOf(normalizedFirst, normalizedSecond).maxBy { it.length }
        return shorter.length >= 6 && longer.contains(shorter)
    }

    private fun String?.normalizeQueueAddress(): String {
        return this
            .orEmpty()
            .lowercase()
            .replace(AddressDistanceMarkerRegex, "")
            .replace(AddressNormalizeRegex, "")
            .trim()
    }
}
