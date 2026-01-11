package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.LearnedMapping
import com.ezcorp.fammoney.data.repository.LearningRepository
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
        if (merchantName.isBlank()) {
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
            return SmartCategoryResult(
                category = exactMatch.category,
                confidence = 0.95f + (exactMatch.useCount * 0.01f).coerceAtMost(0.04f),
                source = CategorySource.LEARNED,
                reason = "학습된 매핑 (${exactMatch.useCount}회 사용)"
            )
        }

        // 2. 부분 일치 검색
        val partialMatch = learningRepository.findMappingByPartialMatch(groupId, merchantName)
        if (partialMatch != null) {
            return SmartCategoryResult(
                category = partialMatch.category,
                confidence = 0.8f,
                source = CategorySource.LEARNED_PARTIAL,
                reason = "유사 가맹점 학습: ${partialMatch.originalMerchantName}"
            )
        }

        // 3. 로컬 키워드 기반 분류
        val localResult = localCategorizationService.categorize(merchantName, amount)
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
        if (merchantName.isBlank() || category.isBlank()) return

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
        if (merchantName.isBlank()) return null

        val mapping = learningRepository.findMapping(groupId, merchantName)
            ?: learningRepository.findMappingByPartialMatch(groupId, merchantName)

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
