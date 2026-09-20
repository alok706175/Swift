package com.example.pdfutilityapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pdfutilityapp.data.PdfRepository
import com.example.pdfutilityapp.data.PdfRepositoryImpl
import com.example.pdfutilityapp.utils.PdfFileItem
import com.example.pdfutilityapp.utils.PdfHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _searchResults = MutableStateFlow<List<PdfFileItem>>(emptyList())
    val searchResults: StateFlow<List<PdfFileItem>> = _searchResults.asStateFlow()

    private val _allFiles = MutableStateFlow<List<PdfFileItem>>(emptyList())
    val allFiles: StateFlow<List<PdfFileItem>> = _allFiles.asStateFlow()

    private val _recentlyOpened = MutableStateFlow<List<PdfFileItem>>(emptyList())
    val recentlyOpened: StateFlow<List<PdfFileItem>> = _recentlyOpened.asStateFlow()

    fun loadAllFiles(context: Context) {
        viewModelScope.launch {
            _allFiles.value = PdfHelper.searchPdfFiles(context, "")
            loadRecentFiles(context)
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

    fun searchDocuments(context: Context, query: String) {
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
