package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * 자녀 정보
 *
 * 자녀 라이프사이클:
 * 1. 적립 단계 (allowanceStatus = "saving"): 어린 아이, 부모가 수입/지출 기록
 *    - balance = totalIncome - totalExpense
 * 2. 용돈 단계 (allowanceStatus = "active"): 자녀가 직접 용돈 관리
 *    - 이전 적립금은 preSavingsAmount로 고정
 *    - 새 용돈은 allowanceBalance로 0부터 시작
 */
data class Child(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val birthDate: Timestamp? = null,
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,

    // === 용돈 설정 (Allowance 통합) ===
    val allowanceStatus: String = "saving",  // "saving" = 적립 단계, "active" = 용돈 단계
    val allowanceAmount: Long = 0,           // 정기 용돈 금액
    val allowanceFrequency: String = "monthly", // "weekly" | "monthly"
    val allowanceStartDate: Timestamp? = null,  // 용돈 시작일
    val allowanceBalance: Long = 0,          // 현재 용돈 잔액 (용돈 단계에서만 사용)

    // === 적립금 고정 기록 (용돈 시작 시 저장) ===
    val preSavingsAmount: Long = 0,          // 용돈 시작 전까지 모은 금액
    val preSavingsStartDate: Timestamp? = null, // 적립 시작일 (= createdAt)
    val preSavingsEndDate: Timestamp? = null,   // 적립 종료일 (= allowanceStartDate)

    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    // 잔액: 적립 단계면 수입-지출, 용돈 단계면 용돈 잔액
    val balance: Long get() = if (allowanceStatus == "active") allowanceBalance else (totalIncome - totalExpense)

    // 적립 단계 잔액 (용돈 시작 전 금액 계산용)
    val savingsBalance: Long get() = totalIncome - totalExpense

    // 용돈 단계인지 여부
    val isAllowanceActive: Boolean get() = allowanceStatus == "active"

    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "name" to name,
        "birthDate" to birthDate,
        "totalIncome" to totalIncome,
        "totalExpense" to totalExpense,
        "allowanceStatus" to allowanceStatus,
        "allowanceAmount" to allowanceAmount,
        "allowanceFrequency" to allowanceFrequency,
        "allowanceStartDate" to allowanceStartDate,
        "allowanceBalance" to allowanceBalance,
        "preSavingsAmount" to preSavingsAmount,
        "preSavingsStartDate" to preSavingsStartDate,
        "preSavingsEndDate" to preSavingsEndDate,
        "createdAt" to createdAt
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): Child {
            return Child(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                birthDate = map["birthDate"] as? Timestamp,
                totalIncome = (map["totalIncome"] as? Number)?.toLong() ?: 0,
                totalExpense = (map["totalExpense"] as? Number)?.toLong() ?: 0,
                allowanceStatus = map["allowanceStatus"] as? String ?: "saving",
                allowanceAmount = (map["allowanceAmount"] as? Number)?.toLong() ?: 0,
                allowanceFrequency = map["allowanceFrequency"] as? String ?: "monthly",
                allowanceStartDate = map["allowanceStartDate"] as? Timestamp,
                allowanceBalance = (map["allowanceBalance"] as? Number)?.toLong() ?: 0,
                preSavingsAmount = (map["preSavingsAmount"] as? Number)?.toLong() ?: 0,
                preSavingsStartDate = map["preSavingsStartDate"] as? Timestamp,
                preSavingsEndDate = map["preSavingsEndDate"] as? Timestamp,
                createdAt = map["createdAt"] as? Timestamp
            )
        }
    }
}

/**
 * 자녀 수입 기록
 */
data class ChildIncome(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val childId: String = "",
    val childName: String = "",
    val amount: Long = 0,
    val giverType: IncomeGiverType = IncomeGiverType.OTHER,
    val giverName: String = "",  // 기타인 경우 직접 입력한 이름
    val memo: String = "",
    val recordedByUserId: String = "",
    val recordedByUserName: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val incomeDate: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "childId" to childId,
        "childName" to childName,
        "amount" to amount,
        "giverType" to giverType.name,
        "giverName" to giverName,
        "memo" to memo,
        "recordedByUserId" to recordedByUserId,
        "recordedByUserName" to recordedByUserName,
        "createdAt" to createdAt,
        "incomeDate" to incomeDate
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ChildIncome {
            return ChildIncome(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                childId = map["childId"] as? String ?: "",
                childName = map["childName"] as? String ?: "",
                amount = (map["amount"] as? Number)?.toLong() ?: 0,
                giverType = try {
                    IncomeGiverType.valueOf(map["giverType"] as? String ?: "OTHER")
                } catch (e: Exception) {
                    IncomeGiverType.OTHER
                },
                giverName = map["giverName"] as? String ?: "",
                memo = map["memo"] as? String ?: "",
                recordedByUserId = map["recordedByUserId"] as? String ?: "",
                recordedByUserName = map["recordedByUserName"] as? String ?: "",
                createdAt = map["createdAt"] as? Timestamp,
                incomeDate = map["incomeDate"] as? Timestamp
            )
        }
    }
}

