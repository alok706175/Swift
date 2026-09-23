package com.swiftapp.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swiftapp.data.ESignPdfService
import com.swiftapp.data.model.HandwritingStyle
import com.swiftapp.data.model.SavedSignatureItem
import com.swiftapp.data.model.SignElementItem
import com.swiftapp.data.model.SignElementType
import com.swiftapp.data.model.SignatureCreationMode
import com.swiftapp.data.model.ESignUiState
import com.swiftapp.ui.components.*
import com.swiftapp.ui.viewmodel.ESignPdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ESignPdfScreen(
    initialPdfFile: File? = null,
    onNavigateBack: () -> Unit,
    onOpenPdf: (File) -> Unit,
    viewModel: ESignPdfViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docInfo by viewModel.documentInfo.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val pageBitmap by viewModel.pageBitmap.collectAsState()
    val isRendering by viewModel.isPageRendering.collectAsState()
    val zoomScale by viewModel.zoomScale.collectAsState()
    val elements by viewModel.elements.collectAsState()
    val selectedElementId by viewModel.selectedElementId.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isSigDialogVisible by viewModel.isSignatureDialogVisible.collectAsState()
    val isTextDialogVisible by viewModel.isTextDialogVisible.collectAsState()
    val savedSignatures by viewModel.savedSignatures.collectAsState()

    // File picker launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val tempFile = ESignPdfService.copyUriToCacheFile(context, it)
                if (tempFile != null) {
                    viewModel.loadDocument(context, tempFile)
                } else {
                    Toast.makeText(context, "Failed to load PDF file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(initialPdfFile) {
        initialPdfFile?.let { file ->
            if (file.exists()) {
                viewModel.loadDocument(context, file)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (docInfo != null) docInfo!!.file.name else "E-Sign PDF",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (docInfo != null) {
                            Text(
                                "Page ${currentPageIndex + 1} of ${docInfo!!.pageCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    TactileIconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (docInfo != null) {
                        // Zoom controls
                        TactileIconButton(onClick = { viewModel.setZoom(zoomScale - 0.2f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                        }
                        TactileIconButton(onClick = { viewModel.setZoom(zoomScale + 0.2f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                        }
                        TactileIconButton(onClick = { viewModel.setZoom(1.0f) }) {
                            Icon(Icons.Default.FitScreen, contentDescription = "Reset Zoom")
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Finish & Sign Button
                        TactileButton(
                            onClick = { viewModel.burnAndFlattenPdf(context) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign & Save", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (docInfo != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Page navigation thumbnail carousel
                        if (docInfo!!.pageCount > 1) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(docInfo!!.pageCount) { idx ->
                                    val isCurrent = (idx == currentPageIndex)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.clickable { viewModel.loadPage(context, idx) }
                                    ) {
                                        Text(
                                            text = "Page ${idx + 1}",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }

                        // Toolbar Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolActionButton(
                                icon = Icons.Outlined.Draw,
                                label = "Signature",
                                onClick = { viewModel.openSignatureDialog() }
                            )
                            ToolActionButton(
                                icon = Icons.Outlined.TextFields,
                                label = "Text",
                                onClick = { viewModel.openTextDialog() }
                            )
                            ToolActionButton(
                                icon = Icons.Outlined.CalendarToday,
                                label = "Date",
                                onClick = { viewModel.addDateElement() }
                            )
                            ToolActionButton(
                                icon = Icons.Outlined.CheckCircleOutline,
                                label = "Checkmark",
                                onClick = { viewModel.addSymbolElement(SignElementType.CHECKMARK) }
                            )
                            ToolActionButton(
                                icon = Icons.Outlined.Cancel,
                                label = "Cross",
                                onClick = { viewModel.addSymbolElement(SignElementType.CROSS) }
                            )
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
            if (docInfo == null) {
                // Empty state: Upload PDF
                ESignEmptyState(
                    onPickPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                )
            } else {
                // Active Document Viewer & Placement Canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { _, _ ->
                                // Deselect on background touch if needed
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    } else if (pageBitmap != null) {
                        InteractivePageCanvas(
                            pageBitmap = pageBitmap!!,
                            zoomScale = zoomScale,
                            elements = elements.filter { it.pageIndex == currentPageIndex },
                            selectedElementId = selectedElementId,
                            onSelectElement = { id -> viewModel.selectElement(id) },
                            onMoveElement = { id, x, y -> viewModel.updateElementPosition(id, x, y) },
                            onResizeElement = { id, w, h -> viewModel.updateElementSize(id, w, h) },
                            onDuplicateElement = { id -> viewModel.duplicateElement(id) },
                            onDeleteElement = { id -> viewModel.deleteElement(id) }
                        )
                    }
                }
            }
        }
    }

    // Signature Creation Modal (Draw, Type, Upload, Saved)
    if (isSigDialogVisible) {
        SignatureCreationDialog(
            savedSignatures = savedSignatures,
            onDismiss = { viewModel.closeSignatureDialog() },
            onConfirmSignature = { bitmap, saveToFav, name ->
                viewModel.addSignatureBitmap(context, bitmap, saveToFav, name)
            },
            onDeleteSavedSignature = { item ->
                viewModel.deleteSavedSignature(context, item)
            }
        )
    }

    // Text Annotation Dialog
    if (isTextDialogVisible) {
        TextAnnotationDialog(
            onDismiss = { viewModel.closeTextDialog() },
            onConfirm = { text, color ->
                viewModel.addTextElement(text, color)
            }
        )
    }

    // Processing Dialog
    if (uiState is ESignUiState.Processing) {
        val proc = uiState as ESignUiState.Processing
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
    if (uiState is ESignUiState.Success) {
        val successState = uiState as ESignUiState.Success
        ESignSuccessDialog(
            file = successState.outputFile,
            pageCount = successState.pageCount,
            fileSize = successState.fileSize,
            onClose = {
                viewModel.dismissSuccess()
            },
            onOpenPdf = { file ->
                onOpenPdf(file)
            }
        )
    }

    // Error Dialog
    if (uiState is ESignUiState.Error) {
        val err = uiState as ESignUiState.Error
        AlertDialog(
            onDismissRequest = { viewModel.dismissSuccess() },
            title = { Text("Signing Error") },
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
 * Interactive Page Viewport with draggable and resizable annotation overlays.
 */
@Composable
private fun InteractivePageCanvas(
    pageBitmap: Bitmap,
    zoomScale: Float,
    elements: List<SignElementItem>,
    selectedElementId: String?,
    onSelectElement: (String?) -> Unit,
    onMoveElement: (String, Float, Float) -> Unit,
    onResizeElement: (String, Float, Float) -> Unit,
    onDuplicateElement: (String) -> Unit,
    onDeleteElement: (String) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
                .shadow(6.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .onGloballyPositioned { coordinates ->
                    canvasSize = coordinates.size
                }
                .clickable { onSelectElement(null) }
        ) {
            // Document Page Bitmap
            Image(
                bitmap = pageBitmap.asImageBitmap(),
                contentDescription = "PDF Page",
                modifier = Modifier
                    .fillMaxWidth(zoomScale.coerceIn(0.6f, 1.0f))
                    .wrapContentHeight(),
                contentScale = ContentScale.FillWidth
            )

            // Placed Annotations Layer
            if (canvasSize.width > 0 && canvasSize.height > 0) {
                val pageW = canvasSize.width.toFloat()
                val pageH = canvasSize.height.toFloat()

                elements.forEach { item ->
                    val isSelected = (item.id == selectedElementId)
                    val elemX = (item.xNorm * pageW)
                    val elemY = (item.yNorm * pageH)
                    val elemW = (item.widthNorm * pageW)
                    val elemH = (item.heightNorm * pageH)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(elemX.roundToInt(), elemY.roundToInt()) }
                            .size(
                                width = with(LocalDensity.current) { elemW.toDp() },
                                height = with(LocalDensity.current) { elemH.toDp() }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) Color(0xFF1E88E5) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .background(
                                color = if (isSelected) Color(0xFF1E88E5).copy(alpha = 0.08f) else Color.Transparent
                            )
                            .pointerInput(item.id) {
                                detectDragGestures(
                                    onDragStart = { onSelectElement(item.id) },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val newXNorm = item.xNorm + (dragAmount.x / pageW)
                                        val newYNorm = item.yNorm + (dragAmount.y / pageH)
                                        onMoveElement(item.id, newXNorm, newYNorm)
                                    }
                                )
                            }
                    ) {
                        // Render Content
                        if (item.bitmap != null) {
                            Image(
                                bitmap = item.bitmap.asImageBitmap(),
                                contentDescription = item.type.displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (!item.text.isNullOrBlank()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.text,
                                    color = item.textColor,
                                    fontWeight = if (item.type == SignElementType.CHECKMARK || item.type == SignElementType.CROSS) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Selection Action Controls
                        if (isSelected) {
                            // Top-Right: Delete Icon
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .size(22.dp)
                                    .clickable { onDeleteElement(item.id) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                shadowElevation = 2.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = Color.White,
                                    modifier = Modifier.padding(3.dp)
                                )
                            }

                            // Top-Left: Duplicate Icon
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = (-8).dp, y = (-8).dp)
                                    .size(22.dp)
                                    .clickable { onDuplicateElement(item.id) },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 2.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate",
                                    tint = Color.White,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }

                            // Bottom-Right: Resize Handle
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 6.dp, y = 6.dp)
                                    .size(20.dp)
                                    .pointerInput(item.id) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val newWNorm = item.widthNorm + (dragAmount.x / pageW)
                                            val newHNorm = item.heightNorm + (dragAmount.y / pageH)
                                            onResizeElement(item.id, newWNorm, newHNorm)
                                        }
                                    },
                                shape = CircleShape,
                                color = Color(0xFF1E88E5),
                                shadowElevation = 2.dp
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Resize",
                                    tint = Color.White,
                                    modifier = Modifier.padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty State for document selection.
 */
@Composable
private fun ESignEmptyState(
    onPickPdf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.BorderColor,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "E-Sign PDF Document",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Open any PDF document to place your signature, initials, date stamps, and form annotations.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        TactileButton(
            onClick = onPickPdf,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Choose PDF to Sign", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/**
 * Single Action button on the bottom bar.
 */
@Composable
private fun ToolActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bounceClick()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Unified Signature Creation Dialog with 4 tabs (Draw, Type, Upload, Saved).
 */
@Composable
private fun SignatureCreationDialog(
    savedSignatures: List<SavedSignatureItem>,
    onDismiss: () -> Unit,
    onConfirmSignature: (Bitmap, Boolean, String) -> Unit,
    onDeleteSavedSignature: (SavedSignatureItem) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(SignatureCreationMode.DRAW) }
    val colors = listOf(
        Color(0xFF000000), // Black
        Color(0xFF003399), // Classic Blue
        Color(0xFF1A237E), // Dark Navy
        Color(0xFFD32F2F)  // Red
    )
    var selectedColor by remember { mutableStateOf(colors[1]) }
    var saveToFavorites by remember { mutableStateOf(true) }

    // Drawing State
    val paths = remember { mutableStateListOf<Pair<Path, Color>>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    // Typed State
    var typedName by remember { mutableStateOf("") }
    var selectedStyle by remember { mutableStateOf(HandwritingStyle.CURSIVE_CLASSIC) }

    // Gallery Picker for signature upload
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
            if (bitmap != null) {
                val transparentSig = ESignPdfService.removeWhiteBackground(bitmap)
                onConfirmSignature(transparentSig, saveToFavorites, "Signature")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Tabs
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Add Signature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tab Selector
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        SignatureCreationMode.entries.forEach { mode ->
                            Tab(
                                selected = selectedTab == mode,
                                onClick = { selectedTab = mode },
                                text = { Text(mode.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }

                // Tab Content Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    when (selectedTab) {
                        SignatureCreationMode.DRAW -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Signature Drawing Pad
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFFAFAFA))
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(selectedColor) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        val path = Path().apply { moveTo(offset.x, offset.y) }
                                                        currentPath = path
                                                        paths.add(Pair(path, selectedColor))
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        currentPath?.lineTo(change.position.x, change.position.y)
                                                        // Trigger redraw
                                                        if (paths.isNotEmpty()) {
                                                            val last = paths.removeAt(paths.size - 1)
                                                            paths.add(last)
                                                        }
                                                    },
                                                    onDragEnd = { currentPath = null },
                                                    onDragCancel = { currentPath = null }
                                                )
                                            }
                                    ) {
                                        paths.forEach { (path, col) ->
                                            drawPath(
                                                path = path,
                                                color = col,
                                                style = Stroke(
                                                    width = 4.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }

                                    if (paths.isEmpty()) {
                                        Text(
                                            "Draw your signature here with finger or stylus",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Color Selector & Clear / Undo
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        colors.forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (selectedColor == color) 2.5.dp else 0.dp,
                                                        color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { selectedColor = color }
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) },
                                            enabled = paths.isNotEmpty()
                                        ) {
                                            Text("Undo")
                                        }
                                        TextButton(
                                            onClick = { paths.clear() },
                                            enabled = paths.isNotEmpty()
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            }
                        }

                        SignatureCreationMode.TYPE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = typedName,
                                    onValueChange = { typedName = it },
                                    label = { Text("Enter Your Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Text(
                                    "Choose Handwriting Style:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                HandwritingStyle.entries.forEach { style ->
                                    val isSelected = (selectedStyle == style)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedStyle = style },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        ),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (typedName.isNotBlank()) typedName else "Your Signature",
                                                fontSize = 20.sp,
                                                fontStyle = FontStyle.Italic,
                                                fontFamily = if (style == HandwritingStyle.CURSIVE_CLASSIC) FontFamily.Serif else FontFamily.Cursive,
                                                color = selectedColor
                                            )
                                            RadioButton(selected = isSelected, onClick = { selectedStyle = style })
                                        }
                                    }
                                }
                            }
                        }

                        SignatureCreationMode.UPLOAD -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(54.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Upload Photo of Your Signature",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "We automatically remove white paper backgrounds to generate a transparent, crisp signature stamp.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { galleryPicker.launch("image/*") },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pick from Gallery")
                                }
                            }
                        }

                        SignatureCreationMode.SAVED -> {
                            if (savedSignatures.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "No saved signatures yet.\nDraw or type a signature and check 'Save to favorites'.",
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(savedSignatures) { item ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .clickable {
                                                    val bmp = BitmapFactory.decodeFile(item.file.absolutePath)
                                                    if (bmp != null) {
                                                        onConfirmSignature(bmp, false, "Signature")
                                                    }
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context).data(item.file).build(),
                                                    contentDescription = "Saved Signature",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                                IconButton(
                                                    onClick = { onDeleteSavedSignature(item) },
                                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Confirmation Actions
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (selectedTab == SignatureCreationMode.DRAW || selectedTab == SignatureCreationMode.TYPE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = saveToFavorites,
                                onCheckedChange = { saveToFavorites = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save to My Signatures for quick reuse", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        if (selectedTab == SignatureCreationMode.DRAW || selectedTab == SignatureCreationMode.TYPE) {
                            Button(
                                onClick = {
                                    if (selectedTab == SignatureCreationMode.DRAW) {
                                        if (paths.isNotEmpty()) {
                                            // Render drawing to Bitmap
                                            val w = 600
                                            val h = 300
                                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(bmp)
                                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                                strokeWidth = 10f
                                                style = android.graphics.Paint.Style.STROKE
                                                strokeCap = android.graphics.Paint.Cap.ROUND
                                                strokeJoin = android.graphics.Paint.Join.ROUND
                                            }
                                            paths.forEach { (path, color) ->
                                                paint.color = color.toArgb()
                                                canvas.drawPath(path.asAndroidPath(), paint)
                                            }
                                            onConfirmSignature(bmp, saveToFavorites, "Drawn Signature")
                                        } else {
                                            Toast.makeText(context, "Please draw a signature first", Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (selectedTab == SignatureCreationMode.TYPE) {
                                        if (typedName.isNotBlank()) {
                                            val bmp = ESignPdfService.createTypedSignatureBitmap(
                                                text = typedName.trim(),
                                                style = selectedStyle,
                                                colorInt = selectedColor.toArgb()
                                            )
                                            onConfirmSignature(bmp, saveToFavorites, typedName.trim())
                                        } else {
                                            Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Insert Signature")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Text Annotation Input Dialog.
 */
@Composable
private fun TextAnnotationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Color) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val colors = listOf(Color.Black, Color(0xFF003399), Color(0xFFD32F2F))
    var selectedColor by remember { mutableStateOf(colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text content") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { col ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (selectedColor == col) 2.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = col }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text.trim(), selectedColor) }) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Standardized Success Dialog.
 */
@Composable
private fun ESignSuccessDialog(
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
        val chooser = Intent.createChooser(shareIntent, "Share Signed PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    fun saveToDevice() {
        scope.launch {
            isSaving = true
            val result = ESignPdfService.savePdfToDownloads(context, file)
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
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = Color(0xFF2E7D32)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "PDF Signed Successfully!",
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
