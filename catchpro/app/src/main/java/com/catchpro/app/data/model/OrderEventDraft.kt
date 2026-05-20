package com.catchpro.app.data.model

data class OrderEventDraft(
    val presetId: Long? = null,
    val orderTitle: String,
    val originSummary: String,
    val destinationSummary: String,
    val price: Int,
    val status: String,
    val failureReason: String? = null,
)
