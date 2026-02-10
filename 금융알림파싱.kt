/**
 * 한국 금융기관별 알림 파싱 시스템
 * 가계부 앱에서 NotificationListenerService와 함께 사용
 * 
 * 지원 금융기관:
 * - 카드사: 삼성, 신한, 현대, KB국민, 하나, 롯데, BC, NH농협, 우리
 * - 은행: KB국민, 신한, 우리, 하나, 농협, 기업, SC제일, 케이뱅크, 카카오뱅크, 토스뱅크
 * - 간편결제: 카카오페이, 네이버페이, 토스, 삼성페이, 페이코
 */

import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// ==================== 데이터 모델 ====================

data class TransactionData(
    val type: TransactionType,          // 거래 유형
    val institution: String,            // 금융기관명
    val cardName: String? = null,       // 카드명/계좌명
    val cardNumber: String? = null,     // 카드번호 마스킹
    val amount: Long,                   // 거래금액
    val balance: Long? = null,          // 잔액 (은행)
    val cumulativeAmount: Long? = null, // 누적금액 (카드)
    val merchantName: String,           // 가맹점/상호명
    val transactionDate: Date,          // 거래일시
    val installment: Int? = null,       // 할부개월 (null이면 일시불)
    val isApproval: Boolean = true,     // 승인/취소 여부
    val category: String? = null,       // 카테고리 (자동분류용)
    val rawMessage: String              // 원본 메시지
)

enum class TransactionType {
    CARD_PAYMENT,       // 카드 결제
    CARD_CANCEL,        // 카드 취소
    BANK_WITHDRAW,      // 출금
    BANK_DEPOSIT,       // 입금
    BANK_TRANSFER,      // 이체
    EASY_PAY,           // 간편결제
    CHECK_CARD,         // 체크카드
    AUTO_PAYMENT,       // 자동이체
    UNKNOWN
}

// ==================== 금융기관별 알림 형식 정의 ====================

/**
 * 📌 각 금융기관별 실제 SMS/푸시 알림 형식 예시
 */
object FinancialNotificationFormats {
    
    // ========== 카드사 SMS 형식 ==========
    
