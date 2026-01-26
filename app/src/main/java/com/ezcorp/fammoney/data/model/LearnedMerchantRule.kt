package com.ezcorp.fammoney.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 학습된 사용처 규칙 (Room Entity)
 *
 * 정책 3 준수: 우선순위
 *   1. User-confirmed (사용자가 직접 수정)
 *   2. Signature-based (자동 학습)
 *   3. Heuristic (N-best 파싱)
 *
 * 정책 2 준수: 사용자 수정 시 학습하여 재발 방지
 */
@Entity(
    tableName = "learned_merchant_rules",
    indices = [
        Index(value = ["signature"], unique = true),
        Index(value = ["updatedAt"]),
        Index(value = ["groupId"])
    ]
)
data class LearnedMerchantRule(
    @PrimaryKey
    val id: String,                     // UUID

    val groupId: String,                // 그룹 ID (가족 단위 공유)

    val signature: String,              // 패턴 시그니처 (unique)
                                        // 형식: "pkg|텍스트패턴해시"

    val merchant: String,               // 확정된 사용처

    val sourceHint: String? = null,     // 패키지명 또는 발신자 힌트

    val isUserConfirmed: Boolean = false, // 사용자가 직접 확정했는지 여부

    val hitCount: Int = 1,              // 매칭 횟수 (신뢰도 가점용)

    val patternVersion: Int = 1,        // 패턴 버전 (signature 생성 로직 변경 시)

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        private val KRW_AMOUNT = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")

        /**
         * Signature 생성
         *
         * @param cleanText 전처리된 알림 텍스트
         * @param pkg 패키지명 (알림 기반) 또는 sender (SMS 기반)
         * @return signature 문자열
         */
        fun generateSignature(cleanText: String, pkg: String?): String {
            val hint = pkg?.lowercase()?.take(64) ?: "unknown"

            // 금액과 숫자를 제거하여 패턴만 추출
            val pattern = cleanText
                .replace(KRW_AMOUNT, " ")
                .replace(Regex("""\d+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(60)
                .lowercase()

            return "$hint|$pattern"
        }

        /**
         * hitCount 기반 신뢰도 가점 계산
         * 정책 3: +0.05 per 10 hits, max +0.1
         */
        fun confidenceBonus(hitCount: Int): Double {
            val bonus = (hitCount / 10) * 0.05
            return bonus.coerceAtMost(0.1)
        }
    }
}
