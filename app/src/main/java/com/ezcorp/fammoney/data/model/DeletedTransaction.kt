package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * 삭제된 거래 (휴지통)
 * 30일 후 자동 삭제
 */
data class DeletedTransaction(
    @DocumentId
    val id: String = "",
    val originalTransaction: Map<String, Any?> = emptyMap(),
    val groupId: String = "",
    val userId: String = "",
    @ServerTimestamp
    val deletedAt: Timestamp? = null,
    // 30일 후 만료 시간
    val expiresAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "originalTransaction" to originalTransaction,
        "groupId" to groupId,
        "userId" to userId,
        "deletedAt" to deletedAt,
        "expiresAt" to expiresAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): DeletedTransaction {
            @Suppress("UNCHECKED_CAST")
            return DeletedTransaction(
                id = id,
                originalTransaction = (map["originalTransaction"] as? Map<String, Any?>) ?: emptyMap(),
                groupId = map["groupId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                deletedAt = map["deletedAt"] as? Timestamp,
                expiresAt = map["expiresAt"] as? Timestamp
            )
        }

        // 30일을 밀리초로
        const val RETENTION_DAYS = 30
        const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000L
    }
}
