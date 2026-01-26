package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class Transaction(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: Long = 0,
    val bankId: String = "",
    val bankName: String = "",
    val description: String = "",
    val category: String = "",           // 소비유형 (SpendingCategory.name)
    val incomeSubType: String = "",      // 수입 세부 유형 (IncomeSubType.name)
    val expenseSubType: String = "",     // 지출 세부 유형 (ExpenseSubType.name)
    val merchant: String = "",           // 가맹점 (Merchant.id)
    val merchantName: String = "",       // 가맹점 이름 (표시용)
    val memo: String = "",
    val source: InputSource = InputSource.NOTIFICATION,
    val originalText: String = "",

    // === 자녀 용돈 연결 ===
    val linkedChildId: String = "",      // 연결된 자녀 ID (자녀 용돈 카테고리 선택 시)
    val linkedChildName: String = "",    // 연결된 자녀 이름 (표시용)

    // === 태그/그룹 (여행, 이벤트 등) ===
    val tagId: String = "",              // 태그 ID
    val tagName: String = "",            // 태그 이름 (표시용)

    // === N-best 파싱 시스템 (정책 1, 2 관련) ===
    val confidence: Double = 0.0,        // 파싱 신뢰도 (0.0 ~ 1.0)
    val merchantCandidates: List<String> = emptyList(),  // 사용처 후보 목록

    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val transactionDate: Timestamp? = null,
    val isConfirmed: Boolean = true
) {
    // 검토 필요 여부 (정책 1: confidence < 0.75)
    val needsReview: Boolean get() = confidence < 0.75 && confidence > 0.0

    // 폴백 UX 필요 여부 (정책 1: confidence < 0.50)
    val needsFallbackUX: Boolean get() = confidence < 0.50
    // 자녀 용돈에 연결된 거래인지 확인
    val isLinkedToChild: Boolean get() = linkedChildId.isNotEmpty()

    // 태그가 있는 거래인지 확인
    val hasTag: Boolean get() = tagId.isNotEmpty()

    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "userId" to userId,
        "userName" to userName,
        "type" to type.name,
        "amount" to amount,
        "bankId" to bankId,
        "bankName" to bankName,
        "description" to description,
        "category" to category,
        "incomeSubType" to incomeSubType,
        "expenseSubType" to expenseSubType,
        "merchant" to merchant,
        "merchantName" to merchantName,
        "memo" to memo,
        "source" to source.name,
        "originalText" to originalText,
        "linkedChildId" to linkedChildId,
        "linkedChildName" to linkedChildName,
        "tagId" to tagId,
        "tagName" to tagName,
        "confidence" to confidence,
        "merchantCandidates" to merchantCandidates,
        "createdAt" to createdAt,
        "transactionDate" to transactionDate,
        "isConfirmed" to isConfirmed
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Transaction {
            return Transaction(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                type = TransactionType.valueOf(map["type"] as? String ?: "EXPENSE"),
                amount = (map["amount"] as? Number)?.toLong() ?: 0,
                bankId = map["bankId"] as? String ?: "",
                bankName = map["bankName"] as? String ?: "",
                description = map["description"] as? String ?: "",
                category = map["category"] as? String ?: "",
                incomeSubType = map["incomeSubType"] as? String ?: "",
                expenseSubType = map["expenseSubType"] as? String ?: "",
                merchant = map["merchant"] as? String ?: "",
                merchantName = map["merchantName"] as? String ?: "",
                memo = map["memo"] as? String ?: "",
                source = InputSource.valueOf(map["source"] as? String ?: "NOTIFICATION"),
                originalText = map["originalText"] as? String ?: "",
                linkedChildId = map["linkedChildId"] as? String ?: "",
                linkedChildName = map["linkedChildName"] as? String ?: "",
                tagId = map["tagId"] as? String ?: "",
                tagName = map["tagName"] as? String ?: "",
                confidence = (map["confidence"] as? Number)?.toDouble() ?: 0.0,
                merchantCandidates = (map["merchantCandidates"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = map["createdAt"] as? Timestamp,
                transactionDate = map["transactionDate"] as? Timestamp,
                isConfirmed = map["isConfirmed"] as? Boolean ?: true
            )
        }
    }
}

enum class TransactionType {
    INCOME,
    EXPENSE
}

enum class InputSource {
    NOTIFICATION,
    MANUAL_TEXT_INPUT,
    MANUAL_ENTRY
}
