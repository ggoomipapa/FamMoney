package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.service.GeminiService
import com.ezcorp.fammoney.service.MonthlyFinancialData
import com.ezcorp.fammoney.service.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AICoachingUiState(
    val isLoading: Boolean = false,
    val isApiKeySet: Boolean = false,
    val error: String? = null,
    val totalIncome: Long = 0L,
    val totalExpense: Long = 0L,
    val balance: Long = 0L,
    val categoryExpenses: Map<String, Long> = emptyMap(),
    val financialAnalysis: String? = null,
    val investmentAnalysis: String? = null,
    val goalAnalysis: String? = null,
    // 추가 분석 결과
    val productSearchResult: String? = null,
    val savingsStrategyResult: String? = null,
    val investmentGuideResult: String? = null,
    val connectedBanks: List<String> = emptyList()
)

@HiltViewModel
class AICoachingViewModel @Inject constructor(
    private val geminiService: GeminiService,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AICoachingUiState())
    val uiState: StateFlow<AICoachingUiState> = _uiState.asStateFlow()

    private var monthlyDataCache: List<MonthlyFinancialData> = emptyList()

    init {
        loadApiKey()
        loadCurrentMonthData()
    }

    private fun loadApiKey() {
        viewModelScope.launch {
            userPreferences.geminiApiKeyFlow.collect { apiKey ->
                if (apiKey.isNotBlank()) {
                    geminiService.initialize(apiKey)
                    _uiState.update { it.copy(isApiKeySet = true) }
                } else {
                    _uiState.update { it.copy(isApiKeySet = false) }
                }
            }
        }
    }

    /**
     * 연결된 은행 설정 (카드포함 가능)
     */
    fun setConnectedBanks(banks: List<String>) {
        val bankNames = banks.filter { it.isNotBlank() }
        _uiState.update { it.copy(connectedBanks = bankNames) }
        geminiService.setConnectedBanks(bankNames)
    }

    private fun loadCurrentMonthData() {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1

            transactionRepository.getTransactionsByMonth(groupId, year, month).collect { transactions ->
                var totalIncome = 0L
                var totalExpense = 0L
                val categoryMap = mutableMapOf<String, Long>()

                transactions.forEach { transaction ->
                    if (transaction.type == com.ezcorp.fammoney.data.model.TransactionType.INCOME) {
                        totalIncome += transaction.amount
                    } else {
                        totalExpense += transaction.amount
                        val category = transaction.category.ifBlank { "미분류" }
                        categoryMap[category] = (categoryMap[category] ?: 0L) + transaction.amount
                    }
                }

                _uiState.update {
                    it.copy(
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        balance = totalIncome - totalExpense,
                        categoryExpenses = categoryMap
                    )
                }

                // 월별 데이터 캐시에 저장
                monthlyDataCache = listOf(
                    MonthlyFinancialData(
                        year = year,
                        month = month,
                        totalIncome = totalIncome,
                        totalExpense = totalExpense,
                        balance = totalIncome - totalExpense,
                        categoryExpenses = categoryMap,
                        topMerchants = emptyList()
                    )
                )
            }
        }
    }

    fun refresh() {
        loadCurrentMonthData()
        _uiState.update {
            it.copy(
                financialAnalysis = null,
                investmentAnalysis = null,
                goalAnalysis = null,
                error = null
            )
        }
    }

    fun analyzeFinances() {
        if (!geminiService.isInitialized()) {
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = geminiService.analyzeFinances(
                monthlyData = monthlyDataCache,
                savingsGoals = null,
                userName = "사용자"
            )

            result.fold(
                onSuccess = { analysis ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            financialAnalysis = analysis
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    fun analyzeInvestment(riskPreference: String, investmentPeriod: String) {
        if (!geminiService.isInitialized()) {
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = geminiService.analyzeInvestment(
                monthlyBalance = _uiState.value.balance,
                riskPreference = riskPreference,
                investmentPeriod = investmentPeriod
            )

            result.fold(
                onSuccess = { analysis ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            investmentAnalysis = analysis
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    fun analyzeGoalProgress(goalName: String, targetAmount: Long, targetYears: Int) {
        if (!geminiService.isInitialized()) {
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = geminiService.analyzeGoalProgress(
                goalName = goalName,
                targetAmount = targetAmount,
                currentAmount = 0L,
                targetYears = targetYears,
                averageMonthlyBalance = _uiState.value.balance,
                categoryExpenses = _uiState.value.categoryExpenses
            )

            result.fold(
                onSuccess = { analysis ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            goalAnalysis = analysis
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    /**
     * 금융상품 검색 (예금, CMA, ETF 등)
     */
    fun searchFinancialProducts(productType: String) {
        if (!geminiService.isInitialized()) {
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, productSearchResult = null) }

            val result = geminiService.searchFinancialProducts(
                productType = productType,
                connectedBankNames = _uiState.value.connectedBanks,
                monthlySurplus = _uiState.value.balance.coerceAtLeast(0)
            )

            result.fold(
                onSuccess = { searchResult ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            productSearchResult = searchResult
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    /**
     * 저축전략 맞춤형 안내 제공
     */
    fun getSavingsStrategy(savingsGoal: String? = null, targetAmount: Long? = null) {
        if (!geminiService.isInitialized()) {
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        val primaryBank = _uiState.value.connectedBanks.firstOrNull() ?: "미지정"

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, savingsStrategyResult = null) }

            val result = geminiService.getSavingsStrategy(
                primaryBank = primaryBank,
                monthlySurplus = _uiState.value.balance.coerceAtLeast(0),
                savingsGoal = savingsGoal,
                targetAmount = targetAmount
            )

            result.fold(
                onSuccess = { strategy ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            savingsStrategyResult = strategy
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    /**
     * 투자 시작 가이드 제공
     */
    fun getInvestmentGuide(
        investorProfile: String,
        riskLevel: String,
        preferredProducts: List<String> = emptyList()
    ) {
        if (!geminiService.isInitialized()) {
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, investmentGuideResult = null) }

            val result = geminiService.getInvestmentStartGuide(
                investorProfile = investorProfile,
                riskLevel = riskLevel,
                monthlySurplus = _uiState.value.balance.coerceAtLeast(0),
                preferredProducts = preferredProducts
            )

            result.fold(
                onSuccess = { guide ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            investmentGuideResult = guide
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    /**
     * 분석결과 초기화
     */
    fun clearResults() {
        _uiState.update {
            it.copy(
                productSearchResult = null,
                savingsStrategyResult = null,
                investmentGuideResult = null,
                error = null
            )
        }
    }
}
