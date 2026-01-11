package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.data.model.TransactionType
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

data class ParsedTransaction(
    val amount: Long,
    val type: TransactionType,
    val bankConfig: BankConfig,
    val description: String,
    val merchantName: String,
    val senderName: String = "",
    val accountNumber: String = "",
    val originalText: String
)

@Singleton
class NotificationParser @Inject constructor(
    private val exchangeRateService: ExchangeRateService
) {

    companion object {
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
            "토스", "삼성페이", "페이코", "은행", "카드"
        )
    }

    suspend fun parse(
        packageName: String,
        notificationText: String,
        selectedBanks: List<BankConfig>
    ): ParsedTransaction? {
        val matchingBank = selectedBanks.find { bank ->
            bank.packageNames.contains(packageName)
        } ?: return null

        return parseWithBankConfig(notificationText, matchingBank)
    }

    suspend fun parseManualInput(
        text: String,
        selectedBanks: List<BankConfig>
    ): ParsedTransaction? {
        for (bank in selectedBanks) {
            val result = parseWithBankConfig(text, bank)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private suspend fun parseWithBankConfig(text: String, bankConfig: BankConfig): ParsedTransaction? {
        // 1. 금액 추출
        val (amount, isForeign) = extractAmount(text) ?: return null

        val finalAmount = if (isForeign) {
            // 실시간 환율 적용
            val exchangeRate = exchangeRateService.getExchangeRate(baseCurrency = "USD", targetCurrency = "KRW") ?: 1300.0 // Default to 1300 if API fails
            (amount.toDouble() * exchangeRate).toLong()
        } else {
            amount.toLong()
        }
                
        if (finalAmount <= 0) return null

        // 2. 거래 유형 판별
        val type = determineTransactionType(text, bankConfig)

        // 3. 가맹점/송금인 추출
        val merchantName: String
        val senderName: String

        if (type == TransactionType.INCOME) {
            // 입금인 경우: 송금인 추출
            senderName = extractSenderName(text)
            merchantName = if (senderName.isNotBlank()) senderName else extractIncomeReason(text)
        } else {
            // 출금인 경우: 가맹점 추출
            merchantName = extractMerchantName(text, isForeign)
            senderName = ""
        }

        // 4. 계좌번호 추출
        val accountNumber = extractAccountNumber(text)

        // 5. 설명 추출
        val description = extractDescription(text, type)

        return ParsedTransaction(
            amount = finalAmount,
            type = type,
            bankConfig = bankConfig,
            description = description,
            merchantName = merchantName,
            senderName = senderName,
            accountNumber = accountNumber,
            originalText = text
        )
    }

    /**
     * 금액 추출
     */
    private fun extractAmount(text: String): Pair<Number, Boolean>? {
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
                    return Pair(amount, true) // (금액, 해외거래여부)
                }
            }
        }
        
        // 국내 패턴 (원)
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1).replace(",", "")
                val amount = amountStr.toLongOrNull()
                if (amount != null && amount > 0) {
                    return Pair(amount, false)
                }
            }
        }
        return null
    }

    /**
     * 거래 유형 판별
     */
    private fun determineTransactionType(text: String, bankConfig: BankConfig): TransactionType {
        // 취소/환불 키워드 우선 확인
        val cancelKeywords = listOf("취소", "CANCELED", "CANCELLED", "환불", "REFUND")
        if (cancelKeywords.any { text.contains(it, ignoreCase = true) }) {
            return TransactionType.INCOME
        }

        // "출금취소"는 입금으로 처리 (우선 체크)
        if (text.contains("출금취소")) {
            return TransactionType.INCOME
        }
        
        // 수입 키워드 확인 (은행 설정 + 기본 키워드)
        val allIncomeKeywords = bankConfig.incomeKeywords + INCOME_KEYWORDS
        val allExpenseKeywords = bankConfig.expenseKeywords + EXPENSE_KEYWORDS

        // 각 키워드의 첫 등장 위치 확인
        var incomeIndex = Int.MAX_VALUE
        var expenseIndex = Int.MAX_VALUE

        for (keyword in allIncomeKeywords) {
            val idx = text.indexOf(keyword)
            if (idx >= 0 && idx < incomeIndex) {
                incomeIndex = idx
            }
        }

        for (keyword in allExpenseKeywords) {
            // "출금취소"에서 "출금"이 매칭되지 않도록
            if (keyword == "출금" && text.contains("출금취소")) continue

            val idx = text.indexOf(keyword)
            if (idx >= 0 && idx < expenseIndex) {
                expenseIndex = idx
            }
        }

        return when {
            incomeIndex < expenseIndex -> TransactionType.INCOME
            expenseIndex < incomeIndex -> TransactionType.EXPENSE
            else -> TransactionType.EXPENSE // 기본값
        }
    }

    /**
     * 가맹점명 추출 (출금/결제 시)
     */
    private fun extractMerchantName(text: String, isForeign: Boolean): String {
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
    private fun extractSenderName(text: String): String {
        // "김*성님" 형식의 마스킹된 이름 찾기
        val senderPatterns = listOf(
            Pattern.compile("([가-힣]\\*[가-힣]{1,2})님?\\s*(?:입금|송금|이체)"),
            Pattern.compile("(?:입금|송금|이체)\\s*([가-힣]\\*[가-힣]{1,2})님?"),
            Pattern.compile("([가-힣]{2,4})님?\\s*(?:입금|송금|이체)"),
            Pattern.compile("(?:입금|송금|이체)\\s*([가-힣]{2,4})님?"),
            Pattern.compile("보내신\\s*분\\s*[:：]?\\s*([가-힣*]+)"),
            Pattern.compile("([가-힣]\\*[가-힣]{1,2})님?에게서")
        )

        for (pattern in senderPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val sender = matcher.group(1)?.trim() ?: continue
                // 마스킹된 본인 이름이 아닌 경우에만 반환
                if (sender.isNotBlank() && sender.length in 2..5) {
                    return sender.replace("님", "")
                }
            }
        }
        return ""
    }

    /**
     * 입금 사유 추출 (출금취소, 환불 등)
     */
    private fun extractIncomeReason(text: String): String {
        val incomeReasons = listOf(
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
