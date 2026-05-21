package com.catchpro.app

import android.app.Application
import com.naver.maps.map.NaverMapSdk

object FlavorNaverMapInitializer {
    fun initialize(application: Application) {
        val naverMapKey = BuildConfig.NAVER_MAP_NCP_KEY_ID.trim()
        if (naverMapKey.isNotBlank()) {
            NaverMapSdk.getInstance(application).client = NaverMapSdk.NcpKeyClient(naverMapKey)
        }
    }
}
