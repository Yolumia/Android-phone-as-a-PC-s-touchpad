package com.motorola.motomouse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motorola.motomouse.ui.PairingScreen
import com.motorola.motomouse.ui.TouchpadScreen
import com.motorola.motomouse.ui.theme.MotoMouseTheme

@Composable
fun MotoMouseApp(
    viewModel: MainViewModel = viewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    MotoMouseTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (uiState.screen) {
                AppScreen.Loading -> {
                    Text(
                        text = uiState.statusMessage,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                AppScreen.Pairing -> {
                    PairingScreen(
                        statusMessage = uiState.statusMessage,
                        onQrScanned = viewModel::onQrScanned,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                AppScreen.Touchpad -> {
                    TouchpadScreen(
                        serverName = uiState.serverName,
                        isConnected = uiState.isConnected,
                        statusMessage = uiState.statusMessage,
                        reconnectMessage = uiState.reconnectMessage,
                        onRetry = viewModel::retryConnection,
                        onForgetPairing = viewModel::clearPairing,
                        onPointerMove = viewModel::sendPointerMove,
                        onLeftClick = viewModel::sendLeftClick,
                        onRightClick = viewModel::sendRightClick,
                        onDragStart = viewModel::sendDragStart,
                        onDragMove = viewModel::sendDragMove,
                        onDragEnd = viewModel::sendDragEnd,
                        onScroll = viewModel::sendScroll,
                        onZoom = viewModel::sendZoom,
                        onGesture = viewModel::sendGesture,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

