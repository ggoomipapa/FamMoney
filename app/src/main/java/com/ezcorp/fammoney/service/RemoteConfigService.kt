package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteConfig"

/**
 * Firebase Remote Config 서비스
 * 서버에서 API 키와 설정을 안전하게 가져옴
 */
@Singleton
class RemoteConfigService @Inject constructor() {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    private var isInitialized = false

    companion object {
        // Remote Config 키 이름
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_AI_ENABLED = "ai_enabled"
        const val KEY_FREE_AI_TRIAL_COUNT = "free_ai_trial_count"

        // 기본값 (Remote Config에서 가져오지 못할 경우)
        private val DEFAULTS = mapOf(
            KEY_GEMINI_API_KEY to "",
            KEY_AI_ENABLED to true,
            KEY_FREE_AI_TRIAL_COUNT to 3L
        )
    }

    /**
     * Remote Config 초기화 및 값 가져오기
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) {
            AppLogger.d(TAG, "initialize: 이미 초기화됨, 스킵")
            return true
        }
        AppLogger.d(TAG, "initialize: Remote Config 초기화 시작")

        return try {
            // 개발 중에는 빠른 fetch를 위해 최소 fetch 간격 설정
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1시간 (프로덕션)
                .build()

            remoteConfig.setConfigSettingsAsync(configSettings).await()
            remoteConfig.setDefaultsAsync(DEFAULTS).await()

            // 서버에서 값 가져오기
            remoteConfig.fetchAndActivate().await()

            isInitialized = true
            val geminiKey = remoteConfig.getString(KEY_GEMINI_API_KEY)
            val aiEnabled = remoteConfig.getBoolean(KEY_AI_ENABLED)
            val freeTrialCount = remoteConfig.getLong(KEY_FREE_AI_TRIAL_COUNT)
            AppLogger.i(TAG, "Remote Config 초기화 성공: aiEnabled=$aiEnabled, freeTrialCount=$freeTrialCount, geminiKeyLength=${geminiKey.length}")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Remote Config 초기화 실패", e)
            false
        }
    }

    /**
     * Gemini API 키 가져오기
     */
    fun getGeminiApiKey(): String {
        val apiKey = remoteConfig.getString(KEY_GEMINI_API_KEY)
        AppLogger.d(TAG, "getGeminiApiKey: keyLength=${apiKey.length}, isEmpty=${apiKey.isEmpty()}")
        return apiKey
    }

    /**
     * AI 기능 활성화 여부
     */
    fun isAiEnabled(): Boolean {
        val enabled = remoteConfig.getBoolean(KEY_AI_ENABLED)
        AppLogger.d(TAG, "isAiEnabled: $enabled")
        return enabled
    }

    /**
     * 무료 AI 사용 횟수 (비구독자용)
     */
    fun getFreeAiTrialCount(): Int {
        val count = remoteConfig.getLong(KEY_FREE_AI_TRIAL_COUNT).toInt()
        AppLogger.d(TAG, "getFreeAiTrialCount: $count")
        return count
    }

    /**
     * 강제로 Remote Config 새로고침
     */
    suspend fun forceRefresh(): Boolean {
        AppLogger.d(TAG, "forceRefresh: 강제 새로고침 시작")
        return try {
            remoteConfig.fetch(0).await()
            remoteConfig.activate().await()
            val aiEnabled = remoteConfig.getBoolean(KEY_AI_ENABLED)
            val freeTrialCount = remoteConfig.getLong(KEY_FREE_AI_TRIAL_COUNT)
            AppLogger.i(TAG, "Remote Config 강제 새로고침 성공: aiEnabled=$aiEnabled, freeTrialCount=$freeTrialCount")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Remote Config 새로고침 실패", e)
            false
        }
    }
}
