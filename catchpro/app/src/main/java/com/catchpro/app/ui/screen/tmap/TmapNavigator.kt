package com.catchpro.app.ui.screen.tmap

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object TmapNavigator {
    private const val TmapPackageName = "com.skt.tmap.ku"

    fun launchForAddress(
        context: Context,
        address: String,
    ): Boolean {
        val normalized = address.trim()
        if (normalized.isBlank()) return false

        val navigationIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${Uri.encode(normalized)}"),
        ).apply {
            `package` = TmapPackageName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return runCatching {
            context.startActivity(navigationIntent)
            true
        }.recoverCatching {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$TmapPackageName"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            true
        }.recover {
            if (it is ActivityNotFoundException) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$TmapPackageName"),
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
