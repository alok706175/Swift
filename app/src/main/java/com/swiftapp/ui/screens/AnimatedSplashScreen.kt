package com.swiftapp.ui.screens

import android.graphics.PathMeasure
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import kotlinx.coroutines.delay

/**
 * Animated Vector Splash Screen
 * Swift Bird multi-phase stroke-draw, card expansion, silhouette fill, and typography sequence.
 */
@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    // Master animation progress driving the 4 phases
    // 0.0s to 1.0s: Phase 1 (Stroke Draw)
    // 1.0s to 1.8s: Phase 2 (Card Scale & Gradient Emergence)
    // 1.8s to 2.4s: Phase 3 (Silhouette White Transition & Settle Bounce)
    // 2.4s to 2.8s: Phase 4 (Typography Fade-in & Exit)

    val strokeEasing = CubicBezierEasing(0.25f, 1.0f, 0.5f, 1.0f)
    val cardEmergenceEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
    val bounceEasing = FastOutSlowInEasing

    val animTime = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animTime.animateTo(
            targetValue = 2.2f,
            animationSpec = tween(
                durationMillis = 2200,
                easing = LinearEasing
            )
        )
        delay(50)
        onSplashFinished()
    }

    val t = animTime.value

    // --- Phase 1: Stroke Draw Progress (0.0s to 0.8s) ---
    val strokeProgress = remember(t) {
        if (t <= 0.0f) 0.05f
        else if (t >= 0.8f) 1f
        else 0.05f + (0.95f * strokeEasing.transform(t / 0.8f))
    }

    // --- Phase 2: Card Scale & Opacity (0.0s to 0.8s & Bounce 0.8s to 1.3s) ---
    val cardScale = remember(t) {
        when {
            t <= 0.7f -> {
                val progress = cardEmergenceEasing.transform(t / 0.7f)
                0.86f + (0.14f * progress) // 0.86 -> 1.0
            }
            t in 0.7f..1.0f -> {
                // Micro settle bounce 1.0 -> 1.04
                val progress = bounceEasing.transform((t - 0.7f) / 0.3f)
                1.0f + (0.04f * progress)
            }
            t in 1.0f..1.3f -> {
                // Bounce return 1.04 -> 1.0
                val progress = bounceEasing.transform((t - 1.0f) / 0.3f)
                1.04f - (0.04f * progress)
            }
            else -> 1.0f
        }
    }

    val cardAlpha = remember(t) {
        when {
            t <= 0.5f -> (0.4f + (0.6f * (t / 0.5f))).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    // --- Phase 3: Silhouette White Transition (0.6s to 1.2s) ---
    val birdWhiteProgress = remember(t) {
        when {
            t < 0.6f -> 0f
            t in 0.6f..1.2f -> ((t - 0.6f) / 0.6f).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    // Stroke color morphs from warm coral #FF6B4A to pure white #FFFFFF
    val strokeColor = remember(birdWhiteProgress) {
        lerpColor(Color(0xFFFF6B4A), Color(0xFFFFFFFF), birdWhiteProgress)
    }

    // --- Phase 4: Typography Fade & Slide Up (1.0s to 1.7s) ---
    val textAlpha = remember(t) {
        when {
            t < 1.0f -> 0f
            t in 1.0f..1.6f -> ((t - 1.0f) / 0.6f).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    val textOffsetY = remember(t) {
        when {
            t < 1.0f -> 20.dp
            t in 1.0f..1.6f -> {
                val progress = cardEmergenceEasing.transform((t - 1.0f) / 0.6f)
                (20 * (1f - progress)).dp
            }
            else -> 0.dp
        }
    }

    // Overall Splash Container Fade Out (2.0s to 2.2s)
    val splashExitAlpha = remember(t) {
        when {
            t < 2.0f -> 1f
            t in 2.0f..2.2f -> 1f - ((t - 2.0f) / 0.2f).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    // Card Gradient (Coral-Peach: #FFA07A to #FF5E3A)
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFA07A), // Peach
            Color(0xFFFF6B4A), // Coral
            Color(0xFFFF5E3A)  // Deep Coral
        ),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(400f, 400f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .alpha(splashExitAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Animated Icon Container
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(cardScale),
                contentAlignment = Alignment.Center
            ) {
                // Background Gradient Squircle Card
                if (cardAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(cardAlpha)
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(36.dp),
                                spotColor = Color(0xFFFF5E3A).copy(alpha = 0.35f),
                                ambientColor = Color(0xFFFF6B4A).copy(alpha = 0.2f)
                            )
                            .clip(RoundedCornerShape(36.dp))
                            .background(cardGradient)
                    )
                }

                // Vector Swift Bird Animated Stroke & Fill Canvas
                SwiftBirdCanvas(
                    strokeProgress = strokeProgress,
                    strokeColor = strokeColor,
                    modifier = Modifier
                        .size(110.dp)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Brand Typography
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = textOffsetY)
                    .alpha(textAlpha)
            ) {
                Text(
                    text = "Swift",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "All-in-One PDF Suite",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = Color(0xFFFF6B4A),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Native Canvas Vector Drawing of the Swift Bird
 * Uses PathMeasure for precise frame-rate independent stroke-dash offset animation.
 */
@Composable
private fun SwiftBirdCanvas(
    strokeProgress: Float,
    strokeColor: Color,
    modifier: Modifier = Modifier
) {
    // 5 Clean Vector Path Strokes of the Swift Bird (120x120 viewport)
    val rawPathStrings = remember {
        listOf(
            "M 18,50 C 28,28 56,14 88,18 C 72,26 56,36 44,48",
            "M 24,58 C 36,42 62,30 92,32 C 76,40 60,50 48,60",
            "M 16,68 C 30,64 46,58 62,44 C 72,36 84,28 96,26 C 84,38 68,54 50,70 C 38,80 26,86 18,82",
            "M 28,76 C 40,76 58,70 74,58 C 62,66 48,76 34,80",
            "M 88,20 C 94,16 102,16 106,20 C 100,23 92,24 88,20"
        )
    }

    val parsedPaths = remember {
        rawPathStrings.mapNotNull { d ->
            try {
                PathParser.createPathFromPathData(d)
            } catch (e: Exception) {
                null
            }
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width / 120f
        val scaleY = size.height / 120f
        val strokeWidthPx = 4.8.dp.toPx()

        parsedPaths.forEach { androidPath ->
            val pathMeasure = PathMeasure(androidPath, false)
            val pathLength = pathMeasure.length
            val currentLength = pathLength * strokeProgress

            if (currentLength > 0f) {
                val segmentPath = android.graphics.Path()
                pathMeasure.getSegment(0f, currentLength, segmentPath, true)

                // Scale to fit canvas
                val matrix = android.graphics.Matrix()
                matrix.setScale(scaleX, scaleY)
                segmentPath.transform(matrix)

                drawPath(
                    path = segmentPath.asComposePath(),
                    color = strokeColor,
                    style = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}
