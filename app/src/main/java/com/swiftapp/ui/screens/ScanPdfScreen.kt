package com.swiftapp.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.swiftapp.data.ScanPdfService
import com.swiftapp.data.model.MarginOption
import com.swiftapp.data.model.PageSizeOption
import com.swiftapp.data.model.PolygonCorners
import com.swiftapp.data.model.ScanExportConfig
import com.swiftapp.data.model.ScanFilter
import com.swiftapp.data.model.ScanPageItem
import com.swiftapp.data.model.ScanUiState
import com.swiftapp.ui.components.*
import com.swiftapp.ui.viewmodel.ScanPdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import kotlin.math.hypot
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.swiftapp.data.model.FlashMode
import com.swiftapp.data.model.IdCardStep
import com.swiftapp.data.model.ScanCaptureMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPdfScreen(
    onNavigateBack: () -> Unit,
    onOpenPdf: (File) -> Unit,
    viewModel: ScanPdfViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val pages by viewModel.pages.collectAsState()
    val activePageIndex by viewModel.activePageIndex.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isCropDialogVisible by viewModel.isCropDialogVisible.collectAsState()
    val isExportDialogVisible by viewModel.isExportDialogVisible.collectAsState()
    val exportConfig by viewModel.exportConfig.collectAsState()

    val captureMode by viewModel.captureMode.collectAsState()
    val isAutoCapture by viewModel.isAutoCapture.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()
    val idCardStep by viewModel.idCardStep.collectAsState()

    var isCameraActive by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showAddSourceSheet by remember { mutableStateOf(false) }

    // Permission Launcher for Camera
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCameraActive = true
        } else {
            Toast.makeText(context, "Camera permission is required to scan documents", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun startLiveCamera(mode: ScanCaptureMode? = null) {
        if (mode != null) {
            viewModel.setCaptureMode(mode)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isCameraActive = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Multi-Image Gallery Picker Launcher
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importGalleryImages(context, uris)
        }
    }

    // Fullscreen Camera Scanner
    if (isCameraActive) {
        CameraScannerView(
            captureMode = captureMode,
            onModeSelected = { viewModel.setCaptureMode(it) },
            flashMode = flashMode,
            onCycleFlash = { viewModel.cycleFlashMode() },
            isAutoCapture = isAutoCapture,
            onToggleAutoCapture = { viewModel.toggleAutoCapture() },
            idCardStep = idCardStep,
            onResetIdCard = { viewModel.resetIdCardCapture() },
            capturedPageCount = pages.size,
            onPhotoCaptured = { photoFile ->
                viewModel.addCapturedImage(context, photoFile)
            },
            onPickGallery = {
                galleryPickerLauncher.launch(arrayOf("image/*", "image/jpeg", "image/png", "image/webp"))
            },
            onFinishScanning = {
                isCameraActive = false
            },
            onClose = {
                if (pages.isEmpty()) {
                    onNavigateBack()
                } else {
                    isCameraActive = false
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Scan to PDF",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        )
                        if (pages.isNotEmpty()) {
                            Text(
                                "${pages.size} page${if (pages.size > 1) "s" else ""} captured",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    TactileIconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (pages.isNotEmpty()) {
                        TactileIconButton(onClick = { viewModel.clearAll() }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (pages.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileOutlinedButton(
                            onClick = { showAddSourceSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Page")
                        }

                        TactileButton(
                            onClick = { viewModel.openExportDialog() },
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF (${pages.size})")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (pages.isEmpty()) {
                // Empty state: Options to start scanning or pick images
                ScanEmptyState(
                    onStartCamera = { mode -> startLiveCamera(mode) },
                    onPickGallery = { galleryPickerLauncher.launch(arrayOf("image/*", "image/jpeg", "image/png", "image/webp")) }
                )
            } else {
                // Active Scan View
                val activePage = pages.getOrNull(activePageIndex) ?: pages.first()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Page Info & Preview Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                            .shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (activePage.isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            } else {
                                val imageModel = activePage.previewImageFile ?: activePage.originalImageFile
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageModel)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Page ${activePageIndex + 1}",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            // Badge for current page number
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = "Page ${activePageIndex + 1} of ${pages.size}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Quick Actions for Active Page
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScanToolActionItem(
                            icon = Icons.Outlined.Crop,
                            label = "Crop",
                            onClick = {
                                com.swiftapp.utils.HapticManager.light()
                                viewModel.openCropDialog()
                            }
                        )
                        ScanToolActionItem(
                            icon = Icons.Outlined.RotateRight,
                            label = "Rotate",
                            onClick = {
                                com.swiftapp.utils.HapticManager.light()
                                viewModel.rotatePage(context, activePageIndex, clockwise = true)
                            }
                        )
                        ScanToolActionItem(
                            icon = Icons.Outlined.Image,
                            label = "Save JPG",
                            onClick = {
                                com.swiftapp.utils.HapticManager.light()
                                viewModel.saveCurrentPageAsJpeg(context) { ok, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        ScanToolActionItem(
                            icon = Icons.Outlined.Share,
                            label = "Share",
                            onClick = {
                                com.swiftapp.utils.HapticManager.light()
                                viewModel.shareCurrentPageAsJpeg(context)
                            }
                        )
                        ScanToolActionItem(
                            icon = Icons.Outlined.Delete,
                            label = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                com.swiftapp.utils.HapticManager.light()
                                viewModel.deletePage(activePageIndex)
                            }
                        )
                    }

                    // Filter Options Bar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Document Enhancement Filter",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ScanFilter.entries.forEach { filter ->
                                val isSelected = activePage.filter == filter
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateFilter(context, activePageIndex, filter) },
                                    label = { Text(filter.displayName, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // Thumbnails Strip
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "All Pages (${pages.size})",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (pages.size > 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { viewModel.movePage(activePageIndex, activePageIndex - 1) },
                                        enabled = activePageIndex > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Move Left")
                                    }
                                    IconButton(
                                        onClick = { viewModel.movePage(activePageIndex, activePageIndex + 1) },
                                        enabled = activePageIndex < pages.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Move Right")
                                    }
                                }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(pages) { index, item ->
                                val isSelected = index == activePageIndex
                                Box(
                                    modifier = Modifier
                                        .size(width = 76.dp, height = 100.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { viewModel.selectPage(index) }
                                ) {
                                    val thumbModel = item.previewImageFile ?: item.originalImageFile
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(thumbModel)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Thumb ${index + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Page Number Chip
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Add More Card
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(width = 76.dp, height = 100.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                        .clickable { showAddSourceSheet = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add Page", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Add", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }

    // Modal Sheet to Pick Camera or Gallery
    if (showAddSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSourceSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Add Scanned Page",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clickable {
                                showAddSourceSheet = false
                                startLiveCamera()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Use Camera", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 13.sp)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clickable {
                                showAddSourceSheet = false
                                galleryPickerLauncher.launch(arrayOf("image/*", "image/jpeg", "image/png", "image/webp"))
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("From Gallery", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 4-Point Polygon Interactive Cropper Dialog
    if (isCropDialogVisible && pages.isNotEmpty()) {
        val activePage = pages.getOrNull(activePageIndex) ?: pages.first()
        InteractivePolygonCropDialog(
            pageItem = activePage,
            onDismiss = { viewModel.closeCropDialog() },
            onApply = { newCorners ->
                viewModel.updateCorners(context, activePageIndex, newCorners)
                viewModel.closeCropDialog()
            }
        )
    }

    // Export PDF Settings Dialog
    if (isExportDialogVisible) {
        ScanExportDialog(
            config = exportConfig,
            pageCount = pages.size,
            onDismiss = { viewModel.closeExportDialog() },
            onExport = { updatedConfig ->
                viewModel.updateExportConfig(updatedConfig)
                viewModel.generatePdf(context)
            }
        )
    }

    // Processing Dialog
    if (uiState is ScanUiState.Processing) {
        val proc = uiState as ScanUiState.Processing
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { proc.progress },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        proc.message,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Success Dialog
    if (uiState is ScanUiState.Success) {
        val successState = uiState as ScanUiState.Success
        ScanSuccessDialog(
            file = successState.outputFile,
            pageCount = successState.pageCount,
            fileSize = successState.fileSize,
            onClose = {
                viewModel.resetState()
            },
            onOpenPdf = { file ->
                onOpenPdf(file)
            }
        )
    }

    // Error Dialog
    if (uiState is ScanUiState.Error) {
        val err = uiState as ScanUiState.Error
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            title = { Text("Scan Failed") },
            text = { Text(err.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSuccess() }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Empty State for Scan to PDF screen with Dedicated Capture Mode Presets.
 */
@Composable
private fun ScanEmptyState(
    onStartCamera: (ScanCaptureMode) -> Unit,
    onPickGallery: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Scan Hero Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable { onStartCamera(ScanCaptureMode.DOCUMENT) },
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Start Document Scanner",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Auto-Capture, Edge Alignment & HD Flattening",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.85f)),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dedicated Preset Capture Modes Grid Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Dedicated Capture Modes",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Capture Mode Presets Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScanModeCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Badge,
                title = "ID Card",
                subtitle = "2-Sided single page merge",
                badge = "Front + Back",
                tintColor = Color(0xFF00C853),
                onClick = { onStartCamera(ScanCaptureMode.ID_CARD) }
            )

            ScanModeCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.MenuBook,
                title = "Book Scan",
                subtitle = "Dual-page auto split",
                badge = "Auto Split",
                tintColor = Color(0xFFFFAB00),
                onClick = { onStartCamera(ScanCaptureMode.BOOK) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScanModeCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Dashboard,
                title = "Whiteboard",
                subtitle = "Glare-free text filter",
                badge = "High Contrast",
                tintColor = Color(0xFF2979FF),
                onClick = { onStartCamera(ScanCaptureMode.WHITEBOARD) }
            )

            ScanModeCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.ContactPage,
                title = "Business Card",
                subtitle = "Compact card crop",
                badge = "Card Preset",
                tintColor = Color(0xFFAA00FF),
                onClick = { onStartCamera(ScanCaptureMode.BUSINESS_CARD) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Import from Gallery Button
        TactileOutlinedButton(
            onClick = onPickGallery,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Images from Gallery", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

/**
 * Modern preset card for dedicated capture modes.
 */
@Composable
private fun ScanModeCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    tintColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tintColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tintColor, modifier = Modifier.size(22.dp))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tintColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = badge,
                        color = tintColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Single Action tool item on scan editing bar.
 */
@Composable
private fun ScanToolActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Interactive 4-Point Polygon Cropper Dialog with touch loupe magnifier.
 */
@Composable
private fun InteractivePolygonCropDialog(
    pageItem: ScanPageItem,
    onDismiss: () -> Unit,
    onApply: (PolygonCorners) -> Unit
) {
    val context = LocalContext.current
    var corners by remember { mutableStateOf(pageItem.corners) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }

                    Text(
                        "Adjust Corners",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    TextButton(onClick = { onApply(corners) }) {
                        Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Main Cropper Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            containerSize = coordinates.size
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Raw original image
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pageItem.originalImageFile)
                            .crossfade(false)
                            .build(),
                        contentDescription = "Original Page",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // Interactive Drag Layer
                    if (containerSize.width > 0 && containerSize.height > 0) {
                        // Calculate aspect ratio fit inside container
                        val imgW = pageItem.width.toFloat()
                        val imgH = pageItem.height.toFloat()
                        val contW = containerSize.width.toFloat()
                        val contH = containerSize.height.toFloat()

                        val scale = if (imgW > 0 && imgH > 0) {
                            kotlin.math.min(contW / imgW, contH / imgH)
                        } else 1f

                        val displayW = imgW * scale
                        val displayH = imgH * scale
                        val offsetX = (contW - displayW) / 2f
                        val offsetY = (contH - displayH) / 2f

                        // Convert normalized corners (0..1) to canvas pixel coordinates
                        fun normToPx(norm: Offset): Offset {
                            return Offset(offsetX + norm.x * displayW, offsetY + norm.y * displayH)
                        }

                        fun pxToNorm(px: Offset): Offset {
                            val nx = ((px.x - offsetX) / displayW).coerceIn(0f, 1f)
                            val ny = ((px.y - offsetY) / displayH).coerceIn(0f, 1f)
                            return Offset(nx, ny)
                        }

                        val pTL = normToPx(corners.topLeft)
                        val pTR = normToPx(corners.topRight)
                        val pBR = normToPx(corners.bottomRight)
                        val pBL = normToPx(corners.bottomLeft)

                        // Midpoint Edge handles
                        val pTop = (pTL + pTR) / 2f
                        val pRight = (pTR + pBR) / 2f
                        val pBottom = (pBL + pBR) / 2f
                        val pLeft = (pTL + pBL) / 2f

                        val allPoints = listOf(pTL, pTR, pBR, pBL, pTop, pRight, pBottom, pLeft)

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(corners) {
                                    detectDragGestures(
                                        onDragStart = { touchOffset ->
                                            val closestIdx = allPoints.indices.minByOrNull { i ->
                                                hypot((allPoints[i].x - touchOffset.x).toDouble(), (allPoints[i].y - touchOffset.y).toDouble())
                                            }
                                            if (closestIdx != null && hypot((allPoints[closestIdx].x - touchOffset.x).toDouble(), (allPoints[closestIdx].y - touchOffset.y).toDouble()) < 110.0) {
                                                activeCornerIndex = closestIdx
                                                com.swiftapp.utils.HapticManager.light()
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val idx = activeCornerIndex ?: return@detectDragGestures
                                            when (idx) {
                                                0 -> corners = corners.copy(topLeft = pxToNorm(pTL + dragAmount))
                                                1 -> corners = corners.copy(topRight = pxToNorm(pTR + dragAmount))
                                                2 -> corners = corners.copy(bottomRight = pxToNorm(pBR + dragAmount))
                                                3 -> corners = corners.copy(bottomLeft = pxToNorm(pBL + dragAmount))
                                                4 -> { // Top Edge
                                                    corners = corners.copy(
                                                        topLeft = pxToNorm(pTL + dragAmount),
                                                        topRight = pxToNorm(pTR + dragAmount)
                                                    )
                                                }
                                                5 -> { // Right Edge
                                                    corners = corners.copy(
                                                        topRight = pxToNorm(pTR + dragAmount),
                                                        bottomRight = pxToNorm(pBR + dragAmount)
                                                    )
                                                }
                                                6 -> { // Bottom Edge
                                                    corners = corners.copy(
                                                        bottomLeft = pxToNorm(pBL + dragAmount),
                                                        bottomRight = pxToNorm(pBR + dragAmount)
                                                    )
                                                }
                                                7 -> { // Left Edge
                                                    corners = corners.copy(
                                                        topLeft = pxToNorm(pTL + dragAmount),
                                                        bottomLeft = pxToNorm(pBL + dragAmount)
                                                    )
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            activeCornerIndex = null
                                        },
                                        onDragCancel = {
                                            activeCornerIndex = null
                                        }
                                    )
                                }
                        ) {
                            // Draw Semi-transparent outer mask
                            val polygonPath = Path().apply {
                                moveTo(pTL.x, pTL.y)
                                lineTo(pTR.x, pTR.y)
                                lineTo(pBR.x, pBR.y)
                                lineTo(pBL.x, pBL.y)
                                close()
                            }

                            // Draw polygon edge lines
                            drawPath(
                                path = polygonPath,
                                color = Color(0xFF388AF6),
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Draw Edge Midpoint pill handles (Indices 4..7)
                            val edgePoints = listOf(pTop, pRight, pBottom, pLeft)
                            for ((i, pt) in edgePoints.withIndex()) {
                                val isHeld = (i + 4 == activeCornerIndex)
                                val radius = if (isHeld) 11.dp.toPx() else 8.dp.toPx()
                                drawCircle(color = Color.White, radius = radius + 2.dp.toPx(), center = pt)
                                drawCircle(color = Color(0xFF0D47A1), radius = radius, center = pt)
                            }

                            // Draw corner handles (Indices 0..3)
                            val cornerPoints = listOf(pTL, pTR, pBR, pBL)
                            for ((i, pt) in cornerPoints.withIndex()) {
                                val isHeld = (i == activeCornerIndex)
                                val radius = if (isHeld) 18.dp.toPx() else 14.dp.toPx()

                                // Outer white halo
                                drawCircle(
                                    color = Color.White,
                                    radius = radius,
                                    center = pt
                                )
                                // Inner blue accent
                                drawCircle(
                                    color = Color(0xFF1E88E5),
                                    radius = radius - 3.dp.toPx(),
                                    center = pt
                                )
                                // Center white dot
                                drawCircle(
                                    color = Color.White,
                                    radius = 4.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                // Bottom presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { corners = PolygonCorners.FULL }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Full Image", color = Color.White)
                    }
                    TextButton(onClick = { corners = PolygonCorners.DEFAULT }) {
                        Icon(Icons.Default.CropFree, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto Margins", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Export PDF Settings Dialog.
 */
@Composable
private fun ScanExportDialog(
    config: ScanExportConfig,
    pageCount: Int,
    onDismiss: () -> Unit,
    onExport: (ScanExportConfig) -> Unit
) {
    var fileName by remember { mutableStateOf(config.fileName) }
    var selectedPageSize by remember { mutableStateOf(config.pageSize) }
    var selectedMargin by remember { mutableStateOf(config.margin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Export PDF Document",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Page Size Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Page Dimensions", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    PageSizeOption.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedPageSize = option }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedPageSize == option,
                                onClick = { selectedPageSize = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(option.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(option.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Margin Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Page Margins", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    MarginOption.entries.forEach { marginOpt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedMargin = marginOpt }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMargin == marginOpt,
                                onClick = { selectedMargin = marginOpt }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(marginOpt.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(
                        config.copy(
                            fileName = if (fileName.isNotBlank()) fileName.trim() else config.fileName,
                            pageSize = selectedPageSize,
                            margin = selectedMargin
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Generate ($pageCount pages)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Modern Success Dialog with vertical action buttons and top-right close 'X'.
 */
@Composable
private fun ScanSuccessDialog(
    file: File,
    pageCount: Int,
    fileSize: Long,
    onClose: () -> Unit,
    onOpenPdf: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    fun sharePdf() {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Scanned PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    fun saveToDevice() {
        scope.launch {
            isSaving = true
            val result = ScanPdfService.savePdfToDownloads(context, file)
            isSaving = false
            result.fold(
                onSuccess = { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
                onFailure = { err ->
                    Toast.makeText(context, "Failed to save: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Top-Right Close Button
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedSuccessCheckmark(
                            size = 64.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "PDF Scanned Successfully!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File Details Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "File Name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$pageCount page${if (pageCount > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "File Size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFileSize(fileSize),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Vertical Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Open PDF
                    Button(
                        onClick = {
                            onClose()
                            onOpenPdf(file)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Open PDF",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }

                    // 2. Save to Device
                    OutlinedButton(
                        onClick = { saveToDevice() },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSaving) "Saving..." else "Save to Device",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                    }

                    // 3. Share
                    FilledTonalButton(
                        onClick = { sharePdf() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}
