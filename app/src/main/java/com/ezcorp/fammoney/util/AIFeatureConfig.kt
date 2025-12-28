package com.ezcorp.fammoney.util

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * AI ê¸°ë¥ ?ê²© ?¤ì  ê´ë¦? * Firebase Remote Configë¥??µí´ ë¬´ë£/? ë£ ê¸°ë¥???ì ?¼ë¡ ê´ë¦? *
 * Firebase Console?ì ?¤ì :
 * - ai_free_features: ë¬´ë£ ?¬ì©?ìê²??ê³µ??AI ê¸°ë¥ ëª©ë¡ (JSON ë°°ì´)
 * - ai_paid_features: ? ë£ êµ¬ë?ë§ ?¬ì©?????ë AI ê¸°ë¥ ëª©ë¡ (JSON ë°°ì´)
 * - ai_features_enabled: AI ê¸°ë¥ ?ì²´ ?ì±???¬ë? (boolean)
 */
object AIFeatureConfig {
    private const val TAG = "AIFeatureConfig"

    // Remote Config 
private const val KEY_FREE_FEATURES = "ai_free_features"
    private const val KEY_PAID_FEATURES = "ai_paid_features"
    private const val KEY_AI_ENABLED = "ai_features_enabled"
    private const val KEY_MIN_FETCH_INTERVAL = "ai_config_fetch_interval_hours"

    // AI ê¸°ë¥ ?ë³
object Features {
        const val AUTO_CATEGORIZE = "autoCategorize"           // ?ë ì¹´íê³ ë¦¬ ë¶ë¥
        const val MERCHANT_EXTRACT = "merchantExtract"         // ê°ë§¹ì ëª?ì¶ì¶
        const val SPENDING_PREDICTION = "spendingPrediction"   // ì§ì¶??ì¸¡
        const val SMART_INSIGHTS = "smartInsights"             // ?¤ë§???¸ì¬?´í¸
        const val ANOMALY_DETECTION = "anomalyDetection"       // ?´ì ì§ì¶?ê°ì"
        const val GOAL_PREDICTION = "goalPrediction"           // ëª©í ?¬ì± ?ì¸¡
        const val DUPLICATE_ANALYSIS = "duplicateAnalysis"     // ì¤ë³µ ê±°ë ë¶ì
        const val FINANCIAL_ANALYSIS = "financialAnalysis"     // ?¬ì  ë¶ì
        const val GOAL_COACHING = "goalCoaching"               // ëª©í ì½ì¹­

        // Debug ?ì© (Release?ì UI ?¨ê")
        const val INVESTMENT_ANALYSIS = "investmentAnalysis"   // ?¬ì ë¶ì
        const val PRODUCT_SEARCH = "productSearch"             // ?í ê²
const val SAVINGS_STRATEGY = "savingsStrategy"         // ?ì¶??ëµ
        const val INVESTMENT_GUIDE = "investmentGuide"         // ?¬ì ê°?´ë
    }

    // ê¸°ë³¸ê°?(Remote Config ?°ê²° ???ë ?¤í¨ ?"
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

    // ?ì¬ ?¤ì ê°?(ìºì)
    private var freeFeatures: Set<String> = DEFAULT_FREE_FEATURES
    private var paidFeatures: Set<String> = DEFAULT_PAID_FEATURES
    private var isAIEnabled: Boolean = true
    private var isInitialized: Boolean = false

    private var remoteConfig: FirebaseRemoteConfig? = null

    /**
     * Remote Config ì´ê¸°
* Application onCreate?ì ?¸ì¶
     */
    fun initialize() {
        try {
            remoteConfig = FirebaseRemoteConfig.getInstance()

            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1?ê°ë§ë¤ fetch (?ë¡?ì)
                .build()

            remoteConfig?.setConfigSettingsAsync(configSettings)

            // ê¸°ë³¸ê°??¤ì 
            val defaults = mapOf(
                KEY_FREE_FEATURES to """["autoCategorize", "merchantExtract"]""",
                KEY_PAID_FEATURES to """["spendingPrediction", "smartInsights", "anomalyDetection", "goalPrediction", "duplicateAnalysis", "financialAnalysis", "goalCoaching", "investmentAnalysis", "productSearch", "savingsStrategy", "investmentGuide"]""",
                KEY_AI_ENABLED to true,
                KEY_MIN_FETCH_INTERVAL to 1L
            )
            remoteConfig?.setDefaultsAsync(defaults)

            // ìºì??ê°?ë¨¼ì? ?ì©
            applyConfig()

            isInitialized = true
            Log.d(TAG, "AIFeatureConfig initialized with defaults")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Remote Config: ${e.message}")
        }
    }

