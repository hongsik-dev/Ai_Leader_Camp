package com.catchpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_events",
    indices = [
        Index("presetId"),
        Index("createdAtMillis"),
    ],
)
data class OrderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long? = null,
    val orderTitle: String,
    val originSummary: String,
    val destinationSummary: String,
    val price: Int,
    val status: String,
    val failureReason: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)
