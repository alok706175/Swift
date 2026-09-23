package com.swiftapp.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swiftapp.data.model.PageRange
import com.swiftapp.data.model.SplitMode
import com.swiftapp.data.model.SplitPageThumbnailItem
import com.swiftapp.data.model.SplitPdfConfig
import com.swiftapp.data.model.SplitPdfResult
import com.swiftapp.data.model.SplitUiState
import com.swiftapp.ui.components.*
import com.swiftapp.ui.viewmodel.SplitPdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    initialPdfFile: File? = null,
    onNavigateBack: () -> Unit,
    onOpenPdf: (File) -> Unit,
    viewModel: SplitPdfViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedFile by viewModel.selectedFile.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val thumbnails by viewModel.thumbnails.collectAsState()
    val config by viewModel.config.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFile(context, null, it) }
    }

    LaunchedEffect(initialPdfFile) {
        if (initialPdfFile != null) {
            viewModel.loadFile(context, initialPdfFile)
        }
    }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordPromptFile by remember { mutableStateOf<File?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is SplitUiState.PasswordRequired) {
            val req = uiState as SplitUiState.PasswordRequired
            passwordPromptFile = req.file
            passwordError = req.message
            showPasswordDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Split PDF",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (selectedFile != null) {
                            Text(
                                text = "${selectedFile?.name} • $totalPages pages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    if (selectedFile != null) {
                        TactileButton(
                            onClick = { viewModel.executeSplit() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Split",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (selectedFile == null) {
                // Empty State
                SplitEmptyUploadView(
                    onSelectFile = { filePickerLauncher.launch("application/pdf") }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mode Selector Segmented Tabs
                    SplitModeSegmentedTabs(
                        currentMode = config.mode,
                        onModeSelected = { viewModel.setSplitMode(it) }
                    )

                    // Main Content: Config Header + Thumbnails Grid
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Mode Configuration Card
                        item {
                            when (config.mode) {
                                SplitMode.CUSTOM_RANGES -> {
                                    CustomRangesConfigView(
                                        totalPages = totalPages,
                                        config = config,
                                        onAddRange = { viewModel.addRange() },
                                        onRemoveRange = { viewModel.removeRange(it) },
                                        onUpdateRange = { id, from, to -> viewModel.updateRange(id, from, to) },
                                        onToggleMerge = { viewModel.setMergeRangesIntoSingleFile(it) }
                                    )
                                }
                                SplitMode.EXTRACT_PAGES -> {
                                    ExtractPagesConfigView(
                                        selectedCount = viewModel.selectedPagesCount,
                                        totalPages = totalPages,
                                        rangeText = config.rangeTextInput,
                                        extractIntoSingleFile = config.extractIntoSingleFile,
                                        onSelectAll = { viewModel.selectAllPages() },
                                        onDeselectAll = { viewModel.deselectAllPages() },
                                        onSelectOdd = { viewModel.selectOddPages() },
                                        onSelectEven = { viewModel.selectEvenPages() },
                                        onRangeTextChanged = { viewModel.setRangeTextInput(it) },
                                        onToggleSingleFile = { viewModel.setExtractIntoSingleFile(it) }
                                    )
                                }
                                SplitMode.BURST_ALL -> {
                                    BurstAllConfigView(totalPages = totalPages)
                                }
                            }
                        }

                        // Thumbnail Grid Section Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Document Pages ($totalPages)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                if (config.mode == SplitMode.EXTRACT_PAGES) {
                                    Text(
                                        text = "${viewModel.selectedPagesCount} selected",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Thumbnails Grid
                        item {
                            val chunked = thumbnails.chunked(2)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                chunked.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                SplitPageThumbnailCard(
                                                    item = item,
                                                    isExtractMode = config.mode == SplitMode.EXTRACT_PAGES,
                                                    onToggleSelect = { viewModel.togglePageSelection(item.pageIndex) }
                                                )
                                            }
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Processing Overlay
            if (uiState is SplitUiState.Loading) {
                val loading = uiState as SplitUiState.Loading
                SplitProcessingOverlay(message = loading.message)
            }

            // Password Prompt Dialog
            if (showPasswordDialog && passwordPromptFile != null) {
                AlertDialog(
                    onDismissRequest = {
                        showPasswordDialog = false
                        viewModel.resetState()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = { Text("Password Protected PDF", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                text = "Enter password to unlock and split this document.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    passwordError = null
                                },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                isError = passwordError != null,
                                supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (passwordInput.isNotBlank()) {
                                    showPasswordDialog = false
                                    viewModel.loadFile(context, passwordPromptFile, password = passwordInput)
                                }
                            }
                        ) {
                            Text("Unlock")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showPasswordDialog = false
                                viewModel.resetState()
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Success Dialog
            if (uiState is SplitUiState.Success) {
                val successResult = (uiState as SplitUiState.Success).result
                SplitSuccessDialog(
                    result = successResult,
                    onOpenPdf = { file ->
                        viewModel.resetState()
                        onOpenPdf(file)
                    },
                    onSaveToDevice = { file ->
                        scope.launch {
                            val saveResult = viewModel.saveToDownloads(file)
                            saveResult.fold(
                                onSuccess = {
                                    Toast.makeText(context, "Saved to Downloads/SwiftPDF", Toast.LENGTH_LONG).show()
                                },
                                onFailure = {
                                    Toast.makeText(context, "Failed to save: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    },
                    onShare = { file ->
                        shareSplitOutput(context, file)
                    },
                    onDismiss = {
                        viewModel.resetState()
                        onNavigateBack()
                    }
                )
            }

            // Error Dialog
            if (uiState is SplitUiState.Error) {
                val errorMsg = (uiState as SplitUiState.Error).message
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Error") },
                    text = { Text(errorMsg) },
                    confirmButton = {
                        Button(onClick = { viewModel.resetState() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

/**
 * Segmented Buttons for Mode Switching (Custom Ranges, Extract Pages, Split All).
 */
@Composable
fun SplitModeSegmentedTabs(
    currentMode: SplitMode,
    onModeSelected: (SplitMode) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SplitMode.entries.forEach { mode ->
                val isSelected = currentMode == mode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .bounceClick()
                        .clickable { onModeSelected(mode) }
                ) {
                    Text(
                        text = mode.title,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Mode A: Custom Ranges Config Builder.
 */
@Composable
fun CustomRangesConfigView(
    totalPages: Int,
    config: SplitPdfConfig,
    onAddRange: () -> Unit,
    onRemoveRange: (String) -> Unit,
    onUpdateRange: (String, Int, Int) -> Unit,
    onToggleMerge: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Define Page Ranges",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Specify segments of pages to split into files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Range Items
            config.ranges.forEachIndexed { index, range ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Part ${index + 1}:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.width(52.dp)
                    )

                    // From Input
                    OutlinedTextField(
                        value = "${range.fromPage}",
                        onValueChange = {
                            val num = it.toIntOrNull() ?: 1
                            onUpdateRange(range.id, num, range.toPage)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        prefix = { Text("p. ") },
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text("to", style = MaterialTheme.typography.bodySmall)

                    // To Input
                    OutlinedTextField(
                        value = "${range.toPage}",
                        onValueChange = {
                            val num = it.toIntOrNull() ?: 1
                            onUpdateRange(range.id, range.fromPage, num)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        prefix = { Text("p. ") },
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Delete Button
                    if (config.ranges.size > 1) {
                        IconButton(
                            onClick = { onRemoveRange(range.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove Range",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Add Range Button
            OutlinedButton(
                onClick = onAddRange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Another Range", fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Merge into single file toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleMerge(!config.mergeRangesIntoSingleFile) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = config.mergeRangesIntoSingleFile,
                    onCheckedChange = { onToggleMerge(it) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Merge all ranges into one PDF",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = if (config.mergeRangesIntoSingleFile) "Exports 1 consolidated PDF" else "Exports separate PDF files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Mode B: Extract Pages Config View.
 */
@Composable
fun ExtractPagesConfigView(
    selectedCount: Int,
    totalPages: Int,
    rangeText: String,
    extractIntoSingleFile: Boolean,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSelectOdd: () -> Unit,
    onSelectEven: () -> Unit,
    onRangeTextChanged: (String) -> Unit,
    onToggleSingleFile: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Extract Selected Pages",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Select pages on the grid below or type page numbers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Selection Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SuggestionChip(onClick = onSelectAll, label = { Text("All", fontSize = 11.sp) })
                SuggestionChip(onClick = onSelectOdd, label = { Text("Odd", fontSize = 11.sp) })
                SuggestionChip(onClick = onSelectEven, label = { Text("Even", fontSize = 11.sp) })
                SuggestionChip(onClick = onDeselectAll, label = { Text("Clear", fontSize = 11.sp) })
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Free-text Range Input
            OutlinedTextField(
                value = rangeText,
                onValueChange = onRangeTextChanged,
                label = { Text("Page selection (e.g. 1-3, 5, 8-$totalPages)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Single File vs Separate Files
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSingleFile(!extractIntoSingleFile) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = extractIntoSingleFile,
                    onCheckedChange = { onToggleSingleFile(it) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Combine into one PDF document",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = if (extractIntoSingleFile) "Exports 1 single PDF with $selectedCount pages" else "Exports $selectedCount separate single-page PDF files",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Mode C: Burst / Split All Pages Config View.
 */
@Composable
fun BurstAllConfigView(totalPages: Int) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Burst into Individual Pages",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Will split every page of this document into $totalPages separate 1-page PDF files bundled inside a clean ZIP archive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Individual Page Thumbnail Card.
 */
@Composable
fun SplitPageThumbnailCard(
    item: SplitPageThumbnailItem,
    isExtractMode: Boolean,
    onToggleSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isExtractMode && item.isSelected) 2.dp else 1.dp,
                color = if (isExtractMode && item.isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExtractMode && item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Checkbox (in extract mode) + Page Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page ${item.pageNumber}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (isExtractMode) {
                    Checkbox(
                        checked = item.isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Center Preview Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                if (item.thumbnailBitmap != null) {
                    Image(
                        bitmap = item.thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Page ${item.pageNumber}",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Processing / Loading overlay.
 */
@Composable
fun SplitProcessingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Empty Upload View.
 */
@Composable
fun SplitEmptyUploadView(onSelectFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.CallSplit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Split PDF Document",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Separate PDF pages by custom ranges, extract specific pages, or burst all pages into standalone files.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        TactileButton(
            onClick = onSelectFile,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select PDF Document", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

/**
 * Standardized Completion Dialog matching the Swift PDF design system:
 * 1. Top-right close 'X' button
 * 2. Summary badge (Name, generated file count, size)
 * 3. Vertically stacked 3 action buttons:
 *    - Open PDF / Open ZIP (Filled button, no left icon)
 *    - Save to Device (Outlined button with download icon)
 *    - Share (Tonal button with share icon)
 */
@Composable
fun SplitSuccessDialog(
    result: SplitPdfResult,
    onOpenPdf: (File) -> Unit,
    onSaveToDevice: (File) -> Unit,
    onShare: (File) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top-right close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Green Success Checkmark
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "PDF Split Successfully!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (result.isSingleFile) "Generated 1 consolidated PDF document."
                    else "Generated ${result.totalGeneratedFiles} PDF files archived into a ZIP bundle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Summary Badge Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Output File", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = result.primaryOutputFile.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Generated Files", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${result.totalGeneratedFiles} files", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatSplitFileSize(result.fileSize), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3 Vertically Stacked Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Open PDF / Open ZIP (Filled button, no left icon)
                    Button(
                        onClick = { onOpenPdf(result.primaryOutputFile) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (result.isSingleFile) "Open PDF" else "Open ZIP",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }

                    // 2. Save to Device
                    OutlinedButton(
                        onClick = { onSaveToDevice(result.primaryOutputFile) },
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
                            text = if (result.isSingleFile) "Save to Device" else "Save ZIP to Device",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                    }

                    // 3. Share
                    FilledTonalButton(
                        onClick = { onShare(result.primaryOutputFile) },
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

private fun shareSplitOutput(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = if (file.name.endsWith(".zip", ignoreCase = true)) "application/zip" else "application/pdf"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Split File", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Split File"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun formatSplitFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}
