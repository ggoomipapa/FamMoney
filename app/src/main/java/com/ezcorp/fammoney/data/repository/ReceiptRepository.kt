package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.PriceHistory
import com.ezcorp.fammoney.data.model.ReceiptItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val receiptItemsCollection = firestore.collection("receipt_items")
    private val priceHistoryCollection = firestore.collection("price_history")

    // ===== Receipt Items =====

    suspend fun addReceiptItems(items: List<ReceiptItem>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            items.forEach { item ->
                val docRef = receiptItemsCollection.document()
                batch.set(docRef, item.toMap())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReceiptItemsByTransaction(transactionId: String): List<ReceiptItem> {
        return try {
            val snapshot = receiptItemsCollection
                .whereEqualTo("transactionId", transactionId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { ReceiptItem.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteReceiptItemsByTransaction(transactionId: String): Result<Unit> {
        return try {
            val snapshot = receiptItemsCollection
                .whereEqualTo("transactionId", transactionId)
                .get()
                .await()

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== Price History =====

    suspend fun addPriceHistory(history: PriceHistory): Result<String> {
        return try {
            val docRef = priceHistoryCollection.add(history.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPriceHistoryBatch(histories: List<PriceHistory>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            histories.forEach { history ->
                val docRef = priceHistoryCollection.document()
                batch.set(docRef, history.toMap())
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchPriceHistory(groupId: String, searchQuery: String): List<PriceHistory> {
        return try {
            // Firestore doesn't support full-text search, so we fetch all and filter
            val snapshot = priceHistoryCollection
                .whereEqualTo("groupId", groupId)
                .orderBy("purchaseDate", Query.Direction.DESCENDING)
                .get()
                .await()

            val allItems = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { PriceHistory.fromMap(doc.id, it) }
            }

            // 검색어 필터링
            if (searchQuery.isBlank()) {
                allItems
            } else {
                allItems.filter { it.itemName.contains(searchQuery, ignoreCase = true) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPriceHistoryForItem(groupId: String, itemName: String): List<PriceHistory> {
        return try {
            val snapshot = priceHistoryCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { PriceHistory.fromMap(doc.id, it) }
            }.filter {
                it.itemName.equals(itemName, ignoreCase = true) ||
                it.itemName.contains(itemName, ignoreCase = true)
            }.sortedByDescending { it.purchaseDate.toDate().time }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUniqueItemNames(groupId: String): List<String> {
        return try {
            val snapshot = priceHistoryCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.get("itemName") as? String
            }.distinct().sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
