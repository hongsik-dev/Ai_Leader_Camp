package com.catchpro.app.feature

import com.catchpro.app.BuildConfig

object CatchProEdition {
    val label: String
        get() = when {
            BuildConfig.IS_PERSONAL_EDITION -> "개인 운행판"
            BuildConfig.IS_PRO_EDITION -> "Pro"
            BuildConfig.IS_FREE_EDITION -> "Free"
            else -> BuildConfig.CATCHPRO_EDITION
        }

    val autoConfirmAvailable: Boolean
        get() = BuildConfig.FEATURE_AUTO_CONFIRM

    val experimentalAutoDetailConfirmAvailable: Boolean
        get() = BuildConfig.FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM

    val routeAddressCloudSyncAvailable: Boolean
        get() = BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC

    val naviOptimizationAvailable: Boolean
        get() = BuildConfig.FEATURE_NAVI_OPTIMIZATION
}
