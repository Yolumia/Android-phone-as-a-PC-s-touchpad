package com.motorola.motomouse.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TouchpadScreen(
    serverName: String,
    isConnected: Boolean,
    statusMessage: String,
    reconnectMessage: String?,
    onRetry: () -> Unit,
    onForgetPairing: () -> Unit,
    onPointerMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onScroll: (Float, Float) -> Unit,
    onZoom: (Int) -> Unit,
    onGesture: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gestureThresholds = remember(density) {
        GestureThresholds(
            tapSlopPx = with(density) { 14.dp.toPx() },
            movementActivationPx = with(density) { 10.dp.toPx() },
            dragActivationPx = with(density) { 18.dp.toPx() },
            multiSwipeThresholdPx = with(density) { 64.dp.toPx() },
            zoomStepPx = with(density) { 24.dp.toPx() },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (isConnected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isConnected) "已连接" else "连接中 / 重连中",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = serverName.ifBlank { "Windows PC" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .padding(2.dp),
                        )
                    }
                }
                Text(
                    text = reconnectMessage ?: statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onRetry) {
                        Text("重新连接")
                    }
                    OutlinedButton(onClick = onForgetPairing) {
                        Text("重新扫码")
                    }
                }
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "触摸板区域",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(28.dp),
                        )
                        .pointerInput(isConnected) {
                            detectMotoMouseGestures(
                                thresholds = gestureThresholds,
                                onPointerMove = { dx, dy -> if (isConnected) onPointerMove(dx, dy) },
                                onLeftClick = { if (isConnected) onLeftClick() },
                                onRightClick = { if (isConnected) onRightClick() },
                                onDragStart = { if (isConnected) onDragStart() },
                                onDragMove = { dx, dy -> if (isConnected) onDragMove(dx, dy) },
                                onDragEnd = { if (isConnected) onDragEnd() },
                                onScroll = { dx, dy -> if (isConnected) onScroll(dx, dy) },
                                onZoom = { steps -> if (isConnected) onZoom(steps) },
                                onGesture = { gestureName -> if (isConnected) onGesture(gestureName) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isConnected) "在这里滑动、点击、多指操作" else "等待连接恢复后即可继续控制电脑",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "支持的手势映射",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("• 单指移动：鼠标移动")
                Text("• 单指轻点：左键单击")
                Text("• 轻点一次后再次按住并移动：拖动")
                Text("• 双指轻点：右键")
                Text("• 双指滑动：滚轮 / 水平滚动")
                Text("• 双指捏合：缩放（Ctrl + 滚轮）")
                Text("• 三指左右滑：切换应用")
                Text("• 四指左右滑：切换虚拟桌面")
            }
        }
    }
}

private data class GestureThresholds(
    val tapSlopPx: Float,
    val movementActivationPx: Float,
    val dragActivationPx: Float,
    val multiSwipeThresholdPx: Float,
    val zoomStepPx: Float,
)

