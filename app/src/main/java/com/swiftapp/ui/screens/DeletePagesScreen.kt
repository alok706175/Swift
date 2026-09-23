package com.swiftapp.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.swiftapp.data.model.DeletePageItem
import com.swiftapp.data.model.DeletePagesResult
import com.swiftapp.data.model.DeletePagesUiState
import com.swiftapp.ui.components.*
import com.swiftapp.ui.viewmodel.DeletePagesViewModel
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletePagesScreen(
    initialPdfFile: File? = null,
    onNavigateBack: () -> Unit,
    onOpenPdf: (File) -> Unit,
    viewModel: DeletePagesViewModel = viewModel()
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val selectedFile by viewModel.selectedFile.collectAsState()
    val pages by viewModel.pages.collectAsState()
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

    var previewPageItem by remember { mutableStateOf<DeletePageItem?>(null) }
    var customOutputFileName by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is DeletePagesUiState.PasswordRequired) {
            passwordPromptFile = (uiState as DeletePagesUiState.PasswordRequired).file
            passwordInput = ""
            passwordError = null
            showPasswordDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Delete Pages",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        selectedFile?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedFile != null && uiState !is DeletePagesUiState.Success) {
                        IconButton(onClick = { filePickerLauncher.launch("application/pdf") }) {
                            Icon(Icons.Default.FileOpen, contentDescription = "Change PDF")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (selectedFile != null && pages.isNotEmpty() && uiState !is DeletePagesUiState.Success) {
                val selectedCount = pages.count { it.isSelectedForDeletion }
                val remainingCount = pages.size - selectedCount

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedCount == 0) "Tap pages to mark for deletion" else "Deleting $selectedCount of ${pages.size} pages",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$remainingCount pages will remain",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TactileButton(
                            onClick = {
                                if (selectedCount == 0) {
                                    Toast.makeText(context, "Select at least one page to delete", Toast.LENGTH_SHORT).show()
                                } else if (remainingCount == 0) {
                                    Toast.makeText(context, "Cannot delete all pages! Keep at least 1 page.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.deleteSelectedPages(context, customOutputFileName.takeIf { it.isNotBlank() })
                                }
                            },
                            enabled = selectedCount in 1 until pages.size && uiState !is DeletePagesUiState.Processing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedCount == 0) "Select Pages to Delete" else "Delete $selectedCount ${if (selectedCount == 1) "Page" else "Pages"}",
                                style = MaterialTheme.typography.titleSmall,
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
        ) {
            when (val state = uiState) {
                is DeletePagesUiState.Idle -> {
                    DeleteFileSelectionEmptyView(
                        onSelectFile = { filePickerLauncher.launch("application/pdf") }
                    )
                }

                is DeletePagesUiState.LoadingThumbnails -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Preparing document pages...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is DeletePagesUiState.Ready -> {
                    DeletePagesGridContent(
                        pages = pages,
                        onTogglePage = { viewModel.togglePageDeletion(it) },
                        onSelectAll = { viewModel.selectAll() },
                        onDeselectAll = { viewModel.deselectAll() },
                        onSelectOdd = { viewModel.selectOddPages() },
                        onSelectEven = { viewModel.selectEvenPages() },
                        onPreviewPage = { previewPageItem = it }
                    )
                }

                is DeletePagesUiState.Processing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { state.progress },
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(54.dp)
                                )
                                Text(
                                    text = "Deleting Selected Pages",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                is DeletePagesUiState.Success -> {
                    DeletePagesSuccessView(
                        result = state.result,
                        onOpen = { onOpenPdf(state.result.outputFile) },
                        onShare = { sharePdf(context, state.result.outputFile) },
                        onDone = { onNavigateBack() }
                    )
                }

                is DeletePagesUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Operation Failed",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                TactileButton(
                                    onClick = { viewModel.reset() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }

                is DeletePagesUiState.PasswordRequired -> {
                    // Dialog will be shown via state
                }
            }
        }
    }

    // Password Prompt Dialog
    if (showPasswordDialog && passwordPromptFile != null) {
        Dialog(onDismissRequest = {
            showPasswordDialog = false
            onNavigateBack()
        }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Password Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This PDF is encrypted. Enter password to remove pages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        isError = passwordError != null,
                        supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showPasswordDialog = false
                                onNavigateBack()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        TactileButton(
                            onClick = {
                                if (passwordInput.isNotBlank()) {
                                    showPasswordDialog = false
                                    viewModel.loadFile(context, passwordPromptFile, password = passwordInput)
                                } else {
                                    passwordError = "Please enter password"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Unlock")
                        }
                    }
                }
            }
        }
    }

    // Page Full Preview Dialog
    previewPageItem?.let { item ->
        Dialog(onDismissRequest = { previewPageItem = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page ${item.pageNumber} Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { previewPageItem = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    if (item.bitmap != null) {
                        Image(
                            bitmap = item.bitmap.asImageBitmap(),
                            contentDescription = "Page ${item.pageNumber}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    TactileButton(
                        onClick = {
                            viewModel.togglePageDeletion(item.pageIndex)
                            previewPageItem = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.isSelectedForDeletion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isSelectedForDeletion) Icons.Default.RestoreFromTrash else Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (item.isSelectedForDeletion) "Keep this Page (Undo Delete)" else "Mark Page for Deletion")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteFileSelectionEmptyView(
    onSelectFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(90.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Remove Unwanted Pages",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select any PDF file, pick the pages you don't need, and create a clean PDF document instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        TactileButton(
            onClick = onSelectFile,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Select PDF Document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DeletePagesGridContent(
    pages: List<DeletePageItem>,
    onTogglePage: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSelectOdd: () -> Unit,
    onSelectEven: () -> Unit,
    onPreviewPage: (DeletePageItem) -> Unit
) {
    val selectedCount = pages.count { it.isSelectedForDeletion }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Selection Action Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onSelectAll,
                label = { Text("Select All", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(8.dp)
            )
            AssistChip(
                onClick = onDeselectAll,
                label = { Text("Clear", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(8.dp)
            )
            AssistChip(
                onClick = onSelectOdd,
                label = { Text("Odd", style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(8.dp)
            )
            AssistChip(
                onClick = onSelectEven,
                label = { Text("Even", style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(8.dp)
            )
        }

        // Instructions and counts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total Pages: ${pages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Marked to Delete: $selectedCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Grid of pages
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(pages, key = { it.pageIndex }) { page ->
                DeletePageCard(
                    page = page,
                    onToggle = { onTogglePage(page.pageIndex) },
                    onPreview = { onPreviewPage(page) }
                )
            }
        }
    }
}

@Composable
private fun DeletePageCard(
    page: DeletePageItem,
    onToggle: () -> Unit,
    onPreview: () -> Unit
) {
    val isDeleted = page.isSelectedForDeletion

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDeleted) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isDeleted) {
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDeleted) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail Area with Badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                if (page.bitmap != null) {
                    Image(
                        bitmap = page.bitmap.asImageBitmap(),
                        contentDescription = "Page ${page.pageNumber}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Deletion Red Overlay
                if (isDeleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Marked for Deletion",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Preview Button (Top Right)
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clickable { onPreview() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Preview Page",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer info with Page Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page ${page.pageNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDeleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                Checkbox(
                    checked = isDeleted,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.error,
                        checkmarkColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun DeletePagesSuccessView(
    result: DeletePagesResult,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    val df = DecimalFormat("#.##")
    val origSizeMb = df.format(result.originalSizeBytes / (1024.0 * 1024.0))
    val outSizeMb = df.format(result.outputSizeBytes / (1024.0 * 1024.0))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFE8F5E9),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Pages Removed Successfully!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${result.deletedPageCount} pages deleted. ${result.remainingPageCount} pages saved in new document.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("File Name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(result.outputFile.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${result.initialPageCount} → ${result.remainingPageCount} pages", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("File Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$origSizeMb MB → $outSizeMb MB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }

            TactileButton(
                onClick = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open PDF")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = onDone) {
            Text("Done / Back to Home")
        }
    }
}

private fun sharePdf(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Modified PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
