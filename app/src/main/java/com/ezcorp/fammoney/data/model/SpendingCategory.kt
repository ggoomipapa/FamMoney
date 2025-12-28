package com.ezcorp.fammoney.data.model

/**
 * 소비 유형 (지출 카테고리)
 */
enum class SpendingCategory(
    val displayName: String,
    val icon: String
) {
    // 식비
    FOOD("식비", "🍚"),
    CAFE_SNACK("카페/간식", "☕"),
    DINING_OUT("외식", "🍽️"),
    DELIVERY("배달", "🛵"),
    GROCERY("장보기/마트", "🛒"),

    // 생활
    DAILY_NECESSITIES("생활용품", "🧴"),
    HEALTH("건강/의료", "💊"),
    BEAUTY("뷰티/미용", "💄"),
    PET("반려동물", "🐾"),

    // 쇼핑
    CLOTHING("의류", "👕"),
    SHOES_BAG("신발/가방", "👟"),
    ELECTRONICS("전자기기", "📱"),
    ONLINE_SHOPPING("온라인쇼핑", "📦"),

    // 주거
    RENT("월세", "🏠"),
    MAINTENANCE_FEE("관리비", "🏢"),
    UTILITIES("공과금", "💡"),
    INTERNET_PHONE("통신비", "📶"),

    // 금융
    LOAN("대출상환", "🏦"),
    INTEREST("이자", "💰"),
    INSURANCE("보험", "🛡️"),
    SAVINGS("저축/투자", "📈"),
    TAX("세금", "📋"),

    // 교통
    TRANSPORTATION("대중교통", "🚌"),
    TAXI("택시", "🚕"),
    CAR("자동차유지", "🚗"),
    PARKING("주차", "🅿️"),

    // 문화/여가
    OTT("OTT/구독", "📺"),
    MUSIC("음악/스트리밍", "🎵"),
    GAME("게임", "🎮"),
    HOBBY("취미", "🎨"),
    MOVIE("영화/공연", "🎬"),
    TRAVEL("여행", "✈️"),
    SPORTS("운동/스포츠", "⚽"),
    BOOK("도서", "📚"),

    // 교육
    EDUCATION("교육", "🎓"),
    ACADEMY("학원", "📖"),
    ONLINE_COURSE("온라인강의", "💻"),

    // 경조사
    GIFT("선물", "🎁"),
    FAMILY_EVENT("경조사비", "💐"),
    DONATION("기부", "❤️"),

    // 기타
    ATM("ATM출금", "🏧"),
    TRANSFER("이체", "💸"),
    OTHER("기타", "📝"),
    UNCATEGORIZED("미분류", "❓"),

    // 자녀 용돈 (동적 카테고리의 기본값)
    CHILD_ALLOWANCE("자녀 용돈", "👶");

    companion object {
        fun fromString(value: String): SpendingCategory {
            // CHILD_ 접두사로 시작하면 자녀 용돈 카테고리로 처리
            if (value?.startsWith("CHILD_") == true && value != "CHILD_ALLOWANCE") {
                return CHILD_ALLOWANCE
            }
            return values().find { it.name == value } ?: UNCATEGORIZED
        }

        fun getCategories(): List<SpendingCategory> = values().toList()

        // 그룹별 카테고리
        val foodGroup = listOf(FOOD, CAFE_SNACK, DINING_OUT, DELIVERY, GROCERY)
        val livingGroup = listOf(DAILY_NECESSITIES, HEALTH, BEAUTY, PET)
        val shoppingGroup = listOf(CLOTHING, SHOES_BAG, ELECTRONICS, ONLINE_SHOPPING)
        val housingGroup = listOf(RENT, MAINTENANCE_FEE, UTILITIES, INTERNET_PHONE)
        val financeGroup = listOf(LOAN, INTEREST, INSURANCE, SAVINGS, TAX)
        val transportGroup = listOf(TRANSPORTATION, TAXI, CAR, PARKING)
        val cultureGroup = listOf(OTT, MUSIC, GAME, HOBBY, MOVIE, TRAVEL, SPORTS, BOOK)
        val educationGroup = listOf(EDUCATION, ACADEMY, ONLINE_COURSE)
        val eventGroup = listOf(GIFT, FAMILY_EVENT, DONATION)
        val otherGroup = listOf(ATM, TRANSFER, OTHER, UNCATEGORIZED)

        // 자녀 용돈은 별도 그룹으로 처리 (동적 생성)
        val childAllowanceGroup = listOf(CHILD_ALLOWANCE)

        /**
         * 자녀 목록에서 동적 카테고리 생성
         * @param children 자녀 목록
         * @return 자녀별 용돈 카테고리 목록
         */
        fun getChildAllowanceCategories(children: List<Child>): List<ChildAllowanceCategory> {
            return children.map { child ->
                ChildAllowanceCategory(
                    childId = child.id,
                    categoryKey = "CHILD_${child.id}",
                    displayName = "${child.name} 용돈",
                    icon = "👶"
                )
            }
        }

        /**
         * 카테고리 키에서 자녀 ID 추출
         * @param categoryKey "CHILD_xxx" 형식의 카테고리 키
         * @return 자녀 ID 또는 null
         */
        fun extractChildIdFromCategory(categoryKey: String): String? {
            if (categoryKey.startsWith("CHILD_") && categoryKey != "CHILD_ALLOWANCE") {
                return categoryKey.removePrefix("CHILD_")
            }
            return null
        }

        /**
         * 자녀 용돈 카테고리인지 확인
         */
        fun isChildAllowanceCategory(categoryKey: String): Boolean {
            return categoryKey.startsWith("CHILD_") && categoryKey != "CHILD_ALLOWANCE"
        }
    }
}

