package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.Transaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val transactionsCollection = firestore.collection("transactions")

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
}
