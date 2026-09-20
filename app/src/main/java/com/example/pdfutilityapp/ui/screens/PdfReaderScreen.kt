package com.example.pdfutilityapp.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdfutilityapp.utils.PdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    file: File,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    
    // UI State
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isNightMode by remember { mutableStateOf(false) }

    // Editor / Annotation Tools
    var activeTool by remember { mutableStateOf("none") }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var signatureText by remember { mutableStateOf("Jane Doe") }
    var appliedSignature by remember { mutableStateOf<String?>(null) }

    // Load PDF
    DisposableEffect(file) {
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)
        pdfRenderer = renderer
        pageCount = renderer.pageCount
        
        onDispose {
            renderer.close()
            fileDescriptor.close()
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
                        Text(
                            text = "Editor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isNightMode = !isNightMode }) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Night Mode",
                        )
                    }
                    IconButton(onClick = { 
                        isSearchActive = !isSearchActive 
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(if (isSearchActive) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search Text")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                        .background(if (isNightMode) Color.Black else Color(0xFFF4F5F9)),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        items(pageCount) { index ->
                            PdfPageItem(
                                renderer = pdfRenderer,
                                pageIndex = index,
                                isNightMode = isNightMode,
                                signatureStamp = if (index == (pageCount - 1)) appliedSignature else null,
                            )
                        }
                    }
                    
                    // Page Indicator
                    val currentPage by remember {
                        derivedStateOf { listState.firstVisibleItemIndex + 1 }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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
                }
            }

            // Floating Annotation Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnnotationIconButton(
                        icon = Icons.Outlined.Crop,
                        isSelected = activeTool == "crop",
                        onClick = { activeTool = if (activeTool == "crop") "none" else "crop" },
                    )
                    AnnotationIconButton(
                        icon = Icons.Outlined.Edit,
                        isSelected = activeTool == "pen",
                        onClick = { activeTool = if (activeTool == "pen") "none" else "pen" },
                    )
                    AnnotationIconButton(
                        icon = Icons.Outlined.Create,
                        isSelected = activeTool == "marker",
                        onClick = { activeTool = if (activeTool == "marker") "none" else "marker" },
                    )
                    AnnotationIconButton(
                        icon = Icons.Outlined.AutoFixNormal,
                        isSelected = activeTool == "eraser",
                        onClick = { activeTool = if (activeTool == "eraser") "none" else "eraser" },
                    )
                    AnnotationIconButton(
                        icon = Icons.Outlined.TextFields,
                        isSelected = activeTool == "text",
                        onClick = { activeTool = if (activeTool == "text") "none" else "text" },
                    )
                    AnnotationIconButton(
                        icon = Icons.Outlined.BorderColor,
                        isSelected = activeTool == "signature",
                        onClick = { 
                            activeTool = "signature"
                            showSignatureDialog = true 
                        },
                    )
                }
            }
        }
    }

    if (showSignatureDialog) {
        AlertDialog(
            onDismissRequest = { showSignatureDialog = false },
            title = { Text("Add E-Signature", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Type your name or signature label below:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = signatureText,
                        onValueChange = { signatureText = it },
                        label = { Text("Signature Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    
                    // Signature Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(Color(0xFFFFF0EE), RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = signatureText.ifEmpty { "Jane Doe" },
                            fontSize = 28.sp,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Cursive,
                            color = Color(0xFF003366),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appliedSignature = signatureText.ifEmpty { "Jane Doe" }
                        showSignatureDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Apply Signature")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignatureDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun AnnotationIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(38.dp),
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
    signatureStamp: String? = null,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val nightModeMatrix = remember {
        ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    LaunchedEffect(renderer, pageIndex) {
        if ((renderer != null) && (bitmap == null)) {
            withContext(Dispatchers.IO) {
                val page = renderer.openPage(pageIndex)
                val width = page.width * 2
                val height = page.height * 2
                val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap = b
                page.close()
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
            bitmap?.let {
                val colorFilter = if (isNightMode) ColorFilter.colorMatrix(nightModeMatrix) else null

                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    colorFilter = colorFilter,
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(if (isNightMode) Color.Black else Color.White),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
