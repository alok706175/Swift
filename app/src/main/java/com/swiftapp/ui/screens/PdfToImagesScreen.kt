package com.swiftapp.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swiftapp.data.PdfToImagesService
import com.swiftapp.data.model.ConvertedImageItem
import com.swiftapp.data.model.ImageDpiOption
import com.swiftapp.data.model.ImageOutputFormat
import com.swiftapp.data.model.PdfImageExtractMode
import com.swiftapp.data.model.PdfPageThumbnailItem
import com.swiftapp.data.model.PdfToImagesConfig
import com.swiftapp.data.model.PdfToImagesResult
import com.swiftapp.data.model.PdfToImagesUiState
import com.swiftapp.ui.components.*
import com.swiftapp.ui.viewmodel.PdfToImagesViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImagesScreen(
    initialPdfFile: File? = null,
    onNavigateBack: () -> Unit,
    onOpenPdf: (File) -> Unit,
    viewModel: PdfToImagesViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val config by viewModel.config.collectAsState()
    val previewImage by viewModel.previewImage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var isSettingsOpen by remember { mutableStateOf(false) }

    // File Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val tempFile = PdfToImagesService.copyUriToCacheFile(context, it)
                if (tempFile != null) {
                    viewModel.loadPdfDocument(context, tempFile)
                } else {
                    Toast.makeText(context, "Failed to load PDF file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(initialPdfFile) {
        initialPdfFile?.let { file ->
            if (file.exists()) {
                viewModel.loadPdfDocument(context, file)
            }
        }
    }

    val selectedCount = thumbnails.count { it.isSelected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "PDF to Images",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        )
                        if (selectedFile != null) {
                            Text(
                                "${selectedFile!!.name} \u2022 $totalPages pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    TactileIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedFile != null) {
                        TactileIconButton(onClick = { isSettingsOpen = true }) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Settings")
                        }
                        TactileIconButton(onClick = { viewModel.resetState() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Reset")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (selectedFile != null) {
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
                            onClick = { isSettingsOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(config.format.name)
                        }

                        TactileButton(
                            onClick = { viewModel.convertPdf(context) },
                            enabled = selectedCount > 0,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (config.extractMode == PdfImageExtractMode.RENDER_PAGES) "Convert ($selectedCount Pages)" else "Extract Images",
                                fontWeight = FontWeight.Bold
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
            if (selectedFile == null) {
                // Empty state view
                PdfToImagesEmptyState(
                    onPickPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                )
            } else {
                // Active document conversion view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Dual Mode Segmented Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Extraction Strategy",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = config.extractMode == PdfImageExtractMode.RENDER_PAGES,
                                    onClick = { viewModel.updateExtractMode(PdfImageExtractMode.RENDER_PAGES) },
                                    label = { Text("Entire Pages", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f)
                                )

                                FilterChip(
                                    selected = config.extractMode == PdfImageExtractMode.EXTRACT_EMBEDDED,
                                    onClick = { viewModel.updateExtractMode(PdfImageExtractMode.EXTRACT_EMBEDDED) },
                                    label = { Text("Embedded Only", fontSize = 12.sp) },
                                    leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Page Selection Header & Custom Range
                    if (config.extractMode == PdfImageExtractMode.RENDER_PAGES) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Select Pages ($selectedCount of $totalPages)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.selectAllPages() }) {
                                    Text("All", fontSize = 12.sp)
                                }
                                TextButton(onClick = { viewModel.deselectAllPages() }) {
                                    Text("None", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Thumbnails Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(thumbnails) { item ->
                            PdfThumbnailCard(
                                item = item,
                                onClick = { viewModel.togglePageSelection(item.pageIndex) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Settings Modal
    if (isSettingsOpen) {
        PdfToImagesSettingsDialog(
            config = config,
            onDismiss = { isSettingsOpen = false },
            onApply = { newConfig ->
                viewModel.updateExtractMode(newConfig.extractMode)
                viewModel.updateOutputFormat(newConfig.format)
                viewModel.updateDpi(newConfig.dpi)
                viewModel.updateJpgQuality(newConfig.jpgQuality)
                isSettingsOpen = false
            }
        )
    }

    // Fullscreen Lightbox
    if (previewImage != null) {
        ConvertedImageLightboxDialog(
            item = previewImage!!,
            onDismiss = { viewModel.closePreview() }
        )
    }

    // Processing Dialog
    if (uiState is PdfToImagesUiState.Processing) {
        val proc = uiState as PdfToImagesUiState.Processing
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
    if (uiState is PdfToImagesUiState.Success) {
        val successState = uiState as PdfToImagesUiState.Success
        PdfToImagesSuccessDialog(
            result = successState.result,
            onClose = {
                viewModel.dismissSuccess()
            },
            onPreviewImage = { img ->
                viewModel.openPreview(img)
            }
        )
    }

    // Error Dialog
    if (uiState is PdfToImagesUiState.Error) {
        val err = uiState as PdfToImagesUiState.Error
        AlertDialog(
            onDismissRequest = { viewModel.resetState() },
            title = { Text("Conversion Failed") },
            text = { Text(err.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetState() }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Empty state view with icon and selector.
 */
@Composable
private fun PdfToImagesEmptyState(
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
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Convert PDF to Images",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Extract embedded image objects or convert entire pages into high-resolution JPG / PNG image files.",
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
            Text("Select PDF File", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/**
 * Individual Page Thumbnail card with checkbox.
 */
@Composable
private fun PdfThumbnailCard(
    item: PdfPageThumbnailItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .bounceClick()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .border(
                width = if (item.isSelected) 2.dp else 1.dp,
                color = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.thumbnailBitmap != null) {
                Image(
                    bitmap = item.thumbnailBitmap.asImageBitmap(),
                    contentDescription = "Page ${item.pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
                )
            }

            // Top-Right Checkbox
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .padding(4.dp)
            )

            // Bottom-Left Page Number
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Text(
                    text = "${item.pageIndex + 1}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Format and DPI Settings Dialog.
 */
@Composable
private fun PdfToImagesSettingsDialog(
    config: PdfToImagesConfig,
    onDismiss: () -> Unit,
    onApply: (PdfToImagesConfig) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(config.format) }
    var selectedDpi by remember { mutableStateOf(config.dpi) }
    var jpgQuality by remember { mutableStateOf(config.jpgQuality.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Output Image Settings", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Output Format
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Image Format", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    ImageOutputFormat.entries.forEach { format ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFormat = format }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedFormat == format, onClick = { selectedFormat = format })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(format.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // DPI Resolution
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Resolution & Quality", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    ImageDpiOption.entries.forEach { dpiOpt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedDpi = dpiOpt }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedDpi == dpiOpt, onClick = { selectedDpi = dpiOpt })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(dpiOpt.displayName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // JPG Quality Slider
                if (selectedFormat == ImageOutputFormat.JPG) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("JPEG Quality", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            Text("${jpgQuality.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = jpgQuality,
                            onValueChange = { jpgQuality = it },
                            valueRange = 50f..100f,
                            steps = 9
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        config.copy(
                            format = selectedFormat,
                            dpi = selectedDpi,
                            jpgQuality = jpgQuality.toInt()
                        )
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Fullscreen Lightbox for previewing extracted images.
 */
@Composable
private fun ConvertedImageLightboxDialog(
    item: ConvertedImageItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = item.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.size(48.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(item.file).build(),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = "${item.width} \u00d7 ${item.height} px \u2022 ${formatFileSize(item.size)}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                )
            }
        }
    }
}

/**
 * Standardized Success Dialog for PDF to Images.
 */
@Composable
private fun PdfToImagesSuccessDialog(
    result: PdfToImagesResult,
    onClose: () -> Unit,
    onPreviewImage: (ConvertedImageItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    val mainFileToSave = result.outputZipFile ?: result.individualImages.first().file
    val mimeType = if (result.isSingleImage) "image/jpeg" else "application/zip"

    fun shareMainOutput() {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            mainFileToSave
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(mainFileToSave.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Converted Images")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    fun saveMainToDevice() {
        scope.launch {
            isSaving = true
            val saveResult = PdfToImagesService.saveFileToDownloads(context, mainFileToSave, mimeType)
            isSaving = false
            saveResult.fold(
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
                            text = "Images Extracted Successfully!",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Details Card
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
                                text = "Package Name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = mainFileToSave.name,
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
                                text = "Total Images",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${result.totalImages} image${if (result.totalImages > 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Size",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatFileSize(result.totalSize),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Extracted images thumbnail preview strip
                if (result.individualImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(result.individualImages) { img ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE0E0E0))
                                    .clickable { onPreviewImage(img) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(img.file).build(),
                                    contentDescription = img.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Vertical Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Preview / Open Image
                    Button(
                        onClick = {
                            if (result.individualImages.isNotEmpty()) {
                                onPreviewImage(result.individualImages.first())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (result.isSingleImage) "Open Image" else "Preview Images",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }

                    // 2. Save to Device (ZIP / Image)
                    OutlinedButton(
                        onClick = { saveMainToDevice() },
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
                            text = if (isSaving) "Saving..." else if (result.isSingleImage) "Save to Device" else "Save ZIP to Device",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                    }

                    // 3. Share
                    FilledTonalButton(
                        onClick = { shareMainOutput() },
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
