package com.catchpro.app.data.sync

import java.util.Base64

object RouteAddressSyncPayload {
    private const val Prefix = "CATCHPRO_ROUTE_SYNC_V1:"
    private const val DeepLinkPrefix = "catchpro://route-sync?data="
    private const val SlotCount = 6
    private val PrefixRegex = Regex("""CATCHPRO_ROUTE_SYNC_V1:([A-Za-z0-9_-]+)""")
    private val DeepLinkRegex = Regex("""catchpro://route-sync\?data=([A-Za-z0-9_-]+)""")

    fun shareText(addresses: List<String>): String {
        val encoded = encode(addresses)
        return buildString {
            appendLine("CatchPro 주소 동기화")
            appendLine("$DeepLinkPrefix$encoded")
            append(Prefix)
            append(encoded)
        }
    }

    fun decode(text: String?): List<String>? {
        if (text.isNullOrBlank()) return null
        val encoded = DeepLinkRegex.find(text)?.groupValues?.getOrNull(1)
            ?: PrefixRegex.find(text)?.groupValues?.getOrNull(1)
            ?: return null
        return runCatching {
            val decoded = String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
            decoded
                .split('\n')
                .map(String::trim)
                .take(SlotCount)
                .let { slots ->
                    slots + List((SlotCount - slots.size).coerceAtLeast(0)) { "" }
                }
        }.getOrNull()
    }

    private fun encode(addresses: List<String>): String {
        val slots = addresses
            .map(String::trim)
            .take(SlotCount)
            .let { it + List((SlotCount - it.size).coerceAtLeast(0)) { "" } }
        val raw = slots.joinToString("\n")
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.toByteArray(Charsets.UTF_8))
    }
}