    val CARD_FORMATS = mapOf(
        // 삼성카드
        "삼성" to listOf(
            "[Web발신]\n삼성카드 승인\n이*지님\n12/25 14:30\n(일시불)35,000원\n스타벅스강남점\n누적1,234,567원",
            "[Web발신]\n삼성카드 승인\n이*지님 12/25 14:30\n35,000원 일시불\n스타벅스강남점\n누적1,234,567원",
            "[Web발신]\n삼성(7*4*) 승인\n12/25 14:30\n35,000원(일시불)\n스타벅스강남\n누적:1,234,567원"
        ),
        
        // 신한카드
        "신한" to listOf(
            "[Web발신]\n신한카드(1*2*)승인\n이*지님 35,000원\n일시불 12/25 14:30\n스타벅스강남점",
            "[Web발신]\n신한카드 승인\n이*지(1234) 35,000원\n일시불 12/25 14:30\n스타벅스강남\n누적:567,890원",
            "[Web발신]\n신한체크 승인\n35,000원 12/25 14:30\n스타벅스강남"
        ),
        
        // 현대카드
        "현대" to listOf(
            "[Web발신]\n현대카드 35,000원 승인\n12/25 14:30 일시불\n스타벅스강남점\n누적123,456원",
            "[Web발신]\n현대(M3)35,000원승인\n12/25 14:30 일시불\n스타벅스강남\n누적 567,890원",
            "[Web발신]\n현대M카드 이*지님\n35,000원 일시불\n12/25 14:30\n스타벅스강남점"
        ),
        
        // KB국민카드
        "KB국민" to listOf(
            "[Web발신]\n[KB]12/25 14:30\n이*지님(1234)\n스타벅스강남\n일시불 35,000원\n누적:1,234,567원",
            "[Web발신]\nKB국민카드 승인\n35,000원 일시불\n12/25 14:30\n스타벅스강남\n이*지(1*2*)",
            "[Web발신]\n[KB국민]12/25 14:30\n279801**027\n스타벅스강남\n체크카드출금\n35,000원\n잔액1,234,567원"
        ),
        
        // 하나카드
        "하나" to listOf(
            "[Web발신]\n하나(6*8*) ***님\n12/25 14:30\n스타벅스강남\n일시불/35,000원\n누적-567,890원",
            "[Web발신]\n하나카드 승인\n12/25 14:30 35,000원\n스타벅스강남 일시불\n누적:1,234,567원",
            "[Web발신]\n[하나]이*지님\n35,000원 일시불\n12/25 14:30\n스타벅스강남점"
        ),
        
        // 롯데카드
        "롯데" to listOf(
            "[Web발신]\n롯데카드 승인\n35,000원(일시불)\n12/25 14:30\n스타벅스강남\n이*지님",
            "[Web발신]\n롯데(5*3*) 이*지님\n35,000원 일시불\n12/25 14:30 스타벅스강남\n누적567,890원",
            "[Web발신]\n[롯데카드]\n승인 35,000원\n스타벅스강남점\n12/25 14:30"
        ),
        
        // BC카드
        "BC" to listOf(
            "[Web발신]\nBC카드 승인\n35,000원(일시불)\n12/25 14:30\n스타벅스강남",
            "[Web발신]\n비씨(4*1*) 승인\n35,000원 일시불\n12/25 14:30\n스타벅스강남\n누적:234,567원",
            "[Web발신]\n[BC]이*지님\n35,000원 승인\n스타벅스강남 일시불"
        ),
        
        // NH농협카드
        "NH농협" to listOf(
            "[Web발신]\n농협카드 승인\n35,000원(일시불)\n12/25 14:30\n스타벅스강남",
            "[Web발신]\nNH농협(9*2*)승인\n35,000원 12/25 14:30\n스타벅스강남 일시불\n누적:345,678원",
            "[Web발신]\n[NH]이*지님\n35,000원 일시불\n스타벅스강남점"
        ),
        
        // 우리카드
        "우리" to listOf(
            "[Web발신]\n우리카드 승인\n35,000원 일시불\n12/25 14:30\n스타벅스강남",
            "[Web발신]\n우리(3*7*)이*지님\n35,000원 승인\n12/25 14:30 스타벅스강남\n누적:456,789원",
            "[Web발신]\n[우리카드]승인\n35,000원(일시불)\n스타벅스강남점"
        )
    )
    
    // ========== 은행 SMS 형식 ==========
    
    val BANK_FORMATS = mapOf(
        // KB국민은행
        "KB국민" to listOf(
            "[Web발신]\n[KB]12/25 14:30\n279801**027\n출금 35,000원\n스타벅스강남\n잔액 1,234,567원",
            "[Web발신]\n[KB국민]입금\n35,000원 12/25 14:30\n홍길동\n잔액 2,345,678원",
            "[Web발신]\nKB 12/25 14:30\n*0027\n출금 35,000원\n스타벅스 잔액1,234,567원"
        ),
        
        // 신한은행
        "신한" to listOf(
            "[Web발신]\n[신한]출금 35,000원\n12/25 14:30\n스타벅스강남\n잔액 1,234,567원",
            "[Web발신]\n신한 입금 35,000원\n12/25 14:30\n홍길동\n잔액:2,345,678원",
            "[Web발신]\n[신한은행]이체\n35,000원 12/25 14:30\n받는통장:우리 홍길동\n잔액 987,654원"
        ),
        
        // 우리은행
        "우리" to listOf(
            "[Web발신]\n우리 12/25 14:30\n*250284\n출금 35,000원\n스타벅스강남\n잔액 1,234,567원",
            "[Web발신]\n[우리]입금 35,000원\n12/25 14:30\n홍길동\n잔액:2,345,678원",
            "[Web발신]\n우리은행 출금\n35,000원 12/25 14:30\n잔액 987,654원"
        ),
        
        // 하나은행
        "하나" to listOf(
            "[Web발신]\n[하나]출금35,000원\n12/25 14:30\n스타벅스강남\n잔액1,234,567원",
            "[Web발신]\n하나은행 입금\n35,000원 12/25 14:30\n홍길동\n잔액 2,345,678원",
            "[Web발신]\n하나 이체완료\n35,000원\n받는분:홍길동\n잔액:987,654원"
        ),
        
        // 농협은행
        "농협" to listOf(
            "[Web발신]\n농협 출금35,000원\n12/25 14:30\n301-****-2640-41\n스타벅스\n잔액1,234,567원",
            "[Web발신]\n[NH농협]입금 35,000원\n12/25 14:30\n홍길동\n잔액:2,345,678원",
            "[Web발신]\nNH 12/25 14:30\n출금 35,000원\n잔액 987,654원"
        ),
        
        // 기업은행
        "기업" to listOf(
            "[Web발신]\n기업 출금35,000원\n12/25 14:30\n스타벅스강남\n잔액1,234,567원",
            "[Web발신]\n[IBK]입금 35,000원\n12/25 14:30\n홍길동\n잔액:2,345,678원",
            "[Web발신]\n2024/12/25 14:30\n출금 35,000원\n잔액 987,654원\n현대카드\n469***03801011\n기업"
        ),
        
        // 카카오뱅크
        "카카오뱅크" to listOf(
            "[Web발신]\n[카카오뱅크]출금\n35,000원 12/25 14:30\n스타벅스강남\n잔액 1,234,567원",
            "[Web발신]\n카카오뱅크 입금\n35,000원\n홍길동\n잔액:2,345,678원",
            "[Web발신]\n카뱅 이체완료\n35,000원 →홍길동\n잔액 987,654원"
        ),
        
        // 토스뱅크
        "토스뱅크" to listOf(
            "[Web발신]\n[토스뱅크]출금\n35,000원 12/25 14:30\n스타벅스강남\n잔액 1,234,567원",
            "[Web발신]\n토스뱅크 입금\n35,000원\n홍길동으로부터\n잔액:2,345,678원"
        ),
        
        // 케이뱅크
        "케이뱅크" to listOf(
            "[Web발신]\n[케이뱅크]출금\n35,000원 12/25 14:30\n스타벅스강남\n잔액 1,234,567원",
            "[Web발신]\nK뱅크 입금 35,000원\n홍길동\n잔액:2,345,678원"
        )
    )
    
