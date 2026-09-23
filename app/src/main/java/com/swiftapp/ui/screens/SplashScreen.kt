package com.swiftapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Color Palette
private val SwiftCoralPrimary = Color(0xFFFF6B4A)
private val SwiftGradientStart = Color(0xFFFF7A59)
private val SwiftGradientEnd = Color(0xFFFF5F45)
private val SwiftCharcoalTitle = Color(0xFF1F2937)
private val SwiftSubtitleGray = Color(0xFF6B7280)
private val CanvasBackground = Color(0xFFFFFFFF)

/**
 * Calculates animation progress between [startMs] and [endMs] using the given [easing].
 */
private fun computeProgress(
    timeMs: Float,
    startMs: Float,
    endMs: Float,
    easing: Easing = FastOutSlowInEasing
): Float {
    if (timeMs <= startMs) return 0f
    if (timeMs >= endMs) return 1f
    val fraction = (timeMs - startMs) / (endMs - startMs)
    return easing.transform(fraction.coerceIn(0f, 1f))
}

/**
 * Premium Android Splash Screen Animation for "Swift" - All-in-One PDF Utility.
 *
 * Timeline (0.0s - 3.7s):
 * - 0.00sâ€“0.25s: Blank pure white screen
 * - 0.25sâ€“0.65s: Stroke 1 (Upper large wing) progressively drawn
 * - 0.50sâ€“0.90s: Stroke 2 (Second thinner wing) progressively drawn
 * - 0.75sâ€“1.15s: Stroke 3 (Central body & 'S' shape) drawn
 * - 1.00sâ€“1.35s: Stroke 4 (Lower wing/tail) drawn
 * - 1.15sâ€“1.45s: Stroke 5 (Head/Beak shape) drawn
 * - 1.35sâ€“1.75s: Complete bird logo scales with subtle overshoot (0.90 -> 1.025 -> 1.00)
 * - 1.65sâ€“2.10s: Rounded-square gradient background forms; bird morphs from orange to white
 * - 2.10sâ€“2.50s: App icon settles with soft ambient glow
 * - 2.50sâ€“2.90s: "Swift" title reveals with slide-up & fade
 * - 2.90sâ€“3.20s: "All-in-One PDF Utility" subtitle reveals
 * - 3.20sâ€“3.50s: Full splash hold
 * - 3.50sâ€“3.70s: Smooth fade/scale transition to app home
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    onSplashFinished: () -> Unit = {}
) {
    val animTime = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animTime.animateTo(
            targetValue = 3700f,
            animationSpec = tween(
                durationMillis = 3700,
                easing = LinearEasing
            )
        )
        onSplashFinished()
    }

    val t = animTime.value

    // Exit transition (3500ms - 3700ms)
    val exitProgress = computeProgress(t, 3500f, 3700f, FastOutSlowInEasing)
    val screenAlpha = 1f - exitProgress
    val screenScale = 1f + (exitProgress * 0.04f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .scale(screenScale)
            .alpha(screenAlpha),
        contentAlignment = Alignment.Center
    ) {
        SwiftSplashContent(currentTimeMs = t)
    }
}

/**
 * Core Splash Animation Content rendering the icon and typography with timeline synchronization.
 */
