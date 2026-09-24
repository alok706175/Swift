package com.swiftapp.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.swiftapp.data.model.FlashMode
import com.swiftapp.data.model.IdCardStep
import com.swiftapp.data.model.ScanCaptureMode
import com.swiftapp.utils.HapticFeedbackStrength
import com.swiftapp.utils.HapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Full-featured Camera Scanner with:
 * - Dedicated Capture Modes: Document, ID Card (2-sided), Book (dual split), Whiteboard, Business Card
 * - Auto-Capture (stability countdown) vs Manual Capture (shutter)
 * - Real-time Viewfinder Edge Guides & Center Split Overlays
 * - Continuous Flash / Torch & Auto controls
 */
@Composable
fun CameraScannerView(
    captureMode: ScanCaptureMode,
    onModeSelected: (ScanCaptureMode) -> Unit,
    flashMode: FlashMode,
    onCycleFlash: () -> Unit,
    isAutoCapture: Boolean,
    onToggleAutoCapture: () -> Unit,
    idCardStep: IdCardStep,
    onResetIdCard: () -> Unit,
    capturedPageCount: Int,
    onPhotoCaptured: (File) -> Unit,
    onPickGallery: () -> Unit,
    onFinishScanning: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler {
        if (capturedPageCount > 0) {
            onFinishScanning()
        } else {
            onClose()
        }
    }

    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Auto-capture countdown animation state (0f to 1f)
    var autoCaptureProgress by remember { mutableFloatStateOf(0f) }
    var isAutoCaptureCountingDown by remember { mutableStateOf(false) }

    // Torch & Flash sync
    LaunchedEffect(flashMode, camera) {
        val cam = camera ?: return@LaunchedEffect
        val imgCap = imageCapture ?: return@LaunchedEffect
        try {
            when (flashMode) {
                FlashMode.TORCH -> {
                    if (cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(true)
                    }
                    imgCap.flashMode = ImageCapture.FLASH_MODE_OFF
                }
                FlashMode.OFF -> {
                    if (cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(false)
                    }
                    imgCap.flashMode = ImageCapture.FLASH_MODE_OFF
                }
                FlashMode.AUTO -> {
                    if (cam.cameraInfo.hasFlashUnit()) {
                        cam.cameraControl.enableTorch(false)
                    }
                    imgCap.flashMode = ImageCapture.FLASH_MODE_AUTO
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Function to trigger image capture
    fun takePicture() {
        if (isCapturing) return
        val cap = imageCapture ?: return

        isCapturing = true
        HapticManager.performHaptic(view = view, strength = HapticFeedbackStrength.LIGHT)

        val photoFile = File(
            context.cacheDir,
            "scan_camera_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        cap.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    onPhotoCaptured(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Toast.makeText(context, "Failed to capture: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Auto-Capture Loop: When auto-capture is enabled and camera is ready, perform a smooth steady countdown
    LaunchedEffect(isAutoCapture, captureMode, idCardStep, isCapturing) {
        if (!isAutoCapture || isCapturing) {
            autoCaptureProgress = 0f
            isAutoCaptureCountingDown = false
            return@LaunchedEffect
        }

        // Delay after mode change or step change before starting countdown
        delay(1200)
        isAutoCaptureCountingDown = true
        autoCaptureProgress = 0f

        val totalSteps = 40
        for (i in 1..totalSteps) {
            if (!isAutoCapture || isCapturing) break
            delay(50)
            autoCaptureProgress = i.toFloat() / totalSteps
        }

        if (isAutoCapture && !isCapturing && autoCaptureProgress >= 0.95f) {
            takePicture()
            autoCaptureProgress = 0f
            isAutoCaptureCountingDown = false
        }
    }

    // Animated guide line scanner effect
    val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Live Preview View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetRotation(previewView.display?.rotation ?: android.view.Surface.ROTATION_0)
                        .build()

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            capture
                        )
                        imageCapture = capture
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Visual Overlay Guides for Edge Detection & Capture Modes
        ScannerGuideOverlay(
            captureMode = captureMode,
            laserYRatio = laserYRatio
        )

        // Top Controls Bar (Close, Flash)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable {
                        if (capturedPageCount > 0) onFinishScanning() else onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Flash / Torch Toggle Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (flashMode != FlashMode.OFF) Color(0xFFFFA000).copy(alpha = 0.3f)
                        else Color.Black.copy(alpha = 0.55f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (flashMode != FlashMode.OFF) Color(0xFFFFA000) else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable {
                        HapticManager.performHaptic(view = view, strength = HapticFeedbackStrength.LIGHT)
                        onCycleFlash()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        FlashMode.OFF -> Icons.Outlined.FlashOff
                        FlashMode.TORCH -> Icons.Filled.FlashOn
                        FlashMode.AUTO -> Icons.Filled.FlashAuto
                    },
                    contentDescription = "Flash ${flashMode.displayName}",
                    tint = if (flashMode != FlashMode.OFF) Color(0xFFFFA000) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Controls Area (Mode Switcher, Shutter Button, Pages Counter, Gallery)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                    )
                )
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector Carousel
            CaptureModeSelectorCarousel(
                selectedMode = captureMode,
                onModeSelected = { mode ->
                    HapticManager.performHaptic(view = view, strength = HapticFeedbackStrength.LIGHT)
                    onModeSelected(mode)
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Shutter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Gallery Shortcut
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onPickGallery() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Center: Big Shutter Trigger with Auto-Capture Progress Ring
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Auto-capture countdown ring
                    if (isAutoCapture && isAutoCaptureCountingDown) {
                        CircularProgressIndicator(
                            progress = { autoCaptureProgress },
                            modifier = Modifier.size(80.dp),
                            color = Color(0xFF4CAF50),
                            strokeWidth = 4.dp,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }

                    // Outer shutter border
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color.White, CircleShape)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(if (isCapturing) Color.LightGray else Color.White)
                            .clickable(enabled = !isCapturing) {
                                takePicture()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                // Right: Captured Pages Badge / Finish Button
                if (capturedPageCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 6.dp,
                        modifier = Modifier.clickable {
                            HapticManager.performHaptic(view = view, strength = HapticFeedbackStrength.LIGHT)
                            onFinishScanning()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Done",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Done ($capturedPageCount)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // Placeholder spacing to keep shutter centered
                    Spacer(modifier = Modifier.size(52.dp))
                }
            }
        }
    }
}

/**
 * Real-time dynamic viewfinder overlay tailored per capture mode.
 */
@Composable
private fun ScannerGuideOverlay(
    captureMode: ScanCaptureMode,
    laserYRatio: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        when (captureMode) {
            ScanCaptureMode.DOCUMENT, ScanCaptureMode.WHITEBOARD -> {
                // Document / Whiteboard Frame: 3:4 aspect ratio centered
                val frameWidth = canvasWidth * 0.85f
                val frameHeight = frameWidth * 1.35f
                val left = (canvasWidth - frameWidth) / 2f
                val top = (canvasHeight - frameHeight) / 2f - 30.dp.toPx()
                val right = left + frameWidth
                val bottom = top + frameHeight

                // Corner bracket guides
                val bracketLen = 36.dp.toPx()
                val strokeW = 4.dp.toPx()
                val bracketColor = Color.White

                // Top-Left
                drawLine(bracketColor, Offset(left, top), Offset(left + bracketLen, top), strokeW, StrokeCap.Round)
                drawLine(bracketColor, Offset(left, top), Offset(left, top + bracketLen), strokeW, StrokeCap.Round)

                // Top-Right
                drawLine(bracketColor, Offset(right, top), Offset(right - bracketLen, top), strokeW, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, top), Offset(right, top + bracketLen), strokeW, StrokeCap.Round)

                // Bottom-Left
                drawLine(bracketColor, Offset(left, bottom), Offset(left + bracketLen, bottom), strokeW, StrokeCap.Round)
                drawLine(bracketColor, Offset(left, bottom), Offset(left, bottom - bracketLen), strokeW, StrokeCap.Round)

                // Bottom-Right
                drawLine(bracketColor, Offset(right, bottom), Offset(right - bracketLen, bottom), strokeW, StrokeCap.Round)
                drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - bracketLen), strokeW, StrokeCap.Round)

                // Animated Green Laser Guide Line
                val currentLaserY = top + (bottom - top) * laserYRatio
                drawLine(
                    color = Color(0xFF00E676).copy(alpha = 0.85f),
                    start = Offset(left + 8.dp.toPx(), currentLaserY),
                    end = Offset(right - 8.dp.toPx(), currentLaserY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                )
            }

            ScanCaptureMode.ID_CARD -> {
                // Standard CR-80 Card Frame (85.6mm : 53.98mm ~ 1.58 : 1 ratio)
                val cardWidth = canvasWidth * 0.88f
                val cardHeight = cardWidth / 1.58f
                val left = (canvasWidth - cardWidth) / 2f
                val top = (canvasHeight - cardHeight) / 2f - 40.dp.toPx()

                // Draw rounded card bounding box
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.8f),
                    topLeft = Offset(left, top),
                    size = Size(cardWidth, cardHeight),
                    cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f), 0f)
                    )
                )

                // Card photo placeholder silhouette on left
                val photoBoxW = cardWidth * 0.26f
                val photoBoxH = cardHeight * 0.55f
                val photoLeft = left + cardWidth * 0.08f
                val photoTop = top + (cardHeight - photoBoxH) / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    topLeft = Offset(photoLeft, photoTop),
                    size = Size(photoBoxW, photoBoxH),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Card simulated text lines on right
                val textLeft = photoLeft + photoBoxW + 16.dp.toPx()
                val textRight = left + cardWidth - 20.dp.toPx()
                for (i in 0..2) {
                    val lineY = photoTop + 14.dp.toPx() + (i * 20.dp.toPx())
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(textLeft, lineY),
                        end = Offset(textRight - (i * 30.dp.toPx()), lineY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            ScanCaptureMode.BOOK -> {
                // Book Spread: Wide horizontal box with distinct vertical center divider
                val bookWidth = canvasWidth * 0.92f
                val bookHeight = bookWidth * 0.72f
                val left = (canvasWidth - bookWidth) / 2f
                val top = (canvasHeight - bookHeight) / 2f - 30.dp.toPx()
                val right = left + bookWidth
                val bottom = top + bookHeight
                val centerX = left + bookWidth / 2f

                // Outer border
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.7f),
                    topLeft = Offset(left, top),
                    size = Size(bookWidth, bookHeight),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Center vertical split line (Dashed orange/amber)
                drawLine(
                    color = Color(0xFFFFB300),
                    start = Offset(centerX, top + 6.dp.toPx()),
                    end = Offset(centerX, bottom - 6.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(25f, 15f), 0f),
                    cap = StrokeCap.Round
                )

                // Spine arrows at top and bottom of center
                drawCircle(
                    color = Color(0xFFFFB300),
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, top)
                )
                drawCircle(
                    color = Color(0xFFFFB300),
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, bottom)
                )
            }

            ScanCaptureMode.BUSINESS_CARD -> {
                // Compact 3.5" x 2" (1.75 : 1 ratio)
                val cardWidth = canvasWidth * 0.82f
                val cardHeight = cardWidth / 1.75f
                val left = (canvasWidth - cardWidth) / 2f
                val top = (canvasHeight - cardHeight) / 2f - 40.dp.toPx()

                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(cardWidth, cardHeight),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }
    }
}

/**
 * Bottom horizontal scrolling mode carousel for switching capture modes.
 */
@Composable
private fun CaptureModeSelectorCarousel(
    selectedMode: ScanCaptureMode,
    onModeSelected: (ScanCaptureMode) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScanCaptureMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.12f)
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.displayName.uppercase(),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
