package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.data.model.LearnedMerchantRule
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.repository.LearnedMerchantRuleRepository
import com.ezcorp.fammoney.util.AppLogger
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 한국 금융기관별 알림 파싱 시스템
 *
 * 지원 금융기관:
 * - 카드사: 삼성, 신한, 현대, KB국민, 하나, 롯데, BC, NH농협, 우리
 * - 은행: KB국민, 신한, 우리, 하나, 농협, 기업, SC제일, 케이뱅크, 카카오뱅크, 토스뱅크
 * - 간편결제: 카카오페이, 네이버페이, 토스, 삼성페이, 페이코
 */

/**
 * 파싱 결과 데이터 클래스
 *
 * 정책 1 준수: confidence 필드로 자동 확정/검토 필요/폴백 UX 분기
 * 정책 2 준수: merchantName이 null일 수 있음 (미분류 허용)
 */
data class ParsedTransaction(
    val amount: Long,
    val type: TransactionType,
    val bankConfig: BankConfig,
    val description: String,
    val merchantName: String,
    val senderName: String = "",
    val accountNumber: String = "",
    val originalText: String,
    // N-best 파싱 관련 필드
    val confidence: Double = 0.0,  // 신뢰도 (0.0 ~ 1.0)
    val merchantCandidates: List<String> = emptyList()  // 후보 목록 (Top 5)
)

