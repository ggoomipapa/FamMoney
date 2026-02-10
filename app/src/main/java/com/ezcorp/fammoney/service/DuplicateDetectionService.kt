package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.DuplicateTransactionInfo
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.repository.DuplicateRepository
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.Timestamp
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
    // 중복 감지 시간 창 (분) - 3분 이내 같은 금액의 거래를 중복으로 간주
    private val DUPLICATE_TIME_WINDOW_MINUTES = 3

    // 카드 알림 키워드
    private val cardKeywords = listOf("승인", "일시불", "할부", "체크", "신용", "카드")
    // 은행 알림 키워드
    private val bankKeywords = listOf("출금", "이체", "입금", "계좌")

    companion object {
        private const val TAG = "DuplicateDetection"
    }

    /**
     * 알림 텍스트가 카드 알림인지 판단
     */
    private fun isCardNotification(originalText: String): Boolean {
        val cardScore = cardKeywords.count { originalText.contains(it) }
        val bankScore = bankKeywords.count { originalText.contains(it) }
        val isCard = cardScore > bankScore
        AppLogger.d(TAG, "알림 유형 판별 - cardScore: $cardScore, bankScore: $bankScore, isCard: $isCard, text: ${originalText.take(50)}")
        return isCard
    }

    /**
     * 새 거래가 중복인지 확인하고 처리
     * Firestore에서 직접 조회하여 중복 확인 (앱 재시작에도 안정적)
     */
    suspend fun checkAndHandleDuplicate(
        transaction: Transaction
    ): DuplicateCheckResult {
        AppLogger.d(TAG, "중복 검사 시작 - transactionId: ${transaction.id}, groupId: ${transaction.groupId}, userId: ${transaction.userId}, amount: ${transaction.amount}, timeWindow: ${DUPLICATE_TIME_WINDOW_MINUTES}분")

        // Firestore에서 최근 같은 금액의 거래 조회
        val recentDuplicates = transactionRepository.findRecentDuplicateCandidates(
            groupId = transaction.groupId,
            userId = transaction.userId,
            amount = transaction.amount,
            withinMinutes = DUPLICATE_TIME_WINDOW_MINUTES,
            excludeTransactionId = transaction.id
        )

        AppLogger.d(TAG, "중복 후보 조회 결과 - 발견: ${recentDuplicates.size}건")

        if (recentDuplicates.isEmpty()) {
            // 중복 없음
            AppLogger.d(TAG, "중복 없음 - 정상 저장 진행")
            return DuplicateCheckResult.NoDuplicate
        }

        // 가장 최근 거래를 중복 후보로 사용
        val existingTransaction = recentDuplicates.first()

        AppLogger.d("DuplicateDetection", "중복 후보 발견: ${existingTransaction.id} vs ${transaction.id}, 금액: ${transaction.amount}")

        // 사용자 설정 확인 (카드 우선 / 은행 우선 / 매번 물어보기)
        val preference = userPreferences.getDuplicatePreference()
        AppLogger.d(TAG, "사용자 중복 설정 - preference: $preference")

        if (preference != UserPreferences.DUPLICATE_PREF_ASK) {
            // 자동 처리
            val existingIsCard = isCardNotification(existingTransaction.originalText)
            val newIsCard = isCardNotification(transaction.originalText)

            AppLogger.d(TAG, "자동 처리 판별 - existingIsCard: $existingIsCard, newIsCard: $newIsCard")

            // 같은 유형이면 첫 번째 유지
            if (existingIsCard == newIsCard) {
                AppLogger.d(TAG, "같은 유형 중복 → SkipSecond (첫 번째 유지)")
                return DuplicateCheckResult.SkipSecond
            }

            val keepCard = preference == UserPreferences.DUPLICATE_PREF_CARD

            return if (keepCard) {
                // 카드 알림 우선
                if (existingIsCard) {
                    // 기존이 카드 -> 새 거래(은행) 스킵
                    AppLogger.d(TAG, "카드 우선 모드 - 기존이 카드 → SkipSecond (새 거래 은행 스킵)")
                    DuplicateCheckResult.SkipSecond
                } else {
                    // 새 거래가 카드 -> 기존(은행) 삭제
                    AppLogger.d(TAG, "카드 우선 모드 - 새 거래가 카드 → KeepSecond (기존 은행 ${existingTransaction.id} 삭제)")
                    transactionRepository.deleteTransaction(existingTransaction.id)
                    DuplicateCheckResult.KeepSecond(existingTransaction.id)
                }
            } else {
                // 은행 알림 우선
                if (!existingIsCard) {
                    // 기존이 은행 -> 새 거래(카드) 스킵
                    AppLogger.d(TAG, "은행 우선 모드 - 기존이 은행 → SkipSecond (새 거래 카드 스킵)")
                    DuplicateCheckResult.SkipSecond
                } else {
                    // 새 거래가 은행 -> 기존(카드) 삭제
                    AppLogger.d(TAG, "은행 우선 모드 - 새 거래가 은행 → KeepSecond (기존 카드 ${existingTransaction.id} 삭제)")
                    transactionRepository.deleteTransaction(existingTransaction.id)
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

        AppLogger.d(TAG, "규칙 조회 - groupId: $groupId, bank1Id: ${existingTransaction.bankId}, bank2Id: ${transaction.bankId}, rule: ${rule?.resolution}")

        if (rule != null) {
            // 기존 규칙에 따라 자동 처리
            AppLogger.d(TAG, "기존 규칙 적용 - resolution: ${rule.resolution}")
            return when (rule.resolution) {
                DuplicateResolution.KEEP_BOTH -> {
                    AppLogger.d(TAG, "규칙 결과 → KeepBoth (둘 다 유지)")
                    DuplicateCheckResult.KeepBoth
                }
                DuplicateResolution.KEEP_FIRST -> {
                    AppLogger.d(TAG, "규칙 결과 → SkipSecond (첫 번째 유지, 두 번째 스킵)")
                    DuplicateCheckResult.SkipSecond
                }
                DuplicateResolution.KEEP_SECOND -> {
                    AppLogger.d(TAG, "규칙 결과 → KeepSecond (두 번째 유지, 기존 ${existingTransaction.id} 삭제)")
                    transactionRepository.deleteTransaction(existingTransaction.id)
                    DuplicateCheckResult.KeepSecond(existingTransaction.id)
                }
                DuplicateResolution.DELETE_BOTH -> {
                    AppLogger.d(TAG, "규칙 결과 → DeleteBoth (둘 다 삭제, 기존 ${existingTransaction.id} 삭제)")
                    transactionRepository.deleteTransaction(existingTransaction.id)
                    DuplicateCheckResult.DeleteBoth(existingTransaction.id)
                }
                DuplicateResolution.PENDING -> {
                    AppLogger.d(TAG, "규칙 결과 → Pending (사용자 확인 대기)")
                    createPendingDuplicate(existingTransaction, transaction)
                }
            }
        } else {
            // 규칙이 없으면 사용자에게 물어봄
            AppLogger.d(TAG, "규칙 없음 → 사용자에게 중복 확인 요청")
            return createPendingDuplicate(existingTransaction, transaction)
        }
    }

    private suspend fun createPendingDuplicate(
        existingTransaction: Transaction,
        newTransaction: Transaction
    ): DuplicateCheckResult {
        AppLogger.d(TAG, "PendingDuplicate 생성 - existing: ${existingTransaction.id} (${existingTransaction.bankName}), new: ${newTransaction.id} (${newTransaction.bankName}), amount: ${existingTransaction.amount}")

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

        AppLogger.i(TAG, "PendingDuplicate 저장 완료 - groupId: ${pendingDuplicate.groupId}, userId: ${pendingDuplicate.userId}")

        return DuplicateCheckResult.DuplicateDetected(pendingDuplicate)
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
        AppLogger.i(TAG, "중복 해결 시작 - duplicateId: $duplicateId, resolution: $resolution, applyToFuture: $applyToFuture")
        AppLogger.d(TAG, "중복 해결 상세 - transaction1: $transaction1Id, transaction2: $transaction2Id, bank1: $bank1Id, bank2: $bank2Id, groupId: $groupId")

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
        AppLogger.d(TAG, "중복 기록 해결 완료 - duplicateId: $duplicateId")

        // "앞으로도 같이 적용" 선택 시 규칙 저장
        if (applyToFuture && resolution != DuplicateResolution.PENDING) {
            val rule = com.ezcorp.fammoney.data.model.DuplicateRule(
                groupId = groupId,
                bank1Id = bank1Id,
                bank2Id = bank2Id,
                resolution = resolution
            )
            duplicateRepository.addDuplicateRule(rule)
            AppLogger.i(TAG, "중복 규칙 저장 - bank1: $bank1Id, bank2: $bank2Id, resolution: $resolution")
        }
    }
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
