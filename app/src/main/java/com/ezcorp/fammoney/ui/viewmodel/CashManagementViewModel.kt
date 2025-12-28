package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.InputSource
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.service.UserPreferences
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

    private val _uiState = MutableStateFlow(CashManagementUiState())
    val uiState: StateFlow<CashManagementUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var currentGroupId: String? = null

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            currentUserId = userPreferences.getUserId()
            currentUserName = userPreferences.getUserName()
            currentGroupId = userPreferences.getGroupId()
            loadTransactions()
        }
    }

    private fun loadTransactions() {
        val groupId = currentGroupId ?: return

        viewModelScope.launch {
            val state = _uiState.value
            // ?ê¸ ê±°ëë§??í°ë§?(source = MANUAL_ENTRY?´ê³  bankIdê° "CASH"??ê±°ë)
            transactionRepository.getTransactionsByMonth(
                groupId,
                state.currentYear,
                state.currentMonth
            ).collect { allTransactions ->
                val cashTransactions = allTransactions.filter { it.bankId == "CASH" }

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
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            val userName = currentUserName ?: ""
            val groupId = currentGroupId ?: return@launch

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
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transactionId)
        }
    }

    fun previousMonth() {
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
