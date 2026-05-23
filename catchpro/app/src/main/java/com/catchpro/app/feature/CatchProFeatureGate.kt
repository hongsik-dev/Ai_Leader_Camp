package com.catchpro.app.feature

import android.content.Context
import android.os.Build
import com.catchpro.app.BuildConfig

object CatchProFeatureGate {
    private const val GooglePlayInstallerPackage = "com.android.vending"
    private const val PackageInstallerPackage = "com.google.android.packageinstaller"

    fun proEntitlementSatisfied(context: Context): Boolean {
        if (!BuildConfig.IS_PRO_EDITION) return true
        if (BuildConfig.DEBUG || BuildConfig.IS_PERSONAL_EDITION) return true
        return context.installingPackageName() == GooglePlayInstallerPackage
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
        if (!BuildConfig.IS_PRO_EDITION || proEntitlementSatisfied(context)) return null
        return "Pro 기능은 Google Play에서 설치한 유료 앱에서 활성화됩니다."
    }

    @Suppress("DEPRECATION")
    private fun Context.installingPackageName(): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                packageManager.getInstallerPackageName(packageName)
            }
        }.getOrNull()?.takeUnless { it == PackageInstallerPackage }
    }
}
