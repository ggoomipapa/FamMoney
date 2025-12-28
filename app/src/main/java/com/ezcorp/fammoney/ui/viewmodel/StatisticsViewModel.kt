package com.ezcorp.fammoney.ui.viewmodel

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Merchant
import com.ezcorp.fammoney.data.model.SpendingCategory
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.service.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CategoryStat(
    val categoryId: String,
    val icon: String,
    val name: String,
    val amount: Long,
    val percentage: Float,
    val color: Color
)

data class MerchantStat(
    val merchantId: String,
    val icon: String,
    val name: String,
    val amount: Long,
    val percentage: Float,
    val count: Int
)

data class StatisticsUiState(
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val isYearlyMode: Boolean = false,
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val categoryStats: List<CategoryStat> = emptyList(),
    val merchantStats: List<MerchantStat> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val categoryColors = listOf(
        Color(0xFF00BFA5),  // 
Color(0xFF1DE9B6),  // ë¯¼í¸
        Color(0xFF00C853),  // ì´ë¡
        Color(0xFFFFB300),  // ?¸ë
        Color(0xFFFF7043),  // ?¤ë ì§
        Color(0xFFE91E63),  // ?í¬
        Color(0xFF9C27B0),  // ë³´ë¼
        Color(0xFF3F51B5),  // ?¸ëê³?        Color(0xFF2196F3),  // ?ë
        Color(0xFF00BCD4),  // ?ì
    )

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val groupId = userPreferences.getGroupId()
                Log.d("StatisticsViewModel", "loadStatistics: groupId=$groupId")
                if (groupId == null) {
                    Log.e("StatisticsViewModel", "groupId is null, returning")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                // ê¸°ê°???°ë¼ ê±°ë ?´ì­ ì¡°í
                val year = _uiState.value.selectedYear
                val month = _uiState.value.selectedMonth
                val isYearly = _uiState.value.isYearlyMode
                Log.d("StatisticsViewModel", "loadStatistics: year=$year, month=$month, isYearly=$isYearly")

                val transactions = if (isYearly) {
                    transactionRepository.getTransactionsByYear(groupId, year)
                } else {
                    transactionRepository.getTransactionsByMonthForStats(groupId, year, month)
                }
                Log.d("StatisticsViewModel", "loadStatistics: transactions count=${transactions.size}")

                // ì´ì¡ ê³ì°
                val totalIncome = transactions
                    .filter { it.type == TransactionType.INCOME }
                    .sumOf { it.amount }

                val totalExpense = transactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount }

                // ?ë¹? íë³??µê³
                val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
                val categoryGroups = expenseTransactions
                    .groupBy { it.category.ifBlank { "UNCATEGORIZED" } }
                    .mapValues { (_, txList) -> txList.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }

                val categoryStats = categoryGroups.mapIndexed { index, (categoryId, amount) ->
                    val category = try {
                        SpendingCategory.valueOf(categoryId)
                    } catch (e: Exception) {
                        SpendingCategory.UNCATEGORIZED
                    }
                    CategoryStat(
                        categoryId = categoryId,
                        icon = category.icon,
                        name = category.displayName,
                        amount = amount,
                        percentage = if (totalExpense > 0) (amount.toFloat() / totalExpense * 100) else 0f,
                        color = categoryColors[index % categoryColors.size]
                    )
                }

                // ?¬ì©ì²ë³ ?µê³
                val merchantGroups = expenseTransactions
                    .filter { it.merchant.isNotBlank() || it.merchantName.isNotBlank() }
                    .groupBy { it.merchant.ifBlank { it.merchantName } }

                val merchantStats = merchantGroups
                    .map { (merchantId, txList) ->
                        val merchant = Merchant.getDefaultMerchants().find { it.id == merchantId }
                        val name = merchant?.displayName ?: txList.firstOrNull()?.merchantName ?: merchantId
                        val icon = merchant?.icon ?: "?"
                        val amount = txList.sumOf { it.amount }
                        MerchantStat(
                            merchantId = merchantId,
                            icon = icon,
                            name = name,
                            amount = amount,
                            percentage = if (totalExpense > 0) (amount.toFloat() / totalExpense * 100) else 0f,
                            count = txList.size
                        )
                    }
                    .sortedByDescending { it.amount }

                _uiState.value = _uiState.value.copy(
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    categoryStats = categoryStats,
                    merchantStats = merchantStats,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("StatisticsViewModel", "loadStatistics error", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun previousYear() {
        _uiState.value = _uiState.value.copy(selectedYear = _uiState.value.selectedYear - 1)
        loadStatistics()
    }

    fun nextYear() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (_uiState.value.selectedYear < currentYear) {
            _uiState.value = _uiState.value.copy(selectedYear = _uiState.value.selectedYear + 1)
            loadStatistics()
        }
    }

    fun setPeriodMode(isYearly: Boolean) {
        _uiState.value = _uiState.value.copy(isYearlyMode = isYearly)
        loadStatistics()
    }

    fun previousMonth() {
        val currentState = _uiState.value
        if (currentState.selectedMonth == 1) {
            _uiState.value = currentState.copy(
                selectedYear = currentState.selectedYear - 1,
                selectedMonth = 12
            )
        } else {
            _uiState.value = currentState.copy(
                selectedMonth = currentState.selectedMonth - 1
            )
        }
        loadStatistics()
    }

    fun nextMonth() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val state = _uiState.value

        // ?ì¬ ?ë³´??ë¯¸ëë¡ë ?´ë ë¶ê"
        if (state.selectedYear == currentYear && state.selectedMonth >= currentMonth) {
            return
        }

        if (state.selectedMonth == 12) {
            _uiState.value = state.copy(
                selectedYear = state.selectedYear + 1,
                selectedMonth = 1
            )
        } else {
            _uiState.value = state.copy(
                selectedMonth = state.selectedMonth + 1
            )
        }
        loadStatistics()
    }
}
