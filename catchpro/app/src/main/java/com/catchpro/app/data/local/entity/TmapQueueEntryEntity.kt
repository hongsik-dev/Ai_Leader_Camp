package com.catchpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tmap_queue_entries",
    indices = [
        Index("orderSignature", unique = true),
        Index("updatedAtMillis"),
    ],
)
data class TmapQueueEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderSignature: String,
    val sourceType: String,
    val orderTitle: String,
    val pickupAddress: String? = null,
    val dropoffAddress: String? = null,
    val manualPickupAddress: String? = null,
    val manualDropoffAddress: String? = null,
    val queueStatus: String = "queued",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
