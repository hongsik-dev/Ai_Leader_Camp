package com.catchpro.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.catchpro.app.data.repository.AccessibilityCaptureRepository
import com.catchpro.app.data.repository.OrderEventRepository
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.data.repository.TmapQueueRepository
import com.catchpro.app.data.sync.RouteAddressCloudSyncManager
import com.catchpro.app.navigation.CatchProNavHost
import com.catchpro.app.observation.NaverRouteDistanceService
import com.catchpro.app.ui.theme.CatchProTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Base64
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var accessibilityCaptureRepository: AccessibilityCaptureRepository

    @Inject
    lateinit var orderEventRepository: OrderEventRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var tmapQueueRepository: TmapQueueRepository

    @Inject
    lateinit var routeDistanceService: NaverRouteDistanceService

    @Inject
    lateinit var routeAddressCloudSyncManager: RouteAddressCloudSyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC) {
            routeAddressCloudSyncManager.start()
        }
        handleInsungDebugRouteAddressIntent(intent)

        setContent {
            CatchProTheme {
                CatchProNavHost(
                    accessibilityCaptureRepository = accessibilityCaptureRepository,
                    orderEventRepository = orderEventRepository,
                    settingsRepository = settingsRepository,
                    tmapQueueRepository = tmapQueueRepository,
                    routeDistanceService = routeDistanceService,
                    routeAddressCloudSyncManager = routeAddressCloudSyncManager,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInsungDebugRouteAddressIntent(intent)
    }

    private fun handleInsungDebugRouteAddressIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || BuildConfig.IS_NAVI_APP) return
        if (intent?.action != DebugSetRouteAddressesAction) return
        val encodedAddresses = intent.getStringExtra(DebugRouteAddressesExtra).orEmpty()
        val addresses = runCatching {
            String(Base64.getDecoder().decode(encodedAddresses), Charsets.UTF_8)
        }.getOrNull()?.trim().orEmpty()
        if (addresses.isBlank()) return
        lifecycleScope.launch {
            settingsRepository.setRouteAddressCloudSyncEnabled(true)
            settingsRepository.setTmapManualRouteAddressesText(addresses)
        }
    }

    private companion object {
        const val DebugSetRouteAddressesAction = "com.catchpro.app.DEBUG_SET_ROUTE_ADDRESSES"
        const val DebugRouteAddressesExtra = "addresses_base64"
    }
}
