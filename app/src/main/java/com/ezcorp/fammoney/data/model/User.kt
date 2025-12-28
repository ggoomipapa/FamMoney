package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class User(
    @DocumentId
    val id: String = "",
    val authUid: String = "",
    val name: String = "",  // ?ë¤??(?±ì???ì?ë ?´ë¦)
    val realName: String = "",  // ?¤ëª (????ê¸ ???¬ì©?ë ?´ë¦)
    val aliasNames: List<String> = emptyList(),  // ì¶ê? ë³ì¹­ ëª©ë¡ (?ê¸ ë§¤ì¹­?"
    val email: String? = null,
    val groupId: String = "",
    // ?¤ì¤ ê°ê³ë? ì§
val groupIds: List<String> = emptyList(),
    val activeGroupId: String = "",
    val selectedBankIds: List<String> = emptyList(),
    val isOwner: Boolean = false,
    val isAnonymous: Boolean = true,
    val fcmToken: String? = null,
    val notifyGroupOnTransaction: Boolean = true,
    val receiveGroupNotifications: Boolean = true,
    // ê³µì  ë²ì ?¤ì  - ??? ì§ ?´í??ê±°ëë§??¤ë¥¸ ë©¤ë²?ê² ê³µì 
    val shareFromDate: Timestamp? = null,
    // ?¨ê¸¸ ê±°ë ID ëª©ë¡ - ?¹ì  ê±°ëë§??¨ê¸°ê¸?
    val hiddenTransactionIds: List<String> = emptyList(),
    // ??  (parent, child)
    val role: String = "parent",
    // êµ¬ë ?ë³´
    val subscriptionType: String = "free", // free, connect, connect_plus, forever
    val subscriptionExpiry: Timestamp? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val deviceId: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "authUid" to authUid,
        "name" to name,
        "realName" to realName,
        "aliasNames" to aliasNames,
        "email" to email,
        "groupId" to groupId,
        "groupIds" to groupIds,
        "activeGroupId" to activeGroupId,
        "selectedBankIds" to selectedBankIds,
        "isOwner" to isOwner,
        "isAnonymous" to isAnonymous,
        "fcmToken" to fcmToken,
        "notifyGroupOnTransaction" to notifyGroupOnTransaction,
        "receiveGroupNotifications" to receiveGroupNotifications,
        "shareFromDate" to shareFromDate,
        "hiddenTransactionIds" to hiddenTransactionIds,
        "role" to role,
        "subscriptionType" to subscriptionType,
        "subscriptionExpiry" to subscriptionExpiry,
        "createdAt" to createdAt,
        "deviceId" to deviceId
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): User {
            @Suppress("UNCHECKED_CAST")
            return User(
                id = id,
                authUid = map["authUid"] as? String ?: "",
                name = map["name"] as? String ?: "",
                realName = map["realName"] as? String ?: "",
                aliasNames = (map["aliasNames"] as? List<String>) ?: emptyList(),
                email = map["email"] as? String,
                groupId = map["groupId"] as? String ?: "",
                groupIds = (map["groupIds"] as? List<String>) ?: emptyList(),
                activeGroupId = map["activeGroupId"] as? String ?: "",
                selectedBankIds = (map["selectedBankIds"] as? List<String>) ?: emptyList(),
                isOwner = map["isOwner"] as? Boolean ?: false,
                isAnonymous = map["isAnonymous"] as? Boolean ?: true,
                fcmToken = map["fcmToken"] as? String,
                notifyGroupOnTransaction = map["notifyGroupOnTransaction"] as? Boolean ?: true,
                receiveGroupNotifications = map["receiveGroupNotifications"] as? Boolean ?: true,
                shareFromDate = map["shareFromDate"] as? Timestamp,
                hiddenTransactionIds = (map["hiddenTransactionIds"] as? List<String>) ?: emptyList(),
                role = map["role"] as? String ?: "parent",
                subscriptionType = map["subscriptionType"] as? String ?: "free",
                subscriptionExpiry = map["subscriptionExpiry"] as? Timestamp,
                createdAt = map["createdAt"] as? Timestamp,
                deviceId = map["deviceId"] as? String ?: ""
            )
        }
    }
}

