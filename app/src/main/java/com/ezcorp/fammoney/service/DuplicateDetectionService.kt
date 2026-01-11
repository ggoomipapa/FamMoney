package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.DuplicateTransactionInfo
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.repository.DuplicateRepository
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.google.firebase.Timestamp
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 중복 거래 감지 서비스
 * 카드와 은행에서 알림이 동시에 오는 경우를 감지하여 처리
 */
@Singleton
class DuplicateDetectionService @Inject constructor(
    private val duplicateRepository: DuplicateRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
) {
    // 최근 거래를 저장 (메모리 캐시)
    // key: "${userId}_${amount}", value: TransactionCacheEntry
    private val recentTransactions = ConcurrentHashMap<String, TransactionCacheEntry>()

    // 중복 감지 시간 창 (밀리초) - 2분 이내 같은 금액의 거래를 중복으로 간주
    private val DUPLICATE_TIME_WINDOW_MS = 2 * 60 * 1000L

    // 카드 알림 키워드
    private val cardKeywords = listOf("승인", "일시불", "할부", "체크", "신용", "카드")
    // 은행 알림 키워드
    private val bankKeywords = listOf("출금", "이체", "입금", "계좌")

    /**
     * 알림 텍스트가 카드 알림인지 판단
     */
    private fun isCardNotification(originalText: String): Boolean {
        val cardScore = cardKeywords.count { originalText.contains(it) }
        val bankScore = bankKeywords.count { originalText.contains(it) }
        return cardScore > bankScore
    }

    /**
     * 새 거래가 중복인지 확인하고 처리
     */
    suspend fun checkAndHandleDuplicate(
        transaction: Transaction
    ): DuplicateCheckResult {
        val cacheKey = "${transaction.userId}_${transaction.amount}"
        val now = System.currentTimeMillis()

        // 오래된 캐시 정리
        cleanupExpiredCache(now)

        val existingEntry = recentTransactions[cacheKey]

        if (existingEntry != null) {
            val timeDiff = now - existingEntry.timestamp
            if (timeDiff < DUPLICATE_TIME_WINDOW_MS) {
                // 중복 감지됨
                val existingTransaction = existingEntry.transaction

                // 사용자 설정 확인 (카드 우선 / 은행 우선 / 매번 물어보기)
                val preference = userPreferences.getDuplicatePreference()

                if (preference != UserPreferences.DUPLICATE_PREF_ASK) {
                    // 자동 처리
                    val existingIsCard = isCardNotification(existingTransaction.originalText)
                    val newIsCard = isCardNotification(transaction.originalText)

                    // 같은 유형이면 첫 번째 유지
                    if (existingIsCard == newIsCard) {
                        recentTransactions.remove(cacheKey)
                        return DuplicateCheckResult.SkipSecond
                    }

                    val keepCard = preference == UserPreferences.DUPLICATE_PREF_CARD

                    return if (keepCard) {
                        // 카드 알림 우선
                        if (existingIsCard) {
                            // 기존이 카드 -> 새 거래(은행) 스킵
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.SkipSecond
                        } else {
                            // 새 거래가 카드 -> 기존(은행) 삭제
                            transactionRepository.deleteTransaction(existingTransaction.id)
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.KeepSecond(existingTransaction.id)
                        }
                    } else {
                        // 은행 알림 우선
                        if (!existingIsCard) {
                            // 기존이 은행 -> 새 거래(카드) 스킵
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.SkipSecond
                        } else {
                            // 새 거래가 은행 -> 기존(카드) 삭제
                            transactionRepository.deleteTransaction(existingTransaction.id)
                            recentTransactions.remove(cacheKey)
                            DuplicateCheckResult.KeepSecond(existingTransaction.id)
                        }
                    }
                }

                // 기존 규칙이 있는지 확인
                val groupId = transaction.groupId
                val rule = duplicateRepository.getDuplicateRule(
                    groupId = groupId,
                    bank1Id = existingTransaction.bankId,
                    bank2Id = transaction.bankId
                )

                if (rule != null) {
                    // 기존 규칙에 따라 자동 처리
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
                            createPendingDuplicate(existingTransaction, transaction, cacheKey)
                        }
                    }
                } else {
                    // 규칙이 없으면 사용자에게 물어봄
                    return createPendingDuplicate(existingTransaction, transaction, cacheKey)
                }
            }
        }

        // 캐시에 추가
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
     * 중복 해결 처리
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
        // 해결 방법에 따라 거래 삭제
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
                // KEEP_BOTH, PENDING은 삭제 없음
            }
        }

        // 중복 기록 해결 처리
        duplicateRepository.resolveDuplicate(duplicateId, resolution)

        // "앞으로도 같이 적용" 선택 시 규칙 저장
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
 * 중복 확인 결과
 */
sealed class DuplicateCheckResult {
    /** 중복 아님 - 정상 저장 */
    object NoDuplicate : DuplicateCheckResult()

    /** 중복 감지 - 사용자 확인 필요 */
    data class DuplicateDetected(val pendingDuplicate: PendingDuplicate) : DuplicateCheckResult()

    /** 규칙에 따라 둘 다 유지 */
    object KeepBoth : DuplicateCheckResult()

    /** 규칙에 따라 두번째 거래 건너뜀 */
    object SkipSecond : DuplicateCheckResult()

    /** 규칙에 따라 두번째만 유지 (첫번째 삭제됨) */
    data class KeepSecond(val deletedTransactionId: String) : DuplicateCheckResult()

    /** 규칙에 따라 둘 다 삭제 */
    data class DeleteBoth(val deletedTransactionId: String) : DuplicateCheckResult()
}
