package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp

/**
 * ì¤ë³µ ê±°ë ?ë³´ - ì¹´ë? ??ì???ì???ë¦¼???????¬ì©?ìê²??ì¸ë°ê¸° ?í´ ??? */
data class PendingDuplicate(
    val id: String = "",
    val groupId: String = "",
    val userId: String = "",
    val amount: Long = 0,
    val transaction1: DuplicateTransactionInfo = DuplicateTransactionInfo(),
    val transaction2: DuplicateTransactionInfo = DuplicateTransactionInfo(),
    val createdAt: Timestamp = Timestamp.now(),
    val isResolved: Boolean = false,
    val resolvedAt: Timestamp? = null,
    val resolution: DuplicateResolution = DuplicateResolution.PENDING
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "userId" to userId,
        "amount" to amount,
        "transaction1" to transaction1.toMap(),
        "transaction2" to transaction2.toMap(),
        "createdAt" to createdAt,
        "isResolved" to isResolved,
        "resolvedAt" to resolvedAt,
        "resolution" to resolution.name
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): PendingDuplicate {
            @Suppress("UNCHECKED_CAST")
            val tx1Map = map["transaction1"] as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val tx2Map = map["transaction2"] as? Map<String, Any?> ?: emptyMap()

            return PendingDuplicate(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                amount = (map["amount"] as? Number)?.toLong() ?: 0,
                transaction1 = DuplicateTransactionInfo.fromMap(tx1Map),
                transaction2 = DuplicateTransactionInfo.fromMap(tx2Map),
                createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now(),
                isResolved = map["isResolved"] as? Boolean ?: false,
                resolvedAt = map["resolvedAt"] as? Timestamp,
                resolution = try {
                    DuplicateResolution.valueOf(map["resolution"] as? String ?: "PENDING")
                } catch (e: Exception) {
                    DuplicateResolution.PENDING
                }
            )
        }
    }
}

/**
 * ì¤ë³µ ê±°ë ?ë³´
 */
data class DuplicateTransactionInfo(
    val transactionId: String = "",
    val bankId: String = "",
    val bankName: String = "",
    val description: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val notificationTime: Timestamp = Timestamp.now(),
    val originalText: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "transactionId" to transactionId,
        "bankId" to bankId,
        "bankName" to bankName,
        "description" to description,
        "type" to type.name,
        "notificationTime" to notificationTime,
        "originalText" to originalText
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): DuplicateTransactionInfo {
            return DuplicateTransactionInfo(
                transactionId = map["transactionId"] as? String ?: "",
                bankId = map["bankId"] as? String ?: "",
                bankName = map["bankName"] as? String ?: "",
                description = map["description"] as? String ?: "",
                type = try {
                    TransactionType.valueOf(map["type"] as? String ?: "EXPENSE")
                } catch (e: Exception) {
                    TransactionType.EXPENSE
                },
                notificationTime = map["notificationTime"] as? Timestamp ?: Timestamp.now(),
                originalText = map["originalText"] as? String ?: ""
            )
        }
    }
}

/**
 * ì¤ë³µ ?´ê²° ë°©ë²
 */
enum class DuplicateResolution {
    PENDING,        // ?ì§ ë¯¸ê²°
KEEP_BOTH,      // ????? ì"
    KEEP_FIRST,     // ì²?ë²ì§¸ë§?? ì?
    KEEP_SECOND,    // ??ë²ì§¸ë§?? ì?
    DELETE_BOTH     // ?????? 
}

/**
 * ì¤ë³µ ì²ë¦¬ ê·ì¹ (?¬ì©?ê? "?¤ìë¶???ì¼?ê² ?ì©"??? í?ì ?"
 */
data class DuplicateRule(
    val id: String = "",
    val groupId: String = "",
    val bank1Id: String = "",  // ?? ì¹´ë
val bank2Id: String = "",  // ?? ?
val resolution: DuplicateResolution = DuplicateResolution.KEEP_FIRST,
    val createdAt: Timestamp = Timestamp.now()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "bank1Id" to bank1Id,
        "bank2Id" to bank2Id,
        "resolution" to resolution.name,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): DuplicateRule {
            return DuplicateRule(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                bank1Id = map["bank1Id"] as? String ?: "",
                bank2Id = map["bank2Id"] as? String ?: "",
                resolution = try {
                    DuplicateResolution.valueOf(map["resolution"] as? String ?: "KEEP_FIRST")
                } catch (e: Exception) {
                    DuplicateResolution.KEEP_FIRST
                },
                createdAt = map["createdAt"] as? Timestamp ?: Timestamp.now()
            )
        }
    }
}
