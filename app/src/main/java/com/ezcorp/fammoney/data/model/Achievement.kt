package com.ezcorp.fammoney.data.model

import java.util.Date

/**
 * 사용자 업적/배지 시스템
 */
data class Achievement(
    val id: String,
    val type: AchievementType,
    val title: String,
    val description: String,
    val icon: String,
    val unlockedAt: Date? = null,
    val progress: Int = 0,
    val targetProgress: Int = 1
) {
    val isUnlocked: Boolean get() = unlockedAt != null
    val progressPercent: Float get() = (progress.toFloat() / targetProgress).coerceIn(0f, 1f)
}

enum class AchievementType {
    FIRST_SURPLUS,
    CONSECUTIVE_SURPLUS,
    SAVINGS_MILESTONE,
    BUDGET_MASTER,
    CATEGORY_SAVER,
    GOAL_ACHIEVER,
    STREAK
}

/**
 * 사용자 레벨 시스템
 */
data class UserLevel(
    val level: Int,
    val title: String,
    val icon: String,
    val minSurplusMonths: Int,
    val minSavingsRate: Int
)

object LevelSystem {
    val levels = listOf(
        UserLevel(1, "절약 입문자", "🌱", 0, 0),
        UserLevel(2, "절약 새싹", "🌿", 1, 5),
        UserLevel(3, "저축 견습생", "🌳", 2, 10),
        UserLevel(4, "절약 달인", "💰", 3, 15),
        UserLevel(5, "저축 마스터", "🏆", 6, 20),
        UserLevel(6, "재정 전문가", "👑", 9, 25),
        UserLevel(7, "저축왕", "🎖", 12, 30)
    )

    fun getLevelForStats(surplusMonths: Int, savingsRate: Int): UserLevel {
        return levels.lastOrNull {
            surplusMonths >= it.minSurplusMonths && savingsRate >= it.minSavingsRate
        } ?: levels.first()
    }

    fun getNextLevel(currentLevel: UserLevel): UserLevel? {
        val currentIndex = levels.indexOf(currentLevel)
        return if (currentIndex < levels.size - 1) levels[currentIndex + 1] else null
    }
}

/**
 * 미리 정의된 업적 목록
 */
object Achievements {
    val allAchievements = listOf(
        Achievement(
            id = "first_surplus",
            type = AchievementType.FIRST_SURPLUS,
            title = "첫 흑자 달성!",
            description = "처음으로 수입이 지출보다 많은 달을 만들었어요",
            icon = "🎉",
            targetProgress = 1
        ),
        Achievement(
            id = "surplus_streak_3",
            type = AchievementType.CONSECUTIVE_SURPLUS,
            title = "절약 새싹",
            description = "3개월 연속 흑자 달성",
            icon = "🌱",
            targetProgress = 3
        ),
        Achievement(
            id = "surplus_streak_6",
            type = AchievementType.CONSECUTIVE_SURPLUS,
            title = "절약 달인",
            description = "6개월 연속 흑자 달성",
            icon = "💪",
            targetProgress = 6
        ),
        Achievement(
            id = "surplus_streak_12",
            type = AchievementType.CONSECUTIVE_SURPLUS,
            title = "재정 마스터",
            description = "12개월 연속 흑자 달성",
            icon = "🏆",
            targetProgress = 12
        ),
        Achievement(
            id = "savings_100k",
            type = AchievementType.SAVINGS_MILESTONE,
            title = "10만원 저축",
            description = "총 10만원을 저축했어요",
            icon = "💵",
            targetProgress = 100000
        ),
        Achievement(
            id = "savings_500k",
            type = AchievementType.SAVINGS_MILESTONE,
            title = "50만원 저축",
            description = "총 50만원을 저축했어요",
            icon = "💰",
            targetProgress = 500000
        ),
        Achievement(
            id = "savings_1m",
            type = AchievementType.SAVINGS_MILESTONE,
            title = "100만원 저축",
            description = "총 100만원을 저축했어요",
            icon = "🎯",
            targetProgress = 1000000
        ),
        Achievement(
            id = "savings_5m",
            type = AchievementType.SAVINGS_MILESTONE,
            title = "500만원 저축",
            description = "총 500만원을 저축했어요",
            icon = "⭐",
            targetProgress = 5000000
        ),
        Achievement(
            id = "savings_10m",
            type = AchievementType.SAVINGS_MILESTONE,
            title = "1000만원 저축",
            description = "총 1000만원을 저축했어요! 대단해요",
            icon = "🌟",
            targetProgress = 10000000
        ),
        Achievement(
            id = "food_saver",
            type = AchievementType.CATEGORY_SAVER,
            title = "식비 절약왕",
            description = "식비를 저번달 대비 20% 줄였어요",
            icon = "🍽️",
            targetProgress = 1
        ),
        Achievement(
            id = "shopping_saver",
            type = AchievementType.CATEGORY_SAVER,
            title = "알뜰 쇼퍼",
            description = "쇼핑비를 저번달 대비 30% 줄였어요",
            icon = "🛍️",
            targetProgress = 1
        ),
        Achievement(
            id = "first_goal",
            type = AchievementType.GOAL_ACHIEVER,
            title = "목표 달성자",
            description = "첫 번째 저축 목표를 달성했어요",
            icon = "🎯",
            targetProgress = 1
        ),
        Achievement(
            id = "goal_master",
            type = AchievementType.GOAL_ACHIEVER,
            title = "목표 달성 마스터",
            description = "5개의 저축 목표를 달성했어요",
            icon = "🏅",
            targetProgress = 5
        )
    )

