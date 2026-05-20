package com.catchpro.app.observation

fun ParsedOrderDraft.effectiveOrigin(): String? = origin

fun ParsedOrderDraft.effectiveDestination(): String? = destination

fun ParsedOrderDraft.effectiveRouteText(): String? = when {
    !effectiveOrigin().isNullOrBlank() && !effectiveDestination().isNullOrBlank() ->
        "${effectiveOrigin()} -> ${effectiveDestination()}"
    else -> routeText
}
