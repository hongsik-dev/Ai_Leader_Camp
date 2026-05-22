package com.catchpro.app.data.sync

import com.catchpro.app.data.model.AppSettings
import com.catchpro.app.data.model.OperationLogDraft
import com.catchpro.app.data.repository.OperationLogRepository
import com.catchpro.app.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

data class RouteAddressCloudSyncStatus(
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val roomCode: String = "",
    val message: String = "실시간 동기화 꺼짐",
)

@Singleton
class RouteAddressCloudSyncManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val operationLogRepository: OperationLogRepository,
    private val okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(RouteAddressCloudSyncStatus())
    val status: StateFlow<RouteAddressCloudSyncStatus> = _status.asStateFlow()

    private var started = false
    private var settingsJob: Job? = null
    private var reconnectJob: Job? = null
    private var initialSnapshotWaitJob: Job? = null
    private var webSocket: WebSocket? = null
    private var activeConnectionKey: String? = null
    private var joined = false
    private var latestSettings: AppSettings = AppSettings()
    private var lastSentSnapshotKey: String? = null
    private var suppressNextLocalSnapshotKey: String? = null
    private var lastLocalSnapshotSentAtMillis: Long = 0L
    private var lastRemoteSnapshotAppliedAtMillis: Long = 0L
    private var lastAcceptedRemoteAddresses: List<String> = emptyList()
    private var lastAcceptedRemoteActiveDriveDestination: String = ""

    @Synchronized
    fun start() {
        if (started) return
        started = true
        settingsJob = scope.launch {
            settingsRepository.settings.collectLatest { settings ->
                handleSettings(settings)
            }
        }
    }

    @Synchronized
    private fun handleSettings(settings: AppSettings) {
        latestSettings = settings
        val roomCode = settings.routeAddressCloudSyncRoomCode.sanitizedRoomCode()
        if (!settings.routeAddressCloudSyncEnabled) {
            disconnect("실시간 동기화 꺼짐")
            return
        }
        if (roomCode.length != CloudSyncRoomCodeLength) {
            disconnect("방 코드 6자리가 필요합니다.")
            return
        }

        val url = settings.routeAddressCloudSyncServerUrl.trim()
        val connectionKey = "$url|$roomCode"
        if (activeConnectionKey != connectionKey || webSocket == null) {
            connect(url = url, roomCode = roomCode, connectionKey = connectionKey)
            return
        }

        val snapshotKey = settings.toCloudSnapshotKey()
        if (snapshotKey == suppressNextLocalSnapshotKey) {
            suppressNextLocalSnapshotKey = null
            return
        }
        if (initialSnapshotWaitJob != null) return
        maybeSendLatestSnapshot()
    }

    @Synchronized
    private fun connect(
        url: String,
        roomCode: String,
        connectionKey: String,
    ) {
        reconnectJob?.cancel()
        initialSnapshotWaitJob?.cancel()
        initialSnapshotWaitJob = null
        webSocket?.cancel()
        webSocket = null
        joined = false
        activeConnectionKey = connectionKey
        lastSentSnapshotKey = null
        suppressNextLocalSnapshotKey = null
        lastLocalSnapshotSentAtMillis = 0L
        lastRemoteSnapshotAppliedAtMillis = 0L
        lastAcceptedRemoteAddresses = emptyList()
        lastAcceptedRemoteActiveDriveDestination = ""
        _status.value = RouteAddressCloudSyncStatus(
            enabled = true,
            connected = false,
            roomCode = roomCode,
            message = "실시간 동기화 연결 중",
        )

        val request = runCatching {
            Request.Builder()
                .url(url)
                .build()
        }.getOrElse { error ->
            _status.value = RouteAddressCloudSyncStatus(
                enabled = true,
                connected = false,
                roomCode = roomCode,
                message = "동기화 서버 주소 오류",
            )
            logCloudSync(
                status = "CONNECT_URL_INVALID",
                roomCode = roomCode,
                reason = error.message ?: "invalid url",
                rawContext = JSONObject()
                    .put("url", url),
            )
            return
        }
        logCloudSync(
            status = "CONNECTING",
            roomCode = roomCode,
            rawContext = JSONObject()
                .put("url", url),
        )
        webSocket = okHttpClient.newWebSocket(
            request,
            SyncWebSocketListener(
                connectionKey = connectionKey,
                roomCode = roomCode,
            ),
        )
    }

    @Synchronized
    private fun disconnect(message: String) {
        val previousConnectionKey = activeConnectionKey
        reconnectJob?.cancel()
        initialSnapshotWaitJob?.cancel()
        initialSnapshotWaitJob = null
        activeConnectionKey = null
        joined = false
        webSocket?.cancel()
        webSocket = null
        _status.value = RouteAddressCloudSyncStatus(
            enabled = false,
            connected = false,
            roomCode = latestSettings.routeAddressCloudSyncRoomCode,
            message = message,
        )
        if (previousConnectionKey != null) {
            logCloudSync(
                status = "DISCONNECTED",
                roomCode = latestSettings.routeAddressCloudSyncRoomCode.sanitizedRoomCode(),
                reason = message,
            )
        }
    }

    @Synchronized
    private fun scheduleReconnect(connectionKey: String) {
        if (activeConnectionKey != connectionKey || !latestSettings.routeAddressCloudSyncEnabled) return
        reconnectJob?.cancel()
        logCloudSync(
            status = "RECONNECT_SCHEDULED",
            roomCode = latestSettings.routeAddressCloudSyncRoomCode.sanitizedRoomCode(),
            rawContext = JSONObject()
                .put("delayMillis", ReconnectDelayMillis),
        )
        reconnectJob = scope.launch {
            delay(ReconnectDelayMillis)
            reconnectIfStillCurrent(connectionKey)
        }
    }

    @Synchronized
    private fun reconnectIfStillCurrent(connectionKey: String) {
        val roomCode = latestSettings.routeAddressCloudSyncRoomCode.sanitizedRoomCode()
        val url = latestSettings.routeAddressCloudSyncServerUrl.trim()
        if (
            latestSettings.routeAddressCloudSyncEnabled &&
            roomCode.length == CloudSyncRoomCodeLength &&
            "$url|$roomCode" == connectionKey
        ) {
            connect(url = url, roomCode = roomCode, connectionKey = connectionKey)
        }
    }

    @Synchronized
    private fun maybeSendLatestSnapshot() {
        val socket = webSocket ?: return
        if (!joined) return
        val settings = latestSettings
        val snapshotKey = settings.toCloudSnapshotKey()
        if (snapshotKey == lastSentSnapshotKey) return
        lastSentSnapshotKey = snapshotKey
        lastLocalSnapshotSentAtMillis = System.currentTimeMillis()
        val addresses = settings.routeAddressCloudSyncAddresses()
        socket.send(
            JSONObject()
                .put("type", "update")
                .put("addresses", JSONArray(addresses))
                .put("activeDriveDestination", settings.activeDriveDestinationText)
                .toString(),
        )
        logCloudSync(
            status = "UPDATE_SENT",
            roomCode = settings.routeAddressCloudSyncRoomCode.sanitizedRoomCode(),
            rawContext = JSONObject()
                .put("addresses", JSONArray(addresses))
                .put("activeDriveDestination", settings.activeDriveDestinationText),
        )
    }

    @Synchronized
    private fun scheduleInitialSnapshotSend(
        connectionKey: String,
        roomCode: String,
    ) {
        initialSnapshotWaitJob?.cancel()
        logCloudSync(
            status = "INITIAL_SYNC_WAITING",
            roomCode = roomCode,
            rawContext = JSONObject()
                .put("delayMillis", InitialSnapshotWaitMillis),
        )
        initialSnapshotWaitJob = scope.launch {
            delay(InitialSnapshotWaitMillis)
            sendInitialSnapshotIfStillCurrent(
                connectionKey = connectionKey,
                roomCode = roomCode,
            )
        }
    }

    @Synchronized
    private fun sendInitialSnapshotIfStillCurrent(
        connectionKey: String,
        roomCode: String,
    ) {
        initialSnapshotWaitJob = null
        if (activeConnectionKey != connectionKey || !joined) return
        logCloudSync(
            status = "INITIAL_SYNC_SEND_ALLOWED",
            roomCode = roomCode,
        )
        maybeSendLatestSnapshot()
    }

    @Synchronized
    private fun handleMessage(
        connectionKey: String,
        roomCode: String,
        text: String,
    ) {
        if (activeConnectionKey != connectionKey) return
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "joined" -> {
                joined = true
                _status.value = RouteAddressCloudSyncStatus(
                    enabled = true,
                    connected = true,
                    roomCode = roomCode,
                    message = "실시간 동기화 연결됨 · 방 $roomCode",
                )
                logCloudSync(
                    status = "JOINED",
                    roomCode = roomCode,
                )
                scheduleInitialSnapshotSend(
                    connectionKey = connectionKey,
                    roomCode = roomCode,
                )
            }

            "ack" -> {
                val updatedAt = json.optLong("updatedAt")
                if (updatedAt > 0L) {
                    lastLocalSnapshotSentAtMillis = maxOf(lastLocalSnapshotSentAtMillis, updatedAt)
                }
                _status.update {
                    it.copy(
                        connected = true,
                        message = "주소 동기화 전송 완료 · 방 $roomCode",
                    )
                }
                logCloudSync(
                    status = "UPDATE_ACK",
                    roomCode = roomCode,
                    rawContext = JSONObject()
                        .put("updatedAt", updatedAt),
                )
            }

            "snapshot" -> {
                initialSnapshotWaitJob?.cancel()
                initialSnapshotWaitJob = null
                val payload = json.optJSONObject("payload") ?: return
                val updatedAt = payload.optLong("updatedAt")
                val addresses = payload.optJSONArray("addresses")
                    ?.toStringList()
                    .orEmpty()
                    .padCloudAddressSlots()
                val activeDriveDestination = payload.optString("activeDriveDestination").orEmpty()
                val snapshotKey = cloudSnapshotKey(addresses, activeDriveDestination)
                if (snapshotKey == lastSentSnapshotKey) return
                if (
                    updatedAt > 0L &&
                    updatedAt < maxOf(lastLocalSnapshotSentAtMillis, lastRemoteSnapshotAppliedAtMillis)
                ) {
                    logCloudSync(
                        status = "SNAPSHOT_IGNORED_STALE",
                        roomCode = roomCode,
                        rawContext = JSONObject()
                            .put("addresses", JSONArray(addresses))
                            .put("activeDriveDestination", activeDriveDestination)
                            .put("updatedAt", updatedAt)
                            .put("lastLocalSnapshotSentAtMillis", lastLocalSnapshotSentAtMillis)
                            .put("lastRemoteSnapshotAppliedAtMillis", lastRemoteSnapshotAppliedAtMillis),
                    )
                    return
                }
                val previousAddresses = lastAcceptedRemoteAddresses.ifEmpty {
                    latestSettings.routeAddressCloudSyncAddresses()
                }
                val partialReason = partialRemoteSnapshotReason(
                    previousAddresses = previousAddresses,
                    previousActiveDriveDestination = lastAcceptedRemoteActiveDriveDestination.ifBlank {
                        latestSettings.activeDriveDestinationText
                    },
                    incomingAddresses = addresses,
                    incomingActiveDriveDestination = activeDriveDestination,
                )
                if (partialReason != null) {
                    logCloudSync(
                        status = "SNAPSHOT_IGNORED_PARTIAL",
                        roomCode = roomCode,
                        reason = partialReason,
                        rawContext = JSONObject()
                            .put("addresses", JSONArray(addresses))
                            .put("activeDriveDestination", activeDriveDestination)
                            .put("updatedAt", updatedAt),
                    )
                    return
                }
                val clearedSlotCount = clearedAddressSlotCount(previousAddresses, addresses)
                suppressNextLocalSnapshotKey = snapshotKey
                if (updatedAt > 0L) {
                    lastRemoteSnapshotAppliedAtMillis = updatedAt
                }
                lastAcceptedRemoteAddresses = addresses
                lastAcceptedRemoteActiveDriveDestination = activeDriveDestination
                scope.launch {
                    settingsRepository.applyRouteAddressCloudSync(
                        addresses = addresses,
                        activeDriveDestination = activeDriveDestination,
                        remoteUpdatedAtMillis = updatedAt,
                    )
                }
                logCloudSync(
                    status = "SNAPSHOT_RECEIVED",
                    roomCode = roomCode,
                    rawContext = JSONObject()
                        .put("addresses", JSONArray(addresses))
                        .put("activeDriveDestination", activeDriveDestination)
                        .put("updatedAt", updatedAt)
                        .put("clearedSlotCount", clearedSlotCount),
                )
                _status.update {
                    it.copy(
                        connected = true,
                        message = "주소 동기화 수신 완료 · 방 $roomCode",
                    )
                }
            }

            "error" -> {
                _status.update {
                    it.copy(
                        connected = false,
                        message = json.optString("message").ifBlank { "동기화 오류" },
                    )
                }
                logCloudSync(
                    status = "SERVER_ERROR",
                    roomCode = roomCode,
                    reason = json.optString("message").ifBlank { "동기화 오류" },
                )
            }
        }
    }

    private inner class SyncWebSocketListener(
        private val connectionKey: String,
        private val roomCode: String,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            handleOpen(
                webSocket = webSocket,
                connectionKey = connectionKey,
                roomCode = roomCode,
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(
                connectionKey = connectionKey,
                roomCode = roomCode,
                text = text,
            )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleClosed(
                connectionKey = connectionKey,
                roomCode = roomCode,
                code = code,
                reason = reason,
            )
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleFailure(
                connectionKey = connectionKey,
                roomCode = roomCode,
                throwable = t,
                response = response,
            )
        }
    }

    @Synchronized
    private fun handleOpen(
        webSocket: WebSocket,
        connectionKey: String,
        roomCode: String,
    ) {
        if (activeConnectionKey != connectionKey) {
            webSocket.cancel()
            return
        }
        webSocket.send(
            JSONObject()
                .put("type", "join")
                .put("roomCode", roomCode)
                .toString(),
        )
        logCloudSync(
            status = "JOIN_SENT",
            roomCode = roomCode,
        )
    }

    @Synchronized
    private fun handleClosed(
        connectionKey: String,
        roomCode: String,
        code: Int,
        reason: String,
    ) {
        if (activeConnectionKey != connectionKey) return
        initialSnapshotWaitJob?.cancel()
        initialSnapshotWaitJob = null
        joined = false
        _status.update {
            it.copy(
                connected = false,
                message = "동기화 연결 끊김 · 재연결 대기",
            )
        }
        logCloudSync(
            status = "CLOSED",
            roomCode = roomCode,
            reason = reason,
            rawContext = JSONObject()
                .put("code", code),
        )
        scheduleReconnect(connectionKey)
    }

    @Synchronized
    private fun handleFailure(
        connectionKey: String,
        roomCode: String,
        throwable: Throwable,
        response: Response?,
    ) {
        if (activeConnectionKey != connectionKey) return
        initialSnapshotWaitJob?.cancel()
        initialSnapshotWaitJob = null
        joined = false
        val message = throwable.message ?: "원인 미확인"
        _status.update {
            it.copy(
                connected = false,
                message = "동기화 연결 실패: $message",
            )
        }
        logCloudSync(
            status = "FAILURE",
            roomCode = roomCode,
            reason = message,
            rawContext = JSONObject()
                .put("httpCode", response?.code ?: 0)
                .put("httpMessage", response?.message.orEmpty()),
        )
        scheduleReconnect(connectionKey)
    }

    private fun logCloudSync(
        status: String,
        roomCode: String,
        reason: String? = null,
        rawContext: JSONObject? = null,
    ) {
        scope.launch {
            operationLogRepository.log(
                OperationLogDraft(
                    eventType = CloudSyncLogEventType,
                    status = status,
                    mode = "AWS_SYNC",
                    source = "route_address_cloud_sync",
                    region = roomCode,
                    reason = reason,
                    rawContext = rawContext?.toString(),
                ),
            )
        }
    }

    private companion object {
        const val CloudSyncLogEventType = "ROUTE_ADDRESS_CLOUD_SYNC"
        const val CloudSyncRoomCodeLength = 6
        const val ReconnectDelayMillis = 3_000L
        const val InitialSnapshotWaitMillis = 800L
    }
}

