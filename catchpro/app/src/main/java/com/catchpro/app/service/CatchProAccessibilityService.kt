package com.catchpro.app.service

import android.Manifest
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.catchpro.app.BuildConfig
import com.catchpro.app.data.local.entity.AccessibilityCaptureEntity
import com.catchpro.app.data.model.AppSettings
import com.catchpro.app.data.model.OrderEventDraft
import com.catchpro.app.data.model.OperationLogDraft
import com.catchpro.app.data.region.KoreaAdministrativeAreas
import com.catchpro.app.data.repository.AccessibilityCaptureRepository
import com.catchpro.app.data.repository.OrderEventRepository
import com.catchpro.app.data.repository.OperationLogRepository
import com.catchpro.app.data.repository.SettingsRepository
import com.catchpro.app.data.repository.TmapQueueRepository
import com.catchpro.app.data.repository.isOperationalDestinationAddress
import com.catchpro.app.data.sync.RouteAddressCloudSyncManager
import com.catchpro.app.feature.CatchProFeatureGate
import com.catchpro.app.observation.AddressDistanceResolver
import com.catchpro.app.observation.AutoConfirmDecision
import com.catchpro.app.observation.AutoConfirmEvaluator
import com.catchpro.app.observation.DeviceLocationProvider
import com.catchpro.app.observation.KakaoRouteDistanceService
import com.catchpro.app.observation.KoreanOrderDraftParser
import com.catchpro.app.observation.ParsedOrderDraft
import com.catchpro.app.observation.RouteDistanceOutcome
import com.catchpro.app.observation.RouteWaypoint
import com.catchpro.app.observation.effectiveDestination
import com.catchpro.app.observation.effectiveOrigin
import com.catchpro.app.observation.effectiveRouteText
import com.catchpro.app.observation.matchesObservedPackage
import com.catchpro.app.observation.toObservationPackageFilters
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CatchProAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var captureRepository: AccessibilityCaptureRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var orderEventRepository: OrderEventRepository

    @Inject
    lateinit var operationLogRepository: OperationLogRepository

    @Inject
    lateinit var tmapQueueRepository: TmapQueueRepository

    @Inject
    lateinit var kakaoRouteDistanceService: KakaoRouteDistanceService

    @Inject
    lateinit var routeAddressCloudSyncManager: RouteAddressCloudSyncManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val addressDistanceResolver by lazy { AddressDistanceResolver(this) }
    private val deviceLocationProvider by lazy { DeviceLocationProvider(this) }
    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var lastCaptureSignature: String? = null
    private var lastCaptureAtMillis: Long = 0L
    private var lastDiagnosticCaptureSignature: String? = null
    private var lastDiagnosticCaptureAtMillis: Long = 0L
    private var lastTmapArrivalSignature: String? = null
    private var lastTmapArrivalAtMillis: Long = 0L
    private var lastNavigationDestinationSignature: String? = null
    private var lastNavigationDestinationAtMillis: Long = 0L
    private var lastNavigationDestinationMissSignature: String? = null
    private var lastNavigationDestinationMissAtMillis: Long = 0L
    private var lastNavigationAddressContextSignature: String? = null
    private var lastNavigationAddressContextAtMillis: Long = 0L
    private var pendingNavigationAddressSyncContext: PendingNavigationAddressSyncContext? = null
    private var lastInsungDetailAddressMissSignature: String? = null
    private var lastInsungDetailAddressMissAtMillis: Long = 0L
    private val recentInsungDetailAddressSyncSignatures = linkedMapOf<String, Long>()
    private var pendingPickupCompletionPrompt: PendingPickupCompletionPrompt? = null
    private var lastPickupPromptSignature: String? = null
    private var lastPickupPromptAtMillis: Long = 0L
    private var lastPickupConfirmedSignature: String? = null
    private var lastPickupConfirmedAtMillis: Long = 0L
    private var lastDropoffSignatureClickSignature: String? = null
    private var lastDropoffSignatureClickAtMillis: Long = 0L
    private var lastDropoffPromptSignature: String? = null
    private var lastDropoffPromptAtMillis: Long = 0L
    private var lastDropoffSendActionSignature: String? = null
    private var lastDropoffSendActionAtMillis: Long = 0L
    private var lastDropoffConfirmedSignature: String? = null
    private var lastDropoffConfirmedAtMillis: Long = 0L
    private var lastAutoConfirmSignature: String? = null
    private var lastAutoConfirmAtMillis: Long = 0L
    private var autoEntryListPausedUntilMillis: Long = 0L
    private var lastDuplicateConfirmSuppressedSignature: String? = null
    private var lastDuplicateConfirmSuppressedAtMillis: Long = 0L
    private var lastInsungConfirmFailureTextSignature: String? = null
    private var lastInsungConfirmFailureTextAtMillis: Long = 0L
    private var lastCancelledAutoConfirmSignature: String? = null
    private var lastCancelledAutoConfirmAtMillis: Long = 0L
    private var roadDistanceInFlightSignature: String? = null
    private var lastDismissedManualSignature: String? = null
    private var lastDismissedManualAtMillis: Long = 0L
    private var lastManualPromptSignature: String? = null
    private var lastManualPromptAtMillis: Long = 0L
    private var lastManualInputRequiredSignature: String? = null
    private var lastManualInputRequiredAtMillis: Long = 0L
    private var lastCaptureCleanupAtMillis: Long = 0L
    private var lastCaptureCompactAtMillis: Long = 0L
    @Volatile
    private var dailyLogCleanupInFlight: Boolean = false
    @Volatile
    private var lastDailyLogCleanupDate: LocalDate? = null
    private var lastDailyLogCleanupCheckAtMillis: Long = 0L
    private var dailyLogResetRunnable: Runnable? = null
    private var trackedAutoConfirmedOrder: TrackedAutoConfirmedOrder? = null
    private var trackingReferenceOrder: TrackedAutoConfirmedOrder? = null
    private var trackingReferencePickupCompleted: Boolean = false
    private var trackingReferencePickupCompletedAtMillis: Long = 0L
    private var trackingReferenceDropoffCompleted: Boolean = false
    private var trackingAdditionalOrder: TrackedAutoConfirmedOrder? = null
    private var trackingAdditionalDropoffCompleted: Boolean = false
    private var trackingAdditionalConfirmedCount: Int = 0
    private var addressCopyOverlayView: TextView? = null
    private var keepScreenOnOverlayView: View? = null
    private var runModeOverlay: RunModeOverlayViews? = null
    private var runModeOverlayVisible: Boolean = false
    private var lastRunModeOverlaySignature: String? = null
    private var manualDecisionOverlay: ManualDecisionOverlayViews? = null
    private var pendingManualConfirmation: PendingManualConfirmation? = null
    private var lastAddressCopyCandidate: String? = null
    private var lastAddressCopyOverlayLogSignature: String? = null
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady: Boolean = false
    private var pendingSpeechMessage: String? = null
    private var lastAlertDeliveryFailureSignature: String? = null
    private var lastAlertDeliveryFailureAtMillis: Long = 0L
    private val roadDistanceCache = linkedMapOf<String, CachedRoadDistanceEvaluation>()
    private val processedOrderLocks = linkedMapOf<String, Long>()
    private val autoEntryListLocks = linkedMapOf<String, Long>()
    private val autoEntryCheckedDetailSignatures = linkedMapOf<String, Long>()
    private val autoEntryDetailNotOpenLogStates = linkedMapOf<String, AutoEntryDetailNotOpenLogState>()
    private val autoConfirmVerificationLoggedSignatures = linkedMapOf<String, Long>()
    private val pendingConfirmAttempts = linkedMapOf<String, PendingConfirmAttempt>()
    private val navigationOrderSlotBySignature = linkedMapOf<String, Int>()
    private var pendingAutoListEntry: PendingAutoListEntry? = null
    private var lastAutoListEntryAtMillis: Long = 0L
    private var lastAutoEntryMode: AutoEntryMode? = null
    private val autoEntryRegionLocks = linkedMapOf<AutoEntryListRegion, Long>()
    private var lastAutoEntryWarmListRescanAtMillis: Long = 0L
    private var lastAutoEntryWarmListRescanSignature: String? = null
    private var lastTrackingAutoEntryGateBlockSignature: String? = null
    private var lastTrackingAutoEntryGateBlockAtMillis: Long = 0L
    private var primaryAutoEntryCheckCount: Int = 0
    private var secondaryAutoEntryCheckCount: Int = 0
    private var primaryAutoEntryConfirmedCount: Int = 0
    private var secondaryAutoEntryConfirmedCount: Int = 0
    private var primaryAutoEntryNextRegion: AutoEntryListRegion = AutoEntryListRegion.Top
    private var secondaryAutoEntryNextRegion: AutoEntryListRegion = AutoEntryListRegion.Top
    private val autoEntryCandidateCursors = linkedMapOf<String, Int>()

    @Volatile
    private var activePackageFilters: List<String> = emptyList()

    @Volatile
    private var activeSettings: AppSettings = AppSettings()

    @Volatile
    private var listOcrInFlight: Boolean = false

    private var settingsJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        ensureNotificationChannel()
        val now = System.currentTimeMillis()
        maybeResetPreviousLogsForToday(nowMillis = now, force = true)
        scheduleNextDailyLogReset(now)
        if (BuildConfig.FEATURE_ROUTE_ADDRESS_CLOUD_SYNC) {
            routeAddressCloudSyncManager.start()
        }
        if (settingsJob == null) {
            settingsJob = serviceScope.launch {
                settingsRepository.settings.collectLatest { settings ->
                    activeSettings = settings
                    activePackageFilters = settings.observationPackageFilters.toObservationPackageFilters()
                    addressDistanceResolver.warm(settings.activeDriveDestinationText)
                    maybeCleanupCaptureLog(System.currentTimeMillis())
                    mainHandler.post {
                        updateKeepScreenOnOverlay(settings.keepScreenOn)
                        updateRunModeOverlay(
                            visible = runModeOverlayVisible,
                            settings = settings,
                        )
                        updateAlertVoiceEngine(settings)
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType !in RelevantEvents) return
        val now = System.currentTimeMillis()
        maybeResetPreviousLogsForToday(nowMillis = now)
        val eventPackageName = event.packageName?.toString().orEmpty()
        if (
            maybeHandleInsungConfirmFailureText(
                packageName = eventPackageName,
                texts = event.text.map(CharSequence::toString),
                capturedAtMillis = now,
                detectionSource = "event:${event.eventType.toReadableEventType()}",
            )
        ) {
            return
        }

        val root = rootInActiveWindow ?: run {
            if (buildRunModeOverlaySignature(visible = false, settings = activeSettings) != lastRunModeOverlaySignature) {
                mainHandler.post { updateRunModeOverlay(visible = false) }
            }
            return
        }
        val packageName = (root.packageName ?: event.packageName)?.toString().orEmpty()
        val isInsungPackage = isSupportedInsungPackage(packageName)
        val showRunModeOverlay = isRunModeOverlayPackage(packageName)
        if (buildRunModeOverlaySignature(visible = showRunModeOverlay, settings = activeSettings) != lastRunModeOverlaySignature) {
            mainHandler.post {
                updateRunModeOverlay(
                    visible = showRunModeOverlay,
                    settings = activeSettings,
                )
            }
        }
        if (!shouldCapturePackage(packageName)) {
            if (!isInsungPackage) {
                clearAddressCopyOverlay("지원하지 않는 패키지:$packageName")
            }
            return
        }
        if (
            hasRecentPendingConfirmAttempt(now) &&
            maybeHandleInsungConfirmFailureText(
                packageName = packageName,
                texts = root.collectVisibleTexts(maxTokens = MaxPreviewTokens * 6),
                capturedAtMillis = now,
                detectionSource = "visible-root",
            )
        ) {
            return
        }

        if (!isInsungPackage) {
            clearAddressCopyOverlay("지원하지 않는 패키지:$packageName")
        }
        if (isTmapPackage(packageName)) {
            maybeHandleTmapArrivalDetected(
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
            )
        }
        if (isNavigationAddressProviderPackage(packageName)) {
            maybeHandleNavigationDestinationDetected(
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
            )
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            var clickedDraft: ParsedOrderDraft? = null
            maybeRememberNavigationAddressSyncContext(
                event = event,
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
            )
            if (isInsungPackage) {
                clickedDraft = KoreanOrderDraftParser.parseInsungQuick(
                    root = root,
                    packageName = packageName,
                )
                maybeHandleInsungDetailAddressDetected(
                    root = root,
                    packageName = packageName,
                    parsedDraft = clickedDraft,
                    capturedAtMillis = now,
                )
            }
            if (
                isInsungPackage &&
                clickedDraft?.isConfirmableDetailScreen == true &&
                maybeHandleInsungConfirmButtonClickEvent(
                    event = event,
                    root = root,
                    parsedDraft = clickedDraft,
                    capturedAtMillis = now,
                )
            ) {
                return
            }
            if (
                isInsungPackage &&
                clickedDraft?.isConfirmableDetailScreen == true &&
                maybeAutoConfirmFast(
                    root = root,
                    packageName = packageName,
                    capturedAtMillis = now,
                    parsedDraftOverride = clickedDraft,
                )
            ) {
                return
            }
            maybeHandlePickupCompletionClick(
                event = event,
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
            )
            maybeHandleDropoffCompletionClick(
                event = event,
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
            )
            maybeHandleAutoCancelledClick(
                event = event,
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
            )
            return
        }

        if (
            maybeAutoEnterOrderListPriority(
                root = root,
                packageName = packageName,
                eventType = event.eventType,
                capturedAtMillis = now,
            )
        ) {
            return
        }

        val parsedDraft = if (isInsungPackage) {
            KoreanOrderDraftParser.parseInsungQuick(
                root = root,
                packageName = packageName,
            )
        } else {
            null
        }
        if (
            parsedDraft?.isConfirmableDetailScreen == true &&
            maybeAutoConfirmFast(
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
                parsedDraftOverride = parsedDraft,
            )
        ) {
            return
        }

        if (parsedDraft != null) {
            maybeHandleInsungDetailAddressDetected(
                root = root,
                packageName = packageName,
                parsedDraft = parsedDraft,
                capturedAtMillis = now,
            )
            maybeRememberVisibleNavigationAddressSyncContext(
                root = root,
                packageName = packageName,
                parsedDraft = parsedDraft,
                capturedAtMillis = now,
            )
        }

        maybeHandleAutoConfirmVerifiedStatus(
            parsedDraft = parsedDraft,
            capturedAtMillis = now,
        )

        if (
            maybeHandlePickupCompletionPrompt(
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
                parsedDraft = parsedDraft,
            )
        ) {
            return
        }
        if (
            maybeHandleDropoffCompletionPrompt(
                root = root,
                packageName = packageName,
                capturedAtMillis = now,
                parsedDraft = parsedDraft,
            )
        ) {
            return
        }

        maybeClearStaleManualConfirmation(
            packageName = packageName,
            parsedDraft = parsedDraft,
        )
        updateAddressCopyOverlay(
            packageName = packageName,
            parsedDraft = parsedDraft,
        )

        maybeHandleCancelledStatus(
            parsedDraft = parsedDraft,
            capturedAtMillis = now,
        )

        if (
            maybeAutoEnterOrderList(
                root = root,
                packageName = packageName,
                eventType = event.eventType,
                capturedAtMillis = now,
                parsedDraft = parsedDraft,
            )
        ) {
            return
        }

        val snapshot = buildSnapshot(
            root = root,
            packageName = packageName,
            eventType = event.eventType,
        )
        if (snapshot.nodeCount == 0) return

        val signature = buildString {
            append(packageName)
            append('|')
            append(snapshot.eventType)
            append('|')
            append(snapshot.screenTitle.orEmpty())
            append('|')
            append(snapshot.summaryText)
        }
        if (signature == lastCaptureSignature && now - lastCaptureAtMillis < CaptureCooldownMillis) {
            return
        }

        lastCaptureSignature = signature
        lastCaptureAtMillis = now
        serviceScope.launch {
            captureRepository.saveCapture(snapshot)
            maybeCleanupCaptureLog(now)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        dailyLogResetRunnable?.let(mainHandler::removeCallbacks)
        dailyLogResetRunnable = null
        removeAddressCopyOverlay()
        removeKeepScreenOnOverlay()
        removeRunModeOverlay()
        removeManualDecisionOverlay()
        shutdownTextToSpeech()
        settingsJob?.cancel()
        serviceScope.cancel()
    }

    private fun saveDiagnosticCapture(
        root: AccessibilityNodeInfo,
        packageName: String,
        tag: String,
        detail: String,
    ) {
        val now = System.currentTimeMillis()
        val signature = "$tag|$detail"
        if (
            signature == lastDiagnosticCaptureSignature &&
            now - lastDiagnosticCaptureAtMillis < DiagnosticCaptureCooldownMillis
        ) {
            return
        }
        val snapshot = buildSnapshot(
            root = root,
            packageName = packageName,
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        )
        if (snapshot.nodeCount == 0) return

        lastDiagnosticCaptureSignature = signature
        lastDiagnosticCaptureAtMillis = now
        val diagnosticSnapshot = snapshot.copy(
            eventType = "Diagnostic: $tag",
            screenTitle = tag,
            summaryText = detail.take(MaxSummaryLength),
            rawHierarchy = buildString {
                appendLine("diagnostic=$tag")
                appendLine("detail=$detail")
                appendLine("capturedAtMillis=$now")
                appendLine()
                append(snapshot.rawHierarchy)
            },
            capturedAtMillis = now,
        )
        serviceScope.launch {
            captureRepository.saveCapture(diagnosticSnapshot)
        }
    }

    private fun logOperation(
        eventType: String,
        status: String? = null,
        parsedDraft: ParsedOrderDraft? = null,
        orderSignature: String? = parsedDraft?.let(::buildOrderSignature),
        mode: AutoEntryMode? = null,
        source: String? = null,
        region: AutoEntryListRegion? = null,
        roadDistanceEvaluation: RoadDistanceEvaluation? = null,
        decision: AutoConfirmDecision? = null,
        manualInputRequired: Boolean? = null,
        manualReviewRequired: Boolean? = null,
        confirmed: Boolean? = null,
        destinationSummaryOverride: String? = null,
        reason: String? = null,
        clickDiagnostic: String? = null,
        screenSummary: String? = null,
        rawContext: String? = null,
        createdAtMillis: Long,
    ) {
        val price = parsedDraft?.price
        val straightKm = parsedDraft?.pickupToDropoffDistanceKm
        val estimatedRoadKm = straightKm?.takeIf { it > 0.0 }?.times(SecondaryEstimatedRoadDistanceMultiplier)
        serviceScope.launch {
            operationLogRepository.log(
                OperationLogDraft(
                    eventType = eventType,
                    status = status,
                    orderSignature = orderSignature?.take(MaxAutoEntrySignatureLength),
                    mode = mode?.toOperationModeLabel(),
                    source = source,
                    region = region?.toKoreanLabel(),
                    clientText = parsedDraft?.clientText?.shortDiagnosticText(),
                    orderTitle = parsedDraft?.effectiveRouteText()?.shortDiagnosticText(),
                    originSummary = parsedDraft?.effectiveOrigin()?.shortDiagnosticText(),
                    destinationSummary = destinationSummaryOverride ?: parsedDraft?.effectiveDestination()?.shortDiagnosticText(),
                    requesterLocation = parsedDraft?.requesterLocation?.shortDiagnosticText(),
                    pickupAddress = parsedDraft?.operationalPickupAddress()?.shortDiagnosticText(),
                    dropoffAddress = parsedDraft?.operationalDropoffAddress()?.shortDiagnosticText(),
                    detailNote = parsedDraft?.detailNote?.shortDiagnosticText(),
                    price = price,
                    currentToPickupDistanceKm = parsedDraft?.currentToPickupDistanceKm,
                    pickupToDropoffStraightKm = straightKm,
                    estimatedPickupToDropoffRoadKm = estimatedRoadKm,
                    pickupRoadDistanceKm = roadDistanceEvaluation?.pickupDistanceKm,
                    destinationMatchDistanceKm = roadDistanceEvaluation?.destinationRadiusDistanceKm,
                    farePerStraightKm = price.farePerKm(straightKm),
                    farePerEstimatedRoadKm = price.farePerKm(estimatedRoadKm),
                    shouldConfirm = decision?.shouldConfirm,
                    confirmed = confirmed,
                    manualInputRequired = manualInputRequired ?: decision?.manualInputRequired,
                    manualReviewRequired = manualReviewRequired ?: decision?.manualReviewRequired,
                    reason = reason?.take(MaxOperationReasonLength),
                    clickDiagnostic = clickDiagnostic?.take(MaxOperationReasonLength),
                    screenSummary = screenSummary?.shortDiagnosticText(),
                    rawContext = rawContext?.take(MaxOperationRawContextLength),
                ),
            )
        }
    }

    private fun logAlertDeliveryFailure(
        channel: String,
        reason: String,
        detail: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val signature = "$channel|$reason|${detail.orEmpty()}".take(MaxAutoEntrySignatureLength)
        if (
            lastAlertDeliveryFailureSignature == signature &&
            now - lastAlertDeliveryFailureAtMillis < AlertDeliveryFailureLogCooldownMillis
        ) {
            return
        }
        lastAlertDeliveryFailureSignature = signature
        lastAlertDeliveryFailureAtMillis = now
        logOperation(
            eventType = "order_alert_delivery_failed",
            status = channel,
            reason = reason,
            rawContext = detail,
            createdAtMillis = now,
        )
    }

    private fun shouldCapturePackage(packageName: String): Boolean {
        if (packageName.isBlank() || packageName in IgnoredPackages) return false
        if (IgnoredPackagePrefixes.any(packageName::startsWith)) return false
        val filters = activePackageFilters.ifEmpty { listOf(InsungPackageFilter) }
        return filters.matchesObservedPackage(packageName) ||
            isSupportedInsungPackage(packageName) ||
            isTmapPackage(packageName) ||
            isKakaoNavigationPackage(packageName)
    }

    private fun isSupportedInsungPackage(packageName: String): Boolean {
        if (packageName.startsWith("com.catchpro.", ignoreCase = true)) return false
        return KoreanOrderDraftParser.supportsPackage(packageName)
    }

    private fun isRunModeOverlayPackage(packageName: String): Boolean {
        return packageName.equals(InsungQuickPackage, ignoreCase = true) ||
            packageName.startsWith("$InsungQuickPackage.", ignoreCase = true)
    }

    private fun isTmapPackage(packageName: String): Boolean {
        return packageName.equals(TmapPackageName, ignoreCase = true) ||
            packageName.startsWith("$TmapPackageName.", ignoreCase = true)
    }

    private fun isKakaoNavigationPackage(packageName: String): Boolean {
        return KakaoNavigationPackageNames.any { knownPackage ->
            packageName.equals(knownPackage, ignoreCase = true) ||
                packageName.startsWith("$knownPackage.", ignoreCase = true)
        }
    }

    private fun isNavigationAddressProviderPackage(packageName: String): Boolean {
        return isKakaoNavigationPackage(packageName) || isTmapPackage(packageName)
    }

    private fun navigationAddressSourceLabel(packageName: String): String {
        return when {
            isTmapPackage(packageName) -> "tmap_navigation"
            isKakaoNavigationPackage(packageName) -> "kakao_navigation"
            else -> "navigation"
        }
    }

    private fun navigationAddressAppLabel(packageName: String): String {
        return when {
            isTmapPackage(packageName) -> "TMAP"
            isKakaoNavigationPackage(packageName) -> "다음지도/카카오내비"
            else -> "지도앱"
        }
    }

    private fun maybeRememberNavigationAddressSyncContext(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
    ) {
        if (!isSupportedInsungPackage(packageName)) return
        val parsedDraft = KoreanOrderDraftParser.parseInsungQuick(
            root = root,
            packageName = packageName,
        )
        val visibleTexts = root.collectVisibleTexts(maxTokens = MaxPreviewTokens * 4)
        val summary = visibleTexts.joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!event.isLikelyNavigationGuideClick(summary, parsedDraft)) return

        rememberNavigationAddressSyncContext(
            root = root,
            packageName = packageName,
            parsedDraft = parsedDraft,
            visibleTexts = visibleTexts,
            summary = summary,
            capturedAtMillis = capturedAtMillis,
            source = "insung-click",
        )
    }

    private fun maybeRememberVisibleNavigationAddressSyncContext(
        root: AccessibilityNodeInfo,
        packageName: String,
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ) {
        if (!isSupportedInsungPackage(packageName)) return
        if (!parsedDraft.isManualAddressDetailScreen()) return
        val visibleTexts = root.collectVisibleTexts(maxTokens = MaxPreviewTokens * 4)
        val summary = visibleTexts.joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!summary.contains("길안내")) return

        rememberNavigationAddressSyncContext(
            root = root,
            packageName = packageName,
            parsedDraft = parsedDraft,
            visibleTexts = visibleTexts,
            summary = summary,
            capturedAtMillis = capturedAtMillis,
            source = "insung-visible",
        )
    }

    private fun rememberNavigationAddressSyncContext(
        root: AccessibilityNodeInfo,
        packageName: String,
        parsedDraft: ParsedOrderDraft,
        visibleTexts: List<String>,
        summary: String,
        capturedAtMillis: Long,
        source: String,
    ) {
        val role = parsedDraft.navigationDetailAddressRole(summary) ?: run {
            logNavigationAddressContextMissing(
                parsedDraft = parsedDraft,
                source = source,
                summary = summary,
                visibleTexts = visibleTexts,
                capturedAtMillis = capturedAtMillis,
            )
            return
        }
        val routeSlots = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync()
        val addressSlotIndex = resolveSequentialManualRouteAddressSlotIndex(
            address = parsedDraft.routeAddressCandidates(role).firstOrNull(),
            routeSlots = routeSlots,
        ) ?: run {
            logNavigationAddressContextMissing(
                parsedDraft = parsedDraft,
                source = source,
                summary = summary,
                visibleTexts = visibleTexts,
                capturedAtMillis = capturedAtMillis,
            )
            return
        }
        val orderSignature = buildOrderSignature(parsedDraft)
        pendingNavigationAddressSyncContext = PendingNavigationAddressSyncContext(
            addressSlotIndex = addressSlotIndex,
            role = role,
            orderSignature = orderSignature,
            capturedAtMillis = capturedAtMillis,
            screenSummary = summary,
        )
        val contextSignature = "$source|$addressSlotIndex|${orderSignature.normalizeLogSignature()}|${summary.normalizeLogSignature()}"
        if (
            contextSignature != lastNavigationAddressContextSignature ||
            capturedAtMillis - lastNavigationAddressContextAtMillis >= NavigationAddressContextLogCooldownMillis
        ) {
            lastNavigationAddressContextSignature = contextSignature
            lastNavigationAddressContextAtMillis = capturedAtMillis
            saveDiagnosticCapture(
                root = root,
                packageName = packageName,
                tag = "NAVIGATION_ADDRESS_SYNC_CONTEXT_READY",
                detail = "${addressSlotIndex.toManualRouteAddressSlotLabel()} · source=$source · screen=${summary.shortDiagnosticText()}",
            )
            logOperation(
                eventType = "navigation_address_sync_context_ready",
                status = "navigation-address-sync-context-ready",
                parsedDraft = parsedDraft,
                orderSignature = orderSignature,
                source = source,
                confirmed = false,
                reason = "${addressSlotIndex.toManualRouteAddressSlotLabel()} 길안내 주소 저장 대기",
                screenSummary = summary,
                rawContext = visibleTexts.joinToString(" | ").take(MaxOperationRawContextLength),
                createdAtMillis = capturedAtMillis,
            )
        }
    }

    private fun logNavigationAddressContextMissing(
        parsedDraft: ParsedOrderDraft,
        source: String,
        summary: String,
        visibleTexts: List<String>,
        capturedAtMillis: Long,
    ) {
        val signature = "$source|missing|${summary.normalizeLogSignature()}"
        if (
            signature == lastNavigationAddressContextSignature &&
            capturedAtMillis - lastNavigationAddressContextAtMillis < NavigationAddressContextLogCooldownMillis
        ) {
            return
        }
        lastNavigationAddressContextSignature = signature
        lastNavigationAddressContextAtMillis = capturedAtMillis
        logOperation(
            eventType = "navigation_address_sync_context_missing",
            status = "navigation-address-sync-context-missing",
            parsedDraft = parsedDraft,
            source = source,
            confirmed = false,
            reason = "길안내 화면은 감지했지만 출발지/도착지 상세 화면 구분에 실패했습니다.",
            screenSummary = summary,
            rawContext = visibleTexts.joinToString(" | ").take(MaxOperationRawContextLength),
            createdAtMillis = capturedAtMillis,
        )
    }

    private fun maybeHandleInsungDetailAddressDetected(
        root: AccessibilityNodeInfo,
        packageName: String,
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ) {
        if (!isSupportedInsungPackage(packageName)) return
        val visibleTexts = root.collectVisibleTexts(maxTokens = MaxNavigationTextTokens)
        if (visibleTexts.isEmpty()) return
        val summary = visibleTexts
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(MaxNavigationSummaryLength)
        if (!summary.isLikelyInsungManualAddressDetailScreen()) return

        val role = parsedDraft.navigationDetailAddressRole(summary) ?: run {
            logInsungDetailAddressMiss(
                root = root,
                packageName = packageName,
                parsedDraft = parsedDraft,
                visibleTexts = visibleTexts,
                summary = summary,
                capturedAtMillis = capturedAtMillis,
                failureReason = "인성 상세 화면은 감지했지만 출발지/도착지 구분에 실패했습니다.",
            )
            return
        }
        val address = findInsungDetailLocationAddress(
            visibleTexts = visibleTexts,
            summary = summary,
            parsedDraft = parsedDraft,
            role = role,
        ) ?: run {
            logInsungDetailAddressMiss(
                root = root,
                packageName = packageName,
                parsedDraft = parsedDraft,
                visibleTexts = visibleTexts,
                summary = summary,
                capturedAtMillis = capturedAtMillis,
                failureReason = "인성 ${role.toKoreanRouteAddressRole()} 상세 화면의 위치 주소 후보를 찾지 못했습니다.",
            )
            return
        }

        val currentSlots = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync()
        val addressSlotIndex = resolveSequentialManualRouteAddressSlotIndex(
            address = address,
            routeSlots = currentSlots,
        ) ?: run {
            logInsungDetailAddressMiss(
                root = root,
                packageName = packageName,
                parsedDraft = parsedDraft,
                visibleTexts = visibleTexts,
                summary = summary,
                capturedAtMillis = capturedAtMillis,
                failureReason = "주소 6칸이 모두 채워져 인성 상세 주소를 추가 저장하지 않았습니다.",
            )
            return
        }
        val slotLabel = addressSlotIndex.toManualRouteAddressSlotLabel()
        val orderSignature = buildOrderSignature(parsedDraft)
        val storedAddress = currentSlots.getOrNull(addressSlotIndex).orEmpty()
        val normalizedAddressKey = address.normalizeRouteAddressKey()
        val alreadyStored = storedAddress.normalizeRouteAddressKey() == address.normalizeRouteAddressKey() &&
            activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync().any {
                it.normalizeRouteAddressKey() == address.normalizeRouteAddressKey()
            }
        val signature = "$addressSlotIndex|$normalizedAddressKey".normalizeLogSignature()
        trimRecentInsungDetailAddressSyncSignatures(capturedAtMillis)
        if (
            alreadyStored ||
            capturedAtMillis - (recentInsungDetailAddressSyncSignatures[signature] ?: 0L) < InsungDetailAddressSyncCooldownMillis
        ) {
            return
        }
        recentInsungDetailAddressSyncSignatures[signature] = capturedAtMillis
        val updatedSlots = currentSlots.toMutableList()
        updatedSlots[addressSlotIndex] = address
        activeSettings = activeSettings.copy(
            tmapManualRouteAddressesText = updatedSlots.joinToString("\n"),
        )
        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = "INSUNG_DETAIL_ADDRESS_SYNCED",
            detail = "slot=$slotLabel | address=${address.shortDiagnosticText()} | screen=${summary.shortDiagnosticText()}",
        )
        logOperation(
            eventType = "insung_detail_address_synced",
            status = "insung-detail-address-synced",
            parsedDraft = parsedDraft,
            orderSignature = orderSignature,
            source = "insung-detail",
            destinationSummaryOverride = address.shortDiagnosticText(),
            confirmed = false,
            reason = buildList {
                add("인성 ${role.toKoreanRouteAddressRole()} 상세 화면의 위치 주소를 $slotLabel 칸에 자동 저장했습니다.")
            }.joinToString(" · "),
            screenSummary = summary,
            rawContext = visibleTexts.joinToString(" | ").take(MaxOperationRawContextLength),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            settingsRepository.saveDetectedNavigationDestinationAddress(
                value = address,
                slotIndex = addressSlotIndex,
                updateActiveDriveDestination = false,
            )
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = "인성 상세 위치주소 자동 저장",
                    originSummary = "인성 ${role.toKoreanRouteAddressRole()} 상세",
                    destinationSummary = address,
                    price = parsedDraft.price ?: 0,
                    status = "insung-detail-address-synced",
                    failureReason = "$slotLabel 저장",
                ),
            )
        }
    }

    private fun trimRecentInsungDetailAddressSyncSignatures(nowMillis: Long) {
        val expiredKeys = recentInsungDetailAddressSyncSignatures
            .filterValues { timestamp -> nowMillis - timestamp >= InsungDetailAddressSyncCooldownMillis }
            .keys
            .toList()
        expiredKeys.forEach(recentInsungDetailAddressSyncSignatures::remove)
        while (recentInsungDetailAddressSyncSignatures.size > MaxRecentInsungDetailAddressSyncSignatures) {
            val firstKey = recentInsungDetailAddressSyncSignatures.keys.firstOrNull() ?: return
            recentInsungDetailAddressSyncSignatures.remove(firstKey)
        }
    }

    private fun logInsungDetailAddressMiss(
        root: AccessibilityNodeInfo,
        packageName: String,
        parsedDraft: ParsedOrderDraft,
        visibleTexts: List<String>,
        summary: String,
        capturedAtMillis: Long,
        failureReason: String,
    ) {
        val signature = "$failureReason|${summary.normalizeLogSignature()}"
        if (
            signature == lastInsungDetailAddressMissSignature &&
            capturedAtMillis - lastInsungDetailAddressMissAtMillis < InsungDetailAddressMissLogCooldownMillis
        ) {
            return
        }
        lastInsungDetailAddressMissSignature = signature
        lastInsungDetailAddressMissAtMillis = capturedAtMillis

        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = "INSUNG_DETAIL_ADDRESS_NOT_FOUND",
            detail = summary.shortDiagnosticText(),
        )
        logOperation(
            eventType = "insung_detail_address_not_found",
            status = "insung-detail-address-not-found",
            parsedDraft = parsedDraft,
            source = "insung-detail",
            confirmed = false,
            reason = failureReason,
            screenSummary = summary,
            rawContext = visibleTexts.joinToString(" | ").take(MaxOperationRawContextLength),
            createdAtMillis = capturedAtMillis,
        )
    }

    private fun findInsungDetailLocationAddress(
        visibleTexts: List<String>,
        summary: String,
        parsedDraft: ParsedOrderDraft,
        role: RouteAddressRole,
    ): String? {
        val candidates = mutableListOf<String>()
        candidates += parsedDraft.routeAddressCandidates(role)
        parsedDraft.bestDetailedAddressForClipboard()?.let(candidates::add)
        candidates += visibleTexts.extractInsungLocationFieldAddressCandidates()
        findNavigationDestinationAddress(visibleTexts, summary)?.let(candidates::add)
        candidates += summary.extractNavigationAddressCandidates()

        return candidates
            .flatMap { candidate ->
                listOf(candidate) + candidate.extractNavigationAddressCandidates()
            }
            .map { it.cleanNavigationAddressCandidate() }
            .filter { it.length in 8..120 }
            .filter(::isOperationalAddress)
            .filter { it.isOperationalDestinationAddress() }
            .distinctByIndexed { it.normalizeRouteAddressKey() }
            .maxWithOrNull(
                compareBy<IndexedValue<String>> { it.value.navigationAddressScore() }
                    .thenBy { it.value.normalizeRouteAddressKey().length }
                    .thenBy { it.index },
            )?.value
    }

    private fun List<String>.extractInsungLocationFieldAddressCandidates(): List<String> {
        val result = mutableListOf<String>()
        forEachIndexed { index, text ->
            val normalized = text.cleanNavigationTextToken()
            if (!normalized.contains("위치")) return@forEachIndexed
            normalized
                .substringAfter("위치", "")
                .trim(':', '：', ' ', '\t')
                .takeIf { it.length >= 4 }
                ?.let(result::add)
            (1..6).forEach { windowSize ->
                val combined = drop(index + 1)
                    .take(windowSize)
                    .joinToString(" ")
                    .cleanNavigationTextToken()
                if (combined.length >= 4) result += combined
            }
        }
        return result.distinctBy { it.normalizeRouteAddressKey() }
    }

    private fun maybeHandleNavigationDestinationDetected(
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
    ) {
        val syncContext = pendingNavigationAddressSyncContext
            ?.takeIf { capturedAtMillis - it.capturedAtMillis in 0..NavigationAddressSyncContextKeepMillis }
            ?: return
        val visibleTexts = root.collectVisibleTexts(maxTokens = MaxNavigationTextTokens)
        if (visibleTexts.isEmpty()) return
        val summary = visibleTexts
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(MaxNavigationSummaryLength)
        val address = findNavigationDestinationAddress(visibleTexts, summary)
        if (address == null) {
            logNavigationDestinationAddressMiss(
                root = root,
                packageName = packageName,
                visibleTexts = visibleTexts,
                summary = summary,
                capturedAtMillis = capturedAtMillis,
            )
            return
        }
        if (!address.isOperationalDestinationAddress()) {
            logNavigationDestinationAddressMiss(
                root = root,
                packageName = packageName,
                visibleTexts = visibleTexts,
                summary = summary,
                capturedAtMillis = capturedAtMillis,
                failureReason = "주소 후보에 네비 안내 UI 문구가 섞여 저장하지 않았습니다: ${address.shortDiagnosticText()}",
            )
            return
        }
        val currentSlots = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync()
        val addressSlotIndex = resolveSequentialManualRouteAddressSlotIndex(
            address = address,
            routeSlots = currentSlots,
        ) ?: syncContext.addressSlotIndex.coerceIn(0, ManualRouteAddressSlotCount - 1)
        val slotLabel = addressSlotIndex.toManualRouteAddressSlotLabel()
        val appLabel = navigationAddressAppLabel(packageName)
        val sourceLabel = navigationAddressSourceLabel(packageName)
        val signature = "$packageName|$addressSlotIndex|${address.normalizeRouteAddressKey()}".normalizeLogSignature()
        if (
            signature == lastNavigationDestinationSignature &&
            capturedAtMillis - lastNavigationDestinationAtMillis < NavigationDestinationSyncCooldownMillis
        ) {
            return
        }
        lastNavigationDestinationSignature = signature
        lastNavigationDestinationAtMillis = capturedAtMillis
        val updatedSlots = currentSlots.toMutableList()
        updatedSlots[addressSlotIndex] = address
        activeSettings = activeSettings.copy(tmapManualRouteAddressesText = updatedSlots.joinToString("\n"))
        pendingNavigationAddressSyncContext = null

        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = "NAVIGATION_DESTINATION_ADDRESS_SYNCED",
            detail = "slot=$slotLabel | address=${address.shortDiagnosticText()} | screen=${summary.shortDiagnosticText()}",
        )
        logOperation(
            eventType = "navigation_destination_address_synced",
            status = "navigation-destination-address-synced",
            orderSignature = address.normalizeRouteAddressKey().take(MaxAutoEntrySignatureLength),
            source = sourceLabel,
            destinationSummaryOverride = address.shortDiagnosticText(),
            confirmed = false,
            reason = buildList {
                add("$appLabel 목적지 주소를 $slotLabel 칸에 자동 저장했습니다.")
                add("인성화면=${syncContext.screenSummary.shortDiagnosticText()}")
            }.joinToString(" · "),
            screenSummary = summary,
            rawContext = visibleTexts.joinToString(" | ").take(MaxOperationRawContextLength),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            settingsRepository.saveDetectedNavigationDestinationAddress(
                value = address,
                slotIndex = addressSlotIndex,
                updateActiveDriveDestination = false,
            )
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = "지도앱 목적지 상세주소 자동 저장",
                    originSummary = appLabel,
                    destinationSummary = address,
                    price = 0,
                    status = "navigation-destination-address-synced",
                    failureReason = "$slotLabel 저장",
                ),
            )
        }
    }

    private fun AccessibilityEvent.isLikelyNavigationGuideClick(
        screenSummary: String,
        parsedDraft: ParsedOrderDraft,
    ): Boolean {
        val eventText = (text.orEmpty().map(CharSequence::toString) + listOfNotNull(contentDescription?.toString()))
            .joinToString(" ")
        if (eventText.contains("길안내")) return true
        return parsedDraft.isManualAddressDetailScreen() && screenSummary.contains("길안내")
    }

    private fun ParsedOrderDraft.navigationDetailAddressRole(summary: String): RouteAddressRole? {
        val normalized = "${screenMode.orEmpty()} $summary ${(effectiveOrigin() ?: "").trim()} ${(effectiveDestination() ?: "").trim()}"
        return when {
            Regex("""(출발지|상차지|출발)\s*상세|출발\b|상차\b""").containsMatchIn(normalized) &&
                !Regex("""(도착지|하차지|도착)\s*상세""").containsMatchIn(normalized) -> RouteAddressRole.Pickup
            Regex("""(도착지|하차지|도착)\s*상세|도착\b|하차\b""").containsMatchIn(normalized) -> RouteAddressRole.Dropoff
            !origin.isNullOrBlank() && destination.isNullOrBlank() -> RouteAddressRole.Pickup
            origin.isNullOrBlank() && !destination.isNullOrBlank() -> RouteAddressRole.Dropoff
            else -> null
        }
    }

    private fun RouteAddressRole.toKoreanRouteAddressRole(): String =
        when (this) {
            RouteAddressRole.Pickup -> "출발지"
            RouteAddressRole.Dropoff -> "도착지"
        }

    private fun String.isLikelyInsungManualAddressDetailScreen(): Boolean {
        if (!contains("위치")) return false
        return InsungManualAddressDetailScreenRegex.containsMatchIn(this)
    }

    private fun resolveInsungDetailRouteOrderSlotIndex(
        parsedDraft: ParsedOrderDraft,
        role: RouteAddressRole,
        capturedAtMillis: Long,
    ): Int {
        val routeSlots = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync()
        val referenceOrder = trackingReferenceOrder
        if (
            role == RouteAddressRole.Dropoff &&
            referenceOrder != null &&
            !trackingReferenceDropoffCompleted &&
            trackingAdditionalOrder == null
        ) {
            val signature = buildOrderSignature(parsedDraft)
            if (signature.isNotBlank()) {
                navigationOrderSlotBySignature[signature] = 0
            }
            navigationOrderSlotBySignature[referenceOrder.signature] = 0
            return 0
        }
        val hasPrimaryPickup = routeSlots.getOrNull(0).orEmpty().isNotBlank()
        val hasPrimaryDropoff = routeSlots.getOrNull(1).orEmpty().isNotBlank()
        if (role == RouteAddressRole.Dropoff && hasPrimaryPickup && !hasPrimaryDropoff) {
            val signature = buildOrderSignature(parsedDraft)
            if (signature.isNotBlank()) {
                navigationOrderSlotBySignature[signature] = 0
            }
            return 0
        }
        val firstOrderIsEmpty = routeSlots.getOrNull(0).orEmpty().isBlank() &&
            routeSlots.getOrNull(1).orEmpty().isBlank()
        if (role == RouteAddressRole.Pickup && firstOrderIsEmpty) {
            val signature = buildOrderSignature(parsedDraft)
            if (signature.isNotBlank()) {
                navigationOrderSlotBySignature[signature] = 0
            }
            return 0
        }
        return resolveNavigationRouteOrderSlotIndex(parsedDraft, capturedAtMillis)
    }

    private fun resolveNavigationRouteOrderSlotIndex(
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ): Int {
        val signature = buildOrderSignature(parsedDraft)
        navigationOrderSlotBySignature[signature]?.let { return it.coerceIn(0, RouteOrderSlotCount - 1) }

        val referenceOrder = trackingReferenceOrder
        if (referenceOrder != null && isSameTrackedOrder(referenceOrder, parsedDraft, capturedAtMillis)) {
            navigationOrderSlotBySignature[referenceOrder.signature] = 0
            return 0
        }

        val trackedOrder = trackedAutoConfirmedOrder
        if (trackedOrder != null && trackedOrder.isSecondary && isSameTrackedOrder(trackedOrder, parsedDraft, capturedAtMillis)) {
            val assigned = navigationOrderSlotBySignature[trackedOrder.signature]
                ?: nextAdditionalNavigationOrderSlotIndex().also { navigationOrderSlotBySignature[trackedOrder.signature] = it }
            return assigned
        }

        val routeSlots = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync()
        val firstOrderIsEmpty = routeSlots.getOrNull(0).isNullOrBlank() && routeSlots.getOrNull(1).isNullOrBlank()
        val fallbackSlot = if (trackingReferenceOrder == null && firstOrderIsEmpty) {
            0
        } else {
            nextAdditionalNavigationOrderSlotIndex()
        }
        if (signature.isNotBlank()) {
            navigationOrderSlotBySignature[signature] = fallbackSlot
        }
        return fallbackSlot
    }

    private fun nextAdditionalNavigationOrderSlotIndex(): Int {
        return 1
    }

    private fun buildRouteAddressSlotDiagnostic(
        routeSlots: List<String> = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync(),
        activeDriveDestination: String = activeSettings.activeDriveDestinationText.trim(),
    ): String {
        val slotText = routeSlots
            .mapIndexed { index, value ->
                "${index.toManualRouteAddressSlotLabel()}=${value.ifBlank { "없음" }.shortDiagnosticText()}"
            }
            .joinToString(" | ")
        return "activeDriveDestination=${activeDriveDestination.ifBlank { "없음" }.shortDiagnosticText()} | $slotText"
    }

    private fun resolveSequentialManualRouteAddressSlotIndex(
        address: String?,
        routeSlots: List<String> = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync(),
    ): Int? {
        val addressKey = address.orEmpty().normalizeRouteAddressKey()
        if (addressKey.isNotBlank()) {
            val existingIndex = routeSlots.indexOfFirst { it.normalizeRouteAddressKey() == addressKey }
            if (existingIndex >= 0) return existingIndex
        }
        return routeSlots.indexOfFirst { it.isBlank() }.takeIf { it >= 0 }
    }

    private fun logNavigationDestinationAddressMiss(
        root: AccessibilityNodeInfo,
        packageName: String,
        visibleTexts: List<String>,
        summary: String,
        capturedAtMillis: Long,
        failureReason: String = "다음지도/카카오내비 화면에서 상세주소 후보를 찾지 못했습니다.",
    ) {
        val signature = "$packageName|${summary.normalizeLogSignature()}"
        if (
            signature == lastNavigationDestinationMissSignature &&
            capturedAtMillis - lastNavigationDestinationMissAtMillis < NavigationDestinationMissLogCooldownMillis
        ) {
            return
        }
        lastNavigationDestinationMissSignature = signature
        lastNavigationDestinationMissAtMillis = capturedAtMillis

        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = "NAVIGATION_DESTINATION_ADDRESS_NOT_FOUND",
            detail = summary.shortDiagnosticText(),
        )
        logOperation(
            eventType = "navigation_destination_address_not_found",
            status = "navigation-destination-address-not-found",
            source = navigationAddressSourceLabel(packageName),
            confirmed = false,
            reason = failureReason,
            screenSummary = summary,
            rawContext = visibleTexts.joinToString(" | ").take(MaxOperationRawContextLength),
            createdAtMillis = capturedAtMillis,
        )
    }

    private fun findNavigationDestinationAddress(
        visibleTexts: List<String>,
        summary: String,
    ): String? {
        val candidates = mutableListOf<String>()
        val meaningfulTexts = visibleTexts
            .map { it.cleanNavigationTextToken() }
            .filter { it.length >= 2 }

        meaningfulTexts.forEach { text ->
            candidates += text.extractNavigationAddressCandidates()
        }
        (2..4).forEach { windowSize ->
            meaningfulTexts
                .windowed(windowSize)
                .map { it.joinToString(" ") }
                .forEach { text -> candidates += text.extractNavigationAddressCandidates() }
        }
        candidates += summary.extractNavigationAddressCandidates()

        return candidates
            .map { it.cleanNavigationAddressCandidate() }
            .filter { it.length in 8..140 }
            .filter(::isOperationalAddress)
            .distinctByIndexed { it.normalizeRouteAddressKey() }
            .maxWithOrNull(
                compareBy<IndexedValue<String>> { it.value.navigationAddressScore() }
                    .thenBy { it.value.normalizeRouteAddressKey().length }
                    .thenBy { it.index },
            )?.value
    }

    private fun String.extractNavigationAddressCandidates(): List<String> {
        val normalized = replace("\n", " ")
            .replace("\r", " ")
            .replace("|", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (normalized.isBlank()) return emptyList()

        val candidates = mutableListOf<String>()
        NavigationAddressRegexes.forEach { regex ->
            regex.findAll(normalized)
                .map { it.value }
                .forEach(candidates::add)
        }
        return candidates
            .map { it.cleanNavigationAddressCandidate() }
            .filter { it.length in 8..140 }
            .distinctBy { it.normalizeRouteAddressKey() }
    }

    private fun String.cleanNavigationTextToken(): String =
        replace(Regex("""\s+"""), " ")
            .trim()
            .trim('|', ',', '·')

    private fun String.cleanNavigationAddressCandidate(): String {
        var candidate = cleanRouteAddressCandidate()
            .replace(NavigationLeadingTownBeforeProvinceRegex, "")
            .replace(NavigationNoisePrefixRegex, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('|', ',', '·', '-', ':', '：')
        NavigationNextAddressRegex.find(candidate)
            ?.groups
            ?.get(1)
            ?.range
            ?.last
            ?.plus(1)
            ?.takeIf { it >= 8 }
            ?.let { keepUntil ->
                candidate = candidate.take(keepUntil).trim()
            }
        NavigationAddressStopRegex.find(candidate)
            ?.range
            ?.first
            ?.takeIf { it >= 8 }
            ?.let { stopIndex ->
                candidate = candidate.take(stopIndex).trim()
            }
        return candidate
    }

    private inline fun <T, K> Iterable<T>.distinctByIndexed(selector: (T) -> K): List<IndexedValue<T>> {
        val observed = HashSet<K>()
        val result = mutableListOf<IndexedValue<T>>()
        forEachIndexed { index, value ->
            if (observed.add(selector(value))) {
                result += IndexedValue(index, value)
            }
        }
        return result
    }

    private fun String.navigationAddressScore(): Int {
        var score = 0
        if (RouteRoadAddressRegex.containsMatchIn(this)) score += 5
        if (Regex("""\d+(?:-\d+)?""").containsMatchIn(this)) score += 3
        if (Regex("""(동|읍|면|리|가)\s+\d""").containsMatchIn(this)) score += 2
        if (contains("(") && contains(")")) score += 1
        return score
    }

    private fun maybeHandleTmapArrivalDetected(
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
    ) {
        val summary = root.collectVisibleText()
        if (!TmapArrivalStrongRegex.containsMatchIn(summary)) return
        val signature = summary.normalizeLogSignature()
        if (
            signature == lastTmapArrivalSignature &&
            capturedAtMillis - lastTmapArrivalAtMillis < TmapArrivalLogCooldownMillis
        ) {
            return
        }
        lastTmapArrivalSignature = signature
        lastTmapArrivalAtMillis = capturedAtMillis

        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = "TMAP_ARRIVAL_DETECTED",
            detail = summary.shortDiagnosticText(),
        )
        logOperation(
            eventType = "tmap_arrival_detected",
            status = "tmap-arrival-detected",
            source = "tmap",
            screenSummary = summary,
            rawContext = summary,
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = "TMAP 실제 도착 감지",
                    originSummary = "TMAP",
                    destinationSummary = "상차지 실제 도착 후보",
                    price = 0,
                    status = "tmap-arrival-detected",
                    failureReason = "TMAP 도착 문구 감지 · ${summary.shortDiagnosticText()}",
                ),
            )
        }
    }

    private fun maybeHandlePickupCompletionPrompt(
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
        parsedDraft: ParsedOrderDraft?,
    ): Boolean {
        if (!isSupportedInsungPackage(packageName)) return false
        val summary = root.collectVisibleText()
        if (!PickupCompletionPromptRegex.containsMatchIn(summary)) return false
        val orderSignature = parsedDraft?.let(::buildOrderSignature).orEmpty()
        val signature = (orderSignature.ifBlank { summary }).normalizeLogSignature()
        if (parsedDraft != null) {
            pendingPickupCompletionPrompt = PendingPickupCompletionPrompt(
                signature = signature,
                parsedDraft = parsedDraft,
                capturedAtMillis = capturedAtMillis,
                screenSummary = summary,
            )
        }
        if (
            signature == lastPickupPromptSignature &&
            capturedAtMillis - lastPickupPromptAtMillis < PickupPromptLogCooldownMillis
        ) {
            return true
        }
        lastPickupPromptSignature = signature
        lastPickupPromptAtMillis = capturedAtMillis

        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = "PICKUP_COMPLETION_PROMPT",
            detail = buildPickupDiagnosticDetail(parsedDraft, summary),
        )
        logOperation(
            eventType = "pickup_completion_prompt",
            status = "pickup-complete-prompt-detected",
            parsedDraft = parsedDraft,
            source = "insung",
            screenSummary = summary,
            rawContext = buildPickupDiagnosticDetail(parsedDraft, summary),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft?.effectiveRouteText().orEmpty()
                        .ifBlank { "픽업완료 확인창 감지" },
                    originSummary = parsedDraft?.effectiveOrigin() ?: "상차지 미확인",
                    destinationSummary = parsedDraft?.effectiveDestination() ?: "하차지 미확인",
                    price = parsedDraft?.price ?: 0,
                    status = "pickup-complete-prompt-detected",
                    failureReason = "인성 픽업완료 확인창 감지 · ${summary.shortDiagnosticText()}",
                ),
            )
        }
        return true
    }

    private fun maybeHandlePickupCompletionClick(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
    ) {
        if (!isSupportedInsungPackage(packageName)) return
        val parsedDraft = KoreanOrderDraftParser.parseInsungQuick(
            root = root,
            packageName = packageName,
        )
        val recentPickupPrompt = pendingPickupCompletionPrompt
            ?.takeIf { capturedAtMillis - it.capturedAtMillis in 0..PickupCompletionPromptKeepMillis }
        if (event.isPickupButtonClick()) {
            logPickupTrackingEvent(
                root = root,
                packageName = packageName,
                parsedDraft = parsedDraft,
                capturedAtMillis = capturedAtMillis,
                status = "pickup-button-clicked",
                title = "픽업 버튼 클릭 감지",
                detail = "인성 픽업 버튼 클릭 감지",
            )
        }
        val promptBackedYesClick = recentPickupPrompt != null && event.isAffirmativeConfirmationClick()
        if (!event.isPickupCompletionYesClick(root) && !promptBackedYesClick) return
        val pickupDraft = recentPickupPrompt?.parsedDraft ?: parsedDraft
        pendingPickupCompletionPrompt = null
        val orderSignature = buildOrderSignature(pickupDraft)
        val signature = orderSignature.normalizeLogSignature()
        if (
            signature == lastPickupConfirmedSignature &&
            capturedAtMillis - lastPickupConfirmedAtMillis < PickupConfirmedLogCooldownMillis
        ) {
            return
        }
        lastPickupConfirmedSignature = signature
        lastPickupConfirmedAtMillis = capturedAtMillis
        logPickupTrackingEvent(
            root = root,
            packageName = packageName,
            parsedDraft = pickupDraft,
            capturedAtMillis = capturedAtMillis,
            status = "pickup-completed-confirmed",
            title = "픽업완료 확인 클릭 감지",
            detail = buildList {
                add("인성 픽업완료 확인 클릭 감지")
                if (promptBackedYesClick) {
                    add("직전 픽업완료 확인창 기준으로 보정 감지")
                }
            }.joinToString(" · "),
        )
        maybeMarkTrackingReferencePickupCompleted(
            parsedDraft = pickupDraft,
            capturedAtMillis = capturedAtMillis,
        )
    }

    private fun maybeMarkTrackingReferencePickupCompleted(
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ) {
        val referenceOrder = trackingReferenceOrder ?: return
        if (trackingReferencePickupCompleted) return
        val routeSlots = activeSettings.tmapManualRouteAddressesText.toManualRouteAddressSlotsForSync()
        val activeDriveDestination = activeSettings.activeDriveDestinationText.trim()
        val promptDestination = parsedDraft.effectiveDestination()?.trim().orEmpty()
        val matchedByOrder = isSameTrackedOrder(referenceOrder, parsedDraft, capturedAtMillis)
        val matchedBySingleReferenceContext = !matchedByOrder &&
            promptDestination.isBlank() &&
            trackingAdditionalOrder == null &&
            trackingAdditionalConfirmedCount == 0
        if (!matchedByOrder && !matchedBySingleReferenceContext) {
            logOperation(
                eventType = "tracking_reference_pickup_match_missed",
                status = "tracking-reference-pickup-match-missed",
                parsedDraft = parsedDraft,
                orderSignature = referenceOrder.signature,
                mode = AutoEntryMode.Secondary,
                source = "insung",
                reason = buildList {
                    add("픽업완료 예 클릭을 감지했지만 기준오더와 일치하지 않아 추적 추가오더를 열지 않았습니다.")
                    add("기준=${referenceOrder.destination.shortDiagnosticText()}")
                    add("현재=${promptDestination.ifBlank { "미확인" }.shortDiagnosticText()}")
                    add("저장주소=${routeSlots.firstOrNull(String::isNotBlank).orEmpty().ifBlank { "없음" }.shortDiagnosticText()}")
                    add("추적기준=${activeDriveDestination.ifBlank { "없음" }.shortDiagnosticText()}")
                }.joinToString(" · "),
                rawContext = buildRouteAddressSlotDiagnostic(routeSlots, activeDriveDestination),
                createdAtMillis = capturedAtMillis,
            )
            return
        }

        trackingReferencePickupCompleted = true
        trackingReferencePickupCompletedAtMillis = capturedAtMillis
        logOperation(
            eventType = "tracking_reference_pickup_completed",
            status = if (matchedBySingleReferenceContext) {
                "tracking-reference-pickup-completed-by-context"
            } else {
                "tracking-reference-pickup-completed"
            },
            parsedDraft = parsedDraft,
            orderSignature = referenceOrder.signature,
            mode = AutoEntryMode.Secondary,
            source = "insung",
            reason = buildList {
                add(
                    if (matchedBySingleReferenceContext) {
                        "픽업완료 팝업에서 도착지가 미노출됐지만 진행 중인 기준오더가 1건이라 기준오더 상차완료로 인정했습니다."
                    } else {
                        "기준오더 상차완료 확인. 이제 추가오더 자동확정 1건만 허용합니다."
                    },
                )
                add("추적고정기준=경로상우회<=${TrackingPickupRouteDetourLimitKm.formatDistanceKm()}km, 상차→하차<=${TrackingPickupToDropoffLimitKm.formatDistanceKm()}km")
                activeDriveDestination
                    .takeIf(String::isNotBlank)
                    ?.let { add("기준하차상세=${it.shortDiagnosticText()}") }
                add("저장주소=${routeSlots.firstOrNull(String::isNotBlank).orEmpty().ifBlank { "없음" }.shortDiagnosticText()}")
            }.joinToString(" · "),
            rawContext = buildRouteAddressSlotDiagnostic(routeSlots, activeDriveDestination),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = referenceOrder.orderTitle,
                    originSummary = referenceOrder.origin,
                    destinationSummary = referenceOrder.destination,
                    price = referenceOrder.price,
                    status = "tracking-reference-pickup-completed",
                    failureReason = buildList {
                        add("기준오더 상차완료 확인")
                        add("추가오더 자동확정 1건 제한 활성화")
                        add("경로상우회<=${TrackingPickupRouteDetourLimitKm.formatDistanceKm()}km")
                        add("상차→하차<=${TrackingPickupToDropoffLimitKm.formatDistanceKm()}km")
                    }.joinToString(" · "),
                ),
            )
        }
    }

    private fun logPickupTrackingEvent(
        root: AccessibilityNodeInfo,
        packageName: String,
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
        status: String,
        title: String,
        detail: String,
    ) {
        val summary = root.collectVisibleText()
        val pickupDelayText = elapsedFromLastTmapArrival(capturedAtMillis)
        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = status.uppercase(Locale.US).replace('-', '_'),
            detail = buildPickupDiagnosticDetail(parsedDraft, summary, pickupDelayText),
        )
        logOperation(
            eventType = status,
            status = status,
            parsedDraft = parsedDraft,
            source = "insung",
            reason = buildList {
                add(detail)
                pickupDelayText?.let { add(it) }
            }.joinToString(" · "),
            screenSummary = summary,
            rawContext = buildPickupDiagnosticDetail(parsedDraft, summary, pickupDelayText),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft.effectiveRouteText() ?: title,
                    originSummary = parsedDraft.effectiveOrigin() ?: "상차지 미확인",
                    destinationSummary = parsedDraft.effectiveDestination() ?: "하차지 미확인",
                    price = parsedDraft.price ?: 0,
                    status = status,
                    failureReason = buildList {
                        add(detail)
                        pickupDelayText?.let { add(it) }
                        add(summary.shortDiagnosticText())
                    }.joinToString(" · "),
                ),
            )
        }
    }

    private fun maybeHandleDropoffCompletionPrompt(
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
        parsedDraft: ParsedOrderDraft?,
    ): Boolean {
        if (!isSupportedInsungPackage(packageName)) return false
        val summary = root.collectVisibleText()
        if (!DropoffCompletionPromptRegex.containsMatchIn(summary)) return false
        val orderSignature = parsedDraft?.let(::buildOrderSignature).orEmpty()
        val signature = (orderSignature.ifBlank { summary }).normalizeLogSignature()
        if (
            signature == lastDropoffPromptSignature &&
            capturedAtMillis - lastDropoffPromptAtMillis < DropoffPromptLogCooldownMillis
        ) {
            return true
        }
        lastDropoffPromptSignature = signature
        lastDropoffPromptAtMillis = capturedAtMillis

        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = "DROPOFF_COMPLETION_PROMPT",
            detail = buildDropoffDiagnosticDetail(parsedDraft, summary),
        )
        logOperation(
            eventType = "dropoff_completion_prompt",
            status = "dropoff-complete-prompt-detected",
            parsedDraft = parsedDraft,
            source = "insung",
            screenSummary = summary,
            rawContext = buildDropoffDiagnosticDetail(parsedDraft, summary),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft?.effectiveRouteText().orEmpty()
                        .ifBlank { "하차완료 확인창 감지" },
                    originSummary = parsedDraft?.effectiveOrigin() ?: "상차지 미확인",
                    destinationSummary = parsedDraft?.effectiveDestination() ?: "하차지 미확인",
                    price = parsedDraft?.price ?: 0,
                    status = "dropoff-complete-prompt-detected",
                    failureReason = "인성 하차완료 확인창 감지 · ${summary.shortDiagnosticText()}",
                ),
            )
        }
        return true
    }

    private fun maybeHandleDropoffCompletionClick(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
    ) {
        if (!isSupportedInsungPackage(packageName)) return
        val parsedDraft = KoreanOrderDraftParser.parseInsungQuick(
            root = root,
            packageName = packageName,
        )
        if (event.isDropoffSignatureButtonClick(root)) {
            val orderSignature = buildOrderSignature(parsedDraft)
            val signature = ("signature|$orderSignature").normalizeLogSignature()
            if (
                signature != lastDropoffSignatureClickSignature ||
                capturedAtMillis - lastDropoffSignatureClickAtMillis >= DropoffActionLogCooldownMillis
            ) {
                lastDropoffSignatureClickSignature = signature
                lastDropoffSignatureClickAtMillis = capturedAtMillis
                logDropoffTrackingEvent(
                    root = root,
                    packageName = packageName,
                    parsedDraft = parsedDraft,
                    capturedAtMillis = capturedAtMillis,
                    status = "dropoff-signature-button-clicked",
                    title = "도착지 서명 버튼 클릭 감지",
                    detail = "인성 도착지 서명 버튼 클릭 감지",
                )
            }
        }
        if (event.isDropoffSendActionClick(root)) {
            val orderSignature = buildOrderSignature(parsedDraft)
            val signature = ("send|$orderSignature|${root.collectVisibleText()}").normalizeLogSignature()
            if (
                signature != lastDropoffSendActionSignature ||
                capturedAtMillis - lastDropoffSendActionAtMillis >= DropoffActionLogCooldownMillis
            ) {
                lastDropoffSendActionSignature = signature
                lastDropoffSendActionAtMillis = capturedAtMillis
                logDropoffTrackingEvent(
                    root = root,
                    packageName = packageName,
                    parsedDraft = parsedDraft,
                    capturedAtMillis = capturedAtMillis,
                    status = "dropoff-send-action-clicked",
                    title = "하차 전송/저장 버튼 클릭 감지",
                    detail = "인성 하차 사진/서명 전송 또는 저장 버튼 클릭 감지",
                )
            }
        }
        if (!event.isDropoffCompletionYesClick(root)) return
        val orderSignature = buildOrderSignature(parsedDraft)
        val signature = orderSignature.normalizeLogSignature()
        if (
            signature == lastDropoffConfirmedSignature &&
            capturedAtMillis - lastDropoffConfirmedAtMillis < DropoffConfirmedLogCooldownMillis
        ) {
            return
        }
        lastDropoffConfirmedSignature = signature
        lastDropoffConfirmedAtMillis = capturedAtMillis
        logDropoffTrackingEvent(
            root = root,
            packageName = packageName,
            parsedDraft = parsedDraft,
            capturedAtMillis = capturedAtMillis,
            status = "dropoff-completed-confirmed",
            title = "하차완료 예 클릭 감지",
            detail = "인성 하차완료 예 클릭 감지",
        )
        handleTrackedOrderDropoffCompleted(parsedDraft, capturedAtMillis)
    }

    private fun handleTrackedOrderDropoffCompleted(
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ) {
        val additionalOrder = trackingAdditionalOrder
            ?: trackedAutoConfirmedOrder?.takeIf { it.isSecondary }
        if (
            additionalOrder != null &&
            isSameTrackedOrder(additionalOrder, parsedDraft, capturedAtMillis)
        ) {
            trackingAdditionalDropoffCompleted = true
            if (trackedAutoConfirmedOrder?.signature == additionalOrder.signature) {
                trackedAutoConfirmedOrder = trackingReferenceOrder
            }
            logTrackedOrderDropoffState(
                trackedOrder = additionalOrder,
                capturedAtMillis = capturedAtMillis,
                status = "tracked-additional-dropoff-completed",
                detail = "추가오더 B 하차완료 감지",
            )
            if (trackingReferenceDropoffCompleted) {
                endTrackingSession(
                    capturedAtMillis = capturedAtMillis,
                    reason = "기준오더 A와 추가오더 B 하차완료 감지로 전체 추적을 종료했습니다.",
                )
            }
            return
        }

        val referenceOrder = trackingReferenceOrder ?: return
        if (!isSameTrackedOrder(referenceOrder, parsedDraft, capturedAtMillis)) return

        trackingReferenceDropoffCompleted = true
        logTrackedOrderDropoffState(
            trackedOrder = referenceOrder,
            capturedAtMillis = capturedAtMillis,
            status = "tracking-reference-dropoff-completed",
            detail = "기준오더 A 하차완료 감지",
        )
        if (trackingAdditionalOrder != null && !trackingAdditionalDropoffCompleted) {
            logOperation(
                eventType = "order_tracking_waiting_additional_dropoff",
                status = "order-tracking-waiting-additional-dropoff",
                orderSignature = referenceOrder.signature,
                mode = AutoEntryMode.Secondary,
                source = "insung",
                reason = "기준오더 A는 완료됐지만 추가오더 B가 아직 완료되지 않아 추적 세션을 유지합니다.",
                createdAtMillis = capturedAtMillis,
            )
            return
        }

        endTrackingSession(
            capturedAtMillis = capturedAtMillis,
            reason = "기준오더 A 하차완료 감지로 추적을 종료했습니다.",
        )
    }

    private fun logTrackedOrderDropoffState(
        trackedOrder: TrackedAutoConfirmedOrder,
        capturedAtMillis: Long,
        status: String,
        detail: String,
    ) {
        logOperation(
            eventType = "tracked_order_dropoff_completed",
            status = status,
            orderSignature = trackedOrder.signature,
            mode = if (trackedOrder.isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = "insung",
            confirmed = true,
            reason = detail,
            screenSummary = trackedOrder.orderTitle,
            rawContext = listOf(
                "origin=${trackedOrder.origin}",
                "destination=${trackedOrder.destination}",
                "price=${trackedOrder.price}",
                "isSecondary=${trackedOrder.isSecondary}",
                "referenceDropoffCompleted=$trackingReferenceDropoffCompleted",
                "additionalDropoffCompleted=$trackingAdditionalDropoffCompleted",
            ).joinToString(" | "),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = trackedOrder.orderTitle,
                    originSummary = trackedOrder.origin,
                    destinationSummary = trackedOrder.destination,
                    price = trackedOrder.price,
                    status = status,
                    failureReason = detail,
                ),
            )
        }
    }

    private fun endTrackingSession(
        capturedAtMillis: Long,
        reason: String,
    ) {
        val referenceOrder = trackingReferenceOrder
        val additionalOrder = trackingAdditionalOrder
        val referenceDestination = activeSettings.activeDriveDestinationText.trim()
        val routeSlotsBeforeClear = activeSettings.tmapManualRouteAddressesText
            .toManualRouteAddressSlotsForSync()
        val slotsToClear = buildSet {
            add(0)
            if (additionalOrder != null) add(1)
        }
        activeSettings = activeSettings.copy(
            activeDriveDestinationText = "",
            tmapManualRouteAddressesText = slotsToClear.fold(activeSettings.tmapManualRouteAddressesText) { text, slotIndex ->
                text.clearManualRouteAddressOrderSlot(slotIndex)
            },
        )
        trackingReferenceOrder = null
        trackingReferencePickupCompleted = false
        trackingReferencePickupCompletedAtMillis = 0L
        trackingReferenceDropoffCompleted = false
        trackingAdditionalOrder = null
        trackingAdditionalDropoffCompleted = false
        trackingAdditionalConfirmedCount = 0
        trackedAutoConfirmedOrder = null
        navigationOrderSlotBySignature.clear()
        logOperation(
            eventType = "order_tracking_ended",
            status = "order-tracking-ended",
            orderSignature = referenceOrder?.signature,
            mode = AutoEntryMode.Secondary,
            source = "insung",
            reason = buildList {
                add(reason)
                add("추적 기준 상세주소와 TMAP A/B 상세주소를 초기화했습니다.")
                if (referenceDestination.isNotBlank()) {
                    add("기준상세주소=${referenceDestination.shortDiagnosticText()}")
                }
                additionalOrder?.let {
                    add("추가오더=${it.destination.shortDiagnosticText()}")
                }
                add("삭제전주소칸=${routeSlotsBeforeClear.filter(String::isNotBlank).joinToString(" / ").shortDiagnosticText()}")
            }.joinToString(" · "),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            settingsRepository.setActiveDriveDestinationText("")
            slotsToClear.sorted().forEach { slotIndex ->
                settingsRepository.clearTmapManualRouteOrderSlot(
                    orderSlotIndex = slotIndex,
                    clearActiveDriveDestination = slotIndex == 0,
                )
            }
            referenceOrder?.let {
                orderEventRepository.logEvent(
                    OrderEventDraft(
                        orderTitle = it.orderTitle,
                        originSummary = it.origin,
                        destinationSummary = it.destination,
                        price = it.price,
                        status = "order-tracking-ended",
                        failureReason = buildList {
                            add(reason)
                            add("추적 기준 상세주소와 TMAP A/B 상세주소를 초기화했습니다.")
                            if (referenceDestination.isNotBlank()) {
                                add("기준상세주소=${referenceDestination.shortDiagnosticText()}")
                            }
                        }.joinToString(" · "),
                    ),
                )
            }
        }
    }

    private fun logDropoffTrackingEvent(
        root: AccessibilityNodeInfo,
        packageName: String,
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
        status: String,
        title: String,
        detail: String,
    ) {
        val summary = root.collectVisibleText()
        val arrivalElapsedText = elapsedFromLastTmapArrival(capturedAtMillis)
        saveDiagnosticCapture(
            root = root,
            packageName = packageName,
            tag = status.uppercase(Locale.US).replace('-', '_'),
            detail = buildDropoffDiagnosticDetail(parsedDraft, summary, arrivalElapsedText),
        )
        logOperation(
            eventType = status,
            status = status,
            parsedDraft = parsedDraft,
            source = "insung",
            reason = buildList {
                add(detail)
                arrivalElapsedText?.let { add(it) }
            }.joinToString(" · "),
            screenSummary = summary,
            rawContext = buildDropoffDiagnosticDetail(parsedDraft, summary, arrivalElapsedText),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft.effectiveRouteText() ?: title,
                    originSummary = parsedDraft.effectiveOrigin() ?: "상차지 미확인",
                    destinationSummary = parsedDraft.effectiveDestination() ?: "하차지 미확인",
                    price = parsedDraft.price ?: 0,
                    status = status,
                    failureReason = buildList {
                        add(detail)
                        arrivalElapsedText?.let { add(it) }
                        add(summary.shortDiagnosticText())
                    }.joinToString(" · "),
                ),
            )
        }
    }

    private fun elapsedFromLastTmapArrival(nowMillis: Long): String? {
        val arrivedAt = lastTmapArrivalAtMillis.takeIf { it > 0L } ?: return null
        val elapsedMillis = nowMillis - arrivedAt
        if (elapsedMillis !in 0..TmapArrivalMatchWindowMillis) return null
        return "TMAP 도착 감지 후 ${elapsedMillis.formatElapsedMinutes()} 경과"
    }

    private fun buildPickupDiagnosticDetail(
        parsedDraft: ParsedOrderDraft?,
        summary: String,
        pickupDelayText: String? = null,
    ): String {
        return buildList {
            add("origin=${(parsedDraft?.effectiveOrigin() ?: "미확인").shortDiagnosticText()}")
            add("destination=${(parsedDraft?.effectiveDestination() ?: "미확인").shortDiagnosticText()}")
            parsedDraft?.price?.let { add("price=$it") }
            pickupDelayText?.let(::add)
            add("screen=${summary.shortDiagnosticText()}")
        }.joinToString(" | ")
    }

    private fun buildDropoffDiagnosticDetail(
        parsedDraft: ParsedOrderDraft?,
        summary: String,
        arrivalElapsedText: String? = null,
    ): String {
        return buildList {
            add("origin=${(parsedDraft?.effectiveOrigin() ?: "미확인").shortDiagnosticText()}")
            add("destination=${(parsedDraft?.effectiveDestination() ?: "미확인").shortDiagnosticText()}")
            parsedDraft?.price?.let { add("price=$it") }
            arrivalElapsedText?.let(::add)
            add("screen=${summary.shortDiagnosticText()}")
        }.joinToString(" | ")
    }

    private fun buildSnapshot(
        root: AccessibilityNodeInfo,
        packageName: String,
        eventType: Int,
    ): AccessibilityCaptureEntity {
        val lines = mutableListOf<String>()
        val visibleTexts = linkedSetOf<String>()
        var nodeCount = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (nodeCount >= MaxNodes || depth > MaxDepth) return
            nodeCount += 1

            val text = node.text?.toString()?.sanitizeForCapture()
            val contentDescription = node.contentDescription?.toString()?.sanitizeForCapture()
            val viewId = node.viewIdResourceName?.sanitizeForCapture()
            val className = node.className?.toString()?.substringAfterLast('.').orEmpty()
            val bounds = Rect().also(node::getBoundsInScreen)

            listOfNotNull(text, contentDescription)
                .filter { it.isNotBlank() }
                .forEach { candidate ->
                    if (visibleTexts.size < MaxPreviewTokens) {
                        visibleTexts += candidate
                    }
                }

            lines += buildString {
                append("  ".repeat(depth))
                append("- ")
                append(className.ifBlank { "Node" })
                if (!text.isNullOrBlank()) {
                    append(" text=\"")
                    append(text)
                    append('"')
                }
                if (!contentDescription.isNullOrBlank() && contentDescription != text) {
                    append(" desc=\"")
                    append(contentDescription)
                    append('"')
                }
                if (!viewId.isNullOrBlank()) {
                    append(" id=\"")
                    append(viewId)
                    append('"')
                }
                append(" clickable=")
                append(node.isClickable)
                append(" selected=")
                append(node.isSelected)
                append(" focused=")
                append(node.isFocused)
                append(" enabled=")
                append(node.isEnabled)
                append(" scrollable=")
                append(node.isScrollable)
                append(" bounds=")
                append("[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                walk(child, depth + 1)
            }
        }

        walk(root, depth = 0)

        val title = visibleTexts.firstOrNull()
        val summary = visibleTexts
            .take(MaxPreviewTokens)
            .joinToString(separator = " | ")
            .ifBlank { "No visible text captured." }
            .take(MaxSummaryLength)
        val rawHierarchy = buildString {
            appendLine("package=$packageName")
            appendLine("event=${eventType.toReadableEventType()}")
            appendLine("nodeCount=$nodeCount")
            appendLine()
            lines.forEach(::appendLine)
        }

        return AccessibilityCaptureEntity(
            packageName = packageName,
            eventType = eventType.toReadableEventType(),
            screenTitle = title,
            summaryText = summary,
            nodeCount = nodeCount,
            rawHierarchy = rawHierarchy,
        )
    }

    private fun maybeAutoConfirmFast(
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
        parsedDraftOverride: ParsedOrderDraft? = null,
    ): Boolean {
        if (!CatchProFeatureGate.autoConfirmAvailable(this)) {
            return false
        }
        if (!isSupportedInsungPackage(packageName)) {
            return false
        }

        val parsedDraft = parsedDraftOverride ?: KoreanOrderDraftParser.parseInsungQuick(
            root = root,
            packageName = packageName,
        )
        if (!parsedDraft.isConfirmableDetailScreen) return false

        val actionSignature = buildOrderSignature(parsedDraft)
        if (
            actionSignature == lastCancelledAutoConfirmSignature &&
            capturedAtMillis - lastCancelledAutoConfirmAtMillis < AutoCancelRetryCooldownMillis
        ) {
            return false
        }

        return evaluateAndConfirmDetailDraft(
            root = root,
            parsedDraft = parsedDraft,
            actionSignature = actionSignature,
            capturedAtMillis = capturedAtMillis,
            forceManualConfirmation = false,
        )
    }

    private fun maybeAutoEnterOrderListPriority(
        root: AccessibilityNodeInfo,
        packageName: String,
        eventType: Int,
        capturedAtMillis: Long,
    ): Boolean {
        if (!isSupportedInsungPackage(packageName)) return false
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return false
        }
        if (!root.hasInsungOrderListRows()) {
            maybeScheduleAutoEntryWarmListRescan(
                root = root,
                packageName = packageName,
                eventType = eventType,
                capturedAtMillis = capturedAtMillis,
            )
            return false
        }
        return maybeAutoEnterOrderList(
            root = root,
            packageName = packageName,
            eventType = eventType,
            capturedAtMillis = capturedAtMillis,
            parsedDraft = null,
        )
    }

    private fun maybeAutoEnterOrderList(
        root: AccessibilityNodeInfo,
        packageName: String,
        eventType: Int,
        capturedAtMillis: Long,
        parsedDraft: ParsedOrderDraft?,
    ): Boolean {
        if (!isSupportedInsungPackage(packageName)) return false
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return false
        }
        trimPendingConfirmAttempts(capturedAtMillis)
        if (capturedAtMillis < autoEntryListPausedUntilMillis) return false
        if (parsedDraft?.isDetailedScreen == true || parsedDraft?.isConfirmableDetailScreen == true) return false
        if (root.hasVisibleOrderDetailTitle()) return false

        val mode = currentAutoEntryMode() ?: return false
        resetAutoEntryCycleIfNeeded(mode, capturedAtMillis)
        if (isAutoEntryConfirmLimitReached(mode)) return false
        if (autoEntryCheckCount(mode) >= maxAutoEntryChecksPerCycle()) return false
        if (capturedAtMillis - lastAutoListEntryAtMillis < AutoEntryGlobalCooldownMillis) return false
        clearPendingAutoEntryIfNavigationDidNotOpenDetail(root, capturedAtMillis)
        if (activePendingAutoEntry(capturedAtMillis) != null) return false

        trimAutoEntryLocks(capturedAtMillis)
        val candidate = findOrderListAutoEntryCandidate(
            root = root,
            nowMillis = capturedAtMillis,
            preferredRegion = nextAutoEntryRegion(mode),
            mode = mode,
        ) ?: return false
        val clickAttempt = candidate.node.clickOrderListRow()
        val clickDiagnostic = buildAutoEntryClickDiagnostic(
            root = root,
            mode = mode,
            candidate = candidate,
            clickAttempt = clickAttempt,
        )
        if (!clickAttempt.accepted) {
            autoEntryListLocks[candidate.signature] = capturedAtMillis + AutoEntryClickFailureRetryMillis
            advanceAutoEntryCandidateCursor(
                mode = mode,
                region = candidate.region,
                selectedIndexInRegion = candidate.candidateIndexInRegion,
                candidateCountInRegion = candidate.candidateCountForRegion(),
            )
            advanceAutoEntryRegion(mode, candidate.region)
            saveDiagnosticCapture(
                root = root,
                packageName = packageName,
                tag = "AUTO_ENTRY_CLICK_FAILED",
                detail = clickDiagnostic,
            )
            logOperation(
                eventType = "auto_entry_click_failed",
                status = "order-list-auto-entry-failed",
                orderSignature = candidate.signature,
                mode = mode,
                source = "order_list",
                region = candidate.region,
                reason = "클릭 가능한 오더 항목을 누르지 못했습니다.",
                clickDiagnostic = clickDiagnostic,
                screenSummary = candidate.summary,
                rawContext = clickDiagnostic,
                createdAtMillis = capturedAtMillis,
            )
            logOrderListAutoEntry(
                status = "order-list-auto-entry-failed",
                candidate = candidate,
                mode = mode,
                reason = "${candidate.region.toKoreanLabel()} 클릭 가능한 오더 항목을 누르지 못했습니다. · ${candidate.listDistanceDiagnostic()} · $clickDiagnostic",
            )
            return true
        }

        logOperation(
            eventType = "auto_entry_click",
            status = "order-list-auto-entry",
            orderSignature = candidate.signature,
            mode = mode,
            source = "order_list",
            region = candidate.region,
            clickDiagnostic = clickDiagnostic,
            screenSummary = candidate.summary,
            rawContext = clickDiagnostic,
            createdAtMillis = capturedAtMillis,
        )

        pendingAutoListEntry = PendingAutoListEntry(
            listSignature = candidate.signature,
            listSummary = candidate.summary,
            listSignatureReliable = candidate.signatureReliable,
            mode = mode,
            region = candidate.region,
            clickedAtMillis = capturedAtMillis,
            clickDiagnostic = clickDiagnostic,
            tapX = clickAttempt.tapX,
            tapY = clickAttempt.tapY,
            candidateIndexInRegion = candidate.candidateIndexInRegion,
            candidateCountInRegion = candidate.candidateCountForRegion(),
        )
        scheduleAutoEntryFallbackTapIfNeeded(
            pending = pendingAutoListEntry,
            clickAttempt = clickAttempt,
            packageName = packageName,
        )
        scheduleAutoEntryNavigationTimeout(
            clickedAtMillis = capturedAtMillis,
            listSignature = candidate.signature,
        )
        incrementAutoEntryCheckCount(mode)
        advanceAutoEntryRegion(mode, candidate.region)
        lastAutoListEntryAtMillis = capturedAtMillis
        lastAutoEntryMode = mode
        autoEntryListLocks[candidate.signature] = capturedAtMillis + AutoEntryGlobalCooldownMillis
        return true
    }

    private fun currentAutoEntryMode(): AutoEntryMode? {
        if (!CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(this)) return null
        if (!activeSettings.primaryOrderListAutoEntryEnabled) return null
        return AutoEntryMode.Primary
    }

    private fun logTrackingAutoEntryGateBlocked(reason: String) {
        val nowMillis = System.currentTimeMillis()
        val referenceOrder = trackingReferenceOrder
        val signature = "${referenceOrder?.signature.orEmpty()}|$reason|${activeSettings.activeDriveDestinationText.normalizeLogSignature()}"
        if (
            signature == lastTrackingAutoEntryGateBlockSignature &&
            nowMillis - lastTrackingAutoEntryGateBlockAtMillis < TrackingAutoEntryGateBlockLogCooldownMillis
        ) {
            return
        }
        lastTrackingAutoEntryGateBlockSignature = signature
        lastTrackingAutoEntryGateBlockAtMillis = nowMillis
        logOperation(
            eventType = "order_tracking_auto_entry_blocked",
            status = "order-tracking-auto-entry-blocked",
            orderSignature = referenceOrder?.signature,
            mode = AutoEntryMode.Secondary,
            source = "order_list",
            reason = buildList {
                add(reason)
                referenceOrder?.let { add("기준=${it.destination.shortDiagnosticText()}") }
                add("상차완료=${if (trackingReferencePickupCompleted) "예" else "아니오"}")
                add("추가확정=$trackingAdditionalConfirmedCount/$TrackingAdditionalAutoConfirmLimit")
            }.joinToString(" · "),
            rawContext = buildRouteAddressSlotDiagnostic(),
            createdAtMillis = nowMillis,
        )
    }

    private fun findOrderListAutoEntryCandidate(
        root: AccessibilityNodeInfo,
        nowMillis: Long,
        preferredRegion: AutoEntryListRegion,
        mode: AutoEntryMode,
    ): OrderListAutoEntryCandidate? {
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val newOrderListRegions = root.visibleNewOrderListRegions(rootBounds)
        if (newOrderListRegions.isEmpty()) return null

        val candidates = root.flattenClickableNodes()
            .asSequence()
            .mapNotNull { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                val region = bounds.autoEntryListRegion(rootBounds)
                val isInsungOrderRow = node.isInsungOrderListRowNode()
                if (
                    !node.isVisibleToUser ||
                    region == null ||
                    region !in newOrderListRegions ||
                    !bounds.isLikelyOrderListRow(rootBounds) ||
                    !isInsungOrderRow ||
                    isAutoEntryRegionLocked(region, nowMillis)
                ) {
                    return@mapNotNull null
                }

                val summary = node.collectVisibleText()
                val textSignature = summary.normalizeAutoEntryListSignature()
                val signature = textSignature.ifBlank {
                    "row:${node.viewIdResourceName.orEmpty()}:${region.name}:${bounds.top}:${bounds.bottom}"
                }
                if (
                    signature.isNotBlank() &&
                    !isAutoEntryListLocked(signature, nowMillis)
                ) {
                    OrderListAutoEntryCandidate(
                        node = node,
                        summary = summary.ifBlank { "${region.toKoreanLabel()} 오더 행" },
                        signature = signature,
                        signatureReliable = textSignature.isNotBlank(),
                        region = region,
                        bounds = Rect(bounds),
                        rootBounds = Rect(rootBounds),
                        nodeViewId = node.viewIdResourceName.orEmpty(),
                        nodeClassName = node.className?.toString().orEmpty(),
                        nodeClickable = node.isClickable,
                    )
                } else {
                    null
                }
            }
            .toList()
        fun selectFromRegion(region: AutoEntryListRegion): OrderListAutoEntryCandidate? {
            val regionCandidates = candidates.filter { it.region == region }
            if (regionCandidates.isEmpty()) return null
            val cursor = autoEntryCandidateCursor(mode, region) % regionCandidates.size
            return regionCandidates.drop(cursor).firstOrNull() ?: regionCandidates.first()
        }

        val selected = listOf(preferredRegion, preferredRegion.opposite())
            .filter { region -> region in newOrderListRegions }
            .distinct()
            .asSequence()
            .mapNotNull(::selectFromRegion)
            .firstOrNull()
            ?: return null
        val sameRegionCandidates = candidates.filter { it.region == selected.region }
        return selected.copy(
            candidateIndexOverall = candidates.indexOfFirst { it.signature == selected.signature && it.region == selected.region && it.bounds == selected.bounds }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: 1,
            candidateIndexInRegion = sameRegionCandidates.indexOfFirst { it.signature == selected.signature && it.bounds == selected.bounds }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: 1,
            candidateCountTop = candidates.count { it.region == AutoEntryListRegion.Top },
            candidateCountBottom = candidates.count { it.region == AutoEntryListRegion.Bottom },
        )
    }

    private fun findPendingAutoEntryFallbackCandidate(
        root: AccessibilityNodeInfo,
        pending: PendingAutoListEntry,
    ): OrderListAutoEntryCandidate? {
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val newOrderListRegions = root.visibleNewOrderListRegions(rootBounds)
        if (pending.region !in newOrderListRegions) return null

        val candidates = root.flattenClickableNodes()
            .asSequence()
            .mapNotNull { node ->
                val bounds = Rect().also(node::getBoundsInScreen)
                val region = bounds.autoEntryListRegion(rootBounds)
                if (
                    !node.isVisibleToUser ||
                    region != pending.region ||
                    !bounds.isLikelyOrderListRow(rootBounds) ||
                    !node.isInsungOrderListRowNode()
                ) {
                    return@mapNotNull null
                }

                val summary = node.collectVisibleText()
                val textSignature = summary.normalizeAutoEntryListSignature()
                val signature = textSignature.ifBlank {
                    "row:${node.viewIdResourceName.orEmpty()}:${region.name}:${bounds.top}:${bounds.bottom}"
                }
                OrderListAutoEntryCandidate(
                    node = node,
                    summary = summary.ifBlank { "${region.toKoreanLabel()} 오더 행" },
                    signature = signature,
                    signatureReliable = textSignature.isNotBlank(),
                    region = region,
                    bounds = Rect(bounds),
                    rootBounds = Rect(rootBounds),
                    nodeViewId = node.viewIdResourceName.orEmpty(),
                    nodeClassName = node.className?.toString().orEmpty(),
                    nodeClickable = node.isClickable,
                )
            }
            .toList()
        if (candidates.isEmpty()) return null

        val topCount = candidates.count { it.region == AutoEntryListRegion.Top }
        val bottomCount = candidates.count { it.region == AutoEntryListRegion.Bottom }
        val indexedCandidates = candidates.mapIndexed { index, candidate ->
            val sameRegionCandidates = candidates.filter { it.region == candidate.region }
            candidate.copy(
                candidateIndexOverall = index + 1,
                candidateIndexInRegion = sameRegionCandidates.indexOfFirst {
                    it.signature == candidate.signature && it.bounds == candidate.bounds
                }.takeIf { it >= 0 }?.plus(1) ?: 1,
                candidateCountTop = topCount,
                candidateCountBottom = bottomCount,
            )
        }

        return indexedCandidates.firstOrNull { candidate ->
            candidate.signature == pending.listSignature &&
                (pending.listSignatureReliable || candidate.bounds.contains(pending.tapX, pending.tapY))
        } ?: if (!pending.listSignatureReliable) {
            indexedCandidates.firstOrNull { candidate ->
                candidate.bounds.contains(pending.tapX, pending.tapY) &&
                    candidate.candidateIndexInRegion == pending.candidateIndexInRegion
            }
        } else {
            null
        }
    }

    private fun AccessibilityNodeInfo.visibleNewOrderListRegions(rootBounds: Rect): Set<AutoEntryListRegion> {
        val regions = linkedSetOf<AutoEntryListRegion>()

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return
            val id = node.viewIdResourceName.orEmpty()
            if (id.endsWith(":id/kor_OrderList") || id.endsWith(":id/q_OrderList")) {
                val bounds = Rect().also(node::getBoundsInScreen)
                bounds.autoEntryListRegion(rootBounds)?.let(regions::add)
            }
            repeat(node.childCount) { index ->
                visit(node.getChild(index))
            }
        }

        visit(this)
        return regions
    }

    private fun AccessibilityNodeInfo.collectVisibleText(maxLength: Int = MaxAutoEntrySummaryLength): String {
        return collectVisibleTexts(maxTokens = MaxPreviewTokens * 4)
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(maxLength)
    }

    private fun AccessibilityNodeInfo.collectVisibleTexts(maxTokens: Int): List<String> {
        val texts = mutableListOf<String>()

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return
            node.readNodeText()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(texts::add)
            repeat(node.childCount) { index ->
                visit(node.getChild(index))
            }
        }

        visit(this)
        return texts
            .distinct()
            .take(maxTokens)
    }

    private fun Rect.isLikelyOrderListRow(rootBounds: Rect): Boolean {
        val width = width()
        val height = height()
        if (width < dp(120) || height < dp(36)) return false
        if (height > rootBounds.height() * 0.45f) return false
        if (bottom > rootBounds.bottom - dp(80)) return false
        if (top < rootBounds.top + dp(40)) return false
        return true
    }

    private fun Rect.autoEntryListRegion(rootBounds: Rect): AutoEntryListRegion? {
        if (isEmpty || rootBounds.isEmpty) return null
        val splitY = rootBounds.top + (rootBounds.height() / 2)
        return if (centerY() < splitY) {
            AutoEntryListRegion.Top
        } else {
            AutoEntryListRegion.Bottom
        }
    }

    private fun AccessibilityNodeInfo.isInsungOrderListRowNode(): Boolean {
        val id = viewIdResourceName.orEmpty()
        return id.endsWith(":id/kor_LOrderSub") || id.endsWith(":id/q_LOrderSub")
    }

    private fun String.isLikelyOrderListItemText(): Boolean {
        val normalized = replace(Regex("""\s+"""), " ").trim()
        if (normalized.length < 8) return false
        if (AutoEntryExcludedTextRegex.containsMatchIn(normalized)) return false
        val hasLocationSignal = RouteCityOrDistrictRegex.containsMatchIn(normalized) ||
            RouteTownOrRoadRegex.containsMatchIn(normalized) ||
            AutoEntryRouteSignalRegex.containsMatchIn(normalized)
        val hasOrderSignal = AutoEntryPriceRegex.containsMatchIn(normalized) ||
            AutoEntryDistanceRegex.containsMatchIn(normalized) ||
            AutoEntryVehicleOrPaymentRegex.containsMatchIn(normalized)
        return hasLocationSignal && hasOrderSignal
    }

    private fun String.normalizeAutoEntryListSignature(): String {
        return lowercase(Locale.KOREAN)
            .replace(AutoEntryDistanceRegex, "")
            .replace(Regex("""[\s/(),._\-·:：]+"""), "")
            .take(MaxAutoEntrySignatureLength)
    }

    private fun maybeHandleRoadDistanceEvaluation(
        root: AccessibilityNodeInfo,
        parsedDraft: ParsedOrderDraft,
        actionSignature: String,
        capturedAtMillis: Long,
        forceManualConfirmation: Boolean,
        isSecondary: Boolean,
    ): Boolean {
        if (!AutoConfirmEvaluator.needsRoadDistanceEvaluation(activeSettings, parsedDraft, isSecondary)) {
            return false
        }

        val cacheKey = buildRoadDistanceCacheKey(actionSignature, isSecondary)
        roadDistanceCache[cacheKey]?.takeIf { cached ->
            capturedAtMillis - cached.cachedAtMillis < RoadDistanceCacheMillis
        }?.let { cached ->
            evaluateAndConfirmDetailDraft(
                root = root,
                parsedDraft = parsedDraft,
                actionSignature = actionSignature,
                capturedAtMillis = capturedAtMillis,
                forceManualConfirmation = forceManualConfirmation,
                roadDistanceEvaluation = cached.evaluation,
            )
            return true
        }

        if (isSecondary) {
            calculateFastSecondaryRoadDistanceEvaluation(parsedDraft)?.let { evaluation ->
                roadDistanceCache[cacheKey] = CachedRoadDistanceEvaluation(
                    evaluation = evaluation,
                    cachedAtMillis = capturedAtMillis,
                )
                trimRoadDistanceCache()
                evaluateAndConfirmDetailDraft(
                    root = root,
                    parsedDraft = parsedDraft,
                    actionSignature = actionSignature,
                    capturedAtMillis = capturedAtMillis,
                    forceManualConfirmation = forceManualConfirmation,
                    roadDistanceEvaluation = evaluation,
                )
                return true
            }
        }

        if (roadDistanceInFlightSignature == cacheKey) {
            return true
        }

        if (!isSecondary && !hasRoadDistanceApiKey()) {
            return evaluateAndConfirmDetailDraft(
                root = root,
                parsedDraft = parsedDraft,
                actionSignature = actionSignature,
                capturedAtMillis = capturedAtMillis,
                forceManualConfirmation = forceManualConfirmation,
                roadDistanceEvaluation = RoadDistanceEvaluation(
                    pickupFailureReason = "카카오 REST API 키가 없어 실제 주행거리 계산을 하지 못했습니다.",
                    destinationRadiusFailureReason = "카카오 REST API 키가 없어 실제 주행거리 계산을 하지 못했습니다.",
                ),
            )
        }

        roadDistanceInFlightSignature = cacheKey
        serviceScope.launch {
            val evaluation = calculateRoadDistanceEvaluation(
                parsedDraft = parsedDraft,
                isSecondary = isSecondary,
            )
            roadDistanceInFlightSignature = null
            roadDistanceCache[cacheKey] = CachedRoadDistanceEvaluation(
                evaluation = evaluation,
                cachedAtMillis = System.currentTimeMillis(),
            )
            trimRoadDistanceCache()

            mainHandler.post {
                val currentRoot = rootInActiveWindow ?: return@post
                val currentPackage = (currentRoot.packageName ?: "").toString()
                if (!isSupportedInsungPackage(currentPackage)) return@post

                val currentDraft = KoreanOrderDraftParser.parseInsungQuick(
                    root = currentRoot,
                    packageName = currentPackage,
                )
                if (buildOrderSignature(currentDraft) != actionSignature) return@post

                evaluateAndConfirmDetailDraft(
                    root = currentRoot,
                    parsedDraft = parsedDraft,
                    actionSignature = actionSignature,
                    capturedAtMillis = System.currentTimeMillis(),
                    forceManualConfirmation = forceManualConfirmation,
                    roadDistanceEvaluation = evaluation,
                )
            }
        }
        return true
    }

    private fun calculateFastSecondaryRoadDistanceEvaluation(
        parsedDraft: ParsedOrderDraft,
    ): RoadDistanceEvaluation? {
        val needsPickupDistance =
            activeSettings.secondaryMaximumPickupDistanceKmText.trim().toDoubleOrNull() != null
        val needsTrackingDestinationMatch =
            activeSettings.orderTrackingModeEnabled &&
                activeSettings.activeDriveDestinationText.trim().isNotBlank()
        val needsDestinationRadius = !needsTrackingDestinationMatch &&
            activeSettings.secondaryDestinationRadiusKmText.trim().toDoubleOrNull() != null

        if (needsTrackingDestinationMatch) {
            return calculateTrackingRoadDistanceEvaluation(parsedDraft)
        }

        var pickupDistanceKm: Double? = null
        var pickupFailureReason: String? = null
        var pickupDistanceLabel: String? = null
        var destinationRadiusDistanceKm: Double? = null
        var destinationRadiusFailureReason: String? = null
        var destinationRadiusDistanceLabel: String? = null

        if (needsPickupDistance) {
            val straightDistanceKm = parsedDraft.currentToPickupDistanceKm
            if (straightDistanceKm == null) {
                pickupFailureReason = "추가 상차거리 추정 실패: 상세 화면의 현위치→상차지 직선거리 값을 찾지 못했습니다."
            } else {
                pickupDistanceKm = straightDistanceKm * SecondaryEstimatedRoadDistanceMultiplier
                pickupDistanceLabel =
                    "추정거리(직선 ${straightDistanceKm.formatDistanceKm()}km×${SecondaryEstimatedRoadDistanceMultiplier.formatMultiplier()})"
            }
        }

        if (needsDestinationRadius) {
            val administrativeMatch = findSecondaryDestinationAdministrativeMatch(parsedDraft)
            if (administrativeMatch == null) {
                return null
            }
            destinationRadiusDistanceKm = 0.0
            destinationRadiusDistanceLabel = "행정동일치($administrativeMatch)"
        }

        return RoadDistanceEvaluation(
            pickupDistanceKm = pickupDistanceKm,
            pickupFailureReason = pickupFailureReason,
            pickupDistanceLabel = pickupDistanceLabel,
            destinationRadiusDistanceKm = destinationRadiusDistanceKm,
            destinationRadiusFailureReason = destinationRadiusFailureReason,
            destinationRadiusDistanceLabel = destinationRadiusDistanceLabel,
        )
    }

    private fun calculateRoadDistanceEvaluation(
        parsedDraft: ParsedOrderDraft,
        isSecondary: Boolean,
    ): RoadDistanceEvaluation {
        val needsPickupDistance = if (isSecondary) {
            activeSettings.secondaryMaximumPickupDistanceKmText.trim().toDoubleOrNull() != null
        } else {
            activeSettings.primaryMaximumPickupDistanceKmText.trim().toDoubleOrNull() != null &&
                parsedDraft.currentToPickupDistanceKm == null
        }
        val needsTrackingDestinationMatch = isSecondary &&
            activeSettings.orderTrackingModeEnabled &&
            activeSettings.activeDriveDestinationText.trim().isNotBlank()
        val needsDestinationRadius = isSecondary &&
            !needsTrackingDestinationMatch &&
            activeSettings.secondaryDestinationRadiusKmText.trim().toDoubleOrNull() != null

        if (needsTrackingDestinationMatch) {
            return calculateTrackingRoadDistanceEvaluation(parsedDraft)
        }

        var pickupDistanceKm: Double? = null
        var pickupFailureReason: String? = null
        var pickupDistanceLabel: String? = null
        var destinationRadiusDistanceKm: Double? = null
        var destinationRadiusFailureReason: String? = null
        var destinationRadiusDistanceLabel: String? = null

        if (needsPickupDistance) {
            if (isSecondary) {
                val straightDistanceKm = parsedDraft.currentToPickupDistanceKm
                if (straightDistanceKm == null) {
                    pickupFailureReason = "추가 상차거리 추정 실패: 상세 화면의 현위치→상차지 직선거리 값을 찾지 못했습니다."
                } else {
                    pickupDistanceKm = straightDistanceKm * SecondaryEstimatedRoadDistanceMultiplier
                    pickupDistanceLabel =
                        "추정거리(직선 ${straightDistanceKm.formatDistanceKm()}km×${SecondaryEstimatedRoadDistanceMultiplier.formatMultiplier()})"
                }
            } else {
                val pickupAddressCandidates = parsedDraft.routeAddressCandidates(RouteAddressRole.Pickup)
                val currentLocation = deviceLocationProvider.lastKnownLocation()

                when {
                    pickupAddressCandidates.isEmpty() -> {
                        pickupFailureReason = "상차지 전체 주소 후보가 부족해 현재 위치 기준 주행거리 계산을 하지 못했습니다. TMAP 연결 탭에서 상차지 주소를 직접 입력해 주세요."
                    }
                    currentLocation.location == null -> {
                        pickupFailureReason = currentLocation.failureReason
                            ?: "현재 위치를 확인하지 못해 상차지 주행거리 계산을 하지 못했습니다."
                    }
                    else -> {
                        val outcome = calculateDrivingDistanceToAnyDestination(
                            origin = RouteWaypoint.LatLng(
                                latitude = currentLocation.location.latitude,
                                longitude = currentLocation.location.longitude,
                            ),
                            destinationCandidates = pickupAddressCandidates,
                        )
                        pickupDistanceKm = outcome.distanceKm
                        pickupFailureReason = outcome.failureReason?.let {
                            "상차지 주행거리 계산 실패: $it"
                        }
                    }
                }
            }
        }

        if (needsDestinationRadius) {
            val administrativeMatch = findSecondaryDestinationAdministrativeMatch(parsedDraft)
            if (administrativeMatch != null) {
                destinationRadiusDistanceKm = 0.0
                destinationRadiusDistanceLabel = "행정동일치($administrativeMatch)"
            } else {
                val orderDestinationCandidates = parsedDraft.estimatedAddressCandidates(RouteAddressRole.Dropoff)
                val referenceDestinationCandidates = activeSettings.activeDriveDestinationText
                    .estimatedAddressCandidatesFromText()

                when {
                    orderDestinationCandidates.isEmpty() -> {
                        destinationRadiusFailureReason = "추가 오더 하차지 요약주소가 동/읍/면 또는 도로명 이상으로 확인되지 않아 메인 목적지 반경을 추정하지 못했습니다."
                    }
                    referenceDestinationCandidates.isEmpty() -> {
                        destinationRadiusFailureReason = "메인오더 목적지가 동/읍/면 또는 도로명 이상으로 확인되지 않아 반경을 추정하지 못했습니다. 오더 추적 화면에서 기준 도착지 상세주소를 직접 입력해 주세요."
                    }
                    else -> {
                        val estimate = estimateRouteDistanceBetweenAddressCandidates(
                            fromCandidates = orderDestinationCandidates,
                            toCandidates = referenceDestinationCandidates,
                        )
                        val estimatedDistanceKm = estimate.distanceKm
                        val straightDistanceKm = estimate.straightDistanceKm
                        destinationRadiusDistanceKm = estimatedDistanceKm
                        if (estimatedDistanceKm != null && straightDistanceKm != null) {
                            destinationRadiusDistanceLabel =
                                "추정반경(직선 ${straightDistanceKm.formatDistanceKm()}km×${SecondaryEstimatedRoadDistanceMultiplier.formatMultiplier()})"
                        }
                        destinationRadiusFailureReason = estimate.failureReason?.let {
                            "메인 목적지 반경 추정 실패: $it"
                        }
                    }
                }
            }
        }

        return RoadDistanceEvaluation(
            pickupDistanceKm = pickupDistanceKm,
            pickupFailureReason = pickupFailureReason,
            pickupDistanceLabel = pickupDistanceLabel,
            destinationRadiusDistanceKm = destinationRadiusDistanceKm,
            destinationRadiusFailureReason = destinationRadiusFailureReason,
            destinationRadiusDistanceLabel = destinationRadiusDistanceLabel,
        )
    }

    private fun calculateTrackingRoadDistanceEvaluation(
        parsedDraft: ParsedOrderDraft,
    ): RoadDistanceEvaluation {
        val referenceDestination = activeSettings.activeDriveDestinationText.trim()
        val referenceCandidates = referenceDestination.estimatedAddressCandidatesFromText()
        val pickupCandidates = parsedDraft.estimatedAddressCandidates(RouteAddressRole.Pickup)
        val currentToPickupKm = parsedDraft.currentToPickupDistanceKm
        val pickupToDropoffKm = parsedDraft.pickupToDropoffDistanceKm
        val currentLocation = deviceLocationProvider.lastKnownLocation()

        var pickupDistanceKm: Double? = null
        var pickupFailureReason: String? = null
        var pickupDistanceLabel: String? = null

        when {
            pickupToDropoffKm == null -> {
                pickupFailureReason = "추적 빠른 제외: 적요상세의 상차지→하차지 직선거리 값을 찾지 못했습니다."
            }
            pickupToDropoffKm > TrackingPickupToDropoffLimitKm -> {
                pickupFailureReason =
                    "추적 빠른 제외: 상차→하차 직선거리 ${pickupToDropoffKm.formatDistanceKm()}km > ${TrackingPickupToDropoffLimitKm.formatDistanceKm()}km"
            }
            currentToPickupKm == null -> {
                pickupFailureReason = "추적 경로상 상차 우회 계산 실패: 적용상세의 현위치→상차지 직선거리 값을 찾지 못했습니다."
            }
            currentLocation.location == null -> {
                pickupFailureReason = currentLocation.failureReason
                    ?: "추적 경로상 상차 우회 계산 실패: 현재 위치를 확인하지 못했습니다."
            }
            referenceCandidates.isEmpty() -> {
                pickupFailureReason = "추적 경로상 상차 우회 계산 실패: 기준 하차지 상세주소 후보가 부족합니다."
            }
            pickupCandidates.isEmpty() -> {
                pickupFailureReason = "추적 경로상 상차 우회 계산 실패: 추가오더 상차지 주소 후보가 부족합니다."
            }
            else -> {
                val currentPoint = currentLocation.location
                val currentToReferenceKm = referenceCandidates
                    .asSequence()
                    .mapNotNull { reference ->
                        addressDistanceResolver.distanceKmFrom(
                            latitude = currentPoint.latitude,
                            longitude = currentPoint.longitude,
                            toAddress = reference,
                        )
                    }
                    .firstOrNull()
                val pickupToReferenceOutcome = estimateRouteDistanceBetweenAddressCandidates(
                    fromCandidates = pickupCandidates,
                    toCandidates = referenceCandidates,
                )
                val pickupToReferenceKm = pickupToReferenceOutcome.straightDistanceKm

                when {
                    currentToReferenceKm == null -> {
                        pickupFailureReason = "추적 경로상 상차 우회 계산 실패: 현재위치→기준하차 상세주소 직선거리를 계산하지 못했습니다."
                    }
                    pickupToReferenceKm == null -> {
                        pickupFailureReason = "추적 경로상 상차 우회 계산 실패: 추가상차→기준하차 직선거리 계산 실패: ${pickupToReferenceOutcome.failureReason ?: "주소 후보 좌표 변환 실패"}"
                    }
                    else -> {
                        val detourKm = (currentToPickupKm + pickupToReferenceKm - currentToReferenceKm)
                            .coerceAtLeast(0.0)
                        pickupDistanceKm = detourKm
                        pickupDistanceLabel = "우회직선(현위치→추가상차 ${currentToPickupKm.formatDistanceKm()}km + 추가상차→기준하차 ${pickupToReferenceKm.formatDistanceKm()}km - 현위치→기준하차 ${currentToReferenceKm.formatDistanceKm()}km)"
                    }
                }
            }
        }

        val match = findTrackingDestinationCompatibility(parsedDraft)
        val destinationMatchReason = match?.reason
        val destinationFailureReason = if (match == null) {
            "추적 하차지 조건 불일치: 기준=${referenceDestination.shortDiagnosticText()}, 후보=${parsedDraft.destinationAdministrativeMatchTexts().joinToString(" / ").shortDiagnosticText()}"
        } else {
            null
        }

        return RoadDistanceEvaluation(
            pickupDistanceKm = pickupDistanceKm,
            pickupFailureReason = pickupFailureReason,
            pickupDistanceLabel = pickupDistanceLabel,
            destinationRadiusDistanceKm = destinationMatchReason?.let { 0.0 },
            destinationRadiusFailureReason = destinationFailureReason,
            destinationRadiusDistanceLabel = destinationMatchReason,
        )
    }

    private fun calculateDrivingDistanceToAnyDestination(
        origin: RouteWaypoint,
        destinationCandidates: List<String>,
    ): RouteDistanceOutcome {
        val failureReasons = mutableListOf<String>()
        destinationCandidates.forEach { destination ->
            val outcome = calculateDrivingDistance(
                origin = origin,
                destination = RouteWaypoint.Address(destination),
            )
            if (outcome.distanceKm != null) {
                return outcome
            }
            outcome.failureReason
                ?.takeIf { it.isNotBlank() }
                ?.let { failureReasons += "$destination: $it" }
        }
        return RouteDistanceOutcome.failure(
            failureReasons
                .take(3)
                .joinToString(" / ")
                .ifBlank { "카카오가 사용할 수 있는 주소 후보를 찾지 못했습니다." },
        )
    }

    private fun calculateDrivingDistanceBetweenAddressCandidates(
        originCandidates: List<String>,
        destinationCandidates: List<String>,
    ): RouteDistanceOutcome {
        val failureReasons = mutableListOf<String>()
        originCandidates.forEach { origin ->
            destinationCandidates.forEach { destination ->
                val outcome = calculateDrivingDistance(
                    origin = RouteWaypoint.Address(origin),
                    destination = RouteWaypoint.Address(destination),
                )
                if (outcome.distanceKm != null) {
                    return outcome
                }
                outcome.failureReason
                    ?.takeIf { it.isNotBlank() }
                    ?.let { failureReasons += "$origin -> $destination: $it" }
            }
        }
        return RouteDistanceOutcome.failure(
            failureReasons
                .take(3)
                .joinToString(" / ")
                .ifBlank { "카카오가 사용할 수 있는 주소 후보 조합을 찾지 못했습니다." },
        )
    }

    private fun estimateRouteDistanceBetweenAddressCandidates(
        fromCandidates: List<String>,
        toCandidates: List<String>,
    ): EstimatedDistanceOutcome {
        val failureReasons = mutableListOf<String>()
        fromCandidates.forEach { from ->
            toCandidates.forEach { to ->
                val straightDistanceKm = addressDistanceResolver.distanceKm(from, to)
                if (straightDistanceKm != null) {
                    return EstimatedDistanceOutcome.success(
                        straightDistanceKm = straightDistanceKm,
                        distanceKm = straightDistanceKm * SecondaryEstimatedRoadDistanceMultiplier,
                    )
                }
                failureReasons += "$from -> $to"
            }
        }
        return EstimatedDistanceOutcome.failure(
            failureReasons
                .take(3)
                .joinToString(" / ")
                .ifBlank { "좌표로 변환할 수 있는 주소 후보 조합을 찾지 못했습니다." },
        )
    }

    private fun calculateDrivingDistance(
        origin: RouteWaypoint,
        destination: RouteWaypoint,
    ): RouteDistanceOutcome {
        val kakaoApiKey = activeSettings.kakaoRestApiKey.trim().ifBlank { BuildConfig.KAKAO_REST_API_KEY }
        if (kakaoApiKey.isNotBlank()) {
            return kakaoRouteDistanceService.drivingDistanceKm(
                apiKey = kakaoApiKey,
                origin = origin,
                destination = destination,
            )
        }

        return RouteDistanceOutcome.failure("경로 API 키가 없습니다.")
    }

    private fun hasRoadDistanceApiKey(): Boolean {
        return activeSettings.kakaoRestApiKey.trim().ifBlank { BuildConfig.KAKAO_REST_API_KEY }.isNotBlank()
    }

    private fun evaluateAndConfirmDetailDraft(
        root: AccessibilityNodeInfo,
        parsedDraft: ParsedOrderDraft,
        actionSignature: String,
        capturedAtMillis: Long,
        forceManualConfirmation: Boolean,
        roadDistanceEvaluation: RoadDistanceEvaluation? = null,
    ): Boolean {
        val confirmNode = findAutoConfirmNode(root) ?: run {
            logOperation(
                eventType = "order_confirm_button_missing",
                status = "order-confirm-button-missing",
                parsedDraft = parsedDraft,
                source = activePendingAutoEntry(capturedAtMillis)?.let { "order_list_auto_entry" } ?: "direct_detail",
                confirmed = false,
                reason = "확정 가능한 상세화면으로 보였지만 확정 버튼 노드를 찾지 못했습니다.",
                screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
                rawContext = root.collectVisibleTexts(maxTokens = MaxPreviewTokens * 8)
                    .joinToString(" ")
                    .operationContextText(720),
                createdAtMillis = capturedAtMillis,
            )
            return false
        }
        val autoEntry = activePendingAutoEntry(capturedAtMillis)
        val detailSignature = buildAutoEntryDetailSignature(parsedDraft)
        if (
            autoEntry != null &&
            detailSignature.isNotBlank() &&
            autoEntryCheckedDetailSignatures.containsKey(detailSignature)
        ) {
            handleAutoEntrySkippedDetail(
                parsedDraft = parsedDraft,
                autoEntry = autoEntry,
                detailSignature = detailSignature,
                reason = "이미 상세 확인한 오더입니다.",
                capturedAtMillis = capturedAtMillis,
            )
            return true
        }
        val activeDriveDestination = activeSettings.activeDriveDestinationText.trim()
        val shouldUseSecondaryRules = shouldUseSecondaryRulesForDraft()
        val trackingBlockReason = if (shouldUseSecondaryRules) {
            trackingAdditionalAutoConfirmBlockReason()
        } else {
            null
        }

        if (trackingBlockReason == null && roadDistanceEvaluation == null) {
            val roadDistanceHandled = maybeHandleRoadDistanceEvaluation(
                root = root,
                parsedDraft = parsedDraft,
                actionSignature = actionSignature,
                capturedAtMillis = capturedAtMillis,
                forceManualConfirmation = forceManualConfirmation,
                isSecondary = shouldUseSecondaryRules,
            )
            if (roadDistanceHandled) return true
        }

        val decision = if (trackingBlockReason != null) {
            AutoConfirmDecision(
                shouldConfirm = false,
                reasons = listOf(trackingBlockReason),
            )
        } else if (shouldUseSecondaryRules) {
            AutoConfirmEvaluator.evaluateTrackedAdditional(
                settings = activeSettings,
                draft = parsedDraft,
                pickupRoadDistanceKm = roadDistanceEvaluation?.pickupDistanceKm,
                pickupRoadDistanceFailureReason = roadDistanceEvaluation?.pickupFailureReason,
                pickupDistanceLabel = roadDistanceEvaluation?.pickupDistanceLabel ?: "주행거리",
                destinationMatchReason = roadDistanceEvaluation?.destinationRadiusDistanceLabel,
                destinationMatchFailureReason = roadDistanceEvaluation?.destinationRadiusFailureReason,
            )
        } else {
            AutoConfirmEvaluator.evaluatePrimary(
                settings = activeSettings,
                draft = parsedDraft,
                pickupRoadDistanceKm = roadDistanceEvaluation?.pickupDistanceKm,
                pickupRoadDistanceFailureReason = roadDistanceEvaluation?.pickupFailureReason,
            )
        }
        val mergedReasons = decision.reasons.distinct()
        val secondaryDiagnostics = if (shouldUseSecondaryRules) {
            buildSecondaryDecisionDiagnostics(
                parsedDraft = parsedDraft,
                activeDriveDestination = activeDriveDestination,
                roadDistanceEvaluation = roadDistanceEvaluation,
                autoEntry = autoEntry,
            )
        } else {
            emptyList()
        }
        val decisionReasons = (mergedReasons + secondaryDiagnostics).distinct()
        val decisionDiagnostic = buildAutoConfirmDecisionDiagnostic(
            parsedDraft = parsedDraft,
            autoEntry = autoEntry,
            shouldUseSecondaryRules = shouldUseSecondaryRules,
            decision = decision,
            reasons = decisionReasons,
        )
        fun recordAutoDetailDecisionCapture() {
            saveDiagnosticCapture(
                root = root,
                packageName = (root.packageName ?: "").toString().ifBlank { InsungQuickPackage },
                tag = "AUTO_DETAIL_DECISION",
                detail = decisionDiagnostic,
            )
        }
        logOperation(
            eventType = "auto_detail_decision",
            status = if (decision.shouldConfirm) "decision-confirmable" else "decision-rejected",
            parsedDraft = parsedDraft,
            mode = autoEntry?.mode ?: if (shouldUseSecondaryRules) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = autoEntry?.let { "order_list_auto_entry" } ?: "direct_detail",
            region = autoEntry?.region,
            roadDistanceEvaluation = roadDistanceEvaluation,
            decision = decision,
            confirmed = false,
            reason = decisionReasons.joinToString(" · "),
            clickDiagnostic = autoEntry?.clickDiagnostic,
            screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
            rawContext = decisionDiagnostic,
            createdAtMillis = capturedAtMillis,
        )
        if (decision.manualInputRequired) {
            recordAutoDetailDecisionCapture()
            return handleManualInputRequired(
                signature = actionSignature,
                parsedDraft = parsedDraft,
                reasons = decisionReasons,
                capturedAtMillis = capturedAtMillis,
            )
        }
        if (!decision.shouldConfirm) {
            recordAutoDetailDecisionCapture()
            if (autoEntry != null) {
                val skipReasons = (
                    mergedReasons.ifEmpty { listOf("자동확정 조건에 맞지 않습니다.") } +
                        secondaryDiagnostics
                    ).distinct()
                handleAutoEntrySkippedDetail(
                    parsedDraft = parsedDraft,
                    autoEntry = autoEntry,
                    detailSignature = detailSignature,
                    reason = skipReasons.joinToString(" · "),
                    capturedAtMillis = capturedAtMillis,
                )
                return true
            }
            return false
        }

        if (autoEntry != null && isAutoEntryConfirmLimitReached(autoEntry.mode)) {
            recordAutoDetailDecisionCapture()
            handleAutoEntrySkippedDetail(
                parsedDraft = parsedDraft,
                autoEntry = autoEntry,
                detailSignature = detailSignature,
                reason = "${autoEntry.mode.toKoreanLabel()} 확정 개수 제한에 도달했습니다.",
                capturedAtMillis = capturedAtMillis,
            )
            return true
        }

        if (autoEntry != null && autoEntry.mode.isSecondary != shouldUseSecondaryRules) {
            recordAutoDetailDecisionCapture()
            handleAutoEntrySkippedDetail(
                parsedDraft = parsedDraft,
                autoEntry = autoEntry,
                detailSignature = detailSignature,
                reason = "자동진입 모드와 현재 상세 판단 모드가 달라 확정하지 않았습니다.",
                capturedAtMillis = capturedAtMillis,
            )
            return true
        }

        val confirmAttemptAtMillis = System.currentTimeMillis()
        if (
            shouldSuppressDuplicateConfirmClick(
                actionSignature = actionSignature,
                parsedDraft = parsedDraft,
                autoEntry = autoEntry,
                shouldUseSecondaryRules = shouldUseSecondaryRules,
                roadDistanceEvaluation = roadDistanceEvaluation,
                decision = decision,
                decisionReasons = decisionReasons,
                capturedAtMillis = confirmAttemptAtMillis,
            )
        ) {
            return true
        }
        val confirmClickAttempt = confirmNode.clickSelfOrAncestorWithDiagnostics()
        if (!confirmClickAttempt.clicked) {
            recordAutoDetailDecisionCapture()
            val confirmClickDiagnostic = "confirmButton=${confirmClickAttempt.diagnostic}"
            val failureDiagnostic = buildConfirmAttemptDiagnostic(
                failureType = "확정버튼클릭실패",
                parsedDraft = parsedDraft,
                attemptedAtMillis = confirmAttemptAtMillis,
                observedAtMillis = confirmAttemptAtMillis,
                source = autoEntry?.let { "order_list_auto_entry" } ?: "direct_detail",
                mode = autoEntry?.mode ?: if (shouldUseSecondaryRules) AutoEntryMode.Secondary else AutoEntryMode.Primary,
                autoEntryRegion = autoEntry?.region,
                autoEntryReason = autoEntry?.let {
                    "${it.mode.toKoreanLabel()} 자동진입: ${it.region.toKoreanLabel()} · ${it.listDistanceDiagnostic()}"
                },
                clickDiagnostic = listOfNotNull(autoEntry?.clickDiagnostic, confirmClickDiagnostic).joinToString(" · "),
                decisionReasons = decisionReasons,
                extra = listOf(decisionDiagnostic),
            )
            logOperation(
                eventType = "order_confirm_click_failed",
                status = if (shouldUseSecondaryRules) {
                    "tracked-additional-auto-confirm-click-failed"
                } else {
                    "primary-auto-confirm-click-failed"
                },
                parsedDraft = parsedDraft,
                mode = autoEntry?.mode ?: if (shouldUseSecondaryRules) AutoEntryMode.Secondary else AutoEntryMode.Primary,
                source = autoEntry?.let { "order_list_auto_entry" } ?: "direct_detail",
                region = autoEntry?.region,
                roadDistanceEvaluation = roadDistanceEvaluation,
                decision = decision,
                confirmed = false,
                reason = (decisionReasons + listOf("실패유형=확정버튼클릭실패", confirmClickDiagnostic)).distinct().joinToString(" · "),
                clickDiagnostic = listOfNotNull(autoEntry?.clickDiagnostic, confirmClickDiagnostic).joinToString(" · "),
                screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
                rawContext = failureDiagnostic,
                createdAtMillis = confirmAttemptAtMillis,
            )
            return false
        }

        recordAutoDetailDecisionCapture()
        val confirmTimingReason = autoEntry?.let {
            "상세진입후확정시도=${confirmAttemptAtMillis - it.clickedAtMillis}ms"
        }

        handleSuccessfulConfirmation(
            parsedDraft = parsedDraft,
            capturedAtMillis = confirmAttemptAtMillis,
            reasons = (
                decisionReasons +
                    listOfNotNull(confirmTimingReason)
                ).distinct(),
            isSecondary = shouldUseSecondaryRules,
            confirmedByUser = false,
            confirmClickDiagnostic = "confirmButton=${confirmClickAttempt.diagnostic}",
        )
        return true
    }

    private fun shouldSuppressDuplicateConfirmClick(
        actionSignature: String,
        parsedDraft: ParsedOrderDraft,
        autoEntry: PendingAutoListEntry?,
        shouldUseSecondaryRules: Boolean,
        roadDistanceEvaluation: RoadDistanceEvaluation?,
        decision: AutoConfirmDecision,
        decisionReasons: List<String>,
        capturedAtMillis: Long,
    ): Boolean {
        trimPendingConfirmAttempts(capturedAtMillis)
        val pendingAttempt = pendingConfirmAttempts[actionSignature]
        val pendingReason = pendingAttempt
            ?.takeIf { capturedAtMillis - it.attemptedAtMillis in 0 until ConfirmDuplicateSuppressMillis }
            ?.let {
                "같은 오더 확정 클릭 직후 pending 중(${capturedAtMillis - it.attemptedAtMillis}ms)"
        }
        val recentReason = if (
            actionSignature == lastAutoConfirmSignature &&
            capturedAtMillis - lastAutoConfirmAtMillis in 0 until ConfirmDuplicateSuppressMillis
        ) {
            "같은 오더 확정 클릭 직후(${capturedAtMillis - lastAutoConfirmAtMillis}ms)"
        } else {
            null
        }
        val reason = pendingReason ?: recentReason ?: return false
        if (
            actionSignature != lastDuplicateConfirmSuppressedSignature ||
            capturedAtMillis - lastDuplicateConfirmSuppressedAtMillis >= ConfirmDuplicateSuppressLogCooldownMillis
        ) {
            lastDuplicateConfirmSuppressedSignature = actionSignature
            lastDuplicateConfirmSuppressedAtMillis = capturedAtMillis
            val duplicateDiagnostic = buildConfirmAttemptDiagnostic(
                failureType = "중복확정클릭차단",
                parsedDraft = parsedDraft,
                attemptedAtMillis = pendingAttempt?.attemptedAtMillis ?: lastAutoConfirmAtMillis,
                observedAtMillis = capturedAtMillis,
                source = autoEntry?.let { "order_list_auto_entry" } ?: "direct_detail",
                mode = autoEntry?.mode ?: if (shouldUseSecondaryRules) AutoEntryMode.Secondary else AutoEntryMode.Primary,
                autoEntryRegion = autoEntry?.region,
                autoEntryReason = autoEntry?.let {
                    "${it.mode.toKoreanLabel()} 자동진입: ${it.region.toKoreanLabel()} · ${it.listDistanceDiagnostic()}"
                },
                clickDiagnostic = autoEntry?.clickDiagnostic,
                decisionReasons = decisionReasons + reason,
            )
            logOperation(
                eventType = "order_confirm_duplicate_suppressed",
                status = "order-confirm-duplicate-suppressed",
                parsedDraft = parsedDraft,
                orderSignature = actionSignature,
                mode = autoEntry?.mode ?: if (shouldUseSecondaryRules) AutoEntryMode.Secondary else AutoEntryMode.Primary,
                source = autoEntry?.let { "order_list_auto_entry" } ?: "direct_detail",
                region = autoEntry?.region,
                roadDistanceEvaluation = roadDistanceEvaluation,
                decision = decision,
                confirmed = false,
                reason = (decisionReasons + reason).distinct().joinToString(" · "),
                clickDiagnostic = autoEntry?.clickDiagnostic,
                screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
                rawContext = duplicateDiagnostic,
                createdAtMillis = capturedAtMillis,
            )
        }
        return true
    }

    private fun shouldUseSecondaryRulesForDraft(): Boolean {
        return false
    }

    private fun trackingAdditionalAutoConfirmBlockReason(): String? {
        if (trackingReferenceOrder == null) {
            return "추적 기준오더가 없어 추가오더 자동확정을 차단했습니다."
        }
        if (!trackingReferencePickupCompleted) {
            return "기준오더 상차완료 전이라 추가오더 자동확정을 차단했습니다."
        }
        if (trackingAdditionalConfirmedCount >= TrackingAdditionalAutoConfirmLimit) {
            return "추가오더 자동확정 ${TrackingAdditionalAutoConfirmLimit}개 제한에 도달했습니다."
        }
        return null
    }

    private fun buildSecondaryDecisionDiagnostics(
        parsedDraft: ParsedOrderDraft,
        activeDriveDestination: String,
        roadDistanceEvaluation: RoadDistanceEvaluation?,
        autoEntry: PendingAutoListEntry?,
    ): List<String> {
        val matchRule = "서울 같은 구 / 경기 같은 동·리·읍·면 / 검증 권역"
        val evaluation = roadDistanceEvaluation
        val pickupResult = when {
            evaluation?.pickupDistanceKm != null -> {
                val label = evaluation.pickupDistanceLabel ?: "상차거리"
                "$label ${evaluation.pickupDistanceKm.formatDistanceKm()}km"
            }
            !evaluation?.pickupFailureReason.isNullOrBlank() ->
                "실패:${evaluation.pickupFailureReason}"
            else -> "계산없음"
        }
        val radiusResult = when {
            evaluation?.destinationRadiusDistanceKm != null -> {
                val label = evaluation.destinationRadiusDistanceLabel ?: "목적지반경"
                "$label ${evaluation.destinationRadiusDistanceKm.formatDistanceKm()}km"
            }
            !evaluation?.destinationRadiusFailureReason.isNullOrBlank() ->
                "실패:${evaluation.destinationRadiusFailureReason}"
            else -> "계산없음"
        }
        val detailPickupStraight = parsedDraft.currentToPickupDistanceKm
            ?.let { "${it.formatDistanceKm()}km" }
            ?: "미확인"
        val detailRouteStraight = parsedDraft.pickupToDropoffDistanceKm
            ?.let { "${it.formatDistanceKm()}km" }
            ?: "미확인"
        return listOfNotNull(
            autoEntry?.let { "추적 리스트: ${it.region.toKoreanLabel()} · ${it.listDistanceDiagnostic()} · ${it.listSummary.shortDiagnosticText()}" },
            "추적 기준: 기준상세주소=${activeDriveDestination.ifBlank { "없음" }.shortDiagnosticText()}",
            "추적 상태: 기준상차완료=${if (trackingReferencePickupCompleted) "예" else "아니오"}, 추가확정=${trackingAdditionalConfirmedCount}/${TrackingAdditionalAutoConfirmLimit}",
            "추적 고정조건: 경로상상차우회<=${TrackingPickupRouteDetourLimitKm.formatDistanceKm()}km, 상차→하차<=${TrackingPickupToDropoffLimitKm.formatDistanceKm()}km, 하차=$matchRule",
            "추적 화면값: 상세상차직선=$detailPickupStraight, 상세상하차직선=$detailRouteStraight, 요금=${parsedDraft.price?.toString() ?: "미확인"}",
            parsedDraft.farePerKmDiagnostic(),
            "추적 상세: 출발=${(parsedDraft.effectiveOrigin() ?: parsedDraft.requesterLocation ?: "미확인").shortDiagnosticText()}, 도착=${(parsedDraft.effectiveDestination() ?: "미확인").shortDiagnosticText()}",
            "추적 계산: 상차=$pickupResult, 하차=$radiusResult",
        )
    }

    private fun buildAutoConfirmDecisionDiagnostic(
        parsedDraft: ParsedOrderDraft,
        autoEntry: PendingAutoListEntry?,
        shouldUseSecondaryRules: Boolean,
        decision: AutoConfirmDecision,
        reasons: List<String>,
    ): String {
        val modeLabel = if (shouldUseSecondaryRules) "추적" else "기준"
        val autoEntryLabel = autoEntry?.let {
            "${it.mode.toKoreanLabel()} ${it.region.toKoreanLabel()} ${it.listDistanceDiagnostic()}"
        } ?: "직접상세"
        return buildList {
            add("mode=$modeLabel")
            add("source=$autoEntryLabel")
            add("confirm=${decision.shouldConfirm}")
            add("manualInput=${decision.manualInputRequired}")
            add("manualReview=${decision.manualReviewRequired}")
            add("origin=${(parsedDraft.effectiveOrigin() ?: parsedDraft.requesterLocation ?: "미확인").shortDiagnosticText()}")
            add("destination=${(parsedDraft.effectiveDestination() ?: "미확인").shortDiagnosticText()}")
            add("price=${parsedDraft.price ?: 0}")
            add("detailPickup=${parsedDraft.currentToPickupDistanceKm?.formatDistanceKm() ?: "미확인"}")
            add("routeStraight=${parsedDraft.pickupToDropoffDistanceKm?.formatDistanceKm() ?: "미확인"}")
            parsedDraft.farePerKmDiagnostic()?.let { add(it) }
            autoEntry?.clickDiagnostic?.let { add("entryClick=${it.take(180)}") }
            add("reasons=${reasons.joinToString(" | ").take(160)}")
        }.joinToString(" · ")
    }

    private fun buildPendingConfirmAttemptDiagnostic(
        attempt: PendingConfirmAttempt,
        failureType: String,
        observedAtMillis: Long,
        detectionSource: String? = null,
        screenText: String? = null,
        extra: List<String> = emptyList(),
    ): String {
        return buildConfirmAttemptDiagnostic(
            failureType = failureType,
            parsedDraft = attempt.draft,
            attemptedAtMillis = attempt.attemptedAtMillis,
            observedAtMillis = observedAtMillis,
            source = attempt.confirmSource,
            mode = attempt.autoEntryMode ?: if (attempt.isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            autoEntryRegion = attempt.autoEntryRegion,
            autoEntryReason = attempt.autoEntryReason,
            clickDiagnostic = buildCombinedClickDiagnostic(
                attempt.autoEntryClickDiagnostic,
                attempt.confirmClickDiagnostic,
            ),
            decisionReasons = attempt.reasons,
            detectionSource = detectionSource,
            screenText = screenText,
            extra = extra,
        )
    }

    private fun buildCombinedClickDiagnostic(
        autoEntryClickDiagnostic: String?,
        confirmClickDiagnostic: String?,
    ): String? {
        return listOfNotNull(autoEntryClickDiagnostic, confirmClickDiagnostic)
            .filter(String::isNotBlank)
            .joinToString(" · ")
            .takeIf(String::isNotBlank)
    }

    private fun buildConfirmAttemptDiagnostic(
        failureType: String,
        parsedDraft: ParsedOrderDraft,
        attemptedAtMillis: Long,
        observedAtMillis: Long,
        source: String,
        mode: AutoEntryMode?,
        autoEntryRegion: AutoEntryListRegion?,
        autoEntryReason: String? = null,
        clickDiagnostic: String? = null,
        decisionReasons: List<String> = emptyList(),
        detectionSource: String? = null,
        screenText: String? = null,
        extra: List<String> = emptyList(),
    ): String {
        val elapsedMillis = (observedAtMillis - attemptedAtMillis).coerceAtLeast(0L)
        return buildList {
            add("확정로그유형=$failureType")
            add("시도시각=${attemptedAtMillis.toOperationClockText()}")
            add("관측시각=${observedAtMillis.toOperationClockText()}")
            add("경과=${elapsedMillis}ms")
            add("source=$source")
            add("mode=${mode?.toOperationModeLabel() ?: "unknown"}")
            autoEntryRegion?.let { add("listRegion=${it.toKoreanLabel()}") }
            add("order=${(parsedDraft.effectiveRouteText() ?: "인성 상세 오더").operationContextText()}")
            add("origin=${(parsedDraft.effectiveOrigin() ?: parsedDraft.requesterLocation ?: "미확인").operationContextText()}")
            add("destination=${(parsedDraft.effectiveDestination() ?: "미확인").operationContextText()}")
            add("price=${parsedDraft.price ?: 0}")
            add("currentToPickup=${parsedDraft.currentToPickupDistanceKm?.formatDistanceKm() ?: "미확인"}")
            add("pickupToDropoff=${parsedDraft.pickupToDropoffDistanceKm?.formatDistanceKm() ?: "미확인"}")
            parsedDraft.farePerKmDiagnostic()?.let { add(it) }
            detectionSource?.takeIf(String::isNotBlank)?.let { add("detection=$it") }
            screenText?.takeIf(String::isNotBlank)?.let { add("screen=${it.operationContextText(360)}") }
            autoEntryReason?.takeIf(String::isNotBlank)?.let { add("autoEntry=${it.operationContextText(240)}") }
            clickDiagnostic?.takeIf(String::isNotBlank)?.let { add("click=${it.operationContextText(420)}") }
            if (decisionReasons.isNotEmpty()) {
                add("reasons=${decisionReasons.joinToString(" | ").operationContextText(420)}")
            }
            extra
                .filter(String::isNotBlank)
                .forEach { add(it.operationContextText(420)) }
        }.distinct().joinToString(" · ")
    }

    private fun handleAutoEntrySkippedDetail(
        parsedDraft: ParsedOrderDraft,
        autoEntry: PendingAutoListEntry,
        detailSignature: String,
        reason: String,
        capturedAtMillis: Long,
    ) {
        val diagnosticReason = (
            listOf(autoEntry.listDistanceDiagnostic()) +
                listOfNotNull(parsedDraft.farePerKmDiagnostic()) +
                listOf(autoEntry.clickDiagnostic, reason)
            ).joinToString(" · ")
        if (detailSignature.isNotBlank()) {
            autoEntryCheckedDetailSignatures[detailSignature] = capturedAtMillis
        }
        lockAutoEntryListSignature(autoEntry, capturedAtMillis, AutoEntryRejectedListLockMillis)
        advanceAutoEntryCandidateCursor(
            mode = autoEntry.mode,
            region = autoEntry.region,
            selectedIndexInRegion = autoEntry.candidateIndexInRegion,
            candidateCountInRegion = autoEntry.candidateCountInRegion,
        )
        advanceAutoEntryRegion(autoEntry.mode, autoEntry.region)
        pendingAutoListEntry = null
        markOrderProcessingLocked(buildOrderSignature(parsedDraft), capturedAtMillis)

        Log.d(
            LogTag,
            "order-list-auto-entry skipped: mode=${autoEntry.mode} destination=${parsedDraft.effectiveDestination() ?: "미확인"} reason=$diagnosticReason",
        )
        val skippedStatus = if (autoEntry.mode == AutoEntryMode.Secondary) {
            "tracked-additional-rejected"
        } else {
            "order-list-auto-entry-skipped"
        }
        logOperation(
            eventType = "auto_entry_detail_skipped",
            status = skippedStatus,
            parsedDraft = parsedDraft,
            mode = autoEntry.mode,
            source = "order_list_auto_entry",
            region = autoEntry.region,
            confirmed = false,
            reason = diagnosticReason,
            clickDiagnostic = autoEntry.clickDiagnostic,
            screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
            rawContext = diagnosticReason,
            createdAtMillis = capturedAtMillis,
        )

        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft.effectiveRouteText() ?: autoEntry.listSummary.ifBlank { "자동상세확정 확인 오더" },
                    originSummary = parsedDraft.effectiveOrigin() ?: parsedDraft.requesterLocation?.let { if (it == "비공개") "출발지 비공개" else it } ?: "미확인",
                    destinationSummary = parsedDraft.effectiveDestination() ?: "미확인",
                    price = parsedDraft.price ?: 0,
                    status = skippedStatus,
                    failureReason = diagnosticReason,
                ),
            )
        }
        scheduleAutoEntryBackToList()
    }

    private fun scheduleAutoEntryBackToList() {
        mainHandler.postDelayed(
            {
                val root = rootInActiveWindow ?: return@postDelayed
                val packageName = (root.packageName ?: "").toString()
                if (!isSupportedInsungPackage(packageName)) return@postDelayed
                val draft = KoreanOrderDraftParser.parseInsungQuick(
                    root = root,
                    packageName = packageName,
                )
                if (draft.isConfirmableDetailScreen) {
                    if (!clickAutoEntryDetailCancelButton(root)) {
                        Log.d(LogTag, "order-list-auto-entry: detail cancel not clicked, waiting for app auto-return")
                    }
                    scheduleAutoEntryListRescan(450L, 900L, 1_400L)
                } else if (root.hasInsungOrderListRows()) {
                    scheduleAutoEntryListRescan(80L, 350L)
                }
            },
            AutoEntryBackDelayMillis,
        )
    }

    private fun buildAutoEntryDetailSignature(draft: ParsedOrderDraft): String {
        return listOfNotNull(
            draft.clientText,
            draft.effectiveOrigin() ?: draft.requesterLocation,
            draft.effectiveDestination(),
            draft.price?.toString(),
            draft.vehicleType,
            draft.paymentMode,
        )
            .joinToString("|")
            .lowercase(Locale.KOREAN)
            .replace(Regex("""[\s/(),._\-·:：]+"""), "")
            .take(MaxAutoEntrySignatureLength)
    }

    private fun registerAutoEntryConfirmationIfNeeded(
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
        autoEntryMode: AutoEntryMode? = activePendingAutoEntry(capturedAtMillis)?.mode,
    ) {
        val mode = autoEntryMode ?: return
        when (mode) {
            AutoEntryMode.Primary -> {
                primaryAutoEntryConfirmedCount += 1
            }
            AutoEntryMode.Secondary -> secondaryAutoEntryConfirmedCount += 1
        }
    }

    private fun registerAutoEntryAttemptIfNeeded(
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ) {
        val autoEntry = activePendingAutoEntry(capturedAtMillis) ?: return
        advanceAutoEntryCandidateCursor(
            mode = autoEntry.mode,
            region = autoEntry.region,
            selectedIndexInRegion = autoEntry.candidateIndexInRegion,
            candidateCountInRegion = autoEntry.candidateCountInRegion,
        )
        advanceAutoEntryRegion(autoEntry.mode, autoEntry.region)
        pendingAutoListEntry = null
    }

    private fun hasRecentPendingConfirmAttempt(nowMillis: Long): Boolean {
        trimPendingConfirmAttempts(nowMillis)
        return pendingConfirmAttempts.values.any { pending ->
            nowMillis - pending.attemptedAtMillis in 0..ConfirmAttemptVerificationWindowMillis
        }
    }

    private fun handleManualInputRequired(
        signature: String,
        parsedDraft: ParsedOrderDraft,
        reasons: List<String>,
        capturedAtMillis: Long,
    ): Boolean {
        val manualSignature = "manual-input|$signature"
        if (
            manualSignature == lastManualInputRequiredSignature &&
            capturedAtMillis - lastManualInputRequiredAtMillis < ManualInputRequiredCooldownMillis
        ) {
            return true
        }

        lastManualInputRequiredSignature = manualSignature
        lastManualInputRequiredAtMillis = capturedAtMillis
        markOrderProcessingLocked(signature, capturedAtMillis)
        activePendingAutoEntry(capturedAtMillis)?.let { autoEntry ->
            val detailSignature = buildAutoEntryDetailSignature(parsedDraft)
            if (detailSignature.isNotBlank()) {
                autoEntryCheckedDetailSignatures[detailSignature] = capturedAtMillis
            }
            lockAutoEntryListSignature(autoEntry, capturedAtMillis, AutoEntryRejectedListLockMillis)
            advanceAutoEntryCandidateCursor(
                mode = autoEntry.mode,
                region = autoEntry.region,
                selectedIndexInRegion = autoEntry.candidateIndexInRegion,
                candidateCountInRegion = autoEntry.candidateCountInRegion,
            )
            advanceAutoEntryRegion(autoEntry.mode, autoEntry.region)
            pendingAutoListEntry = null
            scheduleAutoEntryBackToList()
        }

        Log.d(
            LogTag,
            "manual-input-required: destination=${parsedDraft.effectiveDestination() ?: "미확인"} reasons=${reasons.joinToString(" | ")}",
        )
        val isTrackingAdditional = shouldUseSecondaryRulesForDraft()
        val manualInputStatus = if (isTrackingAdditional) {
            "tracked-additional-manual-input-required"
        } else {
            "manual-input-required"
        }
        logOperation(
            eventType = "manual_input_required",
            status = manualInputStatus,
            parsedDraft = parsedDraft,
            mode = if (isTrackingAdditional) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = "detail_decision",
            confirmed = false,
            manualInputRequired = true,
            reason = reasons.joinToString(" · "),
            screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
            rawContext = reasons.joinToString(" · "),
            createdAtMillis = capturedAtMillis,
        )

        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft.effectiveRouteText() ?: "주소 수동입력 필요 오더",
                    originSummary = parsedDraft.effectiveOrigin() ?: parsedDraft.requesterLocation ?: "수동입력 필요",
                    destinationSummary = parsedDraft.effectiveDestination() ?: "수동입력 필요",
                    price = parsedDraft.price ?: 0,
                    status = manualInputStatus,
                    failureReason = (
                        listOfNotNull(parsedDraft.farePerKmDiagnostic()) +
                            reasons
                        ).distinct().joinToString(" · "),
                ),
            )
        }
        triggerManualInputRequiredAlert(
            parsedDraft = parsedDraft,
            reasons = reasons,
        )
        return true
    }

    private fun maybeRequestManualConfirmation(
        signature: String,
        parsedDraft: ParsedOrderDraft,
        reasons: List<String>,
        capturedAtMillis: Long,
        isSecondary: Boolean,
    ): Boolean {
        if (
            signature == lastDismissedManualSignature &&
            capturedAtMillis - lastDismissedManualAtMillis < ManualDismissCooldownMillis
        ) {
            return true
        }
        if (
            signature == lastManualPromptSignature &&
            capturedAtMillis - lastManualPromptAtMillis < ManualPromptCooldownMillis
        ) {
            return true
        }
        if (pendingManualConfirmation?.signature == signature) {
            return true
        }

        val pending = PendingManualConfirmation(
            signature = signature,
            draft = parsedDraft,
            reasons = reasons,
            isSecondary = isSecondary,
            requestedAtMillis = capturedAtMillis,
        )
        val isSpecialOrder = pending.isSpecialOrderManualReview()
        pendingManualConfirmation = pending
        lastManualPromptSignature = signature
        lastManualPromptAtMillis = capturedAtMillis

        Log.d(
            LogTag,
            "manual-overlay show: isSecondary=$isSecondary destination=${parsedDraft.effectiveDestination() ?: "미확인"} price=${parsedDraft.price ?: 0} reasons=${reasons.joinToString(" | ")}",
        )
        logOperation(
            eventType = if (isSpecialOrder) "special_manual_review" else "manual_review",
            status = when {
                isSecondary && isSpecialOrder -> "tracked-additional-special-manual-review"
                isSecondary -> "tracked-additional-manual-review"
                isSpecialOrder -> "special-manual-review"
                else -> "manual-review"
            },
            parsedDraft = parsedDraft,
            mode = if (isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = "manual_overlay",
            confirmed = false,
            manualReviewRequired = true,
            reason = reasons.joinToString(" · "),
            screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
            rawContext = reasons.joinToString(" · "),
            createdAtMillis = capturedAtMillis,
        )

        mainHandler.post {
            showManualDecisionOverlay(pending)
        }
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft.effectiveRouteText() ?: "조건 일치 오더",
                    originSummary = parsedDraft.effectiveOrigin() ?: parsedDraft.requesterLocation ?: "미확인",
                    destinationSummary = parsedDraft.effectiveDestination() ?: "미확인",
                    price = parsedDraft.price ?: 0,
                    status = when {
                        isSecondary && isSpecialOrder -> "tracked-additional-special-manual-review"
                        isSecondary -> "tracked-additional-manual-review"
                        isSpecialOrder -> "special-manual-review"
                        else -> "manual-review"
                    },
                    failureReason = (
                        listOfNotNull(parsedDraft.farePerKmDiagnostic()) +
                            reasons
                        ).distinct().joinToString(" · "),
                ),
            )
        }
        triggerManualReviewAlert(pending)
        return true
    }

    private fun maybeClearStaleManualConfirmation(
        packageName: String,
        parsedDraft: ParsedOrderDraft?,
    ) {
        val pending = pendingManualConfirmation ?: return
        val clearReason = when {
            !isSupportedInsungPackage(packageName) -> "package-changed:$packageName"
            parsedDraft == null -> "draft-missing"
            !parsedDraft.isConfirmableDetailScreen -> "not-confirmable-detail"
            !isSamePendingOrder(pending, parsedDraft) -> {
                "detail-mismatch: pending=${pending.draft.effectiveDestination() ?: "미확인"} current=${parsedDraft.effectiveDestination() ?: "미확인"}"
            }
            else -> null
        } ?: return

        Log.d(LogTag, "manual-overlay cleared: $clearReason")
        clearPendingManualConfirmation()
    }

    private fun handleSuccessfulConfirmation(
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
        reasons: List<String>,
        isSecondary: Boolean,
        confirmedByUser: Boolean,
        confirmClickDiagnostic: String?,
        confirmSource: String? = null,
    ) {
        val actionSignature = buildOrderSignature(parsedDraft)
        lastAutoConfirmSignature = actionSignature
        lastAutoConfirmAtMillis = capturedAtMillis
        autoEntryListPausedUntilMillis = maxOf(
            autoEntryListPausedUntilMillis,
            capturedAtMillis + PostConfirmAutoEntryPauseMillis,
        )
        markOrderProcessingLocked(actionSignature, capturedAtMillis)
        val autoEntry = activePendingAutoEntry(capturedAtMillis)
        val autoEntryReason = autoEntry?.let {
            "${it.mode.toKoreanLabel()} 자동진입: ${it.region.toKoreanLabel()} · ${it.listDistanceDiagnostic()} · ${it.clickDiagnostic}"
        }
        registerAutoEntryAttemptIfNeeded(
            parsedDraft = parsedDraft,
            capturedAtMillis = capturedAtMillis,
        )
        val attempt = PendingConfirmAttempt(
            signature = actionSignature,
            draft = parsedDraft,
            reasons = reasons,
            isSecondary = isSecondary,
            confirmedByUser = confirmedByUser,
            attemptedAtMillis = capturedAtMillis,
            autoEntryMode = autoEntry?.mode,
            autoEntryRegion = autoEntry?.region,
            autoEntryReason = autoEntryReason,
            autoEntryClickDiagnostic = autoEntry?.clickDiagnostic,
            confirmClickDiagnostic = confirmClickDiagnostic,
            confirmSource = confirmSource ?: if (confirmedByUser) {
                "manual_overlay"
            } else {
                autoEntry?.let { "order_list_auto_entry" } ?: "direct_detail"
            },
        )
        pendingConfirmAttempts[actionSignature] = attempt
        trimPendingConfirmAttempts(capturedAtMillis)

        val attemptReason = (
            listOfNotNull(
                "확정 버튼 클릭 시도",
                "성공 검증 전",
                autoEntryReason,
            ) + reasons
        ).distinct().joinToString(" · ")
        val attemptDiagnostic = buildPendingConfirmAttemptDiagnostic(
            attempt = attempt,
            failureType = "확정버튼클릭시도",
            observedAtMillis = capturedAtMillis,
            extra = listOf(attemptReason),
        )
        logOperation(
            eventType = "order_confirm_click_attempted",
            status = confirmAttemptStatus(confirmedByUser = confirmedByUser, isSecondary = isSecondary),
            parsedDraft = parsedDraft,
            mode = autoEntry?.mode ?: if (isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = attempt.confirmSource,
            region = autoEntry?.region,
            confirmed = false,
            reason = attemptReason,
            clickDiagnostic = buildCombinedClickDiagnostic(autoEntry?.clickDiagnostic, confirmClickDiagnostic),
            screenSummary = parsedDraft.effectiveRouteText() ?: parsedDraft.detailNote,
            rawContext = attemptDiagnostic,
            createdAtMillis = capturedAtMillis,
        )

        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = parsedDraft.effectiveRouteText() ?: "인성 상세 오더",
                    originSummary = parsedDraft.effectiveOrigin() ?: parsedDraft.requesterLocation ?: "미확인",
                    destinationSummary = parsedDraft.effectiveDestination() ?: "미확인",
                    price = parsedDraft.price ?: 0,
                    status = confirmAttemptStatus(confirmedByUser = confirmedByUser, isSecondary = isSecondary),
                    failureReason = (
                        listOfNotNull("확정 버튼 클릭 시도", "성공 검증 전", autoEntryReason, parsedDraft.farePerKmDiagnostic()) +
                            reasons
                        ).distinct().joinToString(" · "),
                ),
            )
        }
        if (confirmedByUser) {
            clearPendingManualConfirmation()
        }
        if (autoEntry != null) {
            pendingAutoListEntry = null
        }
        scheduleConfirmAttemptVerificationTimeout(actionSignature, capturedAtMillis)
    }

    private fun scheduleConfirmAttemptVerificationTimeout(
        signature: String,
        attemptedAtMillis: Long,
    ) {
        mainHandler.postDelayed(
            {
                val attempt = pendingConfirmAttempts[signature] ?: return@postDelayed
                if (attempt.attemptedAtMillis != attemptedAtMillis) return@postDelayed
                pendingConfirmAttempts.remove(signature)
                val reason = "확정 클릭 후 ${ConfirmAttemptVerificationWindowMillis.formatElapsedMinutes()} 동안 인성앱 확정 상태를 확인하지 못했습니다. 자동확정 성공으로 집계하지 않습니다."
                val observedAtMillis = System.currentTimeMillis()
                logOperation(
                    eventType = "order_confirm_unverified",
                    status = confirmUnverifiedStatus(
                        confirmedByUser = attempt.confirmedByUser,
                        isSecondary = attempt.isSecondary,
                    ),
                    parsedDraft = attempt.draft,
                    mode = attempt.autoEntryMode ?: if (attempt.isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
                    source = attempt.confirmSource,
                    region = attempt.autoEntryRegion,
                    confirmed = false,
                    reason = reason,
                    clickDiagnostic = buildCombinedClickDiagnostic(
                        attempt.autoEntryClickDiagnostic,
                        attempt.confirmClickDiagnostic,
                    ),
                    screenSummary = attempt.draft.effectiveRouteText() ?: attempt.draft.detailNote,
                    rawContext = buildPendingConfirmAttemptDiagnostic(
                        attempt = attempt,
                        failureType = "확정검증시간초과",
                        observedAtMillis = observedAtMillis,
                        extra = listOf(reason),
                    ),
                    createdAtMillis = observedAtMillis,
                )
            },
            ConfirmAttemptVerificationWindowMillis,
        )
    }

    private fun trimPendingConfirmAttempts(nowMillis: Long) {
        val expiredKeys = pendingConfirmAttempts
            .filterValues { attempt ->
                nowMillis - attempt.attemptedAtMillis > ConfirmAttemptVerificationWindowMillis
            }
            .keys
            .toList()
        expiredKeys.forEach(pendingConfirmAttempts::remove)
        while (pendingConfirmAttempts.size > MaxPendingConfirmAttempts) {
            val firstKey = pendingConfirmAttempts.keys.firstOrNull() ?: break
            pendingConfirmAttempts.remove(firstKey)
        }
    }

    private fun confirmAttemptStatus(
        confirmedByUser: Boolean,
        isSecondary: Boolean,
    ): String = when {
        confirmedByUser && isSecondary -> "tracked-additional-manual-confirm-click-attempted"
        confirmedByUser -> "primary-manual-confirm-click-attempted"
        isSecondary -> "tracked-additional-auto-confirm-click-attempted"
        else -> "primary-auto-confirm-click-attempted"
    }

    private fun confirmUnverifiedStatus(
        confirmedByUser: Boolean,
        isSecondary: Boolean,
    ): String = when {
        confirmedByUser && isSecondary -> "tracked-additional-manual-confirm-unverified"
        confirmedByUser -> "primary-manual-confirm-unverified"
        isSecondary -> "tracked-additional-auto-confirm-unverified"
        else -> "primary-auto-confirm-unverified"
    }

    private fun verifiedConfirmStatus(
        confirmedByUser: Boolean,
        isSecondary: Boolean,
    ): String = when {
        confirmedByUser && isSecondary -> "tracked-additional-manual-confirmed"
        confirmedByUser -> "primary-manual-confirmed"
        isSecondary -> "tracked-additional-auto-confirmed"
        else -> "primary-auto-confirmed"
    }

    private fun updateAddressCopyOverlay(
        packageName: String,
        parsedDraft: ParsedOrderDraft?,
    ) {
        val address = parsedDraft?.bestDetailedAddressForClipboard()
        val hideReason = when {
            !isSupportedInsungPackage(packageName) -> "지원하지 않는 패키지:$packageName"
            parsedDraft == null -> "파싱 결과 없음"
            address.isNullOrBlank() -> "상세주소 후보 없음"
            !parsedDraft.isManualAddressDetailScreen() -> parsedDraft.addressCopyOverlayBlockReason()
            else -> null
        }

        if (hideReason != null) {
            logAddressCopyOverlayState(
                "hide",
                "$hideReason detail=${parsedDraft?.detailNote.orEmpty().take(48)}",
            )
            lastAddressCopyCandidate = null
            removeAddressCopyOverlay()
            return
        }

        val safeAddress = address.orEmpty()
        logAddressCopyOverlayState("show", safeAddress.take(64))
        lastAddressCopyCandidate = safeAddress
        ensureAddressCopyOverlay().text = buildAddressCopyOverlayText(safeAddress, copied = false)
    }

    private fun clearAddressCopyOverlay(reason: String) {
        if (addressCopyOverlayView == null && lastAddressCopyCandidate == null) return
        logAddressCopyOverlayState("hide", reason)
        lastAddressCopyCandidate = null
        removeAddressCopyOverlay()
    }

    private fun logAddressCopyOverlayState(
        state: String,
        detail: String,
    ) {
        val signature = "$state|$detail"
        if (signature == lastAddressCopyOverlayLogSignature) return
        lastAddressCopyOverlayLogSignature = signature
        Log.d(LogTag, "address-copy-overlay $state: $detail")
    }

    private fun ensureAddressCopyOverlay(): TextView {
        addressCopyOverlayView?.let { return it }

        val overlayView = TextView(this).apply {
            text = "상세주소 복사"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            gravity = Gravity.CENTER
            maxLines = 3
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(0xEE111827.toInt())
                setStroke(dp(1), 0xFF34D399.toInt())
            }
            elevation = dp(8).toFloat()
            setOnClickListener {
                copyCurrentDetailedAddressToClipboard()
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            dp(260),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(AddressCopyOverlayBottomOffsetDp)
        }

        windowManager.addView(overlayView, layoutParams)
        addressCopyOverlayView = overlayView
        return overlayView
    }

    private fun copyCurrentDetailedAddressToClipboard() {
        val address = lastAddressCopyCandidate?.takeIf { it.isNotBlank() } ?: return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CatchPro 상세주소", address))
        addressCopyOverlayView?.text = buildAddressCopyOverlayText(address, copied = true)
    }

    private fun removeAddressCopyOverlay() {
        val overlayView = addressCopyOverlayView ?: return
        runCatching { windowManager.removeView(overlayView) }
        addressCopyOverlayView = null
    }

    private fun maybeHandleInsungConfirmFailureText(
        packageName: String,
        texts: List<String>,
        capturedAtMillis: Long,
        detectionSource: String,
    ): Boolean {
        if (packageName.isNotBlank() && !isSupportedInsungPackage(packageName)) return false
        if (texts.isEmpty()) return false
        val summary = texts
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (summary.isBlank()) return false
        val failureText = InsungConfirmFailureRegex.find(summary)?.value ?: return false
        trimPendingConfirmAttempts(capturedAtMillis)
        val attempt = pendingConfirmAttempts.values
            .filter { pending ->
                capturedAtMillis - pending.attemptedAtMillis in 0..ConfirmAttemptVerificationWindowMillis
            }
            .maxByOrNull { pending -> pending.attemptedAtMillis }
        if (attempt == null) {
            logInsungConfirmFailureTextWithoutPending(
                packageName = packageName,
                failureText = failureText,
                screenText = summary,
                capturedAtMillis = capturedAtMillis,
                detectionSource = detectionSource,
            )
            return true
        }

        pendingConfirmAttempts.remove(attempt.signature)
        val reason = "인성앱 확정 거절/경합 문구 감지: $failureText"
        logOperation(
            eventType = "order_confirm_rejected_by_insung",
            status = "order-confirm-rejected-by-insung",
            parsedDraft = attempt.draft,
            orderSignature = attempt.signature,
            mode = attempt.autoEntryMode ?: if (attempt.isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = attempt.confirmSource,
            region = attempt.autoEntryRegion,
            confirmed = false,
            reason = reason,
            clickDiagnostic = buildCombinedClickDiagnostic(
                attempt.autoEntryClickDiagnostic,
                attempt.confirmClickDiagnostic,
            ),
            screenSummary = attempt.draft.effectiveRouteText() ?: attempt.draft.detailNote,
            rawContext = buildPendingConfirmAttemptDiagnostic(
                attempt = attempt,
                failureType = "인성실패문구:$failureText",
                observedAtMillis = capturedAtMillis,
                detectionSource = detectionSource,
                screenText = "$packageName · $summary",
                extra = listOf(
                    reason,
                    "같은 오더가 다시 보일 수 있으므로 장시간 자동재시도 차단은 적용하지 않습니다.",
                ),
            ),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = attempt.draft.effectiveRouteText() ?: "인성 상세 오더",
                    originSummary = attempt.draft.effectiveOrigin() ?: attempt.draft.requesterLocation ?: "미확인",
                    destinationSummary = attempt.draft.effectiveDestination() ?: "미확인",
                    price = attempt.draft.price ?: 0,
                    status = "order-confirm-rejected-by-insung",
                    failureReason = "$reason · $detectionSource",
                ),
            )
        }
        return true
    }

    private fun logInsungConfirmFailureTextWithoutPending(
        packageName: String,
        failureText: String,
        screenText: String,
        capturedAtMillis: Long,
        detectionSource: String,
    ) {
        val signature = "$failureText|${screenText.take(120)}"
        if (
            signature == lastInsungConfirmFailureTextSignature &&
            capturedAtMillis - lastInsungConfirmFailureTextAtMillis < ConfirmFailureTextLogCooldownMillis
        ) {
            return
        }
        lastInsungConfirmFailureTextSignature = signature
        lastInsungConfirmFailureTextAtMillis = capturedAtMillis
        val reason = "인성앱 확정 거절/경합 문구 감지(대기 중 확정시도 없음): $failureText"
        logOperation(
            eventType = "order_confirm_failure_text_observed",
            status = "order-confirm-failure-text-observed",
            source = "insung_failure_text",
            confirmed = false,
            reason = reason,
            screenSummary = failureText,
            rawContext = buildList {
                add("확정로그유형=인성실패문구대기없음")
                add("관측시각=${capturedAtMillis.toOperationClockText()}")
                add("package=$packageName")
                add("detection=$detectionSource")
                add("failureText=${failureText.operationContextText(120)}")
                add("screen=${screenText.operationContextText(720)}")
            }.joinToString(" · "),
            createdAtMillis = capturedAtMillis,
        )
    }

    private fun maybeHandleAutoConfirmVerifiedStatus(
        parsedDraft: ParsedOrderDraft?,
        capturedAtMillis: Long,
    ) {
        val draft = parsedDraft ?: return
        if (!draft.isActiveDriveScreen) return
        trimPendingConfirmAttempts(capturedAtMillis)
        val pendingAttempt = pendingConfirmAttempts.values
            .firstOrNull { attempt ->
                capturedAtMillis - attempt.attemptedAtMillis in 0..ConfirmAttemptVerificationWindowMillis &&
                    isSameTrackedOrder(attempt.toTrackedOrder(), draft, capturedAtMillis)
            }
        if (pendingAttempt != null) {
            promoteVerifiedConfirmAttempt(
                attempt = pendingAttempt,
                verifiedDraft = draft,
                capturedAtMillis = capturedAtMillis,
            )
            return
        }

        val trackedOrder = listOfNotNull(
            trackedAutoConfirmedOrder,
            trackingReferenceOrder,
            trackingAdditionalOrder,
        )
            .distinctBy { it.signature }
            .firstOrNull { isSameTrackedOrder(it, draft, capturedAtMillis) }
            ?: return
        if (autoConfirmVerificationLoggedSignatures.containsKey(trackedOrder.signature)) return

        autoConfirmVerificationLoggedSignatures[trackedOrder.signature] = capturedAtMillis
        while (autoConfirmVerificationLoggedSignatures.size > 100) {
            val firstKey = autoConfirmVerificationLoggedSignatures.keys.firstOrNull() ?: break
            autoConfirmVerificationLoggedSignatures.remove(firstKey)
        }

        val verifiedStatus = when {
            trackedOrder.confirmedByUser && trackedOrder.isSecondary -> "tracked-additional-manual-confirmed"
            trackedOrder.confirmedByUser -> "primary-manual-confirmed"
            trackedOrder.isSecondary -> "tracked-additional-auto-confirmed"
            else -> "primary-auto-confirmed"
        }
        val verifyReason = "인성 오더 상태 '${draft.statusText ?: "수행중"}' 확인으로 확정 성공 검증"

        logOperation(
            eventType = "order_confirmed",
            status = verifiedStatus,
            parsedDraft = draft,
            orderSignature = trackedOrder.signature,
            mode = if (trackedOrder.isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = "insung-status-verification",
            confirmed = true,
            reason = verifyReason,
            screenSummary = draft.effectiveRouteText() ?: draft.detailNote ?: trackedOrder.orderTitle,
            rawContext = buildList {
                add(verifyReason)
                add("tracked=${trackedOrder.orderTitle.shortDiagnosticText()}")
                add("status=${draft.statusText ?: "미확인"}")
            }.joinToString(" · "),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = draft.effectiveRouteText() ?: trackedOrder.orderTitle,
                    originSummary = draft.effectiveOrigin() ?: trackedOrder.origin,
                    destinationSummary = draft.effectiveDestination() ?: trackedOrder.destination,
                    price = draft.price ?: trackedOrder.price,
                    status = verifiedStatus,
                    failureReason = verifyReason,
                ),
            )
        }
    }

    private fun promoteVerifiedConfirmAttempt(
        attempt: PendingConfirmAttempt,
        verifiedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ) {
        pendingConfirmAttempts.remove(attempt.signature)
        if (autoConfirmVerificationLoggedSignatures.containsKey(attempt.signature)) return

        autoConfirmVerificationLoggedSignatures[attempt.signature] = capturedAtMillis
        while (autoConfirmVerificationLoggedSignatures.size > 100) {
            val firstKey = autoConfirmVerificationLoggedSignatures.keys.firstOrNull() ?: break
            autoConfirmVerificationLoggedSignatures.remove(firstKey)
        }

        registerAutoEntryConfirmationIfNeeded(
            parsedDraft = verifiedDraft,
            capturedAtMillis = capturedAtMillis,
            autoEntryMode = attempt.autoEntryMode,
        )
        val confirmedOrder = attempt.toTrackedOrder(confirmedAtMillis = capturedAtMillis)
        trackedAutoConfirmedOrder = confirmedOrder
        if (!attempt.isSecondary) {
            navigationOrderSlotBySignature[confirmedOrder.signature] = 0
        } else {
            trackingAdditionalOrder = confirmedOrder
            trackingAdditionalDropoffCompleted = false
            trackingAdditionalConfirmedCount += 1
            navigationOrderSlotBySignature.getOrPut(confirmedOrder.signature) {
                nextAdditionalNavigationOrderSlotIndex()
            }
        }

        val verifiedStatus = verifiedConfirmStatus(
            confirmedByUser = attempt.confirmedByUser,
            isSecondary = attempt.isSecondary,
        )
        val verifyReason = "인성 오더 상태 '${verifiedDraft.statusText ?: "수행중"}' 확인으로 확정 성공 검증"

        logOperation(
            eventType = "order_confirmed",
            status = verifiedStatus,
            parsedDraft = verifiedDraft,
            orderSignature = attempt.signature,
            mode = attempt.autoEntryMode ?: if (attempt.isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = "insung-status-verification",
            confirmed = true,
            reason = verifyReason,
            screenSummary = verifiedDraft.effectiveRouteText() ?: verifiedDraft.detailNote ?: attempt.draft.effectiveRouteText(),
            rawContext = buildList {
                add(verifyReason)
                add("attempt=${attempt.draft.effectiveRouteText()?.shortDiagnosticText() ?: attempt.signature.shortDiagnosticText()}")
                add("status=${verifiedDraft.statusText ?: "미확인"}")
                attempt.autoEntryReason?.let { add(it) }
            }.joinToString(" · "),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            if (!attempt.isSecondary) {
                activeSettings = activeSettings.copy(activeDriveDestinationText = "")
                settingsRepository.setActiveDriveDestinationText("")
            }
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = verifiedDraft.effectiveRouteText() ?: attempt.draft.effectiveRouteText() ?: "인성 상세 오더",
                    originSummary = verifiedDraft.effectiveOrigin() ?: attempt.draft.effectiveOrigin() ?: attempt.draft.requesterLocation ?: "미확인",
                    destinationSummary = verifiedDraft.effectiveDestination() ?: attempt.draft.effectiveDestination() ?: "미확인",
                    price = verifiedDraft.price ?: attempt.draft.price ?: 0,
                    status = verifiedStatus,
                    failureReason = verifyReason,
                ),
            )
            if (attempt.isSecondary) {
                enqueueTmapQueueForDraft(
                    orderSignature = attempt.signature,
                    sourceType = "tracked-additional",
                    parsedDraft = verifiedDraft,
                    fallbackTitle = attempt.draft.effectiveRouteText() ?: "인성 상세 오더",
                )
            } else {
                tmapQueueRepository.enqueueConfirmedOrder(
                    orderSignature = attempt.signature,
                    sourceType = "primary",
                    orderTitle = "기준 확정 오더",
                    pickupAddress = null,
                    dropoffAddress = null,
                )
            }
        }
        triggerConfirmedAlert(
            parsedDraft = verifiedDraft,
            isSecondary = attempt.isSecondary,
            confirmedByUser = attempt.confirmedByUser,
        )
    }


    private fun maybeHandleAutoCancelledClick(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        packageName: String,
        capturedAtMillis: Long,
    ) {
        if (!isSupportedInsungPackage(packageName)) return
        val trackedOrder = trackedAutoConfirmedOrder ?: return

        if (event.isCancelButtonClick()) {
            Log.d(LogTag, "auto-cancel pending: cancel button clicked, waiting for confirmation")
            return
        }
        if (!event.isCancelConfirmationYesClick(root)) return

        val parsedDraft = KoreanOrderDraftParser.parseInsungQuick(
            root = root,
            packageName = packageName,
        )
        if (!isSameTrackedOrder(trackedOrder, parsedDraft, capturedAtMillis)) return

        markAutoOrderCancelled(
            trackedOrder = trackedOrder,
            capturedAtMillis = capturedAtMillis,
            reason = "자동확정 후 취소 확인 예 버튼 감지",
        )
    }

    private fun maybeHandleCancelledStatus(
        parsedDraft: ParsedOrderDraft?,
        capturedAtMillis: Long,
    ) {
        val draft = parsedDraft ?: return
        val trackedOrder = trackedAutoConfirmedOrder ?: return
        if (!draft.isCancelledStatusScreen) return
        if (!isSameTrackedOrder(trackedOrder, draft, capturedAtMillis)) return

        markAutoOrderCancelled(
            trackedOrder = trackedOrder,
            capturedAtMillis = capturedAtMillis,
            reason = "오더 상태 화면에서 취소 상태 감지",
        )
    }

    private fun markAutoOrderCancelled(
        trackedOrder: TrackedAutoConfirmedOrder,
        capturedAtMillis: Long,
        reason: String,
    ) {
        lastCancelledAutoConfirmSignature = trackedOrder.signature
        lastCancelledAutoConfirmAtMillis = capturedAtMillis
        if (trackedAutoConfirmedOrder?.signature == trackedOrder.signature) {
            trackedAutoConfirmedOrder = null
        }
        val wasTrackingReference = trackingReferenceOrder?.signature == trackedOrder.signature
        val cancelledOrderSlotIndex = navigationOrderSlotBySignature[trackedOrder.signature]
        if (wasTrackingReference) {
            trackingReferenceOrder = null
            trackingReferencePickupCompleted = false
            trackingReferencePickupCompletedAtMillis = 0L
            trackingReferenceDropoffCompleted = false
            trackingAdditionalOrder = null
            trackingAdditionalDropoffCompleted = false
            trackingAdditionalConfirmedCount = 0
            navigationOrderSlotBySignature.clear()
            if (!PreserveRouteAddressesOnCancelForVerification) {
                activeSettings = activeSettings.copy(
                    activeDriveDestinationText = "",
                    tmapManualRouteAddressesText = activeSettings.tmapManualRouteAddressesText.clearManualRouteAddressOrderSlot(0),
                )
            }
        } else {
            if (trackingAdditionalOrder?.signature == trackedOrder.signature) {
                trackingAdditionalOrder = null
                trackingAdditionalDropoffCompleted = false
                trackingAdditionalConfirmedCount = 0
            }
            navigationOrderSlotBySignature.remove(trackedOrder.signature)
            if (!PreserveRouteAddressesOnCancelForVerification) {
                cancelledOrderSlotIndex?.let { slotIndex ->
                    activeSettings = activeSettings.copy(
                        tmapManualRouteAddressesText = activeSettings.tmapManualRouteAddressesText.clearManualRouteAddressOrderSlot(slotIndex),
                    )
                }
            }
        }
        val cancelledStatus = if (trackedOrder.isSecondary) {
            "tracked-additional-cancelled"
        } else {
            "auto-cancelled"
        }
        logOperation(
            eventType = "auto_order_cancelled",
            status = cancelledStatus,
            orderSignature = trackedOrder.signature,
            mode = if (trackedOrder.isSecondary) AutoEntryMode.Secondary else AutoEntryMode.Primary,
            source = "insung_cancel",
            confirmed = false,
            reason = buildList {
                add(reason)
                if (PreserveRouteAddressesOnCancelForVerification) {
                    add("저장 확인용으로 TMAP 상세주소/오더추적 기준주소는 삭제하지 않습니다.")
                } else {
                    add(if (wasTrackingReference) "기준오더 취소로 추적 기준/TMAP 기준주소 초기화" else "추가오더 취소로 해당 TMAP 주소칸 초기화")
                }
                cancelledOrderSlotIndex?.let { add("tmapOrderSlot=${it + 1}") }
                add("additionalConfirmed=$trackingAdditionalConfirmedCount/$TrackingAdditionalAutoConfirmLimit")
            }.joinToString(" · "),
            screenSummary = trackedOrder.orderTitle,
            rawContext = listOf(
                "origin=${trackedOrder.origin}",
                "destination=${trackedOrder.destination}",
                "price=${trackedOrder.price}",
                "isSecondary=${trackedOrder.isSecondary}",
                "wasTrackingReference=$wasTrackingReference",
            ).joinToString(" | "),
            createdAtMillis = capturedAtMillis,
        )
        serviceScope.launch {
            tmapQueueRepository.markCancelled(trackedOrder.signature)
            if (!PreserveRouteAddressesOnCancelForVerification) {
                if (wasTrackingReference) {
                    settingsRepository.clearTmapManualRouteOrderSlot(
                        orderSlotIndex = 0,
                        clearActiveDriveDestination = true,
                    )
                } else {
                    cancelledOrderSlotIndex?.let { slotIndex ->
                        settingsRepository.clearTmapManualRouteOrderSlot(
                            orderSlotIndex = slotIndex,
                            clearActiveDriveDestination = false,
                        )
                    }
                }
            }
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = trackedOrder.orderTitle,
                    originSummary = trackedOrder.origin,
                    destinationSummary = trackedOrder.destination,
                    price = trackedOrder.price,
                    status = cancelledStatus,
                    failureReason = reason,
                ),
            )
        }
    }

    private fun updateKeepScreenOnOverlay(enabled: Boolean) {
        if (enabled) {
            ensureKeepScreenOnOverlay()
        } else {
            removeKeepScreenOnOverlay()
        }
    }

    private fun ensureKeepScreenOnOverlay(): View {
        keepScreenOnOverlayView?.let { return it }

        val overlayView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            alpha = 0f
        }
        val layoutParams = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        windowManager.addView(overlayView, layoutParams)
        keepScreenOnOverlayView = overlayView
        return overlayView
    }

    private fun removeKeepScreenOnOverlay() {
        val overlayView = keepScreenOnOverlayView ?: return
        runCatching { windowManager.removeView(overlayView) }
        keepScreenOnOverlayView = null
    }

    private fun updateRunModeOverlay(
        visible: Boolean,
        settings: AppSettings = activeSettings,
    ) {
        val signature = buildRunModeOverlaySignature(visible = visible, settings = settings)
        if (
            signature == lastRunModeOverlaySignature &&
            ((!visible && runModeOverlay == null) || (visible && runModeOverlay != null))
        ) {
            return
        }
        lastRunModeOverlaySignature = signature
        runModeOverlayVisible = visible
        if (!visible) {
            removeRunModeOverlay()
            return
        }

        val overlay = ensureRunModeOverlay()
        val autoConfirmAvailable = CatchProFeatureGate.autoConfirmAvailable(this)
        val autoEntryAvailable = CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(this)
        val awsSyncAvailable = settings.routeAddressCloudSyncFeatureAvailable
        val autoConfirmOn = autoConfirmAvailable && settings.primaryAutoConfirmEnabled
        val autoEntryOn = autoEntryAvailable && settings.primaryOrderListAutoEntryEnabled
        val awsSyncOn = awsSyncAvailable && settings.routeAddressCloudSyncEnabled
        val driveModeOn = autoConfirmOn && autoEntryOn

        overlay.statusView.visibility = View.GONE
        overlay.autoConfirmButton.isEnabled = autoConfirmAvailable
        overlay.autoEntryButton.visibility = View.GONE
        overlay.awsButton.visibility = if (awsSyncAvailable) View.VISIBLE else View.GONE
        overlay.enableDriveModeButton.visibility = View.GONE

        if (autoConfirmAvailable) {
            tintRunModeButton(
                button = overlay.autoConfirmButton,
                enabled = autoConfirmOn,
                onText = "자동확정 ON",
                offText = "자동확정 OFF",
            )
        } else {
            tintDisabledRunModeButton(
                button = overlay.autoConfirmButton,
                text = "자동확정 PRO",
            )
        }
        if (autoEntryAvailable) {
            tintRunModeButton(
                button = overlay.autoEntryButton,
                enabled = autoEntryOn,
                onText = "자동상세 ON",
                offText = "자동상세 OFF",
            )
        }
        if (awsSyncAvailable) {
            tintRunModeButton(
                button = overlay.awsButton,
                enabled = awsSyncOn,
                onText = "AWS ON",
                offText = "AWS OFF",
            )
        }
        overlay.enableDriveModeButton.backgroundTintList = ColorStateList.valueOf(0xFF0EA5E9.toInt())
        overlay.enableDriveModeButton.setTextColor(Color.WHITE)
        overlay.container.visibility = View.VISIBLE
    }

    private fun buildRunModeOverlaySignature(
        visible: Boolean,
        settings: AppSettings,
    ): String {
        return listOf(
            visible,
            settings.primaryAutoConfirmEnabled,
            settings.primaryOrderListAutoEntryEnabled,
            settings.routeAddressCloudSyncEnabled,
            BuildConfig.CATCHPRO_EDITION,
            CatchProFeatureGate.autoConfirmAvailable(this),
            CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(this),
            settings.routeAddressCloudSyncFeatureAvailable,
        ).joinToString("|")
    }

    private fun ensureRunModeOverlay(): RunModeOverlayViews {
        runModeOverlay?.let { return it }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(0xE6111727.toInt())
                setStroke(dp(1), 0xFF38BDF8.toInt())
            }
            elevation = dp(10).toFloat()
        }

        val statusView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }
        val autoConfirmButton = runModeButton {
            if (!CatchProFeatureGate.autoConfirmAvailable(this)) return@runModeButton
            serviceScope.launch {
                settingsRepository.setPrimaryAutoConfirmEnabled(!activeSettings.primaryAutoConfirmEnabled)
            }
        }
        val autoEntryButton = runModeButton {
            if (!CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(this)) return@runModeButton
            serviceScope.launch {
                settingsRepository.setPrimaryOrderListAutoEntryEnabled(!activeSettings.primaryOrderListAutoEntryEnabled)
            }
        }
        val awsButton = runModeButton {
            if (!activeSettings.routeAddressCloudSyncFeatureAvailable) return@runModeButton
            serviceScope.launch {
                settingsRepository.setRouteAddressCloudSyncEnabled(!activeSettings.routeAddressCloudSyncEnabled)
            }
        }.apply {
            minWidth = dp(58)
        }
        val enableDriveModeButton = runModeButton {
            if (
                !CatchProFeatureGate.autoConfirmAvailable(this) ||
                !CatchProFeatureGate.experimentalAutoDetailConfirmAvailable(this)
            ) {
                return@runModeButton
            }
            serviceScope.launch {
                settingsRepository.setPrimaryAutoConfirmEnabled(true)
                settingsRepository.setPrimaryOrderListAutoEntryEnabled(true)
            }
        }.apply {
            text = "운행 켜기"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        }

        buttonRow.addView(
            autoConfirmButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                marginEnd = dp(4)
            },
        )
        buttonRow.addView(
            autoEntryButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                marginEnd = dp(4)
            },
        )
        buttonRow.addView(
            awsButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                marginEnd = dp(4)
            },
        )
        buttonRow.addView(
            enableDriveModeButton,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)),
        )
        container.addView(buttonRow)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(6)
            y = dp(88)
        }

        windowManager.addView(container, layoutParams)
        return RunModeOverlayViews(
            container = container,
            statusView = statusView,
            autoConfirmButton = autoConfirmButton,
            autoEntryButton = autoEntryButton,
            awsButton = awsButton,
            enableDriveModeButton = enableDriveModeButton,
        ).also { runModeOverlay = it }
    }

    private fun runModeButton(onClick: () -> Unit): Button {
        return Button(this).apply {
            isAllCaps = false
            minWidth = dp(74)
            minHeight = dp(30)
            setPadding(dp(6), 0, dp(6), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setOnClickListener { onClick() }
        }
    }

    private fun tintRunModeButton(
        button: Button,
        enabled: Boolean,
        onText: String,
        offText: String,
    ) {
        button.text = if (enabled) onText else offText
        button.backgroundTintList = ColorStateList.valueOf(
            if (enabled) 0xFF059669.toInt() else 0xFFDC2626.toInt(),
        )
        button.setTextColor(Color.WHITE)
    }

    private fun tintDisabledRunModeButton(
        button: Button,
        text: String,
    ) {
        button.text = text
        button.backgroundTintList = ColorStateList.valueOf(0xFF6B7280.toInt())
        button.setTextColor(Color.WHITE)
    }

    private fun removeRunModeOverlay() {
        val overlay = runModeOverlay ?: return
        runCatching { windowManager.removeView(overlay.container) }
        runModeOverlay = null
    }

    private fun ensureManualDecisionOverlay(): ManualDecisionOverlayViews {
        manualDecisionOverlay?.let { return it }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(0xF112172A.toInt())
                setStroke(dp(1), 0xFF60A5FA.toInt())
            }
            elevation = dp(12).toFloat()
        }

        val titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
        }
        val bodyView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(12), 0, 0)
            gravity = Gravity.CENTER
        }
        val reasonView = TextView(this).apply {
            setTextColor(0xFFBFDBFE.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(10), 0, 0)
            gravity = Gravity.CENTER
            maxLines = 2
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }
        val skipButton = Button(this).apply {
            text = "보류"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            minHeight = dp(76)
            setOnClickListener { handleManualSkipClick() }
        }
        val confirmButton = Button(this).apply {
            text = "확정"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            minHeight = dp(76)
            setOnClickListener { handleManualConfirmClick() }
        }

        val spacing = dp(8)
        buttonRow.addView(
            skipButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = spacing / 2
            },
        )
        buttonRow.addView(
            confirmButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = spacing / 2
            },
        )

        container.addView(titleView)
        container.addView(bodyView)
        container.addView(reasonView)
        container.addView(buttonRow)

        val layoutParams = WindowManager.LayoutParams(
            resources.displayMetrics.widthPixels - dp(32),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }

        windowManager.addView(container, layoutParams)
        return ManualDecisionOverlayViews(
            container = container,
            titleView = titleView,
            bodyView = bodyView,
            reasonView = reasonView,
            confirmButton = confirmButton,
            skipButton = skipButton,
        ).also { manualDecisionOverlay = it }
    }

    private fun showManualDecisionOverlay(pending: PendingManualConfirmation) {
        val overlay = ensureManualDecisionOverlay()
        val isSpecialOrder = pending.isSpecialOrderManualReview()
        applyManualDecisionOverlayStyle(overlay, isSpecialOrder)
        overlay.titleView.text = if (isSpecialOrder) {
            "특수오더 확인"
        } else if (pending.isSecondary) {
            "추적 오더 조건 일치"
        } else {
            "기준 오더 조건 일치"
        }
        overlay.bodyView.text = buildString {
            if (isSpecialOrder) {
                append("자동확정 보류 · 직접 보고 결정\n")
            }
            append("도착지: ")
            append(pending.draft.effectiveDestination() ?: "미확인")
            append('\n')
            append("요금: ")
            append((pending.draft.price ?: 0).toString())
            append("원")
        }
        overlay.reasonView.text = pending.reasons.joinToString(
            separator = " · ",
            prefix = if (isSpecialOrder) "특수조건: " else "조건: ",
        )
        overlay.container.visibility = View.VISIBLE
    }

    private fun applyManualDecisionOverlayStyle(
        overlay: ManualDecisionOverlayViews,
        isSpecialOrder: Boolean,
    ) {
        val backgroundColor = if (isSpecialOrder) 0xF11C1917.toInt() else 0xF112172A.toInt()
        val strokeColor = if (isSpecialOrder) 0xFFF59E0B.toInt() else 0xFF60A5FA.toInt()
        val titleColor = if (isSpecialOrder) 0xFFFFF7ED.toInt() else Color.WHITE
        val reasonColor = if (isSpecialOrder) 0xFFFCD34D.toInt() else 0xFFBFDBFE.toInt()
        val confirmTint = if (isSpecialOrder) 0xFFF59E0B.toInt() else 0xFF2563EB.toInt()
        val skipTint = if (isSpecialOrder) 0xFF44403C.toInt() else 0xFF334155.toInt()

        overlay.container.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(if (isSpecialOrder) 12 else 24).toFloat()
            setColor(backgroundColor)
            setStroke(dp(if (isSpecialOrder) 3 else 1), strokeColor)
        }
        overlay.titleView.setTextColor(titleColor)
        overlay.reasonView.setTextColor(reasonColor)
        overlay.confirmButton.text = if (isSpecialOrder) "보고 확정" else "확정"
        overlay.skipButton.text = if (isSpecialOrder) "넘김" else "보류"
        overlay.confirmButton.backgroundTintList = ColorStateList.valueOf(confirmTint)
        overlay.skipButton.backgroundTintList = ColorStateList.valueOf(skipTint)
        overlay.confirmButton.setTextColor(Color.WHITE)
        overlay.skipButton.setTextColor(Color.WHITE)
    }

    private fun removeManualDecisionOverlay() {
        val overlay = manualDecisionOverlay ?: return
        runCatching { windowManager.removeView(overlay.container) }
        manualDecisionOverlay = null
    }

    private fun clearPendingManualConfirmation() {
        if (pendingManualConfirmation != null) {
            Log.d(LogTag, "manual-overlay hide")
        }
        pendingManualConfirmation = null
        mainHandler.post {
            manualDecisionOverlay?.container?.visibility = View.GONE
        }
    }

    private fun maybeHandleInsungConfirmButtonClickEvent(
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo,
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ): Boolean {
        if (!event.isConfirmButtonClick(root)) return false
        val signature = buildOrderSignature(parsedDraft)
        trimPendingConfirmAttempts(capturedAtMillis)
        val existingAttempt = pendingConfirmAttempts[signature]
        if (
            existingAttempt != null &&
            capturedAtMillis - existingAttempt.attemptedAtMillis in 0..ConfirmAttemptVerificationWindowMillis
        ) {
            return true
        }
        handleSuccessfulConfirmation(
            parsedDraft = parsedDraft,
            capturedAtMillis = capturedAtMillis,
            reasons = listOf("사용자 확정 버튼 직접 클릭 감지"),
            isSecondary = shouldUseSecondaryRulesForDraft(),
            confirmedByUser = true,
            confirmClickDiagnostic = "manualConfirmTap=${event.confirmButtonClickDiagnostic(root)}",
            confirmSource = "manual_confirm_button",
        )
        return true
    }

    private fun handleManualConfirmClick() {
        val pending = pendingManualConfirmation ?: return
        val root = rootInActiveWindow ?: run {
            markManualConfirmFailure(pending, "확정 대상 화면을 찾지 못했습니다.")
            return
        }
        val packageName = (root.packageName ?: "").toString()
        if (!isSupportedInsungPackage(packageName)) {
            markManualConfirmFailure(pending, "인성 상세 화면이 아닐 때 확정이 시도되었습니다.")
            return
        }
        val parsedDraft = KoreanOrderDraftParser.parseInsungQuick(
            root = root,
            packageName = packageName,
        )
        if (!isSamePendingOrder(pending, parsedDraft)) {
            markManualConfirmFailure(pending, "팝업을 띄운 오더와 현재 화면의 오더가 달라졌습니다.")
            return
        }

        val confirmNode = findAutoConfirmNode(root)
        val confirmClickAttempt = confirmNode?.clickSelfOrAncestorWithDiagnostics()
        if (confirmClickAttempt?.clicked != true) {
            markManualConfirmFailure(
                pending = pending,
                reason = "확정 버튼 클릭에 실패했습니다. ${confirmClickAttempt?.diagnostic ?: "confirmButton=not-found"}",
            )
            return
        }

        handleSuccessfulConfirmation(
            parsedDraft = parsedDraft,
            capturedAtMillis = System.currentTimeMillis(),
            reasons = pending.reasons,
            isSecondary = pending.isSecondary,
            confirmedByUser = true,
            confirmClickDiagnostic = "confirmButton=${confirmClickAttempt.diagnostic}",
        )
    }

    private fun handleManualSkipClick() {
        val pending = pendingManualConfirmation ?: return
        val now = System.currentTimeMillis()
        lastDismissedManualSignature = pending.signature
        lastDismissedManualAtMillis = now
        markOrderProcessingLocked(pending.signature, now)
        activePendingAutoEntry(now)?.let { autoEntry ->
            val detailSignature = buildAutoEntryDetailSignature(pending.draft)
            if (detailSignature.isNotBlank()) {
                autoEntryCheckedDetailSignatures[detailSignature] = now
            }
            lockAutoEntryListSignature(autoEntry, now, AutoEntryRejectedListLockMillis)
            pendingAutoListEntry = null
        }
        clearPendingManualConfirmation()
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = pending.draft.routeText ?: "수동확인 오더",
                    originSummary = pending.draft.effectiveOrigin() ?: pending.draft.requesterLocation ?: "미확인",
                    destinationSummary = pending.draft.effectiveDestination() ?: "미확인",
                    price = pending.draft.price ?: 0,
                    status = "manual-skipped",
                    failureReason = pending.reasons.joinToString(" · "),
                ),
            )
        }
    }

    private fun markManualConfirmFailure(
        pending: PendingManualConfirmation,
        reason: String,
    ) {
        rootInActiveWindow?.let { root ->
            saveDiagnosticCapture(
                root = root,
                packageName = (root.packageName ?: "").toString().ifBlank { InsungQuickPackage },
                tag = "MANUAL_CONFIRM_FAILED",
                detail = "$reason · ${pending.draft.effectiveDestination() ?: "미확인"} · ${pending.reasons.joinToString(" | ").shortDiagnosticText()}",
            )
        }
        clearPendingManualConfirmation()
        markOrderProcessingLocked(pending.signature, System.currentTimeMillis())
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = pending.draft.routeText ?: "수동확인 오더",
                    originSummary = pending.draft.effectiveOrigin() ?: pending.draft.requesterLocation ?: "미확인",
                    destinationSummary = pending.draft.effectiveDestination() ?: "미확인",
                    price = pending.draft.price ?: 0,
                    status = "manual-confirm-failed",
                    failureReason = reason,
                ),
            )
        }
    }

    private fun triggerManualReviewAlert(
        pending: PendingManualConfirmation,
    ) {
        if (!activeSettings.alertsEnabled) return
        val isSpecialOrder = pending.isSpecialOrderManualReview()
        if (activeSettings.vibrationEnabled) {
            vibrateAlert()
        }
        showNotification(
            title = when {
                isSpecialOrder -> "특수오더 확인"
                pending.isSecondary -> "추적 오더 조건 일치"
                else -> "기준 오더 조건 일치"
            },
            text = "${pending.draft.effectiveDestination() ?: "미확인"} · 직접 확인해 주세요.",
            notificationId = ManualReviewNotificationId,
        )
        if (activeSettings.voiceAlertsEnabled) {
            speakAlert(
                if (isSpecialOrder) {
                    "특수 오더입니다. 직접 확인해 주세요."
                } else if (pending.isSecondary) {
                    "추적 오더 조건이 맞는 주문이 있습니다. 팝업에서 확인해 주세요."
                } else {
                    "기준 오더 조건이 맞는 주문이 있습니다. 팝업에서 확인해 주세요."
                },
            )
        }
    }

    private fun PendingManualConfirmation.isSpecialOrderManualReview(): Boolean {
        return reasons.any { reason -> reason.contains("특수오더 수동확인") }
    }

    private fun triggerManualInputRequiredAlert(
        parsedDraft: ParsedOrderDraft,
        reasons: List<String>,
    ) {
        if (!activeSettings.alertsEnabled) return
        if (activeSettings.vibrationEnabled) {
            vibrateAlert()
        }
        val destination = parsedDraft.effectiveDestination() ?: "도착지 미확인"
        showNotification(
            title = "주소 입력 필요",
            text = "$destination · ${reasons.firstOrNull() ?: "전체 주소 확인이 필요합니다."}",
            notificationId = ManualReviewNotificationId,
        )
        if (activeSettings.voiceAlertsEnabled) {
            speakAlert("주소 확인이 필요합니다. 티맵 연결 탭에서 주소를 확인해 주세요.")
        }
    }

    private fun triggerConfirmedAlert(
        parsedDraft: ParsedOrderDraft,
        isSecondary: Boolean,
        confirmedByUser: Boolean,
    ) {
        if (!activeSettings.alertsEnabled) return
        if (activeSettings.vibrationEnabled) {
            vibrateAlert()
        }
        showNotification(
            title = when {
                confirmedByUser && isSecondary -> "추가 오더 수동 확정"
                confirmedByUser -> "메인 오더 수동 확정"
                isSecondary -> "추가 오더 자동 확정"
                else -> "메인 오더 자동 확정"
            },
            text = "${parsedDraft.effectiveDestination() ?: "미확인"} · ${parsedDraft.price ?: 0}원",
            notificationId = ConfirmedNotificationId,
        )
        if (activeSettings.voiceAlertsEnabled) {
            speakAlert(
                when {
                    confirmedByUser && isSecondary -> "추가 오더를 수동으로 확정했습니다. 티맵 연결 탭에서 상차지를 확인하세요."
                    confirmedByUser -> "메인 오더를 수동으로 확정했습니다. 티맵 연결 탭에서 상차지를 확인하세요."
                    isSecondary -> "추가 오더를 자동으로 확정했습니다. 티맵 연결 탭에서 상차지를 확인하세요."
                    else -> "메인 오더를 자동으로 확정했습니다. 티맵 연결 탭에서 상차지를 확인하세요."
                },
            )
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = notificationManager.getNotificationChannel(AlertChannelId)
        if (existing != null) return

        val channel = NotificationChannel(
            AlertChannelId,
            "CatchPro 알림",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "자동확정, 자동상세확정, 확정 결과 알림"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(
        title: String,
        text: String,
        notificationId: Int,
    ) {
        ensureNotificationChannel()
        val blockReason = notificationBlockReason()
        if (blockReason != null) {
            logAlertDeliveryFailure(
                channel = "notification",
                reason = blockReason,
                detail = title,
            )
            return
        }

        runCatching {
            val notification = android.app.Notification.Builder(this, AlertChannelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(android.app.Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            notificationManager.notify(notificationId, notification)
        }.onFailure { throwable ->
            logAlertDeliveryFailure(
                channel = "notification",
                reason = "알림 게시 실패",
                detail = "${throwable::class.java.simpleName}: ${throwable.message.orEmpty()}".take(MaxOperationReasonLength),
            )
        }
    }

    private fun notificationBlockReason(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return "POST_NOTIFICATIONS 권한 없음"
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            return "앱 알림 시스템 차단"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(AlertChannelId)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                return "CatchPro 알림 채널 차단"
            }
        }
        return null
    }

    private fun vibrateAlert() {
        runCatching {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 140, 60, 140),
                -1,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(effect)
                } else {
                    logAlertDeliveryFailure(
                        channel = "vibration",
                        reason = "진동 하드웨어 없음",
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(effect)
                } else {
                    logAlertDeliveryFailure(
                        channel = "vibration",
                        reason = "진동 하드웨어 없음",
                    )
                }
            }
        }.onFailure { throwable ->
            logAlertDeliveryFailure(
                channel = "vibration",
                reason = "진동 실행 실패",
                detail = "${throwable::class.java.simpleName}: ${throwable.message.orEmpty()}".take(MaxOperationReasonLength),
            )
        }
    }

    private fun updateAlertVoiceEngine(settings: AppSettings) {
        if (settings.alertsEnabled && settings.voiceAlertsEnabled) {
            ensureTextToSpeech()
        } else {
            shutdownTextToSpeech()
        }
    }

    private fun speakAlert(message: String) {
        mainHandler.post {
            ensureTextToSpeech()
            if (!textToSpeechReady) {
                pendingSpeechMessage = message
                return@post
            }
            speakNow(message)
        }
    }

    private fun speakNow(message: String) {
        val result = textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "catchpro-${System.currentTimeMillis()}",
        ) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            logAlertDeliveryFailure(
                channel = "voice",
                reason = "음성 안내 실행 실패",
                detail = message,
            )
        }
    }

    private fun ensureTextToSpeech() {
        if (textToSpeech != null) return
        textToSpeech = TextToSpeech(this) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    val languageResult = textToSpeech?.setLanguage(Locale.KOREAN) ?: TextToSpeech.ERROR
                    textToSpeechReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED &&
                        languageResult != TextToSpeech.ERROR
                    if (!textToSpeechReady) {
                        pendingSpeechMessage = null
                        logAlertDeliveryFailure(
                            channel = "voice",
                            reason = "한국어 TTS 미지원",
                            detail = "setLanguage=$languageResult",
                        )
                        return@post
                    }
                    pendingSpeechMessage?.let { message ->
                        pendingSpeechMessage = null
                        speakNow(message)
                    }
                } else {
                    textToSpeechReady = false
                    pendingSpeechMessage = null
                    logAlertDeliveryFailure(
                        channel = "voice",
                        reason = "TTS 초기화 실패",
                        detail = "status=$status",
                    )
                }
            }
        }
    }

    private fun shutdownTextToSpeech() {
        pendingSpeechMessage = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        textToSpeechReady = false
    }

    private fun findAutoConfirmNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val viewIdCandidates = listOf(
            "insung.split.quick:id/q_btnClose",
            "insung.split.quick:id/kor_btnClose",
        )
        viewIdCandidates.forEach { viewId ->
            val matches = root.findAccessibilityNodeInfosByViewId(viewId)
            val node = matches.firstOrNull {
                it.isVisibleToUser && it.readNodeText()?.startsWith("확정") == true
            }
            if (node != null) return node
        }
        return findNode(root) { node ->
            val text = node.readNodeText().orEmpty()
            node.isVisibleToUser && text.startsWith("확정")
        }
    }

    private fun clickAutoEntryDetailCancelButton(root: AccessibilityNodeInfo): Boolean {
        val cancelNode = findAutoEntryDetailCancelNode(root) ?: return false
        val clicked = cancelNode.clickSelfOrAncestor()
        Log.d(LogTag, "order-list-auto-entry: detail cancel click clicked=$clicked text=${cancelNode.readNodeText().orEmpty()}")
        return clicked
    }

    private fun findAutoEntryDetailCancelNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val viewIdCandidates = listOf(
            "insung.split.quick:id/q_btnCard",
            "insung.split.quick:id/kor_btnCard",
        )
        viewIdCandidates.forEach { viewId ->
            val matches = root.findAccessibilityNodeInfosByViewId(viewId)
            val node = matches.firstOrNull { it.isVisibleToUser && it.isLikelyDetailCancelButton(root) }
            if (node != null) return node
        }
        return findNode(root) { node ->
            node.isVisibleToUser && node.isLikelyDetailCancelButton(root)
        }
    }

    private fun AccessibilityNodeInfo.isLikelyDetailCancelButton(root: AccessibilityNodeInfo): Boolean {
        val text = readNodeText()
            .orEmpty()
            .replace(Regex("""\s+"""), " ")
            .trim()
        val idLooksLikeDetailCancel = viewIdResourceName.orEmpty().let { id ->
            id.endsWith(":id/q_btnCard") || id.endsWith(":id/kor_btnCard")
        }
        if (text.contains("뒤로가기") || text.contains("취소:")) return false
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val bounds = Rect().also(this::getBoundsInScreen)
        if (bounds.width() < dp(90) || bounds.height() < dp(45)) return false
        val isBottomAction = bounds.centerY() >= rootBounds.top + (rootBounds.height() * DetailActionButtonMinYRatio).toInt()
        return isBottomAction && (text.contains("취소") || idLooksLikeDetailCancel)
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val match = findNode(child, predicate)
            if (match != null) return match
        }
        return null
    }

    private fun AccessibilityNodeInfo.clickSelfOrAncestor(): Boolean {
        var current: AccessibilityNodeInfo? = this
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun AccessibilityNodeInfo.clickSelfOrAncestorWithDiagnostics(): NodeClickResult {
        val inspected = mutableListOf<String>()
        var current: AccessibilityNodeInfo? = this
        var depth = 0
        var gestureTarget: AccessibilityNodeInfo? = null
        var gestureTargetDiagnostic: String? = null
        while (current != null && depth <= MaxClickDiagnosticAncestorDepth) {
            val diagnostic = current.nodeClickDiagnostic(depth)
            inspected += diagnostic
            val bounds = Rect().also(current::getBoundsInScreen)
            if (current.isVisibleToUser && current.isEnabled && !bounds.isEmpty) {
                if (current.isClickable) {
                    gestureTarget = current
                    gestureTargetDiagnostic = diagnostic
                    break
                }
                if (gestureTarget == null) {
                    gestureTarget = current
                    gestureTargetDiagnostic = diagnostic
                }
            }
            current = current.parent
            depth += 1
        }

        gestureTarget?.let { target ->
            val bounds = Rect().also(target::getBoundsInScreen)
            val tapX = if (bounds.isEmpty) 0 else bounds.centerX()
            val tapY = if (bounds.isEmpty) 0 else bounds.centerY()
            val gestureAccepted = dispatchAutoEntryTapGesture(tapX, tapY)
            if (gestureAccepted) {
                return NodeClickResult(
                    clicked = true,
                    diagnostic = "method=GESTURE_CENTER,target=$gestureTargetDiagnostic,tap=$tapX,$tapY,gesture=true inspected=${inspected.joinToString(" -> ")}",
                )
            }
            if (target.isClickable) {
                val clicked = runCatching {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                return NodeClickResult(
                    clicked = clicked,
                    diagnostic = "method=GESTURE_FAILED_ACTION_CLICK,target=$gestureTargetDiagnostic,tap=$tapX,$tapY,gesture=false,action=$clicked inspected=${inspected.joinToString(" -> ")}",
                )
            }
            return NodeClickResult(
                clicked = false,
                diagnostic = "method=GESTURE_FAILED_NO_CLICKABLE_TARGET,target=$gestureTargetDiagnostic,tap=$tapX,$tapY,gesture=false inspected=${inspected.joinToString(" -> ")}",
            )
        }

        return NodeClickResult(
            clicked = false,
            diagnostic = "noClickableAncestor inspected=${inspected.joinToString(" -> ")}",
        )
    }

    private fun AccessibilityNodeInfo.nodeClickDiagnostic(depth: Int): String {
        val bounds = Rect().also(::getBoundsInScreen)
        val text = readNodeText()
            .orEmpty()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(40)
        return buildList {
            add("depth=$depth")
            add("class=${className?.toString()?.substringAfterLast('.').orEmpty().ifBlank { "unknown" }}")
            viewIdResourceName?.substringAfterLast('/')?.takeIf(String::isNotBlank)?.let { add("id=$it") }
            if (text.isNotBlank()) add("text=$text")
            add("bounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
            add("clickable=$isClickable")
            add("enabled=$isEnabled")
            add("visible=$isVisibleToUser")
            add("focusable=$isFocusable")
        }.joinToString(",")
    }

    private fun AccessibilityNodeInfo.clickOrderListRow(): AutoEntryClickAttempt {
        val bounds = Rect().also(::getBoundsInScreen)
        val tapX = if (bounds.isEmpty) 0 else bounds.centerX()
        val tapY = if (bounds.isEmpty) 0 else bounds.centerY()
        val actionClicked = clickSelfOrAncestor()
        if (actionClicked || bounds.isEmpty) {
            return AutoEntryClickAttempt(
                accepted = actionClicked,
                method = if (actionClicked) "ACTION_CLICK" else "NO_BOUNDS",
                actionClickSucceeded = actionClicked,
                gestureAccepted = false,
                bounds = Rect(bounds),
                tapX = tapX,
                tapY = tapY,
            )
        }
        val gestureAccepted = dispatchAutoEntryTapGesture(tapX, tapY)
        return AutoEntryClickAttempt(
            accepted = gestureAccepted,
            method = if (gestureAccepted) "ACTION_CLICK_FAILED_GESTURE_CENTER" else "FAILED",
            actionClickSucceeded = false,
            gestureAccepted = gestureAccepted,
            bounds = Rect(bounds),
            tapX = tapX,
            tapY = tapY,
        )
    }

    private fun dispatchAutoEntryTapGesture(
        tapX: Int,
        tapY: Int,
    ): Boolean {
        if (tapX <= 0 || tapY <= 0) return false
        val tapPath = Path().apply {
            moveTo(tapX.toFloat(), tapY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    tapPath,
                    0L,
                    AutoEntryGestureTapDurationMillis,
                ),
            )
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun AccessibilityNodeInfo.readNodeText(): String? {
        return text?.toString()
            ?: contentDescription?.toString()
    }

    private fun AccessibilityEvent.isConfirmButtonClick(root: AccessibilityNodeInfo): Boolean {
        val sourceNode = source
        val eventText = buildString {
            append(sourceNode?.readNodeText().orEmpty())
            append(' ')
            append(text.joinToString(separator = " "))
        }.replace(Regex("""\s+"""), " ").trim()
        if (eventText.startsWith("확정")) return true
        val confirmNode = findAutoConfirmNode(root) ?: return false
        val sourceBounds = Rect().also { sourceNode?.getBoundsInScreen(it) }
        val confirmBounds = Rect().also(confirmNode::getBoundsInScreen)
        return !sourceBounds.isEmpty &&
            !confirmBounds.isEmpty &&
            Rect.intersects(sourceBounds, confirmBounds)
    }

    private fun AccessibilityEvent.confirmButtonClickDiagnostic(root: AccessibilityNodeInfo): String {
        val sourceNode = source
        val sourceBounds = Rect().also { sourceNode?.getBoundsInScreen(it) }
        val confirmNode = findAutoConfirmNode(root)
        val confirmBounds = Rect().also { confirmNode?.getBoundsInScreen(it) }
        val eventText = buildString {
            append(sourceNode?.readNodeText().orEmpty())
            append(' ')
            append(text.joinToString(separator = " "))
        }.replace(Regex("""\s+"""), " ").trim().take(80)
        return buildList {
            add("event=${eventType.toReadableEventType()}")
            if (eventText.isNotBlank()) add("text=$eventText")
            add("sourceBounds=${sourceBounds.left},${sourceBounds.top},${sourceBounds.right},${sourceBounds.bottom}")
            add("confirmBounds=${confirmBounds.left},${confirmBounds.top},${confirmBounds.right},${confirmBounds.bottom}")
            add("sourceClass=${sourceNode?.className?.toString()?.substringAfterLast('.').orEmpty().ifBlank { "unknown" }}")
            add("sourceClickable=${sourceNode?.isClickable}")
            add("confirm=${confirmNode?.nodeClickDiagnostic(0) ?: "not-found"}")
        }.joinToString(",")
    }

    private fun AccessibilityNodeInfo.flattenClickableNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isClickable) {
                result += node
            }
            repeat(node.childCount) { index ->
                visit(node.getChild(index))
            }
        }

        visit(this)
        return result
    }

    private fun AccessibilityEvent.isCancelButtonClick(): Boolean {
        val sourceNode = source
        val viewId = sourceNode?.viewIdResourceName.orEmpty()
        val buttonText = buildString {
            append(sourceNode?.readNodeText().orEmpty())
            append(' ')
            append(text.joinToString(separator = " "))
        }.trim()

        return viewId.endsWith("q_btnCard") ||
            viewId.endsWith("kor_btnCard") ||
            buttonText.contains("취소")
    }

    private fun AccessibilityEvent.isCancelConfirmationYesClick(root: AccessibilityNodeInfo): Boolean {
        val sourceNode = source
        val clickedYes = (
            listOfNotNull(sourceNode?.readNodeText()) + text.mapNotNull { it?.toString() }
            )
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .any { it == "예" }
        return clickedYes && root.hasVisibleTextContaining("취소 하시겠습니까")
    }

    private fun AccessibilityEvent.isPickupButtonClick(): Boolean {
        val sourceNode = source
        val viewId = sourceNode?.viewIdResourceName.orEmpty()
        val buttonText = buildString {
            append(sourceNode?.readNodeText().orEmpty())
            append(' ')
            append(text.joinToString(separator = " "))
        }.replace(Regex("""\s+"""), " ").trim()
        return buttonText == "픽업" ||
            buttonText.contains("픽업") &&
            (viewId.endsWith(":id/q_btnPickup") || viewId.endsWith(":id/kor_btnPickup"))
    }

    private fun AccessibilityEvent.isAffirmativeConfirmationClick(): Boolean {
        val sourceNode = source
        return (
            listOfNotNull(sourceNode?.readNodeText()) + text.mapNotNull { it?.toString() }
            )
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .any { clickedText ->
                clickedText == "예" ||
                    clickedText == "확인" ||
                    clickedText.equals("yes", ignoreCase = true) ||
                    clickedText.equals("ok", ignoreCase = true)
            }
    }

    private fun AccessibilityEvent.isPickupCompletionYesClick(root: AccessibilityNodeInfo): Boolean {
        return isAffirmativeConfirmationClick() &&
            PickupCompletionPromptRegex.containsMatchIn(root.collectVisibleText())
    }

    private fun AccessibilityEvent.isDropoffSignatureButtonClick(root: AccessibilityNodeInfo): Boolean {
        val sourceNode = source ?: return false
        val buttonText = (
            listOfNotNull(sourceNode.readNodeText()) + text.mapNotNull { it?.toString() }
            )
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!buttonText.contains("서명")) return false

        val sourceBounds = Rect().also(sourceNode::getBoundsInScreen)
        if (sourceBounds.width() <= 0 || sourceBounds.height() <= 0) return false
        val dropoffLabelBounds = root.findVisibleTextBounds("도착지", "하차지") ?: return false
        val verticalGap = kotlin.math.abs(sourceBounds.centerY() - dropoffLabelBounds.centerY())
        return verticalGap <= dp(92)
    }

    private fun AccessibilityEvent.isDropoffSendActionClick(root: AccessibilityNodeInfo): Boolean {
        val sourceNode = source
        val clickedText = (
            listOfNotNull(sourceNode?.readNodeText()) + text.mapNotNull { it?.toString() }
            )
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!DropoffSendActionTextRegex.containsMatchIn(clickedText)) return false
        val summary = root.collectVisibleText()
        return DropoffSendContextRegex.containsMatchIn("$summary $clickedText")
    }

    private fun AccessibilityEvent.isDropoffCompletionYesClick(root: AccessibilityNodeInfo): Boolean {
        val sourceNode = source
        val clickedYes = (
            listOfNotNull(sourceNode?.readNodeText()) + text.mapNotNull { it?.toString() }
            )
            .map { it.replace(Regex("""\s+"""), " ").trim() }
            .any { it == "예" || it.equals("yes", ignoreCase = true) }
        return clickedYes && DropoffCompletionPromptRegex.containsMatchIn(root.collectVisibleText())
    }

    private fun AccessibilityNodeInfo.findVisibleTextBounds(vararg labels: String): Rect? {
        if (isVisibleToUser) {
            val normalized = readNodeText().orEmpty()
                .replace(Regex("""\s+"""), "")
                .replace(":", "")
                .replace("：", "")
                .trim()
            if (labels.any { normalized == it }) {
                return Rect().also(::getBoundsInScreen)
            }
        }
        for (index in 0 until childCount) {
            val child = getChild(index) ?: continue
            val match = child.findVisibleTextBounds(*labels)
            if (match != null) return match
        }
        return null
    }

    private fun AccessibilityNodeInfo.hasVisibleTextContaining(text: String): Boolean {
        if (isVisibleToUser && readNodeText()?.contains(text) == true) return true
        for (index in 0 until childCount) {
            val child = getChild(index) ?: continue
            if (child.hasVisibleTextContaining(text)) return true
        }
        return false
    }

    private fun AccessibilityNodeInfo.hasVisibleOrderDetailTitle(): Boolean {
        val text = readNodeText().orEmpty().replace(Regex("""\s+"""), "")
        if (
            isVisibleToUser &&
            (
                text.contains("오더상세") ||
                    text.contains("출발지상세") ||
                    text.contains("상차지상세") ||
                    text.contains("도착지상세") ||
                    text.contains("하차지상세")
                )
        ) {
            return true
        }
        for (index in 0 until childCount) {
            val child = getChild(index) ?: continue
            if (child.hasVisibleOrderDetailTitle()) return true
        }
        return false
    }

    private fun isSameTrackedOrder(
        trackedOrder: TrackedAutoConfirmedOrder,
        parsedDraft: ParsedOrderDraft,
        capturedAtMillis: Long,
    ): Boolean {
        if (buildOrderSignature(parsedDraft) == trackedOrder.signature) {
            return true
        }

        if (
            parsedDraft.destination?.trim() == trackedOrder.destination &&
            parsedDraft.price == trackedOrder.price
        ) {
            return true
        }

        if (!parsedDraft.isDetailedScreen) {
            return capturedAtMillis - trackedOrder.confirmedAtMillis < ImmediateCancelMatchWindowMillis
        }

        return false
    }

    private fun isLikelyDetailedAddressForSummary(
        summaryAddress: String,
        detailedAddress: String,
    ): Boolean {
        if (!isOperationalAddress(detailedAddress)) return false
        val summaryKey = summaryAddress.normalizeRouteAddressKey()
        val detailedKey = detailedAddress.normalizeRouteAddressKey()
        if (detailedKey.isBlank() || detailedKey == summaryKey) return false
        return (
            summaryKey.length >= 2 &&
                detailedKey.length >= summaryKey.length + 3 &&
                detailedKey.contains(summaryKey)
            ) || (
            detailedKey.length > summaryKey.length &&
                administrativeAddressWordsMatch(
            summaryAddress = summaryAddress,
            detailedAddress = detailedAddress,
                )
            )
    }

    private fun administrativeAddressWordsMatch(
        summaryAddress: String,
        detailedAddress: String,
    ): Boolean {
        val summaryWords = summaryAddress
            .cleanRouteAddressCandidate()
            .split(Regex("""\s+"""))
            .filter { word -> RouteAdministrativeWordRegex.matches(word) }
            .distinct()
        if (summaryWords.size < 2) return false

        val hasTown = summaryWords.any { word -> RouteAdministrativeTownWordRegex.matches(word) }
        val hasCityOrDistrict = summaryWords.any { word ->
            word.endsWith("시") || word.endsWith("군") || word.endsWith("구")
        }
        if (!hasTown || !hasCityOrDistrict) return false

        val detailedWords = detailedAddress
            .cleanRouteAddressCandidate()
            .split(Regex("""\s+"""))
            .toSet()
        if (summaryWords.all(detailedWords::contains)) return true

        val detailedAdministrativeWords = detailedWords
            .filter { word -> RouteAdministrativeWordRegex.matches(word) }
        val summaryTownWords = summaryWords.filter { word -> RouteAdministrativeTownWordRegex.matches(word) }
        val detailedTownWords = detailedAdministrativeWords.filter { word -> RouteAdministrativeTownWordRegex.matches(word) }
        val townMatches = summaryTownWords.any { summaryTown -> detailedTownWords.contains(summaryTown) }
        if (!townMatches) return false

        val summaryAreaWords = summaryWords.filterNot { word -> RouteAdministrativeTownWordRegex.matches(word) }
        val detailedAreaWords = detailedAdministrativeWords.filterNot { word -> RouteAdministrativeTownWordRegex.matches(word) }
        return summaryAreaWords.any { summaryArea ->
            detailedAreaWords.any { detailedArea ->
                summaryArea.contains(detailedArea) || detailedArea.contains(summaryArea)
            }
        }
    }

    private fun isSamePendingOrder(
        pending: PendingManualConfirmation,
        parsedDraft: ParsedOrderDraft,
    ): Boolean {
        if (buildOrderSignature(parsedDraft) == pending.signature) {
            return true
        }
        return parsedDraft.destination?.trim() == pending.draft.destination?.trim() &&
            parsedDraft.price == pending.draft.price
    }

    private fun buildOrderSignature(parsedDraft: ParsedOrderDraft): String {
        return buildString {
            append(InsungQuickPackage)
            append('|')
            append(parsedDraft.price ?: 0)
            append('|')
            append(parsedDraft.requesterLocation.orEmpty())
            append('|')
            append(parsedDraft.origin.orEmpty())
            append('|')
            append(parsedDraft.destination.orEmpty())
            append('|')
            append(parsedDraft.detailNote.orEmpty().take(80))
        }
    }

    private fun buildRoadDistanceCacheKey(
        actionSignature: String,
        isSecondary: Boolean,
    ): String = "road|$isSecondary|$actionSignature"

    private fun ParsedOrderDraft.primaryDestinationArea(): PrimaryDestinationArea? {
        val candidates = mutableListOf<String>()
        candidates += listOfNotNull(destination, effectiveDestination())
        routeText
            ?.splitRouteText()
            ?.let { (_, routeDestination) -> candidates += routeDestination }
        detailNote?.let { note ->
            candidates += note.extractLabeledAddressCandidates(RouteAddressRole.Dropoff)
        }
        return candidates
            .asSequence()
            .map { it.cleanRouteAddressCandidate() }
            .filter(String::isNotBlank)
            .mapNotNull { it.toPrimaryDestinationArea() }
            .firstOrNull()
    }

    private fun String.toPrimaryDestinationArea(): PrimaryDestinationArea? {
        val normalized = cleanRouteAddressCandidate()
        if (normalized.isBlank()) return null
        val normalizedKey = normalized.normalizeAdministrativeAreaKey()
        val districtCandidates = KoreaAdministrativeAreas.provinces
            .flatMap { province ->
                province.districts.map { district -> province to district }
            }
            .sortedByDescending { (_, district) -> district.normalizeAdministrativeAreaKey().length }
        val simpleDistrictCounts = districtCandidates
            .map { (_, district) -> district.simpleDistrictName().normalizeAdministrativeAreaKey() }
            .groupingBy { it }
            .eachCount()
        val townCandidates = districtCandidates
            .flatMap { (province, district) ->
                KoreaAdministrativeAreas.townsForDistrict(province.name, district).map { town ->
                    Triple(province, district, town)
                }
            }
        val simpleTownCounts = townCandidates
            .map { (_, _, town) -> town.normalizeAdministrativeAreaKey() }
            .groupingBy { it }
            .eachCount()
        val labels = linkedSetOf<String>()
        val matchKeys = linkedSetOf<String>()

        districtCandidates.forEach { (province, district) ->
            val label = if (province.name == "세종") {
                province.fullName
            } else {
                "${province.fullName} $district"
            }
            val provinceDistrictKeys = province.aliases.map { alias ->
                "$alias $district".normalizeAdministrativeAreaKey()
            } + label.normalizeAdministrativeAreaKey()
            val districtKey = district.normalizeAdministrativeAreaKey()
            val districtWithoutCityMarkerKey = district.replace("시 ", "").normalizeAdministrativeAreaKey()
            val simpleDistrictKey = district.simpleDistrictName().normalizeAdministrativeAreaKey()
            val simpleDistrictIsUnique = simpleDistrictCounts[simpleDistrictKey] == 1
            val districtKeyMatch = if (districtKey == simpleDistrictKey) {
                simpleDistrictIsUnique && normalizedKey.contains(districtKey)
            } else {
                normalizedKey.contains(districtKey)
            }

            val matched = KoreaAdministrativeAreas.matchesKeyword(label, normalized) ||
                provinceDistrictKeys.any { key -> normalizedKey.contains(key) } ||
                districtKeyMatch ||
                (districtWithoutCityMarkerKey != districtKey && normalizedKey.contains(districtWithoutCityMarkerKey)) ||
                (simpleDistrictIsUnique && normalizedKey.contains(simpleDistrictKey))

            if (matched) {
                labels += label
                matchKeys += "district:${label.normalizeAdministrativeAreaKey()}"
                if (simpleDistrictIsUnique) {
                    matchKeys += "district:$simpleDistrictKey"
                }
                KoreaAdministrativeAreas.townsForDistrict(province.name, district)
                    .forEach { town ->
                        val townKey = town.normalizeAdministrativeAreaKey()
                        if (townKey.isNotBlank() && normalizedKey.contains(townKey)) {
                            labels += "$label $town"
                            matchKeys += "town:${label.normalizeAdministrativeAreaKey()}:$townKey"
                            if (simpleTownCounts[townKey] == 1) {
                                matchKeys += "town:$townKey"
                            }
                        }
                    }
            }
        }

        townCandidates.forEach { (province, district, town) ->
            val townKey = town.normalizeAdministrativeAreaKey()
            if (townKey.isBlank() || simpleTownCounts[townKey] != 1 || !normalizedKey.contains(townKey)) {
                return@forEach
            }
            val districtLabel = if (province.name == "세종") {
                province.fullName
            } else {
                "${province.fullName} $district"
            }
            labels += "$districtLabel $town"
            matchKeys += "town:$townKey"
        }

        if (matchKeys.isEmpty()) return null
        return PrimaryDestinationArea(
            label = labels.joinToString(", ").ifBlank { normalized.shortDiagnosticText() },
            matchKeys = matchKeys,
        )
    }

    private fun String.simpleDistrictName(): String =
        trim()
            .split(Regex("""\s+"""))
            .lastOrNull()
            .orEmpty()

    private fun String.normalizeAdministrativeAreaKey(): String =
        replace(Regex("""\s+"""), "")
            .replace("특별시", "")
            .replace("광역시", "")
            .replace("특별자치시", "")
            .replace("특별자치도", "")
            .replace("경기도", "경기")
            .replace("강원특별자치도", "강원")
            .replace("전북특별자치도", "전북")
            .replace("충청북도", "충북")
            .replace("충청남도", "충남")
            .replace("전라남도", "전남")
            .replace("경상북도", "경북")
            .replace("경상남도", "경남")
            .replace("제주특별자치도", "제주")
            .lowercase(Locale.KOREAN)

    private fun findSecondaryDestinationAdministrativeMatch(draft: ParsedOrderDraft): String? {
        val referenceTexts = (
            listOf(activeSettings.activeDriveDestinationText) +
                activeSettings.activeDriveDestinationText.estimatedAddressCandidatesFromText()
            )
            .map { it.cleanRouteAddressCandidate() }
            .filter(String::isNotBlank)
            .distinct()
        if (referenceTexts.isEmpty()) return null

        val orderTexts = draft.destinationAdministrativeMatchTexts()
        orderTexts.forEach { orderText ->
            referenceTexts.forEach { referenceText ->
                if (orderText.matchesAdministrativeDestination(referenceText)) {
                    return orderText.shortDiagnosticText()
                }
            }
        }
        return null
    }

    private fun findTrackingDestinationCompatibility(draft: ParsedOrderDraft): TrackingDestinationCompatibility? {
        val referenceText = activeSettings.activeDriveDestinationText
            .cleanRouteAddressCandidate()
            .takeIf(String::isNotBlank)
            ?: return null
        val referenceArea = referenceText.toTrackingDestinationArea() ?: return null
        val orderTexts = draft.destinationAdministrativeMatchTexts()

        orderTexts.forEach { orderText ->
            val orderArea = orderText.toTrackingDestinationArea() ?: return@forEach
            referenceArea.matchTrackingDestination(orderArea)?.let { reason ->
                return TrackingDestinationCompatibility(reason)
            }
            trackingVerifiedZoneMatch(referenceText, orderText)?.let { zoneName ->
                return TrackingDestinationCompatibility(
                    "검증 권역 일치($zoneName): 기준=${referenceText.shortDiagnosticText()}, 후보=${orderText.shortDiagnosticText()}",
                )
            }
        }
        return null
    }

    private fun String.toTrackingDestinationArea(): TrackingDestinationArea? {
        val cleaned = cleanRouteAddressCandidate()
        if (cleaned.isBlank()) return null

        val normalized = cleaned.normalizeAdministrativeAreaKey()
        val primaryArea = cleaned.toPrimaryDestinationArea()
        val primaryLabelKey = primaryArea?.label.orEmpty().normalizeAdministrativeAreaKey()
        val provinceKey = when {
            normalized.contains("서울") || primaryLabelKey.contains("서울") -> "서울"
            normalized.contains("경기") || primaryLabelKey.contains("경기") -> "경기"
            else -> null
        }
        val districtKeys = linkedSetOf<String>()
        val townKeys = linkedSetOf<String>()
        val villageKeys = linkedSetOf<String>()

        primaryArea?.matchKeys.orEmpty().forEach { key ->
            when {
                key.startsWith("district:") -> districtKeys += key.substringAfterLast(':')
                key.startsWith("town:") -> townKeys += key.substringAfterLast(':')
            }
        }
        extractAdministrativeWordsBySuffix("구", "군")
            .forEach { districtKeys += it }
        extractAdministrativeWordsBySuffix("동", "읍", "면")
            .forEach { townKeys += it }
        extractAdministrativeWordsBySuffix("리")
            .forEach { villageKeys += it }

        if (
            provinceKey == null &&
            districtKeys.isEmpty() &&
            townKeys.isEmpty() &&
            villageKeys.isEmpty()
        ) {
            return null
        }
        return TrackingDestinationArea(
            label = primaryArea?.label ?: cleaned.shortDiagnosticText(),
            provinceKey = provinceKey,
            districtKeys = districtKeys,
            townKeys = townKeys,
            villageKeys = villageKeys,
        )
    }

    private fun String.extractAdministrativeWordsBySuffix(vararg suffixes: String): Set<String> {
        val suffixPattern = suffixes.joinToString("|") { Regex.escape(it) }
        return Regex("""[가-힣A-Za-z0-9]+(?:$suffixPattern)""")
            .findAll(this)
            .map { it.value.normalizeAdministrativeAreaKey() }
            .filter { it.length >= 2 }
            .filterNot { it in setOf("경기도", "강원도", "전라도", "경상도", "충청도", "제주도") }
            .toSet()
    }

    private fun TrackingDestinationArea.matchTrackingDestination(
        other: TrackingDestinationArea,
    ): String? {
        if (isSeoulLikeWith(other)) {
            val district = districtKeys.intersect(other.districtKeys).firstOrNull()
            if (district != null) {
                return "서울 같은 구(${district.trackingAreaKeyLabel()})"
            }
            return null
        }

        if (isGyeonggiLikeWith(other)) {
            val village = villageKeys.intersect(other.villageKeys).firstOrNull()
            if (village != null) {
                return "경기 같은 리(${village.trackingAreaKeyLabel()})"
            }
            val town = townKeys.intersect(other.townKeys).firstOrNull()
            if (town != null) {
                return "경기 같은 동/읍/면(${town.trackingAreaKeyLabel()})"
            }
            return null
        }

        val village = villageKeys.intersect(other.villageKeys).firstOrNull()
        if (village != null) {
            return "같은 리(${village.trackingAreaKeyLabel()})"
        }
        val town = townKeys.intersect(other.townKeys).firstOrNull()
        if (town != null) {
            return "같은 동/읍/면(${town.trackingAreaKeyLabel()})"
        }
        val district = districtKeys.intersect(other.districtKeys).firstOrNull()
        if (district != null) {
            return "같은 군/구(${district.trackingAreaKeyLabel()})"
        }
        return null
    }

    private fun TrackingDestinationArea.isSeoulLikeWith(other: TrackingDestinationArea): Boolean {
        return provinceKey == "서울" || other.provinceKey == "서울"
    }

    private fun TrackingDestinationArea.isGyeonggiLikeWith(other: TrackingDestinationArea): Boolean {
        return provinceKey == "경기" || other.provinceKey == "경기"
    }

    private fun String.trackingAreaKeyLabel(): String =
        replace(Regex("""\s+"""), "")
            .ifBlank { this }

    private fun trackingVerifiedZoneMatch(
        referenceText: String,
        orderText: String,
    ): String? {
        val referenceKey = referenceText.normalizeAdministrativeAreaKey()
        val orderKey = orderText.normalizeAdministrativeAreaKey()
        return VerifiedTrackingZones.firstOrNull { zone ->
            val aliasKeys = zone.aliases.map { it.normalizeAdministrativeAreaKey() }
            aliasKeys.any { referenceKey.contains(it) } &&
                aliasKeys.any { orderKey.contains(it) }
        }?.name
    }

    private fun ParsedOrderDraft.destinationAdministrativeMatchTexts(): List<String> {
        val candidates = mutableListOf<String>()
        candidates += listOfNotNull(destination, effectiveDestination())
        routeText
            ?.splitRouteText()
            ?.let { (_, routeDestination) -> candidates += routeDestination }
        detailNote?.let { note ->
            candidates += note.extractLabeledAddressCandidates(RouteAddressRole.Dropoff)
            candidates += note.extractAddressLikeSegments()
        }
        return candidates
            .map { it.cleanRouteAddressCandidate() }
            .filter(String::isNotBlank)
            .distinctBy { it.normalizeRouteAddressKey() }
    }

    private fun String.matchesAdministrativeDestination(reference: String): Boolean {
        val orderText = cleanRouteAddressCandidate()
        val referenceText = reference.cleanRouteAddressCandidate()
        if (orderText.isBlank() || referenceText.isBlank()) return false
        return KoreaAdministrativeAreas.matchesKeyword(orderText, referenceText) ||
            KoreaAdministrativeAreas.matchesKeyword(referenceText, orderText)
    }

    private fun String.shortDiagnosticText(): String =
        replace(Regex("""\s+"""), " ")
            .trim()
            .take(36)

    private fun String.operationContextText(limit: Int = 160): String =
        replace(Regex("""\s+"""), " ")
            .trim()
            .take(limit)

    private fun String.toManualRouteAddressSlotsForSync(): List<String> =
        split('\n')
            .map { it.trim() }
            .take(ManualRouteAddressSlotCount)
            .let { slots ->
                slots + List((ManualRouteAddressSlotCount - slots.size).coerceAtLeast(0)) { "" }
            }

    private fun String.clearManualRouteAddressOrderSlot(orderSlotIndex: Int): String {
        if (orderSlotIndex !in 0 until RouteOrderSlotCount) return this
        val slots = toManualRouteAddressSlotsForSync().toMutableList()
        val pickupSlotIndex = orderSlotIndex * 2
        val dropoffSlotIndex = pickupSlotIndex + 1
        slots[pickupSlotIndex] = ""
        slots[dropoffSlotIndex] = ""
        return slots.joinToString("\n")
    }

    private fun Int.toManualRouteAddressSlotLabel(): String =
        "주소 ${this + 1}"

    private fun ParsedOrderDraft.routeAddressCandidates(role: RouteAddressRole): List<String> {
        val candidates = mutableListOf<String>()
        val primary = when (role) {
            RouteAddressRole.Pickup -> origin
            RouteAddressRole.Dropoff -> destination
        }
        val effective = when (role) {
            RouteAddressRole.Pickup -> effectiveOrigin() ?: requesterLocation
            RouteAddressRole.Dropoff -> effectiveDestination()
        }
        candidates += listOfNotNull(primary, effective)

        routeText
            ?.splitRouteText()
            ?.let { (routeOrigin, routeDestination) ->
                candidates += when (role) {
                    RouteAddressRole.Pickup -> routeOrigin
                    RouteAddressRole.Dropoff -> routeDestination
                }
            }

        detailNote
            ?.let { note ->
                candidates += note.extractLabeledAddressCandidates(role)
                candidates += note.extractAddressLikeSegments()
            }

        return candidates
            .map { it.cleanRouteAddressCandidate() }
            .filter(::isOperationalAddress)
            .distinctBy { it.normalizeRouteAddressKey() }
            .sortedByDescending { it.normalizeRouteAddressKey().length }
            .take(MaxRouteAddressCandidates)
    }

    private fun ParsedOrderDraft.estimatedAddressCandidates(role: RouteAddressRole): List<String> {
        val candidates = mutableListOf<String>()
        val primary = when (role) {
            RouteAddressRole.Pickup -> origin
            RouteAddressRole.Dropoff -> destination
        }
        val effective = when (role) {
            RouteAddressRole.Pickup -> effectiveOrigin() ?: requesterLocation
            RouteAddressRole.Dropoff -> effectiveDestination()
        }
        candidates += listOfNotNull(primary, effective)

        routeText
            ?.splitRouteText()
            ?.let { (routeOrigin, routeDestination) ->
                candidates += when (role) {
                    RouteAddressRole.Pickup -> routeOrigin
                    RouteAddressRole.Dropoff -> routeDestination
                }
            }

        detailNote
            ?.let { note ->
                candidates += note.extractLabeledAddressCandidates(role)
                candidates += note.extractAddressLikeSegments()
            }

        return candidates
            .map { it.cleanRouteAddressCandidate() }
            .filter(::isEstimatedAddressCandidate)
            .distinctBy { it.normalizeRouteAddressKey() }
            .sortedByDescending { it.normalizeRouteAddressKey().length }
            .take(MaxRouteAddressCandidates)
    }

    private fun ParsedOrderDraft.detailedOperationalAddressCandidates(): List<String> {
        val candidates = mutableListOf<String>()
        candidates += listOfNotNull(
            origin,
            effectiveOrigin(),
            requesterLocation,
            destination,
            effectiveDestination(),
        )
        routeText
            ?.splitRouteText()
            ?.let { (routeOrigin, routeDestination) ->
                candidates += routeOrigin
                candidates += routeDestination
            }
        detailNote
            ?.let { note ->
                candidates += note.extractLabeledAddressCandidates(RouteAddressRole.Pickup)
                candidates += note.extractLabeledAddressCandidates(RouteAddressRole.Dropoff)
                candidates += note.extractAddressLikeSegments()
            }

        return candidates
            .map { it.cleanRouteAddressCandidate() }
            .filter(::isOperationalAddress)
            .distinctBy { it.normalizeRouteAddressKey() }
            .sortedByDescending { it.normalizeRouteAddressKey().length }
    }

    private fun String.routeAddressCandidatesFromText(): List<String> {
        return (
            listOf(this) + splitRouteText()
                ?.let { (_, destination) -> listOf(destination) }
                .orEmpty() + extractAddressLikeSegments()
            )
            .map { it.cleanRouteAddressCandidate() }
            .filter(::isOperationalAddress)
            .distinctBy { it.normalizeRouteAddressKey() }
            .sortedByDescending { it.normalizeRouteAddressKey().length }
            .take(MaxRouteAddressCandidates)
    }

    private fun String.estimatedAddressCandidatesFromText(): List<String> {
        return (
            listOf(this) + splitRouteText()
                ?.let { (_, destination) -> listOf(destination) }
                .orEmpty() + extractAddressLikeSegments()
            )
            .map { it.cleanRouteAddressCandidate() }
            .filter(::isEstimatedAddressCandidate)
            .distinctBy { it.normalizeRouteAddressKey() }
            .sortedByDescending { it.normalizeRouteAddressKey().length }
            .take(MaxRouteAddressCandidates)
    }

    private fun String.splitRouteText(): Pair<String, String>? {
        val match = RouteArrowRegex.find(this) ?: return null
        val origin = match.groupValues.getOrNull(1)?.cleanRouteAddressCandidate().orEmpty()
        val destination = match.groupValues.getOrNull(2)?.cleanRouteAddressCandidate().orEmpty()
        if (origin.isBlank() || destination.isBlank()) return null
        return origin to destination
    }

    private fun String.extractLabeledAddressCandidates(role: RouteAddressRole): List<String> {
        val labels = when (role) {
            RouteAddressRole.Pickup -> PickupAddressLabels
            RouteAddressRole.Dropoff -> DropoffAddressLabels
        }
        val normalized = replace("\n", " ")
            .replace("\r", " ")
            .replace("|", " ")
        val result = mutableListOf<String>()
        labels.forEach { label ->
            var searchStart = 0
            while (searchStart < normalized.length) {
                val labelIndex = normalized.indexOf(label, startIndex = searchStart)
                if (labelIndex < 0) break
                val valueStart = labelIndex + label.length
                val tail = normalized.substring(valueStart)
                    .trimStart(':', '：', '-', ' ', '\t')
                val boundary = AddressCandidateBoundaryRegex.find(tail)?.range?.first ?: tail.length
                result += tail.take(boundary)
                searchStart = valueStart
            }
        }
        return result
    }

    private fun String.extractAddressLikeSegments(): List<String> {
        val normalized = replace("\n", " ")
            .replace("\r", " ")
            .replace("|", " ")
        return normalized
            .split('/', ',', ';')
            .map { it.cleanRouteAddressCandidate() }
            .filter { candidate ->
                candidate.length >= 6 &&
                    RouteCityOrDistrictRegex.containsMatchIn(candidate) &&
                    RouteTownOrRoadRegex.containsMatchIn(candidate)
            }
    }

    private fun String.cleanRouteAddressCandidate(): String {
        return replace(RouteDistanceTextRegex, " ")
            .replace(RouteAddressLabelRegex, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('*', '@', '/', '|', ',', '-', '·')
            .trim()
    }

    private fun String.normalizeRouteAddressKey(): String {
        return lowercase(Locale.KOREAN)
            .replace(RouteDistanceTextRegex, "")
            .replace(RouteAddressLabelRegex, "")
            .replace(Regex("""[\s/(),._\-·]+"""), "")
            .trim()
    }

    private fun ParsedOrderDraft.operationalPickupAddress(): String? {
        return detailedAddressMatching(
            summaries = listOfNotNull(origin, effectiveOrigin(), requesterLocation),
        )
    }

    private fun ParsedOrderDraft.operationalDropoffAddress(): String? {
        return detailedAddressMatching(
            summaries = listOfNotNull(destination, effectiveDestination()),
        )
    }

    private fun ParsedOrderDraft.detailedAddressMatching(
        summaries: List<String>,
    ): String? {
        val cleanSummaries = summaries
            .map { it.cleanRouteAddressCandidate() }
            .filter(String::isNotBlank)
            .distinctBy { it.normalizeRouteAddressKey() }
        return detailedOperationalAddressCandidates()
            .firstOrNull { candidate ->
                cleanSummaries.any { summary ->
                    isLikelyDetailedAddressForSummary(
                        summaryAddress = summary,
                        detailedAddress = candidate,
                    )
                }
            }
    }

    private fun ParsedOrderDraft.bestDetailedAddressForClipboard(): String? {
        return detailedOperationalAddressCandidates()
            .firstOrNull()
    }

    private fun ParsedOrderDraft.isManualAddressDetailScreen(): Boolean {
        if (addressCopyOverlayBlockReason() != null) return false
        return true
    }

    private fun ParsedOrderDraft.addressCopyOverlayBlockReason(): String? {
        if (bestDetailedAddressForClipboard() == null) return "상세주소 후보 없음"
        if (isConfirmableDetailScreen) return "확정 가능한 오더 상세 화면"
        if (currentToPickupDistanceKm != null) return "상차 직선거리 감지"
        if (confirmActionLabel != null) return "확정 버튼 감지:$confirmActionLabel"
        if (cancelActionLabel?.contains("취소") == true) return "취소 버튼 감지:$cancelActionLabel"
        if (!origin.isNullOrBlank() && !destination.isNullOrBlank()) return "출발/도착 동시 감지"
        return null
    }

    private fun isOperationalAddress(value: String): Boolean {
        val normalized = value
            .cleanRouteAddressCandidate()
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!isSpecificRouteAddress(normalized)) return false

        val words = normalized.split(Regex("""\s+""")).filter(String::isNotBlank)
        val hasDetailedDongAddress = words.indices.any { index ->
            RouteAdministrativeTownWordRegex.matches(words[index]) &&
                words.drop(index + 1).any { detail ->
                    detail.length >= 2 &&
                        !RouteAdministrativeWordRegex.matches(detail) &&
                        !RouteAddressLabelRegex.containsMatchIn(detail)
                }
        }
        val hasRoadDetail = RouteRoadAddressRegex.containsMatchIn(normalized)
        return hasDetailedDongAddress || hasRoadDetail
    }

    private fun isEstimatedAddressCandidate(value: String): Boolean {
        val normalized = value
            .cleanRouteAddressCandidate()
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!isSpecificRouteAddress(normalized)) return false
        return !RouteAddressLabelRegex.containsMatchIn(normalized)
    }

    private fun isSpecificRouteAddress(value: String): Boolean {
        val normalized = value
            .replace(Regex("\\s+"), "")
            .trim()
        if (normalized.length < 6) return false
        val hasCityOrDistrict = RouteCityOrDistrictRegex.containsMatchIn(normalized)
        val hasTownOrRoad = RouteTownOrRoadRegex.containsMatchIn(normalized)
        return hasCityOrDistrict && hasTownOrRoad
    }

    private fun Double.formatDistanceKm(): String =
        if (this % 1.0 == 0.0) {
            toInt().toString()
        } else {
            "%.1f".format(Locale.getDefault(), this)
        }

    private fun Double.formatMultiplier(): String =
        if (this % 1.0 == 0.0) {
            toInt().toString()
        } else {
            "%.1f".format(Locale.getDefault(), this)
        }

    private suspend fun enqueueTmapQueueForDraft(
        orderSignature: String,
        sourceType: String,
        parsedDraft: ParsedOrderDraft,
        fallbackTitle: String,
    ) {
        tmapQueueRepository.enqueueConfirmedOrder(
            orderSignature = orderSignature,
            sourceType = sourceType,
            orderTitle = parsedDraft.effectiveRouteText() ?: fallbackTitle,
            pickupAddress = parsedDraft.operationalPickupAddress(),
            dropoffAddress = parsedDraft.operationalDropoffAddress(),
        )
    }

    private fun buildAddressCopyOverlayText(
        address: String,
        copied: Boolean,
    ): String {
        return buildString {
            append(if (copied) "복사됨" else "상세주소 복사")
            append('\n')
            append(address.take(22))
        }
    }

    private fun trimRoadDistanceCache() {
        while (roadDistanceCache.size > MaxRoadDistanceCacheEntries) {
            val firstKey = roadDistanceCache.keys.firstOrNull() ?: return
            roadDistanceCache.remove(firstKey)
        }
    }

    private fun maybeResetPreviousLogsForToday(
        nowMillis: Long,
        force: Boolean = false,
    ) {
        if (
            !force &&
            nowMillis - lastDailyLogCleanupCheckAtMillis < DailyLogCleanupCheckIntervalMillis
        ) {
            return
        }
        lastDailyLogCleanupCheckAtMillis = nowMillis

        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
        if (!force && lastDailyLogCleanupDate == today) return
        if (dailyLogCleanupInFlight) return

        dailyLogCleanupInFlight = true
        serviceScope.launch {
            try {
                val cutoffMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()
                operationLogRepository.deleteOlderThan(cutoffMillis)
                orderEventRepository.deleteOlderThan(cutoffMillis)
                captureRepository.deleteAllOlderThan(cutoffMillis)
                lastDailyLogCleanupDate = today
            } catch (error: Throwable) {
                Log.w(LogTag, "daily log cleanup failed: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                dailyLogCleanupInFlight = false
            }
        }
    }

    private fun scheduleNextDailyLogReset(nowMillis: Long = System.currentTimeMillis()) {
        dailyLogResetRunnable?.let(mainHandler::removeCallbacks)
        val delayMillis = (nextLocalMidnightMillis(nowMillis) - nowMillis)
            .coerceAtLeast(DailyLogResetMinimumDelayMillis)
        val runnable = Runnable {
            val currentMillis = System.currentTimeMillis()
            maybeResetPreviousLogsForToday(nowMillis = currentMillis, force = true)
            scheduleNextDailyLogReset(currentMillis + DailyLogResetMinimumDelayMillis)
        }
        dailyLogResetRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun nextLocalMidnightMillis(nowMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .toLocalDate()
        return today.plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    private suspend fun maybeCleanupCaptureLog(nowMillis: Long) {
        if (nowMillis - lastCaptureCleanupAtMillis < CaptureCleanupIntervalMillis) return
        lastCaptureCleanupAtMillis = nowMillis
        val shouldCompact = nowMillis - lastCaptureCompactAtMillis >= CaptureCompactIntervalMillis
        if (shouldCompact) {
            lastCaptureCompactAtMillis = nowMillis
        }
        runCatching {
            captureRepository.pruneInsungOnly(
                retentionDays = activeSettings.historyRetentionDays,
                maxRows = MaxStoredCaptures,
                nowMillis = nowMillis,
                compact = shouldCompact,
            )
        }.onFailure { error ->
            Log.w(LogTag, "capture cleanup failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun activePendingAutoEntry(nowMillis: Long): PendingAutoListEntry? {
        val pending = pendingAutoListEntry ?: return null
        if (nowMillis - pending.clickedAtMillis <= AutoEntryPendingMillis) return pending
        pendingAutoListEntry = null
        return null
    }

    private fun scheduleAutoEntryFallbackTapIfNeeded(
        pending: PendingAutoListEntry?,
        clickAttempt: AutoEntryClickAttempt,
        packageName: String,
    ) {
        if (pending == null) return
        if (!clickAttempt.actionClickSucceeded) return
        if (clickAttempt.bounds.isEmpty) return
        mainHandler.postDelayed(
            {
                val current = pendingAutoListEntry ?: return@postDelayed
                if (
                    current.listSignature != pending.listSignature ||
                    current.clickedAtMillis != pending.clickedAtMillis
                ) {
                    return@postDelayed
                }
                val nowMillis = System.currentTimeMillis()
                val root = rootInActiveWindow ?: return@postDelayed
                if (!isSupportedInsungPackage(root.packageName?.toString().orEmpty())) return@postDelayed
                if (!root.hasInsungOrderListRows() || root.hasVisibleOrderDetailTitle()) return@postDelayed

                val fallbackCandidate = findPendingAutoEntryFallbackCandidate(
                    root = root,
                    pending = current,
                )
                if (fallbackCandidate == null) {
                    val skipDiagnostic = "ACTION_CLICK 성공 후 보조좌표탭 생략 · 같은 행 확인 실패 · tap=${current.tapX},${current.tapY}"
                    pendingAutoListEntry = null
                    logOperation(
                        eventType = "auto_entry_click_retry",
                        status = "order-list-auto-entry-fallback-skipped",
                        orderSignature = current.listSignature,
                        mode = current.mode,
                        source = "order_list_auto_entry",
                        region = current.region,
                        confirmed = false,
                        reason = "보조 좌표탭 직전 같은 오더 행을 확인하지 못해 즉시 다음 후보를 탐색합니다.",
                        clickDiagnostic = skipDiagnostic,
                        screenSummary = current.listSummary,
                        rawContext = current.clickDiagnostic,
                        createdAtMillis = nowMillis,
                    )
                    scheduleAutoEntryListRescan(40L)
                    return@postDelayed
                }

                val tapX = fallbackCandidate.bounds.centerX()
                val tapY = fallbackCandidate.bounds.centerY()
                val accepted = dispatchAutoEntryTapGesture(tapX, tapY)
                val retryDiagnostic = "ACTION_CLICK 성공 후 상세 미진입 보조좌표탭=${if (accepted) "accepted" else "failed"} · tap=$tapX,$tapY · sameRow=true"
                pendingAutoListEntry = current.copy(
                    clickDiagnostic = "${current.clickDiagnostic} · $retryDiagnostic",
                    fallbackTapAttemptedAtMillis = nowMillis,
                )
                logOperation(
                    eventType = "auto_entry_click_retry",
                    status = "order-list-auto-entry-action-click-fallback-tap",
                    orderSignature = current.listSignature,
                    mode = current.mode,
                    source = "order_list_auto_entry",
                    region = current.region,
                    confirmed = false,
                    reason = "ACTION_CLICK은 성공으로 반환됐지만 ${nowMillis - current.clickedAtMillis}ms 뒤에도 상세화면이 열리지 않아 같은 행 좌표를 보조 탭했습니다.",
                    clickDiagnostic = retryDiagnostic,
                    screenSummary = current.listSummary,
                    rawContext = current.clickDiagnostic,
                    createdAtMillis = nowMillis,
                )
                if (!accepted) {
                    saveDiagnosticCapture(
                        root = root,
                        packageName = packageName,
                        tag = "AUTO_ENTRY_FALLBACK_TAP_FAILED",
                        detail = "${current.mode.toKoreanLabel()} ${current.region.toKoreanLabel()} · $retryDiagnostic · ${current.clickDiagnostic}",
                    )
                }
            },
            AutoEntryActionClickFallbackDelayMillis,
        )
    }

    private fun scheduleAutoEntryNavigationTimeout(
        clickedAtMillis: Long,
        listSignature: String,
        delayMillis: Long = AutoEntryNavigationWaitMillis,
    ) {
        mainHandler.postDelayed(
            {
                val current = pendingAutoListEntry ?: return@postDelayed
                if (current.clickedAtMillis != clickedAtMillis || current.listSignature != listSignature) {
                    return@postDelayed
                }
                val root = rootInActiveWindow ?: return@postDelayed
                val nowMillis = System.currentTimeMillis()
                if (!root.hasInsungOrderListRows()) {
                    if (nowMillis - clickedAtMillis < AutoEntryPendingMillis) {
                        scheduleAutoEntryNavigationTimeout(
                            clickedAtMillis = clickedAtMillis,
                            listSignature = listSignature,
                            delayMillis = AutoEntryNavigationRecheckMillis,
                        )
                    } else {
                        pendingAutoListEntry = null
                        scheduleAutoEntryListRescan(80L)
                    }
                    return@postDelayed
                }
                clearPendingAutoEntryIfNavigationDidNotOpenDetail(
                    root = root,
                    nowMillis = nowMillis,
                )
            },
            delayMillis,
        )
    }

    private fun scheduleAutoEntryListRescan(vararg delaysMillis: Long) {
        delaysMillis
            .filter { it >= 0L }
            .distinct()
            .forEach { delayMillis ->
                mainHandler.postDelayed(
                    {
                        val root = rootInActiveWindow ?: return@postDelayed
                        val packageName = root.packageName?.toString().orEmpty()
                        if (!isSupportedInsungPackage(packageName)) return@postDelayed
                        if (!root.hasInsungOrderListRows()) return@postDelayed
                        if (root.hasVisibleOrderDetailTitle()) return@postDelayed
                        maybeAutoEnterOrderListPriority(
                            root = root,
                            packageName = packageName,
                            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                            capturedAtMillis = System.currentTimeMillis(),
                        )
                    },
                    delayMillis,
                )
            }
    }

    private fun maybeScheduleAutoEntryWarmListRescan(
        root: AccessibilityNodeInfo,
        packageName: String,
        eventType: Int,
        capturedAtMillis: Long,
    ) {
        if (!isSupportedInsungPackage(packageName)) return
        if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) return
        val mode = currentAutoEntryMode() ?: return
        if (capturedAtMillis < autoEntryListPausedUntilMillis) return
        if (activePendingAutoEntry(capturedAtMillis) != null) return
        if (root.hasVisibleOrderDetailTitle()) return

        val rootBounds = Rect().also(root::getBoundsInScreen)
        val regions = root.visibleNewOrderListRegions(rootBounds)
        if (regions.isEmpty()) return

        val signature = buildString {
            append(packageName)
            append('|')
            append(eventType)
            append('|')
            append(regions.sortedBy { it.ordinal }.joinToString(",") { it.name })
            append('|')
            append(nextAutoEntryRegion(mode).name)
        }
        if (
            signature == lastAutoEntryWarmListRescanSignature &&
            capturedAtMillis - lastAutoEntryWarmListRescanAtMillis < AutoEntryWarmListRescanCooldownMillis
        ) {
            return
        }
        lastAutoEntryWarmListRescanSignature = signature
        lastAutoEntryWarmListRescanAtMillis = capturedAtMillis
        scheduleAutoEntryListRescan(60L, 140L, 260L)
    }

    private fun clearPendingAutoEntryIfNavigationDidNotOpenDetail(
        root: AccessibilityNodeInfo,
        nowMillis: Long,
    ) {
        val pending = pendingAutoListEntry ?: return
        if (nowMillis - pending.clickedAtMillis < AutoEntryNavigationWaitMillis) return
        if (!root.hasInsungOrderListRows()) return
        lockAutoEntryListSignature(pending, nowMillis, AutoEntryClickFailureRetryMillis)
        advanceAutoEntryCandidateCursor(
            mode = pending.mode,
            region = pending.region,
            selectedIndexInRegion = pending.candidateIndexInRegion,
            candidateCountInRegion = pending.candidateCountInRegion,
        )
        advanceAutoEntryRegion(pending.mode, pending.region)
        val detailNotOpenLog = recordAutoEntryDetailNotOpen(pending, nowMillis)
        val detailNotOpenStatus = if (pending.mode == AutoEntryMode.Secondary) {
            "tracked-additional-detail-not-open"
        } else {
            "order-list-auto-entry-detail-not-open"
        }
        if (detailNotOpenLog != null) {
            saveDiagnosticCapture(
                root = root,
                packageName = root.packageName?.toString().orEmpty(),
                tag = "AUTO_ENTRY_DETAIL_NOT_OPEN",
                detail = "${pending.mode.toKoreanLabel()} ${pending.region.toKoreanLabel()} · elapsed=${nowMillis - pending.clickedAtMillis}ms · ${pending.listDistanceDiagnostic()} · ${pending.clickDiagnostic} · ${detailNotOpenLog.diagnosticSummary}",
            )
            logOperation(
                eventType = "auto_entry_detail_not_open",
                status = detailNotOpenStatus,
                orderSignature = pending.listSignature,
                mode = pending.mode,
                source = "order_list_auto_entry",
                region = pending.region,
                confirmed = false,
                reason = "클릭 후 ${nowMillis - pending.clickedAtMillis}ms 동안 상세화면이 열리지 않았습니다. · ${detailNotOpenLog.historySummary}",
                clickDiagnostic = pending.clickDiagnostic,
                screenSummary = pending.listSummary,
                rawContext = "${pending.listDistanceDiagnostic()} · ${pending.clickDiagnostic}",
                createdAtMillis = nowMillis,
            )
        }
        // Navigation misses should not slow new-order pickup; only repeated logs are compressed.
        decrementAutoEntryCheckCount(pending.mode)
        Log.d(
            LogTag,
            "order-list-auto-entry: pending cleared because detail did not open summary=${pending.listSummary.take(80)}",
        )
        if (detailNotOpenLog != null) {
            serviceScope.launch {
                orderEventRepository.logEvent(
                    OrderEventDraft(
                        orderTitle = pending.listSummary.ifBlank { "오더리스트 자동상세확정" },
                        originSummary = "리스트 오더",
                        destinationSummary = "상세 진입 실패",
                        price = 0,
                        status = detailNotOpenStatus,
                        failureReason = "${pending.mode.toKoreanLabel()} 자동상세확정 · ${pending.region.toKoreanLabel()} · ${pending.listDistanceDiagnostic()} · 클릭 후 ${nowMillis - pending.clickedAtMillis}ms 동안 상세화면이 열리지 않았습니다. · ${pending.clickDiagnostic} · ${detailNotOpenLog.historySummary}",
                    ),
                )
            }
        }
        pendingAutoListEntry = null
        scheduleAutoEntryListRescan(80L, 350L)
    }

    private fun recordAutoEntryDetailNotOpen(
        pending: PendingAutoListEntry,
        nowMillis: Long,
    ): AutoEntryDetailNotOpenLog? {
        trimAutoEntryDetailNotOpenLogStates(nowMillis)
        val key = "${pending.mode.name}:${pending.listSignature}"
        val previous = autoEntryDetailNotOpenLogStates[key]
        val totalCount = (previous?.totalCount ?: 0) + 1
        val lastLoggedAtMillis = previous?.lastLoggedAtMillis ?: 0L
        val shouldLog = previous == null ||
            nowMillis - lastLoggedAtMillis >= AutoEntryDetailNotOpenLogCooldownMillis
        val loggedCount = if (shouldLog) totalCount else previous.loggedCount
        autoEntryDetailNotOpenLogStates[key] = AutoEntryDetailNotOpenLogState(
            totalCount = totalCount,
            loggedCount = loggedCount,
            lastLoggedAtMillis = if (shouldLog) nowMillis else lastLoggedAtMillis,
            lastSeenAtMillis = nowMillis,
        )
        if (!shouldLog) return null
        val repeatedCount = (totalCount - (previous?.loggedCount ?: 0)).coerceAtLeast(1)
        return AutoEntryDetailNotOpenLog(
            repeatedCount = repeatedCount,
            totalCount = totalCount,
        )
    }

    private fun trimAutoEntryDetailNotOpenLogStates(nowMillis: Long) {
        val expiredKeys = autoEntryDetailNotOpenLogStates
            .filterValues { state -> nowMillis - state.lastSeenAtMillis >= AutoEntryDetailNotOpenStateKeepMillis }
            .keys
            .toList()
        expiredKeys.forEach(autoEntryDetailNotOpenLogStates::remove)
    }

    private fun AccessibilityNodeInfo.hasInsungOrderListRows(): Boolean {
        var found = false
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || found) return
            if (node.isVisibleToUser && node.isInsungOrderListRowNode()) {
                found = true
                return
            }
            repeat(node.childCount) { index ->
                visit(node.getChild(index))
            }
        }
        visit(this)
        return found
    }

    private fun lockAutoEntryListSignature(
        autoEntry: PendingAutoListEntry,
        nowMillis: Long,
        reliableLockMillis: Long,
    ) {
        val lockMillis = if (autoEntry.listSignatureReliable) {
            reliableLockMillis
        } else {
            reliableLockMillis.coerceAtMost(AutoEntryUnreliableListLockMillis)
        }
        autoEntryListLocks[autoEntry.listSignature] = nowMillis + lockMillis
    }

    private fun resetAutoEntryCycleIfNeeded(
        mode: AutoEntryMode,
        nowMillis: Long,
    ) {
        if (lastAutoEntryMode != mode || nowMillis - lastAutoListEntryAtMillis > AutoEntryCycleIdleResetMillis) {
            when (mode) {
                AutoEntryMode.Primary -> {
                    primaryAutoEntryCheckCount = 0
                    primaryAutoEntryConfirmedCount = 0
                }
                AutoEntryMode.Secondary -> {
                    secondaryAutoEntryCheckCount = 0
                    secondaryAutoEntryConfirmedCount = 0
                }
            }
            lastAutoEntryMode = mode
            clearAutoEntryCandidateCursors(mode)
        }
    }

    private fun autoEntryCheckCount(mode: AutoEntryMode): Int {
        return when (mode) {
            AutoEntryMode.Primary -> primaryAutoEntryCheckCount
            AutoEntryMode.Secondary -> secondaryAutoEntryCheckCount
        }
    }

    private fun incrementAutoEntryCheckCount(mode: AutoEntryMode) {
        when (mode) {
            AutoEntryMode.Primary -> primaryAutoEntryCheckCount += 1
            AutoEntryMode.Secondary -> secondaryAutoEntryCheckCount += 1
        }
    }

    private fun decrementAutoEntryCheckCount(mode: AutoEntryMode) {
        when (mode) {
            AutoEntryMode.Primary -> primaryAutoEntryCheckCount = (primaryAutoEntryCheckCount - 1).coerceAtLeast(0)
            AutoEntryMode.Secondary -> secondaryAutoEntryCheckCount = (secondaryAutoEntryCheckCount - 1).coerceAtLeast(0)
        }
    }

    private fun nextAutoEntryRegion(mode: AutoEntryMode): AutoEntryListRegion {
        return when (mode) {
            AutoEntryMode.Primary -> primaryAutoEntryNextRegion
            AutoEntryMode.Secondary -> secondaryAutoEntryNextRegion
        }
    }

    private fun autoEntryCandidateCursor(
        mode: AutoEntryMode,
        region: AutoEntryListRegion,
    ): Int {
        return autoEntryCandidateCursors[autoEntryCandidateCursorKey(mode, region)] ?: 0
    }

    private fun advanceAutoEntryCandidateCursor(
        mode: AutoEntryMode,
        region: AutoEntryListRegion,
        selectedIndexInRegion: Int,
        candidateCountInRegion: Int,
    ) {
        if (candidateCountInRegion <= 1) {
            autoEntryCandidateCursors.remove(autoEntryCandidateCursorKey(mode, region))
            return
        }
        autoEntryCandidateCursors[autoEntryCandidateCursorKey(mode, region)] =
            selectedIndexInRegion.coerceAtLeast(1) % candidateCountInRegion
    }

    private fun clearAutoEntryCandidateCursors(mode: AutoEntryMode) {
        autoEntryCandidateCursors.keys
            .filter { key -> key.startsWith("${mode.name}:") }
            .toList()
            .forEach(autoEntryCandidateCursors::remove)
    }

    private fun autoEntryCandidateCursorKey(
        mode: AutoEntryMode,
        region: AutoEntryListRegion,
    ): String = "${mode.name}:${region.name}"

    private fun advanceAutoEntryRegion(
        mode: AutoEntryMode,
        selectedRegion: AutoEntryListRegion,
    ) {
        val nextRegion = selectedRegion.opposite()
        when (mode) {
            AutoEntryMode.Primary -> primaryAutoEntryNextRegion = nextRegion
            AutoEntryMode.Secondary -> secondaryAutoEntryNextRegion = nextRegion
        }
    }

    private fun isAutoEntryConfirmLimitReached(mode: AutoEntryMode): Boolean {
        return when (mode) {
            AutoEntryMode.Primary -> primaryAutoEntryConfirmedCount >= PrimaryAutoEntryConfirmLimit
            AutoEntryMode.Secondary -> trackingAdditionalConfirmedCount >= TrackingAdditionalAutoConfirmLimit ||
                secondaryAutoEntryConfirmedCount >= orderTrackingConfirmLimit()
        }
    }

    private fun orderTrackingConfirmLimit(): Int {
        return TrackingAdditionalAutoConfirmLimit
    }

    private fun maxAutoEntryChecksPerCycle(): Int {
        return activeSettings.orderListAutoEntryMaxChecksText
            .trim()
            .toIntOrNull()
            ?.coerceIn(1, 30)
            ?: DefaultAutoEntryMaxChecksPerCycle
    }

    private fun isAutoEntryListLocked(
        signature: String,
        nowMillis: Long,
    ): Boolean {
        val lockedUntil = autoEntryListLocks[signature] ?: return false
        return nowMillis < lockedUntil
    }

    private fun trimAutoEntryLocks(nowMillis: Long) {
        val expiredListLocks = autoEntryListLocks
            .filterValues { lockedUntil -> nowMillis >= lockedUntil }
            .keys
            .toList()
        expiredListLocks.forEach(autoEntryListLocks::remove)

        val expiredDetails = autoEntryCheckedDetailSignatures
            .filterValues { checkedAt -> nowMillis - checkedAt >= AutoEntryCheckedDetailKeepMillis }
            .keys
            .toList()
        expiredDetails.forEach(autoEntryCheckedDetailSignatures::remove)

        val expiredRegionLocks = autoEntryRegionLocks
            .filterValues { lockedUntil -> nowMillis >= lockedUntil }
            .keys
            .toList()
        expiredRegionLocks.forEach(autoEntryRegionLocks::remove)

    }

    private fun isAutoEntryRegionLocked(
        region: AutoEntryListRegion,
        nowMillis: Long,
    ): Boolean {
        val lockedUntil = autoEntryRegionLocks[region] ?: return false
        return nowMillis < lockedUntil
    }

    private fun logOrderListAutoEntry(
        status: String,
        candidate: OrderListAutoEntryCandidate,
        mode: AutoEntryMode,
        reason: String,
    ) {
        Log.d(LogTag, "order-list-auto-entry: status=$status mode=$mode summary=${candidate.summary.take(80)} reason=$reason")
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = candidate.summary.ifBlank { "오더리스트 자동상세확정" },
                    originSummary = "리스트 오더",
                    destinationSummary = "상세 진입 전",
                    price = 0,
                    status = status,
                    failureReason = reason,
                ),
            )
        }
    }

    private fun logOrderListTextExclusion(
        status: String,
        summary: String,
        mode: AutoEntryMode,
        reason: String,
    ) {
        Log.d(LogTag, "order-list-auto-entry: status=$status mode=$mode summary=${summary.take(80)} reason=$reason")
        logOperation(
            eventType = "auto_entry_list_excluded",
            status = status,
            mode = mode,
            source = "order_list",
            confirmed = false,
            reason = reason,
            screenSummary = summary,
            rawContext = reason,
            createdAtMillis = System.currentTimeMillis(),
        )
        serviceScope.launch {
            orderEventRepository.logEvent(
                OrderEventDraft(
                    orderTitle = summary.ifBlank { "오더리스트 자동 제외" },
                    originSummary = "리스트 오더",
                    destinationSummary = "상세 진입 전 제외",
                    price = 0,
                    status = status,
                    failureReason = reason,
                ),
            )
        }
    }

    private fun OrderListAutoEntryCandidate.listDistanceDiagnostic(): String {
        return buildList {
            add("리스트거리정렬=사용안함")
            if (!signatureReliable) add("리스트텍스트=접근성미노출")
        }.joinToString(", ")
    }

    private fun PendingAutoListEntry.listDistanceDiagnostic(): String {
        return buildList {
            add("리스트거리정렬=사용안함")
            if (!listSignatureReliable) add("리스트텍스트=접근성미노출")
        }.joinToString(", ")
    }

    private fun buildAutoEntryClickDiagnostic(
        root: AccessibilityNodeInfo,
        mode: AutoEntryMode,
        candidate: OrderListAutoEntryCandidate,
        clickAttempt: AutoEntryClickAttempt,
    ): String {
        return buildList {
            add("mode=${mode.toKoreanLabel()}")
            add("region=${candidate.region.toKoreanLabel()}")
            add("method=${clickAttempt.method}")
            add("accepted=${clickAttempt.accepted}")
            add("actionClick=${clickAttempt.actionClickSucceeded}")
            add("gesture=${clickAttempt.gestureAccepted}")
            add("tap=${clickAttempt.tapX},${clickAttempt.tapY}")
            add("rowBounds=${candidate.bounds.toDiagnosticBounds()}")
            add("clickBounds=${clickAttempt.bounds.toDiagnosticBounds()}")
            add("rootBounds=${candidate.rootBounds.toDiagnosticBounds()}")
            add("rowIndex=${candidate.candidateIndexInRegion}/${candidate.candidateCountForRegion()}")
            add("rowCursor=${autoEntryCandidateCursor(mode, candidate.region)}")
            add("visibleRows=top:${candidate.candidateCountTop},bottom:${candidate.candidateCountBottom}")
            add("nodeId=${candidate.nodeViewId.substringAfterLast('/').ifBlank { "none" }}")
            add("nodeClass=${candidate.nodeClassName.substringAfterLast('.').ifBlank { "unknown" }}")
            add("nodeClickable=${candidate.nodeClickable}")
            add("tabs=${root.insungTabDiagnostic()}")
            add(candidate.listDistanceDiagnostic())
            add("summary=${candidate.summary.shortDiagnosticText()}")
        }.joinToString(" · ")
    }

    private fun AccessibilityNodeInfo.insungTabDiagnostic(): String {
        val tabs = mutableListOf<String>()

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || tabs.size >= 8) return
            val id = node.viewIdResourceName.orEmpty()
            if (
                id.endsWith(":id/kor_btnNew") ||
                id.endsWith(":id/q_btnNew") ||
                id.endsWith(":id/kor_btnComplete") ||
                id.endsWith(":id/q_btnComplete") ||
                id.endsWith(":id/kor_btnMessage") ||
                id.endsWith(":id/q_btnMessage")
            ) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val label = when {
                    id.contains("btnNew") -> "신규"
                    id.contains("btnComplete") -> "완료"
                    id.contains("btnMessage") -> "메시지"
                    else -> id.substringAfterLast('/')
                }
                tabs += "$label(selected=${node.isSelected},focused=${node.isFocused},enabled=${node.isEnabled},bounds=${bounds.toDiagnosticBounds()})"
            }
            repeat(node.childCount) { index ->
                visit(node.getChild(index))
            }
        }

        visit(this)
        return tabs.joinToString(";").ifBlank { "탭정보없음" }.take(220)
    }

    private fun Rect.toDiagnosticBounds(): String =
        "[${left},${top}][${right},${bottom}]"

    private fun OrderListAutoEntryCandidate.candidateCountForRegion(): Int {
        return when (region) {
            AutoEntryListRegion.Top -> candidateCountTop
            AutoEntryListRegion.Bottom -> candidateCountBottom
        }.coerceAtLeast(1)
    }

    private fun ParsedOrderDraft.farePerKmDiagnostic(): String? {
        val priceValue = price?.takeIf { it > 0 } ?: return null
        val routeKm = pickupToDropoffDistanceKm?.takeIf { it > 0.0 } ?: return "운임분석=상차하차거리미확인"
        val farePerKm = priceValue / routeKm
        return "운임분석=상차하차직선km:${routeKm.formatDistanceKm()},원km:${String.format(Locale.US, "%.0f", farePerKm)}"
    }

    private fun Int?.farePerKm(distanceKm: Double?): Double? {
        val priceValue = this?.takeIf { it > 0 } ?: return null
        val distanceValue = distanceKm?.takeIf { it > 0.0 } ?: return null
        return priceValue / distanceValue
    }

    private fun AutoEntryMode.toOperationModeLabel(): String {
        return when (this) {
            AutoEntryMode.Primary -> "primary"
            AutoEntryMode.Secondary -> "tracking"
        }
    }

    private fun isOrderProcessingLocked(
        signature: String,
        nowMillis: Long,
    ): Boolean {
        trimProcessedOrderLocks(nowMillis)
        val lockedAtMillis = processedOrderLocks[signature] ?: return false
        return nowMillis - lockedAtMillis < ProcessedOrderLockMillis
    }

    private fun markOrderProcessingLocked(
        signature: String,
        nowMillis: Long,
    ) {
        processedOrderLocks[signature] = nowMillis
        trimProcessedOrderLocks(nowMillis)
        while (processedOrderLocks.size > MaxProcessedOrderLocks) {
            val firstKey = processedOrderLocks.keys.firstOrNull() ?: return
            processedOrderLocks.remove(firstKey)
        }
    }

    private fun trimProcessedOrderLocks(nowMillis: Long) {
        val expiredKeys = processedOrderLocks
            .filterValues { lockedAtMillis -> nowMillis - lockedAtMillis >= ProcessedOrderLockMillis }
            .keys
            .toList()
        expiredKeys.forEach(processedOrderLocks::remove)
    }

    private fun Long.toLocalDateTime(): LocalDateTime {
        return Instant.ofEpochMilli(this)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
    }

    private fun Long.toOperationClockText(): String {
        val dateTime = toLocalDateTime()
        return String.format(
            Locale.US,
            "%02d:%02d:%02d.%03d",
            dateTime.hour,
            dateTime.minute,
            dateTime.second,
            (this % 1000L).coerceAtLeast(0L),
        )
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private fun String.sanitizeForCapture(): String =
        replace("\n", " ")
            .replace("\r", " ")
            .replace("\"", "'")
            .trim()
            .take(MaxTokenLength)

    private fun String.normalizeLogSignature(): String =
        lowercase(Locale.KOREAN)
            .replace(Regex("""\s+"""), "")
            .take(MaxAutoEntrySignatureLength)

    private fun Long.formatElapsedMinutes(): String {
        val totalSeconds = (this / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return if (minutes > 0L) {
            "${minutes}분 ${seconds}초"
        } else {
            "${seconds}초"
        }
    }

    private fun Int.toReadableEventType(): String = when (this) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "Window changed"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "Content changed"
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> "List scrolled"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "View clicked"
        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "Notification changed"
        else -> "Event $this"
    }

    companion object {
        private const val LogTag = "CatchProOCR"
        private const val InsungQuickPackage = "insung.split.quick"
        private const val InsungPackageFilter = "insung"
        private const val TmapPackageName = "com.skt.tmap.ku"
        private val KakaoNavigationPackageNames = setOf(
            "net.daum.android.map",
            "com.locnall.KimGiSa",
            "com.kakao.navi",
        )
        private const val AlertChannelId = "catchpro_alerts"
        private const val ManualReviewNotificationId = 4101
        private const val ConfirmedNotificationId = 4102
        private const val CaptureCooldownMillis = 1_500L
        private const val DiagnosticCaptureCooldownMillis = 2_500L
        private const val ConfirmAttemptVerificationWindowMillis = 10_000L
        private const val ConfirmDuplicateSuppressMillis = 350L
        private const val ConfirmDuplicateSuppressLogCooldownMillis = 2_000L
        private const val ConfirmFailureTextLogCooldownMillis = 5_000L
        private const val AlertDeliveryFailureLogCooldownMillis = 300_000L
        private const val PostConfirmAutoEntryPauseMillis = 900L
        private const val MaxPendingConfirmAttempts = 20
        private const val RoadDistanceCacheMillis = 30_000L
        private const val AutoCancelRetryCooldownMillis = 90_000L
        private const val ManualPromptCooldownMillis = 4_000L
        private const val ManualDismissCooldownMillis = 60_000L
        private const val ManualInputRequiredCooldownMillis = 10_000L
        private const val TmapArrivalLogCooldownMillis = 60_000L
        private const val TmapArrivalMatchWindowMillis = 2L * 60L * 60L * 1000L
        private const val NavigationDestinationSyncCooldownMillis = 60_000L
        private const val NavigationDestinationMissLogCooldownMillis = 30_000L
        private const val NavigationAddressSyncContextKeepMillis = 2L * 60L * 1000L
        private const val NavigationAddressContextLogCooldownMillis = 30_000L
        private const val PickupPromptLogCooldownMillis = 30_000L
        private const val PickupCompletionPromptKeepMillis = 20_000L
        private const val PickupConfirmedLogCooldownMillis = 30_000L
        private const val DropoffPromptLogCooldownMillis = 30_000L
        private const val DropoffActionLogCooldownMillis = 30_000L
        private const val DropoffConfirmedLogCooldownMillis = 30_000L
        private const val ImmediateCancelMatchWindowMillis = 20_000L
        private const val AutoEntryGlobalCooldownMillis = 800L
        private const val AutoEntryClickFailureRetryMillis = 2_000L
        private const val AutoEntryPendingMillis = 6_000L
        private const val AutoEntryNavigationWaitMillis = 700L
        private const val AutoEntryNavigationRecheckMillis = 350L
        private const val AutoEntryActionClickFallbackDelayMillis = 350L
        private const val AutoEntryGestureTapDurationMillis = 80L
        private const val AutoEntryRegionRetryCooldownMillis = 350L
        private const val AutoEntryWarmListRescanCooldownMillis = 250L
        private const val AutoEntryBackDelayMillis = 350L
        private const val AutoEntryCycleIdleResetMillis = 60_000L
        private const val AutoEntryRejectedListLockMillis = 10L * 60L * 1000L
        private const val AutoEntryUnreliableListLockMillis = 5_000L
        private const val AutoEntryDetailNotOpenLogCooldownMillis = 30_000L
        private const val AutoEntryDetailNotOpenStateKeepMillis = 10L * 60L * 1000L
        private const val AutoEntryCheckedDetailKeepMillis = 6L * 60L * 60L * 1000L
        private const val TrackingAutoEntryGateBlockLogCooldownMillis = 30_000L
        private const val SecondaryEstimatedRoadDistanceMultiplier = 1.5
        private const val AddressCopyOverlayBottomOffsetDp = 96
        private const val DetailActionButtonMinYRatio = 0.78f
        private const val MaxRoadDistanceCacheEntries = 20
        private const val MaxRouteAddressCandidates = 5
        private const val MaxAutoEntrySummaryLength = 180
        private const val MaxAutoEntrySignatureLength = 220
        private const val MaxOperationReasonLength = 1_000
        private const val MaxOperationRawContextLength = 2_000
        private const val DefaultAutoEntryMaxChecksPerCycle = 30
        private const val PrimaryAutoEntryConfirmLimit = 2
        private const val SecondaryAutoEntryConfirmLimit = 2
        private const val TrackingAdditionalAutoConfirmLimit = 1
        private const val TrackingPickupRouteDetourLimitKm = 4.0
        private const val TrackingPickupToDropoffLimitKm = 8.0
        private const val RouteOrderSlotCount = 2
        private const val ManualRouteAddressSlotCount = 6
        private const val MaxStoredCaptures = 1_000
        private const val DailyLogCleanupCheckIntervalMillis = 5L * 60L * 1000L
        private const val DailyLogResetMinimumDelayMillis = 1_000L
        private const val CaptureCleanupIntervalMillis = 30L * 60L * 1000L
        private const val CaptureCompactIntervalMillis = 6L * 60L * 60L * 1000L
        private const val ProcessedOrderLockMillis = 10L * 60L * 1000L
        private const val MaxProcessedOrderLocks = 60
        private const val MaxDepth = 6
        private const val MaxNodes = 180
        private const val MaxPreviewTokens = 8
        private const val MaxSummaryLength = 240
        private const val MaxTokenLength = 120
        private const val MaxNavigationTextTokens = 80
        private const val MaxNavigationSummaryLength = 1_200
        private const val InsungDetailAddressSyncCooldownMillis = 10L * 60L * 1000L
        private const val InsungDetailAddressMissLogCooldownMillis = 30_000L
        private const val MaxRecentInsungDetailAddressSyncSignatures = 30
        private const val MaxClickDiagnosticAncestorDepth = 6
        private const val PreserveRouteAddressesOnCancelForVerification = false

        private val RelevantEvents = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
        )

        private val RouteCityOrDistrictRegex = Regex("(시|군|구)")
        private val RouteTownOrRoadRegex = Regex("(동|읍|면|리|가|로|길)")
        private val RouteAdministrativeWordRegex = Regex(""".*(특별시|광역시|특별자치시|특별자치도|도|시|군|구|동|읍|면|리|가)$""")
        private val RouteAdministrativeTownWordRegex = Regex(""".*(동|읍|면|리|가)$""")
        private val RouteRoadAddressRegex = Regex("""[가-힣A-Za-z0-9]+(로|길)\s*\d+""")
        private val RouteArrowRegex = Regex("""(.{2,90}?)\s*(?:->|→|⇒|▶|~)\s*(.{2,90})""")
        private val RouteDistanceTextRegex = Regex("""\(?\s*\d+(?:\.\d+)?\s*km\s*\)?""", RegexOption.IGNORE_CASE)
        private val RouteAddressLabelRegex = Regex("""(상차지|출발지|상차|출발|하차지|도착지|하차|도착)\s*[:：]?""")
        private val InsungManualAddressDetailScreenRegex = Regex(
            """(출발지|상차지|도착지|하차지|출발|상차|도착|하차)\s*상세|길안내""",
        )
        private val AutoEntryPriceRegex = Regex("""(\d{1,3}(?:,\d{3})+|\d{4,})\s*원?""")
        private val AutoEntryDistanceRegex = Regex("""\d+(?:\.\d+)?\s*km""", RegexOption.IGNORE_CASE)
        private val InsungConfirmFailureRegex = Regex(
            """(다른\s*기사(?:님)?\s*(?:에게|한테|께|가|이)?\s*(?:배정|확정|수락)|타\s*기사\s*(?:배정|확정|수락)|이미\s*배정|배정\s*되었습니다|배정되었습니다|마감(?:된|되었습니다|입니다)?|이미\s*마감|이미\s*완료|이미\s*처리|취소된\s*오더|선점(?:된|되었습니다|입니다)?)""",
        )
        private val TmapArrivalStrongRegex = Regex(
            """(목적지\s*에\s*도착|도착\s*했\s*습니다|도착\s*하\s*였습니다|안내\s*를?\s*종료|경로\s*안내\s*종료)""",
            RegexOption.IGNORE_CASE,
        )
        private val NavigationAddressRegexes = listOf(
            Regex(
                """(?:서울|서울특별시|경기|경기도|인천|인천광역시|부산|부산광역시|대구|대구광역시|광주|광주광역시|대전|대전광역시|울산|울산광역시|세종|세종특별자치시|강원|강원특별자치도|충북|충청북도|충남|충청남도|전북|전북특별자치도|전남|전라남도|경북|경상북도|경남|경상남도|제주|제주특별자치도)?\s*[가-힣A-Za-z0-9]+(?:시|군|구)\s+(?:[가-힣A-Za-z0-9]+구\s+)?(?:[가-힣A-Za-z0-9]+(?:동|읍|면)\s+)?[가-힣A-Za-z0-9]+(?:동|리|가)\s+\d+(?:-\d+)?(?:\s*\([^)]{2,50}\))?(?:\s+[가-힣A-Za-z0-9().·\-]{2,24}){0,4}""",
            ),
            Regex(
                """(?:서울|경기|경기도|인천|부산|대구|광주|대전|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주)?\s*[가-힣A-Za-z0-9]+(?:시|군|구)\s+[가-힣A-Za-z0-9]+(?:동|읍|면|리|가)\s+\d+(?:-\d+)?(?:\s*\([^)]{2,50}\))?(?:\s+[가-힣A-Za-z0-9().·\-]{2,24}){0,4}""",
            ),
            Regex(
                """(?:서울|경기|경기도|인천|부산|대구|광주|대전|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주)?\s*[가-힣A-Za-z0-9]+(?:시|군|구)\s+(?:[가-힣A-Za-z0-9]+(?:동|읍|면|리|가)\s+)?[가-힣A-Za-z0-9]+(?:로|길)\s*\d+(?:-\d+)?(?:\s+[가-힣A-Za-z0-9().·\-]{2,24}){0,4}""",
            ),
        )
        private val NavigationLeadingTownBeforeProvinceRegex = Regex(
            """^[가-힣A-Za-z0-9]+(?:동|읍|면|리|가)\s+(?=(?:서울|경기|경기도|인천|부산|대구|광주|대전|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주))""",
        )
        private val NavigationNextAddressRegex = Regex(
            """(\d+(?:-\d+)?(?:\s*\([^)]{2,50}\))?)\s+(?=(?:서울|경기|경기도|인천|부산|대구|광주|대전|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주)\s*[가-힣A-Za-z0-9]+(?:시|군|구))""",
        )
        private val NavigationNoisePrefixRegex = Regex(
            """^(목적지|도착지|도착|출발|경유지|주소|위치|검색결과|장소)\s*[:：]?\s*""",
            RegexOption.IGNORE_CASE,
        )
        private val NavigationAddressStopRegex = Regex(
            """\s+(?:방면|길안내|안내시작|경로|출발|도착|목적지|전화|공유|저장|검색|상세|리뷰|거리뷰|지도|내비|네비|분|시간|km|m)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val PickupCompletionPromptRegex = Regex(
            """픽업\s*완료.*?(하시겠습니까|확인|예|아니오)|픽업완료.*?(하시겠습니까|확인|예|아니오)""",
            RegexOption.IGNORE_CASE,
        )
        private val DropoffCompletionPromptRegex = Regex(
            """(전송\s*하\s*(?:시\s*)?겠\s*습니까|사진.*전송|수령.*사진|인수증.*전송|저장.*전송|서명.*전송|배송\s*완료|하차\s*완료|완료\s*하\s*(?:시\s*)?겠\s*습니까)""",
            RegexOption.IGNORE_CASE,
        )
        private val DropoffSendActionTextRegex = Regex(
            """^(전송|저장)$|저장\s*후\s*전송|사진.*전송|서명.*전송|인수증.*전송""",
            RegexOption.IGNORE_CASE,
        )
        private val DropoffSendContextRegex = Regex(
            """(서명|인수증|사진|수령|전송|카메라|촬영|배송\s*완료|하차\s*완료)""",
            RegexOption.IGNORE_CASE,
        )
        private val AutoEntryRouteSignalRegex = Regex("""(→|->|⇒|▶|상차|하차|출발|도착)""")
        private val AutoEntryVehicleOrPaymentRegex = Regex("""(오토|다마스|라보|톤|편도|왕복|착불|선불|카드|현금)""")
        private val AutoEntryExcludedTextRegex = Regex(
            """(오더상세|출발지상세|도착지상세|상차지상세|하차지상세|닫기|영수증|위치보기|위치저장|길안내|카드승인|민원|탁송|확정|취소|완료|상세주소복사)""",
        )
        private val AddressCandidateBoundaryRegex = Regex(
            """(상차지|출발지|상차|출발|하차지|도착지|하차|도착|요금|차종|차량|적요|현위치|확정|취소|선불|착불|카드|현금)""",
        )
        private val PickupAddressLabels = listOf("상차지", "출발지", "상차", "출발")
        private val DropoffAddressLabels = listOf("하차지", "도착지", "하차", "도착")
        private val VerifiedTrackingZones = listOf(
            VerifiedTrackingZone(
                name = "강남-서초-송파권",
                aliases = setOf("강남구", "서초구", "송파구", "역삼동", "논현동", "삼성동", "양재동", "문정동"),
            ),
            VerifiedTrackingZone(
                name = "구로-금천권",
                aliases = setOf("구로구", "금천구", "가산동", "독산동", "구로동", "가산디지털단지"),
            ),
            VerifiedTrackingZone(
                name = "영등포-양천-강서권",
                aliases = setOf("영등포구", "양천구", "강서구", "목동", "마곡동", "가양동", "문래동"),
            ),
            VerifiedTrackingZone(
                name = "분당-판교-수지권",
                aliases = setOf("분당구", "판교", "삼평동", "수내동", "정자동", "서현동", "수지구", "죽전동"),
            ),
            VerifiedTrackingZone(
                name = "죽전-보정-구성권",
                aliases = setOf("죽전동", "보정동", "구성동", "마북동", "신갈동"),
            ),
            VerifiedTrackingZone(
                name = "동탄-병점-오산권",
                aliases = setOf("동탄", "반송동", "석우동", "청계동", "영천동", "병점동", "오산시", "세교동", "가장동"),
            ),
            VerifiedTrackingZone(
                name = "남사-이동권",
                aliases = setOf("남사읍", "이동읍", "완장리", "아곡리", "서리"),
            ),
            VerifiedTrackingZone(
                name = "원삼-백암권",
                aliases = setOf("원삼면", "백암면", "좌항리", "백암리", "고안리"),
            ),
            VerifiedTrackingZone(
                name = "시화-반월권",
                aliases = setOf("시화", "정왕동", "반월", "원시동", "성곡동", "안산시 단원구"),
            ),
        )

        private val IgnoredPackages = setOf(
            "com.catchpro.app",
            "com.android.systemui",
        )

        private val IgnoredPackagePrefixes = listOf(
            "com.google.android.inputmethod",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.permissioncontroller",
        )

        fun isEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, CatchProAccessibilityService::class.java)
            val enabledServices = Settings.Secure
                .getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
                .orEmpty()

            return enabledServices
                .split(':')
                .mapNotNull(ComponentName::unflattenFromString)
                .any { it == componentName }
        }
    }

    private data class TrackedAutoConfirmedOrder(
        val signature: String,
        val orderTitle: String,
        val origin: String,
        val destination: String,
        val price: Int,
        val confirmedAtMillis: Long,
        val isSecondary: Boolean,
        val confirmedByUser: Boolean,
    )

    private data class PendingConfirmAttempt(
        val signature: String,
        val draft: ParsedOrderDraft,
        val reasons: List<String>,
        val isSecondary: Boolean,
        val confirmedByUser: Boolean,
        val attemptedAtMillis: Long,
        val autoEntryMode: AutoEntryMode?,
        val autoEntryRegion: AutoEntryListRegion?,
        val autoEntryReason: String?,
        val autoEntryClickDiagnostic: String?,
        val confirmClickDiagnostic: String?,
        val confirmSource: String,
    ) {
        fun toTrackedOrder(confirmedAtMillis: Long = attemptedAtMillis): TrackedAutoConfirmedOrder =
            TrackedAutoConfirmedOrder(
                signature = signature,
                orderTitle = draft.effectiveRouteText() ?: "인성 상세 오더",
                origin = draft.effectiveOrigin() ?: draft.requesterLocation ?: "미확인",
                destination = draft.effectiveDestination() ?: "미확인",
                price = draft.price ?: 0,
                confirmedAtMillis = confirmedAtMillis,
                isSecondary = isSecondary,
                confirmedByUser = confirmedByUser,
            )
    }

    private data class PendingManualConfirmation(
        val signature: String,
        val draft: ParsedOrderDraft,
        val reasons: List<String>,
        val isSecondary: Boolean,
        val requestedAtMillis: Long,
    )

    private data class RoadDistanceEvaluation(
        val pickupDistanceKm: Double? = null,
        val pickupFailureReason: String? = null,
        val pickupDistanceLabel: String? = null,
        val destinationRadiusDistanceKm: Double? = null,
        val destinationRadiusFailureReason: String? = null,
        val destinationRadiusDistanceLabel: String? = null,
    )

    private data class EstimatedDistanceOutcome(
        val straightDistanceKm: Double? = null,
        val distanceKm: Double? = null,
        val failureReason: String? = null,
    ) {
        companion object {
            fun success(
                straightDistanceKm: Double,
                distanceKm: Double,
            ): EstimatedDistanceOutcome = EstimatedDistanceOutcome(
                straightDistanceKm = straightDistanceKm,
                distanceKm = distanceKm,
            )

            fun failure(reason: String): EstimatedDistanceOutcome = EstimatedDistanceOutcome(
                failureReason = reason,
            )
        }
    }

    private data class CachedRoadDistanceEvaluation(
        val evaluation: RoadDistanceEvaluation,
        val cachedAtMillis: Long,
    )

    private data class PrimaryDestinationArea(
        val label: String,
        val matchKeys: Set<String>,
    )

    private data class TrackingDestinationCompatibility(
        val reason: String,
    )

    private data class PendingNavigationAddressSyncContext(
        val addressSlotIndex: Int,
        val role: RouteAddressRole,
        val orderSignature: String,
        val capturedAtMillis: Long,
        val screenSummary: String,
    )

    private data class PendingPickupCompletionPrompt(
        val signature: String,
        val parsedDraft: ParsedOrderDraft,
        val capturedAtMillis: Long,
        val screenSummary: String,
    )

    private data class TrackingDestinationArea(
        val label: String,
        val provinceKey: String?,
        val districtKeys: Set<String>,
        val townKeys: Set<String>,
        val villageKeys: Set<String>,
    )

    private data class VerifiedTrackingZone(
        val name: String,
        val aliases: Set<String>,
    )

    private data class PendingAutoListEntry(
        val listSignature: String,
        val listSummary: String,
        val listSignatureReliable: Boolean,
        val mode: AutoEntryMode,
        val region: AutoEntryListRegion,
        val clickedAtMillis: Long,
        val clickDiagnostic: String,
        val tapX: Int,
        val tapY: Int,
        val candidateIndexInRegion: Int = 1,
        val candidateCountInRegion: Int = 1,
        val fallbackTapAttemptedAtMillis: Long? = null,
    )

    private data class OrderListAutoEntryCandidate(
        val node: AccessibilityNodeInfo,
        val summary: String,
        val signature: String,
        val signatureReliable: Boolean,
        val region: AutoEntryListRegion,
        val bounds: Rect,
        val rootBounds: Rect,
        val nodeViewId: String,
        val nodeClassName: String,
        val nodeClickable: Boolean,
        val candidateIndexOverall: Int = 1,
        val candidateIndexInRegion: Int = 1,
        val candidateCountTop: Int = 0,
        val candidateCountBottom: Int = 0,
    )

    private data class AutoEntryClickAttempt(
        val accepted: Boolean,
        val method: String,
        val actionClickSucceeded: Boolean,
        val gestureAccepted: Boolean,
        val bounds: Rect,
        val tapX: Int,
        val tapY: Int,
    )

    private data class AutoEntryDetailNotOpenLogState(
        val totalCount: Int,
        val loggedCount: Int,
        val lastLoggedAtMillis: Long,
        val lastSeenAtMillis: Long,
    )

    private data class AutoEntryDetailNotOpenLog(
        val repeatedCount: Int,
        val totalCount: Int,
    ) {
        val historySummary: String
            get() = "상세미진입 반복 ${repeatedCount}회 압축 기록 · 누적 ${totalCount}회"

        val diagnosticSummary: String
            get() = "detailNotOpenRepeated=$repeatedCount,total=$totalCount"
    }

    private data class NodeClickResult(
        val clicked: Boolean,
        val diagnostic: String,
    )

    private enum class AutoEntryListRegion {
        Top,
        Bottom,
    }

    private fun AutoEntryListRegion.opposite(): AutoEntryListRegion {
        return when (this) {
            AutoEntryListRegion.Top -> AutoEntryListRegion.Bottom
            AutoEntryListRegion.Bottom -> AutoEntryListRegion.Top
        }
    }

    private fun AutoEntryListRegion.toKoreanLabel(): String {
        return when (this) {
            AutoEntryListRegion.Top -> "상단"
            AutoEntryListRegion.Bottom -> "하단"
        }
    }

    private enum class AutoEntryMode(
        val isSecondary: Boolean,
    ) {
        Primary(isSecondary = false),
        Secondary(isSecondary = true),
    }

    private fun AutoEntryMode.toKoreanLabel(): String {
        return when (this) {
            AutoEntryMode.Primary -> "기준"
            AutoEntryMode.Secondary -> "추적"
        }
    }

    private enum class RouteAddressRole {
        Pickup,
        Dropoff,
    }

    private data class ManualDecisionOverlayViews(
        val container: LinearLayout,
        val titleView: TextView,
        val bodyView: TextView,
        val reasonView: TextView,
        val confirmButton: Button,
        val skipButton: Button,
    )

    private data class RunModeOverlayViews(
        val container: LinearLayout,
        val statusView: TextView,
        val autoConfirmButton: Button,
        val autoEntryButton: Button,
        val awsButton: Button,
        val enableDriveModeButton: Button,
    )
}