    /**
     * ?ë²?ì ìµì  ?¤ì  ê°?¸ì¤ê¸?     */
    suspend fun fetchAndActivate(): Boolean {
        return try {
            val config = remoteConfig ?: return false

            val fetched = config.fetchAndActivate().await()
            applyConfig()

            Log.d(TAG, "Remote Config fetched: $fetched")
            Log.d(TAG, "Free features: $freeFeatures")
            Log.d(TAG, "Paid features: $paidFeatures")

            fetched
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Remote Config: ${e.message}")
            false
        }
    }

    /**
     * Remote Config ê°ì ë¡ì»¬ ë³?ì ?ì©
     */
    private fun applyConfig() {
        val config = remoteConfig ?: return

        try {
            // AI ?ì²´ ?ì±???¬ë"
            isAIEnabled = config.getBoolean(KEY_AI_ENABLED)

            // ë¬´ë£ ê¸°ë¥ ?ì±
            val freeJson = config.getString(KEY_FREE_FEATURES)
            freeFeatures = parseJsonArray(freeJson).ifEmpty { DEFAULT_FREE_FEATURES }

            // ? ë£ ê¸°ë¥ ?ì±
            val paidJson = config.getString(KEY_PAID_FEATURES)
            paidFeatures = parseJsonArray(paidJson).ifEmpty { DEFAULT_PAID_FEATURES }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply config: ${e.message}")
            // ê¸°ë³¸ê°?? ì"
            freeFeatures = DEFAULT_FREE_FEATURES
            paidFeatures = DEFAULT_PAID_FEATURES
            isAIEnabled = true
        }
    }

    /**
     * JSON ë°°ì´ ë¬¸ì?´ì Set?¼ë¡ ?ì±
     */
    private fun parseJsonArray(json: String): Set<String> {
        return try {
            // ê°ë¨??JSON ë°°ì´ ?ì± ["a", "b", "c"]
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

    // ========== ê³µê° API ==========

    /**
     * AI ê¸°ë¥???ì²´?ì¼ë¡??ì±?ë???ëì§
     */
    fun isAIFeaturesEnabled(): Boolean = isAIEnabled

    /**
     * ?´ë¹ ê¸°ë¥??ë¬´ë£?¸ì? ?ì¸
     */
    fun isFeatureFree(featureId: String): Boolean {
        return featureId in freeFeatures
    }

    /**
     * ?´ë¹ ê¸°ë¥??? ë£?¸ì? ?ì¸
     */
    fun isFeaturePaid(featureId: String): Boolean {
        return featureId in paidFeatures
    }

    /**
     * ?¬ì©?ê? ?´ë¹ ê¸°ë¥???¬ì©?????ëì§ ?ì¸
     * @param featureId ê¸°ë¥ ID
     * @param hasPaidSubscription ? ë£ êµ¬ë ?¬ë"
     */
    fun canUseFeature(featureId: String, hasPaidSubscription: Boolean): Boolean {
        if (!isAIEnabled) return false

        // Debug ë¹ë?ì??ëª¨ë  ê¸°ë¥ ?¬ì© ê°
if (DebugConfig.isDebugBuild) return true

        // ë¬´ë£ ê¸°ë¥? ?êµ¬
if (isFeatureFree(featureId)) return true

        // ? ë£ ê¸°ë¥? êµ¬ë?ë§
        if (isFeaturePaid(featureId) && hasPaidSubscription) return true

        return false
    }

    /**
     * ?ì¬ ë¬´ë£ ê¸°ë¥ ëª©ë¡
     */
    fun getFreeFeatures(): Set<String> = freeFeatures.toSet()

    /**
     * ?ì¬ ? ë£ ê¸°ë¥ ëª©ë¡
     */
    fun getPaidFeatures(): Set<String> = paidFeatures.toSet()

    /**
     * ?ë²ê·¸ì©: ?ì¬ ?¤ì  ?í ì¶ë ¥
     */
    fun getDebugInfo(): String {
        return """
            AIFeatureConfig Status:
            - Initialized: $isInitialized
            - AI Enabled: $isAIEnabled
            - Free Features: $freeFeatures
            - Paid Features: $paidFeatures
        """.trimIndent()
    }
}
