package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.DeletedTransaction
import com.ezcorp.fammoney.data.model.Transaction
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

@Singleton
class TransactionRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val transactionsCollection = firestore.collection("transactions")
    private val deletedTransactionsCollection = firestore.collection("deletedTransactions")

    suspend fun addTransaction(transaction: Transaction): Result<String> {
        return try {
            val docRef = transactionsCollection.add(transaction.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 거래�?추�??�고 ID가 ?�함??거래 객체�?반환
     */
    suspend fun addTransactionAndReturn(transaction: Transaction): Transaction {
        val docRef = transactionsCollection.add(transaction.toMap()).await()
        return transaction.copy(id = docRef.id)
    }

    suspend fun updateTransaction(transaction: Transaction): Result<Unit> {
        return try {
            transactionsCollection.document(transaction.id)
                .set(transaction.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(transactionId: String): Result<Unit> {
        return try {
            transactionsCollection.document(transactionId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransactionById(transactionId: String): Transaction? {
        return try {
            val doc = transactionsCollection.document(transactionId).get().await()
            doc.data?.let { Transaction.fromMap(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    fun getTransactionsByGroup(groupId: String): Flow<List<Transaction>> = callbackFlow {
        val listener = transactionsCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("transactionDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // ?�덱??빌드 중이거나 ?�러 ??�?목록 반환 (?�래??방�")
                trySend(emptyList())
                    return@addSnapshotListener
                }

                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Transaction.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(transactions)
            }

        awaitClose { listener.remove() }
    }

    fun getTransactionsByUser(groupId: String, userId: String): Flow<List<Transaction>> = callbackFlow {
        val listener = transactionsCollection
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("userId", userId)
            .orderBy("transactionDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // ?�덱??빌드 중이거나 ?�러 ??�?목록 반환 (?�래??방�")
                trySend(emptyList())
                    return@addSnapshotListener
                }

                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Transaction.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(transactions)
            }

        awaitClose { listener.remove() }
    }

    fun getTransactionsByMonth(groupId: String, year: Int, month: Int): Flow<List<Transaction>> = callbackFlow {
        val calendar = Calendar.getInstance().apply {
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endTime = calendar.timeInMillis

        // 복합 인덱스 없이 동작하도록 groupId만으로 쿼리 후 메모리에서 날짜 필터링
        val listener = transactionsCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("TransactionRepository", "getTransactionsByMonth error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val allTransactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Transaction.fromMap(doc.id, it) }
                } ?: emptyList()

                // 메모리에서 날짜 필터링 및 정렬
                val filtered = allTransactions.filter { tx ->
                    tx.transactionDate?.let { timestamp ->
                        val txTime = timestamp.toDate().time
                        txTime >= startTime && txTime < endTime
                    } ?: false
                }.sortedByDescending { it.transactionDate?.toDate()?.time ?: 0 }

                trySend(filtered)
            }

        awaitClose { listener.remove() }
    }

    suspend fun confirmTransaction(transactionId: String): Result<Unit> {
        return try {
            transactionsCollection.document(transactionId)
                .update("isConfirmed", true)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTransactionsByYear(groupId: String, year: Int): List<Transaction> {
        return try {
            val calendar = Calendar.getInstance().apply {
                set(year, 0, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis

            calendar.set(year + 1, 0, 1, 0, 0, 0)
            val endTime = calendar.timeInMillis

            // groupId만으�?쿼리?�고 ?�짜??메모리에???�터�?(복합 ?�덱??불필?"
            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Transaction.fromMap(doc.id, it) }
            }

            // 메모리에서 날짜 필터링
            transactions.filter { tx ->
                tx.transactionDate?.let { timestamp ->
                    val txTime = timestamp.toDate().time
                    txTime >= startTime && txTime < endTime
                } ?: false
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTransactionsByMonthForStats(groupId: String, year: Int, month: Int): List<Transaction> {
        return try {
            val calendar = Calendar.getInstance().apply {
                set(year, month - 1, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTime = calendar.timeInMillis

            calendar.add(Calendar.MONTH, 1)
            val endTime = calendar.timeInMillis

            android.util.Log.d("TransactionRepository", "getTransactionsByMonthForStats: groupId=$groupId, year=$year, month=$month")

            // groupId만으�?쿼리?�고 ?�짜??메모리에???�터�?(복합 ?�덱??불필?"
            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            android.util.Log.d("TransactionRepository", "getTransactionsByMonthForStats: found ${snapshot.documents.size} total documents for group")

            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Transaction.fromMap(doc.id, it) }
            }

            // 메모리에???�짜 ?�터�?�??�렬
            val filtered = transactions.filter { tx ->
                tx.transactionDate?.let { timestamp ->
                    val txTime = timestamp.toDate().time
                    txTime >= startTime && txTime < endTime
                } ?: false
            }

            android.util.Log.d("TransactionRepository", "getTransactionsByMonthForStats: ${filtered.size} transactions in selected month")

            filtered.sortedByDescending { it.transactionDate?.toDate()?.time ?: 0 }
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "getTransactionsByMonthForStats error", e)
            emptyList()
        }
    }

    suspend fun getUniqueMerchantNames(groupId: String): List<String> {
        return try {
            val snapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { doc ->
                    doc.data?.get("merchantName") as? String
                }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 같은 사용처를 가진 거래들의 사용처를 일괄 업데이트
     * @param groupId 그룹 ID
     * @param oldMerchantName 기존 사용처 이름
     * @param newMerchantName 새 사용처 이름
     * @param excludeTransactionId 제외할 거래 ID (이미 업데이트된 거래)
     * @return 업데이트된 거래 수
     */
    suspend fun updateMerchantNameBatch(
        groupId: String,
        oldMerchantName: String,
        newMerchantName: String,
        excludeTransactionId: String? = null
    ): Int {
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
            count
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "updateMerchantNameBatch error", e)
            0
        }
    }

    /**
     * 중복 거래 확인을 위해 최근 거래 조회
     * @param groupId 그룹 ID
     * @param userId 사용자 ID
     * @param amount 금액
     * @param withinMinutes 몇 분 이내의 거래를 조회할지
     * @param excludeTransactionId 제외할 거래 ID
     * @return 중복 가능성이 있는 거래 목록
     */
    suspend fun findRecentDuplicateCandidates(
        groupId: String,
        userId: String,
        amount: Long,
        withinMinutes: Int = 3,
        excludeTransactionId: String? = null
    ): List<Transaction> {
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

            snapshot.documents
                .mapNotNull { doc -> doc.data?.let { Transaction.fromMap(doc.id, it) } }
                .filter { tx ->
                    tx.id != excludeTransactionId &&
                    tx.transactionDate != null &&
                    tx.transactionDate >= startTimestamp
                }
                .sortedByDescending { it.transactionDate?.toDate()?.time ?: 0 }
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "findRecentDuplicateCandidates error", e)
            emptyList()
        }
    }

    // ==================== 휴지통 기능 ====================

    /**
     * 거래를 휴지통으로 이동 (30일 후 자동 삭제)
     */
    suspend fun moveToTrash(transaction: Transaction): Result<Unit> {
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

            // 휴지통에 추가
            deletedTransactionsCollection.add(deletedTransaction.toMap()).await()

            // 원본 삭제
            transactionsCollection.document(transaction.id).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "moveToTrash error", e)
            Result.failure(e)
        }
    }

    /**
     * 여러 거래를 휴지통으로 이동
     */
    suspend fun moveMultipleToTrash(transactions: List<Transaction>): Result<Int> {
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

                // 휴지통에 추가
                val newDocRef = deletedTransactionsCollection.document()
                batch.set(newDocRef, deletedTransaction.toMap())

                // 원본 삭제
                batch.delete(transactionsCollection.document(transaction.id))
                count++
            }

            batch.commit().await()
            Result.success(count)
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "moveMultipleToTrash error", e)
            Result.failure(e)
        }
    }

    /**
     * 휴지통 목록 조회
     */
    fun getDeletedTransactions(groupId: String): Flow<List<DeletedTransaction>> = callbackFlow {
        val listener = deletedTransactionsCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("deletedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val deletedTransactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { DeletedTransaction.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(deletedTransactions)
            }

        awaitClose { listener.remove() }
    }

    /**
     * 휴지통에서 복원
     */
    suspend fun restoreFromTrash(deletedTransaction: DeletedTransaction): Result<Unit> {
        return try {
            // 원본 거래 데이터 복원
            val originalData = deletedTransaction.originalTransaction.toMutableMap()

            // 새 문서로 추가 (원본 ID는 사용할 수 없음)
            transactionsCollection.add(originalData).await()

            // 휴지통에서 삭제
            deletedTransactionsCollection.document(deletedTransaction.id).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "restoreFromTrash error", e)
            Result.failure(e)
        }
    }

    /**
     * 휴지통에서 영구 삭제
     */
    suspend fun permanentlyDelete(deletedTransactionId: String): Result<Unit> {
        return try {
            deletedTransactionsCollection.document(deletedTransactionId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "permanentlyDelete error", e)
            Result.failure(e)
        }
    }

    /**
     * 휴지통 비우기 (만료된 항목 삭제)
     */
    suspend fun cleanupExpiredTrash(): Int {
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

            snapshot.documents.size
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "cleanupExpiredTrash error", e)
            0
        }
    }
}
