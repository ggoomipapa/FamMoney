package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.repository.BillingRepository
import com.ezcorp.fammoney.util.AIFeatureConfig
import com.ezcorp.fammoney.util.DebugConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 기능 서비스
 * 커넥트 AI 구독용 기능을 제공
 * Firebase Remote Config를 통해 무료/유료 기능을 동적으로 관리
 */
@Singleton
class AIFeatureService @Inject constructor(
    private val geminiService: GeminiService,
    private val billingRepository: BillingRepository
) {
    /**
     * AI 기능 자체 사용 가능 여부 확인
     * - 디버그 빌드: 항상 사용 가능
     * - 릴리즈 빌드: Gemini 초기화 필요
     */
    fun isAIEnabled(): Boolean {
        if (!AIFeatureConfig.isAIFeaturesEnabled()) return false
        if (DebugConfig.isDebugBuild) return true
        return geminiService.isInitialized()
    }

    /**
     * 특정 기능 사용 가능 여부 확인
     * Remote Config에서 무료/유료 여부를 동적으로 결정
     */
    fun canUseFeature(featureId: String): Boolean {
        if (!geminiService.isInitialized()) return false
        return AIFeatureConfig.canUseFeature(featureId, billingRepository.hasPaidSubscription())
    }

    /**
     * AI 기능 사용 불가 사유
     */
    fun getDisabledReason(): String {
        return when {
            !AIFeatureConfig.isAIFeaturesEnabled() -> "AI 기능이 현재 비활성화되어 있습니다."
            !geminiService.isInitialized() -> "AI 기능을 사용하려면 설정에서 Gemini API 키를 입력해주세요."
            !billingRepository.hasPaidSubscription() -> "이 AI 기능은 셀머니 커넥트 구독자만 사용할 수 있습니다."
            else -> ""
        }
    }

    /**
     * 특정 기능의 사용 불가 사유
     */
    fun getFeatureDisabledReason(featureId: String): String {
        return when {
            !AIFeatureConfig.isAIFeaturesEnabled() -> "AI 기능이 현재 비활성화되어 있습니다."
            !geminiService.isInitialized() -> "AI 기능을 사용하려면 설정에서 Gemini API 키를 입력해주세요."
            AIFeatureConfig.isFeatureFree(featureId) -> "" // 무료 기능
            !billingRepository.hasPaidSubscription() -> "이 기능은 셀머니 커넥트 구독자만 사용할 수 있습니다."
            else -> ""
        }
    }

    /**
     * 구독 타입 확인
     */
    fun getSubscriptionType(): String {
        if (DebugConfig.isDebugBuild) return "forever"
        return billingRepository.getSubscriptionType()
    }

    /**
     * 무료로도 사용 가능한 AI 기능 목록
     */
    fun getFreeFeatures(): Set<String> = AIFeatureConfig.getFreeFeatures()

    /**
     * 유료 구독 시에만 사용 가능한 AI 기능 목록
     */
    fun getPaidFeatures(): Set<String> = AIFeatureConfig.getPaidFeatures()

    // ========== AI 기능 래퍼 메서드들 ==========
    // 각 메서드는 Remote Config에서 설정된 무료/유료 여부에 따라 접근 제어

    /**
     * AI 자동 카테고리 분류
     */
    suspend fun autoCategorize(
        merchantName: String,
        amount: Long,
        description: String = ""
    ): Result<AutoCategoryResult> {
        val featureId = AIFeatureConfig.Features.AUTO_CATEGORIZE
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.autoCategorize(merchantName, amount, description)
    }

    /**
     * AI 가맹점명 추출
     */
    suspend fun extractMerchantName(
        notificationText: String
    ): Result<String> {
        val featureId = AIFeatureConfig.Features.MERCHANT_EXTRACT
        if (!canUseFeature(featureId)) {
            return Result.success("") // 비활성화 시 빈 문자열
        }
        return geminiService.extractMerchantName(notificationText)
    }

    /**
     * 월말 지출 예측
     */
    suspend fun predictMonthlySpending(
        currentMonthExpense: Long,
        dayOfMonth: Int,
        daysInMonth: Int,
        categoryExpenses: Map<String, Long>,
        previousMonthsData: List<MonthlyFinancialData> = emptyList()
    ): Result<SpendingPrediction> {
        val featureId = AIFeatureConfig.Features.SPENDING_PREDICTION
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.predictMonthlySpending(
            currentMonthExpense,
            dayOfMonth,
            daysInMonth,
            categoryExpenses,
            previousMonthsData
        )
    }

    /**
     * 스마트 인사이트 생성
     */
    suspend fun generateSmartInsights(
        currentMonth: MonthlyFinancialData,
        previousMonth: MonthlyFinancialData? = null,
        savingsGoals: List<Pair<String, Long>> = emptyList()
    ): Result<List<SmartInsight>> {
        val featureId = AIFeatureConfig.Features.SMART_INSIGHTS
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.generateSmartInsights(currentMonth, previousMonth, savingsGoals)
    }

    /**
     * 이상 지출 감지
     */
    suspend fun detectAnomalies(
        currentTransaction: ParsedTransactionInfo,
        recentTransactions: List<ParsedTransactionInfo>,
        categoryAverages: Map<String, Long>
    ): Result<AnomalyResult?> {
        val featureId = AIFeatureConfig.Features.ANOMALY_DETECTION
        if (!canUseFeature(featureId)) {
            return Result.success(null) // 비활성화 시 이상 없음
        }
        return geminiService.detectAnomalies(currentTransaction, recentTransactions, categoryAverages)
    }

    /**
     * 목표 달성 예측
     */
    suspend fun predictGoalAchievement(
        goalName: String,
        targetAmount: Long,
        currentAmount: Long,
        targetDate: Long,
        averageMonthlyContribution: Long,
        recentContributions: List<Long> = emptyList()
    ): Result<GoalPrediction> {
        val featureId = AIFeatureConfig.Features.GOAL_PREDICTION
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.predictGoalAchievement(
            goalName,
            targetAmount,
            currentAmount,
            targetDate,
            averageMonthlyContribution,
            recentContributions
        )
    }

    /**
     * 중복 거래 AI 판단
     */
    suspend fun analyzeDuplicateTransaction(
        transaction1: ParsedTransactionInfo,
        transaction2: ParsedTransactionInfo,
        timeDiffMinutes: Long
    ): Result<DuplicateAnalysis> {
        val featureId = AIFeatureConfig.Features.DUPLICATE_ANALYSIS
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.analyzeDuplicateTransaction(transaction1, transaction2, timeDiffMinutes)
    }

    // ========== 기존 AI 코칭 기능 래퍼 ==========

    /**
     * 재정 분석
     */
    suspend fun analyzeFinances(
        monthlyData: List<MonthlyFinancialData>,
        savingsGoals: List<Pair<String, Long>>? = null,
        userName: String = "사용자"
    ): Result<String> {
        val featureId = AIFeatureConfig.Features.FINANCIAL_ANALYSIS
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.analyzeFinances(monthlyData, savingsGoals, userName)
    }

    /**
     * 재산 증식 분석 (Debug 전용)
     */
    suspend fun analyzeInvestment(
        monthlyBalance: Long,
        riskPreference: String,
        investmentPeriod: String
    ): Result<String> {
        val featureId = AIFeatureConfig.Features.INVESTMENT_ANALYSIS
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.analyzeInvestment(monthlyBalance, riskPreference, investmentPeriod)
    }

    /**
     * 목표 달성 코칭
     */
    suspend fun analyzeGoalProgress(
        goalName: String,
        targetAmount: Long,
        currentAmount: Long,
        targetYears: Int,
        averageMonthlyBalance: Long,
        categoryExpenses: Map<String, Long>
    ): Result<String> {
        val featureId = AIFeatureConfig.Features.GOAL_COACHING
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.analyzeGoalProgress(
            goalName,
            targetAmount,
            currentAmount,
            targetYears,
            averageMonthlyBalance,
            categoryExpenses
        )
    }

    /**
     * 금융 상품 검색 (Debug 전용)
     */
    suspend fun searchFinancialProducts(
        productType: String,
        connectedBankNames: List<String> = emptyList(),
        monthlySurplus: Long = 0L
    ): Result<String> {
        val featureId = AIFeatureConfig.Features.PRODUCT_SEARCH
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.searchFinancialProducts(productType, connectedBankNames, monthlySurplus)
    }

    /**
     * 저축 전략 (Debug 전용)
     */
    suspend fun getSavingsStrategy(
        primaryBank: String,
        monthlySurplus: Long,
        savingsGoal: String? = null,
        targetAmount: Long? = null
    ): Result<String> {
        val featureId = AIFeatureConfig.Features.SAVINGS_STRATEGY
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.getSavingsStrategy(primaryBank, monthlySurplus, savingsGoal, targetAmount)
    }

    /**
     * 재산 증식 가이드 (Debug 전용)
     */
    suspend fun getInvestmentStartGuide(
        investorProfile: String,
        riskLevel: String,
        monthlySurplus: Long,
        preferredProducts: List<String> = emptyList()
    ): Result<String> {
        val featureId = AIFeatureConfig.Features.INVESTMENT_GUIDE
        if (!canUseFeature(featureId)) {
            return Result.failure(Exception(getFeatureDisabledReason(featureId)))
        }
        return geminiService.getInvestmentStartGuide(investorProfile, riskLevel, monthlySurplus, preferredProducts)
    }
}
