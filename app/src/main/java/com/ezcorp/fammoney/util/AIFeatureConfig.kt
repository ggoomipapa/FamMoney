package com.ezcorp.fammoney.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * AI 기능 자격 설정 관리
 * Firebase Remote Config를 통해 무료/유료 기능을 동적으로 관리
 *
 * Firebase Console에서 설정:
 * - gemini_api_key: Gemini API 키 (서버에서 관리)
 * - ai_free_features: 무료 사용자에게 제공할 AI 기능 목록 (JSON 배열)
 * - ai_paid_features: 유료 구독자만 사용할 수 있는 AI 기능 목록 (JSON 배열)
 * - ai_features_enabled: AI 기능 전체 활성화 여부 (boolean)
 */
object AIFeatureConfig {
    private const val TAG = "AIFeatureConfig"

    // Remote Config 키
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private const val KEY_FREE_FEATURES = "ai_free_features"
    private const val KEY_PAID_FEATURES = "ai_paid_features"
    private const val KEY_AI_ENABLED = "ai_features_enabled"
    private const val KEY_MIN_FETCH_INTERVAL = "ai_config_fetch_interval_hours"

    // AI 기능 식별자
    object Features {
        const val AUTO_CATEGORIZE = "autoCategorize"           // 자동 카테고리 분류
        const val MERCHANT_EXTRACT = "merchantExtract"         // 가맹점명 추출
        const val SPENDING_PREDICTION = "spendingPrediction"   // 지출 예측
        const val SMART_INSIGHTS = "smartInsights"             // 스마트 인사이트
        const val ANOMALY_DETECTION = "anomalyDetection"       // 이상 지출 감지
        const val GOAL_PREDICTION = "goalPrediction"           // 목표 달성 예측
        const val DUPLICATE_ANALYSIS = "duplicateAnalysis"     // 중복 거래 분석
        const val FINANCIAL_ANALYSIS = "financialAnalysis"     // 재정 분석
        const val GOAL_COACHING = "goalCoaching"               // 목표 코칭

        // Debug 전용 (Release에서 UI 숨김)
        const val INVESTMENT_ANALYSIS = "investmentAnalysis"   // 투자 분석
        const val PRODUCT_SEARCH = "productSearch"             // 상품 검색
        const val SAVINGS_STRATEGY = "savingsStrategy"         // 저축 전략
        const val INVESTMENT_GUIDE = "investmentGuide"         // 투자 가이드
    }

    // 기본값 (Remote Config 연결 안됨 또는 실패 시)
    private val DEFAULT_FREE_FEATURES = setOf(
        Features.AUTO_CATEGORIZE,
        Features.MERCHANT_EXTRACT
    )

    private val DEFAULT_PAID_FEATURES = setOf(
        Features.SPENDING_PREDICTION,
        Features.SMART_INSIGHTS,
        Features.ANOMALY_DETECTION,
        Features.GOAL_PREDICTION,
        Features.DUPLICATE_ANALYSIS,
        Features.FINANCIAL_ANALYSIS,
        Features.GOAL_COACHING,
        Features.INVESTMENT_ANALYSIS,
        Features.PRODUCT_SEARCH,
        Features.SAVINGS_STRATEGY,
        Features.INVESTMENT_GUIDE
    )

    // 현재 설정값 (캐시)
    private var geminiApiKey: String = ""
    private var freeFeatures: Set<String> = DEFAULT_FREE_FEATURES
    private var paidFeatures: Set<String> = DEFAULT_PAID_FEATURES
    private var isAIEnabled: Boolean = true
    private var isInitialized: Boolean = false

    private var remoteConfig: FirebaseRemoteConfig? = null

    /**
     * Remote Config 초기화
     * Application onCreate에서 호출
     */
    fun initialize() {
        try {
            remoteConfig = FirebaseRemoteConfig.getInstance()

            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1시간마다 fetch (프로덕션)
                .build()

            remoteConfig?.setConfigSettingsAsync(configSettings)

            // 기본값 설정
            val defaults = mapOf(
                KEY_GEMINI_API_KEY to "",
                KEY_FREE_FEATURES to """["autoCategorize", "merchantExtract"]""",
                KEY_PAID_FEATURES to """["spendingPrediction", "smartInsights", "anomalyDetection", "goalPrediction", "duplicateAnalysis", "financialAnalysis", "goalCoaching", "investmentAnalysis", "productSearch", "savingsStrategy", "investmentGuide"]""",
                KEY_AI_ENABLED to true,
                KEY_MIN_FETCH_INTERVAL to 1L
            )
            remoteConfig?.setDefaultsAsync(defaults)

            // 캐시된 값 먼저 적용
            applyConfig()

            isInitialized = true
            Log.d(TAG, "AIFeatureConfig initialized with defaults")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Remote Config: ${e.message}")
        }
    }

