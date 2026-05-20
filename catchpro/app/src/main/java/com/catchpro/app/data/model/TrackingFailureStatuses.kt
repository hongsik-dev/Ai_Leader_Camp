package com.catchpro.app.data.model

object TrackingFailureStatuses {
    val Skipped = setOf(
        "tracked-additional-cancelled",
        "tracked-additional-rejected",
        "order-tracking-auto-entry-blocked",
    )

    val Failed = setOf(
        "tracking-reference-pickup-match-missed",
        "tracked-additional-auto-confirm-click-failed",
        "tracked-additional-auto-confirm-unverified",
        "tracked-additional-manual-confirm-unverified",
        "tracked-additional-detail-not-open",
        "tracked-additional-manual-input-required",
    )

    val Visible = Failed + Skipped
}
