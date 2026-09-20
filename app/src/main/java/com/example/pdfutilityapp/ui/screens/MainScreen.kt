package com.example.pdfutilityapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdfutilityapp.R
import com.example.pdfutilityapp.ui.viewmodel.PdfUiState
import com.example.pdfutilityapp.ui.viewmodel.PdfViewModel
import com.example.pdfutilityapp.ui.viewmodel.ThemeMode
import com.example.pdfutilityapp.ui.viewmodel.ThemeViewModel
import com.example.pdfutilityapp.utils.PdfFileItem
import com.example.pdfutilityapp.utils.PdfHelper
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * 3-Tab Bottom Navigation corresponding to the design mockup.
 */
enum class NavTab(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Default.GridView),
    Tools("Toolbox", Icons.Default.Layers),
    Settings("Settings", Icons.Default.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PdfViewModel = viewModel(
        factory = PdfViewModel.provideFactory(LocalContext.current),
    ),
    themeViewModel: ThemeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val allFiles by viewModel.allFiles.collectAsState()

    var selectedTab by remember { mutableStateOf(NavTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Reader State
    var readerFile by remember { mutableStateOf<File?>(null) }

    if (readerFile != null) {
        PdfReaderScreen(file = readerFile!!) { readerFile = null }
        return
    }

    // Load files
    LaunchedEffect(Unit) {
        viewModel.loadAllFiles(context)
    }

    // Search Debounce
    LaunchedEffect(searchQuery) {
        if (isSearchActive) {
            if (searchQuery.length >= 2) {
                delay(300.milliseconds)
                viewModel.searchDocuments(context, searchQuery)
            } else if (searchQuery.isEmpty()) {
                viewModel.searchDocuments(context, "")
            }
        }
    }

    // PDF Operation Launchers
    val mergePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.mergeFiles(uris)
    }

    val imageToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.convertImagesToPdf(uris)
    }

    val splitPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.splitFiles(uris)
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pdf?.let { pdf ->
                val file = context.cacheDir.resolve("scanned_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(pdf.uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                viewModel.addToRecent(context, file)
                readerFile = file
            }
        }
    }

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showRotateDialog by remember { mutableStateOf(false) }
    var showProtectDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showTextToPdfDialog by remember { mutableStateOf(false) }
    var showPageIndicesDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showSignDialog by remember { mutableStateOf(false) }

    val genericPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPdfUri = it
        }
    }

    var currentAction by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedPdfUri) {
        selectedPdfUri?.let { uri ->
            when (currentAction) {
                "rotate" -> showRotateDialog = true
                "protect" -> showProtectDialog = true
                "unlock" -> showUnlockDialog = true
                "watermark" -> showWatermarkDialog = true
                "compress" -> showCompressDialog = true
                "sign" -> showSignDialog = true
                "pdf_to_images" -> viewModel.pdfToImages(uri)
                "delete_pages" -> showPageIndicesDialog = true
                "extract_pages" -> showPageIndicesDialog = true
                "reorder_pages" -> showPageIndicesDialog = true
            }
            selectedPdfUri = null
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is PdfUiState.Success) {
            val successState = uiState as PdfUiState.Success
            val file = File(successState.filePath)
            viewModel.addToRecent(context, file)
            readerFile = file
            viewModel.loadAllFiles(context)
        }
    }

    fun startScanning() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setGalleryImportAllowed(true)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as android.app.Activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener {
                Toast.makeText(context, "Scanner unavailable: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    val coralContainer = MaterialTheme.colorScheme.primaryContainer
    val coralPrimary = MaterialTheme.colorScheme.primary

    val allTools = remember(coralContainer, coralPrimary) {
        listOf(
            UtilityToolItem(
                id = "merge_pdf",
                title = "Merge PDF",
                description = "Combine multiple PDFs into one.",
                icon = Icons.Outlined.CallMerge,
                onClick = { mergePdfLauncher.launch(arrayOf("application/pdf")) },
            ),
            UtilityToolItem(
                id = "compress_pdf",
                title = "Compress PDF",
                description = "Reduce document file size.",
                icon = Icons.Outlined.Compress,
                onClick = {
                    currentAction = "compress"
                    genericPdfLauncher.launch("application/pdf")
                },
            ),
            UtilityToolItem(
                id = "scan_pdf",
                title = "Scan to PDF",
                description = "Scan paper documents using camera.",
                icon = Icons.Outlined.DocumentScanner,
                onClick = { startScanning() },
            ),
            UtilityToolItem(
                id = "esign_pdf",
                title = "E-Sign PDF",
                description = "Add signature stamp to PDF.",
                icon = Icons.Outlined.BorderColor,
                onClick = {
                    currentAction = "sign"
                    genericPdfLauncher.launch("application/pdf")
                },
            ),
            UtilityToolItem(
                id = "protect_pdf",
                title = "Protect PDF",
                description = "Add password encryption.",
                icon = Icons.Outlined.Lock,
                onClick = {
                    currentAction = "protect"
                    genericPdfLauncher.launch("application/pdf")
                },
            ),
            UtilityToolItem(
                id = "unlock_pdf",
                title = "Unlock PDF",
                description = "Remove password lock.",
                icon = Icons.Outlined.LockOpen,
                onClick = {
                    currentAction = "unlock"
                    genericPdfLauncher.launch("application/pdf")
                },
            ),
            UtilityToolItem(
                id = "image_to_pdf",
                title = "Image to PDF",
                description = "Convert photos & images to PDF.",
                icon = Icons.Outlined.Collections,
                onClick = { imageToPdfLauncher.launch(arrayOf("image/*", "image/jpeg", "image/png")) },
            ),
            UtilityToolItem(
                id = "pdf_to_images",
                title = "PDF to Images",
                description = "Extract images from pages.",
                icon = Icons.Outlined.Image,
                onClick = {
                    currentAction = "pdf_to_images"
                    genericPdfLauncher.launch("application/pdf")
                },
            ),
            UtilityToolItem(
                id = "text_to_pdf",
                title = "Text to PDF",
                description = "Convert text into PDF pages.",
                icon = Icons.Outlined.TextFields,
                onClick = { showTextToPdfDialog = true },
            ),
            UtilityToolItem(
                id = "watermark_pdf",
                title = "Watermark",
                description = "Add custom text stamp.",
                icon = Icons.Outlined.TextFormat,
                onClick = {
                    currentAction = "watermark"
                    genericPdfLauncher.launch("application/pdf")
                },
            ),
            UtilityToolItem(
                id = "rotate_pdf",
                title = "Rotate PDF",
                description = "Change page orientation.",
                icon = Icons.Outlined.RotateRight,
                onClick = {
                    currentAction = "rotate"
                    genericPdfLauncher.launch("application/pdf")
                },
            ),
            UtilityToolItem(
                id = "split_pdf",
                title = "Split PDF",
                description = "Extract pages into separate PDFs.",
                icon = Icons.Outlined.CallSplit,
                onClick = { splitPdfLauncher.launch(arrayOf("application/pdf")) },
            ),
            UtilityToolItem(
                id = "demo_pdf",
                title = "Sample PDF",
                description = "Create a test PDF document.",
                icon = Icons.Outlined.PictureAsPdf,
                onClick = {
                    scope.launch {
                        val result = PdfHelper.createSamplePdf(context)
                        result.fold(
                            onSuccess = { file ->
                                Toast.makeText(context, "Sample PDF created", Toast.LENGTH_SHORT).show()
                                viewModel.addToRecent(context, file)
                                readerFile = file
                            },
                            onFailure = { error ->
                                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                },
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                NavTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontWeight = FontWeight.Bold) },
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            isSearchActive = false
                            searchQuery = ""
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                StatusCards(
                    uiState = uiState,
                    onDismiss = { viewModel.resetState() },
                    context = context,
                    onOpen = {
                        viewModel.addToRecent(context, it)
                        readerFile = it
                    },
                )
            }

            if (isSearchActive && searchQuery.isNotEmpty()) {
                SearchResultsContent(
                    results = searchResults,
                    context = context,
                    onOpen = {
                        viewModel.addToRecent(context, it)
                        readerFile = it
                    },
                )
            } else {
                when (selectedTab) {
                    NavTab.Home -> HomeDashboardContent(
                        allFiles = allFiles,
                        context = context,
                        onOpen = {
                            viewModel.addToRecent(context, it)
                            readerFile = it
                        },
                        onViewAllClick = { selectedTab = NavTab.Tools },
                    )
                    NavTab.Tools -> ToolboxContent(
                        allTools = allTools,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                    )
                    NavTab.Settings -> SettingsContent(themeViewModel = themeViewModel)
                }
            }
        }
    }

    if (uiState is PdfUiState.Loading) {
        ProcessingDialog(loadingState = uiState as PdfUiState.Loading)
    }

    if (showRotateDialog && (selectedPdfUri != null)) {
        RotateDialog(
            onRotate = { rotation ->
                viewModel.rotatePdf(selectedPdfUri!!, rotation)
                showRotateDialog = false
            },
            onDismiss = { showRotateDialog = false },
        )
    }

    if (showProtectDialog && (selectedPdfUri != null)) {
        PasswordDialog(
            title = "Protect PDF",
            onConfirm = { password ->
                viewModel.protectPdf(selectedPdfUri!!, password)
                showProtectDialog = false
            },
            onDismiss = { showProtectDialog = false },
        )
    }

    if (showUnlockDialog && (selectedPdfUri != null)) {
        PasswordDialog(
            title = "Unlock PDF",
            onConfirm = { password ->
                viewModel.unlockPdf(selectedPdfUri!!, password)
                showUnlockDialog = false
            },
            onDismiss = { showUnlockDialog = false },
        )
    }

    if (showWatermarkDialog && (selectedPdfUri != null)) {
        WatermarkDialog(
            onConfirm = { text ->
                viewModel.addWatermark(selectedPdfUri!!, text)
                showWatermarkDialog = false
            },
            onDismiss = { showWatermarkDialog = false },
        )
    }

    if (showCompressDialog && (selectedPdfUri != null)) {
        CompressDialog(
            onConfirm = { level ->
                viewModel.compressPdf(selectedPdfUri!!, level)
                showCompressDialog = false
            },
            onDismiss = { showCompressDialog = false },
        )
    }

    if (showSignDialog && (selectedPdfUri != null)) {
        SignDialog(
            onConfirm = { name ->
                viewModel.signPdf(selectedPdfUri!!, name)
                showSignDialog = false
            },
            onDismiss = { showSignDialog = false },
        )
    }

    if (showTextToPdfDialog) {
        TextToPdfDialog(
            onConfirm = { text ->
                viewModel.convertTextToPdf(text)
                showTextToPdfDialog = false
            },
            onDismiss = { showTextToPdfDialog = false },
        )
    }

    if (showPageIndicesDialog && (selectedPdfUri != null)) {
        val title = when (currentAction) {
            "delete_pages" -> "Delete Pages"
            "reorder_pages" -> "Reorder Pages"
            else -> "Extract Pages"
        }
        PageIndicesDialog(
            title = title,
            onConfirm = { indices ->
                when (currentAction) {
                    "delete_pages" -> viewModel.deletePages(selectedPdfUri!!, indices)
                    "reorder_pages" -> viewModel.reorderPages(selectedPdfUri!!, indices)
                    else -> viewModel.extractPages(selectedPdfUri!!, indices)
                }
                showPageIndicesDialog = false
                selectedPdfUri = null
            },
            onDismiss = {
                showPageIndicesDialog = false
                selectedPdfUri = null
            },
        )
    }
}

/**
 * Screen 2: Home Dashboard (Design Screen 2)
 */
@Composable
fun HomeDashboardContent(
    allFiles: List<PdfFileItem>,
    context: Context,
    onOpen: (File) -> Unit,
    onViewAllClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Hello User,",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(42.dp),
                    ) {
                        IconButton(onClick = onViewAllClick) {
                            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(42.dp),
                    ) {
                        IconButton(onClick = { Toast.makeText(context, "No new notifications", Toast.LENGTH_SHORT).show() }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // All Files Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "All Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onViewAllClick) {
                    Text("View All", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Files List
        if (allFiles.isNotEmpty()) {
            items(allFiles) { file ->
                DashboardFileCard(file = file, context = context, onOpen = onOpen)
            }
        } else {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No PDF files found on device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DashboardFileCard(file: PdfFileItem, context: Context, onOpen: (File) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(File(file.path)) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEBEE),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatFileSize(file.size)} • PDF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { sharePdfFile(context, file.path) }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Screen 3: Toolbox / All PDF Tools (Design Screen 3)
 */
@Composable
fun ToolboxContent(
    allTools: List<UtilityToolItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
) {
    val filteredTools = remember(searchQuery, allTools) {
        if (searchQuery.isEmpty()) allTools
        else allTools.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TOOLBOX",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = "Avatar", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search tools...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
                singleLine = true,
            )
        }

        // Grid of Tool Cards (2 columns)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in filteredTools.chunked(2)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (tool in row) {
                            ToolboxGridCard(tool = tool, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolboxGridCard(tool: UtilityToolItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(125.dp)
            .clickable { tool.onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Screen 5: Settings (Design Screen 5)
 */
@Composable
fun SettingsContent(themeViewModel: ThemeViewModel) {
    val themeMode by themeViewModel.themeMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Account Group
        SettingsGroupCard(title = "Account") {
            SettingsRowItem(
                icon = Icons.Outlined.Person,
                label = "User Profile",
                subtitle = "user@swift.pdf",
                onClick = {},
            )
        }

        // App Settings Group
        SettingsGroupCard(title = "App Settings") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Theme (Dark Mode)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Switch(
                    checked = themeMode == ThemeMode.DARK,
                    onCheckedChange = { isDark ->
                        themeViewModel.setThemeMode(if (isDark) ThemeMode.DARK else ThemeMode.LIGHT)
                    },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Notification", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                var notifyEnabled by remember { mutableStateOf(true) }
                Switch(
                    checked = notifyEnabled,
                    onCheckedChange = { notifyEnabled = it },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            SettingsRowItem(
                icon = Icons.Outlined.Language,
                label = "Language",
                subtitle = "English",
                onClick = {},
            )
        }

        // Storage Group
        SettingsGroupCard(title = "Storage") {
            SettingsRowItem(
                icon = Icons.Outlined.CloudQueue,
                label = "Cloud Services",
                onClick = {},
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            SettingsRowItem(
                icon = Icons.Outlined.FolderZip,
                label = "Dropbox",
                onClick = {},
            )
        }

        // Privacy Group
        SettingsGroupCard(title = "Privacy") {
            SettingsRowItem(
                icon = Icons.Outlined.Info,
                label = "About Swift PDF",
                subtitle = "Version 1.0.0 (2026)",
                onClick = {},
            )
        }
    }
}

@Composable
fun SettingsGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Gray)
    }
}

@Composable
fun CompressDialog(onConfirm: (Float) -> Unit, onDismiss: () -> Unit) {
    var compressionLevel by remember { mutableFloatStateOf(0.5f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compress PDF File", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select compression strength:", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Compression: ${(compressionLevel * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = compressionLevel,
                    onValueChange = { compressionLevel = it },
                    valueRange = 0.2f..0.8f,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(compressionLevel) }, shape = RoundedCornerShape(10.dp)) {
                Text("Compress Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun SignDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("Jane Doe") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("E-Sign PDF", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter signature text:", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Signature Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotEmpty(), shape = RoundedCornerShape(10.dp)) {
                Text("Add Signature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun RotateDialog(onRotate: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rotate PDF") },
        text = { Text("Choose rotation angle:") },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onRotate(90) }, modifier = Modifier.fillMaxWidth()) { Text("90° Right") }
                Button(onClick = { onRotate(180) }, modifier = Modifier.fillMaxWidth()) { Text("180° Flip") }
                Button(onClick = { onRotate(270) }, modifier = Modifier.fillMaxWidth()) { Text("270° Left") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun PasswordDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = password.isNotEmpty(), shape = RoundedCornerShape(10.dp)) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun WatermarkDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Watermark") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Watermark Text") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotEmpty(), shape = RoundedCornerShape(10.dp)) { Text("Add Stamp") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun TextToPdfDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Text to PDF") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Type text here...") },
                modifier = Modifier.height(180.dp),
                maxLines = 10,
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }, enabled = text.isNotEmpty(), shape = RoundedCornerShape(10.dp)) { Text("Convert") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun PageIndicesDialog(title: String, onConfirm: (List<Int>) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val description = when (title) {
                    "Reorder Pages" -> "Enter page numbers sequence (e.g. 3, 1, 2):"
                    "Delete Pages" -> "Enter page numbers to remove (e.g. 1, 2):"
                    else -> "Enter page numbers to extract (e.g. 1, 2):"
                }
                Text(description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Page Numbers") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val indices = text.split(",")
                        .asSequence()
                        .mapNotNull { it.trim().toIntOrNull()?.minus(1) }
                        .filter { it >= 0 }
                        .toList()
                    if (indices.isNotEmpty()) onConfirm(indices)
                },
                enabled = text.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun StatusCards(uiState: PdfUiState, onDismiss: () -> Unit, context: Context, onOpen: (File) -> Unit) {
    when (uiState) {
        is PdfUiState.Success -> SuccessCard(state = uiState, onDismiss = onDismiss, context = context, onOpen = onOpen)
        is PdfUiState.Error -> ErrorCard(state = uiState, onDismiss = onDismiss)
        else -> {}
    }
}

@Composable
fun SearchResultsContent(results: List<PdfFileItem>, context: Context, onOpen: (File) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(icon = Icons.Default.Description, title = "Found PDF Documents (${results.size})")
        }

        if (results.isEmpty()) {
            item {
                Text(
                    text = "No matching PDF files found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            items(results) { file ->
                DashboardFileCard(file, context, onOpen)
            }
        }
    }
}

@Composable
fun SuccessCard(state: PdfUiState.Success, onDismiss: () -> Unit, context: Context, onOpen: (File) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Operation Completed!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Text(
                text = "📄 ${state.fileName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "💾 ${formatFileSize(state.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onOpen(File(state.filePath)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Open PDF")
                }

                OutlinedButton(
                    onClick = { sharePdfFile(context, state.filePath) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Share")
                }
            }
        }
    }
}

@Composable
fun ErrorCard(state: PdfUiState.Error, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Operation Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
fun ProcessingDialog(loadingState: PdfUiState.Loading) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(52.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                )
                Text(
                    text = "Processing PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = loadingState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

data class UtilityToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun sharePdfFile(context: Context, filePath: String) {
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

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", kb)
    }
}
