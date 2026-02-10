package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.TransactionTag
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TagRepo"

/**
 * 거래내역 태그 Repository
 */
@Singleton
class TagRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val tagsCollection = firestore.collection("transaction_tags")

    /**
     * 새 태그 생성
     */
    suspend fun createTag(tag: TransactionTag): Result<String> {
        AppLogger.d(TAG, "createTag: name=${tag.name}, groupId=${tag.groupId}")
        return try {
            val tagWithTimestamp = tag.copy(
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            val docRef = tagsCollection.add(tagWithTimestamp.toMap()).await()
            AppLogger.i(TAG, "createTag: success - id=${docRef.id}, name=${tag.name}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "createTag: failed for name=${tag.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 태그 업데이트
     */
    suspend fun updateTag(tag: TransactionTag): Result<Unit> {
        AppLogger.d(TAG, "updateTag: id=${tag.id}, name=${tag.name}")
        return try {
            val updatedTag = tag.copy(updatedAt = Timestamp.now())
            tagsCollection.document(tag.id)
                .set(updatedTag.toMap())
                .await()
            AppLogger.i(TAG, "updateTag: success - id=${tag.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateTag: failed for id=${tag.id}", e)
            Result.failure(e)
        }
    }

    /**
     * 태그 삭제
     */
    suspend fun deleteTag(tagId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteTag: tagId=$tagId")
        return try {
            tagsCollection.document(tagId).delete().await()
            AppLogger.i(TAG, "deleteTag: success - tagId=$tagId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteTag: failed for tagId=$tagId", e)
            Result.failure(e)
        }
    }

    /**
     * 그룹의 모든 태그 조회 (실시간)
     */
    fun getTagsByGroup(groupId: String): Flow<List<TransactionTag>> = callbackFlow {
        AppLogger.d(TAG, "getTagsByGroup: groupId=$groupId")
        val listener = tagsCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getTagsByGroup: snapshot error for groupId=$groupId", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val tags = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { TransactionTag.fromMap(doc.id, it) }
                }?.sortedByDescending { it.createdAt } ?: emptyList()

                AppLogger.d(TAG, "getTagsByGroup: received ${tags.size} tags")
                trySend(tags)
            }

        awaitClose {
            AppLogger.d(TAG, "getTagsByGroup: listener removed for groupId=$groupId")
            listener.remove()
        }
    }

    /**
     * 활성화된 태그 조회
     */
    suspend fun getActiveTag(groupId: String): TransactionTag? {
        AppLogger.d(TAG, "getActiveTag: groupId=$groupId")
        return try {
            val snapshot = tagsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()

            val tag = snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { TransactionTag.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "getActiveTag: ${if (tag != null) "found active tag id=${tag.id}, name=${tag.name}" else "no active tag"}")
            tag
        } catch (e: Exception) {
            AppLogger.e(TAG, "getActiveTag: failed for groupId=$groupId", e)
            null
        }
    }

    /**
     * 태그 활성화/비활성화
     */
    suspend fun setTagActive(tagId: String, groupId: String, isActive: Boolean): Result<Unit> {
        AppLogger.d(TAG, "setTagActive: tagId=$tagId, groupId=$groupId, isActive=$isActive")
        return try {
            // 먼저 다른 태그들 비활성화
            if (isActive) {
                val activeTags = tagsCollection
                    .whereEqualTo("groupId", groupId)
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

                AppLogger.d(TAG, "setTagActive: deactivating ${activeTags.documents.size} currently active tags")
                activeTags.documents.forEach { doc ->
                    doc.reference.update("isActive", false).await()
                }
            }

            // 해당 태그 활성화/비활성화
            tagsCollection.document(tagId)
                .update("isActive", isActive)
                .await()

            AppLogger.i(TAG, "setTagActive: success - tagId=$tagId, isActive=$isActive")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "setTagActive: failed for tagId=$tagId", e)
            Result.failure(e)
        }
    }

    /**
     * 태그 통계 업데이트
     */
    suspend fun updateTagStats(
        tagId: String,
        transactionCount: Int,
        totalExpense: Long,
        totalIncome: Long
    ): Result<Unit> {
        AppLogger.d(TAG, "updateTagStats: tagId=$tagId, txCount=$transactionCount, expense=$totalExpense, income=$totalIncome")
        return try {
            tagsCollection.document(tagId)
                .update(
                    mapOf(
                        "transactionCount" to transactionCount,
                        "totalExpense" to totalExpense,
                        "totalIncome" to totalIncome,
                        "updatedAt" to Timestamp.now()
                    )
                )
                .await()
            AppLogger.i(TAG, "updateTagStats: success - tagId=$tagId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateTagStats: failed for tagId=$tagId", e)
            Result.failure(e)
        }
    }

    /**
     * 태그 ID로 조회
     */
    suspend fun getTagById(tagId: String): TransactionTag? {
        AppLogger.d(TAG, "getTagById: tagId=$tagId")
        return try {
            val doc = tagsCollection.document(tagId).get().await()
            val tag = doc.data?.let { TransactionTag.fromMap(doc.id, it) }
            AppLogger.d(TAG, "getTagById: ${if (tag != null) "found tag name=${tag.name}" else "tag not found"}")
            tag
        } catch (e: Exception) {
            AppLogger.e(TAG, "getTagById: failed for tagId=$tagId", e)
            null
        }
    }
}
