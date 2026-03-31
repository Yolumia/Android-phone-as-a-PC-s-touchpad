package com.motorola.motomouse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motorola.motomouse.data.PairingInfo
import com.motorola.motomouse.data.PairingStore
import com.motorola.motomouse.network.ConnectionState
import com.motorola.motomouse.network.UdpRemoteTouchClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    Loading,
    Pairing,
    Touchpad,
}

data class MainUiState(
    val screen: AppScreen = AppScreen.Loading,
    val statusMessage: String = "正在初始化",
    val pairingInfo: PairingInfo? = null,
    val serverName: String = "",
    val isConnected: Boolean = false,
    val reconnectMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val pairingStore = PairingStore(application.applicationContext)
    private val remoteTouchClient = UdpRemoteTouchClient(
        parentScope = viewModelScope,
        onRepairRequired = { reason ->
            pairingStore.clear()
            state.update {
                MainUiState(
                    screen = AppScreen.Pairing,
                    statusMessage = reason,
                )
            }
        },
    )

    private val state = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = state.asStateFlow()

    private var currentPairing: PairingInfo? = null

    init {
        observeConnectionState()
        restoreSavedPairing()
    }

    fun onQrScanned(rawValue: String) {
        PairingInfo.fromQrPayload(rawValue)
            .onSuccess { pairingInfo ->
                currentPairing = pairingInfo
                state.update {
                    it.copy(
                        screen = AppScreen.Touchpad,
                        pairingInfo = pairingInfo,
                        serverName = pairingInfo.serverName,
                        statusMessage = "二维码已识别，正在连接 ${pairingInfo.serverName}",
                        isConnected = false,
                        reconnectMessage = null,
                    )
                }
                viewModelScope.launch {
                    pairingStore.save(pairingInfo)
                    remoteTouchClient.connect(pairingInfo)
                }
            }
            .onFailure { error ->
                state.update {
                    it.copy(
                        screen = AppScreen.Pairing,
                        statusMessage = error.message ?: "二维码内容无效，请重新扫码。",
                    )
                }
            }
    }

    fun clearPairing() {
        currentPairing = null
        remoteTouchClient.clearPairing()
        viewModelScope.launch {
            pairingStore.clear()
            state.value = MainUiState(
                screen = AppScreen.Pairing,
                statusMessage = "请扫描电脑端二维码完成配对。",
            )
        }
    }

    fun retryConnection() {
        currentPairing?.let { pairingInfo ->
            remoteTouchClient.connect(pairingInfo)
        } ?: restoreSavedPairing()
    }

    fun sendPointerMove(dx: Float, dy: Float) = remoteTouchClient.sendPointerMove(dx, dy)

    fun sendLeftClick() = remoteTouchClient.sendLeftClick()

    fun sendRightClick() = remoteTouchClient.sendRightClick()

    fun sendDragStart() = remoteTouchClient.sendDragStart()

    fun sendDragMove(dx: Float, dy: Float) = remoteTouchClient.sendDragMove(dx, dy)

    fun sendDragEnd() = remoteTouchClient.sendDragEnd()

    fun sendScroll(dx: Float, dy: Float) = remoteTouchClient.sendScroll(dx, dy)

    fun sendZoom(steps: Int) = remoteTouchClient.sendZoom(steps)

    fun sendGesture(name: String) = remoteTouchClient.sendGesture(name)

    private fun observeConnectionState() {
        viewModelScope.launch {
            remoteTouchClient.connectionState.collect { connectionState ->
                when (connectionState) {
                    ConnectionState.Idle -> {
                        if (currentPairing == null) {
                            state.update {
                                it.copy(
                                    screen = AppScreen.Pairing,
                                    statusMessage = "请扫描电脑端二维码完成配对。",
                                    isConnected = false,
                                    reconnectMessage = null,
                                )
                            }
                        }
                    }

                    is ConnectionState.Connecting -> {
                        state.update {
                            it.copy(
                                screen = if (currentPairing == null) AppScreen.Loading else AppScreen.Touchpad,
                                pairingInfo = currentPairing,
                                serverName = currentPairing?.serverName.orEmpty(),
                                statusMessage = connectionState.message,
                                isConnected = false,
                                reconnectMessage = null,
                            )
                        }
                    }

                    is ConnectionState.Connected -> {
                        state.update {
                            it.copy(
                                screen = AppScreen.Touchpad,
                                pairingInfo = currentPairing,
                                serverName = connectionState.serverName,
                                statusMessage = "已连接到 ${connectionState.serverName}",
                                isConnected = true,
                                reconnectMessage = null,
                            )
                        }
                    }

                    is ConnectionState.Reconnecting -> {
                        state.update {
                            it.copy(
                                screen = AppScreen.Touchpad,
                                pairingInfo = currentPairing,
                                serverName = currentPairing?.serverName.orEmpty(),
                                statusMessage = connectionState.message,
                                isConnected = false,
                                reconnectMessage = "自动重连中（${connectionState.attempt}/${connectionState.maxAttempts}）",
                            )
                        }
                    }

                    is ConnectionState.Error -> {
                        state.update {
                            it.copy(
                                screen = if (currentPairing == null) AppScreen.Pairing else AppScreen.Touchpad,
                                pairingInfo = currentPairing,
                                serverName = currentPairing?.serverName.orEmpty(),
                                statusMessage = connectionState.message,
                                isConnected = false,
                            )
                        }
                    }

                    is ConnectionState.RepairRequired -> {
                        currentPairing = null
                        state.update {
                            MainUiState(
                                screen = AppScreen.Pairing,
                                statusMessage = connectionState.message,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun restoreSavedPairing() {
        viewModelScope.launch {
            val pairingInfo = pairingStore.load()
            currentPairing = pairingInfo
            if (pairingInfo == null) {
                state.value = MainUiState(
                    screen = AppScreen.Pairing,
                    statusMessage = "请扫描电脑端二维码完成配对。",
                )
            } else {
                state.value = MainUiState(
                    screen = AppScreen.Touchpad,
                    pairingInfo = pairingInfo,
                    serverName = pairingInfo.serverName,
                    statusMessage = "发现已保存配对，正在连接 ${pairingInfo.serverName}",
                    isConnected = false,
                )
                remoteTouchClient.connect(pairingInfo)
            }
        }
    }
}