    /**
     * 서버에서 최신 설정 가져오기
     */
    suspend fun fetchAndActivate(): Boolean {
        return try {
            val config = remoteConfig ?: return false

            val fetched = config.fetchAndActivate().await()
            applyConfig()

            Log.d(TAG, "Remote Config fetched: $fetched")
            Log.d(TAG, "Free features: $freeFeatures")
            Log.d(TAG, "Paid features: $paidFeatures")
            Log.d(TAG, "API Key available: ${geminiApiKey.isNotBlank()}")

            fetched
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Remote Config: ${e.message}")
            false
        }
    }

    /**
     * Remote Config 값을 로컬 변수에 적용
     */
    private fun applyConfig() {
        val config = remoteConfig ?: return

        try {
            // Gemini API 키
            geminiApiKey = config.getString(KEY_GEMINI_API_KEY)

            // AI 전체 활성화 여부
            isAIEnabled = config.getBoolean(KEY_AI_ENABLED)

            // 무료 기능 파싱
            val freeJson = config.getString(KEY_FREE_FEATURES)
            freeFeatures = parseJsonArray(freeJson).ifEmpty { DEFAULT_FREE_FEATURES }

            // 유료 기능 파싱
            val paidJson = config.getString(KEY_PAID_FEATURES)
            paidFeatures = parseJsonArray(paidJson).ifEmpty { DEFAULT_PAID_FEATURES }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply config: ${e.message}")
            // 기본값 유지
            freeFeatures = DEFAULT_FREE_FEATURES
            paidFeatures = DEFAULT_PAID_FEATURES
            isAIEnabled = true
        }
    }

    /**
     * JSON 배열 문자열을 Set으로 파싱
     */
    private fun parseJsonArray(json: String): Set<String> {
        return try {
            // 간단한 JSON 배열 파싱 ["a", "b", "c"]
            json.trim()
                .removePrefix("[")
                .removeSuffix("]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    // ========== 공개 API ==========

    /**
     * Gemini API 키 가져오기
     * Remote Config에서 관리되는 API 키 반환
     */
    fun getGeminiApiKey(): String = geminiApiKey

    /**
     * API 키가 설정되어 있는지 확인
     */
    fun hasApiKey(): Boolean = geminiApiKey.isNotBlank()

    /**
     * AI 기능이 전체적으로 활성화되었는지
     */
    fun isAIFeaturesEnabled(): Boolean = isAIEnabled

    /**
     * 해당 기능이 무료인지 확인
     */
    fun isFeatureFree(featureId: String): Boolean {
        return featureId in freeFeatures
    }

    /**
     * 해당 기능이 유료인지 확인
     */
    fun isFeaturePaid(featureId: String): Boolean {
        return featureId in paidFeatures
    }

    /**
     * 사용자가 해당 기능을 사용할 수 있는지 확인
     * @param featureId 기능 ID
     * @param hasPaidSubscription 유료 구독 여부
     */
    fun canUseFeature(featureId: String, hasPaidSubscription: Boolean): Boolean {
        if (!isAIEnabled) return false
        if (!hasApiKey()) return false

        // Debug 빌드에서는 모든 기능 사용 가능
        if (DebugConfig.isDebugBuild) return true

        // 무료 기능은 누구나
        if (isFeatureFree(featureId)) return true

        // 유료 기능은 구독자만
        if (isFeaturePaid(featureId) && hasPaidSubscription) return true

        return false
    }

    /**
     * 현재 무료 기능 목록
     */
    fun getFreeFeatures(): Set<String> = freeFeatures.toSet()

    /**
     * 현재 유료 기능 목록
     */
    fun getPaidFeatures(): Set<String> = paidFeatures.toSet()

    /**
     * 디버그용: 현재 설정 상태 출력
     */
    fun getDebugInfo(): String {
        return """
            AIFeatureConfig Status:
            - Initialized: $isInitialized
            - AI Enabled: $isAIEnabled
            - API Key Set: ${geminiApiKey.isNotBlank()}
            - Free Features: $freeFeatures
            - Paid Features: $paidFeatures
        """.trimIndent()
    }
}
