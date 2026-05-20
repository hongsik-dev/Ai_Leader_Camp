package com.catchpro.app.data.model

data class PresetDraft(
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
)
