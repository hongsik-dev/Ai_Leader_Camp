package com.catchpro.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.catchpro.app.data.model.OrderEventDraft
import com.catchpro.app.data.repository.AccessibilityCaptureRepository
import com.catchpro.app.data.repository.OrderEventRepository
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.data.repository.TmapQueueRepository
import com.catchpro.app.data.sync.RouteAddressCloudSyncManager
import com.catchpro.app.observation.NaverRouteDistanceService
import com.catchpro.app.ui.screen.dashboard.DashboardScreen
import com.catchpro.app.ui.screen.destinations.DestinationsScreen
import com.catchpro.app.ui.screen.match.MatchConfirmScreen
import com.catchpro.app.ui.screen.observation.ObservationLogScreen
import com.catchpro.app.ui.screen.onboarding.OnboardingScreen
import com.catchpro.app.ui.screen.settings.SettingsScreen
import com.catchpro.app.ui.screen.tmap.TmapQueueScreen
import kotlinx.coroutines.launch

@Composable
fun CatchProNavHost(
    accessibilityCaptureRepository: AccessibilityCaptureRepository,
    orderEventRepository: OrderEventRepository,
    settingsRepository: SettingsRepository,
    tmapQueueRepository: TmapQueueRepository,
    routeDistanceService: NaverRouteDistanceService,
    routeAddressCloudSyncManager: RouteAddressCloudSyncManager,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Routes.Dashboard) {
                                        saveState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Onboarding,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.Onboarding) {
                OnboardingScreen(
                    onContinue = {
                        navController.navigate(Routes.Dashboard) {
                            popUpTo(Routes.Onboarding) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
            composable(Routes.Dashboard) {
                DashboardScreen(
                    settingsRepository = settingsRepository,
                    onOpenDestinations = {
                        navController.navigate(Routes.Destinations)
                    },
                    onReviewLatestMatch = {
                        navController.navigate(Routes.matchConfirm("sample-order"))
                    },
                    onOpenTmapQueue = {
                        navController.navigate(Routes.TmapQueue)
                    },
                )
            }
            composable(Routes.Destinations) {
                DestinationsScreen(settingsRepository = settingsRepository)
            }
            composable(Routes.TmapQueue) {
                TmapQueueScreen(
                    settingsRepository = settingsRepository,
                    routeDistanceService = routeDistanceService,
                    routeAddressCloudSyncManager = routeAddressCloudSyncManager,
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    captureRepository = accessibilityCaptureRepository,
                    settingsRepository = settingsRepository,
                )
            }
            composable(Routes.ObservationLog) {
                ObservationLogScreen(
                    captureRepository = accessibilityCaptureRepository,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.MatchConfirmPattern,
                arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
            ) { entry ->
                val orderId = entry.arguments?.getString("orderId").orEmpty()
                val scope = rememberCoroutineScope()
                MatchConfirmScreen(
                    orderId = orderId,
                    onConfirm = {
                        scope.launch {
                            orderEventRepository.logEvent(
                                OrderEventDraft(
                                    orderTitle = "샘플 오더 확정",
                                    originSummary = "강남 상차",
                                    destinationSummary = "분당 하차",
                                    price = 72000,
                                    status = "confirmed",
                                ),
                            )
                            navController.popBackStack()
                        }
                    },
                    onCancel = {
                        scope.launch {
                            orderEventRepository.logEvent(
                                OrderEventDraft(
                                    orderTitle = "샘플 오더 취소",
                                    originSummary = "강남 상차",
                                    destinationSummary = "분당 하차",
                                    price = 72000,
                                    status = "skipped",
                                    failureReason = "오더 $orderId 검토 중 기사가 취소했습니다.",
                                ),
                            )
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
    }
}