@Composable
fun SwiftSplashContent(
    currentTimeMs: Float,
    modifier: Modifier = Modifier,
    iconSize: Dp = 144.dp
) {
    val t = currentTimeMs

    // Stroke Drawing Progresses
    val stroke1 = computeProgress(t, 250f, 650f, FastOutSlowInEasing)
    val stroke2 = computeProgress(t, 500f, 900f, FastOutSlowInEasing)
    val stroke3 = computeProgress(t, 750f, 1150f, FastOutSlowInEasing)
    val stroke4 = computeProgress(t, 1000f, 1350f, FastOutSlowInEasing)
    val stroke5 = computeProgress(t, 1150f, 1450f, FastOutSlowInEasing)

    // Bird Logo Scale & Subtle Overshoot (1350ms - 1750ms)
    val birdScale = when {
        t < 1350f -> 0.90f
        t in 1350f..1550f -> {
            val p = computeProgress(t, 1350f, 1550f, FastOutSlowInEasing)
            0.90f + (p * 0.125f) // 0.90 -> 1.025
        }
        t in 1550f..1750f -> {
            val p = computeProgress(t, 1550f, 1750f, FastOutSlowInEasing)
            1.025f - (p * 0.025f) // 1.025 -> 1.00
        }
        else -> 1.00f
    }

    // Rounded-Square Background Progress & Bird Color Morph (1650ms - 2100ms)
    val bgProgress = computeProgress(t, 1650f, 2100f, FastOutSlowInEasing)
    val bgScale = 0.85f + (bgProgress * 0.15f) // 0.85 -> 1.00
    val bgAlpha = bgProgress
    val birdColor = lerp(SwiftCoralPrimary, Color.White, bgProgress)

    // Soft Ambient Shadow / Glow (2100ms - 2500ms)
    val shadowProgress = computeProgress(t, 2100f, 2500f, FastOutSlowInEasing)
    val shadowAlpha = shadowProgress * 0.22f

    // Typography: "Swift" Title (2500ms - 2900ms)
    val titleProgress = computeProgress(t, 2500f, 2900f, FastOutSlowInEasing)
    val titleAlpha = titleProgress
    val titleOffsetY = ((1f - titleProgress) * 8).dp

    // Typography: "All-in-One PDF Utility" Subtitle (2900ms - 3200ms)
    val subtitleProgress = computeProgress(t, 2900f, 3200f, FastOutSlowInEasing)
    val subtitleAlpha = subtitleProgress
    val subtitleOffsetY = ((1f - subtitleProgress) * 6).dp

    Column(
        modifier = modifier.offset(y = (-16).dp), // Positioned slightly above vertical center
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Icon Canvas (Vector Drawing & Morphing)
        Box(
            modifier = Modifier.size(iconSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Draw Soft Ambient Shadow (Settles between 2100ms and 2500ms)
                if (shadowAlpha > 0f) {
                    val shadowOffset = Offset(w * 0.5f, h * 0.54f)
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SwiftGradientEnd.copy(alpha = shadowAlpha),
                                SwiftGradientEnd.copy(alpha = shadowAlpha * 0.4f),
                                Color.Transparent
                            ),
                            center = shadowOffset,
                            radius = w * 0.58f
                        ),
                        topLeft = Offset(w * 0.04f, h * 0.08f),
                        size = Size(w * 0.92f, h * 0.92f),
                        cornerRadius = CornerRadius(w * 0.26f, h * 0.26f)
                    )
                }

                // 2. Draw Rounded-Square Gradient Background (1650ms - 2100ms)
                if (bgAlpha > 0f) {
                    val scaledSize = w * bgScale
                    val left = (w - scaledSize) / 2f
                    val top = (h - scaledSize) / 2f
                    val cornerRadius = CornerRadius(scaledSize * 0.26f, scaledSize * 0.26f)

                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(SwiftGradientStart, SwiftGradientEnd),
                            start = Offset(left, top),
                            end = Offset(left + scaledSize, top + scaledSize)
                        ),
                        topLeft = Offset(left, top),
                        size = Size(scaledSize, scaledSize),
                        cornerRadius = cornerRadius,
                        alpha = bgAlpha
                    )
                }

                // 3. Draw Vector Bird Symbol with Progressive Stroke Trimming
                drawSwiftBirdSymbol(
                    scope = this,
                    canvasSize = size,
                    birdScale = birdScale,
                    birdColor = birdColor,
                    stroke1Progress = stroke1,
                    stroke2Progress = stroke2,
                    stroke3Progress = stroke3,
                    stroke4Progress = stroke4,
                    stroke5Progress = stroke5
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Brand Name "Swift"
        Text(
            text = "Swift",
            color = SwiftCharcoalTitle,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(y = titleOffsetY)
                .alpha(titleAlpha)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Tagline / Purpose "All-in-One PDF Utility"
        Text(
            text = "All-in-One PDF Utility",
            color = SwiftSubtitleGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(y = subtitleOffsetY)
                .alpha(subtitleAlpha)
        )
    }
}

/**
 * Draws the 5 aerodynamic vector curves forming the Swift bird & "S" symbol.
 * Uses Compose PathMeasure to achieve true progressive brush drawing at 60 FPS.
 */
