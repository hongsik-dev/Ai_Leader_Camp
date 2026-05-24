package com.catchpro.app.feature

import android.content.Context
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.license.LicenseRepository

object CatchProFeatureGate {
    fun proEntitlementSatisfied(context: Context): Boolean {
        if (!BuildConfig.IS_PRO_EDITION) return true
        if (BuildConfig.IS_PERSONAL_EDITION) return true
        return LicenseRepository.cachedEntitlementSatisfied(context)
    }

    fun autoConfirmAvailable(context: Context): Boolean =
        BuildConfig.FEATURE_AUTO_CONFIRM && proEntitlementSatisfied(context)

    fun experimentalAutoDetailConfirmAvailable(context: Context): Boolean =
        BuildConfig.FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM && proEntitlementSatisfied(context)

    fun routeAddressCloudSyncAvailable(context: Context): Boolean =
        BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC && proEntitlementSatisfied(context)

    fun naviOptimizationAvailable(context: Context): Boolean =
        BuildConfig.FEATURE_NAVI_OPTIMIZATION && proEntitlementSatisfied(context)

    fun proEntitlementNotice(context: Context): String? {
        return LicenseRepository.cachedNotice(context)
    }
}
