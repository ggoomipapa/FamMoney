package com.ezcorp.fammoney.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용처(가맹점명) 정규화 서비스
 *
 * 정책 4 준수: 사용처 파싱 후 정규화 단계에서 실행
 *
 * 기능:
 * - 법인 표기 제거: (주), 주식회사, (유), 유한회사
 * - 부가 정보 제거: TID, MID, TEL, 전화번호, 사업자번호
 * - 지점명 제거: OOO점, #1234
 * - 플랫폼/PG 처리: 실가맹점 우선 추출
 */
@Singleton
class MerchantNormalizer @Inject constructor() {

    companion object {
        // 플랫폼/PG 목록 (실가맹점 우선 추출용)
        private val PLATFORM_KEYWORDS = setOf(
            "네이버페이", "카카오페이", "토스", "쿠팡페이",
            "이니시스", "KCP", "KG이니시스", "토스페이먼츠",
            "페이코", "삼성페이", "애플페이"
        )

        // 법인 표기 패턴
        private val CORPORATE_PATTERN = Regex("""(주식회사|\(주\)|유한회사|\(유\)|\(재\)|\(사\))""")

        // TID/MID/TEL 패턴
        private val TID_MID_TEL_PATTERN = Regex("""(TID|MID|TEL)\s*[:：]?\s*\S+""", RegexOption.IGNORE_CASE)

        // 전화번호 패턴
        private val PHONE_PATTERN = Regex("""\d{2,4}-\d{3,4}-\d{4}""")

        // 긴 숫자 패턴 (6자리 이상)
        private val LONG_NUMBER_PATTERN = Regex("""\d{6,}""")

        // 지점명 패턴: OOO점 (단, 단독 "점"은 제외)
        private val BRANCH_PATTERN = Regex("""[가-힣a-zA-Z0-9]+점(?=\s|$)""")

        // 번호 태그 패턴: #1234
        private val NUMBER_TAG_PATTERN = Regex("""#\d+""")

        // 괄호 내용 패턴 (너무 짧거나 숫자만 있는 경우)
        private val PAREN_CONTENT_PATTERN = Regex("""\([^)]{0,3}\)|\(\d+\)""")

        // 사업자번호 패턴
        private val BIZ_NUMBER_PATTERN = Regex("""\d{3}-\d{2}-\d{5}""")

        // 플랫폼 분리 패턴 (슬래시, 하이픈)
        private val PLATFORM_SEPARATOR = Regex("""[/\-]""")
    }

    /**
     * 가맹점명 정규화
     *
     * @param raw 원본 가맹점명
     * @return 정규화된 가맹점명
     */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        var result = raw.trim()

        // 1. 플랫폼/PG 분리 처리 (실가맹점 우선)
        result = extractRealMerchantFromPlatform(result)

        // 2. 법인 표기 제거
        result = result.replace(CORPORATE_PATTERN, " ")

        // 3. TID/MID/TEL 제거
        result = result.replace(TID_MID_TEL_PATTERN, " ")

        // 4. 전화번호 제거
        result = result.replace(PHONE_PATTERN, " ")

        // 5. 사업자번호 제거
        result = result.replace(BIZ_NUMBER_PATTERN, " ")

        // 6. 긴 숫자 제거
        result = result.replace(LONG_NUMBER_PATTERN, " ")

        // 7. 짧은 괄호 내용 제거
        result = result.replace(PAREN_CONTENT_PATTERN, " ")

        // 8. 번호 태그 제거
        result = result.replace(NUMBER_TAG_PATTERN, " ")

        // 9. 지점명 제거 (선택적 - 설정에 따라)
        // result = result.replace(BRANCH_PATTERN, " ")

        // 10. 다중 공백 정리
        result = result.replace(Regex("""\s+"""), " ").trim()

        // 11. 앞뒤 특수문자 정리
        result = result.trim('[', ']', '(', ')', ' ', '-', '/')

        return result.ifBlank { raw.trim() }
    }

    /**
     * 플랫폼/PG 결제에서 실가맹점 추출
     *
     * 예: "네이버페이/커피빈강남점" → "커피빈강남점"
     * 예: "카카오페이-스타벅스" → "스타벅스"
     */
    private fun extractRealMerchantFromPlatform(text: String): String {
        // 슬래시나 하이픈으로 분리
        val parts = text.split(PLATFORM_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (parts.size < 2) return text

        // 플랫폼이 아닌 부분 찾기
        val nonPlatformParts = parts.filter { part ->
            !PLATFORM_KEYWORDS.any { platform ->
                part.contains(platform, ignoreCase = true)
            }
        }

        // 실가맹점이 있으면 그것 반환, 없으면 원본 반환
        return if (nonPlatformParts.isNotEmpty()) {
            // 가장 길고 의미있는 것 선택
            nonPlatformParts.maxByOrNull { specificity(it) } ?: text
        } else {
            // 모두 플랫폼이면 첫 번째 것 반환
            parts.first()
        }
    }

    /**
     * 가맹점명의 구체성 점수 계산
     * 점수가 높을수록 더 구체적인 가맹점명
     */
    private fun specificity(merchant: String): Int {
        var score = merchant.length

        // 한글 포함 시 가점
        if (merchant.any { it in '\uAC00'..'\uD7A3' }) score += 5

        // 플랫폼 키워드만 있으면 감점
        if (PLATFORM_KEYWORDS.any { merchant.equals(it, ignoreCase = true) }) score -= 10

        // 숫자 비율이 높으면 감점
        val digitRatio = merchant.count { it.isDigit() }.toDouble() / merchant.length
        if (digitRatio > 0.3) score -= 5

        return score
    }

    /**
     * 지점명까지 제거한 정규화 (더 강한 정규화)
     */
    fun normalizeWithBranchRemoval(raw: String): String {
        var result = normalize(raw)
        result = result.replace(BRANCH_PATTERN, " ")
        result = result.replace(Regex("""\s+"""), " ").trim()
        return result.ifBlank { raw.trim() }
    }

    /**
     * 검색/매칭용 정규화 (가장 강한 정규화)
     * - 모든 특수문자 제거
     * - 소문자 변환
     * - 공백 제거
     */
    fun normalizeForMatching(raw: String): String {
        return normalize(raw)
            .lowercase()
            .replace(Regex("[^가-힣a-z0-9]"), "")
            .take(30)
    }
}
