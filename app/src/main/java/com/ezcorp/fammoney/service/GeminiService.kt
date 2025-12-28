package com.ezcorp.fammoney.service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 코칭 분석 결과
 */
data class AICoachingResult(
    val summary: String,           // 요약
    val situation: FinancialSituation,  // 재정 상황
    val recommendations: List<String>,  // 추천 사항
    val savingsAdvice: String?,    // 저축 조언 (선택)
    val investmentAdvice: InvestmentAdvice?,  // 투자 조언 (선택)
    val cutAdvice: List<CategoryCutAdvice>?,  // 줄여야 할 카테고리 (선택)
    val goalProgress: GoalProgressAdvice?  // 목표 달성 진행 상황
)

enum class FinancialSituation {
    SURPLUS,   // 흑자 (잘하고 있음)
    DEFICIT,   // 적자 (많이 쓰고 있음)
    BALANCED   // 균형
}

data class InvestmentAdvice(
    val conservative: List<String>,  // 안전형 투자
    val moderate: List<String>,      // 중립형 투자
    val aggressive: List<String>,    // 공격형 투자
    val marketAnalysis: String,      // 시장 분석
    val recommendation: String       // 추천 투자 성향
)

data class CategoryCutAdvice(
    val category: String,
    val currentAmount: Long,
    val suggestedAmount: Long,
    val savingsAmount: Long,
    val tips: List<String>
)

data class GoalProgressAdvice(
    val goalName: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val monthsRemaining: Int,
    val requiredMonthlySaving: Long,
    val isOnTrack: Boolean,
    val advice: String
)

/**
 * 월별 재정 데이터
 */
data class MonthlyFinancialData(
    val year: Int,
    val month: Int,
    val totalIncome: Long,
    val totalExpense: Long,
    val balance: Long,
    val categoryExpenses: Map<String, Long>,  // 카테고리별 지출
    val topMerchants: List<Pair<String, Long>>  // 상위 사용처
)

/**
 * Gemini AI 서비스
 */
@Singleton
class GeminiService @Inject constructor() {

    private var generativeModel: GenerativeModel? = null

    /**
     * Remote Config에서 API 키를 가져와 자동 초기화
     * Application 시작 시 또는 Remote Config fetch 후 호출
     */
    fun initializeFromRemoteConfig(): Boolean {
        val apiKey = com.ezcorp.fammoney.util.AIFeatureConfig.getGeminiApiKey()
        if (apiKey.isBlank()) {
            generativeModel = null
            return false
        }
        initialize(apiKey)
        return true
    }