@Singleton
class NotificationParser @Inject constructor(
    private val exchangeRateService: ExchangeRateService,
    private val merchantCandidateExtractor: MerchantCandidateExtractor,
    private val merchantNormalizer: MerchantNormalizer,
    private val learnedMerchantRuleRepository: LearnedMerchantRuleRepository
) {

    companion object {
        private const val TAG = "NotificationParser"

        // 금액 추출 패턴들 (우선순위 순)
        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("([\\d,]+)\\s*원\\s*(?:승인|결제|출금|입금|이체)"),
            Pattern.compile("(?:승인|결제|출금|입금|이체)\\s*([\\d,]+)\\s*원"),
            Pattern.compile("(?:일시불|\\d+개월)[/\\s]*([\\d,]+)\\s*원"),
            Pattern.compile("([\\d,]+)\\s*원\\s*(?:일시불|\\d+개월)"),
            Pattern.compile("([\\d,]+)\\s*원")
        )

        // 잔액 추출 패턴
        private val BALANCE_PATTERNS = listOf(
            Pattern.compile("잔액\\s*[:：]?\\s*([\\d,]+)\\s*원?"),
            Pattern.compile("잔액([\\d,]+)원?")
        )

        // 날짜/시간 추출 패턴
        private val DATE_PATTERN = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})")

        // 카드번호/계좌번호 마스킹 패턴
        private val ACCOUNT_PATTERNS = listOf(
            Pattern.compile("(\\d{3,6}[*]+\\d{0,6})"),
            Pattern.compile("([*]+\\d{3,6})"),
            Pattern.compile("(\\d+\\*+\\d*)")
        )

        // 거래 유형 키워드
        private val INCOME_KEYWORDS = listOf(
            "입금", "받으셨", "들어옴", "이체받음", "송금받음", "받았어요", "출금취소"
        )

        private val EXPENSE_KEYWORDS = listOf(
            "출금", "결제", "승인", "이체", "송금", "사용", "지출",
            "체크카드출금", "신용카드출금", "보냈어요"
        )

        // 제외 키워드 (가맹점명에서 제외)
        private val EXCLUDED_KEYWORDS = setOf(
            "Web발신", "[Web발신]", "승인", "결제", "출금", "입금", "이체",
            "일시불", "할부", "잔액", "누적", "님", "체크카드", "신용카드",
            "원", "개월", "취소", "체크카드출금", "신용카드출금", "카드출금",
            "인터넷출금", "자동이체", "계좌이체", "출금취소"
        )

        // 금융기관 제외 키워드 (가맹점명에서 제외)
        private val INSTITUTION_KEYWORDS = setOf(
            "KB", "국민", "신한", "우리", "하나", "농협", "기업", "SC",
            "카카오뱅크", "토스뱅크", "케이뱅크", "삼성카드", "현대카드",
            "롯데카드", "BC카드", "NH", "IBK", "카카오페이", "네이버페이",
            "토스", "삼성페이", "페이코", "은행", "카드", "Pay", "페이"
        )
    }

    suspend fun parse(
        packageName: String,
        notificationText: String,
        selectedBanks: List<BankConfig>
    ): ParsedTransaction? {
        AppLogger.d(TAG, "========== 파싱 시작 ==========")
        AppLogger.d(TAG, "패키지: $packageName")
        AppLogger.d(TAG, "알림 텍스트: $notificationText")
        AppLogger.d(TAG, "선택된 은행 수: ${selectedBanks.size}")

        val matchingBank = selectedBanks.find { bank ->
            bank.packageNames.contains(packageName)
        }
        if (matchingBank == null) {
            AppLogger.d(TAG, "매칭되는 은행 없음 - 패키지 '$packageName'이 선택된 은행에 없음")
            return null
        }

        AppLogger.d(TAG, "매칭된 은행: ${matchingBank.displayName} (bankId=${matchingBank.bankId})")
        return parseWithBankConfig(notificationText, matchingBank)
    }

    suspend fun parseManualInput(
        text: String,
        selectedBanks: List<BankConfig>
    ): ParsedTransaction? {
        AppLogger.d(TAG, "수동 입력 파싱 시작: text=${text.take(50)}...")
        for (bank in selectedBanks) {
            val result = parseWithBankConfig(text, bank)
            if (result != null) {
                AppLogger.i(TAG, "수동 입력 파싱 성공: 은행=${bank.displayName}, 금액=${result.amount}")
                return result
            }
        }
        AppLogger.d(TAG, "수동 입력 파싱 실패 - 모든 은행에서 매칭 안됨")
        return null
    }

    private suspend fun parseWithBankConfig(text: String, bankConfig: BankConfig): ParsedTransaction? {
        AppLogger.d(TAG, "parseWithBankConfig 시작: bank=${bankConfig.displayName}")

        // 1. 금액 추출
        val amountResult = extractAmount(text)
        if (amountResult == null) {
            AppLogger.d(TAG, "금액 추출 실패 - 텍스트에서 금액 패턴 없음")
            return null
        }
        val (amount, isForeign) = amountResult
        AppLogger.d(TAG, "금액 추출 성공: amount=$amount, 해외거래=$isForeign")

        val finalAmount = if (isForeign) {
            // 실시간 환율 적용
            AppLogger.d(TAG, "해외거래 환율 변환 시작: USD $amount")
            val exchangeRate = exchangeRateService.getExchangeRate(baseCurrency = "USD", targetCurrency = "KRW") ?: 1300.0 // Default to 1300 if API fails
            val converted = (amount.toDouble() * exchangeRate).toLong()
            AppLogger.d(TAG, "환율 변환 완료: USD $amount × $exchangeRate = ${converted}원")
            converted
        } else {
            amount.toLong()
        }

        if (finalAmount <= 0) {
            AppLogger.d(TAG, "최종 금액이 0 이하: $finalAmount - 파싱 중단")
            return null
        }

        // 2. 거래 유형 판별
        val type = determineTransactionType(text, bankConfig)
        AppLogger.d(TAG, "거래 유형 판별: ${type.name}")

        // 3. 가맹점/송금인 추출 (N-best 시스템 적용)
        val merchantName: String
        val senderName: String
        var confidence = 0.0
        var merchantCandidates = emptyList<String>()

        if (type == TransactionType.INCOME) {
            AppLogger.d(TAG, "입금 거래 - 입금사유/송금인 추출 시작")
            // 입금인 경우: 특별 입금 사유 우선, 없으면 송금인 추출
            val incomeReason = extractIncomeReason(text)
            senderName = extractSenderName(text)
            AppLogger.d(TAG, "입금사유: '$incomeReason', 송금인: '$senderName'")
            // 특별 입금 사유(체크할인, 캐시백 등)가 있으면 그것을 사용
            merchantName = if (incomeReason != "입금") {
                confidence = 0.9  // 입금 사유 키워드 매칭은 높은 신뢰도
                AppLogger.d(TAG, "입금 사유 키워드 매칭: '$incomeReason' (confidence=0.9)")
                incomeReason
            } else if (senderName.isNotBlank()) {
                confidence = 0.8  // 송금인 추출도 높은 신뢰도
                AppLogger.d(TAG, "송금인 추출 성공: '$senderName' (confidence=0.8)")
                senderName
            } else {
                confidence = 0.3  // 기본 "입금"은 낮은 신뢰도
                AppLogger.d(TAG, "입금 사유/송금인 미확인 - 기본값 '입금' 사용 (confidence=0.3)")
                "입금"
            }
        } else {
            AppLogger.d(TAG, "출금 거래 - N-best 가맹점 추출 시작")
            // 출금인 경우: N-best 후보 추출 + 점수화
            senderName = ""

            // 전처리
            val cleanText = preprocess(text)
            AppLogger.d(TAG, "전처리된 텍스트: ${cleanText.take(80)}...")

            // 정책 3: 학습된 규칙 우선 조회
            val signature = LearnedMerchantRule.generateSignature(cleanText, null)
            val learnedRule = learnedMerchantRuleRepository.findBySignature(signature)
            AppLogger.d(TAG, "학습 규칙 조회: signature=${signature.take(20)}..., 결과=${if (learnedRule != null) "'${learnedRule.merchant}' (hitCount=${learnedRule.hitCount})" else "없음"}")

            if (learnedRule != null && learnedRule.merchant.isNotBlank()) {
                // 학습된 규칙 사용
                merchantName = learnedRule.merchant
                confidence = if (learnedRule.isUserConfirmed) {
                    0.95 + LearnedMerchantRule.confidenceBonus(learnedRule.hitCount)
                } else {
                    0.85 + LearnedMerchantRule.confidenceBonus(learnedRule.hitCount)
                }
                merchantCandidates = listOf(learnedRule.merchant)
                AppLogger.i(TAG, "학습 규칙 적용: merchant='$merchantName', userConfirmed=${learnedRule.isUserConfirmed}, confidence=$confidence")
            } else {
                // N-best 후보 추출
                val extractionResult = merchantCandidateExtractor.extract(cleanText)

                confidence = extractionResult.confidence
                merchantCandidates = extractionResult.candidates.map { it.normalizedText }
                AppLogger.d(TAG, "N-best 후보 추출: ${merchantCandidates.size}개, confidence=$confidence")
                merchantCandidates.forEachIndexed { idx, candidate ->
                    AppLogger.d(TAG, "  후보[$idx]: '$candidate'")
                }

                // 정책 2 준수: confidence < 0.50이면 미분류 허용
                merchantName = if (extractionResult.bestCandidate != null && confidence >= 0.30) {
                    AppLogger.d(TAG, "Best candidate 사용: '${extractionResult.bestCandidate.normalizedText}' (confidence=$confidence >= 0.30)")
                    extractionResult.bestCandidate.normalizedText
                } else {
                    // 기존 방식으로 폴백 (호환성 유지)
                    AppLogger.d(TAG, "N-best 불충분 - 레거시 방식 폴백 시도")
                    val fallback = extractMerchantNameLegacy(text, isForeign)
                    if (fallback.isNotBlank()) {
                        confidence = 0.4  // 레거시 방식은 중간 신뢰도
                        AppLogger.d(TAG, "레거시 방식 성공: '$fallback' (confidence=0.4)")
                        fallback
                    } else {
                        AppLogger.d(TAG, "레거시 방식도 실패 - 미분류 처리")
                        ""  // 미분류 (정책 2)
                    }
                }
            }
        }

        // 4. 계좌번호 추출
        val accountNumber = extractAccountNumber(text)
        if (accountNumber.isNotBlank()) {
            AppLogger.d(TAG, "계좌번호 추출: $accountNumber")
        }

        // 5. 설명 추출
        val description = extractDescription(text, type)

        AppLogger.i(TAG, "========== 파싱 완료 ==========")
        AppLogger.i(TAG, "결과: amount=${finalAmount}원, type=${type.name}, bank=${bankConfig.displayName}")
        AppLogger.i(TAG, "  merchant='$merchantName', sender='$senderName', confidence=$confidence")
        AppLogger.i(TAG, "  후보: ${merchantCandidates.joinToString(", ")}")

        return ParsedTransaction(
            amount = finalAmount,
            type = type,
            bankConfig = bankConfig,
            description = description,
            merchantName = merchantName,
            senderName = senderName,
            accountNumber = accountNumber,
            originalText = text,
            confidence = confidence,
            merchantCandidates = merchantCandidates
        )
    }

    /**
     * 금액 추출
     */
    private fun extractAmount(text: String): Pair<Number, Boolean>? {
        AppLogger.d(TAG, "금액 추출 시작")

        // 해외 패턴 (USD)
        val foreignPatterns = listOf(
            Pattern.compile("USD\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\$\\s*([\\d,]+\\.?\\d*)")
        )

        for (pattern in foreignPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1).replace(",", "")
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount > 0) {
                    AppLogger.d(TAG, "해외 금액 패턴 매칭: $amountStr → $amount (패턴: ${pattern.pattern()})")
                    return Pair(amount, true)
                }
            }
        }

        // 국내 패턴 (원)
        for ((index, pattern) in AMOUNT_PATTERNS.withIndex()) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1).replace(",", "")
                val amount = amountStr.toLongOrNull()
                if (amount != null && amount > 0) {
                    AppLogger.d(TAG, "국내 금액 패턴 매칭: ${amountStr}원 → ${amount}원 (패턴 인덱스: $index)")
                    return Pair(amount, false)
                }
            }
        }
        AppLogger.d(TAG, "금액 추출 실패 - 매칭되는 패턴 없음")
        return null
    }

    /**
     * 거래 유형 판별
     */
    private fun determineTransactionType(text: String, bankConfig: BankConfig): TransactionType {
        AppLogger.d(TAG, "거래 유형 판별 시작")

        // 취소/환불 키워드 우선 확인
        val cancelKeywords = listOf("취소", "CANCELED", "CANCELLED", "환불", "REFUND")
        val foundCancel = cancelKeywords.firstOrNull { text.contains(it, ignoreCase = true) }
        if (foundCancel != null) {
            AppLogger.d(TAG, "취소/환불 키워드 감지: '$foundCancel' → INCOME")
            return TransactionType.INCOME
        }

        // "출금취소"는 입금으로 처리 (우선 체크)
        if (text.contains("출금취소")) {
            AppLogger.d(TAG, "'출금취소' 감지 → INCOME")
            return TransactionType.INCOME
        }

        // 수입 키워드 확인 (은행 설정 + 기본 키워드)
        val allIncomeKeywords = bankConfig.incomeKeywords + INCOME_KEYWORDS
        val allExpenseKeywords = bankConfig.expenseKeywords + EXPENSE_KEYWORDS

        // 각 키워드의 첫 등장 위치 확인
        var incomeIndex = Int.MAX_VALUE
        var expenseIndex = Int.MAX_VALUE
        var matchedIncomeKeyword = ""
        var matchedExpenseKeyword = ""

        for (keyword in allIncomeKeywords) {
            val idx = text.indexOf(keyword)
            if (idx >= 0 && idx < incomeIndex) {
                incomeIndex = idx
                matchedIncomeKeyword = keyword
            }
        }

        for (keyword in allExpenseKeywords) {
            // "출금취소"에서 "출금"이 매칭되지 않도록
            if (keyword == "출금" && text.contains("출금취소")) continue

            val idx = text.indexOf(keyword)
            if (idx >= 0 && idx < expenseIndex) {
                expenseIndex = idx
                matchedExpenseKeyword = keyword
            }
        }

        val result = when {
            incomeIndex < expenseIndex -> {
                AppLogger.d(TAG, "유형 판별: INCOME (키워드='$matchedIncomeKeyword' @$incomeIndex vs '$matchedExpenseKeyword' @$expenseIndex)")
                TransactionType.INCOME
            }
            expenseIndex < incomeIndex -> {
                AppLogger.d(TAG, "유형 판별: EXPENSE (키워드='$matchedExpenseKeyword' @$expenseIndex vs '$matchedIncomeKeyword' @$incomeIndex)")
                TransactionType.EXPENSE
            }
            else -> {
                AppLogger.d(TAG, "유형 판별: EXPENSE (기본값 - 키워드 위치 동일 또는 미발견)")
                TransactionType.EXPENSE
            }
        }
        return result
    }

    /**
     * 텍스트 전처리 (N-best 추출 전)
     */
    private fun preprocess(raw: String): String {
        return raw
            .replace("\u200B", "")  // zero width space
            .replace("\u00A0", " ") // nbsp
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    /**
     * 가맹점명 추출 (레거시 방식 - 폴백용)
     */
    private fun extractMerchantNameLegacy(text: String, isForeign: Boolean): String {
        val lines = text.split("\n", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length >= 2 }

        for (line in lines) {
            // 유효한 가맹점명인지 확인
            if (isValidMerchantName(line, isForeign)) {
                return cleanMerchantName(line)
            }
        }

        // 특수 패턴 처리 (국내 거래용)
        if (!isForeign) {
            // "(주)회사명 체크카드출금" 형식
            val corporatePattern = Pattern.compile("(\\([주유사재]\\)[가-힣a-zA-Z0-9]+)\\s*(?:체크카드출금|신용카드출금|카드출금)")
            val corpMatcher = corporatePattern.matcher(text)
            if (corpMatcher.find()) {
                return corpMatcher.group(1)
            }

            // "[카드사] 승인 금액원 가맹점" 형식
            val cardPattern = Pattern.compile("(?:승인|결제)\\s*[\\d,]+원\\s+([가-힣a-zA-Z0-9]+)")
            val cardMatcher = cardPattern.matcher(text)
            if (cardMatcher.find()) {
                val merchant = cardMatcher.group(1)
                if (isValidMerchantName(merchant, false)) {
                    return merchant
                }
            }
        } else {
            // 해외 거래용 특수 패턴
            // 예: "Amazon.com" 또는 "GOOGLE *SERVICES"
            val foreignMerchantPattern = Pattern.compile("""([a-zA-Z0-9.,*&' -]+)""")
            val matcher = foreignMerchantPattern.matcher(text)
            val candidates = mutableListOf<String>()
            while(matcher.find()) {
                val potentialMerchant = matcher.group(1).trim()
                if (isValidMerchantName(potentialMerchant, true)) {
                    candidates.add(potentialMerchant)
                }
            }
            // 가장 긴 후보를 선택 (가장 구체적인 정보일 가능성이 높음)
            return candidates.maxByOrNull { it.length } ?: ""
        }

        return ""
    }

    /**
     * 송금인 추출 (입금 시)
     */
    /** 송금인으로 잘못 추출되는 금융 용어 */
    private val invalidSenderNames = setOf(
        "카드", "체크", "적금", "예금", "출금", "보험", "대출",
        "이자", "수수료", "해지", "환불", "취소", "잔액", "받기",
        "보내기", "충전", "결제", "승인", "입금", "송금", "이체",
        "할인", "캐시백", "포인트", "리워드", "정산", "배당"
    )

    private fun extractSenderName(text: String): String {
        // "김*성님" 형식의 마스킹된 이름 찾기
        val senderPatterns = listOf(
            Pattern.compile("([가-힣]\\*[가-힣]{1,2})님?\\s*(?:입금|송금|이체)"),
            Pattern.compile("(?:입금|송금|이체)\\s*([가-힣]\\*[가-힣]{1,2})님?"),
            Pattern.compile("([가-힣]{2,4})님?\\s+(?:입금|송금|이체)"),
            Pattern.compile("(?:입금|송금|이체)\\s+([가-힣]{2,4})님?"),
            Pattern.compile("보내신\\s*분\\s*[:：]?\\s*([가-힣*]+)"),
            Pattern.compile("([가-힣]\\*[가-힣]{1,2})님?에게서")
        )

        for (pattern in senderPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val sender = matcher.group(1)?.trim() ?: continue
                val cleaned = sender.replace("님", "")
                // 금융 용어가 아닌 실제 사람 이름만 반환
                if (cleaned.isNotBlank() && cleaned.length in 2..5 && cleaned !in invalidSenderNames) {
                    return cleaned
                }
            }
        }
        return ""
    }

    /**
     * 입금 사유 추출 (출금취소, 환불, 체크할인 등)
     */
    private fun extractIncomeReason(text: String): String {
        val incomeReasons = listOf(
            // 카드 할인/캐시백 (우선순위 높음)
            "KB체크할인" to "KB체크할인",
            "체크할인" to "체크할인",
            "캐시백" to "캐시백",
            "포인트" to "포인트",
            "카드입금" to "카드입금",
            // 기타 입금 사유
            "출금취소" to "출금취소",
            "환불" to "환불",
            "급여" to "급여",
            "월급" to "월급",
            "이자" to "이자",
            "배당" to "배당",
            "용돈" to "용돈"
        )

        for ((keyword, reason) in incomeReasons) {
            if (text.contains(keyword)) {
                return reason
            }
        }
        return "입금"
    }

    /**
     * 계좌번호 추출
     */
    private fun extractAccountNumber(text: String): String {
        for (pattern in ACCOUNT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1) ?: ""
            }
        }
        return ""
    }

    /**
     * 설명 추출
     */
    private fun extractDescription(text: String, type: TransactionType): String {
        // 잔액 정보 추출
        for (pattern in BALANCE_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                // 해외 거래의 경우 '원'이 아닐 수 있으므로 통화 기호를 붙이지 않는다.
                return "잔액 ${matcher.group(1)}"
            }
        }
        return ""
    }

    /**
     * 유효한 가맹점명인지 확인
     */
    private fun isValidMerchantName(text: String, isForeign: Boolean): Boolean {
        // 길이 체크
        if (text.length < 2 || text.length > 30) return false
        
        // 제외 키워드 체크 (공통)
        if (EXCLUDED_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return false
        if (INSTITUTION_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return false

        // 마스킹된 본인 이름 제외 (공통)
        if (isMaskedOwnerName(text)) return false

        // 숫자로만 구성된 경우 제외
        if (text.all { it.isDigit() || it == '*' || it == '-' || it == ',' || it == '.'}) return false
        
        // 날짜/시간 패턴 제외
        if (text.matches(Regex("^\\d{1,2}[/:]\\d{2}$"))) return false
        if (DATE_PATTERN.matcher(text).find()) return false

        if (isForeign) {
            // 해외 가맹점은 영문, 숫자, 일부 특수문자 허용
            return text.matches(Regex(".*[a-zA-Z].*"))
        } else {
            // 국내 가맹점
            // 금액 패턴 제외
            if (text.matches(Regex(".*[\\d,]+\\s*원.*"))) return false
            // 한글 또는 영문숫자가 포함되어야 함
            return text.matches(Regex(".*[가-힣a-zA-Z].*"))
        }
    }

    /**
     * 마스킹된 본인 이름인지 확인
     */
    private fun isMaskedOwnerName(text: String): Boolean {
        val maskedPatterns = listOf(
            Regex("^[가-힣]\\*[가-힣]님?$"),
            Regex("^[가-힣]\\*[가-힣]{2}님?$"),
            Regex("^[가-힣]{2}\\*[가-힣]님?$"),
            Regex("^[가-힣]\\*+님?$")
        )
        return maskedPatterns.any { it.matches(text) }
    }

    /**
     * 제외 키워드인지 확인
     */
    private fun isExcludedKeyword(text: String): Boolean {
        return EXCLUDED_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 금융기관명인지 확인
     */
    private fun isInstitutionName(text: String): Boolean {
        return INSTITUTION_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 가맹점명 정리
     */
    private fun cleanMerchantName(name: String): String {
        var cleaned = name.trim()

        // 앞뒤 특수문자 제거
        cleaned = cleaned.trimStart('[', '(', ' ')
        cleaned = cleaned.trimEnd(']', ')', ' ', '님')

        // "체크카드출금" 등 제거
        cleaned = cleaned.replace(Regex("\\s*(체크카드출금|신용카드출금|카드출금)\\s*"), "")

        return cleaned.trim()
    }
}


// ==================== 카테고리 자동 분류 ====================

object CategoryClassifier {

    private val CATEGORY_KEYWORDS = mapOf(
        "식비" to listOf(
            "스타벅스", "맥도날드", "버거킹", "롯데리아", "KFC", "써브웨이",
            "배달의민족", "요기요", "쿠팡이츠", "배민", "카페", "커피",
            "치킨", "피자", "분식", "한식", "중식", "일식", "양식",
            "CU", "GS25", "세븐일레븐", "이마트24", "편의점", "마트",
            "이마트", "롯데마트", "홈플러스", "코스트코", "식당", "음식점"
        ),
        "교통" to listOf(
            "택시", "카카오T", "타다", "지하철", "버스", "코레일",
            "SRT", "KTX", "고속버스", "시외버스", "주유소", "SK에너지",
            "GS칼텍스", "현대오일뱅크", "S-OIL", "주차", "톨게이트", "하이패스"
        ),
        "쇼핑" to listOf(
            "쿠팡", "네이버쇼핑", "11번가", "G마켓", "옥션", "위메프",
            "티몬", "무신사", "지그재그", "에이블리", "올리브영", "다이소",
            "유니클로", "자라", "H&M", "이케아", "ABC마트"
        ),
        "의료/건강" to listOf(
            "병원", "의원", "약국", "헬스", "피트니스", "PT", "필라테스",
            "요가", "치과", "안과", "피부과", "내과", "외과"
        ),
        "문화/여가" to listOf(
            "CGV", "롯데시네마", "메가박스", "영화관", "넷플릭스", "왓챠",
            "웨이브", "디즈니", "티빙", "쿠팡플레이", "유튜브", "게임",
            "노래방", "PC방", "볼링", "당구", "헬스장"
        ),
        "통신/구독" to listOf(
            "SKT", "KT", "LG유플러스", "알뜰폰", "인터넷", "IPTV",
            "통신비", "휴대폰", "애플", "구글", "마이크로소프트"
        ),
        "교육" to listOf(
            "학원", "과외", "교육", "인강", "클래스101", "패스트캠퍼스",
            "유데미", "노마드코더", "서점", "교보문고", "영풍문고", "YES24"
        ),
        "생활/공과금" to listOf(
            "전기", "가스", "수도", "관리비", "보험", "세금", "국민연금",
            "건강보험", "고용보험", "아파트", "월세"
        )
    )

    fun classify(merchantName: String): String {
        val normalizedName = merchantName.uppercase()

        for ((category, keywords) in CATEGORY_KEYWORDS) {
            for (keyword in keywords) {
                if (normalizedName.contains(keyword.uppercase())) {
                    return category
                }
            }
        }

        return "기타"
    }
}
