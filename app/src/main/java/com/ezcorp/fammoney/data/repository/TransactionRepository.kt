package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.DeletedTransaction
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TransactionRepo"

@Singleton
class TransactionRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val transactionsCollection = firestore.collection("transactions")
    private val deletedTransactionsCollection = firestore.collection("deletedTransactions")

    suspend fun addTransaction(transaction: Transaction): Result<String> {
        AppLogger.apiStart(TAG, "addTransaction", "amount=${transaction.amount}, type=${transaction.type}")
        return try {
            val docRef = transactionsCollection.add(transaction.toMap()).await()
            AppLogger.apiSuccess(TAG, "addTransaction", "id=${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "addTransaction", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    /**
     * 거래를 추가하고 ID가 포함된 거래 객체를 반환
     */
    suspend fun addTransactionAndReturn(transaction: Transaction): Transaction {
        AppLogger.apiStart(TAG, "addTransactionAndReturn", "amount=${transaction.amount}, bank=${transaction.bankName}")
        val docRef = transactionsCollection.add(transaction.toMap()).await()
        AppLogger.apiSuccess(TAG, "addTransactionAndReturn", "id=${docRef.id}")
        return transaction.copy(id = docRef.id)
    }

    suspend fun updateTransaction(transaction: Transaction): Result<Unit> {
        AppLogger.apiStart(TAG, "updateTransaction", "id=${transaction.id}")
        return try {
            transactionsCollection.document(transaction.id)
                .set(transaction.toMap())
                .await()
            AppLogger.apiSuccess(TAG, "updateTransaction", "id=${transaction.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "updateTransaction", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(transactionId: String): Result<Unit> {
        AppLogger.apiStart(TAG, "deleteTransaction", "id=$transactionId")
        return try {
            transactionsCollection.document(transactionId).delete().await()
            AppLogger.apiSuccess(TAG, "deleteTransaction", "id=$transactionId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "deleteTransaction", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    suspend fun getTransactionById(transactionId: String): Transaction? {
        AppLogger.apiStart(TAG, "getTransactionById", "id=$transactionId")
        return try {
            val doc = transactionsCollection.document(transactionId).get().await()
            val transaction = doc.data?.let { Transaction.fromMap(doc.id, it) }
            if (transaction != null) {
                AppLogger.apiSuccess(TAG, "getTransactionById", "found")
            } else {
                AppLogger.d(TAG, "getTransactionById: not found")
            }
            transaction
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "getTransactionById", e.message ?: "Unknown error", e)
            null
        }
    }

    fun getTransactionsByGroup(groupId: String): Flow<List<Transaction>> = callbackFlow {
        AppLogger.d(TAG, "getTransactionsByGroup: 리스너 시작 groupId=$groupId")
        val listener = transactionsCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("transactionDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getTransactionsByGroup 에러: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Transaction.fromMap(doc.id, it) }
                } ?: emptyList()

                AppLogger.d(TAG, "getTransactionsByGroup: ${transactions.size}건 수신")
                trySend(transactions)
            }

        awaitClose {
            AppLogger.d(TAG, "getTransactionsByGroup: 리스너 해제 groupId=$groupId")
            listener.remove()
        }
    }

    fun getTransactionsByUser(groupId: String, userId: String): Flow<List<Transaction>> = callbackFlow {
        AppLogger.d(TAG, "getTransactionsByUser: 리스너 시작 userId=$userId")
        val listener = transactionsCollection
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("userId", userId)
            .orderBy("transactionDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getTransactionsByUser 에러: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Transaction.fromMap(doc.id, it) }
                } ?: emptyList()

                AppLogger.d(TAG, "getTransactionsByUser: ${transactions.size}건 수신")
                trySend(transactions)
            }

        awaitClose {
            listener.remove()
        }
    }

    fun getTransactionsByMonth(groupId: String, year: Int, month: Int): Flow<List<Transaction>> = callbackFlow {
        AppLogger.d(TAG, "getTransactionsByMonth: 리스너 시작 ${year}년 ${month}월")
        val calendar = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endTime = calendar.timeInMillis

        val listener = transactionsCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getTransactionsByMonth 에러: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val allTransactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Transaction.fromMap(doc.id, it) }
                } ?: emptyList()

                val filtered = allTransactions.filter { tx ->
                    tx.transactionDate?.let { timestamp ->
                        val txTime = timestamp.toDate().time
                        txTime >= startTime && txTime < endTime
                    } ?: false
                }.sortedByDescending { it.transactionDate?.toDate()?.time ?: 0 }

                AppLogger.d(TAG, "getTransactionsByMonth: 전체 ${allTransactions.size}건 중 ${filtered.size}건 필터됨")
                trySend(filtered)
            }

        awaitClose { listener.remove() }
    }

    suspend fun confirmTransaction(transactionId: String): Result<Unit> {
        AppLogger.apiStart(TAG, "confirmTransaction", "id=$transactionId")
        return try {
            transactionsCollection.document(transactionId)
                .update("isConfirmed", true)
                .await()
            AppLogger.apiSuccess(TAG, "confirmTransaction")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "confirmTransaction", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    suspend fun getTransactionsByYear(groupId: String, year: Int): List<Transaction> {
        AppLogger.apiStart(TAG, "getTransactionsByYear", "year=$year")
        return try {
            val calendar = Calendar.getInstance().apply {
                set(year, 0, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis

            calendar.set(year + 1, 0, 1, 0, 0, 0)
            val endTime = calendar.timeInMillis

            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Transaction.fromMap(doc.id, it) }
            }

            val filtered = transactions.filter { tx ->
                tx.transactionDate?.let { timestamp ->
                    val txTime = timestamp.toDate().time
                    txTime >= startTime && txTime < endTime
                } ?: false
            }

            AppLogger.apiSuccess(TAG, "getTransactionsByYear", "전체 ${transactions.size}건 중 ${filtered.size}건")
            filtered
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "getTransactionsByYear", e.message ?: "Unknown error", e)
            emptyList()
        }
    }

    suspend fun getTransactionsByMonthForStats(groupId: String, year: Int, month: Int): List<Transaction> {
        AppLogger.apiStart(TAG, "getTransactionsByMonthForStats", "${year}년 ${month}월")
        return try {
            val calendar = Calendar.getInstance().apply {
                set(year, month - 1, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis

            calendar.add(Calendar.MONTH, 1)
            val endTime = calendar.timeInMillis

            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Transaction.fromMap(doc.id, it) }
            }

            val filtered = transactions.filter { tx ->
                tx.transactionDate?.let { timestamp ->
                    val txTime = timestamp.toDate().time
                    txTime >= startTime && txTime < endTime
                } ?: false
            }.sortedByDescending { it.transactionDate?.toDate()?.time ?: 0 }

            AppLogger.apiSuccess(TAG, "getTransactionsByMonthForStats", "전체 ${transactions.size}건 중 ${filtered.size}건")
            filtered
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "getTransactionsByMonthForStats", e.message ?: "Unknown error", e)
            emptyList()
        }
    }

    suspend fun getUniqueMerchantNames(groupId: String): List<String> {
        AppLogger.apiStart(TAG, "getUniqueMerchantNames")
        return try {
            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val merchants = snapshot.documents
                .mapNotNull { doc ->
                    doc.data?.get("merchantName") as? String
                }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            AppLogger.apiSuccess(TAG, "getUniqueMerchantNames", "${merchants.size}개")
            merchants
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "getUniqueMerchantNames", e.message ?: "Unknown error", e)
            emptyList()
        }
    }

    /**
     * 같은 사용처를 가진 거래들의 사용처를 일괄 업데이트
     */
    suspend fun updateMerchantNameBatch(
        groupId: String,
        oldMerchantName: String,
        newMerchantName: String,
        excludeTransactionId: String? = null
    ): Int {
        AppLogger.apiStart(TAG, "updateMerchantNameBatch", "$oldMerchantName → $newMerchantName")
        return try {
            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("merchantName", oldMerchantName)
                .get()
                .await()

            val batch = firestore.batch()
            var count = 0

            snapshot.documents.forEach { doc ->
                if (doc.id != excludeTransactionId) {
                    batch.update(doc.reference, "merchantName", newMerchantName)
                    count++
                }
            }

            if (count > 0) {
                batch.commit().await()
            }
            AppLogger.apiSuccess(TAG, "updateMerchantNameBatch", "${count}건 업데이트")
            count
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "updateMerchantNameBatch", e.message ?: "Unknown error", e)
            0
        }
    }

    /**
     * 중복 거래 확인을 위해 최근 거래 조회
     */
    suspend fun findRecentDuplicateCandidates(
        groupId: String,
        userId: String,
        amount: Long,
        withinMinutes: Int = 3,
        excludeTransactionId: String? = null
    ): List<Transaction> {
        AppLogger.apiStart(TAG, "findRecentDuplicateCandidates", "amount=$amount, within=${withinMinutes}분")
        return try {
            val now = System.currentTimeMillis()
            val windowStart = now - (withinMinutes * 60 * 1000L)
            val startTimestamp = Timestamp(java.util.Date(windowStart))

            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("userId", userId)
                .whereEqualTo("amount", amount)
                .get()
                .await()

            val candidates = snapshot.documents
                .mapNotNull { doc -> doc.data?.let { Transaction.fromMap(doc.id, it) } }
                .filter { tx ->
                    tx.id != excludeTransactionId &&
                    tx.transactionDate != null &&
                    tx.transactionDate >= startTimestamp
                }
                .sortedByDescending { it.transactionDate?.toDate()?.time ?: 0 }

            AppLogger.apiSuccess(TAG, "findRecentDuplicateCandidates", "${candidates.size}건 발견")
            candidates
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "findRecentDuplicateCandidates", e.message ?: "Unknown error", e)
            emptyList()
        }
    }

    // ==================== 휴지통 기능 ====================

    /**
     * 거래를 휴지통으로 이동 (30일 후 자동 삭제)
     */
    suspend fun moveToTrash(transaction: Transaction): Result<Unit> {
        AppLogger.apiStart(TAG, "moveToTrash", "id=${transaction.id}")
        return try {
            val now = System.currentTimeMillis()
            val expiresAt = Timestamp(Date(now + DeletedTransaction.RETENTION_MS))

            val deletedTransaction = DeletedTransaction(
                originalTransaction = transaction.toMap(),
                groupId = transaction.groupId,
                userId = transaction.userId,
                deletedAt = Timestamp.now(),
                expiresAt = expiresAt
            )

            deletedTransactionsCollection.add(deletedTransaction.toMap()).await()
            transactionsCollection.document(transaction.id).delete().await()

            AppLogger.apiSuccess(TAG, "moveToTrash", "id=${transaction.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "moveToTrash", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    /**
     * 여러 거래를 휴지통으로 이동
     */
    suspend fun moveMultipleToTrash(transactions: List<Transaction>): Result<Int> {
        AppLogger.apiStart(TAG, "moveMultipleToTrash", "${transactions.size}건")
        return try {
            val now = System.currentTimeMillis()
            val expiresAt = Timestamp(Date(now + DeletedTransaction.RETENTION_MS))
            var count = 0

            val batch = firestore.batch()

            transactions.forEach { transaction ->
                val deletedTransaction = DeletedTransaction(
                    originalTransaction = transaction.toMap(),
                    groupId = transaction.groupId,
                    userId = transaction.userId,
                    deletedAt = Timestamp.now(),
                    expiresAt = expiresAt
                )

                val newDocRef = deletedTransactionsCollection.document()
                batch.set(newDocRef, deletedTransaction.toMap())
                batch.delete(transactionsCollection.document(transaction.id))
                count++
            }

            batch.commit().await()
            AppLogger.apiSuccess(TAG, "moveMultipleToTrash", "${count}건 이동")
            Result.success(count)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "moveMultipleToTrash", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    /**
     * 휴지통 목록 조회
     */
    fun getDeletedTransactions(groupId: String): Flow<List<DeletedTransaction>> = callbackFlow {
        AppLogger.d(TAG, "getDeletedTransactions: 리스너 시작")
        val listener = deletedTransactionsCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("deletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getDeletedTransactions 에러: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val deletedTransactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { DeletedTransaction.fromMap(doc.id, it) }
                } ?: emptyList()

                AppLogger.d(TAG, "getDeletedTransactions: ${deletedTransactions.size}건")
                trySend(deletedTransactions)
            }

        awaitClose { listener.remove() }
    }

    /**
     * 휴지통에서 복원
     */
    suspend fun restoreFromTrash(deletedTransaction: DeletedTransaction): Result<Unit> {
        AppLogger.apiStart(TAG, "restoreFromTrash", "id=${deletedTransaction.id}")
        return try {
            val originalData = deletedTransaction.originalTransaction.toMutableMap()
            transactionsCollection.add(originalData).await()
            deletedTransactionsCollection.document(deletedTransaction.id).delete().await()

            AppLogger.apiSuccess(TAG, "restoreFromTrash")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "restoreFromTrash", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    /**
     * 휴지통에서 영구 삭제
     */
    suspend fun permanentlyDelete(deletedTransactionId: String): Result<Unit> {
        AppLogger.apiStart(TAG, "permanentlyDelete", "id=$deletedTransactionId")
        return try {
            deletedTransactionsCollection.document(deletedTransactionId).delete().await()
            AppLogger.apiSuccess(TAG, "permanentlyDelete")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "permanentlyDelete", e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    /**
     * 휴지통 비우기 (만료된 항목 삭제)
     */
    suspend fun cleanupExpiredTrash(): Int {
        AppLogger.apiStart(TAG, "cleanupExpiredTrash")
        return try {
            val now = Timestamp.now()
            val snapshot = deletedTransactionsCollection
                .whereLessThan("expiresAt", now)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            if (snapshot.documents.isNotEmpty()) {
                batch.commit().await()
            }

            AppLogger.apiSuccess(TAG, "cleanupExpiredTrash", "${snapshot.documents.size}건 삭제")
            snapshot.documents.size
        } catch (e: Exception) {
            AppLogger.apiError(TAG, "cleanupExpiredTrash", e.message ?: "Unknown error", e)
            0
        }
    }
}
