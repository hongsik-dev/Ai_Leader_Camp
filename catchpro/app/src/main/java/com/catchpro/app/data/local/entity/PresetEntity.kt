package com.catchpro.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minPrice: Int,
    val maxDetourMinutes: Int,
    val originRegions: List<String>,
    val destinationRegions: List<String>,
    val vehicleTypes: List<String>,
    val paymentModes: List<String>,
    val excludeKeywords: List<String>,
    val requiresRoundTrip: Boolean,
    val isEnabled: Boolean,
    val createdAtMillis: Long = System.currentTimeMillis(),
)
