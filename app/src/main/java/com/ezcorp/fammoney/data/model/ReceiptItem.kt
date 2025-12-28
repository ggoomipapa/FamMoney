package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp

/**
 * 영수증의 인식된 개별 항목
 */
data class ReceiptItem(
    val id: String = "",
    val transactionId: String = "",  // 연결된 거래 ID
    val groupId: String = "",
    val itemName: String = "",       // 항목명
    val quantity: Int = 1,           // 수량
    val unitPrice: Long = 0,         // 단가
    val totalPrice: Long = 0,        // 총가격 (수량 * 단가)
    val category: String = "",       // 항목 카테고리 (식료품, 음료, 등)
    val rawText: String = "",        // OCR 원본 텍스트
    val createdAt: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "transactionId" to transactionId,
        "groupId" to groupId,
        "itemName" to itemName,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "totalPrice" to totalPrice,
        "category" to category,
        "rawText" to rawText,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ReceiptItem {
            return ReceiptItem(
                id = id,
                transactionId = map["transactionId"] as? String ?: "",
                groupId = map["groupId"] as? String ?: "",
                itemName = map["itemName"] as? String ?: "",
                quantity = (map["quantity"] as? Long)?.toInt() ?: 1,
                unitPrice = map["unitPrice"] as? Long ?: 0,
                totalPrice = map["totalPrice"] as? Long ?: 0,
                category = map["category"] as? String ?: "",
                rawText = map["rawText"] as? String ?: "",
                createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
            )
        }
    }
}

/**
 * 항목 가격 이력 (가격 변동 추적용)
 */
data class PriceHistory(
    val id: String = "",
    val groupId: String = "",
    val itemName: String = "",       // 정규화된 항목명
    val price: Long = 0,             // 가격
    val merchantName: String = "",   // 구매처
    val purchaseDate: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "itemName" to itemName,
        "price" to price,
        "merchantName" to merchantName,
        "purchaseDate" to purchaseDate
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): PriceHistory {
            return PriceHistory(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                itemName = map["itemName"] as? String ?: "",
                price = map["price"] as? Long ?: 0,
                merchantName = map["merchantName"] as? String ?: "",
                purchaseDate = map["purchaseDate"] as? Timestamp ?: Timestamp.now()
            )
        }
    }
}
