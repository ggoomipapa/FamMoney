package com.ezcorp.fammoney.data.model

import kotlinx.serialization.Serializable

/**
 * ?¬ì©???ì ????¨í´
 * DataStore????¥ë?????ë°?´í¸ ?ì´ ?ì  ê°?? */
@Serializable
data class CustomBankPattern(
    val id: String,                          // ê³ ì  ID
    val displayName: String,                 // ?ì ?´ë¦ (?? "KBêµ???")
    val isEnabled: Boolean = true,           // ?ì±???¬ë"
    val packageNames: List<String>,          // ?ë¦¼ ?¨í¤ì§ëª?ëª©ë¡
    val amountRegex: String,                 // ê¸ì¡ ì¶ì¶ ?ê·
val incomeKeywords: List<String>,        // ?ì ?¤ì
val expenseKeywords: List<String>,       // ì§ì¶??¤ì
val merchantRegexList: List<String> = emptyList(),  // ?¬ì©ì²?ì¶ì¶ ?ê·??ëª©ë¡
    val isCustom: Boolean = false,           // ?¬ì©?ê? ì¶ê????¨í´?¸ì?
    val lastModified: Long = System.currentTimeMillis()
) {
    /**
     * BankConfigë¡?ë³??(ê¸°ì¡´ ?ì¤?ê³¼ ?¸í)
     */
    fun toBankConfig(): BankConfig {
        return BankConfig(
            bankId = id,
            displayName = displayName,
            packageNames = packageNames,
            incomeKeywords = incomeKeywords,
            expenseKeywords = expenseKeywords,
            amountRegex = amountRegex
        )
    }

    companion object {
        /**
         * ê¸°ë³¸ BankConfig?ì CustomBankPattern ?ì±
         */
        fun fromBankConfig(config: BankConfig): CustomBankPattern {
            return CustomBankPattern(
                id = config.bankId,
                displayName = config.displayName,
                packageNames = config.packageNames,
                amountRegex = config.amountRegex,
                incomeKeywords = config.incomeKeywords,
                expenseKeywords = config.expenseKeywords,
                isCustom = false
            )
        }

        /**
         * ê¸°ë³¸ ?¨í´ ëª©ë¡ ?ì±
         */
        fun getDefaultPatterns(): List<CustomBankPattern> {
            return BankConfig.getDefaultBanks().map { fromBankConfig(it) }
        }
    }
}

/**
 * ?¨í´ ?ì¤??ê²°ê³¼
 */
data class PatternTestResult(
    val success: Boolean,
    val amount: Long? = null,
    val transactionType: String? = null,  // "INCOME" or "EXPENSE"
    val merchantName: String? = null,
    val matchedPattern: String? = null,
    val errorMessage: String? = null
)
