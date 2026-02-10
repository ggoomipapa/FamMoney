package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.LearnedMapping
import com.ezcorp.fammoney.data.repository.LearningRepository
import com.ezcorp.fammoney.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 학습 데이터를 활용한 스마트 카테고리 분류 서비스
 * 1. 학습된 매핑 우선 적용
 * 2. 학습 데이터 없으면 로컬 키워드 기반 분류
 */
@Singleton
class SmartCategorizationService @Inject constructor(
    private val learningRepository: LearningRepository,
    private val localCategorizationService: LocalCategorizationService
) {

    companion object {
        private const val TAG = "SmartCategorization"
    }

    /**
     * 스마트 카테고리 분류
     * @param groupId 그룹 ID
     * @param merchantName 가맹점명
     * @param amount 금액
     * @return 카테고리 분류 결과
     */
    suspend fun categorize(
        groupId: String,
        merchantName: String,
        amount: Long = 0
    ): SmartCategoryResult {
        AppLogger.d(TAG, "카테고리 분류 시작 - merchantName: $merchantName, groupId: $groupId, amount: $amount")

        if (merchantName.isBlank()) {
            AppLogger.d(TAG, "카테고리 분류 결과 - category: OTHER, confidence: 0.1, source: DEFAULT (사용처 없음)")
            return SmartCategoryResult(
                category = "OTHER",
                confidence = 0.1f,
                source = CategorySource.DEFAULT,
                reason = "사용처 없음"
            )
        }

        // 1. 학습된 매핑 검색 (정확 일치)
        val exactMatch = learningRepository.findMapping(groupId, merchantName)
        if (exactMatch != null) {
            val confidence = 0.95f + (exactMatch.useCount * 0.01f).coerceAtMost(0.04f)
            AppLogger.d(TAG, "정확 일치 매핑 발견 - merchantName: $merchantName → category: ${exactMatch.category}, useCount: ${exactMatch.useCount}, confidence: $confidence")
            return SmartCategoryResult(
                category = exactMatch.category,
                confidence = confidence,
                source = CategorySource.LEARNED,
                reason = "학습된 매핑 (${exactMatch.useCount}회 사용)"
            )
        }
        AppLogger.d(TAG, "정확 일치 매핑 없음 - merchantName: $merchantName")

        // 2. 부분 일치 검색
        val partialMatch = learningRepository.findMappingByPartialMatch(groupId, merchantName)
        if (partialMatch != null) {
            AppLogger.d(TAG, "부분 일치 매핑 발견 - merchantName: $merchantName → matched: ${partialMatch.originalMerchantName}, category: ${partialMatch.category}, confidence: 0.8")
            return SmartCategoryResult(
                category = partialMatch.category,
                confidence = 0.8f,
                source = CategorySource.LEARNED_PARTIAL,
                reason = "유사 가맹점 학습: ${partialMatch.originalMerchantName}"
            )
        }
        AppLogger.d(TAG, "부분 일치 매핑 없음 - merchantName: $merchantName, 키워드 기반 분류로 전환")

        // 3. 로컬 키워드 기반 분류
        val localResult = localCategorizationService.categorize(merchantName, amount)
        AppLogger.d(TAG, "카테고리 분류 결과 - category: ${localResult.category}, confidence: ${localResult.confidence}, source: KEYWORD, reason: ${localResult.reason}")
        return SmartCategoryResult(
            category = localResult.category,
            confidence = localResult.confidence,
            source = CategorySource.KEYWORD,
            reason = localResult.reason
        )
    }

    /**
     * 사용자 수정 사항 학습
     */
    suspend fun learn(
        groupId: String,
        merchantName: String,
        category: String,
        transactionType: String
    ) {
        if (merchantName.isBlank() || category.isBlank()) {
            AppLogger.w(TAG, "학습 건너뜀 - merchantName 또는 category가 비어있음 (merchantName: '$merchantName', category: '$category')")
            return
        }

        AppLogger.i(TAG, "학습 저장 - merchantName: $merchantName → category: $category, type: $transactionType, groupId: $groupId")

        learningRepository.saveOrUpdateMapping(
            groupId = groupId,
            merchantName = merchantName,
            category = category,
            transactionType = transactionType
        )
    }

    /**
     * 학습 데이터 기반 거래 유형 판단
     */
    suspend fun getLearnedTransactionType(
        groupId: String,
        merchantName: String
    ): String? {
        if (merchantName.isBlank()) {
            AppLogger.d(TAG, "학습된 거래유형 조회 건너뜀 - merchantName이 비어있음")
            return null
        }

        AppLogger.d(TAG, "학습된 거래유형 조회 - merchantName: $merchantName, groupId: $groupId")

        val mapping = learningRepository.findMapping(groupId, merchantName)
            ?: learningRepository.findMappingByPartialMatch(groupId, merchantName)

        AppLogger.d(TAG, "학습된 거래유형 결과 - merchantName: $merchantName → transactionType: ${mapping?.transactionType ?: "없음"}")

        return mapping?.transactionType
    }
}

/**
 * 스마트 카테고리 분류 결과
 */
data class SmartCategoryResult(
    val category: String,
    val confidence: Float,
    val source: CategorySource,
    val reason: String
)

/**
 * 카테고리 분류 출처
 */
enum class CategorySource {
    LEARNED,          // 학습된 정확 매핑
    LEARNED_PARTIAL,  // 학습된 부분 매핑
    KEYWORD,          // 키워드 기반
    DEFAULT           // 기본값
}
