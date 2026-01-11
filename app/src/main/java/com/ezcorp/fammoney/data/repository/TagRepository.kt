package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.TransactionTag
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

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
        return try {
            val tagWithTimestamp = tag.copy(
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            val docRef = tagsCollection.add(tagWithTimestamp.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 태그 업데이트
     */
    suspend fun updateTag(tag: TransactionTag): Result<Unit> {
        return try {
            val updatedTag = tag.copy(updatedAt = Timestamp.now())
            tagsCollection.document(tag.id)
                .set(updatedTag.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 태그 삭제
     */
    suspend fun deleteTag(tagId: String): Result<Unit> {
        return try {
            tagsCollection.document(tagId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 그룹의 모든 태그 조회 (실시간)
     */
    fun getTagsByGroup(groupId: String): Flow<List<TransactionTag>> = callbackFlow {
        val listener = tagsCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val tags = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { TransactionTag.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(tags)
            }

        awaitClose { listener.remove() }
    }

    /**
     * 활성화된 태그 조회
     */
    suspend fun getActiveTag(groupId: String): TransactionTag? {
        return try {
            val snapshot = tagsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { TransactionTag.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 태그 활성화/비활성화
     */
    suspend fun setTagActive(tagId: String, groupId: String, isActive: Boolean): Result<Unit> {
        return try {
            // 먼저 다른 태그들 비활성화
            if (isActive) {
                val activeTags = tagsCollection
                    .whereEqualTo("groupId", groupId)
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()

                activeTags.documents.forEach { doc ->
                    doc.reference.update("isActive", false).await()
                }
            }

            // 해당 태그 활성화/비활성화
            tagsCollection.document(tagId)
                .update("isActive", isActive)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 태그 ID로 조회
     */
    suspend fun getTagById(tagId: String): TransactionTag? {
        return try {
            val doc = tagsCollection.document(tagId).get().await()
            doc.data?.let { TransactionTag.fromMap(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }
}