private fun AppSettings.routeAddressCloudSyncAddresses(): List<String> =
    tmapManualRouteAddressesText
        .split('\n')
        .map(String::trim)
        .take(CloudAddressSlotCount)
        .padCloudAddressSlots()

private fun AppSettings.toCloudSnapshotKey(): String =
    cloudSnapshotKey(
        addresses = routeAddressCloudSyncAddresses(),
        activeDriveDestination = activeDriveDestinationText,
    )

private fun cloudSnapshotKey(
    addresses: List<String>,
    activeDriveDestination: String,
): String = addresses.padCloudAddressSlots().joinToString("\n") + "\n--active--\n" + activeDriveDestination.trim()

private fun partialRemoteSnapshotReason(
    previousAddresses: List<String>,
    previousActiveDriveDestination: String,
    incomingAddresses: List<String>,
    incomingActiveDriveDestination: String,
): String? {
    val previousSlots = previousAddresses.padCloudAddressSlots()
    val incomingSlots = incomingAddresses.padCloudAddressSlots()
    for (index in incomingSlots.indices) {
        partialRemoteFieldReason(
            label = "주소${index + 1}",
            previous = previousSlots.getOrNull(index).orEmpty(),
            incoming = incomingSlots[index],
        )?.let { return it }
    }
    return partialRemoteFieldReason(
        label = "추적기준",
        previous = previousActiveDriveDestination,
        incoming = incomingActiveDriveDestination,
    )
}

