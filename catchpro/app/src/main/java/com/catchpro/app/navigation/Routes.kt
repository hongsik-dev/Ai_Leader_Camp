package com.catchpro.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val Onboarding = "onboarding"
    const val Dashboard = "dashboard"
    const val Destinations = "destinations"
    const val Presets = "presets"
    const val TmapQueue = "tmap_queue"
    const val Settings = "settings"
    const val ObservationLog = "observation_log"
    const val MatchConfirmPattern = "match_confirm/{orderId}"

    fun matchConfirm(orderId: String): String = "match_confirm/$orderId"
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(Routes.Dashboard, "대시보드", Icons.Outlined.Dashboard),
    TopLevelDestination(Routes.Destinations, "오더 조건", Icons.Outlined.Place),
    TopLevelDestination(Routes.TmapQueue, "TMAP 연결", Icons.Outlined.Navigation),
    TopLevelDestination(Routes.Settings, "설정", Icons.Outlined.Settings),
)
