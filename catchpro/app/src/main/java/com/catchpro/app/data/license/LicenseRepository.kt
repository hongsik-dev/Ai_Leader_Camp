package com.catchpro.app.data.license

import android.content.Context
import android.provider.Settings
import com.catchpro.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class LicenseRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefName, Context.MODE_PRIVATE)

    fun snapshot(): LicenseSnapshot =
        prefs.snapshot(context.deviceLicenseId())

    fun saveIdentity(email: String, phone: String) {
        prefs.edit()
            .putString(KeyEmail, email.trim())
            .putString(KeyPhone, phone.onlyDigits())
            .apply()
    }

    suspend fun refreshIfNeeded(force: Boolean = false): LicenseSnapshot = withContext(Dispatchers.IO) {
        if (!BuildConfig.IS_PRO_EDITION || BuildConfig.IS_PERSONAL_EDITION) {
            return@withContext snapshot()
        }

        val cached = snapshot()
        val now = System.currentTimeMillis()
        if (!force && cached.nextCheckAfterMillis > now) {
            return@withContext cached
        }

        val email = cached.email.trim()
        val phone = cached.phone.onlyDigits()
        if (email.isBlank() && phone.isBlank()) {
            setInactive(
                status = "missing_identity",
                message = "이메일 또는 전화번호를 입력한 뒤 라이선스를 확인해 주세요.",
                now = now,
            )
            return@withContext snapshot()
        }

        val request = buildCheckRequest(
            email = email,
            phone = phone,
            deviceId = cached.deviceId.ifBlank { context.deviceLicenseId() },
        ) ?: run {
            setInactive(
                status = "not_configured",
                message = "라이선스 서버 주소가 설정되지 않았습니다.",
                now = now,
            )
            return@withContext snapshot()
        }

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    applyNetworkFailure(
                        message = "라이선스 서버 응답 실패(${response.code})",
                        now = now,
                    )
                    return@use
                }
                val json = JSONObject(body)
                applyServerResponse(json = json, now = now)
            }
        }.getOrElse { error ->
            applyNetworkFailure(
                message = "라이선스 확인 오류: ${error.message ?: error.javaClass.simpleName}",
                now = now,
            )
        }

        snapshot()
    }

    private fun buildCheckRequest(
        email: String,
        phone: String,
        deviceId: String,
    ): Request? {
        val apiBaseUrl = BuildConfig.CATCHPRO_API_BASE_URL.trim().trimEnd('/')
        if (apiBaseUrl.isBlank()) return null
        val payload = JSONObject()
            .put("edition", licenseEditionCode())
            .put("email", email)
            .put("phone", phone)
            .put("deviceId", deviceId)
            .put("packageName", context.packageName)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
        return Request.Builder()
            .url("$apiBaseUrl/api/license/check")
            .post(payload.toString().toRequestBody(JsonMediaType))
            .build()
    }

    private fun applyServerResponse(json: JSONObject, now: Long) {
        val status = json.optString("licenseStatus").ifBlank { json.optString("status") }
        val normalizedStatus = status.ifBlank { "unknown" }
        val expiresAt = json.optString("expiresAt")
        val expiresAtMillis = expiresAt.toEpochMillisOrNull()
        val active = json.optBoolean("ok", false) &&
            normalizedStatus in ActiveStatuses &&
            (expiresAtMillis == null || expiresAtMillis > now)
        val graceUntil = if (active) {
            listOfNotNull(expiresAtMillis, now + GraceMillis).minOrNull() ?: (now + GraceMillis)
        } else {
            0L
        }
        prefs.edit()
            .putBoolean(KeyActive, active)
            .putString(KeyStatus, normalizedStatus)
            .putString(
                KeyMessage,
                json.optString("message").ifBlank {
                    if (active) "라이선스가 활성화되어 있습니다." else "라이선스가 활성 상태가 아닙니다."
                },
            )
            .putString(KeyExpiresAt, expiresAt)
            .putLong(KeyExpiresAtMillis, expiresAtMillis ?: 0L)
            .putLong(KeyCheckedAt, now)
            .putLong(KeyGraceUntil, graceUntil)
            .putLong(KeyNextCheckAfter, now + if (active) ActiveCheckIntervalMillis else InactiveCheckIntervalMillis)
            .putString(KeyDeviceId, json.optString("deviceId").ifBlank { context.deviceLicenseId() })
            .apply()
    }

    private fun applyNetworkFailure(message: String, now: Long) {
        val cached = snapshot()
        val stillInGrace = cached.graceUntilMillis > now
        prefs.edit()
            .putBoolean(KeyActive, stillInGrace)
            .putString(KeyStatus, if (stillInGrace) "grace" else "network_failed")
            .putString(
                KeyMessage,
                if (stillInGrace) {
                    "$message. 최근 정상 인증 유예 시간 안이라 Pro 기능을 임시 유지합니다."
                } else {
                    "$message. 네트워크가 복구되면 다시 확인해 주세요."
                },
            )
            .putLong(KeyCheckedAt, now)
            .putLong(KeyNextCheckAfter, now + NetworkFailureRetryMillis)
            .apply()
    }

    private fun setInactive(status: String, message: String, now: Long) {
        prefs.edit()
            .putBoolean(KeyActive, false)
            .putString(KeyStatus, status)
            .putString(KeyMessage, message)
            .putLong(KeyCheckedAt, now)
            .putLong(KeyNextCheckAfter, now + InactiveCheckIntervalMillis)
            .apply()
    }

    companion object {
        private const val PrefName = "catchpro_license"
        private const val KeyActive = "pro_entitlement_active"
        private const val KeyStatus = "status"
        private const val KeyMessage = "message"
        private const val KeyExpiresAt = "expires_at"
        private const val KeyExpiresAtMillis = "expires_at_millis"
        private const val KeyCheckedAt = "checked_at"
        private const val KeyGraceUntil = "grace_until"
        private const val KeyNextCheckAfter = "next_check_after"
        private const val KeyEmail = "email"
        private const val KeyPhone = "phone"
        private const val KeyDeviceId = "device_id"
        private const val GraceMillis = 24L * 60L * 60L * 1000L
        private const val ActiveCheckIntervalMillis = 6L * 60L * 60L * 1000L
        private const val InactiveCheckIntervalMillis = 15L * 60L * 1000L
        private const val NetworkFailureRetryMillis = 10L * 60L * 1000L
        private val ActiveStatuses = setOf("active", "trial")
        private val JsonMediaType = "application/json; charset=utf-8".toMediaType()

        fun cachedEntitlementSatisfied(context: Context): Boolean {
            if (!BuildConfig.IS_PRO_EDITION || BuildConfig.IS_PERSONAL_EDITION) return true
            val prefs = context.applicationContext.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            return prefs.getBoolean(KeyActive, false) || prefs.getLong(KeyGraceUntil, 0L) > now
        }

        fun cachedNotice(context: Context): String? {
            if (!BuildConfig.IS_PRO_EDITION || cachedEntitlementSatisfied(context)) return null
            val prefs = context.applicationContext.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            return prefs.getString(KeyMessage, null)
                ?.takeIf { it.isNotBlank() }
                ?: "Pro 기능은 라이선스 확인 후 활성화됩니다."
        }

        private fun androidSharedPreferencesSnapshot(
            context: Context,
        ): LicenseSnapshot {
            val prefs = context.applicationContext.getSharedPreferences(PrefName, Context.MODE_PRIVATE)
            return prefs.snapshot(context.deviceLicenseId())
        }

        fun cachedSnapshot(context: Context): LicenseSnapshot =
            androidSharedPreferencesSnapshot(context)

        private fun Context.deviceLicenseId(): String =
            runCatching {
                Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "unknown-device"

        private fun android.content.SharedPreferences.snapshot(deviceIdFallback: String): LicenseSnapshot =
            LicenseSnapshot(
                active = getBoolean(KeyActive, !BuildConfig.IS_PRO_EDITION || BuildConfig.IS_PERSONAL_EDITION),
                status = getString(KeyStatus, "").orEmpty(),
                message = getString(KeyMessage, "").orEmpty(),
                expiresAt = getString(KeyExpiresAt, "").orEmpty(),
                expiresAtMillis = getLong(KeyExpiresAtMillis, 0L),
                checkedAtMillis = getLong(KeyCheckedAt, 0L),
                graceUntilMillis = getLong(KeyGraceUntil, 0L),
                nextCheckAfterMillis = getLong(KeyNextCheckAfter, 0L),
                email = getString(KeyEmail, "").orEmpty(),
                phone = getString(KeyPhone, "").orEmpty(),
                deviceId = getString(KeyDeviceId, "").orEmpty().ifBlank { deviceIdFallback },
                edition = licenseEditionCode(),
            )

        private fun licenseEditionCode(): String =
            "${if (BuildConfig.IS_NAVI_APP) "navi" else "insung"}-${BuildConfig.CATCHPRO_EDITION}"

        private fun String.onlyDigits(): String =
            filter(Char::isDigit)

        private fun String.toEpochMillisOrNull(): Long? {
            if (isBlank()) return null
            return runCatching { Instant.parse(this).toEpochMilli() }
                .recoverCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
                .getOrNull()
        }
    }
}

data class LicenseSnapshot(
    val active: Boolean,
    val status: String,
    val message: String,
    val expiresAt: String,
    val expiresAtMillis: Long,
    val checkedAtMillis: Long,
    val graceUntilMillis: Long,
    val nextCheckAfterMillis: Long,
    val email: String,
    val phone: String,
    val deviceId: String,
    val edition: String,
)
