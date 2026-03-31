package com.motorola.motomouse.network

import android.os.Build
import android.os.SystemClock
import com.motorola.motomouse.data.PairingInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.math.roundToInt

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val message: String) : ConnectionState
    data class Connected(val serverName: String) : ConnectionState
    data class Reconnecting(val attempt: Int, val maxAttempts: Int, val message: String) : ConnectionState
    data class RepairRequired(val message: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

class UdpRemoteTouchClient(
    parentScope: CoroutineScope,
    private val onRepairRequired: suspend (String) -> Unit,
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val connectionMutex = Mutex()
    private val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

    private var pairingInfo: PairingInfo? = null
    private var sessionId: String? = null
    private var serverName: String? = null
    private var socket: DatagramSocket? = null
    private var serverAddress: InetSocketAddress? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastHeartbeatAckElapsed: Long = 0L

    val connectionState: StateFlow<ConnectionState> = state.asStateFlow()

    fun connect(pairingInfo: PairingInfo) {
        this.pairingInfo = pairingInfo
        scope.launch {
            connectionMutex.withLock {
                stopActiveConnection()
                val connected = performHandshake(pairingInfo, reconnectAttempt = 0)
                if (!connected) {
                    scheduleReconnect("无法连接到 ${pairingInfo.serverName}")
                }
            }
        }
    }

    fun clearPairing() {
        pairingInfo = null
        sessionId = null
        scope.launch {
            state.emit(ConnectionState.Idle)
        }
        stopActiveConnection()
    }

    fun sendPointerMove(dx: Float, dy: Float) {
        sendInputMessage(
            type = "move",
            payloadBuilder = {
                put("dx", dx)
                put("dy", dy)
            },
        )
    }

    fun sendLeftClick() {
        sendButtonClick("left")
    }

    fun sendRightClick() {
        sendButtonClick("right")
    }

    fun sendDragStart() {
        sendSimpleMessage("drag_start")
    }

    fun sendDragMove(dx: Float, dy: Float) {
        sendInputMessage(
            type = "drag_move",
            payloadBuilder = {
                put("dx", dx)
                put("dy", dy)
            },
        )
    }

    fun sendDragEnd() {
        sendSimpleMessage("drag_end")
    }

    fun sendScroll(dx: Float, dy: Float) {
        sendInputMessage(
            type = "scroll",
            payloadBuilder = {
                put("dx", dx.roundToInt())
                put("dy", dy.roundToInt())
            },
        )
    }

    fun sendZoom(steps: Int) {
        if (steps == 0) return
        sendInputMessage(
            type = "zoom",
            payloadBuilder = {
                put("steps", steps)
            },
        )
    }

    fun sendGesture(name: String) {
        sendInputMessage(
            type = "gesture",
            payloadBuilder = {
                put("name", name)
            },
        )
    }

    private fun sendButtonClick(button: String) {
        sendInputMessage(
            type = "click",
            payloadBuilder = {
                put("button", button)
            },
        )
    }

    private fun sendSimpleMessage(type: String) {
        sendInputMessage(type = type, payloadBuilder = {})
    }

    private fun sendInputMessage(type: String, payloadBuilder: JSONObject.() -> Unit) {
        scope.launch(Dispatchers.IO) {
            val currentSessionId = sessionId ?: return@launch
            val payload = JSONObject()
                .put("type", type)
                .put("session_id", currentSessionId)
                .apply(payloadBuilder)
            if (!sendPacket(payload)) {
                scheduleReconnect("发送控制指令失败")
            }
        }
    }

    private suspend fun performHandshake(pairingInfo: PairingInfo, reconnectAttempt: Int): Boolean {
        state.emit(
            if (reconnectAttempt == 0) {
                ConnectionState.Connecting("正在连接 ${pairingInfo.serverName}")
            } else {
                ConnectionState.Reconnecting(reconnectAttempt, MAX_RECONNECT_ATTEMPTS, "正在尝试重新连接 ${pairingInfo.serverName}")
            },
        )

        val candidateSocket = DatagramSocket().apply {
            soTimeout = HANDSHAKE_TIMEOUT_MS.toInt()
        }
        val candidateAddress = InetSocketAddress(pairingInfo.serverIp, pairingInfo.port)

        return try {
            val request = JSONObject()
                .put("type", "pair_request")
                .put("token", pairingInfo.token)
                .put("device_name", buildDeviceName())
                .put("protocol_version", PROTOCOL_VERSION)

            val packet = DatagramPacket(
                request.toString().toByteArray(Charsets.UTF_8),
                request.toString().toByteArray(Charsets.UTF_8).size,
                candidateAddress,
            )
            withContext(Dispatchers.IO) {
                candidateSocket.send(packet)
            }

            val response = receiveSinglePacket(candidateSocket)
            if (response.optString("type") != "pair_ack") {
                throw IllegalStateException(response.optString("message", "服务器返回了未知配对响应"))
            }

            val acceptedSessionId = response.getString("session_id")
            sessionId = acceptedSessionId
            serverName = response.optString("server_name", pairingInfo.serverName)
            serverAddress = candidateAddress
            socket = candidateSocket.apply {
                soTimeout = RECEIVE_TIMEOUT_MS.toInt()
            }
            lastHeartbeatAckElapsed = SystemClock.elapsedRealtime()
            startReceiveLoop(candidateSocket)
            startHeartbeatLoop()
            state.emit(ConnectionState.Connected(serverName ?: pairingInfo.serverName))
            true
        } catch (error: Exception) {
            candidateSocket.close()
            state.emit(ConnectionState.Error(error.message ?: "配对失败"))
            false
        }
    }

    private suspend fun receiveSinglePacket(datagramSocket: DatagramSocket): JSONObject = withContext(Dispatchers.IO) {
        val buffer = ByteArray(2048)
        val responsePacket = DatagramPacket(buffer, buffer.size)
        datagramSocket.receive(responsePacket)
        val payload = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
        JSONObject(payload)
    }

    private fun startReceiveLoop(activeSocket: DatagramSocket) {
        receiveJob?.cancel()
        receiveJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(2048)
            while (true) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    activeSocket.receive(packet)
                    val payload = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    handleServerMessage(JSONObject(payload))
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: SocketException) {
                    break
                } catch (error: Exception) {
                    scheduleReconnect(error.message ?: "读取服务器消息失败")
                    break
                }
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val currentSessionId = sessionId ?: break
                val heartbeat = JSONObject()
                    .put("type", "heartbeat")
                    .put("session_id", currentSessionId)
                val sent = sendPacket(heartbeat)
                val elapsed = SystemClock.elapsedRealtime() - lastHeartbeatAckElapsed
                if (!sent || elapsed > HEARTBEAT_TIMEOUT_MS) {
                    scheduleReconnect("与电脑连接超时")
                    break
                }
            }
        }
    }

    private suspend fun handleServerMessage(message: JSONObject) {
        when (message.optString("type")) {
            "heartbeat_ack" -> {
                lastHeartbeatAckElapsed = SystemClock.elapsedRealtime()
            }

            "pair_ack" -> {
                lastHeartbeatAckElapsed = SystemClock.elapsedRealtime()
            }

            "session_reset" -> {
                requestRepair(message.optString("message", "配对已失效，请重新扫码"))
            }

            "error" -> {
                val messageText = message.optString("message", "服务器返回错误")
                if (message.optBoolean("requires_repair", false)) {
                    requestRepair(messageText)
                } else {
                    scheduleReconnect(messageText)
                }
            }
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            connectionMutex.withLock {
                stopActiveConnection()
                val savedPairing = pairingInfo ?: return@withLock
                for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                    val connected = performHandshake(savedPairing, reconnectAttempt = attempt)
                    if (connected) {
                        return@withLock
                    }
                    delay(RECONNECT_BASE_DELAY_MS * attempt)
                }
                requestRepair("自动重连超过 $MAX_RECONNECT_ATTEMPTS 次，请重新扫码配对。")
            }
        }
    }

    private suspend fun requestRepair(message: String) {
        stopActiveConnection()
        state.emit(ConnectionState.RepairRequired(message))
        onRepairRequired(message)
    }

    private fun stopActiveConnection() {
        receiveJob?.cancel()
        heartbeatJob?.cancel()
        receiveJob = null
        heartbeatJob = null
        socket?.close()
        socket = null
        sessionId = null
        serverAddress = null
    }

    private suspend fun sendPacket(payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val datagramSocket = socket ?: return@withContext false
        val remoteAddress = serverAddress ?: return@withContext false
        return@withContext try {
            val rawPayload = payload.toString().toByteArray(Charsets.UTF_8)
            datagramSocket.send(DatagramPacket(rawPayload, rawPayload.size, remoteAddress))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildDeviceName(): String = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .joinToString(separator = " ")
        .ifBlank { "Android Phone" }

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 10
        const val PROTOCOL_VERSION = 1
        const val HANDSHAKE_TIMEOUT_MS = 2_500L
        const val RECEIVE_TIMEOUT_MS = 1_000L
        const val HEARTBEAT_INTERVAL_MS = 2_000L
        const val HEARTBEAT_TIMEOUT_MS = 7_000L
        const val RECONNECT_BASE_DELAY_MS = 1_000L
    }
}

