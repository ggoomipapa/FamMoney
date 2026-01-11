package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp

/**
 * 거래내역 태그/그룹 (여행, 이벤트 등)
 * 예: "강릉 여행", "결혼식", "이사 비용" 등
 */
data class TransactionTag(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",                    // 태그 이름 (예: "강릉 여행")
    val color: String = "#4CAF50",            // 태그 색상
    val icon: String = "label",               // 아이콘 이름
    val isActive: Boolean = false,            // 현재 활성화된 태그인지 (새 거래에 자동 적용)
    val transactionCount: Int = 0,            // 태그된 거래 수
    val totalExpense: Long = 0,               // 총 지출
    val totalIncome: Long = 0,                // 총 수입
    val startDate: Timestamp? = null,         // 시작 날짜
    val endDate: Timestamp? = null,           // 종료 날짜
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "name" to name,
        "color" to color,
        "icon" to icon,
        "isActive" to isActive,
        "transactionCount" to transactionCount,
        "totalExpense" to totalExpense,
        "totalIncome" to totalIncome,
        "startDate" to startDate,
        "endDate" to endDate,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): TransactionTag {
            return TransactionTag(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                color = map["color"] as? String ?: "#4CAF50",
                icon = map["icon"] as? String ?: "label",
                isActive = map["isActive"] as? Boolean ?: false,
                transactionCount = (map["transactionCount"] as? Long)?.toInt() ?: 0,
                totalExpense = map["totalExpense"] as? Long ?: 0,
                totalIncome = map["totalIncome"] as? Long ?: 0,
                startDate = map["startDate"] as? Timestamp,
                endDate = map["endDate"] as? Timestamp,
                createdAt = map["createdAt"] as? Timestamp,
                updatedAt = map["updatedAt"] as? Timestamp
            )
        }

        // 기본 태그 색상 옵션
        val TAG_COLORS = listOf(
            "#4CAF50", // Green
            "#2196F3", // Blue
            "#FF9800", // Orange
            "#9C27B0", // Purple
            "#E91E63", // Pink
            "#00BCD4", // Cyan
            "#795548", // Brown
            "#607D8B"  // Blue Grey
        )

        // 기본 태그 아이콘 옵션
        val TAG_ICONS = listOf(
            "flight" to "여행",
            "hotel" to "숙박",
            "restaurant" to "식사",
            "celebration" to "이벤트",
            "home" to "집",
            "work" to "업무",
            "shopping_cart" to "쇼핑",
            "local_hospital" to "의료",
            "school" to "교육",
            "label" to "기타"
        )
    }
}
