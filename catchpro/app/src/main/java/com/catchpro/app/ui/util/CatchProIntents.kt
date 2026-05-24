package com.catchpro.app.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.catchpro.app.service.CatchProAccessibilityService

private const val AccessibilityDetailsAction = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
private const val AccessibilityServiceComponentExtra =
    "android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME"
private const val KakaoConsultUrl = "http://pf.kakao.com/_AFkbX/chat"

fun Context.openCatchProAccessibilitySettings() {
    val serviceComponent = ComponentName(this, CatchProAccessibilityService::class.java).flattenToString()
    val detailsIntent = Intent(AccessibilityDetailsAction).apply {
        putExtra(AccessibilityServiceComponentExtra, serviceComponent)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val intent = if (detailsIntent.resolveActivity(packageManager) != null) {
        detailsIntent
    } else {
        fallbackIntent
    }
    runCatching { startActivity(intent) }
        .recoverCatching { startActivity(fallbackIntent) }
}

fun Context.openOverlayPermissionSettings() {
    val packageUri = Uri.parse("package:$packageName")
    val detailsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(detailsIntent) }
        .recoverCatching { startActivity(fallbackIntent) }
}

fun Context.openKakaoConsult() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(KakaoConsultUrl)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
