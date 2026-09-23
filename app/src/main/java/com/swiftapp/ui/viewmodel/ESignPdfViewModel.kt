package com.swiftapp.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.ESignPdfService
import com.swiftapp.data.model.ESignDocumentInfo
import com.swiftapp.data.model.ESignUiState
import com.swiftapp.data.model.SavedSignatureItem
import com.swiftapp.data.model.SignElementItem
import com.swiftapp.data.model.SignElementType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ESignPdfViewModel : ViewModel() {

    private val _documentInfo = MutableStateFlow<ESignDocumentInfo?>(null)
    val documentInfo: StateFlow<ESignDocumentInfo?> = _documentInfo.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _pageBitmap = MutableStateFlow<Bitmap?>(null)
    val pageBitmap: StateFlow<Bitmap?> = _pageBitmap.asStateFlow()

    private val _isPageRendering = MutableStateFlow(false)
    val isPageRendering: StateFlow<Boolean> = _isPageRendering.asStateFlow()

    private val _zoomScale = MutableStateFlow(1.0f)
    val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

    private val _elements = MutableStateFlow<List<SignElementItem>>(emptyList())
    val elements: StateFlow<List<SignElementItem>> = _elements.asStateFlow()

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId.asStateFlow()

    private val _savedSignatures = MutableStateFlow<List<SavedSignatureItem>>(emptyList())
    val savedSignatures: StateFlow<List<SavedSignatureItem>> = _savedSignatures.asStateFlow()

    private val _uiState = MutableStateFlow<ESignUiState>(ESignUiState.Idle)
    val uiState: StateFlow<ESignUiState> = _uiState.asStateFlow()

    private val _isSignatureDialogVisible = MutableStateFlow(false)
    val isSignatureDialogVisible: StateFlow<Boolean> = _isSignatureDialogVisible.asStateFlow()

    private val _isTextDialogVisible = MutableStateFlow(false)
    val isTextDialogVisible: StateFlow<Boolean> = _isTextDialogVisible.asStateFlow()

    fun openSignatureDialog() {
        _isSignatureDialogVisible.value = true
    }

    fun closeSignatureDialog() {
        _isSignatureDialogVisible.value = false
    }

    fun openTextDialog() {
        _isTextDialogVisible.value = true
    }

    fun closeTextDialog() {
        _isTextDialogVisible.value = false
    }

    fun loadSavedSignatures(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = ESignPdfService.loadSavedSignatures(context)
            _savedSignatures.value = list
        }
    }

    fun loadDocument(context: Context, file: File, password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ESignUiState.Loading("Opening document...")
            val pageCount = ESignPdfService.getPageCount(file)
            if (pageCount <= 0) {
                _uiState.value = ESignUiState.Error("Unable to open PDF. File might be damaged or password protected.")
                return@launch
            }

            val docInfo = ESignDocumentInfo(
                file = file,
                pageCount = pageCount,
                password = password
            )
            _documentInfo.value = docInfo
            _currentPageIndex.value = 0
            _elements.value = emptyList()
            _selectedElementId.value = null
            _uiState.value = ESignUiState.DocumentLoaded(docInfo)

            loadSavedSignatures(context)
            loadPage(context, 0)
        }
    }

    fun loadPage(context: Context, pageIndex: Int) {
        val doc = _documentInfo.value ?: return
        if (pageIndex !in 0 until doc.pageCount) return

        _currentPageIndex.value = pageIndex
        _selectedElementId.value = null

        viewModelScope.launch(Dispatchers.IO) {
            _isPageRendering.value = true
            val bitmap = ESignPdfService.renderPageToBitmap(context, doc.file, pageIndex, 1400)
            _pageBitmap.value = bitmap
            _isPageRendering.value = false
        }
    }

    fun setZoom(scale: Float) {
        _zoomScale.value = scale.coerceIn(0.6f, 3.0f)
    }

    fun addSignatureBitmap(
        context: Context,
        bitmap: Bitmap,
        saveToFavorites: Boolean = false,
        name: String = "Signature"
    ) {
        val activePage = _currentPageIndex.value
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val defaultWidthNorm = 0.35f
        val defaultHeightNorm = defaultWidthNorm / aspect

        val newItem = SignElementItem(
            id = UUID.randomUUID().toString(),
            type = SignElementType.SIGNATURE,
            pageIndex = activePage,
            xNorm = 0.32f,
            yNorm = 0.45f,
            widthNorm = defaultWidthNorm,
            heightNorm = defaultHeightNorm,
            bitmap = bitmap
        )

        _elements.update { it + newItem }
        _selectedElementId.value = newItem.id
        _isSignatureDialogVisible.value = false

        if (saveToFavorites) {
            viewModelScope.launch(Dispatchers.IO) {
                ESignPdfService.saveSignatureToStorage(context, bitmap, name)
                loadSavedSignatures(context)
            }
        }
    }

    fun addTextElement(text: String, color: Color = Color.Black) {
        if (text.isBlank()) return
        val activePage = _currentPageIndex.value

        val newItem = SignElementItem(
            id = UUID.randomUUID().toString(),
            type = SignElementType.TEXT,
            pageIndex = activePage,
            xNorm = 0.3f,
            yNorm = 0.5f,
            widthNorm = 0.35f,
            heightNorm = 0.08f,
            text = text,
            textColor = color
        )

        _elements.update { it + newItem }
        _selectedElementId.value = newItem.id
        _isTextDialogVisible.value = false
    }

    fun addDateElement(formatPattern: String = "dd/MM/yyyy", color: Color = Color.Black) {
        val sdf = SimpleDateFormat(formatPattern, Locale.getDefault())
        val dateText = sdf.format(Date())
        val activePage = _currentPageIndex.value

        val newItem = SignElementItem(
            id = UUID.randomUUID().toString(),
            type = SignElementType.DATE,
            pageIndex = activePage,
            xNorm = 0.35f,
            yNorm = 0.55f,
            widthNorm = 0.28f,
            heightNorm = 0.06f,
            text = dateText,
            textColor = color
        )

        _elements.update { it + newItem }
        _selectedElementId.value = newItem.id
    }

    fun addSymbolElement(type: SignElementType, color: Color = Color(0xFF003399)) {
        val activePage = _currentPageIndex.value
        val symbolText = if (type == SignElementType.CHECKMARK) "\u2713" else "\u2715"

        val newItem = SignElementItem(
            id = UUID.randomUUID().toString(),
            type = type,
            pageIndex = activePage,
            xNorm = 0.45f,
            yNorm = 0.5f,
            widthNorm = 0.12f,
            heightNorm = 0.08f,
            text = symbolText,
            textColor = color
        )

        _elements.update { it + newItem }
        _selectedElementId.value = newItem.id
    }

    fun selectElement(id: String?) {
        _selectedElementId.value = id
    }

    fun updateElementPosition(id: String, xNorm: Float, yNorm: Float) {
        _elements.update { list ->
            list.map { item ->
                if (item.id == id) {
                    item.copy(
                        xNorm = xNorm.coerceIn(0f, 1f - item.widthNorm),
                        yNorm = yNorm.coerceIn(0f, 1f - item.heightNorm)
                    )
                } else item
            }
        }
    }

    fun updateElementSize(id: String, widthNorm: Float, heightNorm: Float) {
        _elements.update { list ->
            list.map { item ->
                if (item.id == id) {
                    val w = widthNorm.coerceIn(0.05f, 1f - item.xNorm)
                    val h = heightNorm.coerceIn(0.02f, 1f - item.yNorm)
                    item.copy(widthNorm = w, heightNorm = h)
                } else item
            }
        }
    }

    fun duplicateElement(id: String) {
        val original = _elements.value.find { it.id == id } ?: return
        val duplicate = original.copy(
            id = UUID.randomUUID().toString(),
            xNorm = (original.xNorm + 0.04f).coerceIn(0f, 1f - original.widthNorm),
            yNorm = (original.yNorm + 0.04f).coerceIn(0f, 1f - original.heightNorm)
        )
        _elements.update { it + duplicate }
        _selectedElementId.value = duplicate.id
    }

    fun deleteElement(id: String) {
        _elements.update { list -> list.filterNot { it.id == id } }
        if (_selectedElementId.value == id) {
            _selectedElementId.value = null
        }
    }

    fun deleteSavedSignature(context: Context, item: SavedSignatureItem) {
        viewModelScope.launch(Dispatchers.IO) {
            ESignPdfService.deleteSavedSignature(item.file)
            loadSavedSignatures(context)
        }
    }

    fun burnAndFlattenPdf(context: Context) {
        val doc = _documentInfo.value ?: return
        val currentElements = _elements.value

        if (currentElements.isEmpty()) {
            _uiState.value = ESignUiState.Error("Please place at least one signature, text, or date annotation on the document before exporting.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ESignUiState.Processing("Flattening signatures into PDF...", 0.1f)

            val result = ESignPdfService.flattenAndSignPdf(
                context = context,
                sourcePdf = doc.file,
                password = doc.password,
                elements = currentElements,
                onProgress = { p ->
                    _uiState.value = ESignUiState.Processing("Burning annotations into PDF...", p)
                }
            )

            result.fold(
                onSuccess = { outputFile ->
                    _uiState.value = ESignUiState.Success(
                        outputFile = outputFile,
                        pageCount = doc.pageCount,
                        fileSize = outputFile.length()
                    )
                },
                onFailure = { error ->
                    _uiState.value = ESignUiState.Error(error.localizedMessage ?: "Failed to sign PDF")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = ESignUiState.Idle
        _documentInfo.value = null
        _currentPageIndex.value = 0
        _pageBitmap.value = null
        _elements.value = emptyList()
        _selectedElementId.value = null
    }

    fun clearAll() {
        _elements.value = emptyList()
        _selectedElementId.value = null
    }

    fun dismissSuccess() {
        val doc = _documentInfo.value
        _uiState.value = if (doc != null) ESignUiState.DocumentLoaded(doc) else ESignUiState.Idle
    }
}