/**
 * 동적 자녀 용돈 카테고리
 * 자녀 목록에서 동적으로 생성되어 HomeScreen의 카테고리 선택에 표시됨
 */
data class ChildAllowanceCategory(
    val childId: String,       // 자녀 ID
    val categoryKey: String,   // "CHILD_{childId}" 형식 (Transaction.category에 저장)
    val displayName: String,   // "민수 용돈" 형식
    val icon: String = "👶"
)

/**
 * 수입 카테고리
 */
enum class IncomeCategory(
    val displayName: String,
    val icon: String
) {
    SALARY("급여", "💵"),
    BONUS("상여금", "🎉"),
    SIDE_JOB("부업", "💼"),
    ALLOWANCE("용돈", "💝"),
    INTEREST_INCOME("이자수입", "🏦"),
    DIVIDEND("배당금", "📈"),
    REFUND("환불", "↩️"),
    CASHBACK("캐시백", "💳"),
    GIFT_RECEIVED("받은 선물", "🎁"),
    TRANSFER_IN("이체입금", "💰"),
    OTHER_INCOME("기타수입", "📝"),
    UNCATEGORIZED("미분류", "❓");

    companion object {
        fun fromString(value: String): IncomeCategory {
            return values().find { it.name == value } ?: UNCATEGORIZED
        }
    }
}

/**
 * 수입 세부 유형 (급여 관련)
 */
enum class IncomeSubType(
    val displayName: String,
    val icon: String
) {
    // 급여 관련
    MONTHLY_SALARY("월급", "💵"),
    VACATION_PAY("휴가비", "🏖️"),
    HOLIDAY_BONUS("명절상여", "🎊"),
    PERFORMANCE_BONUS("성과급", "🏆"),
    OVERTIME_PAY("야근수당", "🌙"),
    INCENTIVE("인센티브", "💰"),
    TAX_REFUND("연말정산", "📋"),

    // 기타 수입
    TRANSFER_RECEIVED("이체받음", "💰"),
    INTEREST("이자", "🏦"),
    REFUND("환불", "↩️"),
    ALLOWANCE("용돈", "💝"),
    GIFT_MONEY("축의금/조의금", "💐"),
    OTHER("기타", "📝"),
    UNCATEGORIZED("미분류", "❓");

    companion object {
        fun fromString(value: String): IncomeSubType {
            return values().find { it.name == value } ?: UNCATEGORIZED
        }

        // 급여 그룹
        val salaryGroup = listOf(MONTHLY_SALARY, VACATION_PAY, HOLIDAY_BONUS, PERFORMANCE_BONUS, OVERTIME_PAY, INCENTIVE, TAX_REFUND)
        // 기타 그룹
        val otherGroup = listOf(TRANSFER_RECEIVED, INTEREST, REFUND, ALLOWANCE, GIFT_MONEY, OTHER)
    }
}

/**
 * 지출 세부 유형 (이체 관련)
 */
enum class ExpenseSubType(
    val displayName: String,
    val icon: String
) {
    // 이체 관련
    TRANSFER_TO_FAMILY("가족이체", "👨‍👩‍👧"),
    TRANSFER_TO_FRIEND("지인이체", "🤝"),
    TRANSFER_SAVINGS("저축이체", "🏦"),
    TRANSFER_LOAN("대출상환", "🏦"),
    TRANSFER_RENT("월세이체", "🏠"),
    TRANSFER_INSURANCE("보험료", "🛡️"),
    TRANSFER_INVESTMENT("투자이체", "📈"),
    TRANSFER_OTHER("기타이체", "💸"),

    // 일반 지출
    CARD_PAYMENT("카드결제", "💳"),
    CASH_PAYMENT("현금결제", "💵"),
    AUTO_PAYMENT("자동이체", "🔄"),
    OTHER("기타", "📝"),
    UNCATEGORIZED("미분류", "❓");

    companion object {
        fun fromString(value: String): ExpenseSubType {
            return values().find { it.name == value } ?: UNCATEGORIZED
        }

        // 이체 그룹
        val transferGroup = listOf(TRANSFER_TO_FAMILY, TRANSFER_TO_FRIEND, TRANSFER_SAVINGS, TRANSFER_LOAN, TRANSFER_RENT, TRANSFER_INSURANCE, TRANSFER_INVESTMENT, TRANSFER_OTHER)
        // 결제 그룹
        val paymentGroup = listOf(CARD_PAYMENT, CASH_PAYMENT, AUTO_PAYMENT, OTHER)
    }
}