private fun clearedAddressSlotCount(
    previousAddresses: List<String>,
    incomingAddresses: List<String>,
): Int {
    val previousSlots = previousAddresses.padCloudAddressSlots()
    val incomingSlots = incomingAddresses.padCloudAddressSlots()
    return incomingSlots.indices.count { index ->
        previousSlots.getOrNull(index).orEmpty().isNotBlank() && incomingSlots[index].isBlank()
    }
}

private fun partialRemoteFieldReason(
    label: String,
    previous: String,
    incoming: String,
): String? {
    val previousKey = previous.normalizeCloudAddressKey()
    val incomingKey = incoming.normalizeCloudAddressKey()
    if (previousKey.isBlank() || incomingKey.isBlank() || previousKey == incomingKey) return null
    if (incomingKey.length < 8) return null
    val lengthDelta = previousKey.length - incomingKey.length
    if (lengthDelta < 8) return null
    if (!previousKey.contains(incomingKey)) return null
    return "$label 부분주소 덮어쓰기 의심: 이전=${previous.take(40)}, 수신=${incoming.take(40)}"
}

private fun String.normalizeCloudAddressKey(): String =
    lowercase()
        .replace(Regex("""(출발지|상차지|도착지|하차지|상세주소|주소|위치|원주소)"""), "")
        .replace(Regex("""[\s/(),._\-·:：]+"""), "")
        .trim()

private fun List<String>.padCloudAddressSlots(): List<String> =
    map(String::trim)
        .take(CloudAddressSlotCount)
        .let { slots -> slots + List((CloudAddressSlotCount - slots.size).coerceAtLeast(0)) { "" } }

private fun JSONArray.toStringList(): List<String> =
    List(length()) { index -> optString(index).trim() }

private const val CloudAddressSlotCount = 6

private fun String.sanitizedRoomCode(): String =
    filter(Char::isDigit)
        .take(6)
