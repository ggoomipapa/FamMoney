package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.DuplicateTransactionInfo
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.repository.DuplicateRepository
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.google.firebase.Timestamp
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ì¤ë³µ ê±°ë ê°ì? ?ë¹?? * ì¹´ë? ??ì???ì???ë¦¼???¤ë ê²½ì°ë¥?ê°ì??ì¬ ì²ë¦¬
 */
@Singleton
class DuplicateDetectionService @Inject constructor(
    private val duplicateRepository: DuplicateRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
) {
    // ìµê·¼ ê±°ëë¥??ì ???(ë©ëª¨ë¦?ìºì)
    // key: "${userId}_${amount}", value: TransactionCacheEntry
    private val recentTransactions = ConcurrentHashMap<String, TransactionCacheEntry>()

    // ì¤ë³µ ?ì? ?ê° ì°?(ë°ë¦¬ì´) - 2ë¶??´ë´ ê°ì? ê¸ì¡??ê±°ëë¥?ì¤ë³µ?¼ë¡ ê°ì£¼
    private val DUPLICATE_TIME_WINDOW_MS = 2 * 60 * 1000L

    /**
     * ??ê±°ëê° ì¤ë³µ?¸ì? ?ì¸?ê³  ì²ë¦¬
     * @return null?´ë©´ ì¤ë³µ???ë, PendingDuplicate?´ë©´ ì¤ë³µ ê°ì"
*/
    suspend fun checkAndHandleDuplicate(
        transaction: Transaction
    ): DuplicateCheckResult {
        val cacheKey = "${transaction.userId}_${transaction.amount}"
        val now = System.currentTimeMillis()

        // ?¤ë??ìºì ?ë¦¬
        cleanupExpiredCache(now)

        val existingEntry = recentTransactions[cacheKey]

        if (existingEntry != null) {
            val timeDiff = now - existingEntry.timestamp
            if (timeDiff < DUPLICATE_TIME_WINDOW_MS) {
                // ì¤ë³µ ê°ì??"
                val existingTransaction = existingEntry.transaction

                // ê¸°ì¡´ ê·ì¹???ëì§ ?ì¸
                val groupId = transaction.groupId
                val rule = duplicateRepository.getDuplicateRule(
                    groupId = groupId,
                    bank1Id = existingTransaction.bankId,
                    bank2Id = transaction.bankId
                )

                if (rule != null) {
                    // ê¸°ì¡´ ê·ì¹???°ë¼ ?ë ì²ë¦¬
                return when (rule.resolution) {
                        DuplicateResolution.KEEP_BOTH -> {
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.KeepBoth
                        }
                        DuplicateResolution.KEEP_FIRST -> {
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.SkipSecond
                        }
                        DuplicateResolution.KEEP_SECOND -> {
                            // ì²?ë²ì§¸ ê±°ë ?? 
                transactionRepository.deleteTransaction(existingTransaction.id)
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.KeepSecond(existingTransaction.id)
                        }
                        DuplicateResolution.DELETE_BOTH -> {
                            transactionRepository.deleteTransaction(existingTransaction.id)
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.DeleteBoth(existingTransaction.id)
                        }
                        DuplicateResolution.PENDING -> {
                            // ê·ì¹??PENDING?´ë©´ ?¬ì©?ìê²?ë¬¼ì´ë´?
                createPendingDuplicate(existingTransaction, transaction, cacheKey)
                        }
                    }
                } else {
                    // ê·ì¹???ì¼ë©??¬ì©?ìê²?ë¬¼ì´ë´?
                return createPendingDuplicate(existingTransaction, transaction, cacheKey)
                }
            }
        }

        // ìºì???
recentTransactions[cacheKey] = TransactionCacheEntry(
            transaction = transaction,
            timestamp = now
        )

        return DuplicateCheckResult.NoDuplicate
    }

    private suspend fun createPendingDuplicate(
        existingTransaction: Transaction,
        newTransaction: Transaction,
        cacheKey: String
    ): DuplicateCheckResult {
        val pendingDuplicate = PendingDuplicate(
            groupId = existingTransaction.groupId,
            userId = existingTransaction.userId,
            amount = existingTransaction.amount,
            transaction1 = DuplicateTransactionInfo(
                transactionId = existingTransaction.id,
                bankId = existingTransaction.bankId,
                bankName = existingTransaction.bankName,
                description = existingTransaction.description,
                type = existingTransaction.type,
                notificationTime = existingTransaction.transactionDate ?: Timestamp.now(),
                originalText = existingTransaction.originalText
            ),
            transaction2 = DuplicateTransactionInfo(
                transactionId = newTransaction.id,
                bankId = newTransaction.bankId,
                bankName = newTransaction.bankName,
                description = newTransaction.description,
                type = newTransaction.type,
                notificationTime = newTransaction.transactionDate ?: Timestamp.now(),
                originalText = newTransaction.originalText
            ),
            createdAt = Timestamp.now()
        )

        duplicateRepository.addPendingDuplicate(pendingDuplicate)
        recentTransactions.remove(cacheKey)

        return DuplicateCheckResult.DuplicateDetected(pendingDuplicate)
    }

    private fun cleanupExpiredCache(currentTime: Long) {
        val expiredKeys = recentTransactions.entries
            .filter { currentTime - it.value.timestamp > DUPLICATE_TIME_WINDOW_MS }
            .map { it.key }

        expiredKeys.forEach { recentTransactions.remove(it) }
    }

    /**
     * ì¤ë³µ ?´ê²° ì²ë¦¬
     */
    suspend fun resolveDuplicate(
        duplicateId: String,
        resolution: DuplicateResolution,
        transaction1Id: String,
        transaction2Id: String,
        bank1Id: String,
        bank2Id: String,
        groupId: String,
        applyToFuture: Boolean
    ) {
        // ?´ê²° ë°©ë²???°ë¼ ê±°ë ?? 
        when (resolution) {
            DuplicateResolution.KEEP_FIRST -> {
                transactionRepository.deleteTransaction(transaction2Id)
            }
            DuplicateResolution.KEEP_SECOND -> {
                transactionRepository.deleteTransaction(transaction1Id)
            }
            DuplicateResolution.DELETE_BOTH -> {
                transactionRepository.deleteTransaction(transaction1Id)
                transactionRepository.deleteTransaction(transaction2Id)
            }
            else -> {
                // KEEP_BOTH, PENDING? ??  ?ì
            }
        }

        // ì¤ë³µ ê¸°ë¡ ?´ê²° ì²ë¦¬
        duplicateRepository.resolveDuplicate(duplicateId, resolution)

        // "?¤ìë¶???ì¼?ê² ?ì©" ? í ??ê·ì¹ ?
if (applyToFuture && resolution != DuplicateResolution.PENDING) {
            val rule = com.ezcorp.fammoney.data.model.DuplicateRule(
                groupId = groupId,
                bank1Id = bank1Id,
                bank2Id = bank2Id,
                resolution = resolution
            )
            duplicateRepository.addDuplicateRule(rule)
        }
    }

    private data class TransactionCacheEntry(
        val transaction: Transaction,
        val timestamp: Long
    )
}

/**
 * ì¤ë³µ ?ì¸ ê²°ê³¼
 */
sealed class DuplicateCheckResult {
    /** ì¤ë³µ ?ë - ?ì ???*/
    object NoDuplicate : DuplicateCheckResult()

    /** ì¤ë³µ ê°ì? - ?¬ì©???ì¸ ?ì */
    data class DuplicateDetected(val pendingDuplicate: PendingDuplicate) : DuplicateCheckResult()

    /** ê·ì¹???°ë¼ ????? ì? */
    object KeepBoth : DuplicateCheckResult()

    /** ê·ì¹???°ë¼ ??ë²ì§¸ ê±°ë ê±´ë? */
    object SkipSecond : DuplicateCheckResult()

    /** ê·ì¹???°ë¼ ??ë²ì§¸ë§?? ì? (ì²?ë²ì§¸ ?? ?? */
    data class KeepSecond(val deletedTransactionId: String) : DuplicateCheckResult()

    /** ê·ì¹???°ë¼ ??????  */
    data class DeleteBoth(val deletedTransactionId: String) : DuplicateCheckResult()
}