private suspend fun PointerInputScope.detectMotoMouseGestures(
    thresholds: GestureThresholds,
    onPointerMove: (Float, Float) -> Unit,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onScroll: (Float, Float) -> Unit,
    onZoom: (Int) -> Unit,
    onGesture: (String) -> Unit,
) {
    var lastTapUpTime = 0L

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val gestureStart = SystemClock.elapsedRealtime()
        val isSecondTap = gestureStart - lastTapUpTime <= DOUBLE_TAP_WINDOW_MS
        var maxPointers = 1
        var singlePointerTravel = 0f
        var twoFingerTravel = 0f
        var pinchTravel = 0f
        var dragStarted = false
        var threeFingerDx = 0f
        var fourFingerDx = 0f
        var scrollAccumulatorX = 0f
        var scrollAccumulatorY = 0f
        var zoomAccumulator = 0f

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                break
            }
            maxPointers = max(maxPointers, pressed.size)

            when (pressed.size) {
                1 -> {
                    val change = pressed.first()
                    val delta = change.position - change.previousPosition
                    val deltaDistance = delta.getDistance()
                    singlePointerTravel += deltaDistance

                    if (isSecondTap && !dragStarted && singlePointerTravel > thresholds.dragActivationPx) {
                        dragStarted = true
                        onDragStart()
                    }

                    if (dragStarted) {
                        onDragMove(delta.x, delta.y)
                    } else if (singlePointerTravel > thresholds.movementActivationPx) {
                        onPointerMove(delta.x, delta.y)
                    }
                }

                2 -> {
                    val centroidDelta = centroid(pressed) - previousCentroid(pressed)
                    val centroidDistance = centroidDelta.getDistance()
                    twoFingerTravel += centroidDistance

                    val currentDistance = pointerDistance(pressed[0], pressed[1])
                    val previousDistance = previousPointerDistance(pressed[0], pressed[1])
                    val pinchDelta = currentDistance - previousDistance
                    pinchTravel += pinchDelta

                    if (abs(pinchTravel) > thresholds.zoomStepPx && abs(pinchTravel) > twoFingerTravel * 0.7f) {
                        zoomAccumulator += pinchDelta / thresholds.zoomStepPx
                        while (abs(zoomAccumulator) >= 1f) {
                            val step = zoomAccumulator.roundToInt().coerceIn(-3, 3)
                            if (step == 0) break
                            onZoom(step)
                            zoomAccumulator -= step.toFloat()
                        }
                    } else {
                        scrollAccumulatorX += centroidDelta.x / 6f
                        scrollAccumulatorY += -centroidDelta.y / 6f
                        val scrollX = scrollAccumulatorX.toInt()
                        val scrollY = scrollAccumulatorY.toInt()
                        if (scrollX != 0 || scrollY != 0) {
                            onScroll(scrollX.toFloat(), scrollY.toFloat())
                            scrollAccumulatorX -= scrollX
                            scrollAccumulatorY -= scrollY
                        }
                    }
                }

                3 -> {
                    val centroidDelta = centroid(pressed) - previousCentroid(pressed)
                    threeFingerDx += centroidDelta.x
                }

                else -> {
                    val firstFour = pressed.take(4)
                    val centroidDelta = centroid(firstFour) - previousCentroid(firstFour)
                    fourFingerDx += centroidDelta.x
                }
            }

            event.changes.forEach { change ->
                if (change.positionChanged()) {
                    change.consume()
                }
            }
        }

        when {
            dragStarted -> {
                onDragEnd()
                lastTapUpTime = 0L
            }

            maxPointers == 1 && singlePointerTravel <= thresholds.tapSlopPx -> {
                onLeftClick()
                lastTapUpTime = SystemClock.elapsedRealtime()
            }

            maxPointers == 2 && twoFingerTravel <= thresholds.tapSlopPx * 1.4f && abs(pinchTravel) <= thresholds.tapSlopPx -> {
                onRightClick()
                lastTapUpTime = 0L
            }

            maxPointers == 3 && abs(threeFingerDx) > thresholds.multiSwipeThresholdPx -> {
                onGesture(if (threeFingerDx < 0f) APP_SWITCH_NEXT else APP_SWITCH_PREVIOUS)
                lastTapUpTime = 0L
            }

            maxPointers >= 4 && abs(fourFingerDx) > thresholds.multiSwipeThresholdPx -> {
                onGesture(if (fourFingerDx < 0f) DESKTOP_NEXT else DESKTOP_PREVIOUS)
                lastTapUpTime = 0L
            }

            else -> {
                lastTapUpTime = 0L
            }
        }
    }
}

private fun centroid(changes: List<PointerInputChange>): Offset {
    val totalX = changes.sumOf { it.position.x.toDouble() }.toFloat()
    val totalY = changes.sumOf { it.position.y.toDouble() }.toFloat()
    return Offset(totalX / changes.size, totalY / changes.size)
}

private fun previousCentroid(changes: List<PointerInputChange>): Offset {
    val totalX = changes.sumOf { it.previousPosition.x.toDouble() }.toFloat()
    val totalY = changes.sumOf { it.previousPosition.y.toDouble() }.toFloat()
    return Offset(totalX / changes.size, totalY / changes.size)
}

private fun pointerDistance(first: PointerInputChange, second: PointerInputChange): Float {
    return (first.position - second.position).getDistance()
}

private fun previousPointerDistance(first: PointerInputChange, second: PointerInputChange): Float {
    return (first.previousPosition - second.previousPosition).getDistance()
}

private const val DOUBLE_TAP_WINDOW_MS = 320L
private const val APP_SWITCH_NEXT = "app_switch_next"
private const val APP_SWITCH_PREVIOUS = "app_switch_previous"
private const val DESKTOP_NEXT = "desktop_next"
private const val DESKTOP_PREVIOUS = "desktop_previous"