private fun drawSwiftBirdSymbol(
    scope: DrawScope,
    canvasSize: Size,
    birdScale: Float,
    birdColor: Color,
    stroke1Progress: Float,
    stroke2Progress: Float,
    stroke3Progress: Float,
    stroke4Progress: Float,
    stroke5Progress: Float
) {
    val base = canvasSize.minDimension
    val scale = (base / 200f) * birdScale
    val offsetX = (canvasSize.width - (200f * scale)) / 2f
    val offsetY = (canvasSize.height - (200f * scale)) / 2f

    val pathMeasure = PathMeasure()

    // Helper to draw a trimmed path
    fun drawProgressiveStroke(path: Path, progress: Float, strokeWidth: Float) {
        if (progress <= 0f) return
        pathMeasure.setPath(path, false)
        val length = pathMeasure.length
        if (length > 0f) {
            val destination = Path()
            val drawLength = length * progress.coerceIn(0f, 1f)
            pathMeasure.getSegment(0f, drawLength, destination, startWithMoveTo = true)
            scope.drawPath(
                path = destination,
                color = birdColor,
                style = Stroke(
                    width = strokeWidth * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    // --- STROKE 1: Large Upper Wing (Sweeps from left to right along high arc) ---
    val path1 = Path().apply {
        moveTo(offsetX + (36f * scale), offsetY + (56f * scale))
        cubicTo(
            offsetX + (68f * scale), offsetY + (30f * scale),
            offsetX + (122f * scale), offsetY + (24f * scale),
            offsetX + (166f * scale), offsetY + (38f * scale)
        )
    }
    drawProgressiveStroke(path1, stroke1Progress, strokeWidth = 9.5f)

    // --- STROKE 2: Second Thinner Wing (Underneath Stroke 1, aerodynamic flow) ---
    val path2 = Path().apply {
        moveTo(offsetX + (54f * scale), offsetY + (76f * scale))
        cubicTo(
            offsetX + (80f * scale), offsetY + (54f * scale),
            offsetX + (124f * scale), offsetY + (48f * scale),
            offsetX + (152f * scale), offsetY + (62f * scale)
        )
    }
    drawProgressiveStroke(path2, stroke2Progress, strokeWidth = 6.8f)

    // --- STROKE 3: Central Main Body & 'S' Motion (Sweeping body & wing bend) ---
    val path3 = Path().apply {
        moveTo(offsetX + (42f * scale), offsetY + (116f * scale))
        cubicTo(
            offsetX + (68f * scale), offsetY + (98f * scale),
            offsetX + (98f * scale), offsetY + (76f * scale),
            offsetX + (126f * scale), offsetY + (84f * scale)
        )
        cubicTo(
            offsetX + (142f * scale), offsetY + (90f * scale),
            offsetX + (158f * scale), offsetY + (102f * scale),
            offsetX + (164f * scale), offsetY + (92f * scale)
        )
    }
    drawProgressiveStroke(path3, stroke3Progress, strokeWidth = 8.5f)

    // --- STROKE 4: Lower Wing / Tail Stroke (Sweeps downward & backward) ---
    val path4 = Path().apply {
        moveTo(offsetX + (78f * scale), offsetY + (126f * scale))
        cubicTo(
            offsetX + (62f * scale), offsetY + (148f * scale),
            offsetX + (44f * scale), offsetY + (162f * scale),
            offsetX + (28f * scale), offsetY + (168f * scale)
        )
    }
    drawProgressiveStroke(path4, stroke4Progress, strokeWidth = 8.0f)

    // --- STROKE 5: Sleek Head & Beak Shape (Front upper-right) ---
    val path5 = Path().apply {
        moveTo(offsetX + (154f * scale), offsetY + (74f * scale))
        quadraticBezierTo(
            offsetX + (174f * scale), offsetY + (66f * scale),
            offsetX + (182f * scale), offsetY + (56f * scale)
        )
    }
    drawProgressiveStroke(path5, stroke5Progress, strokeWidth = 7.0f)
}

// -------------------------------------------------------------------------
// PREVIEWS FOR ANDROID STUDIO DESIGN PREVIEW
// -------------------------------------------------------------------------

@Preview(name = "0.45s - Drawing 1st & 2nd Strokes", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SplashPreviewEarly() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground),
        contentAlignment = Alignment.Center
    ) {
        SwiftSplashContent(currentTimeMs = 450f)
    }
}

@Preview(name = "1.25s - Full Bird Drawn", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SplashPreviewBirdDrawn() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground),
        contentAlignment = Alignment.Center
    ) {
        SwiftSplashContent(currentTimeMs = 1250f)
    }
}

@Preview(name = "1.90s - Background Morph & Color Transition", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SplashPreviewMorph() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground),
        contentAlignment = Alignment.Center
    ) {
        SwiftSplashContent(currentTimeMs = 1900f)
    }
}

@Preview(name = "3.30s - Complete Splash Screen", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SplashPreviewComplete() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground),
        contentAlignment = Alignment.Center
    ) {
        SwiftSplashContent(currentTimeMs = 3300f)
    }
}
