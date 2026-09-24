package com.swiftapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.swiftapp.utils.HapticManager
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-performance 3x3 interactive Pattern Lock component with fluid line tracing
 * and tactile node connection haptics.
 */
@Composable
fun PatternLockView(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    onPatternCompleted: (List<Int>) -> Unit
) {
    var connectedNodes by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentTouchPoint by remember { mutableStateOf<Offset?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val dotInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    val activeColor by animateColorAsState(
        targetValue = if (isError) errorColor else primaryColor,
        animationSpec = tween(200),
        label = "PatternActiveColor"
    )

    LaunchedEffect(isError) {
        if (isError) {
            // Keep error pattern visible briefly, then clear
            kotlinx.coroutines.delay(800)
            connectedNodes = emptyList()
            currentTouchPoint = null
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(16.dp)
            .pointerInput(enabled, isError) {
                if (!enabled || isError) return@pointerInput

                detectDragGestures(
                    onDragStart = { offset ->
                        connectedNodes = emptyList()
                        currentTouchPoint = offset
                        val node = getNodeFromOffset(offset, size.width.toFloat(), size.height.toFloat())
                        if (node != null) {
                            connectedNodes = listOf(node)
                            HapticManager.light()
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pos = change.position
                        currentTouchPoint = pos
                        val node = getNodeFromOffset(pos, size.width.toFloat(), size.height.toFloat())
                        if (node != null && !connectedNodes.contains(node)) {
                            connectedNodes = connectedNodes + node
                            HapticManager.light()
                        }
                    },
                    onDragEnd = {
                        currentTouchPoint = null
                        if (connectedNodes.isNotEmpty()) {
                            onPatternCompleted(connectedNodes)
                        }
                    },
                    onDragCancel = {
                        currentTouchPoint = null
                        connectedNodes = emptyList()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val cellWidth = width / 3f
            val cellHeight = height / 3f

            val nodeCenters = List(9) { index ->
                val row = index / 3
                val col = index % 3
                Offset(
                    x = (col + 0.5f) * cellWidth,
                    y = (row + 0.5f) * cellHeight
                )
            }

            // 1. Draw Connecting Lines
            if (connectedNodes.size > 1) {
                for (i in 0 until connectedNodes.size - 1) {
                    val start = nodeCenters[connectedNodes[i]]
                    val end = nodeCenters[connectedNodes[i + 1]]
                    drawLine(
                        color = activeColor.copy(alpha = 0.85f),
                        start = start,
                        end = end,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. Draw Floating Drag Line to Current Finger Position
            val lastNode = connectedNodes.lastOrNull()
            val touch = currentTouchPoint
            if (lastNode != null && touch != null && !isError) {
                val start = nodeCenters[lastNode]
                drawLine(
                    color = activeColor.copy(alpha = 0.5f),
                    start = start,
                    end = touch,
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw 3x3 Nodes (Outer ring + Inner circle)
            nodeCenters.forEachIndexed { index, center ->
                val isSelected = connectedNodes.contains(index)
                val baseRadius = 8.dp.toPx()
                val outerRingRadius = 24.dp.toPx()

                if (isSelected) {
                    // Outer glow ring
                    drawCircle(
                        color = activeColor.copy(alpha = 0.2f),
                        radius = outerRingRadius,
                        center = center
                    )
                    drawCircle(
                        color = activeColor,
                        radius = outerRingRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    // Inner filled dot
                    drawCircle(
                        color = activeColor,
                        radius = baseRadius * 1.3f,
                        center = center
                    )
                } else {
                    // Inactive base dot
                    drawCircle(
                        color = dotInactiveColor,
                        radius = baseRadius,
                        center = center
                    )
                    drawCircle(
                        color = outlineColor,
                        radius = outerRingRadius * 0.8f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * Calculates 0..8 node index from canvas touch position.
 */
private fun getNodeFromOffset(offset: Offset, canvasWidth: Float, canvasHeight: Float): Int? {
    val cellWidth = canvasWidth / 3f
    val cellHeight = canvasHeight / 3f
    val hitRadius = minOf(cellWidth, cellHeight) * 0.45f

    for (index in 0 until 9) {
        val row = index / 3
        val col = index % 3
        val centerX = (col + 0.5f) * cellWidth
        val centerY = (row + 0.5f) * cellHeight

        val dist = sqrt((offset.x - centerX).pow(2) + (offset.y - centerY).pow(2))
        if (dist <= hitRadius) {
            return index
        }
    }
    return null
}
