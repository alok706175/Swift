package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.PdfRepository
import com.swiftapp.data.PdfRepositoryImpl
import com.swiftapp.utils.HomeSubSection
import com.swiftapp.utils.PdfFileItem
import com.swiftapp.utils.PdfHelper
import com.swiftapp.utils.PdfListFilter
import com.swiftapp.utils.PdfSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface PdfUiState {
    data object Idle : PdfUiState
    data class Loading(val message: String = "Processing...", val progress: Float? = null) : PdfUiState
    data class Success(val filePath: String, val fileName: String, val fileSizeBytes: Long) : PdfUiState
    data class Error(val message: String) : PdfUiState
}

class PdfViewModel(
    private val pdfRepository: PdfRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PdfUiState>(PdfUiState.Idle)
    val uiState: StateFlow<PdfUiState> = _uiState.asStateFlow()

    private val _allFiles = MutableStateFlow<List<PdfFileItem>>(emptyList())
    val allFiles: StateFlow<List<PdfFileItem>> = _allFiles.asStateFlow()

    private val _recentlyOpened = MutableStateFlow<List<PdfFileItem>>(emptyList())
    val recentlyOpened: StateFlow<List<PdfFileItem>> = _recentlyOpened.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _homeSubSection = MutableStateFlow(HomeSubSection.RECENT)
    val homeSubSection: StateFlow<HomeSubSection> = _homeSubSection.asStateFlow()

    private val _activeFilter = MutableStateFlow(PdfListFilter.ALL)
    val activeFilter: StateFlow<PdfListFilter> = _activeFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(PdfSortOption.DATE_DESC)
    val sortOption: StateFlow<PdfSortOption> = _sortOption.asStateFlow()

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(true)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PdfFileItem>>(emptyList())
    val searchResults: StateFlow<List<PdfFileItem>> = _searchResults.asStateFlow()

    /**
     * Reactively filtered and sorted PDF list based on active Home subsection (Recent vs All Files).
     */
    val displayFiles: StateFlow<List<PdfFileItem>> = combine(
        _allFiles,
        _recentlyOpened,
        _searchQuery,
        _homeSubSection,
        _sortOption
    ) { all, recent, query, section, sort ->
        var list = when (section) {
            HomeSubSection.RECENT -> recent
            HomeSubSection.ALL_FILES -> all
        }

        // 1. Search Query Filter
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

        // 2. Sorting
        when (sort) {
            PdfSortOption.DATE_DESC -> list.sortedByDescending { it.dateModified }
            PdfSortOption.DATE_ASC -> list.sortedBy { it.dateModified }
            PdfSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            PdfSortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            PdfSortOption.SIZE_DESC -> list.sortedByDescending { it.size }
            PdfSortOption.SIZE_ASC -> list.sortedBy { it.size }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setHomeSubSection(section: HomeSubSection) {
        _homeSubSection.value = section
    }

    fun checkStoragePermissions(context: Context) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        _hasStoragePermission.value = hasPermission
    }

    fun setStoragePermission(granted: Boolean) {
        _hasStoragePermission.value = granted
    }

    fun loadAllFiles(context: Context, forceRefresh: Boolean = false, showScanningIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showScanningIndicator && (forceRefresh || _allFiles.value.isEmpty())) {
                _isScanning.value = true
            }
            checkStoragePermissions(context)
            try {
                val scanned = PdfHelper.searchPdfFiles(context, "", forceRefresh = forceRefresh)
                _allFiles.value = scanned
                loadRecentFiles(context)
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun loadRecentFiles(context: Context) {
        val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)
        val paths = prefs.getStringSet("paths", emptySet()) ?: emptySet()
        val files = paths.asSequence().mapNotNull { path ->
            val file = File(path)
            if (file.exists()) PdfHelper.getPdfFileItem(file) else null
        }.sortedByDescending { it.dateModified }.toList()
        _recentlyOpened.value = files
    }

    fun addToRecent(context: Context, file: File) {
        val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)
        val currentPaths = prefs.getStringSet("paths", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentPaths.add(file.absolutePath)
        prefs.edit { putStringSet("paths", currentPaths) }
        loadRecentFiles(context)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: PdfListFilter) {
        _activeFilter.value = filter
    }

    fun setSortOption(sort: PdfSortOption) {
        _sortOption.value = sort
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun renameFile(context: Context, file: File, newName: String, onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = PdfHelper.renamePdf(file, newName)
            result.onSuccess { renamedFile ->
                loadAllFiles(context, forceRefresh = true, showScanningIndicator = false)
                addToRecent(context, renamedFile)
            }
            onResult(result)
        }
    }

    fun deleteFile(context: Context, file: File, onResult: (Result<Boolean>) -> Unit) {
        val targetPath = file.absolutePath
        val canonicalPath = try { file.canonicalPath } catch (_: Exception) { targetPath }

        // 1. Instantly update in-memory state so UI PDF count and list update with 0ms delay
        _allFiles.value = _allFiles.value.filter { it.path != targetPath && it.path != canonicalPath }
        _recentlyOpened.value = _recentlyOpened.value.filter { it.path != targetPath && it.path != canonicalPath }
        _searchResults.value = _searchResults.value.filter { it.path != targetPath && it.path != canonicalPath }

        // 2. Remove immediately from recent preferences
        try {
            val prefs = context.getSharedPreferences("recent_files", Context.MODE_PRIVATE)
            val currentPaths = prefs.getStringSet("paths", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (currentPaths.remove(targetPath) || currentPaths.remove(canonicalPath)) {
                prefs.edit { putStringSet("paths", currentPaths) }
            }
        } catch (_: Exception) {}

        // 3. Perform asynchronous file deletion and background sync
        viewModelScope.launch {
            val result = PdfHelper.deletePdf(context, file)
            if (result.isSuccess) {
                // Background silent sync to ensure exact filesystem parity without showing "Scanning..." flicker
                loadAllFiles(context, forceRefresh = true, showScanningIndicator = false)
            } else {
                // In the rare case deletion failed on disk, rescan to restore accurate list
                loadAllFiles(context, forceRefresh = true, showScanningIndicator = false)
            }
            onResult(result)
        }
    }

    fun searchDocuments(context: Context, query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = PdfHelper.searchPdfFiles(context, query)
        }
    }

    fun mergeFiles(pdfUris: List<Uri>, outputFile: File? = null) {
        if (pdfUris.isEmpty()) {
            _uiState.value = PdfUiState.Error("No PDF files selected for merging.")
            return
        }
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Merging ${pdfUris.size} PDF files...")
            val result = pdfRepository.mergePdfFiles(pdfUris, outputFile)
            handleResult(result, "Failed to merge PDF files.")
        }
    }

    fun convertImagesToPdf(imageUris: List<Uri>, outputFile: File? = null) {
        if (imageUris.isEmpty()) {
            _uiState.value = PdfUiState.Error("No images selected for conversion.")
            return
        }
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Converting ${imageUris.size} images to PDF...")
            val result = pdfRepository.convertImagesToPdf(imageUris, outputFile)
            handleResult(result, "Failed to convert images to PDF.")
        }
    }

    fun splitFiles(pdfUris: List<Uri>, outputDir: File? = null) {
        if (pdfUris.isEmpty()) {
            _uiState.value = PdfUiState.Error("No PDF file selected for splitting.")
            return
        }
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Splitting PDF into individual pages...")
            val result = pdfRepository.splitPdf(pdfUris.first(), outputDir)
            handleResult(result, "Failed to split PDF file.")
        }
    }

    fun rotatePdf(pdfUri: Uri, rotation: Int) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Rotating PDF by $rotation degrees...")
            val result = pdfRepository.rotatePdf(pdfUri, rotation)
            handleResult(result, "Failed to rotate PDF.")
        }
    }

    fun protectPdf(pdfUri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Protecting PDF with password...")
            val result = pdfRepository.protectPdf(pdfUri, password)
            handleResult(result, "Failed to protect PDF.")
        }
    }

    fun unlockPdf(pdfUri: Uri, password: String) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Unlocking PDF...")
            val result = pdfRepository.unlockPdf(pdfUri, password)
            handleResult(result, "Failed to unlock PDF.")
        }
    }

    fun addWatermark(pdfUri: Uri, watermarkText: String) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Adding watermark to PDF...")
            val result = pdfRepository.addWatermark(pdfUri, watermarkText)
            handleResult(result, "Failed to add watermark.")
        }
    }

    fun pdfToImages(pdfUri: Uri) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Extracting images from PDF...")
            val result = pdfRepository.pdfToImages(pdfUri)
            result.fold(
                onSuccess = { files ->
                    if (files.isNotEmpty()) {
                        _uiState.value = PdfUiState.Success(files.first().absolutePath, "Extracted images", files.sumOf { it.length() })
                    } else {
                        _uiState.value = PdfUiState.Error("No images extracted.")
                    }
                },
                onFailure = { _uiState.value = PdfUiState.Error(it.localizedMessage ?: "Failed to extract images.") },
            )
        }
    }

    fun convertTextToPdf(text: String) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Converting text to PDF...")
            val result = pdfRepository.textToPdf(text)
            handleResult(result, "Failed to convert text to PDF.")
        }
    }

    fun deletePages(pdfUri: Uri, pageIndices: List<Int>) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Deleting pages...")
            val result = pdfRepository.deletePages(pdfUri, pageIndices)
            handleResult(result, "Failed to delete pages.")
        }
    }

    fun reorderPages(pdfUri: Uri, newOrder: List<Int>) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Reordering pages...")
            val result = pdfRepository.reorderPages(pdfUri, newOrder)
            handleResult(result, "Failed to reorder pages.")
        }
    }

    fun extractPages(pdfUri: Uri, pageIndices: List<Int>) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Extracting pages...")
            val result = pdfRepository.extractPages(pdfUri, pageIndices)
            handleResult(result, "Failed to extract pages.")
        }
    }

    fun compressPdf(pdfUri: Uri, compressionLevel: Float = 0.5f) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Compressing PDF file...")
            val result = pdfRepository.compressPdf(pdfUri, compressionLevel)
            handleResult(result, "Failed to compress PDF.")
        }
    }

    fun signPdf(pdfUri: Uri, signatureText: String) {
        viewModelScope.launch {
            _uiState.value = PdfUiState.Loading(message = "Adding digital signature...")
            val result = pdfRepository.signPdf(pdfUri, signatureText)
            handleResult(result, "Failed to sign PDF.")
        }
    }

    private fun handleResult(result: Result<File>, defaultErrorMessage: String) {
        result.fold(
            onSuccess = { _uiState.value = PdfUiState.Success(it.absolutePath, it.name, it.length()) },
            onFailure = { _uiState.value = PdfUiState.Error(it.localizedMessage ?: defaultErrorMessage) },
        )
    }

    fun resetState() { _uiState.value = PdfUiState.Idle }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PdfViewModel(PdfRepositoryImpl(context.applicationContext)) as T
                }
            }
    }
}