    /**
     * API 키 설정 (내부용 또는 테스트용)
     */
    fun initialize(apiKey: String) {
        if (apiKey.isBlank()) {
            generativeModel = null
            return
        }

        generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash-exp",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 40
                topP = 0.95f
                maxOutputTokens = 4096  // 더 긴 응답 허용
            }
        )
    }

    /**
     * 연결된 은행 목록 (설정에서 가져옴)
     */
    private var connectedBanks: List<String> = emptyList()

    fun setConnectedBanks(banks: List<String>) {
        connectedBanks = banks
    }

    /**
     * API 키가 설정되었는지 확인
     */
    fun isInitialized(): Boolean = generativeModel != null

    /**
     * 재정 코칭 분석 요청
     */
    suspend fun analyzeFinances(
        monthlyData: List<MonthlyFinancialData>,
        savingsGoals: List<Pair<String, Long>>? = null,  // 목표명, 목표금액
        userName: String = "사용자"
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다. 설정에서 Gemini API 키를 입력해주세요.")
        )

        try {
            val prompt = buildFinancialPrompt(monthlyData, savingsGoals ?: emptyList(), userName)
            val response = model.generateContent(prompt)
            val text = response.text ?: "분석 결과를 생성할 수 없습니다."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("AI 분석 중 오류가 발생했습니다: ${e.message}"))
        }
    }

    /**
     * 투자 추천 분석 요청
     * ⚠️ DEBUG 빌드에서만 사용 (Release에서는 UI에서 숨김)
     */
    suspend fun analyzeInvestment(
        monthlyBalance: Long,
        riskPreference: String,
        investmentPeriod: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = buildInvestmentPrompt(monthlyBalance, riskPreference, investmentPeriod)
            val response = model.generateContent(prompt)
            val text = response.text ?: "분석 결과를 생성할 수 없습니다."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("투자 분석 중 오류가 발생했습니다: ${e.message}"))
        }
    }

    /**
     * 목표 달성 코칭 요청
     */
    suspend fun analyzeGoalProgress(
        goalName: String,
        targetAmount: Long,
        currentAmount: Long,
        targetYears: Int,
        averageMonthlyBalance: Long,
        categoryExpenses: Map<String, Long>
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = buildGoalCoachingPrompt(
                goalName, targetAmount, currentAmount,
                targetYears, averageMonthlyBalance, categoryExpenses
            )
            val response = model.generateContent(prompt)
            val text = response.text ?: "분석 결과를 생성할 수 없습니다."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("목표 분석 중 오류가 발생했습니다: ${e.message}"))
        }
    }

    private fun buildFinancialPrompt(
        monthlyData: List<MonthlyFinancialData>,
        savingsGoals: List<Pair<String, Long>>,
        userName: String
    ): String {
        val dataText = monthlyData.joinToString("\n") { data ->
            """
            ${data.year}년 ${data.month}월
            - 수입: ${String.format("%,d", data.totalIncome)}원
            - 지출: ${String.format("%,d", data.totalExpense)}원
            - 잔액: ${String.format("%,d", data.balance)}원
            - 카테고리별 지출: ${data.categoryExpenses.entries.joinToString(", ") { "${it.key}: ${String.format("%,d", it.value)}원" }}
            """.trimIndent()
        }

        val goalsText = savingsGoals?.joinToString("\n") {
            "- ${it.first}: 목표 ${String.format("%,d", it.second)}원"
        } ?: "없음"

        return """
        당신은 전문 재무 코치입니다. ${userName}님의 가계부 데이터를 분석하고 맞춤형 조언을 제공해주세요.

        ## 최근 가계부 데이터
        $dataText

        ## 저축 목표
        $goalsText

        ## 분석 요청
        다음 내용을 포함하여 친근하고 실용적인 조언을 해주세요:

        1. **재정 상황 요약**: 현재 재정 상태가 어떤지 (흑자/적자/균형)

        2. **수입 대비 지출 분석**:
           - 지출 비율이 적정한지
           - 어떤 카테고리에서 많이 쓰고 있는지

        3. **맞춤 조언**:
           - 적자인 경우: 어떤 카테고리를 줄여야 하는지 구체적으로
           - 흑자인 경우: 남는 돈을 어떻게 활용하면 좋을지 (저축, 투자 등)

        4. **실행 가능한 팁**: 바로 실천할 수 있는 구체적인 절약/저축 팁 3가지

        5. **격려 메시지**: 동기부여가 되는 따뜻한 말 한마디

        응답은 한국어로, 이모지를 적절히 사용하여 친근하게 작성해주세요.
        """.trimIndent()
    }

    private fun buildInvestmentPrompt(
        monthlyBalance: Long,
        riskPreference: String,
        investmentPeriod: String
    ): String {
        return """
        당신은 금융 교육 콘텐츠 제공자입니다. 일반적인 투자 관련 정보를 교육 목적으로 안내해주세요.

        ## 사용자 정보
        - 월 투자 여력: ${String.format("%,d", monthlyBalance)}원
        - 투자 성향: $riskPreference (안전/중립/공격 중)
        - 투자 기간: $investmentPeriod (단기/장기 중)

        ## 분석 요청
        다음 내용으로 일반적인 교육 정보를 제공해주세요:

        1. **시장 상황 일반 정보**:
           - 경제 동향을 이해하는 방법
           - 금리와 투자의 관계 (일반론)

        2. **투자 유형별 특징 안내** (교육 목적):
           - 🛡️ 안전형 상품 유형: 예금, 채권 등의 일반적 특징
           - ⚖️ 중립형 상품 유형: 혼합형 상품의 일반적 특징
           - 🚀 성장형 상품 유형: 주식형 상품의 일반적 특징

        3. **상품 유형 예시**: 일반적인 금융상품 종류 소개 (특정 상품 추천 X)

        4. **투자 시 일반적 주의사항**: 교육적 관점에서 안내

        5. **분산 투자 개념**: 일반적인 자산 배분 원칙 소개

        ## 중요: 반드시 포함해야 할 면책 조항 (응답 끝에)
        ---
        ⚠️ **면책 조항**
        본 정보는 일반적인 금융 교육을 목적으로 제공되며, 특정 금융상품의 매매나 투자 권유 및 추천이 아닙니다.
        모든 투자에는 원금 손실의 위험이 있으며, 실제 투자 결정은 반드시 본인의 판단과 책임 아래 이루어져야 합니다.
        투자 결정 전 금융투자상품 판매회사의 설명을 듣거나 금융 전문가와 상담하시기 바랍니다.
        ---

        응답은 한국어로, 초보자도 이해하기 쉽게 교육적 관점에서 작성해주세요.
        """.trimIndent()
    }

    private fun buildGoalCoachingPrompt(
        goalName: String,
        targetAmount: Long,
        currentAmount: Long,
        targetYears: Int,
        averageMonthlyBalance: Long,
        categoryExpenses: Map<String, Long>
    ): String {
        val remainingAmount = targetAmount - currentAmount
        val monthsRemaining = targetYears * 12
        val requiredMonthly = if (monthsRemaining > 0) remainingAmount / monthsRemaining else remainingAmount

        val categoryText = categoryExpenses.entries
            .sortedByDescending { it.value }
            .take(5)
            .joinToString("\n") { "- ${it.key}: ${String.format("%,d", it.value)}원" }

        return """
        당신은 목표 달성 전문 코치입니다.

        ## 저축 목표 정보
        - 목표: $goalName
        - 목표 금액: ${String.format("%,d", targetAmount)}원
        - 현재 모은 금액: ${String.format("%,d", currentAmount)}원
        - 남은 금액: ${String.format("%,d", remainingAmount)}원
        - 목표 기간: ${targetYears}년 (${monthsRemaining}개월)
        - 월 필요 저축액: ${String.format("%,d", requiredMonthly)}원

        ## 현재 재정 상황
        - 월 평균 잔액: ${String.format("%,d", averageMonthlyBalance)}원
        - 상위 지출 카테고리:
        $categoryText

        ## 분석 요청
        다음을 분석하고 코칭해주세요:

        1. **목표 달성 가능성**:
           - 현재 페이스로 목표 달성이 가능한지
           - 예상 달성 시점

        2. **절약 전략**:
           - 어떤 카테고리에서 절약하면 좋을지
           - 구체적인 절약 금액 예시

        3. **실행 계획**:
           - 단계별 저축 계획
           - 중간 마일스톤 설정

        4. **동기부여**:
           - 목표 달성을 위한 격려
           - 작은 성공 축하하기

        친근하고 실용적인 조언을 이모지와 함께 작성해주세요.
        """.trimIndent()
    }

    /**
     * 실시간 금융 상품 추천 (웹 검색 기반)
     * Gemini가 학습한 최신 정보 + 은행별 맞춤 추천
     */
    suspend fun searchFinancialProducts(
        productType: String,  // "예금", "적금", "CMA", "ETF" 등
        connectedBankNames: List<String> = connectedBanks,
        monthlySurplus: Long = 0L
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = buildProductSearchPrompt(productType, connectedBankNames, monthlySurplus)
            val response = model.generateContent(prompt)
            val text = response.text ?: "상품 정보를 가져올 수 없습니다."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("상품 검색 중 오류가 발생했습니다: ${e.message}"))
        }
    }

    private fun buildProductSearchPrompt(
        productType: String,
        connectedBankNames: List<String>,
        monthlySurplus: Long
    ): String {
        val bankText = if (connectedBankNames.isNotEmpty()) {
            "사용자가 현재 사용 중인 은행: ${connectedBankNames.joinToString(", ")}"
        } else {
            "사용자가 연결한 은행 정보가 없습니다."
        }

        val surplusText = if (monthlySurplus > 0) {
            "월 투자 여력: ${String.format("%,d", monthlySurplus)}원"
        } else {
            "월 투자 여력: 미정"
        }

        return """
        당신은 금융 교육 콘텐츠 제공자입니다. $productType 상품 유형에 대한 일반적인 정보를 교육 목적으로 안내해주세요.

        ⚠️ 중요: 특정 상품을 "추천"하거나 "권유"하지 마세요. 일반적인 상품 유형과 특징만 소개하세요.

        ## 참고 정보
        - 상품 유형: $productType
        - $bankText
        - $surplusText

        ## 요청 사항

        ### 1. $productType 상품 유형 소개
        이런 유형의 상품이 있다는 정보 제공 (특정 상품 권유 X):
        - 주요 금융기관별 일반적인 상품 특징
        - 상품 유형별 일반적인 금리 예시 범위
        - 일반적인 가입 조건

        ### 2. 상품 비교 시 고려사항 안내
        어떤 점을 비교해봐야 하는지 교육:
        - 금리 비교 방법
        - 우대 조건 이해하기
        - 세금 고려사항

        ### 3. 가입 절차 일반 안내
        - 일반적인 가입 절차 (앱/온라인 등)
        - 필요 서류 일반 안내

        ### 4. 상품 유형 선택 체크리스트
        - 본인에게 맞는 상품을 고르는 방법
        - 확인해야 할 사항들

        ## 중요: 반드시 포함해야 할 면책 조항 (응답 끝에)
        ---
        ⚠️ **면책 조항**
        본 정보는 금융상품 유형에 대한 일반적인 교육 자료이며, 특정 금융상품의 매매나 권유 및 추천이 아닙니다.
        금리 및 상품 조건은 수시로 변동되므로, 실제 가입 전에 해당 금융기관에서 최신 정보를 반드시 확인하시기 바랍니다.
        금융상품 선택은 본인의 재정 상황과 목적에 맞게 신중히 결정하시기 바랍니다.
        ---

        응답은 한국어로, 교육적 관점에서 친근하게 작성해주세요.
        """.trimIndent()
    }

    /**
     * 은행별 맞춤 저축 전략
     */
    suspend fun getSavingsStrategy(
        primaryBank: String,
        monthlySurplus: Long,
        savingsGoal: String? = null,
        targetAmount: Long? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = buildSavingsStrategyPrompt(primaryBank, monthlySurplus, savingsGoal ?: "", targetAmount ?: 0L)
            val response = model.generateContent(prompt)
            val text = response.text ?: "전략을 생성할 수 없습니다."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("저축 전략 분석 중 오류가 발생했습니다: ${e.message}"))
        }
    }

    private fun buildSavingsStrategyPrompt(
        primaryBank: String,
        monthlySurplus: Long,
        savingsGoal: String,
        targetAmount: Long): String {
        val goalText = if (savingsGoal != null && targetAmount != null) {
            "저축 목표: $savingsGoal (목표액: ${String.format("%,d", targetAmount)}원)"
        } else {
            "특정 저축 목표 없음 (일반 자산 증식)"
        }

        return """
        당신은 금융 교육 콘텐츠 제공자입니다. 저축 방법에 대한 일반적인 정보를 교육 목적으로 안내해주세요.

        ⚠️ 중요: 특정 상품을 "추천"하거나 "가입하라"고 권유하지 마세요.

        ## 참고 정보
        - 사용자 주거래 은행: $primaryBank
        - 월 투자 여력: ${String.format("%,d", monthlySurplus)}원
        - $goalText

        ## 저축 전략 교육

        ### 1. 저축 상품 유형 소개
        일반적인 저축 상품 종류와 특징:
        - **파킹통장 유형**: 수시입출금 가능한 통장의 일반적 특징
        - **정기예금 유형**: 일정 기간 저축 상품의 일반적 특징
        - **특판 상품**: 특별 금리 상품이란 무엇인지 설명

        ### 2. 자금 배분 원칙 안내
        일반적인 자금 배분 방법론 (예시 비율):
        - 비상금 비율
        - 저축 비율
        - 투자 비율 (선택 포함)

        ### 3. 금융앱 활용 일반 안내
        - 자동이체 활용 방법
        - 우대 금리 조건 확인 방법
        - 앱 기능 활용법

        ### 4. 저축 보조 방법 소개
        다양한 저축 성향에 맞는 교육:
        - CMA 계좌란"
        - 저축챌린지 활용법
        - 자동저축 예산앱 개념

        ## 중요: 반드시 포함해야 할 면책 조항 (응답 끝에)
        ---
        ⚠️ **면책 조항**
        본 정보는 저축 방법에 대한 일반적인 교육 자료이며, 특정 금융상품의 매매나 권유가 아닙니다.
        금리 및 상품 조건은 금융기관과 시점에 따라 다르므로, 실제 가입 전에 해당 금융기관에서 확인하시기 바랍니다.
        ---

        친근하고 교육적인 관점으로 작성해주세요.
        """.trimIndent()
    }

    /**
     * CMA/투자 시작 가이드 (개인 맞춤)
     */
    suspend fun getInvestmentStartGuide(
        investorProfile: String,  // "초보", "경험자"
        riskLevel: String,        // "안전", "중립", "공격"
        monthlySurplus: Long,
        preferredProducts: List<String> = emptyList()  // "ETF", "주식", "펀드" 등
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = buildInvestmentGuidePrompt(investorProfile, riskLevel, monthlySurplus, preferredProducts)
            val response = model.generateContent(prompt)
            val text = response.text ?: "가이드를 생성할 수 없습니다."
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(Exception("투자 가이드 생성 중 오류가 발생했습니다: ${e.message}"))
        }
    }

    private fun buildInvestmentGuidePrompt(
        investorProfile: String,
        riskLevel: String,
        monthlySurplus: Long,
        preferredProducts: List<String>
    ): String {
        val productText = if (preferredProducts.isNotEmpty()) {
            "관심 상품 유형: ${preferredProducts.joinToString(", ")}"
        } else {
            "특별히 관심 있는 상품 유형 없음"
        }

        return """
        당신은 투자 교육 콘텐츠 제공자입니다. $investorProfile 수준의 사용자를 위한 투자 기초 교육을 제공해주세요.

        ⚠️ 중요: 특정 종목이나 상품을 "추천", "매수하라"고 권유하지 마세요. 일반적인 투자 원칙과 교육만 제공하세요.

        ## 사용자 참고 정보
        - 투자 경험 수준: $investorProfile
        - 위험 선호도: $riskLevel
        - 월 투자 고려 금액: ${String.format("%,d", monthlySurplus)}원
        - $productText

        ## 투자 교육 요청

        ### 1. 투자 시작 기초 교육
        ${if (investorProfile == "초보") {
            """
            - Step 1: 증권 계좌란? (계좌 개설 일반 절차)
            - Step 2: CMA란 무엇인가"
            - Step 3: ETF란 무엇인가? (개념 설명)
            - Step 4: 포트폴리오 개념 이해하기
            """
        } else {
            """
            - 포트폴리오 재검토 방법
            - 리밸런싱 개념
            - 다양한 투자 전략 소개
            """
        }}

        ### 2. 투자 상품 유형별 특징 교육 (추천 아님)
        - **$riskLevel 성향에 적합한 상품 유형**: 어떤 유형이 있는지 설명
        - **적립식 투자란**: 개념과 장단점 설명
        - **분산 투자 원칙**: 일반적인 자산 배분 개념

        ### 3. 투자 기초 가이드
        - 증권사별 상품 선택 시 고려사항
        - 매매 시 확인해야 할 사항
        - 수수료 구조 이해하기

        ### 4. 자금 배분 원칙 교육
        일반적인 배분 원칙 소개 (구체적 종목 X):
        - 안전자산 vs 위험자산 비율 개념
        - 비상금 확보 원칙
        - 생애주기별 배분 개념

        ### 5. 투자 전 주의사항 교육
        - 위험 관리를 이해하는 방법
        - 장기 투자 마인드셋
        - 세금 기본 개념 (국내/해외 차이)

        ## 중요: 반드시 포함해야 할 면책 조항 (응답 끝에)
        ---
        ⚠️ **면책 조항**
        본 정보는 투자에 대한 일반적인 교육 자료이며, 특정 금융투자상품의 매매나 투자 권유 및 추천이 아닙니다.
        모든 투자에는 원금 손실의 위험이 있으며, 과거 수익률이 미래 수익을 보장하지 않습니다.
        실제 투자 결정은 반드시 본인의 판단과 책임 아래 이루어져야 하며, 필요시 금융투자상품 판매회사의 설명을 듣거나 전문가와 상담하시기 바랍니다.
        ---

        친근하고 교육적인 관점으로 이해하기 쉽게 작성해주세요.
        """.trimIndent()
    }

    // ========== 커넥트 AI 활용 기능들 ==========

    /**
     * AI 자동 카테고리 분류
     * 가맹점명과 금액을 분석하여 적절한 카테고리 추천
     */
    suspend fun autoCategorize(
        merchantName: String,
        amount: Long,
        description: String = ""
    ): Result<AutoCategoryResult> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = """
            당신은 가계부 카테고리 분류 전문가입니다.

            ## 거래 정보
            - 가맹점/사용처: $merchantName
            - 금액: ${String.format("%,d", amount)}원
            - 설명: ${description.ifBlank { "없음" }}

            ## 카테고리 목록 (하나만 정확히 선택)
            식비: FOOD, CAFE_SNACK, DINING_OUT, DELIVERY, GROCERY
            생활: DAILY_NECESSITIES, HEALTH, BEAUTY, PET
            쇼핑: CLOTHING, SHOES_BAG, ELECTRONICS, ONLINE_SHOPPING
            주거: RENT, MAINTENANCE_FEE, UTILITIES, INTERNET_PHONE
            금융: LOAN, INTEREST, INSURANCE, SAVINGS, TAX
            교통: TRANSPORTATION, TAXI, CAR, PARKING
            문화: OTT, MUSIC, GAME, HOBBY, MOVIE, TRAVEL, SPORTS, BOOK
            교육: EDUCATION, ACADEMY, ONLINE_COURSE
            경조사: GIFT, FAMILY_EVENT, DONATION
            기타: ATM, TRANSFER, OTHER

            ## 응답 형식 (JSON만, 설명 없이)
            {"category": "카테고리_영문코드", "confidence": 0.0~1.0, "reason": "간단한 이유"}

            예시:
            - 스타벅스 → {"category": "CAFE_SNACK", "confidence": 0.95, "reason": "커피 전문점"}
            - 쿠팡 → {"category": "ONLINE_SHOPPING", "confidence": 0.9, "reason": "온라인쇼핑몰"}
            - GS25 → {"category": "GROCERY", "confidence": 0.85, "reason": "편의점"}
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: return@withContext Result.failure(
                Exception("분류 결과를 받지 못했습니다")
            )

            // JSON 파싱
            val jsonMatch = Regex("\\{[^}]+\\}").find(text)
            if (jsonMatch != null) {
                val json = jsonMatch.value
                val category = Regex("\"category\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "OTHER"
                val confidence = Regex("\"confidence\"\\s*:\\s*([0-9.]+)").find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 0.5f
                val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""

                Result.success(AutoCategoryResult(category, confidence, reason))
            } else {
                Result.success(AutoCategoryResult("OTHER", 0.3f, "분류 불확실"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("카테고리 분류 중 오류: ${e.message}"))
        }
    }

    /**
     * AI 가맹점명 추출 (정규식 실패 시 사용)
     */
    suspend fun extractMerchantName(
        notificationText: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = """
            다음 은행/카드 알림에서 사용처(가맹점) 이름만 추출해주세요.

            알림 내용: $notificationText

            규칙:
            - 은행명, 카드사명은 제외
            - "승인", "결제", "출금", "입금" 등 키워드는 제외
            - 금액 제외
            - 날짜/시간 제외
            - 가장 짧게 가맹점/매장 이름만 추출

            응답: 가맹점명만 (없으면 "없음")
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: "없음"

            if (text == "없음" || text.length > 30) {
                Result.success("")
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.success("") // 실패해도 빈 문자열 반환
        }
    }

    /**
     * 이번 달 지출 예측
     */
    suspend fun predictMonthlySpending(
        currentMonthExpense: Long,
        dayOfMonth: Int,
        daysInMonth: Int,
        categoryExpenses: Map<String, Long>,
        previousMonthsData: List<MonthlyFinancialData> = emptyList()
    ): Result<SpendingPrediction> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val remainingDays = daysInMonth - dayOfMonth
            val dailyAverage = if (dayOfMonth > 0) currentMonthExpense / dayOfMonth else 0
            val simpleProjection = currentMonthExpense + (dailyAverage * remainingDays)

            val historyText = if (previousMonthsData.isNotEmpty()) {
                previousMonthsData.takeLast(3).joinToString("\n") {
                    "${it.year}년 ${it.month}월: ${String.format("%,d", it.totalExpense)}원"
                }
            } else "이전 데이터 없음"

            val categoryText = categoryExpenses.entries
                .sortedByDescending { it.value }
                .take(5)
                .joinToString(", ") { "${it.key}: ${String.format("%,d", it.value)}원" }

            val prompt = """
            이번 달 지출을 예측해주세요.

            ## 현재 상황
            - 오늘: ${dayOfMonth}일 / ${daysInMonth}일
            - 현재까지 지출: ${String.format("%,d", currentMonthExpense)}원
            - 일 평균 지출: ${String.format("%,d", dailyAverage)}원
            - 남은 일수: ${remainingDays}일
            - 단순 예측: ${String.format("%,d", simpleProjection)}원

            ## 카테고리별 현재 지출
            $categoryText

            ## 과거 월별 지출
            $historyText

            ## 응답 (JSON만)
            {
              "predictedTotal": 예상총지출액(숫자만),
              "confidence": 0.0~1.0,
              "trend": "increase|stable|decrease",
              "insight": "한줄 인사이트"
            }
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: ""

            val jsonMatch = Regex("\\{[^}]+\\}").find(text)
            if (jsonMatch != null) {
                val json = jsonMatch.value
                val predicted = Regex("\"predictedTotal\"\\s*:\\s*([0-9]+)").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: simpleProjection
                val confidence = Regex("\"confidence\"\\s*:\\s*([0-9.]+)").find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 0.6f
                val trend = Regex("\"trend\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "stable"
                val insight = Regex("\"insight\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""

                Result.success(SpendingPrediction(predicted, confidence, trend, insight, remainingDays))
            } else {
                Result.success(SpendingPrediction(simpleProjection, 0.5f, "stable", "단순 예측 기반", remainingDays))
            }
        } catch (e: Exception) {
            Result.failure(Exception("예측 중 오류: ${e.message}"))
        }
    }

    /**
     * 스마트 인사이트 생성
     */
    suspend fun generateSmartInsights(
        currentMonth: MonthlyFinancialData,
        previousMonth: MonthlyFinancialData? = null,
        savingsGoals: List<Pair<String, Long>> = emptyList()
    ): Result<List<SmartInsight>> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val compareText = if (previousMonth != null) {
                """
                이전 달 (${previousMonth.year}년 ${previousMonth.month}월):
                - 수입: ${String.format("%,d", previousMonth.totalIncome)}원
                - 지출: ${String.format("%,d", previousMonth.totalExpense)}원
                - 카테고리별: ${previousMonth.categoryExpenses.entries.take(5).joinToString(", ") { "${it.key}: ${String.format("%,d", it.value)}원" }}
                """.trimIndent()
            } else "이전 달 데이터 없음"

            val goalsText = if (savingsGoals.isNotEmpty()) {
                savingsGoals.joinToString("\n") { "- ${it.first}: ${String.format("%,d", it.second)}원" }
            } else "목표 없음"

            val prompt = """
            가계부 데이터를 분석하고 유용한 인사이트를 생성해주세요.

            ## 이번 달 (${currentMonth.year}년 ${currentMonth.month}월)
            - 수입: ${String.format("%,d", currentMonth.totalIncome)}원
            - 지출: ${String.format("%,d", currentMonth.totalExpense)}원
            - 잔액: ${String.format("%,d", currentMonth.balance)}원
            - 카테고리별: ${currentMonth.categoryExpenses.entries.take(5).joinToString(", ") { "${it.key}: ${String.format("%,d", it.value)}원" }}

            ## 이전 달 비교
            $compareText

            ## 저축 목표
            $goalsText

            ## 응답 (JSON 배열, 3-5개 인사이트)
            [
              {"type": "spending|saving|goal|tip|warning", "emoji": "이모지", "title": "제목", "message": "메시지", "priority": 1~5}
            ]

            타입 설명:
            - spending: 지출 관련 (증가/감소 분석)
            - saving: 저축 관련 (흑자/적자)
            - goal: 목표 달성 관련
            - tip: 유용한 팁
            - warning: 주의/경고 (과소비 등)
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: "[]"

            val insights = mutableListOf<SmartInsight>()
            val arrayMatch = Regex("\\[([\\s\\S]*?)\\]").find(text)
            if (arrayMatch != null) {
                val items = Regex("\\{[^}]+\\}").findAll(arrayMatch.value)
                items.forEach { match ->
                    val json = match.value
                    val type = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "tip"
                    val emoji = Regex("\"emoji\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "\uD83D\uDCA1"
                    val title = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
                    val message = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
                    val priority = Regex("\"priority\"\\s*:\\s*([0-9]+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 3

                    if (title.isNotBlank() && message.isNotBlank()) {
                        insights.add(SmartInsight(type, emoji, title, message, priority))
                    }
                }
            }

            if (insights.isEmpty()) {
                // 기본 인사이트
                insights.add(SmartInsight(
                    type = if (currentMonth.balance >= 0) "saving" else "warning",
                    emoji = if (currentMonth.balance >= 0) "\uD83C\uDF89" else "\u26A0\uFE0F",
                    title = if (currentMonth.balance >= 0) "흑자 유지 중!" else "지출 초과 주의",
                    message = if (currentMonth.balance >= 0)
                        "이번 달 ${String.format("%,d", currentMonth.balance)}원 흑자입니다!"
                    else
                        "이번 달 ${String.format("%,d", -currentMonth.balance)}원 적자입니다",
                    priority = 1
                ))
            }

            Result.success(insights.sortedBy { it.priority })
        } catch (e: Exception) {
            Result.failure(Exception("인사이트 생성 중 오류: ${e.message}"))
        }
    }

    /**
     * 이상 지출 감지
     */
    suspend fun detectAnomalies(
        currentTransaction: ParsedTransactionInfo,
        recentTransactions: List<ParsedTransactionInfo>,
        categoryAverages: Map<String, Long>
    ): Result<AnomalyResult?> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            // 먼저 간단한 규칙 기반 체크
            val categoryAvg = categoryAverages[currentTransaction.category] ?: 0L
            val isHighAmount = currentTransaction.amount > categoryAvg * 2 && currentTransaction.amount > 50000

            // 최근 동일 가맹점 거래 확인
            val sameMerchant = recentTransactions.filter {
                it.merchantName == currentTransaction.merchantName && it.merchantName.isNotBlank()
            }
            val isDuplicateSuspect = sameMerchant.any {
                it.amount == currentTransaction.amount &&
                kotlin.math.abs(it.timestamp - currentTransaction.timestamp) < 3600000 // 1시간 이내
            }

            if (!isHighAmount && !isDuplicateSuspect) {
                return@withContext Result.success(null)
            }

            val recentText = recentTransactions.takeLast(10).joinToString("\n") {
                "- ${it.merchantName.ifBlank { "미확인" }}: ${String.format("%,d", it.amount)}원 (${it.category})"
            }

            val prompt = """
            이 거래가 이상한지 분석해주세요.

            ## 현재 거래
            - 가맹점: ${currentTransaction.merchantName.ifBlank { "미확인" }}
            - 금액: ${String.format("%,d", currentTransaction.amount)}원
            - 카테고리: ${currentTransaction.category}

            ## 해당 카테고리 평균 지출
            ${String.format("%,d", categoryAvg)}원

            ## 최근 거래 내역
            $recentText

            ## 의심 사항
            - 고액 거래: $isHighAmount
            - 중복 의심: $isDuplicateSuspect

            ## 응답 (JSON)
            {"isAnomaly": true/false, "type": "high_amount|duplicate|unusual_merchant|fraud_risk|normal", "severity": 1~5, "reason": "이유", "suggestion": "제안"}

            정상이면 isAnomaly: false
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: ""

            val jsonMatch = Regex("\\{[^}]+\\}").find(text)
            if (jsonMatch != null) {
                val json = jsonMatch.value
                val isAnomaly = json.contains("\"isAnomaly\"\\s*:\\s*true".toRegex())

                if (isAnomaly) {
                    val type = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "unusual"
                    val severity = Regex("\"severity\"\\s*:\\s*([0-9]+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 2
                    val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
                    val suggestion = Regex("\"suggestion\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""

                    Result.success(AnomalyResult(type, severity, reason, suggestion))
                } else {
                    Result.success(null)
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.success(null) // 오류 시 이상 없음으로 처리
        }
    }

    /**
     * 목표 달성 예측
     */
    suspend fun predictGoalAchievement(
        goalName: String,
        targetAmount: Long,
        currentAmount: Long,
        targetDate: Long, // milliseconds
        averageMonthlyContribution: Long,
        recentContributions: List<Long> = emptyList() // 최근 월별 기여금
    ): Result<GoalPrediction> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val remainingAmount = targetAmount - currentAmount
            val remainingMonths = ((targetDate - System.currentTimeMillis()) / (30L * 24 * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
            val requiredMonthly = remainingAmount / remainingMonths
            val progress = (currentAmount.toFloat() / targetAmount * 100).toInt()

            val contributionText = if (recentContributions.isNotEmpty()) {
                recentContributions.takeLast(6).joinToString(", ") { String.format("%,d", it) }
            } else "데이터 없음"

            val prompt = """
            저축 목표 달성 가능성을 예측해주세요.

            ## 목표 정보
            - 목표: $goalName
            - 목표 금액: ${String.format("%,d", targetAmount)}원
            - 현재 금액: ${String.format("%,d", currentAmount)}원 (${progress}%)
            - 남은 금액: ${String.format("%,d", remainingAmount)}원
            - 남은 기간: ${remainingMonths}개월
            - 월 필요 저축액: ${String.format("%,d", requiredMonthly)}원

            ## 저축 패턴
            - 평균 월 저축액: ${String.format("%,d", averageMonthlyContribution)}원
            - 최근 저축 내역: $contributionText

            ## 응답 (JSON)
            {
              "achievementProbability": 0~100,
              "predictedCompletionMonths": 숫자,
              "onTrack": true/false,
              "recommendation": "추천 사항",
              "motivationalMessage": "격려 메시지"
            }
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: ""

            val jsonMatch = Regex("\\{[^}]+\\}").find(text)
            if (jsonMatch != null) {
                val json = jsonMatch.value
                val probability = Regex("\"achievementProbability\"\\s*:\\s*([0-9]+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 50
                val completionMonths = Regex("\"predictedCompletionMonths\"\\s*:\\s*([0-9]+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: remainingMonths
                val onTrack = json.contains("\"onTrack\"\\s*:\\s*true".toRegex())
                val recommendation = Regex("\"recommendation\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
                val message = Regex("\"motivationalMessage\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""

                Result.success(GoalPrediction(probability, completionMonths, onTrack, recommendation, message))
            } else {
                // 기본 계산
                val probability = if (averageMonthlyContribution >= requiredMonthly) 80 else 40
                Result.success(GoalPrediction(
                    probability,
                    if (averageMonthlyContribution > 0) (remainingAmount / averageMonthlyContribution).toInt() else remainingMonths * 2,
                    averageMonthlyContribution >= requiredMonthly,
                    "월 저축액을 ${String.format("%,d", requiredMonthly)}원 이상으로 유지하세요",
                    "꾸준히 하면 목표 달성 가능합니다! \uD83D\uDCAA"
                ))
            }
        } catch (e: Exception) {
            Result.failure(Exception("목표 예측 중 오류: ${e.message}"))
        }
    }

    /**
     * 중복 거래 AI 판단
     */
    suspend fun analyzeDuplicateTransaction(
        transaction1: ParsedTransactionInfo,
        transaction2: ParsedTransactionInfo,
        timeDiffMinutes: Long
    ): Result<DuplicateAnalysis> = withContext(Dispatchers.IO) {
        val model = generativeModel ?: return@withContext Result.failure(
            Exception("API 키가 설정되지 않았습니다")
        )

        try {
            val prompt = """
            이 두 거래가 중복인지 판단해주세요.

            ## 거래 1
            - 은행: ${transaction1.bankName}
            - 금액: ${String.format("%,d", transaction1.amount)}원
            - 가맹점: ${transaction1.merchantName.ifBlank { "미확인" }}
            - 유형: ${transaction1.type}

            ## 거래 2
            - 은행: ${transaction2.bankName}
            - 금액: ${String.format("%,d", transaction2.amount)}원
            - 가맹점: ${transaction2.merchantName.ifBlank { "미확인" }}
            - 유형: ${transaction2.type}

            ## 시간 차이
            ${timeDiffMinutes}분

            ## 중복 가능성 판단 기준
            - 같은 금액 + 비슷한 시간 = 카드/통장 연동 중복 가능성
            - 같은 가맹점 + 같은 금액 = 이중 결제 가능성
            - 다른 은행에서 같은 거래 = 자동이체/연동 가능성

            ## 응답 (JSON)
            {
              "isDuplicate": true/false,
              "confidence": 0.0~1.0,
              "duplicateType": "card_sync|double_payment|auto_transfer|separate_transaction",
              "reason": "이유",
              "recommendation": "keep_both|keep_first|keep_second|ask_user"
            }
            """.trimIndent()

            val response = model.generateContent(prompt)
            val text = response.text?.trim() ?: ""

            val jsonMatch = Regex("\\{[^}]+\\}").find(text)
            if (jsonMatch != null) {
                val json = jsonMatch.value
                val isDuplicate = json.contains("\"isDuplicate\"\\s*:\\s*true".toRegex())
                val confidence = Regex("\"confidence\"\\s*:\\s*([0-9.]+)").find(json)?.groupValues?.get(1)?.toFloatOrNull() ?: 0.5f
                val duplicateType = Regex("\"duplicateType\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "separate_transaction"
                val reason = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: ""
                val recommendation = Regex("\"recommendation\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "ask_user"

                Result.success(DuplicateAnalysis(isDuplicate, confidence, duplicateType, reason, recommendation))
            } else {
                // 기본: 같은 금액이고 시간이 짧으면 중복 의심
                val sameMerchant = transaction1.merchantName == transaction2.merchantName && transaction1.merchantName.isNotBlank()
                val sameAmount = transaction1.amount == transaction2.amount
                val shortTime = timeDiffMinutes < 30

                val isDuplicate = sameAmount && (sameMerchant || shortTime)
                Result.success(DuplicateAnalysis(
                    isDuplicate,
                    if (isDuplicate) 0.7f else 0.3f,
                    if (isDuplicate) "card_sync" else "separate_transaction",
                    if (isDuplicate) "동일 금액, 짧은 시간 간격" else "별개의 거래로 보임",
                    if (isDuplicate) "ask_user" else "keep_both"
                ))
            }
        } catch (e: Exception) {
            Result.failure(Exception("중복 분석 중 오류: ${e.message}"))
        }
    }
}

// ========== AI 결과 데이터 클래스들 ==========

data class AutoCategoryResult(
    val category: String,
    val confidence: Float,
    val reason: String
)

data class SpendingPrediction(
    val predictedTotal: Long,
    val confidence: Float,
    val trend: String, // "increase", "stable", "decrease"
    val insight: String,
    val remainingDays: Int
)

data class SmartInsight(
    val type: String, // "spending", "saving", "goal", "tip", "warning"
    val emoji: String,
    val title: String,
    val message: String,
    val priority: Int
)

data class ParsedTransactionInfo(
    val amount: Long,
    val type: String, // "INCOME", "EXPENSE"
    val bankName: String,
    val merchantName: String,
    val category: String,
    val timestamp: Long
)

data class AnomalyResult(
    val type: String, // "high_amount", "duplicate", "unusual_merchant", "fraud_risk"
    val severity: Int, // 1-5
    val reason: String,
    val suggestion: String
)

data class GoalPrediction(
    val achievementProbability: Int, // 0-100
    val predictedCompletionMonths: Int,
    val onTrack: Boolean,
    val recommendation: String,
    val motivationalMessage: String
)

data class DuplicateAnalysis(
    val isDuplicate: Boolean,
    val confidence: Float,
    val duplicateType: String, // "card_sync", "double_payment", "auto_transfer", "separate_transaction"
    val reason: String,
    val recommendation: String // "keep_both", "keep_first", "keep_second", "ask_user"
)
