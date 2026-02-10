package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.LearnedMapping
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LearningRepo"

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
        AppLogger.d(TAG, "saveOrUpdateMapping: groupId=$groupId, merchant=$merchantName, category=$category, type=$transactionType")
        return try {
            val normalizedName = LearnedMapping.normalizeMerchantName(merchantName)
            if (normalizedName.isBlank()) {
                AppLogger.w(TAG, "saveOrUpdateMapping: normalized merchant name is blank, skipping")
                return Result.success(Unit)
            }

            // 기존 매핑 검색
            val existing = findMapping(groupId, normalizedName)

            if (existing != null) {
                // 기존 매핑 업데이트 (사용 횟수 증가)
                AppLogger.d(TAG, "saveOrUpdateMapping: updating existing mapping id=${existing.id}, useCount=${existing.useCount + 1}")
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
                AppLogger.d(TAG, "saveOrUpdateMapping: creating new mapping for '$normalizedName'")
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

            AppLogger.i(TAG, "saveOrUpdateMapping: success for merchant='$merchantName'")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveOrUpdateMapping: failed for merchant='$merchantName'", e)
            Result.failure(e)
        }
    }

    /**
     * 가맹점명으로 학습된 매핑 조회
     */
    suspend fun findMapping(groupId: String, merchantName: String): LearnedMapping? {
        AppLogger.d(TAG, "findMapping: groupId=$groupId, merchant=$merchantName")
        return try {
            val normalizedName = LearnedMapping.normalizeMerchantName(merchantName)
            if (normalizedName.isBlank()) {
                AppLogger.d(TAG, "findMapping: normalized name is blank, returning null")
                return null
            }

            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("merchantName", normalizedName)
                .limit(1)
                .get()
                .await()

            val mapping = snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { LearnedMapping.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "findMapping: ${if (mapping != null) "found mapping id=${mapping.id}" else "no mapping found"}")
            mapping
        } catch (e: Exception) {
            AppLogger.e(TAG, "findMapping: failed for merchant='$merchantName'", e)
            null
        }
    }

    /**
     * 부분 일치 검색 (가맹점명 포함 여부)
     */
    suspend fun findMappingByPartialMatch(groupId: String, merchantName: String): LearnedMapping? {
        AppLogger.d(TAG, "findMappingByPartialMatch: groupId=$groupId, merchant=$merchantName")
        return try {
            val normalizedName = LearnedMapping.normalizeMerchantName(merchantName)
            if (normalizedName.length < 2) {
                AppLogger.d(TAG, "findMappingByPartialMatch: name too short (${normalizedName.length}), returning null")
                return null
            }

            // 모든 매핑 조회 후 부분 일치 검색
            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val mappings = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { LearnedMapping.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "findMappingByPartialMatch: searching in ${mappings.size} total mappings")

            // 정확히 일치하는 것 우선, 그 다음 부분 일치
            val result = mappings.find { it.merchantName == normalizedName }
                ?: mappings.find { normalizedName.contains(it.merchantName) || it.merchantName.contains(normalizedName) }
            AppLogger.d(TAG, "findMappingByPartialMatch: ${if (result != null) "found match id=${result.id}, merchant=${result.merchantName}" else "no match found"}")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "findMappingByPartialMatch: failed for merchant='$merchantName'", e)
            null
        }
    }

    /**
     * 그룹의 모든 학습 데이터 조회
     */
    suspend fun getAllMappings(groupId: String): List<LearnedMapping> {
        AppLogger.d(TAG, "getAllMappings: groupId=$groupId")
        return try {
            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val mappings = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { LearnedMapping.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "getAllMappings: found ${mappings.size} mappings")
            mappings
        } catch (e: Exception) {
            AppLogger.e(TAG, "getAllMappings: failed for groupId=$groupId", e)
            emptyList()
        }
    }

    /**
     * 학습 데이터 삭제
     */
    suspend fun deleteMapping(mappingId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteMapping: mappingId=$mappingId")
        return try {
            learnedMappingsCollection.document(mappingId).delete().await()
            AppLogger.i(TAG, "deleteMapping: success - mappingId=$mappingId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteMapping: failed for mappingId=$mappingId", e)
            Result.failure(e)
        }
    }

    /**
     * 그룹의 모든 학습 데이터 삭제
     */
    suspend fun deleteAllMappings(groupId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteAllMappings: groupId=$groupId")
        return try {
            val snapshot = learnedMappingsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val count = snapshot.documents.size
            AppLogger.d(TAG, "deleteAllMappings: deleting $count mappings")

            val batch = firestore.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()

            AppLogger.i(TAG, "deleteAllMappings: success - deleted $count mappings for groupId=$groupId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteAllMappings: failed for groupId=$groupId", e)
            Result.failure(e)
        }
    }
}
