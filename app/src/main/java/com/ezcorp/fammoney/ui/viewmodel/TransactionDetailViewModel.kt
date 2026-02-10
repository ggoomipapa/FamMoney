package com.ezcorp.fammoney.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.PriceHistory
import com.ezcorp.fammoney.data.model.ReceiptItem
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.repository.LearnedMerchantRuleRepository
import com.ezcorp.fammoney.data.repository.ReceiptRepository
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.service.ParsedReceiptItem
import com.ezcorp.fammoney.service.ReceiptOcrService
import com.ezcorp.fammoney.service.SmartCategorizationService
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
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
    private val userPreferences: UserPreferences,
    private val smartCategorizationService: SmartCategorizationService,
    private val learnedMerchantRuleRepository: LearnedMerchantRuleRepository
) : ViewModel() {

    companion object {
        private const val TAG = "TxDetailVM"
    }

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
        AppLogger.i(TAG, "ViewModel 초기화")
        loadMerchantSuggestions()
    }

    private fun loadMerchantSuggestions() {
        AppLogger.d(TAG, "사용처 추천 목록 로드")
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch
            val merchants = transactionRepository.getUniqueMerchantNames(groupId)
            AppLogger.dataLoaded(TAG, "사용처 추천", merchants.size)
            _merchantSuggestions.value = merchants
        }
    }

    fun loadTransaction(transactionId: String) {
        AppLogger.d(TAG, "거래 상세 로드: transactionId=$transactionId")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = transactionRepository.getTransactionById(transactionId)
                AppLogger.d(TAG, "거래 상세 로드 결과: found=${result != null}, amount=${result?.amount}, merchant=${result?.merchantName}")
                _transaction.value = result

                // 저장된 영수증 항목 로드
                if (result != null) {
                    loadReceiptItems(transactionId)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "거래 상세 로드 실패: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadReceiptItems(transactionId: String) {
        AppLogger.d(TAG, "영수증 항목 로드: transactionId=$transactionId")
        viewModelScope.launch {
            val items = receiptRepository.getReceiptItemsByTransaction(transactionId)
            AppLogger.dataLoaded(TAG, "영수증 항목", items.size)
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
        AppLogger.userAction(TAG, "거래 수정", "id=${transaction.id}, amount=${transaction.amount}, merchant=${transaction.merchantName}, category=${transaction.category}")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val originalTransaction = _transaction.value
                transactionRepository.updateTransaction(transaction)

                // 영수증 항목이 있으면 저장
                val items = _receiptItems.value
                if (items.isNotEmpty()) {
                    saveReceiptItems(transaction.id, items, transaction.merchantName)
                }

                val groupId = userPreferences.getGroupId()
                val oldMerchantName = originalTransaction?.merchantName
                val newMerchantName = transaction.merchantName

                // 사용처 변경 시 처리
                if (groupId != null &&
                    newMerchantName.isNotBlank() &&
                    oldMerchantName != newMerchantName
                ) {
                    // 1. 학습 저장 (원본 알림 → 사용처 매핑) - 미래 거래에 적용
                    if (transaction.originalText.isNotBlank()) {
                        learnedMerchantRuleRepository.learnFromUserCorrection(
                            groupId = groupId,
                            originalText = transaction.originalText,
                            pkg = null,
                            confirmedMerchant = newMerchantName
                        )
                    }

                    // 2. 기존 거래 일괄 업데이트 - 같은 사용처를 가진 과거 거래들도 변경
                    if (!oldMerchantName.isNullOrBlank()) {
                        val updatedCount = transactionRepository.updateMerchantNameBatch(
                            groupId = groupId,
                            oldMerchantName = oldMerchantName,
                            newMerchantName = newMerchantName,
                            excludeTransactionId = transaction.id
                        )
                        if (updatedCount > 0) {
                            android.util.Log.d("TransactionDetail", "일괄 업데이트: $oldMerchantName → $newMerchantName ($updatedCount 건)")
                        }
                    }
                }

                // 사용처와 카테고리 매핑 학습
                if (transaction.merchantName.isNotBlank() && transaction.category.isNotBlank()) {
                    if (groupId != null) {
                        smartCategorizationService.learn(
                            groupId = groupId,
                            merchantName = transaction.merchantName,
                            category = transaction.category,
                            transactionType = transaction.type.name
                        )
                    }
                }

                AppLogger.apiSuccess(TAG, "updateTransaction", "거래 수정 완료: ${transaction.id}")
                _isSaved.value = true
            } catch (e: Exception) {
                AppLogger.e(TAG, "거래 수정 실패: ${e.message}", e)
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
        AppLogger.userAction(TAG, "영수증 스캔 (URI)", "uri=$uri")
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            try {
                val result = receiptOcrService.recognizeText(uri, context)
                AppLogger.d(TAG, "영수증 스캔 결과: items=${result.items.size}")
                _receiptItems.value = result.items
                if (result.items.isEmpty()) {
                    AppLogger.w(TAG, "영수증 스캔: 항목 없음")
                    _scanError.value = "영수증에서 항목을 찾을 수 없습니다"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "영수증 스캔(URI) 실패: ${e.message}", e)
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
        AppLogger.userAction(TAG, "영수증 스캔 (Bitmap)", "size=${bitmap.width}x${bitmap.height}")
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            try {
                val result = receiptOcrService.recognizeText(bitmap)
                AppLogger.d(TAG, "영수증 스캔(Bitmap) 결과: items=${result.items.size}")
                _receiptItems.value = result.items
                if (result.items.isEmpty()) {
                    AppLogger.w(TAG, "영수증 스캔(Bitmap): 항목 없음")
                    _scanError.value = "영수증에서 항목을 찾을 수 없습니다"
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "영수증 스캔(Bitmap) 실패: ${e.message}", e)
                _scanError.value = "스캔 실패: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearReceiptItems() {
        AppLogger.d(TAG, "영수증 항목 초기화")
        _receiptItems.value = emptyList()
    }

    fun clearScanError() {
        _scanError.value = null
    }
}