    // ========== 간편결제 알림 형식 (카카오톡 알림톡/푸시) ==========
    
    val EASY_PAY_FORMATS = mapOf(
        // 카카오페이
        "카카오페이" to listOf(
            "[카카오페이] 결제완료\n35,000원\n스타벅스강남점\n12/25 14:30",
            "[카카오페이] 결제\n이*지님 35,000원\n스타벅스강남\n12/25 14:30\n신한카드(1234)",
            "카카오페이머니 결제\n35,000원\n스타벅스강남점"
        ),
        
        // 네이버페이
        "네이버페이" to listOf(
            "[네이버페이] 결제완료\n35,000원\n스타벅스강남점\n12/25 14:30",
            "네이버페이 결제\n35,000원 스타벅스강남\n신한카드(1234) 일시불",
            "[NAVER PAY] 35,000원 결제\n스타벅스강남점"
        ),
        
        // 토스
        "토스" to listOf(
            "[토스] 결제완료\n35,000원\n스타벅스강남점\n12/25 14:30",
            "토스 결제 35,000원\n스타벅스강남\n토스머니 사용",
            "[토스페이] 이*지님\n35,000원 결제완료"
        ),
        
        // 삼성페이
        "삼성페이" to listOf(
            "[삼성페이] 결제\n35,000원\n스타벅스강남점\n삼성카드(1234)",
            "삼성페이 결제완료\n35,000원 일시불\n스타벅스강남",
            "[Samsung Pay] 35,000원\n스타벅스강남점"
        ),
        
        // 페이코
        "페이코" to listOf(
            "[PAYCO] 결제완료\n35,000원\n스타벅스강남점\n12/25 14:30",
            "페이코 결제 35,000원\n스타벅스강남\n신한카드",
            "[페이코] 35,000원 결제\n12/25 14:30"
        )
    )
}


// ==================== 파싱 엔진 ====================

class FinancialNotificationParser {
    
    companion object {
        // 금액 추출 패턴들
        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("(?:승인|결제|출금|입금|이체)?\\s*([\\d,]+)\\s*원"),
            Pattern.compile("([\\d,]+)원\\s*(?:승인|결제|출금|입금)"),
            Pattern.compile("(?:일시불|\\d+개월)[/\\s]*([\\d,]+)\\s*원"),
            Pattern.compile("([\\d,]+)\\s*원\\s*(?:일시불|\\d+개월)")
        )
        
