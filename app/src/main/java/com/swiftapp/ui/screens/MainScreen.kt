package com.swiftapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.swiftapp.utils.HomeSubSection
import com.swiftapp.utils.PdfListFilter
import com.swiftapp.utils.PdfSortOption
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.swiftapp.R
import com.swiftapp.ui.viewmodel.PdfUiState
import com.swiftapp.ui.viewmodel.PdfViewModel
import com.swiftapp.ui.viewmodel.ThemeMode
import com.swiftapp.ui.viewmodel.ThemeViewModel
import com.swiftapp.ui.components.*
import com.swiftapp.utils.PdfFileItem
import com.swiftapp.utils.PdfHelper
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * 3-Tab Bottom Navigation corresponding to the design mockup.
 */
enum class NavTab(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Tools("Toolbox", Icons.Default.Layers),
    Settings("Settings", Icons.Default.Settings),
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
    val displayFiles by viewModel.displayFiles.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val homeSubSection by viewModel.homeSubSection.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsState()

    var selectedTab by remember { mutableStateOf(NavTab.Home) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Dialog states for File Context Menu
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var fileForDetails by remember { mutableStateOf<PdfFileItem?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }

    // Permission Launchers
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.loadAllFiles(context, forceRefresh = true)
    }

    val requestStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        viewModel.setStoragePermission(granted)
        viewModel.loadAllFiles(context, forceRefresh = true)
    }

    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            requestStoragePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    // Reader, Merge, Compress, Scan, Sign, Protect, Unlock, ImageToPdf, PdfToImages, Rotate & Split Screen States
    var readerFile by remember { mutableStateOf<File?>(null) }
    var isMergeScreenOpen by remember { mutableStateOf(false) }
    var isCompressScreenOpen by remember { mutableStateOf(false) }
    var compressPdfFile by remember { mutableStateOf<File?>(null) }
    var isScanScreenOpen by remember { mutableStateOf(false) }
    var isSignScreenOpen by remember { mutableStateOf(false) }
    var signPdfFile by remember { mutableStateOf<File?>(null) }
    var isProtectScreenOpen by remember { mutableStateOf(false) }
    var protectPdfFile by remember { mutableStateOf<File?>(null) }
    var isUnlockScreenOpen by remember { mutableStateOf(false) }
    var unlockPdfFile by remember { mutableStateOf<File?>(null) }
    var isImageToPdfScreenOpen by remember { mutableStateOf(false) }
    var imageToPdfUris by remember { mutableStateOf<List<Uri>?>(null) }
    var isPdfToImagesScreenOpen by remember { mutableStateOf(false) }
    var pdfToImagesFile by remember { mutableStateOf<File?>(null) }
    var isRotateScreenOpen by remember { mutableStateOf(false) }
    var rotatePdfFile by remember { mutableStateOf<File?>(null) }
    var isSplitScreenOpen by remember { mutableStateOf(false) }
    var splitPdfFile by remember { mutableStateOf<File?>(null) }
    var isDeletePagesScreenOpen by remember { mutableStateOf(false) }
    var deletePagesPdfFile by remember { mutableStateOf<File?>(null) }

    if (readerFile != null) {
        BackHandler { readerFile = null }
        PdfReaderScreen(file = readerFile!!) { readerFile = null }
        return
    }

    if (isMergeScreenOpen) {
        BackHandler { isMergeScreenOpen = false }
        MergePdfScreen(
            onNavigateBack = { isMergeScreenOpen = false },
            onOpenMergedPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isMergeScreenOpen = false
                readerFile = file
            },
        )
        return
    }

    if (isCompressScreenOpen) {
        BackHandler {
            isCompressScreenOpen = false
            compressPdfFile = null
        }
        CompressPdfScreen(
            initialPdfFile = compressPdfFile,
            onNavigateBack = {
                isCompressScreenOpen = false
                compressPdfFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isCompressScreenOpen = false
                compressPdfFile = null
                readerFile = file
            },
        )
        return
    }

    if (isScanScreenOpen) {
        BackHandler { isScanScreenOpen = false }
        ScanPdfScreen(
            onNavigateBack = { isScanScreenOpen = false },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isScanScreenOpen = false
                readerFile = file
            },
        )
        return
    }

    if (isSignScreenOpen) {
        BackHandler {
            isSignScreenOpen = false
            signPdfFile = null
        }
        ESignPdfScreen(
            initialPdfFile = signPdfFile,
            onNavigateBack = {
                isSignScreenOpen = false
                signPdfFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isSignScreenOpen = false
                signPdfFile = null
                readerFile = file
            },
        )
        return
    }

    if (isProtectScreenOpen) {
        BackHandler {
            isProtectScreenOpen = false
            protectPdfFile = null
        }
        ProtectPdfScreen(
            initialPdfFile = protectPdfFile,
            onNavigateBack = {
                isProtectScreenOpen = false
                protectPdfFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isProtectScreenOpen = false
                protectPdfFile = null
                readerFile = file
            },
        )
        return
    }

    if (isUnlockScreenOpen) {
        BackHandler {
            isUnlockScreenOpen = false
            unlockPdfFile = null
        }
        UnlockPdfScreen(
            initialPdfFile = unlockPdfFile,
            onNavigateBack = {
                isUnlockScreenOpen = false
                unlockPdfFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isUnlockScreenOpen = false
                unlockPdfFile = null
                readerFile = file
            },
        )
        return
    }

    if (isImageToPdfScreenOpen) {
        BackHandler {
            isImageToPdfScreenOpen = false
            imageToPdfUris = null
        }
        ImageToPdfScreen(
            initialUris = imageToPdfUris,
            onNavigateBack = {
                isImageToPdfScreenOpen = false
                imageToPdfUris = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isImageToPdfScreenOpen = false
                imageToPdfUris = null
                readerFile = file
            },
        )
        return
    }

    if (isPdfToImagesScreenOpen) {
        BackHandler {
            isPdfToImagesScreenOpen = false
            pdfToImagesFile = null
        }
        PdfToImagesScreen(
            initialPdfFile = pdfToImagesFile,
            onNavigateBack = {
                isPdfToImagesScreenOpen = false
                pdfToImagesFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isPdfToImagesScreenOpen = false
                pdfToImagesFile = null
                readerFile = file
            },
        )
        return
    }

    if (isRotateScreenOpen) {
        BackHandler {
            isRotateScreenOpen = false
            rotatePdfFile = null
        }
        RotatePdfScreen(
            initialPdfFile = rotatePdfFile,
            onNavigateBack = {
                isRotateScreenOpen = false
                rotatePdfFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isRotateScreenOpen = false
                rotatePdfFile = null
                readerFile = file
            },
        )
        return
    }

    if (isSplitScreenOpen) {
        BackHandler {
            isSplitScreenOpen = false
            splitPdfFile = null
        }
        SplitPdfScreen(
            initialPdfFile = splitPdfFile,
            onNavigateBack = {
                isSplitScreenOpen = false
                splitPdfFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isSplitScreenOpen = false
                splitPdfFile = null
                readerFile = file
            },
        )
        return
    }

    if (isDeletePagesScreenOpen) {
        BackHandler {
            isDeletePagesScreenOpen = false
            deletePagesPdfFile = null
        }
        DeletePagesScreen(
            initialPdfFile = deletePagesPdfFile,
            onNavigateBack = {
                isDeletePagesScreenOpen = false
                deletePagesPdfFile = null
            },
            onOpenPdf = { file ->
                viewModel.addToRecent(context, file)
                viewModel.loadAllFiles(context)
                isDeletePagesScreenOpen = false
                deletePagesPdfFile = null
                readerFile = file
            },
        )
        return
    }

    // Root Back Handling for Main Screen
    var backPressedTime by remember { mutableLongStateOf(0L) }
    BackHandler {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            viewModel.setSearchQuery("")
        } else if (searchQuery.isNotEmpty()) {
            searchQuery = ""
            viewModel.setSearchQuery("")
        } else if (selectedTab != NavTab.Home) {
            selectedTab = NavTab.Home
        } else {
            val now = System.currentTimeMillis()
            if (now - backPressedTime < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                backPressedTime = now
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
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
                onClick = { isMergeScreenOpen = true },
            ),
            UtilityToolItem(
                id = "compress_pdf",
                title = "Compress PDF",
                description = "Reduce document file size.",
                icon = Icons.Outlined.Compress,
                onClick = { isCompressScreenOpen = true },
            ),
            UtilityToolItem(
                id = "scan_pdf",
                title = "Scan to PDF",
                description = "Scan paper documents using camera.",
                icon = Icons.Outlined.DocumentScanner,
                onClick = { isScanScreenOpen = true },
            ),
            UtilityToolItem(
                id = "esign_pdf",
                title = "E-Sign PDF",
                description = "Add signature stamp to PDF.",
                icon = Icons.Outlined.BorderColor,
                onClick = {
                    signPdfFile = null
                    isSignScreenOpen = true
                },
            ),
            UtilityToolItem(
                id = "protect_pdf",
                title = "Protect PDF",
                description = "Add password encryption.",
                icon = Icons.Outlined.Lock,
                onClick = {
                    protectPdfFile = null
                    isProtectScreenOpen = true
                },
            ),
            UtilityToolItem(
                id = "unlock_pdf",
                title = "Unlock PDF",
                description = "Remove password lock.",
                icon = Icons.Outlined.LockOpen,
                onClick = {
                    unlockPdfFile = null
                    isUnlockScreenOpen = true
                },
            ),
            UtilityToolItem(
                id = "image_to_pdf",
                title = "Image to PDF",
                description = "Convert photos & images to PDF.",
                icon = Icons.Outlined.Collections,
                onClick = {
                    imageToPdfUris = null
                    isImageToPdfScreenOpen = true
                },
            ),
            UtilityToolItem(
                id = "pdf_to_images",
                title = "PDF to Images",
                description = "Extract images from pages.",
                icon = Icons.Outlined.Image,
                onClick = {
                    pdfToImagesFile = null
                    isPdfToImagesScreenOpen = true
                },
            ),
            UtilityToolItem(
                id = "rotate_pdf",
                title = "Rotate PDF",
                description = "Change page orientation.",
                icon = Icons.AutoMirrored.Outlined.RotateRight,
                onClick = {
                    rotatePdfFile = null
                    isRotateScreenOpen = true
                },
            ),
            UtilityToolItem(
                id = "split_pdf",
                title = "Split PDF",
                description = "Extract pages into separate PDFs.",
                icon = Icons.AutoMirrored.Outlined.CallSplit,
                onClick = {
                    splitPdfFile = null
                    isSplitScreenOpen = true
                },
            ),
            UtilityToolItem(
                id = "delete_pages",
                title = "Delete Pages",
                description = "Remove unwanted pages from PDF.",
                icon = Icons.Outlined.DeleteSweep,
                onClick = {
                    deletePagesPdfFile = null
                    isDeletePagesScreenOpen = true
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
            if (uiState !is PdfUiState.Idle) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
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
            }

            if (isSearchActive && searchQuery.isNotEmpty()) {
                SearchResultsContent(
                    results = searchResults,
                    context = context,
                    onOpen = {
                        viewModel.addToRecent(context, it)
                        readerFile = it
                    },
                    onShare = { sharePdfFile(context, it.path) },
                    onRename = { fileToRename = it },
                    onDelete = { fileToDelete = it },
                    onShowDetails = { fileForDetails = it },
                    onSendToTool = { toolId, file ->
                        when (toolId) {
                            "compress" -> { compressPdfFile = file; isCompressScreenOpen = true }
                            "esign" -> { signPdfFile = file; isSignScreenOpen = true }
                            "protect" -> { protectPdfFile = file; isProtectScreenOpen = true }
                            "unlock" -> { unlockPdfFile = file; isUnlockScreenOpen = true }
                            "rotate" -> { rotatePdfFile = file; isRotateScreenOpen = true }
                            "split" -> { splitPdfFile = file; isSplitScreenOpen = true }
                            "delete_pages" -> { deletePagesPdfFile = file; isDeletePagesScreenOpen = true }
                            "pdf_to_images" -> { pdfToImagesFile = file; isPdfToImagesScreenOpen = true }
                        }
                    }
                )
            } else {
                when (selectedTab) {
                    NavTab.Home -> HomeDashboardContent(
                        displayFiles = displayFiles,
                        isScanning = isScanning,
                        homeSubSection = homeSubSection,
                        activeFilter = activeFilter,
                        sortOption = sortOption,
                        isGridView = isGridView,
                        hasStoragePermission = hasStoragePermission,
                        searchQuery = searchQuery,
                        context = context,
                        onSearchChange = {
                            searchQuery = it
                            viewModel.setSearchQuery(it)
                        },
                        onSubSectionSelected = { viewModel.setHomeSubSection(it) },
                        onFilterSelected = { viewModel.setFilter(it) },
                        onSortClick = { showSortDialog = true },
                        onToggleViewMode = { viewModel.toggleViewMode() },
                        onRefresh = { viewModel.loadAllFiles(context, forceRefresh = true) },
                        onRequestPermissions = { requestPermissions() },
                        onOpen = {
                            viewModel.addToRecent(context, it)
                            readerFile = it
                        },
                        onShare = { sharePdfFile(context, it.path) },
                        onRename = { fileToRename = it },
                        onDelete = { fileToDelete = it },
                        onShowDetails = { fileForDetails = it },
                        onSendToTool = { toolId, file ->
                            when (toolId) {
                                "compress" -> {
                                    compressPdfFile = file
                                    isCompressScreenOpen = true
                                }
                                "esign" -> {
                                    signPdfFile = file
                                    isSignScreenOpen = true
                                }
                                "protect" -> {
                                    protectPdfFile = file
                                    isProtectScreenOpen = true
                                }
                                "unlock" -> {
                                    unlockPdfFile = file
                                    isUnlockScreenOpen = true
                                }
                                "rotate" -> {
                                    rotatePdfFile = file
                                    isRotateScreenOpen = true
                                }
                                "split" -> {
                                    splitPdfFile = file
                                    isSplitScreenOpen = true
                                }
                                "delete_pages" -> {
                                    deletePagesPdfFile = file
                                    isDeletePagesScreenOpen = true
                                }
                                "pdf_to_images" -> {
                                    pdfToImagesFile = file
                                    isPdfToImagesScreenOpen = true
                                }
                            }
                        }
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
    // File Operations Dialogs
    if (fileToRename != null) {
        RenamePdfDialog(
            file = fileToRename!!,
            onDismiss = { fileToRename = null },
            onConfirm = { newName ->
                val target = fileToRename!!
                fileToRename = null
                viewModel.renameFile(context, target, newName) { result ->
                    result.fold(
                        onSuccess = { Toast.makeText(context, "Renamed successfully", Toast.LENGTH_SHORT).show() },
                        onFailure = { Toast.makeText(context, "Rename failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        )
    }

    if (fileToDelete != null) {
        DeletePdfDialog(
            file = fileToDelete!!,
            onDismiss = { fileToDelete = null },
            onConfirm = {
                val target = fileToDelete!!
                fileToDelete = null
                viewModel.deleteFile(context, target) { result ->
                    result.fold(
                        onSuccess = { Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show() },
                        onFailure = { Toast.makeText(context, "Delete failed: ${it.message}", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        )
    }

    if (fileForDetails != null) {
        PdfDetailsDialog(
            fileItem = fileForDetails!!,
            onDismiss = { fileForDetails = null }
        )
    }

    if (showSortDialog) {
        PdfSortBottomSheetDialog(
            currentSort = sortOption,
            onSortSelected = {
                viewModel.setSortOption(it)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }
}

/**
 * Screen 2: Adobe Acrobat-Style Home Dashboard (All PDF Files Explorer)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardContent(
    displayFiles: List<PdfFileItem>,
    isScanning: Boolean,
    homeSubSection: HomeSubSection,
    activeFilter: PdfListFilter,
    sortOption: PdfSortOption,
    isGridView: Boolean,
    hasStoragePermission: Boolean,
    searchQuery: String,
    context: Context,
    onSearchChange: (String) -> Unit,
    onSubSectionSelected: (HomeSubSection) -> Unit,
    onFilterSelected: (PdfListFilter) -> Unit,
    onSortClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpen: (File) -> Unit,
    onShare: (PdfFileItem) -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onShowDetails: (PdfFileItem) -> Unit,
    onSendToTool: (String, File) -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val chunkedGridFiles = remember(displayFiles) { displayFiles.chunked(2) }

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh()
        }
    }

    LaunchedEffect(isScanning) {
        if (!isScanning) {
            pullToRefreshState.endRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Header Greeting with Top-Right Search
            item {
                var isSearchFieldOpen by remember { mutableStateOf(false) }

                AnimatedContent(
                    targetState = isSearchFieldOpen || searchQuery.isNotEmpty(),
                    label = "HeaderSearchAnimation"
                ) { isSearching ->
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = { Text("Search file") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = {
                                    onSearchChange("")
                                    isSearchFieldOpen = false
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close search",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                            singleLine = true,
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Swift PDF",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "All Your PDF Tools in One Place",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            TactileIconButton(
                                onClick = { isSearchFieldOpen = true },
                                size = 42.dp,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Permission Banner (if not granted)
            if (!hasStoragePermission) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Storage Access Needed",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Allow storage access to view and manage all PDF documents on your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = onRequestPermissions,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. 2-Tab SubSection Switcher (Left: Recent | Right: All Files)
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Left Subsection: Recent
                        val isRecentSelected = homeSubSection == HomeSubSection.RECENT
                        Surface(
                            onClick = { onSubSectionSelected(HomeSubSection.RECENT) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .bounceClick(scaleDownFactor = 0.97f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isRecentSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isRecentSelected) 2.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isRecentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Recent",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isRecentSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isRecentSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Right Subsection: All Files
                        val isAllFilesSelected = homeSubSection == HomeSubSection.ALL_FILES
                        Surface(
                            onClick = { onSubSectionSelected(HomeSubSection.ALL_FILES) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .bounceClick(scaleDownFactor = 0.97f),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAllFilesSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isAllFilesSelected) 2.dp else 0.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isAllFilesSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "All Files",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isAllFilesSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isAllFilesSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Explorer Control Bar: Count + Sort + Grid/List Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isScanning && homeSubSection == HomeSubSection.ALL_FILES) "Scanning..." else "${displayFiles.size} PDFs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (isScanning && homeSubSection == HomeSubSection.ALL_FILES) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Sort Button
                        TextButton(
                            onClick = onSortClick,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.bounceClick()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sortOption.title.split(" ").first(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Grid / List View Toggle
                        TactileIconButton(
                            onClick = onToggleViewMode,
                            size = 36.dp
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                                contentDescription = "Toggle View",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 5. Content: Shimmer Skeleton | Empty State | Files (List / Grid)
            if (isScanning && displayFiles.isEmpty() && homeSubSection == HomeSubSection.ALL_FILES) {
                // Skeleton Loader items
                items(6) {
                    ShimmerFilePlaceholder()
                }
            } else if (displayFiles.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (homeSubSection == HomeSubSection.RECENT) Icons.Outlined.History else Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) {
                                "No PDFs matching \"$searchQuery\""
                            } else if (homeSubSection == HomeSubSection.RECENT) {
                                "No Recent Documents"
                            } else {
                                "No PDF Files Found"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (homeSubSection == HomeSubSection.RECENT) {
                                "PDF files opened, converted, or altered with Swift PDF will appear here."
                            } else {
                                "Pull down from the top to scan storage and list all PDF documents on your device."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                if (isGridView) {
                    // 2-Column Grid Layout with stable keys
                    items(
                        items = chunkedGridFiles,
                        key = { row -> row.joinToString(separator = "_") { it.path } },
                        contentType = { "grid_row" }
                    ) { rowFiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowFiles.forEach { fileItem ->
                                Box(modifier = Modifier.weight(1f)) {
                                    GridPdfFileCard(
                                        file = fileItem,
                                        context = context,
                                        onOpen = onOpen,
                                        onShare = { onShare(fileItem) },
                                        onRename = { onRename(File(fileItem.path)) },
                                        onDelete = { onDelete(File(fileItem.path)) },
                                        onShowDetails = { onShowDetails(fileItem) },
                                        onSendToTool = { tool -> onSendToTool(tool, File(fileItem.path)) }
                                    )
                                }
                            }
                            if (rowFiles.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    // List Layout with stable keys and recycling contentType
                    items(
                        items = displayFiles,
                        key = { it.path },
                        contentType = { "pdf_list_item" }
                    ) { fileItem ->
                        ListPdfFileCard(
                            file = fileItem,
                            context = context,
                            onOpen = onOpen,
                            onShare = { onShare(fileItem) },
                            onRename = { onRename(File(fileItem.path)) },
                            onDelete = { onDelete(File(fileItem.path)) },
                            onShowDetails = { onShowDetails(fileItem) },
                            onSendToTool = { tool -> onSendToTool(tool, File(fileItem.path)) }
                        )
                    }
                }
            }
        }

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * List View Card with 3-Dot Dropdown Context Menu.
 */
@Composable
fun ListPdfFileCard(
    file: PdfFileItem,
    context: Context,
    onOpen: (File) -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    onSendToTool: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TactileCard(
        onClick = { onOpen(File(file.path)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFEBEE),
                modifier = Modifier.size(46.dp),
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatFileSize(file.size)} • ${PdfHelper.formatRelativeDate(file.dateModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                TactileIconButton(
                    onClick = { menuExpanded = true },
                    size = 36.dp
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                PdfFileContextMenu(
                    expanded = menuExpanded,
                    file = file,
                    onDismiss = { menuExpanded = false },
                    onOpen = { onOpen(File(file.path)) },
                    onShare = onShare,
                    onRename = onRename,
                    onDelete = onDelete,
                    onShowDetails = onShowDetails,
                    onSendToTool = onSendToTool
                )
            }
        }
    }
}

/**
 * 2-Column Grid View Card.
 */
@Composable
fun GridPdfFileCard(
    file: PdfFileItem,
    context: Context,
    onOpen: (File) -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    onSendToTool: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TactileCard(
        onClick = { onOpen(File(file.path)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Card Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFEBEE).copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // File Details & 3-Dot Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    TactileIconButton(
                        onClick = { menuExpanded = true },
                        size = 32.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PdfFileContextMenu(
                        expanded = menuExpanded,
                        file = file,
                        onDismiss = { menuExpanded = false },
                        onOpen = { onOpen(File(file.path)) },
                        onShare = onShare,
                        onRename = onRename,
                        onDelete = onDelete,
                        onShowDetails = onShowDetails,
                        onSendToTool = onSendToTool
                    )
                }
            }
        }
    }
}

/**
 * 3-Dot Context Menu with File Operations & Send to Tools.
 */
@Composable
fun PdfFileContextMenu(
    expanded: Boolean,
    file: PdfFileItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    onSendToTool: (String) -> Unit
) {
    var showMoreTools by remember { mutableStateOf(false) }
    var isProtected by remember(file.path) { mutableStateOf(false) }

    LaunchedEffect(file.path, expanded) {
        if (expanded) {
            withContext(Dispatchers.IO) {
                try {
                    val encrypted = try {
                        PDDocument.load(File(file.path)).use { doc ->
                            doc.isEncrypted
                        }
                    } catch (e: InvalidPasswordException) {
                        true
                    } catch (e: Exception) {
                        e.message?.contains("password", ignoreCase = true) == true ||
                        e.message?.contains("encrypted", ignoreCase = true) == true
                    }
                    withContext(Dispatchers.Main) {
                        isProtected = encrypted
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        isProtected = false
                    }
                }
            }
        } else {
            showMoreTools = false
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showMoreTools = false
            onDismiss()
        }
    ) {
        if (!showMoreTools) {
            DropdownMenuItem(
                text = { Text("Open") },
                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    onOpen()
                }
            )
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("Details") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    onShowDetails()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("More", fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(14.dp)) },
                onClick = {
                    showMoreTools = true
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Back", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMoreTools = false
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text("Compress PDF") },
                leadingIcon = { Icon(Icons.Outlined.Compress, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMoreTools = false
                    onDismiss()
                    onSendToTool("compress")
                }
            )
            DropdownMenuItem(
                text = { Text("E-Sign PDF") },
                leadingIcon = { Icon(Icons.Outlined.BorderColor, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMoreTools = false
                    onDismiss()
                    onSendToTool("esign")
                }
            )
            if (isProtected) {
                DropdownMenuItem(
                    text = { Text("Unlock PDF") },
                    leadingIcon = { Icon(Icons.Outlined.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMoreTools = false
                        onDismiss()
                        onSendToTool("unlock")
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Protect PDF") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showMoreTools = false
                        onDismiss()
                        onSendToTool("protect")
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("PDF to Images") },
                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMoreTools = false
                    onDismiss()
                    onSendToTool("pdf_to_images")
                }
            )
            DropdownMenuItem(
                text = { Text("Rotate PDF") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMoreTools = false
                    onDismiss()
                    onSendToTool("rotate")
                }
            )
            DropdownMenuItem(
                text = { Text("Split PDF") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMoreTools = false
                    onDismiss()
                    onSendToTool("split")
                }
            )
            DropdownMenuItem(
                text = { Text("Delete / Remove Pages") },
                leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    showMoreTools = false
                    onDismiss()
                    onSendToTool("delete_pages")
                }
            )
        }
    }
}

/**
 * Shimmer Loading Placeholder Card.
 */
@Composable
fun ShimmerFilePlaceholder() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Gray.copy(alpha = alpha))
            )
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray.copy(alpha = alpha))
                )
            }
        }
    }
}

/**
 * Rename PDF Dialog.
 */
@Composable
fun RenamePdfDialog(
    file: File,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(file.nameWithoutExtension) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename PDF", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Enter a new name for this document:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    suffix = { Text(".pdf") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameInput.isNotBlank()) {
                        onConfirm(nameInput.trim())
                    }
                }
            ) {
                Text("Rename")
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
 * Delete Confirmation Dialog.
 */
@Composable
fun DeletePdfDialog(
    file: File,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Delete PDF?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "Are you sure you want to delete \"${file.name}\"? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
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
 * PDF Details / File Info Dialog.
 */
@Composable
fun PdfDetailsDialog(
    fileItem: PdfFileItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Document Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(label = "File Name", value = fileItem.name)
                DetailRow(label = "File Size", value = "${formatFileSize(fileItem.size)} (${fileItem.size} bytes)")
                DetailRow(label = "Modified", value = PdfHelper.formatRelativeDate(fileItem.dateModified))
                DetailRow(label = "Storage Path", value = fileItem.path)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Sort Options Dialog / Sheet.
 */
@Composable
fun PdfSortBottomSheetDialog(
    currentSort: PdfSortOption,
    onSortSelected: (PdfSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Documents", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PdfSortOption.entries.forEach { option ->
                    val isSelected = currentSort == option
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortSelected(option) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
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

        // Theme & Appearance Group
        val isAmoled by themeViewModel.isAmoledBlack.collectAsState()

        SettingsGroupCard(title = "Theme & Appearance") {
            ThemeSelectorRow(
                currentMode = themeMode,
                onModeSelected = { themeViewModel.setThemeMode(it) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

            // Pure AMOLED Black Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isAmoled) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Contrast,
                                contentDescription = null,
                                tint = if (isAmoled) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Pure AMOLED Black",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "100% pitch black (#000000) to save battery on OLED displays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isAmoled,
                    onCheckedChange = { themeViewModel.setAmoledBlack(it) }
                )
            }
        }

        // App Preferences Group
        SettingsGroupCard(title = "Preferences") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Notifications", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
fun ThemeSelectorRow(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val options = listOf(
            Triple(ThemeMode.SYSTEM, "System Default", Icons.Outlined.BrightnessAuto),
            Triple(ThemeMode.LIGHT, "Light Mode", Icons.Outlined.LightMode),
            Triple(ThemeMode.DARK, "Dark Mode", Icons.Outlined.DarkMode)
        )

        options.forEach { (mode, title, icon) ->
            val isSelected = currentMode == mode
            TactileCard(
                onClick = { onModeSelected(mode) },
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                } else null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
                Button(onClick = { onRotate(90) }, modifier = Modifier.fillMaxWidth()) { Text("90Â° Right") }
                Button(onClick = { onRotate(180) }, modifier = Modifier.fillMaxWidth()) { Text("180Â° Flip") }
                Button(onClick = { onRotate(270) }, modifier = Modifier.fillMaxWidth()) { Text("270Â° Left") }
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
fun SearchResultsContent(
    results: List<PdfFileItem>,
    context: Context,
    onOpen: (File) -> Unit,
    onShare: (PdfFileItem) -> Unit,
    onRename: (File) -> Unit,
    onDelete: (File) -> Unit,
    onShowDetails: (PdfFileItem) -> Unit,
    onSendToTool: (String, File) -> Unit
) {
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
            items(results, key = { it.path }) { file ->
                ListPdfFileCard(
                    file = file,
                    context = context,
                    onOpen = onOpen,
                    onShare = { onShare(file) },
                    onRename = { onRename(File(file.path)) },
                    onDelete = { onDelete(File(file.path)) },
                    onShowDetails = { onShowDetails(file) },
                    onSendToTool = { toolId -> onSendToTool(toolId, File(file.path)) }
                )
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
                text = "ðŸ“„ ${state.fileName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "ðŸ’¾ ${formatFileSize(state.fileSizeBytes)}",
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