data class Group(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val ownerUserId: String = "",
    val memberIds: List<String> = emptyList(),
    val childIncomeEnabled: Boolean = false,
    // ?µì¥ ?ê³  ê¸°ë¥
    val balanceEnabled: Boolean = false,
    val initialBalance: Long = 0,
    val currentBalance: Long = 0,
    // ê·¸ë£¹ ?ë²¨ ?¤ì 
    val cashManagementEnabled: Boolean = false,
    val highAmountThreshold: Long = 100000L,
    // êµ¬ë ?ë³´ (ë°©ì¥ ê¸°ì")
    val subscriptionType: String = "free", // free, connect, connect_plus, forever
    val maxMembers: Int = 1, // ë¬´ë£: 1, connect: 10, connect_plus: ë¬´ì 
@ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "inviteCode" to inviteCode,
        "ownerUserId" to ownerUserId,
        "memberIds" to memberIds,
        "childIncomeEnabled" to childIncomeEnabled,
        "balanceEnabled" to balanceEnabled,
        "initialBalance" to initialBalance,
        "currentBalance" to currentBalance,
        "cashManagementEnabled" to cashManagementEnabled,
        "highAmountThreshold" to highAmountThreshold,
        "subscriptionType" to subscriptionType,
        "maxMembers" to maxMembers,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Group {
            @Suppress("UNCHECKED_CAST")
            return Group(
                id = id,
                name = map["name"] as? String ?: "",
                inviteCode = map["inviteCode"] as? String ?: "",
                ownerUserId = map["ownerUserId"] as? String ?: "",
                memberIds = (map["memberIds"] as? List<String>) ?: emptyList(),
                childIncomeEnabled = map["childIncomeEnabled"] as? Boolean ?: false,
                balanceEnabled = map["balanceEnabled"] as? Boolean ?: false,
                initialBalance = (map["initialBalance"] as? Long) ?: 0L,
                currentBalance = (map["currentBalance"] as? Long) ?: 0L,
                cashManagementEnabled = map["cashManagementEnabled"] as? Boolean ?: false,
                highAmountThreshold = (map["highAmountThreshold"] as? Long) ?: 100000L,
                subscriptionType = map["subscriptionType"] as? String ?: "free",
                maxMembers = (map["maxMembers"] as? Long)?.toInt() ?: 1,
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }
}

// ?©ë ëª¨ë¸
data class Allowance(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val childUserId: String = "",
    val childName: String = "",
    val parentUserId: String = "",
    val amount: Long = 0,
    val frequency: String = "monthly", // weekly, monthly
    val nextPaymentDate: Timestamp? = null,
    val balance: Long = 0, // ?ì¬ ?©ë ?ì¡
    val isActive: Boolean = true,
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "childUserId" to childUserId,
        "childName" to childName,
        "parentUserId" to parentUserId,
        "amount" to amount,
        "frequency" to frequency,
        "nextPaymentDate" to nextPaymentDate,
        "balance" to balance,
        "isActive" to isActive,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Allowance {
            return Allowance(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                childUserId = map["childUserId"] as? String ?: "",
                childName = map["childName"] as? String ?: "",
                parentUserId = map["parentUserId"] as? String ?: "",
                amount = (map["amount"] as? Long) ?: 0L,
                frequency = map["frequency"] as? String ?: "monthly",
                nextPaymentDate = map["nextPaymentDate"] as? Timestamp,
                balance = (map["balance"] as? Long) ?: 0L,
                isActive = map["isActive"] as? Boolean ?: true,
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }
}

// ê°ì¡?ëª©í ?ì¶?ëª¨ë¸
data class SavingsGoal(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val targetAmount: Long = 0,
    val currentAmount: Long = 0,
    val deadline: Timestamp? = null,
    val iconEmoji: String = "?¯",
    val isCompleted: Boolean = false,
    // ?ë ?ê¸ ?°ë ?¤ì 
    val linkedAccountNumber: String = "",  // ?°ë ê³ì¢ë²í¸
    val linkedBankName: String = "",  // ?°ë ??ëª (?ì?"
    val autoDepositEnabled: Boolean = false,  // ?ë ?ê¸ ê°ì? ?ì±
@ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "name" to name,
        "targetAmount" to targetAmount,
        "currentAmount" to currentAmount,
        "deadline" to deadline,
        "iconEmoji" to iconEmoji,
        "isCompleted" to isCompleted,
        "linkedAccountNumber" to linkedAccountNumber,
        "linkedBankName" to linkedBankName,
        "autoDepositEnabled" to autoDepositEnabled,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): SavingsGoal {
            return SavingsGoal(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                targetAmount = (map["targetAmount"] as? Long) ?: 0L,
                currentAmount = (map["currentAmount"] as? Long) ?: 0L,
                deadline = map["deadline"] as? Timestamp,
                iconEmoji = map["iconEmoji"] as? String ?: "?¯",
                isCompleted = map["isCompleted"] as? Boolean ?: false,
                linkedAccountNumber = map["linkedAccountNumber"] as? String ?: "",
                linkedBankName = map["linkedBankName"] as? String ?: "",
                autoDepositEnabled = map["autoDepositEnabled"] as? Boolean ?: false,
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }

    val progress: Float
        get() = if (targetAmount > 0) (currentAmount.toFloat() / targetAmount.toFloat()).coerceIn(0f, 1f) else 0f
}

// ?ì¶?ê¸°ì¬ ?´ì­
data class SavingsContribution(
    @DocumentId
    val id: String = "",
    val goalId: String = "",
    val userId: String = "",
    val userName: String = "",
    val amount: Long = 0,
    // ?ë ê°ì? ê´
val isAutoDetected: Boolean = false,  // ?ë ê°ì? ?¬ë"
    val detectedSenderName: String = "",  // ?ì±???ê¸???´ë¦ (?ë³¸)
    val matchConfidence: String = "high",  // ë§¤ì¹­ ? ë¢°?? high, medium, low, manual
    val originalNotificationText: String = "",  // ?ë³¸ ?ë¦¼ ?ì¤
val needsReview: Boolean = false,  // ?ë ?ì¸ ?ì ?¬ë"
    // ?ì  ?´ë ¥
    val isModified: Boolean = false,  // ?ì  ?¬ë?
    val modifiedBy: String = "",  // ?ì ???¬ë ID
    val modifiedAt: Timestamp? = null,  // ?ì  ?¼ì
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "goalId" to goalId,
        "userId" to userId,
        "userName" to userName,
        "amount" to amount,
        "isAutoDetected" to isAutoDetected,
        "detectedSenderName" to detectedSenderName,
        "matchConfidence" to matchConfidence,
        "originalNotificationText" to originalNotificationText,
        "needsReview" to needsReview,
        "isModified" to isModified,
        "modifiedBy" to modifiedBy,
        "modifiedAt" to modifiedAt,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): SavingsContribution {
            return SavingsContribution(
                id = id,
                goalId = map["goalId"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                userName = map["userName"] as? String ?: "",
                amount = (map["amount"] as? Long) ?: 0L,
                isAutoDetected = map["isAutoDetected"] as? Boolean ?: false,
                detectedSenderName = map["detectedSenderName"] as? String ?: "",
                matchConfidence = map["matchConfidence"] as? String ?: "high",
                originalNotificationText = map["originalNotificationText"] as? String ?: "",
                needsReview = map["needsReview"] as? Boolean ?: false,
                isModified = map["isModified"] as? Boolean ?: false,
                modifiedBy = map["modifiedBy"] as? String ?: "",
                modifiedAt = map["modifiedAt"] as? Timestamp,
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }
}
