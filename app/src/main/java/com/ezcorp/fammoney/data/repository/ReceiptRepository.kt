package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.PriceHistory
import com.ezcorp.fammoney.data.model.ReceiptItem
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReceiptRepo"

@Singleton
class ReceiptRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val receiptItemsCollection = firestore.collection("receipt_items")
    private val priceHistoryCollection = firestore.collection("price_history")

    // ===== Receipt Items =====

    suspend fun addReceiptItems(items: List<ReceiptItem>): Result<Unit> {
        AppLogger.d(TAG, "addReceiptItems: adding ${items.size} items")
        return try {
            val batch = firestore.batch()
            items.forEach { item ->
                val docRef = receiptItemsCollection.document()
                batch.set(docRef, item.toMap())
            }
            batch.commit().await()
            AppLogger.i(TAG, "addReceiptItems: success - added ${items.size} items")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "addReceiptItems: failed to add ${items.size} items", e)
            Result.failure(e)
        }
    }

    suspend fun getReceiptItemsByTransaction(transactionId: String): List<ReceiptItem> {
        AppLogger.d(TAG, "getReceiptItemsByTransaction: transactionId=$transactionId")
        return try {
            val snapshot = receiptItemsCollection
                .whereEqualTo("transactionId", transactionId)
                .get()
                .await()

            val result = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { ReceiptItem.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "getReceiptItemsByTransaction: found ${result.size} items")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "getReceiptItemsByTransaction: failed for transactionId=$transactionId", e)
            emptyList()
        }
    }

    suspend fun deleteReceiptItemsByTransaction(transactionId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteReceiptItemsByTransaction: transactionId=$transactionId")
        return try {
            val snapshot = receiptItemsCollection
                .whereEqualTo("transactionId", transactionId)
                .get()
                .await()

            val count = snapshot.documents.size
            AppLogger.d(TAG, "deleteReceiptItemsByTransaction: deleting $count items")

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            AppLogger.i(TAG, "deleteReceiptItemsByTransaction: success - deleted $count items")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteReceiptItemsByTransaction: failed for transactionId=$transactionId", e)
            Result.failure(e)
        }
    }

    // ===== Price History =====

    suspend fun addPriceHistory(history: PriceHistory): Result<String> {
        AppLogger.d(TAG, "addPriceHistory: itemName=${history.itemName}")
        return try {
            val docRef = priceHistoryCollection.add(history.toMap()).await()
            AppLogger.i(TAG, "addPriceHistory: success - id=${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "addPriceHistory: failed for item=${history.itemName}", e)
            Result.failure(e)
        }
    }

    suspend fun addPriceHistoryBatch(histories: List<PriceHistory>): Result<Unit> {
        AppLogger.d(TAG, "addPriceHistoryBatch: adding ${histories.size} histories")
        return try {
            val batch = firestore.batch()
            histories.forEach { history ->
                val docRef = priceHistoryCollection.document()
                batch.set(docRef, history.toMap())
            }
            batch.commit().await()
            AppLogger.i(TAG, "addPriceHistoryBatch: success - added ${histories.size} histories")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "addPriceHistoryBatch: failed to add ${histories.size} histories", e)
            Result.failure(e)
        }
    }

    suspend fun searchPriceHistory(groupId: String, searchQuery: String): List<PriceHistory> {
        AppLogger.d(TAG, "searchPriceHistory: groupId=$groupId, query='$searchQuery'")
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
            AppLogger.d(TAG, "searchPriceHistory: fetched ${allItems.size} total items from Firestore")

            // 검색어 필터링
            val result = if (searchQuery.isBlank()) {
                allItems
            } else {
                allItems.filter { it.itemName.contains(searchQuery, ignoreCase = true) }
            }
            AppLogger.d(TAG, "searchPriceHistory: returning ${result.size} items after filtering")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "searchPriceHistory: failed for groupId=$groupId, query='$searchQuery'", e)
            emptyList()
        }
    }

    suspend fun getPriceHistoryForItem(groupId: String, itemName: String): List<PriceHistory> {
        AppLogger.d(TAG, "getPriceHistoryForItem: groupId=$groupId, itemName='$itemName'")
        return try {
            val snapshot = priceHistoryCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val result = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { PriceHistory.fromMap(doc.id, it) }
            }.filter {
                it.itemName.equals(itemName, ignoreCase = true) ||
                it.itemName.contains(itemName, ignoreCase = true)
            }.sortedByDescending { it.purchaseDate.toDate().time }
            AppLogger.d(TAG, "getPriceHistoryForItem: found ${result.size} history entries for '$itemName'")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "getPriceHistoryForItem: failed for item='$itemName'", e)
            emptyList()
        }
    }

    suspend fun getUniqueItemNames(groupId: String): List<String> {
        AppLogger.d(TAG, "getUniqueItemNames: groupId=$groupId")
        return try {
            val snapshot = priceHistoryCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val names = snapshot.documents.mapNotNull { doc ->
                doc.data?.get("itemName") as? String
            }.distinct().sorted()
            AppLogger.d(TAG, "getUniqueItemNames: found ${names.size} unique item names")
            names
        } catch (e: Exception) {
            AppLogger.e(TAG, "getUniqueItemNames: failed for groupId=$groupId", e)
            emptyList()
        }
    }
}
