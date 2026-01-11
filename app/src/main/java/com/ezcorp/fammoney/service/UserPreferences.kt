package com.ezcorp.fammoney.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val userIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }

    val groupIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[GROUP_ID_KEY]
    }

    val userNameFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY]
    }

    suspend fun getUserId(): String? = userIdFlow.first()

    suspend fun getGroupId(): String? = groupIdFlow.first()

    suspend fun getUserName(): String? = userNameFlow.first()

    suspend fun saveUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
        }
    }

    suspend fun saveGroupId(groupId: String) {
        dataStore.edit { preferences ->
            preferences[GROUP_ID_KEY] = groupId
        }
    }

    suspend fun saveUserName(userName: String) {
        dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = userName
        }
    }

    suspend fun saveUserData(userId: String, groupId: String, userName: String) {
        dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[GROUP_ID_KEY] = groupId
            preferences[USER_NAME_KEY] = userName
        }
    }

    suspend fun clearUserData() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun isLoggedIn(): Boolean = getUserId() != null

    val fcmTokenFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[FCM_TOKEN_KEY]
    }

    val authUidFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[AUTH_UID_KEY]
    }

    suspend fun getFcmToken(): String? = fcmTokenFlow.first()

    suspend fun getAuthUid(): String? = authUidFlow.first()

    suspend fun saveFcmToken(token: String) {
        dataStore.edit { preferences ->
            preferences[FCM_TOKEN_KEY] = token
        }
    }

    suspend fun saveAuthUid(authUid: String) {
        dataStore.edit { preferences ->
            preferences[AUTH_UID_KEY] = authUid
        }
    }

    // 고액 거래 확인 기준 금액
    val highAmountThresholdFlow: Flow<Long> = dataStore.data.map { preferences ->
        preferences[HIGH_AMOUNT_THRESHOLD_KEY] ?: DEFAULT_HIGH_AMOUNT_THRESHOLD
    }

    suspend fun getHighAmountThreshold(): Long = highAmountThresholdFlow.first()

    suspend fun saveHighAmountThreshold(amount: Long) {
        dataStore.edit { preferences ->
            preferences[HIGH_AMOUNT_THRESHOLD_KEY] = amount
        }
    }

    // 현금 사용 내역 수동 관리
    val cashManagementEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CASH_MANAGEMENT_ENABLED_KEY] ?: false
    }

    suspend fun getCashManagementEnabled(): Boolean = cashManagementEnabledFlow.first()

    suspend fun saveCashManagementEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CASH_MANAGEMENT_ENABLED_KEY] = enabled
        }
    }

    // Gemini API 키 (AI 코칭용)
    val geminiApiKeyFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: ""
    }

    suspend fun getGeminiApiKey(): String = geminiApiKeyFlow.first()

    suspend fun saveGeminiApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = apiKey
        }
    }

    // 온보딩 완료 여부
    val onboardingCompletedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun isOnboardingCompleted(): Boolean = onboardingCompletedFlow.first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    // 중복 알림 처리 방식
    // "card" = 카드 알림 우선 (은행 알림 무시)
    // "bank" = 은행 알림 우선 (카드 알림 무시)
    // "ask" = 매번 물어보기 (기본값)
    val duplicatePreferenceFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[DUPLICATE_PREFERENCE_KEY] ?: DUPLICATE_PREF_ASK
    }

    suspend fun getDuplicatePreference(): String = duplicatePreferenceFlow.first()

    suspend fun saveDuplicatePreference(preference: String) {
        dataStore.edit { preferences ->
            preferences[DUPLICATE_PREFERENCE_KEY] = preference
        }
    }

    // 현재 활성 태그 (여행/이벤트 등)
    val activeTagIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_TAG_ID_KEY]
    }

    val activeTagNameFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ACTIVE_TAG_NAME_KEY]
    }

    suspend fun getActiveTagId(): String? = activeTagIdFlow.first()

    suspend fun getActiveTagName(): String? = activeTagNameFlow.first()

    suspend fun saveActiveTag(tagId: String, tagName: String) {
        dataStore.edit { preferences ->
            preferences[ACTIVE_TAG_ID_KEY] = tagId
            preferences[ACTIVE_TAG_NAME_KEY] = tagName
        }
    }

    suspend fun clearActiveTag() {
        dataStore.edit { preferences ->
            preferences.remove(ACTIVE_TAG_ID_KEY)
            preferences.remove(ACTIVE_TAG_NAME_KEY)
        }
    }

    suspend fun saveFullUserData(
        userId: String,
        groupId: String,
        userName: String,
        authUid: String
    ) {
        dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[GROUP_ID_KEY] = groupId
            preferences[USER_NAME_KEY] = userName
            preferences[AUTH_UID_KEY] = authUid
        }
    }

    companion object {
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val GROUP_ID_KEY = stringPreferencesKey("group_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val FCM_TOKEN_KEY = stringPreferencesKey("fcm_token")
        private val AUTH_UID_KEY = stringPreferencesKey("auth_uid")
        private val HIGH_AMOUNT_THRESHOLD_KEY = longPreferencesKey("high_amount_threshold")
        private val CASH_MANAGEMENT_ENABLED_KEY = booleanPreferencesKey("cash_management_enabled")
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val DUPLICATE_PREFERENCE_KEY = stringPreferencesKey("duplicate_preference")
        private val ACTIVE_TAG_ID_KEY = stringPreferencesKey("active_tag_id")
        private val ACTIVE_TAG_NAME_KEY = stringPreferencesKey("active_tag_name")

        const val DEFAULT_HIGH_AMOUNT_THRESHOLD = 1_000_000L  // 기본값 100만원

        // 중복 알림 처리 방식 상수
        const val DUPLICATE_PREF_CARD = "card"  // 카드 알림 우선
        const val DUPLICATE_PREF_BANK = "bank"  // 은행 알림 우선
        const val DUPLICATE_PREF_ASK = "ask"    // 매번 물어보기
    }
}
