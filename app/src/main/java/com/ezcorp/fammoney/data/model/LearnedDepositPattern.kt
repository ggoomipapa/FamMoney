package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * ?ìµ???ê¸ ?¨í´ ëª¨ë¸
 * ?¬ì©?ê? ?ë?¼ë¡ ?ë ¥???ê¸ ?ë¦¼???ìµ?ì¬ ?¤ìë¶???ë ?ì±???¬ì©
 */
data class LearnedDepositPattern(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val savingsGoalId: String = "",  // ?°ê???ëª©í ?ì¶?ID

    // ?¨í´ ?ë³´
    val sampleNotificationText: String = "",  // ?ë³¸ ?ë¦¼ ?ì¤???í
    val bankName: String = "",  // ??ëª
    val accountNumberPattern: String = "",  // ê³ì¢ë²í¸ ?¨í´ (ë§ì¤???¬í¨)
    val senderNameRegex: String = "",  // ?ê¸???´ë¦ ì¶ì¶ ?ê·
val amountRegex: String = "",  // ê¸ì¡ ì¶ì¶ ?ê·?"
    // ?¬ì© ?µê³
    val successCount: Int = 0,  // ?±ê³µ?ì¼ë¡?ë§¤ì¹­???ì
    val failCount: Int = 0,  // ë§¤ì¹­ ?¤í¨ ?ì
    val lastUsedAt: Timestamp? = null,  // ë§ì?ë§??¬ì© ?ê°

    val isActive: Boolean = true,  // ?ì±???¬ë?
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "savingsGoalId" to savingsGoalId,
        "sampleNotificationText" to sampleNotificationText,
        "bankName" to bankName,
        "accountNumberPattern" to accountNumberPattern,
        "senderNameRegex" to senderNameRegex,
        "amountRegex" to amountRegex,
        "successCount" to successCount,
        "failCount" to failCount,
        "lastUsedAt" to lastUsedAt,
        "isActive" to isActive,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): LearnedDepositPattern {
            return LearnedDepositPattern(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                savingsGoalId = map["savingsGoalId"] as? String ?: "",
                sampleNotificationText = map["sampleNotificationText"] as? String ?: "",
                bankName = map["bankName"] as? String ?: "",
                accountNumberPattern = map["accountNumberPattern"] as? String ?: "",
                senderNameRegex = map["senderNameRegex"] as? String ?: "",
                amountRegex = map["amountRegex"] as? String ?: "",
                successCount = (map["successCount"] as? Long)?.toInt() ?: 0,
                failCount = (map["failCount"] as? Long)?.toInt() ?: 0,
                lastUsedAt = map["lastUsedAt"] as? Timestamp,
                isActive = map["isActive"] as? Boolean ?: true,
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }
}
