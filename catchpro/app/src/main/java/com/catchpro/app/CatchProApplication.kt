package com.catchpro.app

import android.app.Application
import com.naver.maps.map.NaverMapSdk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CatchProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val naverMapKey = BuildConfig.NAVER_MAP_NCP_KEY_ID.trim()
        if (naverMapKey.isNotBlank()) {
            NaverMapSdk.getInstance(this).client = NaverMapSdk.NcpKeyClient(naverMapKey)
        }
    }
}
