package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

data class User(
    @DocumentId
    val id: String = "",
    val authUid: String = "",
    val name: String = "",  // 닉네임(등록시 사용하는 이름)
    val realName: String = "",  // 실명 (입금 알림 매칭에 사용하는 이름)
    val aliasNames: List<String> = emptyList(),  // 추가 별칭 목록 (입금 매칭용)
    val email: String? = null,
    val groupId: String = "",
    // 다중 가계부 지원
    val groupIds: List<String> = emptyList(),
    val activeGroupId: String = "",
    val selectedBankIds: List<String> = emptyList(),
    val isOwner: Boolean = false,
    val isAnonymous: Boolean = true,
    val fcmToken: String? = null,
    val notifyGroupOnTransaction: Boolean = true,
    val receiveGroupNotifications: Boolean = true,
    // 공유 범위 설정 - 이 날짜 이후의 거래만 다른 멤버에게 공유
    val shareFromDate: Timestamp? = null,
    // 숨길 거래 ID 목록 - 특정 거래만 숨기기
    val hiddenTransactionIds: List<String> = emptyList(),
    // 현금 거래 공유 여부
    val shareCashTransactions: Boolean = true,
    // 용돈 관리 공유 여부
    val shareAllowance: Boolean = true,
    // 역할 (parent, child)
    val role: String = "parent",
    // 구독 정보
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
        "shareCashTransactions" to shareCashTransactions,
        "shareAllowance" to shareAllowance,
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
                shareCashTransactions = map["shareCashTransactions"] as? Boolean ?: true,
                shareAllowance = map["shareAllowance"] as? Boolean ?: true,
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
    // 통장 잔고 기능
    val balanceEnabled: Boolean = false,
    val initialBalance: Long = 0,
    val currentBalance: Long = 0,
    // 그룹 레벨 설정
    val cashManagementEnabled: Boolean = false,
    val highAmountThreshold: Long = 100000L,
    // 구독 정보 (방장 기준)
    val subscriptionType: String = "free", // free, connect, connect_plus, forever
    val maxMembers: Int = 1, // 무료: 1, connect: 10, connect_plus: 무제한
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

// 용돈 모델
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
    val balance: Long = 0, // 현재 용돈 잔액
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

// 가족 목표 저축 모델
data class SavingsGoal(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val targetAmount: Long = 0,
    val currentAmount: Long = 0,
    val deadline: Timestamp? = null,
    val iconEmoji: String = "\uD83C\uDFAF",
    val isCompleted: Boolean = false,
    // 자동 입금 연동 설정
    val linkedAccountNumber: String = "",  // 연동 계좌번호
    val linkedBankName: String = "",  // 연동 은행명 (신한 등)
    val autoDepositEnabled: Boolean = false,  // 자동 입금 감지 활성화
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
                iconEmoji = map["iconEmoji"] as? String ?: "\uD83C\uDFAF",
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

// 저축 기여 이력
data class SavingsContribution(
    @DocumentId
    val id: String = "",
    val goalId: String = "",
    val userId: String = "",
    val userName: String = "",
    val amount: Long = 0,
    // 자동 감지 관련
    val isAutoDetected: Boolean = false,  // 자동 감지 여부
    val detectedSenderName: String = "",  // 감지된 입금자 이름 (원본)
    val matchConfidence: String = "high",  // 매칭 신뢰도: high, medium, low, manual
    val originalNotificationText: String = "",  // 원본 알림 텍스트
    val needsReview: Boolean = false,  // 수동 확인 필요 여부
    // 수정 이력
    val isModified: Boolean = false,  // 수정 여부
    val modifiedBy: String = "",  // 수정한 사용자 ID
    val modifiedAt: Timestamp? = null,  // 수정 일시
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
