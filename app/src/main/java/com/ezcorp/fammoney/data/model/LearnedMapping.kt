package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp

/**
 * 학습된 사용처-카테고리 매핑
 * 사용자가 수동으로 설정한 카테고리를 저장하여 향후 같은 사용처에서 자동 적용
 */
data class LearnedMapping(
    val id: String = "",
    val groupId: String = "",
    val merchantName: String = "",           // 가맹점명 (정규화됨)
    val originalMerchantName: String = "",   // 원본 가맹점명
    val category: String = "",               // 학습된 카테고리
    val transactionType: String = "EXPENSE", // INCOME or EXPENSE
    val useCount: Int = 1,                   // 사용 횟수 (빈도 기반 신뢰도)
    val lastUsedAt: Timestamp? = null,
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "merchantName" to merchantName,
        "originalMerchantName" to originalMerchantName,
        "category" to category,
        "transactionType" to transactionType,
        "useCount" to useCount,
        "lastUsedAt" to lastUsedAt,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): LearnedMapping {
            return LearnedMapping(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                merchantName = map["merchantName"] as? String ?: "",
                originalMerchantName = map["originalMerchantName"] as? String ?: "",
                category = map["category"] as? String ?: "",
                transactionType = map["transactionType"] as? String ?: "EXPENSE",
                useCount = (map["useCount"] as? Long)?.toInt() ?: 1,
                lastUsedAt = map["lastUsedAt"] as? Timestamp,
                createdAt = map["createdAt"] as? Timestamp
            )
        }

        /**
         * 가맹점명 정규화 (검색용)
         * - 공백 제거
         * - 소문자 변환
         * - 특수문자 제거
         */
        fun normalizeMerchantName(name: String): String {
            return name
                .lowercase()
                .replace(Regex("[\\s\\-_()（）]"), "")
                .replace(Regex("[^가-힣a-z0-9]"), "")
                .take(30)
        }
    }
}
