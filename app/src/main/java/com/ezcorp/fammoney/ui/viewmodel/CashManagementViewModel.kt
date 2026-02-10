package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.InputSource
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CashManagementUiState(
    val isLoading: Boolean = true,
    val transactions: List<Transaction> = emptyList(),
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val totalIncome: Long = 0,
    val totalExpense: Long = 0
)

@HiltViewModel
class CashManagementViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "CashMgmtVM"
    }

    private val _uiState = MutableStateFlow(CashManagementUiState())
    val uiState: StateFlow<CashManagementUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var currentGroupId: String? = null

    init {
        AppLogger.i(TAG, "ViewModel 초기화")
        loadUserInfo()
    }

    private fun loadUserInfo() {
        AppLogger.d(TAG, "사용자 정보 로드 시작")
        viewModelScope.launch {
            currentUserId = userPreferences.getUserId()
            currentUserName = userPreferences.getUserName()
            currentGroupId = userPreferences.getGroupId()
            AppLogger.d(TAG, "사용자 정보 로드 완료: userId=$currentUserId, groupId=$currentGroupId")
            loadTransactions()
        }
    }

    private fun loadTransactions() {
        val groupId = currentGroupId ?: run {
            AppLogger.w(TAG, "현금 거래 로드 중단: groupId 없음")
            return
        }

        viewModelScope.launch {
            val state = _uiState.value
            // ?ê¸ ê±°ëë§??í°ë§?(source = MANUAL_ENTRY?´ê³  bankIdê° "CASH"??ê±°ë)
            transactionRepository.getTransactionsByMonth(
                groupId,
                state.currentYear,
                state.currentMonth
            ).collect { allTransactions ->
                val cashTransactions = allTransactions.filter { it.bankId == "CASH" }
                AppLogger.dataLoaded(TAG, "현금 거래", cashTransactions.size, "전체=${allTransactions.size}")

                val totalIncome = cashTransactions
                    .filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amount }

                val totalExpense = cashTransactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    transactions = cashTransactions,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense
                )
            }
        }
    }

    fun addTransaction(
        type: TransactionType,
        amount: Long,
        description: String,
        memo: String
    ) {
        AppLogger.userAction(TAG, "현금 거래 추가", "type=$type, amount=$amount, desc=$description")
        viewModelScope.launch {
            val userId = currentUserId ?: run {
                AppLogger.w(TAG, "현금 거래 추가 실패: userId 없음")
                return@launch
            }
            val userName = currentUserName ?: ""
            val groupId = currentGroupId ?: run {
                AppLogger.w(TAG, "현금 거래 추가 실패: groupId 없음")
                return@launch
            }

            val transaction = Transaction(
                groupId = groupId,
                userId = userId,
                userName = userName,
                type = type,
                amount = amount,
                bankId = "CASH",  // ?ê¸ ê±°ë ?ë³
bankName = "?ê¸",
                description = description,
                memo = memo,
                source = InputSource.MANUAL_ENTRY,
                transactionDate = Timestamp.now(),
                isConfirmed = true
            )

            transactionRepository.addTransaction(transaction)
            AppLogger.apiSuccess(TAG, "addTransaction", "현금 거래 추가 완료: $type, $amount")
        }
    }

    fun deleteTransaction(transactionId: String) {
        AppLogger.userAction(TAG, "현금 거래 삭제", "transactionId=$transactionId")
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transactionId)
            AppLogger.apiSuccess(TAG, "deleteTransaction", "거래 삭제 완료: $transactionId")
        }
    }

    fun previousMonth() {
        AppLogger.userAction(TAG, "이전 달 이동")
        val state = _uiState.value
        var newYear = state.currentYear
        var newMonth = state.currentMonth - 1
        if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }
        _uiState.value = state.copy(currentYear = newYear, currentMonth = newMonth)
        loadTransactions()
    }

    fun nextMonth() {
        AppLogger.userAction(TAG, "다음 달 이동")
        val state = _uiState.value
        var newYear = state.currentYear
        var newMonth = state.currentMonth + 1
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }
        _uiState.value = state.copy(currentYear = newYear, currentMonth = newMonth)
        loadTransactions()
    }
}
