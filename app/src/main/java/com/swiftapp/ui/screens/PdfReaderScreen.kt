package com.swiftapp.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftapp.data.UnlockPdfService
import com.swiftapp.data.model.EncryptionStatus
import com.swiftapp.ui.components.*
import com.swiftapp.ui.theme.SwiftCoral
import com.swiftapp.utils.HapticManager
import com.swiftapp.utils.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

enum class ExportRangeType {
    ALL_PAGES,
    CURRENT_PAGE,
    CUSTOM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    file: File,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeFile by remember { mutableStateOf(file) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Password Protection State
    var isPasswordProtected by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isUnlocking by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Zoom & Pan State
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // UI State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }

    // 3-Dot Menu & Action Dialog States
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSaveAsImagesDialog by remember { mutableStateOf(false) }
    var selectedImageFormat by remember { mutableStateOf("PNG") }
    var exportRangeType by remember { mutableStateOf(ExportRangeType.ALL_PAGES) }
    var customRangeInput by remember { mutableStateOf("") }
    var isSavingImages by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var signatureText by remember { mutableStateOf("Jane Doe") }
    var appliedSignature by remember { mutableStateOf<String?>(null) }

    val pageCache = remember(activeFile.path) {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of available runtime memory
        object : android.util.LruCache<Int, Bitmap>(cacheSize) {
            override fun sizeOf(key: Int, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }
    val renderLock = remember(activeFile.path) { Any() }

    // Load PDF Asynchronously
    LaunchedEffect(activeFile.path) {
        isLoading = true
        errorMessage = null
        withContext(Dispatchers.IO) {
            try {
                // Check if the file is encrypted with a user password
                val inspect = UnlockPdfService.inspectPdfFile(context, activeFile)
                if (inspect.encryptionStatus == EncryptionStatus.PASSWORD_PROTECTED) {
                    withContext(Dispatchers.Main) {
                        isPasswordProtected = true
                        isLoading = false
                    }
                    return@withContext
                }

                var pfd: ParcelFileDescriptor? = null
                // Attempt 1: Open direct file descriptor
                try {
                    if (activeFile.exists() && activeFile.canRead()) {
                        pfd = ParcelFileDescriptor.open(activeFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                } catch (_: Exception) {}

                // Attempt 2: If direct open failed or restricted, make a local cache copy
                if (pfd == null) {
                    try {
                        val temp = File(context.cacheDir, "reader_cache_${System.currentTimeMillis()}.pdf")
                        FileInputStream(activeFile).use { input ->
                            FileOutputStream(temp).use { output ->
                                input.copyTo(output)
                            }
                        }
                        pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                    } catch (_: Exception) {}
                }

                if (pfd == null) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Cannot access PDF file descriptor."
                        isLoading = false
                    }
                    return@withContext
                }

                val renderer = PdfRenderer(pfd)
                withContext(Dispatchers.Main) {
                    fileDescriptor = pfd
                    pdfRenderer = renderer
                    pageCount = renderer.pageCount
                    isPasswordProtected = false
                    isLoading = false
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    isPasswordProtected = true
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage ?: "Failed to open PDF document."
                    isLoading = false
                }
            }
        }
    }

    // Cleanup resources
    DisposableEffect(activeFile.path) {
        onDispose {
            synchronized(renderLock) {
                try {
                    pdfRenderer?.close()
                } catch (_: Exception) {}
                try {
                    fileDescriptor?.close()
                } catch (_: Exception) {}
                pageCache.evictAll()
            }
        }
    }

    // Perform Search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            isSearching = true
            searchResults = PdfHelper.searchTextInPdf(file, searchQuery)
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search text...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            singleLine = true,
                            trailingIcon = {
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                        )
                    } else {
                        Column {
                            Text(
                                text = activeFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pageCount > 0) {
                                Text(
                                    text = "$pageCount ${if (pageCount == 1) "Page" else "Pages"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    TactileIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                },
                actions = {
                    TactileIconButton(
                        onClick = { isNightMode = !isNightMode },
                        icon = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Night Mode"
                    )
                    TactileIconButton(
                        onClick = { 
                            isSearchActive = !isSearchActive 
                            if (!isSearchActive) searchQuery = ""
                        },
                        icon = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search Text"
                    )

                    // 3 Vertical Dots Menu (Right of Search Icon)
                    Box {
                        TactileIconButton(
                            onClick = { showMenu = true },
                            icon = Icons.Default.MoreVert,
                            contentDescription = "More Options"
                        )

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename", fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit", fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Draw,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showSignatureDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save as Images", fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showSaveAsImagesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share", fontWeight = FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    sharePdfFile(context, activeFile.path)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                // Loading Spinner
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Opening PDF...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isPasswordProtected) {
                // Standard Password Prompt Dialog
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    try {
                        focusRequester.requestFocus()
                    } catch (_: Exception) {}
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Enter Password",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "This file is protected. Enter password to open.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            OutlinedTextField(
                                value = enteredPassword,
                                onValueChange = {
                                    enteredPassword = it
                                    passwordError = null
                                },
                                label = { Text("Password") },
                                singleLine = true,
                                isError = passwordError != null,
                                supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (enteredPassword.isNotBlank() && !isUnlocking) {
                                        scope.launch {
                                            isUnlocking = true
                                            passwordError = null
                                            val result = UnlockPdfService.unlockAndStripSecurity(context, file, enteredPassword) {}
                                            result.fold(
                                                onSuccess = { unlockedFile ->
                                                    activeFile = unlockedFile
                                                    isPasswordProtected = false
                                                    isUnlocking = false
                                                },
                                                onFailure = {
                                                    isUnlocking = false
                                                    passwordError = "Incorrect password"
                                                }
                                            )
                                        }
                                    }
                                }),
                                trailingIcon = {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onBack,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Cancel")
                                }

                                TactileButton(
                                    onClick = {
                                        if (enteredPassword.isNotBlank()) {
                                            scope.launch {
                                                isUnlocking = true
                                                passwordError = null
                                                val result = UnlockPdfService.unlockAndStripSecurity(context, file, enteredPassword) {}
                                                result.fold(
                                                    onSuccess = { unlockedFile ->
                                                        activeFile = unlockedFile
                                                        isPasswordProtected = false
                                                        isUnlocking = false
                                                    },
                                                    onFailure = {
                                                        isUnlocking = false
                                                        passwordError = "Incorrect password"
                                                    }
                                                )
                                            }
                                        } else {
                                            passwordError = "Please enter password"
                                        }
                                    },
                                    isLoading = isUnlocking,
                                    enabled = enteredPassword.isNotBlank(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }
            } else if (errorMessage != null) {
                // Error State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Unable to Open PDF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = errorMessage ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onBack,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Results Bar
                    if (isSearchActive && searchResults.isNotEmpty()) {
                        ScrollableTabRow(
                            selectedTabIndex = -1,
                            edgePadding = 16.dp,
                            divider = {},
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            searchResults.forEach { pageIndex ->
                                AssistChip(
                                    onClick = { 
                                        scope.launch { listState.animateScrollToItem(pageIndex) }
                                    },
                                    label = { Text("Page ${pageIndex + 1}") },
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(if (isNightMode) Color.Black else Color(0xFFF4F5F9))
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { tapOffset ->
                                        if (scale > 1.15f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                            val centerX = size.width / 2f
                                            val centerY = size.height / 2f
                                            offset = Offset(
                                                x = (centerX - tapOffset.x) * (2.5f - 1f),
                                                y = (centerY - tapOffset.y) * (2.5f - 1f)
                                            )
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f

                                    val newOffsetX = if (newScale > 1f) {
                                        (offset.x + pan.x * newScale).coerceIn(-maxOffsetX, maxOffsetX)
                                    } else 0f

                                    val newOffsetY = if (newScale > 1f) {
                                        (offset.y + pan.y * newScale).coerceIn(-maxOffsetY, maxOffsetY)
                                    } else 0f

                                    scale = newScale
                                    offset = Offset(newOffsetX, newOffsetY)
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                }
                        ) {
                            LazyColumn(
                                state = listState,
                                userScrollEnabled = (scale <= 1.05f),
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                items(
                                    count = pageCount,
                                    key = { it },
                                    contentType = { "pdf_page" }
                                ) { index ->
                                    PdfPageItem(
                                        renderer = pdfRenderer,
                                        pageIndex = index,
                                        isNightMode = isNightMode,
                                        pageCache = pageCache,
                                        renderLock = renderLock,
                                        signatureStamp = if (index == (pageCount - 1)) appliedSignature else null,
                                    )
                                }
                            }
                        }
                        
                        // Bottom Controls Row: Page Indicator (Left) & Zoom Controller (Right)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Page Indicator
                            if (pageCount > 0) {
                                val currentPage by remember {
                                    derivedStateOf { listState.firstVisibleItemIndex + 1 }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(12.dp),
                                    shadowElevation = 4.dp,
                                ) {
                                    Text(
                                        text = "$currentPage / $pageCount",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(1.dp))
                            }

                            // Quick Zoom Pill Controller
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(16.dp),
                                shadowElevation = 4.dp,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    TactileIconButton(
                                        onClick = {
                                            val newScale = (scale - 0.4f).coerceIn(1f, 5f)
                                            scale = newScale
                                            if (newScale <= 1.05f) offset = Offset.Zero
                                        },
                                        enabled = scale > 1f,
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
                                    }

                                    Surface(
                                        onClick = {
                                            scale = 1f
                                            offset = Offset.Zero
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (scale > 1.05f) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        modifier = Modifier.bounceClick(),
                                    ) {
                                        Text(
                                            text = "${(scale * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (scale > 1.05f) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        )
                                    }

                                    TactileIconButton(
                                        onClick = {
                                            scale = (scale + 0.4f).coerceIn(1f, 5f)
                                        },
                                        enabled = scale < 5f,
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    // Rename PDF Dialog
    if (showRenameDialog) {
        var newNameText by remember { mutableStateOf(activeFile.nameWithoutExtension) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new file name:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = newNameText,
                        onValueChange = { newNameText = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newNameText.trim()
                        if (clean.isNotEmpty()) {
                            val newFileName = if (clean.endsWith(".pdf", ignoreCase = true)) clean else "$clean.pdf"
                            val newTarget = File(activeFile.parentFile, newFileName)
                            if (activeFile.renameTo(newTarget)) {
                                activeFile = newTarget
                                Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showRenameDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete PDF Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Delete PDF?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${activeFile.name}\"? This action cannot be undone.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        if (activeFile.delete()) {
                            Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show()
                        }
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Save as Images Dialog (JPG / JPEG / PNG)
    if (showSaveAsImagesDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingImages) showSaveAsImagesDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = SwiftCoral,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Save as Images", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Choose image format and export range:", style = MaterialTheme.typography.bodyMedium)

                    // Format Selector: JPG, JPEG, PNG
                    Text("Image Format", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = SwiftCoral)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("PNG", "JPG", "JPEG").forEach { format ->
                            val isSelected = selectedImageFormat == format
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedImageFormat = format },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) SwiftCoral else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                ),
                                color = if (isSelected) SwiftCoral.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = format,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) SwiftCoral else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    // Page Range Selector
                    Text("Export Range", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = SwiftCoral)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 1. All Pages
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { exportRangeType = ExportRangeType.ALL_PAGES },
                            shape = RoundedCornerShape(10.dp),
                            color = if (exportRangeType == ExportRangeType.ALL_PAGES) SwiftCoral.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("All Pages ($pageCount ${if (pageCount == 1) "Page" else "Pages"})", style = MaterialTheme.typography.bodyMedium)
                                RadioButton(
                                    selected = exportRangeType == ExportRangeType.ALL_PAGES,
                                    onClick = { exportRangeType = ExportRangeType.ALL_PAGES },
                                    colors = RadioButtonDefaults.colors(selectedColor = SwiftCoral)
                                )
                            }
                        }

                        // 2. Current Page Only
                        val currentPage = (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount.coerceAtLeast(1))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { exportRangeType = ExportRangeType.CURRENT_PAGE },
                            shape = RoundedCornerShape(10.dp),
                            color = if (exportRangeType == ExportRangeType.CURRENT_PAGE) SwiftCoral.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Current Page Only (Page $currentPage)", style = MaterialTheme.typography.bodyMedium)
                                RadioButton(
                                    selected = exportRangeType == ExportRangeType.CURRENT_PAGE,
                                    onClick = { exportRangeType = ExportRangeType.CURRENT_PAGE },
                                    colors = RadioButtonDefaults.colors(selectedColor = SwiftCoral)
                                )
                            }
                        }

                        // 3. Custom Range
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { exportRangeType = ExportRangeType.CUSTOM },
                            shape = RoundedCornerShape(10.dp),
                            color = if (exportRangeType == ExportRangeType.CUSTOM) SwiftCoral.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Custom Range (e.g. 1-3, 5)", style = MaterialTheme.typography.bodyMedium)
                                    RadioButton(
                                        selected = exportRangeType == ExportRangeType.CUSTOM,
                                        onClick = { exportRangeType = ExportRangeType.CUSTOM },
                                        colors = RadioButtonDefaults.colors(selectedColor = SwiftCoral)
                                    )
                                }

                                if (exportRangeType == ExportRangeType.CUSTOM) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = customRangeInput,
                                        onValueChange = { customRangeInput = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("e.g. 1-3, 5 (Max $pageCount)", style = MaterialTheme.typography.bodySmall) },
                                        label = { Text("Pages (e.g. 1-3, 5)", style = MaterialTheme.typography.bodySmall) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = SwiftCoral,
                                            focusedLabelColor = SwiftCoral,
                                            cursorColor = SwiftCoral
                                        ),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done)
                                    )
                                    val parsedCount = remember(customRangeInput, pageCount) {
                                        parseCustomPages(customRangeInput, pageCount).size
                                    }
                                    if (customRangeInput.isNotBlank()) {
                                        Text(
                                            text = if (parsedCount > 0) "$parsedCount page(s) will be exported" else "No valid pages in 1-$pageCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (parsedCount > 0) SwiftCoral else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isSavingImages) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SwiftCoral)
                            Text("Exporting and saving images...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isSavingImages) {
                            isSavingImages = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    var pfd: ParcelFileDescriptor? = null
                                    try {
                                        pfd = ParcelFileDescriptor.open(activeFile, ParcelFileDescriptor.MODE_READ_ONLY)
                                    } catch (_: Exception) {}

                                    if (pfd == null) {
                                        val temp = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")
                                        FileInputStream(activeFile).use { input ->
                                            FileOutputStream(temp).use { output -> input.copyTo(output) }
                                        }
                                        pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                                    }

                                    val nonNullPfd = pfd ?: throw IllegalStateException("Could not open file descriptor")
                                    val renderer = PdfRenderer(nonNullPfd)
                                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                    val outputDir = File(picturesDir, "SwiftPDF").apply { if (!exists()) mkdirs() }

                                    val baseName = activeFile.nameWithoutExtension
                                    val timestamp = System.currentTimeMillis()
                                    val ext = if (selectedImageFormat == "PNG") "png" else if (selectedImageFormat == "JPEG") "jpeg" else "jpg"
                                    val compressFormat = if (selectedImageFormat == "PNG") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

                                    val pagesToExport = when (exportRangeType) {
                                        ExportRangeType.ALL_PAGES -> (0 until renderer.pageCount).toList()
                                        ExportRangeType.CURRENT_PAGE -> listOf(listState.firstVisibleItemIndex.coerceIn(0, (renderer.pageCount - 1).coerceAtLeast(0)))
                                        ExportRangeType.CUSTOM -> {
                                            val custom = parseCustomPages(customRangeInput, renderer.pageCount)
                                            if (custom.isEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    isSavingImages = false
                                                    HapticManager.error()
                                                    Toast.makeText(context, "Please enter valid page numbers between 1 and ${renderer.pageCount}", Toast.LENGTH_LONG).show()
                                                }
                                                renderer.close()
                                                nonNullPfd.close()
                                                return@launch
                                            }
                                            custom
                                        }
                                    }
                                    val savedFiles = mutableListOf<String>()

                                    for (pageIdx in pagesToExport) {
                                        val page = renderer.openPage(pageIdx)
                                        val scaleFactor = 2
                                        val bitmap = Bitmap.createBitmap(page.width * scaleFactor, page.height * scaleFactor, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bitmap)
                                        canvas.drawColor(android.graphics.Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                        val outFile = File(outputDir, "${baseName}_page_${pageIdx + 1}_$timestamp.$ext")
                                        FileOutputStream(outFile).use { out ->
                                            bitmap.compress(compressFormat, 95, out)
                                        }
                                        savedFiles.add(outFile.absolutePath)
                                        page.close()
                                        bitmap.recycle()
                                    }

                                    renderer.close()
                                    nonNullPfd.close()

                                    MediaScannerConnection.scanFile(
                                        context,
                                        savedFiles.toTypedArray(),
                                        arrayOf(if (selectedImageFormat == "PNG") "image/png" else "image/jpeg"),
                                        null
                                    )

                                    withContext(Dispatchers.Main) {
                                        isSavingImages = false
                                        showSaveAsImagesDialog = false
                                        HapticManager.success()
                                        Toast.makeText(context, "Saved ${savedFiles.size} image(s) to Pictures/SwiftPDF", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSavingImages = false
                                        HapticManager.error()
                                        Toast.makeText(context, "Failed to export images: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSavingImages,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SwiftCoral)
                ) {
                    Text("Save Images")
                }
            },
            dismissButton = {
                if (!isSavingImages) {
                    TextButton(onClick = { showSaveAsImagesDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Quick Sign / Edit Dialog
    if (showSignatureDialog) {
        AlertDialog(
            onDismissRequest = { showSignatureDialog = false },
            title = { Text("E-Signature Stamp", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Type signature text to place at the end of the document:")
                    OutlinedTextField(
                        value = signatureText,
                        onValueChange = { signatureText = it },
                        label = { Text("Full Name / Initials") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appliedSignature = signatureText
                        showSignatureDialog = false
                        Toast.makeText(context, "Signature applied to document", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply Signature")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignatureDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun sharePdfFile(context: android.content.Context, filePath: String) {
    try {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF via..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AnnotationIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(38.dp)
            .bounceClick(scaleDownFactor = 0.90f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun PdfPageItem(
    renderer: PdfRenderer?,
    pageIndex: Int,
    isNightMode: Boolean,
    pageCache: android.util.LruCache<Int, Bitmap>?,
    renderLock: Any?,
    signatureStamp: String? = null,
) {
    val context = LocalContext.current
    val cachedBitmap = remember(pageIndex) { pageCache?.get(pageIndex) }
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(cachedBitmap) }
    var renderError by remember(pageIndex) { mutableStateOf(false) }
    var aspectRatio by remember(pageIndex) {
        mutableFloatStateOf(
            if (cachedBitmap != null && cachedBitmap.height > 0) {
                cachedBitmap.width.toFloat() / cachedBitmap.height.toFloat()
            } else 0.707f
        )
    }
    
    val nightModeMatrix = remember {
        ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    LaunchedEffect(renderer, pageIndex) {
        val cached = pageCache?.get(pageIndex)
        if (cached != null) {
            bitmap = cached
            if (cached.height > 0) {
                aspectRatio = cached.width.toFloat() / cached.height.toFloat()
            }
            return@LaunchedEffect
        }
        if (renderer != null) {
            withContext(Dispatchers.IO) {
                try {
                    val displayMetrics = context.resources.displayMetrics
                    val screenWidthPx = displayMetrics.widthPixels
                    var calculatedRatio = 0.707f

                    val bmp = synchronized(renderLock ?: renderer) {
                        val page = renderer.openPage(pageIndex)
                        try {
                            calculatedRatio = page.width.toFloat() / page.height.toFloat()
                            // Adaptive target resolution scaled to device screen
                            val scaleMultiplier = (screenWidthPx.toFloat() / page.width.toFloat()).coerceIn(1.0f, 2.2f)
                            val targetWidth = (page.width * scaleMultiplier).toInt().coerceIn(360, 1440)
                            val targetHeight = (page.height * scaleMultiplier).toInt().coerceIn(480, 2048)

                            val b = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            b.eraseColor(android.graphics.Color.WHITE)
                            page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            pageCache?.put(pageIndex, b)
                            b
                        } finally {
                            page.close()
                        }
                    }
                    if (bmp != null) {
                        bitmap = bmp
                        aspectRatio = calculatedRatio
                    }
                } catch (e: Exception) {
                    renderError = true
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = if (isNightMode) Color.DarkGray else Color.White),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bitmap != null) {
                val colorFilter = if (isNightMode) ColorFilter.colorMatrix(nightModeMatrix) else null

                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    colorFilter = colorFilter,
                )
            } else if (renderError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .background(if (isNightMode) Color.DarkGray else Color(0xFFEEEEEE)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Unable to render page ${pageIndex + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .background(if (isNightMode) Color.Black else Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            // E-Signature Stamp Overlay
            if (signatureStamp != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 32.dp, end = 24.dp),
                    color = Color.Transparent,
                ) {
                    Text(
                        text = signatureStamp,
                        fontSize = 32.sp,
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Cursive,
                        color = Color(0xFF003366),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF003366), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun parseCustomPages(input: String, totalPages: Int): List<Int> {
    if (input.isBlank()) return emptyList()
    val pages = mutableSetOf<Int>()
    val tokens = input.split(",", ";", " ", "\n")
    for (token in tokens) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) continue
        if (trimmed.contains("-")) {
            val parts = trimmed.split("-")
            if (parts.size == 2) {
                val start = parts[0].trim().toIntOrNull()
                val end = parts[1].trim().toIntOrNull()
                if (start != null && end != null) {
                    val min = minOf(start, end).coerceAtLeast(1)
                    val max = maxOf(start, end).coerceAtMost(totalPages)
                    for (p in min..max) {
                        pages.add(p - 1)
                    }
                }
            }
        } else {
            val page = trimmed.toIntOrNull()
            if (page != null && page in 1..totalPages) {
                pages.add(page - 1)
            }
        }
    }
    return pages.sorted()
}

