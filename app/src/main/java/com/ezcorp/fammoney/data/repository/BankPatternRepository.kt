package com.ezcorp.fammoney.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.data.model.CustomBankPattern
import com.ezcorp.fammoney.data.model.PatternTestResult
import com.ezcorp.fammoney.data.model.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.bankPatternDataStore: DataStore<Preferences> by preferencesDataStore(name = "bank_patterns")

@Singleton
class BankPatternRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.bankPatternDataStore
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 저장된 모든 패턴 가져오기 (Flow)
     */
    val patternsFlow: Flow<List<CustomBankPattern>> = dataStore.data.map { preferences ->
        val patternsJson = preferences[PATTERNS_KEY]
        if (patternsJson.isNullOrEmpty()) {
            // 기본 패턴 반환
            CustomBankPattern.getDefaultPatterns()
        } else {
            try {
                json.decodeFromString<List<CustomBankPattern>>(patternsJson)
            } catch (e: Exception) {
                CustomBankPattern.getDefaultPatterns()
            }
        }
    }

    /**
     * 현재 저장된 패턴 가져오기
     */
    suspend fun getPatterns(): List<CustomBankPattern> = patternsFlow.first()

    /**
     * 활성화된 패턴만 가져오기
     */
    suspend fun getEnabledPatterns(): List<CustomBankPattern> {
        return getPatterns().filter { it.isEnabled }
    }

    /**
     * 활성화된 패턴을 BankConfig로 변환해 가져오기
     */
    suspend fun getEnabledBankConfigs(): List<BankConfig> {
        return getEnabledPatterns().map { it.toBankConfig() }
    }

    /**
     * 특정 패턴 가져오기
     */
    suspend fun getPattern(patternId: String): CustomBankPattern? {
        return getPatterns().find { it.id == patternId }
    }

    /**
     * 패턴 저장 (전체 목록)
     */
    suspend fun savePatterns(patterns: List<CustomBankPattern>) {
        dataStore.edit { preferences ->
            preferences[PATTERNS_KEY] = json.encodeToString(patterns)
        }
    }

    /**
     * 단일 패턴 추가/업데이트
     */
    suspend fun savePattern(pattern: CustomBankPattern) {
        val patterns = getPatterns().toMutableList()
        val existingIndex = patterns.indexOfFirst { it.id == pattern.id }

        if (existingIndex >= 0) {
            patterns[existingIndex] = pattern.copy(lastModified = System.currentTimeMillis())
        } else {
            patterns.add(pattern.copy(lastModified = System.currentTimeMillis()))
        }

        savePatterns(patterns)
    }

    /**
     * 패턴 삭제
     */
    suspend fun deletePattern(patternId: String) {
        val patterns = getPatterns().filter { it.id != patternId }
        savePatterns(patterns)
    }

    /**
     * 패턴 활성화/비활성화 토글
     */
    suspend fun togglePatternEnabled(patternId: String) {
        val patterns = getPatterns().map { pattern ->
            if (pattern.id == patternId) {
                pattern.copy(isEnabled = !pattern.isEnabled, lastModified = System.currentTimeMillis())
            } else {
                pattern
            }
        }
        savePatterns(patterns)
    }

    /**
     * 기본 패턴으로 초기화
     */
    suspend fun resetToDefaults() {
        savePatterns(CustomBankPattern.getDefaultPatterns())
    }

    /**
     * 패턴 테스트 - 입력한 텍스트에서 거래 정보 추출 시도
     */
    fun testPattern(pattern: CustomBankPattern, testText: String): PatternTestResult {
        return try {
            // 금액 추출 시도
            val amountRegex = Regex(pattern.amountRegex)
            val amountMatch = amountRegex.find(testText)

            if (amountMatch == null) {
                return PatternTestResult(
                    success = false,
                    errorMessage = "금액을 찾을 수 없습니다. 정규식을 확인해주세요."
                )
            }

            val amountStr = amountMatch.groupValues.getOrNull(1) ?: return PatternTestResult(
                success = false,
                errorMessage = "금액 그룹을 추출할 수 없습니다. 정규식 캡처 그룹 ()이 있는지 확인해주세요."
            )

            val amount = amountStr.replace(",", "").replace(" ", "").toLongOrNull()
                ?: return PatternTestResult(
                    success = false,
                    errorMessage = "금액을 숫자로 변환할 수 없습니다: $amountStr"
                )

            // 거래 유형 판단
            val hasIncomeKeyword = pattern.incomeKeywords.any { testText.contains(it) }
            val hasExpenseKeyword = pattern.expenseKeywords.any { testText.contains(it) }

            val transactionType = when {
                hasIncomeKeyword && !hasExpenseKeyword -> "INCOME"
                hasExpenseKeyword && !hasIncomeKeyword -> "EXPENSE"
                hasIncomeKeyword && hasExpenseKeyword -> {
                    // 먼저 나타난 키워드 기준
                val incomeIndex = pattern.incomeKeywords
                        .mapNotNull { testText.indexOf(it).takeIf { idx -> idx >= 0 } }
                        .minOrNull() ?: Int.MAX_VALUE
                    val expenseIndex = pattern.expenseKeywords
                        .mapNotNull { testText.indexOf(it).takeIf { idx -> idx >= 0 } }
                        .minOrNull() ?: Int.MAX_VALUE
                    if (incomeIndex < expenseIndex) "INCOME" else "EXPENSE"
                }
                else -> "EXPENSE" // 기본값
            }

            // 사용처 추출 (merchantRegexList 사용)
            var merchantName: String? = null
            for (merchantPattern in pattern.merchantRegexList) {
                try {
                    val merchantRegex = Regex(merchantPattern)
                    val merchantMatch = merchantRegex.find(testText)
                    if (merchantMatch != null) {
                        merchantName = merchantMatch.groupValues.getOrNull(1)?.trim()
                        if (!merchantName.isNullOrBlank()) break
                    }
                } catch (e: Exception) {
                    // 잘못된 정규식 무시
                }
            }

            PatternTestResult(
                success = true,
                amount = amount,
                transactionType = transactionType,
                merchantName = merchantName,
                matchedPattern = pattern.amountRegex
            )

        } catch (e: Exception) {
            PatternTestResult(
                success = false,
                errorMessage = "패턴 테스트 중 오류: ${e.message}"
            )
        }
    }

    /**
     * 새 사용자 정의 패턴 생성을 위한 기본 템플릿
     */
    fun createNewPatternTemplate(): CustomBankPattern {
        return CustomBankPattern(
            id = "custom_${System.currentTimeMillis()}",
            displayName = "새 패턴",
            packageNames = listOf("com.kakao.talk"),
            amountRegex = "([0-9,]+)\\s*원",
            incomeKeywords = listOf("입금", "받으셨"),
            expenseKeywords = listOf("출금", "결제", "승인"),
            merchantRegexList = listOf(
                "\\(([가-힣a-zA-Z0-9\\s]+)\\)\\s*(?:승인|결제)",
                "(?:사용처|가맹점)[:\\s]*([가-힣a-zA-Z0-9\\s]+)"
            ),
            isCustom = true
        )
    }

    companion object {
        private val PATTERNS_KEY = stringPreferencesKey("bank_patterns_json")
    }
}
