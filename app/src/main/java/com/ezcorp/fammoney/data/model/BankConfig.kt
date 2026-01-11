package com.ezcorp.fammoney.data.model

data class BankConfig(
    val bankId: String,
    val displayName: String,
    val packageNames: List<String>,
    val incomeKeywords: List<String>,
    val expenseKeywords: List<String>,
    val amountRegex: String,
    val iconResId: Int = 0
) {
    companion object {
        fun getDefaultBanks(): List<BankConfig> = listOf(
            BankConfig(
                bankId = "kb_kookmin",
                displayName = "KB국민",
                packageNames = listOf(
                    "com.kbstar.kbbank",
                    "com.kbcard.kbkookmincard",
                    "com.kbstar.liivbank",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "shinhan",
                displayName = "신한",
                packageNames = listOf(
                    "com.shinhan.sbanking",
                    "com.shcard.smartpay",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "kakaobank",
                displayName = "카카오뱅크",
                packageNames = listOf(
                    "com.kakaobank.channel",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "woori",
                displayName = "우리",
                packageNames = listOf(
                    "com.wooribank.smart.npib",
                    "com.wooricard.smartapp",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "hana",
                displayName = "하나",
                packageNames = listOf(
                    "com.hanabank.ebk.channel.android.hananbank",
                    "com.hanaskcard.rocomo.potal",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "nh",
                displayName = "NH농협",
                packageNames = listOf(
                    "nh.smart.banking",
                    "com.nhncardsmartapp",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "ibk",
                displayName = "IBK기업",
                packageNames = listOf(
                    "com.ibk.android.ionebank",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "toss",
                displayName = "토스",
                packageNames = listOf(
                    "viva.republica.toss"
                ),
                incomeKeywords = listOf("입금", "받으셨", "들어옴", "이체받음", "송금받음", "받았어요", "출금취소"),
                expenseKeywords = listOf("출금", "결제", "이체", "송금", "사용", "승인", "지출", "보냈어요", "체크카드출금", "신용카드출금"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "samsung_card",
                displayName = "삼성카드",
                packageNames = listOf(
                    "kr.co.samsungcard.mpocket",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "hyundai_card",
                displayName = "현대카드",
                packageNames = listOf(
                    "com.hyundaicard.appcard",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "lotte_card",
                displayName = "롯데카드",
                packageNames = listOf(
                    "com.lcacApp",
                    "com.lottemembers.android",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "bc_card",
                displayName = "BC카드",
                packageNames = listOf(
                    "com.bccard.bcpayapp",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "shinhan_card",
                displayName = "신한카드",
                packageNames = listOf(
                    "com.shcard.smartpay",
                    "com.shinhancardsmartpayplus",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "kb_card",
                displayName = "KB국민카드",
                packageNames = listOf(
                    "com.kbcard.kbkookmincard",
                    "com.kbcard.cxh.appcard",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "hana_card",
                displayName = "하나카드",
                packageNames = listOf(
                    "com.hanaskcard.rocomo.potal",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "woori_card",
                displayName = "우리카드",
                packageNames = listOf(
                    "com.wooricard.smartapp",
                    "com.wooricard.wpay",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            ),
            BankConfig(
                bankId = "nh_card",
                displayName = "NH농협카드",
                packageNames = listOf(
                    "com.nhncardsmartapp",
                    "com.kakao.talk"
                ),
                incomeKeywords = listOf("취소", "환불"),
                expenseKeywords = listOf("승인", "결제", "사용", "일시불", "할부"),
                amountRegex = "([0-9,]+)\\s*원"
            )
        )
    }
}
