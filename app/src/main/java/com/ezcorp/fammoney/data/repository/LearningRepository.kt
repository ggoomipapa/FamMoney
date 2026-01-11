package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.LearnedMapping
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 학습 데이터 Repository
 * 사용자가 수정한 사용처-카테고리 매핑을 저장/조회
 */
@Singleton
class LearningRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val learnedMappingsCollection = firestore.collection("learned_mappings")

    /**
     * 학습된 매핑 저장 또는 업데이트
     */
    suspend fun saveOrUpdateMapping(
        groupId: String,
        merchantName: String,
        category: String,
        transactionType: String
    ): Result<Unit> {
        return try {
            val normalizedName = LearnedMapping.normalizeMerchantName(merchantName)
            if (normalizedName.isBlank()) return Result.success(Unit)

            // 기존 매핑 검색
            val existing = findMapping(groupId, normalizedName)

            if (existing != null) {
                // 기존 매핑 업데이트 (사용 횟수 증가)
                learnedMappingsCollection.document(existing.id)
                    .update(
                        mapOf(
                            "category" to category,
                            "transactionType" to transactionType,
                            "useCount" to existing.useCount + 1,
                            "lastUsedAt" to Timestamp.now()
                        )
                    )
                    .await()
            } else {
                // 새 매핑 생성
                val newMapping = LearnedMapping(
                    groupId = groupId,
                    merchantName = normalizedName,
                    originalMerchantName = merchantName,
                    category = category,
                    transactionType = transactionType,
                    useCount = 1,
                    lastUsedAt = Timestamp.now(),
                    createdAt = Timestamp.now()
                )
                learnedMappingsCollection.add(newMapping.toMap()).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 가맹점명으로 학습된 매핑 조회
     */
    suspend fun findMapping(groupId: String, merchantName: String): LearnedMapping? {
        return try {
            val normalizedName = LearnedMapping.normalizeMerchantName(merchantName)
            if (normalizedName.isBlank()) return null

            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("merchantName", normalizedName)
                .limit(1)
                .get()
                .await()

            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { LearnedMapping.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 부분 일치 검색 (가맹점명 포함 여부)
     */
    suspend fun findMappingByPartialMatch(groupId: String, merchantName: String): LearnedMapping? {
        return try {
            val normalizedName = LearnedMapping.normalizeMerchantName(merchantName)
            if (normalizedName.length < 2) return null

            // 모든 매핑 조회 후 부분 일치 검색
            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val mappings = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { LearnedMapping.fromMap(doc.id, it) }
            }

            // 정확히 일치하는 것 우선, 그 다음 부분 일치
            mappings.find { it.merchantName == normalizedName }
                ?: mappings.find { normalizedName.contains(it.merchantName) || it.merchantName.contains(normalizedName) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 그룹의 모든 학습 데이터 조회
     */
    suspend fun getAllMappings(groupId: String): List<LearnedMapping> {
        return try {
            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { LearnedMapping.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 학습 데이터 삭제
     */
    suspend fun deleteMapping(mappingId: String): Result<Unit> {
        return try {
            learnedMappingsCollection.document(mappingId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 그룹의 모든 학습 데이터 삭제
     */
    suspend fun deleteAllMappings(groupId: String): Result<Unit> {
        return try {
            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
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
}
