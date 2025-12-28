package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.data.model.TransactionType

data class ParsedTransaction(
    val amount: Long,
    val type: TransactionType,
    val bankConfig: BankConfig,
    val description: String,
    val merchantName: String,  // 사용처
    val senderName: String = "",  // 입금자 이름 (입금 거래 시)
    val accountNumber: String = "",  // 계좌번호 (마스킹 포함 가능)
    val originalText: String
)

class NotificationParser {

    fun parse(
        packageName: String,
        notificationText: String,
        selectedBanks: List<BankConfig>
    ): ParsedTransaction? {
        val matchingBank = selectedBanks.find { bank ->
            bank.packageNames.contains(packageName)
        } ?: return null

        return parseWithBankConfig(notificationText, matchingBank)
    }

    fun parseManualInput(
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

    private fun parseWithBankConfig(text: String, bankConfig: BankConfig): ParsedTransaction? {
        val amount = extractAmount(text, bankConfig.amountRegex) ?: return null

        val type = determineTransactionType(text, bankConfig)

        val merchantName = extractMerchantName(text)
        val description = extractDescription(text)

        // 입금 거래인 경우 송금자 이름과 계좌번호 추출
        val senderName = if (type == TransactionType.INCOME) {
            extractSenderName(text)
        } else ""

        val accountNumber = extractAccountNumber(text)

        return ParsedTransaction(
            amount = amount,
            type = type,
            bankConfig = bankConfig,
            description = description,
            merchantName = merchantName,
            senderName = senderName,
            accountNumber = accountNumber,
            originalText = text
        )
    }

    private fun extractAmount(text: String, regexPattern: String): Long? {
        val regex = Regex(regexPattern)
        val matchResult = regex.find(text) ?: return null

        val amountStr = matchResult.groupValues.getOrNull(1) ?: return null
        val cleanAmount = amountStr.replace(",", "").replace(" ", "")

        return cleanAmount.toLongOrNull()
    }

    private fun determineTransactionType(text: String, bankConfig: BankConfig): TransactionType {
        val hasIncomeKeyword = bankConfig.incomeKeywords.any { keyword ->
            text.contains(keyword)
        }

        val hasExpenseKeyword = bankConfig.expenseKeywords.any { keyword ->
            text.contains(keyword)
        }

        return when {
            hasIncomeKeyword && !hasExpenseKeyword -> TransactionType.INCOME
            hasExpenseKeyword && !hasIncomeKeyword -> TransactionType.EXPENSE
            hasIncomeKeyword && hasExpenseKeyword -> {
                val incomeIndex = bankConfig.incomeKeywords
                    .mapNotNull { text.indexOf(it).takeIf { idx -> idx >= 0 } }
                    .minOrNull() ?: Int.MAX_VALUE

                val expenseIndex = bankConfig.expenseKeywords
                    .mapNotNull { text.indexOf(it).takeIf { idx -> idx >= 0 } }
                    .minOrNull() ?: Int.MAX_VALUE

                if (incomeIndex < expenseIndex) TransactionType.INCOME else TransactionType.EXPENSE
            }
            else -> TransactionType.EXPENSE
        }
    }

    /**
     * 사용처(가맹점) 이름 추출
     * 다양한 은행/카드 SMS/알림 형식에서 사용처를 추출
     */
    private fun extractMerchantName(text: String): String {
        // 제외할 키워드 (은행/카드사 이름, 일반 키워드)
        val excludeKeywords = listOf(
            "은행", "카드", "국민", "신한", "우리", "하나", "신협", "새마을금고", "카카오뱅크", "토스뱅크",
            "승인", "거절", "결제", "출금", "입금", "일시불", "할부", "취소", "잔액", "체크", "신용",
            "님", "고객", "본인", "해외", "온라인", "오프라인"
        )

        // 다양한 카드/은행 SMS 형식의 사용처 패턴 (우선순위 순)
        val merchantPatterns = listOf(
            // 카드 알림 형식: "[카드사] 홍길동 쿠팡 50,000원" - 이름 뒤 사용처
            Regex("\\]\\s*[가-힣]{2,4}\\s+([가-힣a-zA-Z0-9][가-힣a-zA-Z0-9\\s]{0,20}?)\\s+[0-9,]+원"),
            // 카드 알림 형식: "삼성카드 스타벅스 12,500원" - 카드명 바로 뒤
            Regex("(?:삼성|현대|롯데|BC|KB|NH|IBK)(?:카드)?\\s+([가-힣a-zA-Z][가-힣a-zA-Z0-9\\s]{1,20}?)\\s+[0-9,]+원"),
            // 일시불 뒤에 오는 사용처: "일시불CU편의점 3,500원"
            Regex("(?:일시불|[0-9]+개월)\\s+([가-힣a-zA-Z][가-힣a-zA-Z0-9\\s]{1,20}?)\\s+[0-9,]+원"),
            // 승인 앞의 사용처: "스타벅스 12,500원 승인"
            Regex("([가-힣a-zA-Z][가-힣a-zA-Z0-9\\s]{1,20}?)\\s+[0-9,]+원\\s*(?:승인|결제|출금)"),
            // 결제 키워드 뒤의 사용처: "결제 배달의민족 25,000원"
            Regex("(?:결제|승인|사용)\\s+([가-힣a-zA-Z][가-힣a-zA-Z0-9\\s]{1,20}?)\\s+[0-9,]+원"),
            // 괄호 안의 사용처: "(쿠팡) 승인"
            Regex("\\(([가-힣a-zA-Z0-9][가-힣a-zA-Z0-9\\s]{0,20}?)\\)\\s*(?:[0-9,]+원)?\\s*(?:승인|결제|출금)?"),
            // 사용처 명시적 표기
            Regex("(?:사용처|가맹점|매장)[:\\s]+([가-힣a-zA-Z0-9][가-힣a-zA-Z0-9\\s]{1,20}?)(?:\\s|,|\\n|$)"),
            // 페이 결제: "카카오페이 스타벅스 5,000원"
            Regex("(?:네이버페이|카카오페이|토스|페이코|삼성페이|현대페이|신한페이)\\s+([가-힣a-zA-Z0-9][가-힣a-zA-Z0-9\\s]{1,20}?)\\s+[0-9,]+원"),
            // ~에서 형식: "스타벅스에서"
            Regex("([가-힣a-zA-Z0-9]{2,15})에서"),
            // 대괄호 안 (카드사 제외): "[스타벅스]"
            Regex("\\[([가-힣a-zA-Z0-9][가-힣a-zA-Z0-9\\s]{1,15}?)\\](?!카드|은행|승인|거절)"),
            // 금액 앞 사용처 (마지막 fallback): "GS25 3,500원"
            Regex("([가-힣a-zA-Z][가-힣a-zA-Z0-9]{1,15})\\s+[0-9,]+원")
        )

        for (pattern in merchantPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                var merchant = match.groupValues.getOrNull(1)?.trim() ?: continue

                // 앞뒤 공백 및 특수문자 정리
                merchant = merchant.trim()

                // 유효성 검사
                if (merchant.length < 2 || merchant.length > 25) continue

                // 제외 키워드 포함 여부 확인
                val containsExcluded = excludeKeywords.any { keyword ->
                    merchant.contains(keyword, ignoreCase = true)
                }
                if (containsExcluded) continue

                // 숫자로만 구성된 경우 제외
                if (merchant.all { it.isDigit() }) continue

                // 성공적으로 추출
                return merchant
            }
        }

        return ""
    }

