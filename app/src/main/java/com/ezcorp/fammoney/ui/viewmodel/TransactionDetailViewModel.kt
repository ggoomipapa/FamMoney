package com.ezcorp.fammoney.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.PriceHistory
import com.ezcorp.fammoney.data.model.ReceiptItem
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.repository.ReceiptRepository
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.service.ParsedReceiptItem
import com.ezcorp.fammoney.service.ReceiptOcrService
import com.ezcorp.fammoney.service.UserPreferences
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val receiptRepository: ReceiptRepository,
    private val receiptOcrService: ReceiptOcrService,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _transaction.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    private val _merchantSuggestions = MutableStateFlow<List<String>>(emptyList())
    val merchantSuggestions: StateFlow<List<String>> = _merchantSuggestions.asStateFlow()

    private val _receiptItems = MutableStateFlow<List<ParsedReceiptItem>>(emptyList())
    val receiptItems: StateFlow<List<ParsedReceiptItem>> = _receiptItems.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    init {
        loadMerchantSuggestions()
    }

    private fun loadMerchantSuggestions() {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch
            val merchants = transactionRepository.getUniqueMerchantNames(groupId)
            _merchantSuggestions.value = merchants
        }
    }

    fun loadTransaction(transactionId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = transactionRepository.getTransactionById(transactionId)
                _transaction.value = result

                // 저장된 영수증 항목 로드
                if (result != null) {
                    loadReceiptItems(transactionId)
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadReceiptItems(transactionId: String) {
        viewModelScope.launch {
            val items = receiptRepository.getReceiptItemsByTransaction(transactionId)
            _receiptItems.value = items.map { item ->
                ParsedReceiptItem(
                    name = item.itemName,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    totalPrice = item.totalPrice,
                    rawText = item.rawText
                )
            }
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                transactionRepository.updateTransaction(transaction)

                // 영수증 항목이 있으면 저장
                val items = _receiptItems.value
                if (items.isNotEmpty()) {
                    saveReceiptItems(transaction.id, items, transaction.merchantName)
                }

                _isSaved.value = true
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun saveReceiptItems(
        transactionId: String,
        items: List<ParsedReceiptItem>,
        merchantName: String
    ) {
        val groupId = userPreferences.getGroupId() ?: return

        // 기존 항목 삭제
        receiptRepository.deleteReceiptItemsByTransaction(transactionId)

        // 새 항목 저장
        val receiptItems = items.map { item ->
            ReceiptItem(
                transactionId = transactionId,
                groupId = groupId,
                itemName = item.name,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                totalPrice = item.totalPrice,
                rawText = item.rawText
            )
        }
        receiptRepository.addReceiptItems(receiptItems)

        // 가격 이력 저장 (가격 변동 추적용)
        val priceHistories = items.map { item ->
            PriceHistory(
                groupId = groupId,
                itemName = item.name,
                price = item.unitPrice,
                merchantName = merchantName,
                purchaseDate = _transaction.value?.transactionDate ?: Timestamp.now()
            )
        }
        receiptRepository.addPriceHistoryBatch(priceHistories)
    }

    /**
     * 이미지 URI에서 영수증 스캔
     */
    fun scanReceiptFromUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            try {
                val result = receiptOcrService.recognizeText(uri, context)
                _receiptItems.value = result.items
                if (result.items.isEmpty()) {
                    _scanError.value = "영수증에서 항목을 찾을 수 없습니다"
                }
            } catch (e: Exception) {
                _scanError.value = "스캔 실패: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * 비트맵에서 영수증 스캔
     */
    fun scanReceiptFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            try {
                val result = receiptOcrService.recognizeText(bitmap)
                _receiptItems.value = result.items
                if (result.items.isEmpty()) {
                    _scanError.value = "영수증에서 항목을 찾을 수 없습니다"
                }
            } catch (e: Exception) {
                _scanError.value = "스캔 실패: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearReceiptItems() {
        _receiptItems.value = emptyList()
    }

    fun clearScanError() {
        _scanError.value = null
    }
}
