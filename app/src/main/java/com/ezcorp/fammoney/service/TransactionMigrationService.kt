package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 거래내역 마이그레이션 서비스
 * 기존 거래의 수입/지출 유형을 재판정하여 업데이트
 */
@Singleton
class TransactionMigrationService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val transactionRepository: TransactionRepository
) {
    private val transactionsCollection = firestore.collection("transactions")

    /**
     * 마이그레이션 실행 결과
     */
    data class MigrationResult(
        val totalCount: Int,
        val updatedCount: Int,
        val incomeFixedCount: Int,
        val expenseFixedCount: Int,
        val errors: List<String>
    )

    /**
     * 그룹의 모든 거래내역 마이그레이션
     * - description을 다시 파싱하여 수입/지출 유형 재판정
     * - 수정된 거래만 업데이트
     */
    suspend fun migrateTransactions(groupId: String): MigrationResult {
        val errors = mutableListOf<String>()
        var totalCount = 0
        var updatedCount = 0
        var incomeFixedCount = 0
        var expenseFixedCount = 0

        try {
            // 그룹의 모든 거래 조회
            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Transaction.fromMap(doc.id, it) }
            }

            totalCount = transactions.size

            // 각 거래에 대해 재판정
            for (transaction in transactions) {
                try {
                    val result = reParseTransaction(transaction)
                    if (result != null && result.type != transaction.type) {
                        // 유형이 변경된 경우에만 업데이트
                        transactionsCollection.document(transaction.id)
                            .update("type", result.type.name)
                            .await()

                        updatedCount++

                        if (result.type == TransactionType.INCOME) {
                            incomeFixedCount++
                        } else {
                            expenseFixedCount++
                        }
                    }
                } catch (e: Exception) {
                    errors.add("거래 ${transaction.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("마이그레이션 실패: ${e.message}")
        }

        return MigrationResult(
            totalCount = totalCount,
            updatedCount = updatedCount,
            incomeFixedCount = incomeFixedCount,
            expenseFixedCount = expenseFixedCount,
            errors = errors
        )
    }

    /**
     * 거래 내역 재파싱
     */
    private fun reParseTransaction(transaction: Transaction): ParseResult? {
        val text = transaction.description
        if (text.isBlank()) return null

        // 수입 키워드
        val incomeKeywords = listOf(
            "입금", "받으셨", "들어옴", "이체받음", "송금받음", "받았어요",
            "출금취소", "환불", "급여", "월급", "이자", "배당"
        )

        // 지출 키워드
        val expenseKeywords = listOf(
            "출금", "결제", "승인", "이체", "송금", "사용", "지출",
            "체크카드출금", "신용카드출금", "보냈어요", "납부"
        )

        // 키워드 위치 기반 판단
        var incomePos = Int.MAX_VALUE
        var expensePos = Int.MAX_VALUE

        for (keyword in incomeKeywords) {
            val pos = text.indexOf(keyword)
            if (pos >= 0 && pos < incomePos) {
                incomePos = pos
            }
        }

        for (keyword in expenseKeywords) {
            val pos = text.indexOf(keyword)
            if (pos >= 0 && pos < expensePos) {
                expensePos = pos
            }
        }

        // 특별 케이스: "출금취소"는 수입
        if (text.contains("출금취소")) {
            return ParseResult(TransactionType.INCOME)
        }

        // 키워드 위치로 판단 (먼저 나오는 키워드 우선)
        return when {
            incomePos < expensePos -> ParseResult(TransactionType.INCOME)
            expensePos < incomePos -> ParseResult(TransactionType.EXPENSE)
            else -> null // 변경 없음
        }
    }

    private data class ParseResult(val type: TransactionType)
}
