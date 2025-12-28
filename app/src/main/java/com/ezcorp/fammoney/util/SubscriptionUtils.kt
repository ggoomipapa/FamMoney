package com.ezcorp.fammoney.util

import com.ezcorp.fammoney.data.model.Group

/**
 * 디버그 빌드 설정 - Application에서 설정됨
 */
object DebugConfig {
    var isDebugBuild: Boolean = false
        private set

    fun initialize(debug: Boolean) {
        isDebugBuild = debug
    }
}

private val isDebugBuild: Boolean
    get() = DebugConfig.isDebugBuild

/**
 * 디버그 빌드에서는 자동으로 프리미엄(forever) 구독으로 처리
 * 릴리즈 빌드에서는 실제 구독 상태 반환
 */
val Group.effectiveSubscriptionType: String
    get() = if (isDebugBuild) "forever" else subscriptionType

/**
 * 디버그 빌드에서는 무제한 멤버 허용
 */
val Group.effectiveMaxMembers: Int
    get() = if (isDebugBuild) Int.MAX_VALUE else maxMembers

/**
 * 프리미엄 기능 사용 가능 여부
 */
val Group.isPremium: Boolean
    get() = effectiveSubscriptionType != "free"

/**
 * 구독 타입 표시 이름 (디버그 모드 표시 포함)
 */
val Group.subscriptionDisplayName: String
    get() = when (effectiveSubscriptionType) {
        "connect" -> "팸머니 커넥트"
        "connect_plus" -> "팸머니 커넥트+"
        "forever" -> if (isDebugBuild) "팸머니 포에버(디버그)" else "팸머니 포에버"
        else -> "무료"
    }

/**
 * 구독 타입이 null인 경우에도 안전하게 처리
 */
fun Group?.effectiveSubscription(): String {
    if (this == null) return if (isDebugBuild) "forever" else "free"
    return effectiveSubscriptionType
}
