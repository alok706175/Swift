package com.swiftapp.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized Motion & Easing Tokens for Swift PDF (60/120 FPS Target).
 */
object MotionTokens {
    /** Snappy Entrance & Micro-Interactions: 200ms – 250ms */
    const val SnappyDurationMs = 220
    val SnappyEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Modals, Bottom Sheets & Dialogs: 300ms – 350ms */
    const val ModalDurationMs = 320
    val ModalEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)

    /** Exits & Dismissals: 150ms – 200ms */
    const val ExitDurationMs = 180
    val ExitEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** High-refresh Tactile Spring (Sub-16ms touch recovery) */
    val TactileSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    /** Cheerful Success Pop Spring */
    val SuccessPopSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Checks system accessibility settings to determine if reduced animations are preferred.
 */
@Composable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    val scale = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
    } catch (_: Exception) {
        1.0f
    }
    return scale == 0.0f
}

/**
 * GPU-Accelerated Staggered List / Grid Entrance Modifier.
 *
 * Uses strictly composite properties (alpha + translationY inside graphicsLayer)
 * with ZERO CPU reflow / layout recalculation overhead.
 */
fun Modifier.staggeredEntrance(
    index: Int,
    baseDelayMs: Long = 30L,
    maxDelayMs: Long = 200L,
    initialOffsetY: Float = 24f
): Modifier = composed {
    val isReducedMotion = isReducedMotionEnabled()
    if (isReducedMotion) return@composed this

    val delay = (index * baseDelayMs).coerceAtMost(maxDelayMs).toInt()
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.0f,
        animationSpec = tween(
            durationMillis = MotionTokens.SnappyDurationMs,
            delayMillis = delay,
            easing = MotionTokens.SnappyEasing
        ),
        label = "StaggeredAlpha"
    )

    val translateY by animateFloatAsState(
        targetValue = if (isVisible) 0.0f else initialOffsetY,
        animationSpec = tween(
            durationMillis = MotionTokens.SnappyDurationMs,
            delayMillis = delay,
            easing = MotionTokens.SnappyEasing
        ),
        label = "StaggeredTranslateY"
    )

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translateY
    }
}

/**
 * High-performance container for staggered list animations.
 */
@Composable
fun StaggeredAnimatedItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.staggeredEntrance(index)) {
        content()
    }
}

/**
 * Cheerful Animated SVG Stroke Draw Checkmark with spring scale pop.
 */
@Composable
fun AnimatedSuccessCheckmark(
    modifier: Modifier = Modifier,
    circleColor: Color = Color(0xFF2E7D32),
    checkColor: Color = Color.White,
    size: Dp = 64.dp
) {
    val transitionState = remember { MutableTransitionState(false) }.apply {
        targetState = true
    }
    val transition = updateTransition(transitionState, label = "SuccessCheckmarkTransition")

    val scale by transition.animateFloat(
        transitionSpec = { MotionTokens.SuccessPopSpring },
        label = "ScalePop"
    ) { state -> if (state) 1.0f else 0.2f }

    val strokeProgress by transition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = 350,
                delayMillis = 100,
                easing = MotionTokens.SnappyEasing
            )
        },
        label = "StrokeDraw"
    ) { state -> if (state) 1.0f else 0.0f }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2f

            // 1. Background Filled Circle
            drawCircle(
                color = circleColor,
                radius = radius,
                center = center
            )

            // 2. Animated Checkmark Path Draw
            if (strokeProgress > 0f) {
                val path = Path().apply {
                    val w = this@Canvas.size.width
                    val h = this@Canvas.size.height
                    moveTo(w * 0.28f, h * 0.52f)
                    lineTo(w * 0.44f, h * 0.68f)
                    lineTo(w * 0.72f, h * 0.36f)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(path, false)
                val totalLength = pathMeasure.length

                val animatedPath = Path()
                pathMeasure.getSegment(0f, totalLength * strokeProgress, animatedPath, true)

                drawPath(
                    path = animatedPath,
                    color = checkColor,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * Fluid Morphing Button that smoothly transitions into a progress pill during async tasks.
 */
@Composable
fun FluidMorphingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    text: String,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    TactileButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isLoading = isLoading,
        colors = colors
    ) {
        if (!isLoading) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text)
        }
    }
}