        // 잔액 추출 패턴
        private val BALANCE_PATTERNS = listOf(
            Pattern.compile("잔액\\s*[:：]?\\s*([\\d,]+)\\s*원?"),
            Pattern.compile("잔액([\\d,]+)원?")
        )
        
        // 누적금액 추출 패턴
        private val CUMULATIVE_PATTERNS = listOf(
            Pattern.compile("누적\\s*[:：\\-]?\\s*([\\d,]+)\\s*원?"),
            Pattern.compile("누적([\\d,]+)원?")
        )
        
        // 날짜/시간 추출 패턴
        private val DATE_PATTERNS = listOf(
            Pattern.compile("(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})"),
            Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})\\s+(\\d{1,2}):(\\d{2})"),
            Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})")
        )
        
        // 할부 추출 패턴
        private val INSTALLMENT_PATTERN = Pattern.compile("(\\d+)\\s*개월")
        
        // 카드번호 마스킹 패턴
        private val CARD_NUMBER_PATTERNS = listOf(
            Pattern.compile("\\(([\\d*]+)\\)"),
            Pattern.compile("([\\d*]{4,})"),
            Pattern.compile("\\*+(\\d+)")
        )
        
        // 금융기관 식별 키워드
        private val INSTITUTION_KEYWORDS = mapOf(
            // 카드사
            "삼성카드" to "삼성",
            "삼성(" to "삼성",
            "신한카드" to "신한",
            "신한(" to "신한",
            "신한체크" to "신한",
            "현대카드" to "현대",
            "현대(" to "현대",
            "현대M" to "현대",
            "[KB]" to "KB국민",
            "KB국민" to "KB국민",
            "KB(" to "KB국민",
            "하나(" to "하나",
            "하나카드" to "하나",
            "[하나]" to "하나",
            "롯데카드" to "롯데",
            "롯데(" to "롯데",
            "[롯데" to "롯데",
            "BC카드" to "BC",
            "비씨(" to "BC",
            "[BC]" to "BC",
            "농협카드" to "NH농협",
            "NH농협(" to "NH농협",
            "[NH]" to "NH농협",
            "우리카드" to "우리",
            "우리(" to "우리",
            "[우리" to "우리",
            
            // 은행
            "[신한]" to "신한은행",
            "신한 " to "신한은행",
            "신한은행" to "신한은행",
            "우리 " to "우리은행",
            "[우리]" to "우리은행",
            "우리은행" to "우리은행",
            "[하나]" to "하나은행",
            "하나은행" to "하나은행",
            "하나 " to "하나은행",
            "농협 " to "농협은행",
            "[NH농협]" to "농협은행",
            "기업 " to "기업은행",
            "[IBK]" to "기업은행",
            "기업은행" to "기업은행",
            "카카오뱅크" to "카카오뱅크",
            "[카카오뱅크]" to "카카오뱅크",
            "카뱅 " to "카카오뱅크",
            "토스뱅크" to "토스뱅크",
            "[토스뱅크]" to "토스뱅크",
            "케이뱅크" to "케이뱅크",
            "[케이뱅크]" to "케이뱅크",
            "K뱅크" to "케이뱅크",
            
            // 간편결제
            "카카오페이" to "카카오페이",
            "[카카오페이]" to "카카오페이",
            "네이버페이" to "네이버페이",
            "[네이버페이]" to "네이버페이",
            "NAVER PAY" to "네이버페이",
            "[토스]" to "토스",
            "토스 " to "토스",
            "토스페이" to "토스",
            "삼성페이" to "삼성페이",
            "[삼성페이]" to "삼성페이",
            "Samsung Pay" to "삼성페이",
            "PAYCO" to "페이코",
            "페이코" to "페이코",
            "[페이코]" to "페이코"
        )
        
        // 거래 유형 키워드
        private val TRANSACTION_TYPE_KEYWORDS = mapOf(
            "승인" to TransactionType.CARD_PAYMENT,
            "결제" to TransactionType.CARD_PAYMENT,
            "취소" to TransactionType.CARD_CANCEL,
            "출금" to TransactionType.BANK_WITHDRAW,
            "체크카드출금" to TransactionType.CHECK_CARD,
            "입금" to TransactionType.BANK_DEPOSIT,
            "이체" to TransactionType.BANK_TRANSFER,
            "자동이체" to TransactionType.AUTO_PAYMENT
        )
        
        // 제외 키워드 (가맹점명이 아닌 것들)
        private val EXCLUDED_KEYWORDS = setOf(
            "Web발신", "[Web발신]", "승인", "결제", "출금", "입금", "이체",
            "일시불", "할부", "잔액", "누적", "님", "체크카드", "신용카드",
            "원", "개월", "취소"
        )
    }
    
    /**
     * 알림 메시지 파싱
     */
    fun parse(message: String, packageName: String? = null): TransactionData? {
        if (message.isBlank()) return null
        
        // 1. 금융기관 식별
        val institution = identifyInstitution(message) ?: return null
        
        // 2. 거래 유형 식별
        val transactionType = identifyTransactionType(message)
        
        // 3. 금액 추출
        val amount = extractAmount(message) ?: return null
        
        // 4. 날짜/시간 추출
        val transactionDate = extractDate(message) ?: Date()
        
        // 5. 가맹점명 추출
        val merchantName = extractMerchantName(message)
        
        // 6. 잔액/누적금액 추출
        val balance = extractBalance(message)
        val cumulativeAmount = extractCumulativeAmount(message)
        
        // 7. 할부 추출
        val installment = extractInstallment(message)
        
        // 8. 카드번호 추출
        val cardNumber = extractCardNumber(message)
        
        // 9. 승인/취소 여부
        val isApproval = !message.contains("취소")
        
        return TransactionData(
            type = transactionType,
            institution = institution,
            cardNumber = cardNumber,
            amount = amount,
            balance = balance,
            cumulativeAmount = cumulativeAmount,
            merchantName = merchantName,
            transactionDate = transactionDate,
            installment = installment,
            isApproval = isApproval,
            rawMessage = message
        )
    }
    
    /**
     * 금융기관 식별
     */
    private fun identifyInstitution(message: String): String? {
        for ((keyword, institution) in INSTITUTION_KEYWORDS) {
            if (message.contains(keyword)) {
                return institution
            }
        }
        return null
    }
    
    /**
     * 거래 유형 식별
     */
    private fun identifyTransactionType(message: String): TransactionType {
        for ((keyword, type) in TRANSACTION_TYPE_KEYWORDS) {
            if (message.contains(keyword)) {
                return type
            }
        }
        return TransactionType.UNKNOWN
    }
    
    /**
     * 금액 추출
     */
    private fun extractAmount(message: String): Long? {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val amountStr = matcher.group(1).replace(",", "")
                return amountStr.toLongOrNull()
            }
        }
        return null
    }
    
    /**
     * 잔액 추출
     */
    private fun extractBalance(message: String): Long? {
        for (pattern in BALANCE_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val balanceStr = matcher.group(1).replace(",", "")
                return balanceStr.toLongOrNull()
            }
        }
        return null
    }
    
    /**
     * 누적금액 추출
     */
    private fun extractCumulativeAmount(message: String): Long? {
        for (pattern in CUMULATIVE_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val cumulativeStr = matcher.group(1).replace(",", "")
                return cumulativeStr.toLongOrNull()
            }
        }
        return null
    }
    
    /**
     * 날짜/시간 추출
     */
    private fun extractDate(message: String): Date? {
        val calendar = Calendar.getInstance()
        
        for (pattern in DATE_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                return when (matcher.groupCount()) {
                    5 -> {
                        // yyyy/MM/dd HH:mm
                        calendar.set(
                            matcher.group(1).toInt(),
                            matcher.group(2).toInt() - 1,
                            matcher.group(3).toInt(),
                            matcher.group(4).toInt(),
                            matcher.group(5).toInt()
                        )
                        calendar.time
                    }
                    4 -> {
                        // MM/dd HH:mm (현재 년도 사용)
                        calendar.set(Calendar.MONTH, matcher.group(1).toInt() - 1)
                        calendar.set(Calendar.DAY_OF_MONTH, matcher.group(2).toInt())
                        calendar.set(Calendar.HOUR_OF_DAY, matcher.group(3).toInt())
                        calendar.set(Calendar.MINUTE, matcher.group(4).toInt())
                        calendar.time
                    }
                    2 -> {
                        // MM/dd (현재 년도, 시간은 현재 시간)
                        calendar.set(Calendar.MONTH, matcher.group(1).toInt() - 1)
                        calendar.set(Calendar.DAY_OF_MONTH, matcher.group(2).toInt())
                        calendar.time
                    }
                    else -> null
                }
            }
        }
        return null
    }
    
    /**
     * 할부 개월 추출
     */
    private fun extractInstallment(message: String): Int? {
        if (message.contains("일시불")) return null
        
        val matcher = INSTALLMENT_PATTERN.matcher(message)
        if (matcher.find()) {
            return matcher.group(1).toIntOrNull()
        }
        return null
    }
    
    /**
     * 카드번호 추출 (마스킹된 형태)
     */
    private fun extractCardNumber(message: String): String? {
        for (pattern in CARD_NUMBER_PATTERNS) {
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val number = matcher.group(1)
                if (number.length >= 4 && number.contains("*")) {
                    return number
                }
            }
        }
        return null
    }
    
    /**
     * 가맹점명 추출
     */
    private fun extractMerchantName(message: String): String {
        val lines = message.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        
        for (line in lines) {
            // 제외 키워드가 포함된 라인 스킵
            val containsExcluded = EXCLUDED_KEYWORDS.any { line.contains(it) }
            
            // 금액 패턴이 있는 라인 스킵
            val hasAmount = AMOUNT_PATTERNS.any { it.matcher(line).find() }
            
            // 날짜 패턴이 있는 라인 스킵
            val hasDate = DATE_PATTERNS.any { it.matcher(line).find() }
            
            // 잔액/누적 패턴이 있는 라인 스킵
            val hasBalance = BALANCE_PATTERNS.any { it.matcher(line).find() }
            val hasCumulative = CUMULATIVE_PATTERNS.any { it.matcher(line).find() }
            
            // 카드번호 패턴만 있는 라인 스킵
            val isCardNumberOnly = line.matches(Regex("^[\\d*\\-]+$"))
            
            if (!containsExcluded && !hasAmount && !hasDate && !hasBalance && 
                !hasCumulative && !isCardNumberOnly && line.length >= 2) {
                
                // 한글 또는 영문숫자가 포함된 유효한 가맹점명
                if (line.matches(Regex(".*[가-힣a-zA-Z0-9].*"))) {
                    return line
                }
            }
        }
        
        return "알수없음"
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
            "이마트", "롯데마트", "홈플러스", "코스트코"
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
            "건강보험", "고용보험", "아파트", "월세", "관리비"
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


// ==================== 사용 예시 ====================

fun main() {
    val parser = FinancialNotificationParser()
    
    // 테스트 메시지들
    val testMessages = listOf(
        """[Web발신]
[KB]12/25 14:30
279801**027
스타벅스강남
체크카드출금
18,000원
잔액238,281원""",
        
        """[Web발신]
신한카드(1*2*)승인
이*지님 35,000원
일시불 12/25 14:30
맥도날드강남점
누적:567,890원""",
        
        """[Web발신]
우리 12/25 14:30
*250284
출금 3,000원
GS25강남점
잔액 214,164원""",
        
        """[카카오페이] 결제완료
15,000원
배달의민족
12/25 15:00"""
    )
    
    for (msg in testMessages) {
        println("=" .repeat(50))
        println("원본 메시지:")
        println(msg)
        println("-".repeat(50))
        
        val result = parser.parse(msg)
        if (result != null) {
            println("파싱 결과:")
            println("  금융기관: ${result.institution}")
            println("  거래유형: ${result.type}")
            println("  금액: ${String.format("%,d", result.amount)}원")
            println("  가맹점: ${result.merchantName}")
            println("  카테고리: ${CategoryClassifier.classify(result.merchantName)}")
            println("  잔액: ${result.balance?.let { String.format("%,d", it) + "원" } ?: "없음"}")
            println("  누적: ${result.cumulativeAmount?.let { String.format("%,d", it) + "원" } ?: "없음"}")
            println("  할부: ${result.installment?.let { "${it}개월" } ?: "일시불"}")
            println("  날짜: ${result.transactionDate}")
        } else {
            println("파싱 실패")
        }
        println()
    }
}