    fun getById(id: String): Achievement? = allAchievements.find { it.id == id }
}

/**
 * 사용자 업적 상태 저장
 */
data class UserAchievementStatus(
    val odid: String,
    val unlockedAchievementIds: List<String> = emptyList(),
    val achievementProgress: Map<String, Int> = emptyMap(),
    val consecutiveSurplusMonths: Int = 0,
    val totalSavings: Long = 0L,
    val lastSurplusMonth: String? = null,
    val currentLevel: Int = 1
)

/**
 * 투자 추천 레벨
 */
data class InvestmentRecommendation(
    val level: Int,
    val title: String,
    val description: String,
    val icon: String,
    val minMonthlySurplus: Long,
    val recommendations: List<String>
)

object InvestmentGuide {
    val recommendations = listOf(
        InvestmentRecommendation(
            level = 1,
            title = "저축 시작하기",
            description = "먼저 비상금을 만들어보세요",
            icon = "🌱",
            minMonthlySurplus = 0L,
            recommendations = listOf(
                "파킹통장에 비상금 3개월치 모으기",
                "자동이체 적금 시작하기",
                "소액으로 저축습관 만들기"
            )
        ),
        InvestmentRecommendation(
            level = 2,
            title = "적금 도전",
            description = "정기적인 저축으로 목돈 만들기",
            icon = "💰",
            minMonthlySurplus = 50000L,
            recommendations = listOf(
                "월 5만원 자유적금 시작",
                "CMA 계좌 개설로 이자 받기",
                "저축 목표 설정하기"
            )
        ),
        InvestmentRecommendation(
            level = 3,
            title = "투자 입문",
            description = "소액으로 투자 경험 쌓기",
            icon = "📈",
            minMonthlySurplus = 100000L,
            recommendations = listOf(
                "적금 금리 비교하기",
                "ETF 적립식 투자 알아보기",
                "투자 공부 시작하기"
            )
        ),
        InvestmentRecommendation(
            level = 4,
            title = "포트폴리오 구성",
            description = "분산투자로 안정성 확보",
            icon = "📊",
            minMonthlySurplus = 300000L,
            recommendations = listOf(
                "국내/해외 ETF 분산투자",
                "채권형 상품 일부 편입",
                "연금저축 시작 고려"
            )
        ),
        InvestmentRecommendation(
            level = 5,
            title = "자산 증식",
            description = "본격적인 자산 관리 시작",
            icon = "💎",
            minMonthlySurplus = 500000L,
            recommendations = listOf(
                "ISA 계좌 활용",
                "배당주 배당 ETF 투자",
                "부동산 간접투자(리츠) 고려"
            )
        )
    )

    fun getRecommendationForSurplus(monthlySurplus: Long): InvestmentRecommendation {
        return recommendations.lastOrNull { monthlySurplus >= it.minMonthlySurplus }
            ?: recommendations.first()
    }
}