/**
 * 수입 출처 유형
 */
enum class IncomeGiverType(
    val displayName: String,
    val icon: String
) {
    ALLOWANCE("용돈", "💰"),  // 정기 용돈 (부모가 지급)
    FAMILY("가족", "👨‍👩‍👧"),
    FRIEND("친구", "🤝"),
    COLLEAGUE("회사동료", "💼"),
    NEIGHBOR("이웃", "🏠"),
    OTHER("기타", "📝");

    companion object {
        fun fromString(value: String): IncomeGiverType {
            return values().find { it.name == value } ?: OTHER
        }

        // 전체 목록 (용돈 제외 - 자동 입력용)
        val allTypes = listOf(FAMILY, FRIEND, COLLEAGUE, NEIGHBOR, OTHER)

        // 용돈 포함 전체 목록
        val allTypesWithAllowance = listOf(ALLOWANCE, FAMILY, FRIEND, COLLEAGUE, NEIGHBOR, OTHER)
    }
}

/**
 * 자녀 지출 기록
 */
data class ChildExpense(
    @DocumentId
    val id: String = "",
    val groupId: String = "",
    val childId: String = "",
    val childName: String = "",
    val amount: Long = 0,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val description: String = "",  // 사용처 또는 설명
    val memo: String = "",
    val recordedByUserId: String = "",
    val recordedByUserName: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    val expenseDate: Timestamp? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "childId" to childId,
        "childName" to childName,
        "amount" to amount,
        "category" to category.name,
        "description" to description,
        "memo" to memo,
        "recordedByUserId" to recordedByUserId,
        "recordedByUserName" to recordedByUserName,
        "createdAt" to createdAt,
        "expenseDate" to expenseDate
    )

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ChildExpense {
            return ChildExpense(
                id = id,
                groupId = map["groupId"] as? String ?: "",
                childId = map["childId"] as? String ?: "",
                childName = map["childName"] as? String ?: "",
                amount = (map["amount"] as? Number)?.toLong() ?: 0,
                category = try {
                    ExpenseCategory.valueOf(map["category"] as? String ?: "OTHER")
                } catch (e: Exception) {
                    ExpenseCategory.OTHER
                },
                description = map["description"] as? String ?: "",
                memo = map["memo"] as? String ?: "",
                recordedByUserId = map["recordedByUserId"] as? String ?: "",
                recordedByUserName = map["recordedByUserName"] as? String ?: "",
                createdAt = map["createdAt"] as? Timestamp,
                expenseDate = map["expenseDate"] as? Timestamp
            )
        }
    }
}

/**
 * 자녀 지출 카테고리
 */
enum class ExpenseCategory(
    val displayName: String,
    val icon: String
) {
    // 먹거리
    SNACK("간식", "🍪"),
    DRINK("음료", "🥤"),
    MEAL("식사", "🍽️"),

    // 장난감/취미
    TOY("장난감", "🧸"),
    GAME("게임", "🎮"),
    BOOK("책", "📚"),
    STATIONERY("문구", "✏️"),

    // 생활
    CLOTHING("옷", "👕"),
    ACCESSORY("악세서리", "💍"),

    // 저축/기부
    SAVINGS("저축", "🏦"),

    // 기타
    GIFT("선물", "🎁"),
    DONATION("기부", "❤️"),
    OTHER("기타", "📝");

    companion object {
        fun fromString(value: String): ExpenseCategory {
            return values().find { it.name == value } ?: OTHER
        }

        // 먹거리 그룹
        val foodGroup = listOf(SNACK, DRINK, MEAL)
        // 장난감/취미 그룹
        val hobbyGroup = listOf(TOY, GAME, BOOK, STATIONERY)
        // 생활 그룹
        val lifestyleGroup = listOf(CLOTHING, ACCESSORY)
        // 저축/기타 그룹
        val otherGroup = listOf(SAVINGS, GIFT, DONATION, OTHER)
    }
}
