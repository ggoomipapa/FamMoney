package com.ezcorp.fammoney.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 개인정보 마스킹 유틸리티
 *
 * 마스킹 대상:
 *   1. 전화번호
 *   2. 긴 숫자 (카드번호, 계좌번호)
 *   3. 대시 포함 긴 숫자
 *   4. 인증번호/승인번호
 *   5. 수취인 정보
 *   6. 주소
 *
 * 저장 정책:
 *   - 기본: 원문 저장 안 함
 *   - 옵트인: 사용자 동의 시 익명화 원문만 저장
 */
@Singleton
class PrivacyMasker @Inject constructor() {

    companion object {
        // 전화번호 패턴
        private val PHONE_PATTERN = Regex(
            """01\d[- ]?\d{3,4}[- ]?\d{4}"""
        )

        // 긴 숫자 (카드번호, 계좌번호) - 10~19자리
        private val LONG_NUMBER_PATTERN = Regex(
            """(?<!\d)\d{10,19}(?!\d)"""
        )

        // 대시 포함 긴 숫자 (카드번호, 계좌번호)
        private val DASHED_NUMBER_PATTERN = Regex(
            """(?<!\d)\d{2,6}[-]\d{2,6}(?:[-]\d{2,6}){1,3}(?!\d)"""
        )

        // 별표 마스킹된 계좌/카드번호 (부분 노출된 것도 전체 마스킹)
        private val PARTIAL_MASKED_PATTERN = Regex(
            """(?<!\d)\d{3,6}[*]+\d{0,6}(?!\d)"""
        )

        // 인증번호/승인번호
        private val AUTH_CODE_PATTERN = Regex(
            """(?:인증번호|OTP|승인번호|확인번호|보안번호)\s*[:：]?\s*(\d{4,8})""",
            RegexOption.IGNORE_CASE
        )

        // 수취인 라벨
        private val RECIPIENT_PATTERN = Regex(
            """(?:받는분|수취인|예금주|입금자|송금받는분|보내는분)\s*[:：]\s*([^\n]+)"""
        )

        // 주소 라벨
        private val ADDRESS_PATTERN = Regex(
            """(?:주소|배송지|배송주소)\s*[:：]\s*([^\n]+)"""
        )

        // 이메일 패턴
        private val EMAIL_PATTERN = Regex(
            """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
        )

        // 마스킹된 이름 (이미 마스킹된 것은 그대로 유지)
        private val MASKED_NAME_PATTERN = Regex(
            """[가-힣]\*[가-힣]{1,2}"""
        )

        // 마스킹 문자열
        private const val PHONE_MASK = "***-****-****"
        private const val CODE_MASK = "****"
        private const val RECIPIENT_MASK = "***"
        private const val ADDRESS_MASK = "***"
        private const val EMAIL_MASK = "***@***.***"
    }

    /**
     * 텍스트에서 개인정보 마스킹
     *
     * @param text 원본 텍스트
     * @return 마스킹된 텍스트
     */
    fun mask(text: String): String {
        var result = text

        // 1. 전화번호 마스킹
        result = PHONE_PATTERN.replace(result, PHONE_MASK)

        // 2. 인증번호/승인번호 마스킹
        result = AUTH_CODE_PATTERN.replace(result) { match ->
            val label = match.value.substringBefore(match.groupValues[1])
            "$label$CODE_MASK"
        }

        // 3. 수취인 정보 마스킹
        result = RECIPIENT_PATTERN.replace(result) { match ->
            val label = match.value.substringBefore(match.groupValues[1])
            "$label$RECIPIENT_MASK"
        }

        // 4. 주소 마스킹
        result = ADDRESS_PATTERN.replace(result) { match ->
            val label = match.value.substringBefore(match.groupValues[1])
            "$label$ADDRESS_MASK"
        }

        // 5. 이메일 마스킹
        result = EMAIL_PATTERN.replace(result, EMAIL_MASK)

        // 6. 긴 숫자 마스킹 (카드번호, 계좌번호)
        result = maskLongNumbers(result)

        // 7. 대시 포함 긴 숫자 마스킹
        result = DASHED_NUMBER_PATTERN.replace(result) { match ->
            maskPartialNumber(match.value)
        }

        // 8. 부분 마스킹된 번호 완전 마스킹
        result = PARTIAL_MASKED_PATTERN.replace(result) { match ->
            maskPartialNumber(match.value)
        }

        return result
    }

    /**
     * 긴 숫자 마스킹 (앞 4자리 + **** + 뒤 4자리)
     */
    private fun maskLongNumbers(text: String): String {
        return LONG_NUMBER_PATTERN.replace(text) { match ->
            val number = match.value
            if (number.length <= 8) {
                // 8자리 이하면 중간 마스킹
                val visibleStart = number.take(2)
                val visibleEnd = number.takeLast(2)
                val maskLength = number.length - 4
                "$visibleStart${"*".repeat(maskLength)}$visibleEnd"
            } else {
                // 9자리 이상이면 앞 4자리 + **** + 뒤 4자리
                val visibleStart = number.take(4)
                val visibleEnd = number.takeLast(4)
                "$visibleStart****$visibleEnd"
            }
        }
    }

    /**
     * 부분 마스킹 (가운데 부분만)
     */
    private fun maskPartialNumber(number: String): String {
        val parts = number.split("-", "*")
            .filter { it.isNotBlank() }

        if (parts.isEmpty()) return number

        return when {
            parts.size == 1 -> {
                // 단일 파트: 앞뒤 2자리만 보이게
                val part = parts[0]
                if (part.length <= 4) {
                    "*".repeat(part.length)
                } else {
                    "${part.take(2)}${"*".repeat(part.length - 4)}${part.takeLast(2)}"
                }
            }
            parts.size == 2 -> {
                // 2개 파트: 첫 번째는 보이고, 두 번째는 마스킹
                "${parts[0]}-${"*".repeat(parts[1].length)}"
            }
            else -> {
                // 3개 이상 파트: 첫 번째와 마지막만 보이게
                val first = parts.first()
                val last = parts.last()
                val middle = parts.drop(1).dropLast(1)
                    .joinToString("-") { "*".repeat(it.length) }
                "$first-$middle-$last"
            }
        }
    }

    /**
     * 민감 정보 포함 여부 확인
     *
     * @param text 확인할 텍스트
     * @return 민감 정보 포함 여부
     */
    fun containsSensitiveInfo(text: String): Boolean {
        return PHONE_PATTERN.containsMatchIn(text) ||
                LONG_NUMBER_PATTERN.containsMatchIn(text) ||
                DASHED_NUMBER_PATTERN.containsMatchIn(text) ||
                AUTH_CODE_PATTERN.containsMatchIn(text) ||
                RECIPIENT_PATTERN.containsMatchIn(text) ||
                ADDRESS_PATTERN.containsMatchIn(text) ||
                EMAIL_PATTERN.containsMatchIn(text)
    }

    /**
     * 저장 가능 여부 확인 (옵트인 사용자용)
     *
     * @param text 원본 텍스트
     * @param confidence 파싱 신뢰도
     * @param wasUserCorrected 사용자가 수정했는지 여부
     * @return 저장 가능 여부
     */
    fun canStoreAnonymized(
        text: String,
        confidence: Double,
        wasUserCorrected: Boolean
    ): Boolean {
        // 민감정보가 있으면 저장하지 않음 (마스킹 후에도 위험할 수 있음)
        if (containsSensitiveInfo(text) && !wasUserCorrected) {
            return false
        }
        // confidence < 0.6 또는 사용자 수정 시만 저장 (파싱 개선 목적)
        return confidence < 0.6 || wasUserCorrected
    }

    /**
     * 익명화된 원문 생성 (저장용)
     *
     * @param text 원본 텍스트
     * @return 완전 익명화된 텍스트
     */
    fun anonymize(text: String): String {
        var result = mask(text)

        // 추가 익명화: 금액 제거
        result = result.replace(Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원"""), "[금액]원")

        // 추가 익명화: 날짜/시간 제거
        result = result.replace(
            Regex("""(?<!\d)(\d{1,2})[./\-](\d{1,2})\s+(\d{1,2}):(\d{2})(?!\d)"""),
            "[날짜] [시간]"
        )

        // 추가 익명화: 마스킹된 이름도 완전 익명화
        result = MASKED_NAME_PATTERN.replace(result, "[이름]")

        return result
    }

    /**
     * 텍스트 정규화 (비교용)
     *
     * 개인정보를 제거하고 패턴만 추출
     */
    fun normalizeForSignature(text: String): String {
        var result = text

        // 모든 숫자 제거
        result = result.replace(Regex("""\d+"""), " ")

        // 특수문자 정리
        result = result.replace(Regex("""[*\-/:]"""), " ")

        // 공백 정리
        result = result.replace(Regex("""\s+"""), " ")

        return result.trim().lowercase()
    }
}
