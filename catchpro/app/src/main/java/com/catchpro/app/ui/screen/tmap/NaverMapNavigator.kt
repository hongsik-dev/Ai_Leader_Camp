package com.catchpro.app.ui.screen.tmap

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object NaverMapNavigator {
    private const val NaverMapPackageName = "com.nhn.android.nmap"
    private const val AppName = "com.catchpro.app"

    fun launchNavigation(
        context: Context,
        latitude: Double,
        longitude: Double,
        name: String,
    ): Boolean {
        val url = Uri.Builder()
            .scheme("nmap")
            .authority("navigation")
            .appendQueryParameter("dlat", latitude.toString())
            .appendQueryParameter("dlng", longitude.toString())
            .appendQueryParameter("dname", name.ifBlank { "목적지" })
            .appendQueryParameter("appname", AppName)
            .build()
        return launchNaverMap(context, url)
    }

    fun launchSearch(
        context: Context,
        query: String,
    ): Boolean {
        val normalized = query.trim()
        if (normalized.isBlank()) return false
        val url = Uri.Builder()
            .scheme("nmap")
            .authority("search")
            .appendQueryParameter("query", normalized)
            .appendQueryParameter("appname", AppName)
            .build()
        return launchNaverMap(context, url)
    }

    private fun launchNaverMap(
        context: Context,
        uri: Uri,
    ): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.recoverCatching {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$NaverMapPackageName"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            true
        }.recover {
            if (it is ActivityNotFoundException) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$NaverMapPackageName"),
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
