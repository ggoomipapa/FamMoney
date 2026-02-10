package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.service.GeminiService
import com.ezcorp.fammoney.service.MonthlyFinancialData
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AICoachingUiState(
    val isLoading: Boolean = false,
    val isApiKeySet: Boolean = false,
    val isPremiumUser: Boolean = false,  // 구독자 여부
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

    companion object {
        private const val TAG = "AICoachingVM"
    }

    private val _uiState = MutableStateFlow(AICoachingUiState())
    val uiState: StateFlow<AICoachingUiState> = _uiState.asStateFlow()

    private var monthlyDataCache: List<MonthlyFinancialData> = emptyList()

    init {
        AppLogger.i(TAG, "ViewModel 초기화")
        initializeAI()
        loadCurrentMonthData()
    }

    /**
     * Remote Config에서 API 키를 가져와 AI 서비스 초기화
     * 구독자만 AI 기능 사용 가능
     */
    private fun initializeAI() {
        AppLogger.d(TAG, "AI 서비스 초기화 시작")
        viewModelScope.launch {
            // Remote Config에서 최신 설정 가져오기
            com.ezcorp.fammoney.util.AIFeatureConfig.fetchAndActivate()

            // API 키로 GeminiService 초기화
            val isInitialized = geminiService.initializeFromRemoteConfig()
            AppLogger.d(TAG, "GeminiService 초기화 결과: isInitialized=$isInitialized")

            // 디버그 빌드에서는 항상 프리미엄 취급
            val isPremium = com.ezcorp.fammoney.util.DebugConfig.isDebugBuild ||
                            checkSubscriptionStatus()
            AppLogger.d(TAG, "프리미엄 상태: isPremium=$isPremium")

            _uiState.update {
                it.copy(
                    isApiKeySet = isInitialized,
                    isPremiumUser = isPremium
                )
            }
        }
    }

    /**
     * 구독 상태 확인 (그룹의 subscriptionType 체크)
     */
    private suspend fun checkSubscriptionStatus(): Boolean {
        val groupId = userPreferences.getGroupId() ?: return false
        // TODO: UserRepository에서 그룹의 subscriptionType 확인
        // 현재는 디버그 모드에서 true 반환
        return false
    }

    /**
     * 연결된 은행 설정 (카드포함 가능)
     */
    fun setConnectedBanks(banks: List<String>) {
        val bankNames = banks.filter { it.isNotBlank() }
        AppLogger.d(TAG, "연결된 은행 설정: ${bankNames.joinToString()}, count=${bankNames.size}")
        _uiState.update { it.copy(connectedBanks = bankNames) }
        geminiService.setConnectedBanks(bankNames)
    }

    private fun loadCurrentMonthData() {
        AppLogger.d(TAG, "이번 달 데이터 로드 시작")
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: run {
                AppLogger.w(TAG, "groupId가 null - 데이터 로드 중단")
                return@launch
            }
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            AppLogger.d(TAG, "데이터 조회: groupId=$groupId, year=$year, month=$month")

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

                AppLogger.dataLoaded(TAG, "이번 달 거래", transactions.size, "수입=$totalIncome, 지출=$totalExpense, 잔액=${totalIncome - totalExpense}")

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
        AppLogger.userAction(TAG, "새로고침")
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
        AppLogger.userAction(TAG, "재무 분석 요청")
        if (!geminiService.isInitialized()) {
            AppLogger.w(TAG, "재무 분석 실패: API 키 미설정")
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            AppLogger.apiStart(TAG, "analyzeFinances", "monthlyDataCache.size=${monthlyDataCache.size}")

            val result = geminiService.analyzeFinances(
                monthlyData = monthlyDataCache,
                savingsGoals = null,
                userName = "사용자"
            )

            result.fold(
                onSuccess = { analysis ->
                    AppLogger.apiSuccess(TAG, "analyzeFinances", "분석 결과 길이=${analysis.length}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            financialAnalysis = analysis
                        )
                    }
                },
                onFailure = { error ->
                    AppLogger.apiError(TAG, "analyzeFinances", error.message ?: "Unknown error")
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
        AppLogger.userAction(TAG, "투자 분석 요청", "risk=$riskPreference, period=$investmentPeriod")
        if (!geminiService.isInitialized()) {
            AppLogger.w(TAG, "투자 분석 실패: API 키 미설정")
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            AppLogger.apiStart(TAG, "analyzeInvestment", "balance=${_uiState.value.balance}")

            val result = geminiService.analyzeInvestment(
                monthlyBalance = _uiState.value.balance,
                riskPreference = riskPreference,
                investmentPeriod = investmentPeriod
            )

            result.fold(
                onSuccess = { analysis ->
                    AppLogger.apiSuccess(TAG, "analyzeInvestment", "결과 길이=${analysis.length}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            investmentAnalysis = analysis
                        )
                    }
                },
                onFailure = { error ->
                    AppLogger.apiError(TAG, "analyzeInvestment", error.message ?: "Unknown error")
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
        AppLogger.userAction(TAG, "목표 달성 분석 요청", "goal=$goalName, target=$targetAmount, years=$targetYears")
        if (!geminiService.isInitialized()) {
            AppLogger.w(TAG, "목표 분석 실패: API 키 미설정")
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            AppLogger.apiStart(TAG, "analyzeGoalProgress", "goalName=$goalName, targetAmount=$targetAmount")

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
                    AppLogger.apiSuccess(TAG, "analyzeGoalProgress", "결과 길이=${analysis.length}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            goalAnalysis = analysis
                        )
                    }
                },
                onFailure = { error ->
                    AppLogger.apiError(TAG, "analyzeGoalProgress", error.message ?: "Unknown error")
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
        AppLogger.userAction(TAG, "금융상품 검색", "productType=$productType")
        if (!geminiService.isInitialized()) {
            AppLogger.w(TAG, "금융상품 검색 실패: API 키 미설정")
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, productSearchResult = null) }
            AppLogger.apiStart(TAG, "searchFinancialProducts", "type=$productType, banks=${_uiState.value.connectedBanks}")

            val result = geminiService.searchFinancialProducts(
                productType = productType,
                connectedBankNames = _uiState.value.connectedBanks,
                monthlySurplus = _uiState.value.balance.coerceAtLeast(0)
            )

            result.fold(
                onSuccess = { searchResult ->
                    AppLogger.apiSuccess(TAG, "searchFinancialProducts", "결과 길이=${searchResult.length}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            productSearchResult = searchResult
                        )
                    }
                },
                onFailure = { error ->
                    AppLogger.apiError(TAG, "searchFinancialProducts", error.message ?: "Unknown error")
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
        AppLogger.userAction(TAG, "저축전략 요청", "goal=$savingsGoal, target=$targetAmount")
        if (!geminiService.isInitialized()) {
            AppLogger.w(TAG, "저축전략 요청 실패: API 키 미설정")
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        val primaryBank = _uiState.value.connectedBanks.firstOrNull() ?: "미지정"

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, savingsStrategyResult = null) }
            AppLogger.apiStart(TAG, "getSavingsStrategy", "bank=$primaryBank, surplus=${_uiState.value.balance}")

            val result = geminiService.getSavingsStrategy(
                primaryBank = primaryBank,
                monthlySurplus = _uiState.value.balance.coerceAtLeast(0),
                savingsGoal = savingsGoal,
                targetAmount = targetAmount
            )

            result.fold(
                onSuccess = { strategy ->
                    AppLogger.apiSuccess(TAG, "getSavingsStrategy", "결과 길이=${strategy.length}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            savingsStrategyResult = strategy
                        )
                    }
                },
                onFailure = { error ->
                    AppLogger.apiError(TAG, "getSavingsStrategy", error.message ?: "Unknown error")
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
        AppLogger.userAction(TAG, "투자 가이드 요청", "profile=$investorProfile, risk=$riskLevel, products=$preferredProducts")
        if (!geminiService.isInitialized()) {
            AppLogger.w(TAG, "투자 가이드 실패: API 키 미설정")
            _uiState.update { it.copy(error = "API 키가 설정되지 않았습니다") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, investmentGuideResult = null) }
            AppLogger.apiStart(TAG, "getInvestmentGuide", "profile=$investorProfile, risk=$riskLevel")

            val result = geminiService.getInvestmentStartGuide(
                investorProfile = investorProfile,
                riskLevel = riskLevel,
                monthlySurplus = _uiState.value.balance.coerceAtLeast(0),
                preferredProducts = preferredProducts
            )

            result.fold(
                onSuccess = { guide ->
                    AppLogger.apiSuccess(TAG, "getInvestmentGuide", "결과 길이=${guide.length}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            investmentGuideResult = guide
                        )
                    }
                },
                onFailure = { error ->
                    AppLogger.apiError(TAG, "getInvestmentGuide", error.message ?: "Unknown error")
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
        AppLogger.d(TAG, "분석 결과 초기화")
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
