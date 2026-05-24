import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val catchProVersionCode = 12
val catchProVersionName = "0.1.11"

android {
    val localProperties = Properties().apply {
        val parentLocalPropertiesFile = rootProject.projectDir.parentFile?.resolve("local.properties")
        if (parentLocalPropertiesFile != null && parentLocalPropertiesFile.exists()) {
            parentLocalPropertiesFile.inputStream().use(::load)
        }
        val projectLocalPropertiesFile = rootProject.file("local.properties")
        if (projectLocalPropertiesFile.exists()) {
            projectLocalPropertiesFile.inputStream().use(::load)
        }
    }
    val kakaoRestApiKey = localProperties.getProperty("kakao.rest.api.key", "")
    val naverMapNcpKeyId = localProperties.getProperty("naver.map.ncp.key.id", "")
        .ifBlank { System.getenv("NAVER_MAP_NCP_KEY_ID").orEmpty() }
    val naverProxyBaseUrl = localProperties.getProperty("naver.proxy.base.url", "https://hongsik.blog/api/naver")
    val catchProApiBaseUrl = localProperties.getProperty("catchpro.api.base.url", "https://hongsik.blog")

    namespace = "com.catchpro.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.catchpro.app"
        minSdk = 26
        targetSdk = 37
        versionCode = catchProVersionCode
        versionName = catchProVersionName
        buildConfigField("String", "KAKAO_REST_API_KEY", "\"${kakaoRestApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "NAVER_MAP_NCP_KEY_ID", "\"${naverMapNcpKeyId.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "NAVER_MAP_NCP_KEY", "\"\"")
        buildConfigField("String", "NAVER_PROXY_BASE_URL", "\"${naverProxyBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        buildConfigField("String", "CATCHPRO_API_BASE_URL", "\"${catchProApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "deviceRole"
    productFlavors {
        create("insung") {
            dimension = "deviceRole"
            applicationId = "com.catchpro.app"
            buildConfigField("boolean", "IS_NAVI_APP", "false")
            buildConfigField("String", "CATCHPRO_EDITION", "\"personal\"")
            buildConfigField("boolean", "IS_PERSONAL_EDITION", "true")
            buildConfigField("boolean", "IS_FREE_EDITION", "false")
            buildConfigField("boolean", "IS_PRO_EDITION", "false")
            buildConfigField("boolean", "FEATURE_AUTO_CONFIRM", "true")
            buildConfigField("boolean", "FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM", "true")
            buildConfigField("boolean", "FEATURE_ROUTE_ADDRESS_CLOUD_SYNC", "true")
            buildConfigField("boolean", "FEATURE_NAVI_OPTIMIZATION", "true")
        }
        create("navi") {
            dimension = "deviceRole"
            applicationId = "com.catchpro.app"
            versionCode = 50_000 + catchProVersionCode
            versionNameSuffix = "-navi"
            buildConfigField("boolean", "IS_NAVI_APP", "true")
            buildConfigField("String", "CATCHPRO_EDITION", "\"personal\"")
            buildConfigField("boolean", "IS_PERSONAL_EDITION", "true")
            buildConfigField("boolean", "IS_FREE_EDITION", "false")
            buildConfigField("boolean", "IS_PRO_EDITION", "false")
            buildConfigField("boolean", "FEATURE_AUTO_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_ROUTE_ADDRESS_CLOUD_SYNC", "true")
            buildConfigField("boolean", "FEATURE_NAVI_OPTIMIZATION", "true")
        }
        create("insungFree") {
            dimension = "deviceRole"
            applicationId = "com.catchpro.insung.free"
            versionCode = 10_000 + catchProVersionCode
            versionNameSuffix = "-insung-free"
            buildConfigField("boolean", "IS_NAVI_APP", "false")
            buildConfigField("String", "CATCHPRO_EDITION", "\"free\"")
            buildConfigField("boolean", "IS_PERSONAL_EDITION", "false")
            buildConfigField("boolean", "IS_FREE_EDITION", "true")
            buildConfigField("boolean", "IS_PRO_EDITION", "false")
            buildConfigField("boolean", "FEATURE_AUTO_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_ROUTE_ADDRESS_CLOUD_SYNC", "false")
            buildConfigField("boolean", "FEATURE_NAVI_OPTIMIZATION", "false")
        }
        create("insungPro") {
            dimension = "deviceRole"
            applicationId = "com.catchpro.insung.pro"
            versionCode = 20_000 + catchProVersionCode
            versionNameSuffix = "-insung-pro"
            buildConfigField("boolean", "IS_NAVI_APP", "false")
            buildConfigField("String", "CATCHPRO_EDITION", "\"pro\"")
            buildConfigField("boolean", "IS_PERSONAL_EDITION", "false")
            buildConfigField("boolean", "IS_FREE_EDITION", "false")
            buildConfigField("boolean", "IS_PRO_EDITION", "true")
            buildConfigField("boolean", "FEATURE_AUTO_CONFIRM", "true")
            buildConfigField("boolean", "FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_ROUTE_ADDRESS_CLOUD_SYNC", "true")
            buildConfigField("boolean", "FEATURE_NAVI_OPTIMIZATION", "true")
        }
        create("naviFree") {
            dimension = "deviceRole"
            applicationId = "com.catchpro.navi.free"
            versionCode = 30_000 + catchProVersionCode
            versionNameSuffix = "-navi-free"
            buildConfigField("boolean", "IS_NAVI_APP", "true")
            buildConfigField("String", "CATCHPRO_EDITION", "\"free\"")
            buildConfigField("boolean", "IS_PERSONAL_EDITION", "false")
            buildConfigField("boolean", "IS_FREE_EDITION", "true")
            buildConfigField("boolean", "IS_PRO_EDITION", "false")
            buildConfigField("boolean", "FEATURE_AUTO_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_ROUTE_ADDRESS_CLOUD_SYNC", "false")
            buildConfigField("boolean", "FEATURE_NAVI_OPTIMIZATION", "false")
        }
        create("naviPro") {
            dimension = "deviceRole"
            applicationId = "com.catchpro.navi.pro"
            versionCode = 40_000 + catchProVersionCode
            versionNameSuffix = "-navi-pro"
            buildConfigField("boolean", "IS_NAVI_APP", "true")
            buildConfigField("String", "CATCHPRO_EDITION", "\"pro\"")
            buildConfigField("boolean", "IS_PERSONAL_EDITION", "false")
            buildConfigField("boolean", "IS_FREE_EDITION", "false")
            buildConfigField("boolean", "IS_PRO_EDITION", "true")
            buildConfigField("boolean", "FEATURE_AUTO_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_EXPERIMENTAL_AUTO_DETAIL_CONFIRM", "false")
            buildConfigField("boolean", "FEATURE_ROUTE_ADDRESS_CLOUD_SYNC", "true")
            buildConfigField("boolean", "FEATURE_NAVI_OPTIMIZATION", "true")
        }
    }

    sourceSets {
        getByName("insungFree") {
            java.srcDir("src/insung/java")
            kotlin.srcDir("src/insung/java")
        }
        getByName("insungPro") {
            java.srcDir("src/insung/java")
            kotlin.srcDir("src/insung/java")
        }
        getByName("naviFree") {
            java.srcDir("src/navi/java")
            kotlin.srcDir("src/navi/java")
            manifest.srcFile("src/navi/AndroidManifest.xml")
        }
        getByName("naviPro") {
            java.srcDir("src/navi/java")
            kotlin.srcDir("src/navi/java")
            manifest.srcFile("src/navi/AndroidManifest.xml")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    signingConfigs {
        getByName("debug") {
            val userDebugKeystore = file("C:/Users/misoh/.android/debug.keystore")
            if (userDebugKeystore.exists()) {
                storeFile = userDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val customerDebugApkDir = layout.buildDirectory.dir("customer-apks/debug")
val customerReleaseApkDir = layout.buildDirectory.dir("customer-apks/release")

tasks.register<Copy>("collectCustomerDebugApks") {
    group = "distribution"
    description = "Collects customer Free/Pro debug APKs with stable distribution file names."
    dependsOn(
        "assembleInsungFreeDebug",
        "assembleInsungProDebug",
        "assembleNaviFreeDebug",
        "assembleNaviProDebug",
    )
    into(customerDebugApkDir)
    from(layout.buildDirectory.file("outputs/apk/insungFree/debug/app-insungFree-debug.apk")) {
        rename { "CatchPro-Insung-Free-v$catchProVersionName-debug.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/insungPro/debug/app-insungPro-debug.apk")) {
        rename { "CatchPro-Insung-Pro-v$catchProVersionName-debug.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/naviFree/debug/app-naviFree-debug.apk")) {
        rename { "CatchPro-Navi-Free-v$catchProVersionName-debug.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/naviPro/debug/app-naviPro-debug.apk")) {
        rename { "CatchPro-Navi-Pro-v$catchProVersionName-debug.apk" }
    }
}

tasks.register<Copy>("collectCustomerReleaseApks") {
    group = "distribution"
    description = "Collects customer Free/Pro release APKs with stable distribution file names."
    dependsOn(
        "assembleInsungFreeRelease",
        "assembleInsungProRelease",
        "assembleNaviFreeRelease",
        "assembleNaviProRelease",
    )
    into(customerReleaseApkDir)
    from(layout.buildDirectory.file("outputs/apk/insungFree/release/app-insungFree-release-unsigned.apk")) {
        rename { "CatchPro-Insung-Free-v$catchProVersionName-release-unsigned.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/insungPro/release/app-insungPro-release-unsigned.apk")) {
        rename { "CatchPro-Insung-Pro-v$catchProVersionName-release-unsigned.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/naviFree/release/app-naviFree-release-unsigned.apk")) {
        rename { "CatchPro-Navi-Free-v$catchProVersionName-release-unsigned.apk" }
    }
    from(layout.buildDirectory.file("outputs/apk/naviPro/release/app-naviPro-release-unsigned.apk")) {
        rename { "CatchPro-Navi-Pro-v$catchProVersionName-release-unsigned.apk" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")

    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    add("naviImplementation", "com.naver.maps:map-sdk:3.23.2")
    add("naviFreeImplementation", "com.naver.maps:map-sdk:3.23.2")
    add("naviProImplementation", "com.naver.maps:map-sdk:3.23.2")

    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