    private fun extractDescription(text: String): String {
        val patterns = listOf(
            Regex("\\[(.+?)\\]"),
            Regex("(.+?)에서"),
            Regex("(.+?)결제"),
            Regex("잔액[:\\s]*([0-9,]+원)")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val desc = match.groupValues.getOrNull(1)?.trim()
                if (!desc.isNullOrBlank() && desc.length <= 50) {
                    return desc
                }
            }
        }

        return text.take(50)
    }

    /**
     * 입금 시 송금자 이름 추출
     * 다양한 은행 입금 알림 형식에서 보낸 사람 이름을 추출
     */
    private fun extractSenderName(text: String): String {
        val senderPatterns = listOf(
            // "김철수님이 보냈어요" 형식 (카카오뱅크/토스)
            Regex("([가-힣]{2,4})님이.+보냈"),
            // "김철수님으로부터" 형식
            Regex("([가-힣]{2,4})님으로부터"),
            // "김철수님이 입금" 형식
            Regex("([가-힣]{2,4})님이\\s*입금"),
            // "[은행] 입금 금액 이름" 형식
            Regex("입금\\s*[0-9,]+원\\s+([가-힣]{2,4})(?:\\s|$)"),
            // "[은행] 이름 금액 입금" 형식
            Regex("\\]\\s*([가-힣]{2,4})\\s+[0-9,]+원\\s*입금"),
            // "이름님 금액원 입금" 형식
            Regex("([가-힣]{2,4})님\\s+[0-9,]+원\\s*입금"),
            // "입금 이름" 형식 (금액 뒤에 이름)
            Regex("입금[^가-힣]*([가-힣]{2,4})(?:\\s|잔액|$)"),
            // 마스킹된 이름 "김*수" 형식
            Regex("([가-힣]\\*[가-힣])"),
            // "보낸분 김철수" 형식
            Regex("(?:보낸분|보내신 분|송금자)[:\\s]*([가-힣]{2,4})"),
            // "FROM 김철수" 형식
            Regex("FROM\\s*([가-힣]{2,4})")
        )

        for (pattern in senderPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val sender = match.groupValues.getOrNull(1)?.trim()
                if (!sender.isNullOrBlank() && sender.length in 2..5) {
                    // 은행/카드사 이름이 아닌 경우에만 반환
                val excludeKeywords = listOf("은행", "카드", "입금", "출금", "잔액", "계좌")
                    if (excludeKeywords.none { sender.contains(it) }) {
                        return sender
                    }
                }
            }
        }

        return ""
    }

    /**
     * 계좌번호 추출
     * 마스킹된 계좌번호도 추출 가능
     */
    private fun extractAccountNumber(text: String): String {
        val accountPatterns = listOf(
            // "123-456-789012" 형식
            Regex("(\\d{3,4}-\\d{2,4}-\\d{4,6})"),
            // "***-***-123456" 마스킹 형식
            Regex("(\\*{2,4}-\\*{2,4}-\\d{4,6})"),
            // "123***456" 형식
            Regex("(\\d{3,4}\\*{2,4}\\d{3,6})"),
            // "(1234)" 끝자리 형식
            Regex("\\((\\d{4})\\)\\s*(?:계좌|입금|출금)"),
            // "계좌 1234-5678" 형식
            Regex("계좌[:\\s]*(\\d{3,4}[\\-\\*]?\\d{2,4}[\\-\\*]?\\d{4,6})")
        )

        for (pattern in accountPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val account = match.groupValues.getOrNull(1)?.trim()
                if (!account.isNullOrBlank()) {
                    return account
                }
            }
        }

        return ""
    }

    companion object {
        const val HIGH_AMOUNT_THRESHOLD = 1_000_000L
    }
}
