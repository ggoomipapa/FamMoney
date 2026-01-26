package com.ezcorp.fammoney.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용처 후보 추출 및 점수화 서비스 (N-best 방식)
 *
 * 정책 1 준수: 보수적 접근 - 오탐 방지를 위해 감점 규칙을 강하게 적용
 * 정책 2 준수: 후보가 없으면 null 반환 허용
 *
 * 파이프라인:
 * 1. 여러 출처에서 후보 추출 (라벨, 금액 주변, 상단 라인)
 * 2. 각 후보에 점수 부여 (가점/감점 규칙)
 * 3. 최고점 후보 선택 + 신뢰도 산출
 */
@Singleton
class MerchantCandidateExtractor @Inject constructor(
    private val normalizer: MerchantNormalizer
) {

    companion object {
        // 금액 패턴
        private val KRW_AMOUNT = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")

        // 라벨 기반 가맹점 패턴
        private val MERCHANT_LABEL = Regex("""(가맹점|사용처|매장|상호)\s*[:：]\s*([^\n]+)""")

        // 감점 대상 토큰 (BAD_TOKENS)
        private val BAD_TOKENS = listOf(
            "승인", "취소", "완료", "정기", "자동이체", "잔액", "한도",
            "카드", "계좌", "번호", "TID", "MID", "TEL", "고객센터",
            "사업자", "입금", "출금", "송금", "이체", "누적", "일시불",
            "할부", "결제", "체크", "신용", "Web발신", "원"
        )

        // 가점 대상 힌트 (상호 단서)
        private val MERCHANT_HINTS = listOf(
            "점", "지점", "스토어", "마켓", "카페", "마트", "약국",
            "병원", "주유", "편의점", "커피", "치킨", "피자"
        )

        // 긴 숫자 패턴 (6자리 이상)
        private val LONG_NUMBER = Regex("""\d{6,}""")

        // 날짜/시간 패턴
        private val DATE_TIME_PATTERN = Regex("""\d{1,2}[/\-\.]\d{1,2}|\d{1,2}:\d{2}""")

        // 마스킹된 이름 패턴
        private val MASKED_NAME_PATTERNS = listOf(
            Regex("""^[가-힣]\*[가-힣]님?$"""),
            Regex("""^[가-힣]\*[가-힣]{2}님?$"""),
            Regex("""^[가-힣]{2}\*[가-힣]님?$"""),
            Regex("""^[가-힣]\*+님?$""")
        )

        // 금융기관 키워드
        private val INSTITUTION_KEYWORDS = setOf(
            "KB", "국민", "신한", "우리", "하나", "농협", "기업", "SC",
            "카카오뱅크", "토스뱅크", "케이뱅크", "삼성카드", "현대카드",
            "롯데카드", "BC카드", "NH", "IBK", "카카오페이", "네이버페이",
            "토스", "삼성페이", "페이코", "은행", "Pay", "페이"
        )
    }

    /**
     * 후보 추출 결과
     */
    data class ExtractionResult(
        val bestCandidate: Candidate?,
        val candidates: List<Candidate>,
        val confidence: Double
    )

    /**
     * 후보 모델
     */
    data class Candidate(
        val text: String,
        val normalizedText: String,
        val origin: CandidateOrigin,
        val score: Double,
        val reasons: List<String> = emptyList()
    )

    /**
     * 후보 출처
     */
    enum class CandidateOrigin(val weight: Double) {
        LABEL_AFTER(1.5),      // "가맹점:" 뒤 (가장 강함)
        NEAR_AMOUNT(0.8),      // 금액 주변 토큰
        FIRST_LINES(0.4),      // 상단 라인
        HEURISTIC_BLOCK(0.2)   // 기타 휴리스틱
    }

    /**
     * 사용처 후보 추출 및 점수화
     *
     * @param cleanText 전처리된 알림 텍스트
     * @return 추출 결과 (bestCandidate, 전체 후보, 신뢰도)
     */
    fun extract(cleanText: String): ExtractionResult {
        // 1. 다양한 출처에서 후보 수집
        val rawCandidates = mutableListOf<Candidate>()

        rawCandidates.addAll(extractByLabel(cleanText))
        rawCandidates.addAll(extractNearAmount(cleanText))
        rawCandidates.addAll(extractFromFirstLines(cleanText))

        // 2. 중복 제거 (정규화된 텍스트 기준)
        val uniqueCandidates = rawCandidates
            .distinctBy { it.normalizedText }
            .filter { it.normalizedText.isNotBlank() }

        // 3. 점수 계산
        val scoredCandidates = uniqueCandidates.map { candidate ->
            val (score, reasons) = calculateScore(candidate)
            candidate.copy(score = score, reasons = reasons)
        }.sortedByDescending { it.score }

        // 4. 최고점 후보 선택
        val best = scoredCandidates.firstOrNull()
        val second = scoredCandidates.getOrNull(1)

        // 5. 신뢰도 산출
        val hasAmount = KRW_AMOUNT.containsMatchIn(cleanText)
        val confidence = calculateConfidence(best?.score, second?.score, hasAmount)

        return ExtractionResult(
            bestCandidate = best,
            candidates = scoredCandidates.take(5),
            confidence = confidence
        )
    }

    /**
     * 라벨 기반 후보 추출
     * 예: "가맹점: 스타벅스" → "스타벅스"
     */
    private fun extractByLabel(text: String): List<Candidate> {
        return MERCHANT_LABEL.findAll(text).map { match ->
            val rawText = match.groupValues[2].trim()
            Candidate(
                text = rawText,
                normalizedText = normalizer.normalize(rawText),
                origin = CandidateOrigin.LABEL_AFTER,
                score = 0.0
            )
        }.toList()
    }

    /**
     * 금액 주변 후보 추출
     */
    private fun extractNearAmount(text: String): List<Candidate> {
        val lines = text.split("\n")
        val candidates = mutableListOf<Candidate>()

        for ((index, line) in lines.withIndex()) {
            if (KRW_AMOUNT.containsMatchIn(line)) {
                // 현재 라인에서 금액/키워드 제거 후 잔여 텍스트
                val residual = extractResidualFromLine(line)
                if (residual.isNotBlank() && isValidCandidate(residual)) {
                    candidates.add(
                        Candidate(
                            text = residual,
                            normalizedText = normalizer.normalize(residual),
                            origin = CandidateOrigin.NEAR_AMOUNT,
                            score = 0.0
                        )
                    )
                }

                // 인접 라인 (위/아래)
                listOf(index - 1, index + 1)
                    .filter { it in lines.indices }
                    .forEach { idx ->
                        val adjacentLine = lines[idx].trim()
                        if (adjacentLine.length in 2..40 && isValidCandidate(adjacentLine)) {
                            candidates.add(
                                Candidate(
                                    text = adjacentLine,
                                    normalizedText = normalizer.normalize(adjacentLine),
                                    origin = CandidateOrigin.NEAR_AMOUNT,
                                    score = 0.0
                                )
                            )
                        }
                    }
            }
        }

        return candidates
    }

    /**
     * 라인에서 금액/키워드 제거 후 잔여 텍스트 추출
     */
    private fun extractResidualFromLine(line: String): String {
        return line
            .replace(KRW_AMOUNT, " ")
            .replace(DATE_TIME_PATTERN, " ")
            .replace(Regex("""승인|결제|사용|금액|원|일시불?|할부|잔액|카드|계좌|번호|누적"""), " ")
            .replace(Regex("""[^0-9A-Za-z가-힣 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * 상단 라인 후보 추출
     */
    private fun extractFromFirstLines(text: String): List<Candidate> {
        val lines = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return lines.take(3)
            .filter { it.length in 2..40 && isValidCandidate(it) }
            .map { line ->
                Candidate(
                    text = line,
                    normalizedText = normalizer.normalize(line),
                    origin = CandidateOrigin.FIRST_LINES,
                    score = 0.0
                )
            }
    }

    /**
     * 유효한 후보인지 검증
     */
    private fun isValidCandidate(text: String): Boolean {
        // 길이 체크
        if (text.length < 2 || text.length > 40) return false

        // 마스킹된 이름 제외
        if (MASKED_NAME_PATTERNS.any { it.matches(text) }) return false

        // 숫자만으로 구성 제외
        if (text.all { it.isDigit() || it == '*' || it == '-' || it == ',' || it == '.' }) return false

        // 날짜/시간만 제외
        if (DATE_TIME_PATTERN.matches(text)) return false

        // 금융기관명만 제외
        if (INSTITUTION_KEYWORDS.any { text.equals(it, ignoreCase = true) }) return false

        // 한글 또는 영문 포함 필수
        return text.any { it in '\uAC00'..'\uD7A3' || it.isLetter() }
    }

    /**
     * 후보 점수 계산 (가점/감점 규칙)
     *
     * 정책 1 준수: 감점 규칙을 강하게 적용하여 오탐 방지
     */
    private fun calculateScore(candidate: Candidate): Pair<Double, List<String>> {
        var score = 0.0
        val reasons = mutableListOf<String>()
        val text = candidate.text.trim()

        // === 감점 규칙 ===

        // 길이 범위 체크
        if (text.length !in 2..40) {
            score -= 2.0
            reasons.add("length_out_of_range")
        }

        // 금액 패턴 포함
        if (KRW_AMOUNT.containsMatchIn(text)) {
            score -= 3.0
            reasons.add("contains_amount")
        }

        // 긴 숫자 포함
        if (LONG_NUMBER.containsMatchIn(text)) {
            score -= 2.5
            reasons.add("contains_long_number")
        }

        // BAD_TOKENS 포함 개수
        val badHitCount = BAD_TOKENS.count { text.contains(it, ignoreCase = true) }
        if (badHitCount >= 2) {
            score -= 2.0
            reasons.add("many_bad_tokens:$badHitCount")
        } else if (badHitCount == 1) {
            score -= 0.5
            reasons.add("one_bad_token")
        }

        // 숫자 비율 체크
        val digitCount = text.count { it.isDigit() }
        val ratio = if (text.isNotEmpty()) digitCount.toDouble() / text.length else 1.0
        if (ratio > 0.35) {
            score -= 1.5
            reasons.add("too_many_digits:${(ratio * 100).toInt()}%")
        }

        // 금융기관 키워드 포함
        if (INSTITUTION_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
            score -= 1.0
            reasons.add("contains_institution")
        }

        // === 가점 규칙 ===

        // 한글/영문 글자 포함
        val letterCount = text.count { it.isLetter() || it in '\uAC00'..'\uD7A3' }
        if (letterCount >= 2) {
            score += 1.0
            reasons.add("has_letters")
        }

        // 상호 힌트 포함
        if (MERCHANT_HINTS.any { text.contains(it) }) {
            score += 0.8
            reasons.add("merchant_hint")
        }

        // 출처 가중치
        score += candidate.origin.weight
        reasons.add("origin:${candidate.origin.name}")

        return score to reasons
    }

    /**
     * 신뢰도(Confidence) 산출
     *
     * 정책 1 기준:
     * - ≥ 0.75 : 자동 확정
     * - 0.50 ~ 0.75 : 검토 필요
     * - < 0.50 : 폴백 UX
     */
    private fun calculateConfidence(
        bestScore: Double?,
        secondScore: Double?,
        hasAmount: Boolean
    ): Double {
        if (bestScore == null) return 0.0

        val s2 = secondScore ?: (bestScore - 2.0)
        val margin = bestScore - s2

        var confidence = 0.4

        // 최고점 기반 가산 (최대 0.4)
        confidence += (bestScore / 6.0).coerceIn(0.0, 0.4)

        // 마진(1등과 2등 차이) 기반 가산 (최대 0.3)
        confidence += (margin / 4.0).coerceIn(0.0, 0.3)

        // 금액 추출 성공 시 가산
        if (hasAmount) confidence += 0.1

        return confidence.coerceIn(0.0, 1.0)
    }
}
