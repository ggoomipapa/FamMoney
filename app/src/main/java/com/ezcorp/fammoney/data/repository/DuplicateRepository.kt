package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.DuplicateRule
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DuplicateRepo"

@Singleton
class DuplicateRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val duplicatesCollection = firestore.collection("pending_duplicates")
    private val rulesCollection = firestore.collection("duplicate_rules")

    // ===== Pending Duplicates =====

    suspend fun addPendingDuplicate(duplicate: PendingDuplicate): Result<String> {
        AppLogger.d(TAG, "addPendingDuplicate: groupId=${duplicate.groupId}")
        return try {
            val docRef = duplicatesCollection.add(duplicate.toMap()).await()
            AppLogger.i(TAG, "addPendingDuplicate: success - id=${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "addPendingDuplicate: failed", e)
            Result.failure(e)
        }
    }

    fun getPendingDuplicatesFlow(groupId: String): Flow<List<PendingDuplicate>> = callbackFlow {
        AppLogger.d(TAG, "getPendingDuplicatesFlow: groupId=$groupId")
        val listener = duplicatesCollection
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("isResolved", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getPendingDuplicatesFlow: snapshot error", error)
                    // 에러 시 빈 목록 반환 (사일런트 방식)
                trySend(emptyList())
                    return@addSnapshotListener
                }

                val duplicates = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { PendingDuplicate.fromMap(doc.id, it) }
                }?.sortedByDescending { it.createdAt.toDate().time } ?: emptyList()

                AppLogger.d(TAG, "getPendingDuplicatesFlow: received ${duplicates.size} pending duplicates")
                trySend(duplicates)
            }

        awaitClose {
            AppLogger.d(TAG, "getPendingDuplicatesFlow: listener removed for groupId=$groupId")
            listener.remove()
        }
    }

    suspend fun getUnresolvedDuplicates(groupId: String): List<PendingDuplicate> {
        AppLogger.d(TAG, "getUnresolvedDuplicates: groupId=$groupId")
        return try {
            val snapshot = duplicatesCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("isResolved", false)
                .get()
                .await()

            val result = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { PendingDuplicate.fromMap(doc.id, it) }
            }.sortedByDescending { it.createdAt.toDate().time }
            AppLogger.d(TAG, "getUnresolvedDuplicates: found ${result.size} unresolved duplicates")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "getUnresolvedDuplicates: failed for groupId=$groupId", e)
            emptyList()
        }
    }

    suspend fun resolveDuplicate(
        duplicateId: String,
        resolution: DuplicateResolution
    ): Result<Unit> {
        AppLogger.d(TAG, "resolveDuplicate: id=$duplicateId, resolution=$resolution")
        return try {
            duplicatesCollection.document(duplicateId)
                .update(
                    mapOf(
                        "isResolved" to true,
                        "resolvedAt" to Timestamp.now(),
                        "resolution" to resolution.name
                    )
                )
                .await()
            AppLogger.i(TAG, "resolveDuplicate: success - id=$duplicateId, resolution=$resolution")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "resolveDuplicate: failed for id=$duplicateId", e)
            Result.failure(e)
        }
    }

    // ===== Duplicate Rules =====

    suspend fun addDuplicateRule(rule: DuplicateRule): Result<String> {
        AppLogger.d(TAG, "addDuplicateRule: groupId=${rule.groupId}, bank1=${rule.bank1Id}, bank2=${rule.bank2Id}")
        return try {
            // 기존 규칙이 있으면 업데이트
            val existingRule = getDuplicateRule(rule.groupId, rule.bank1Id, rule.bank2Id)
            if (existingRule != null) {
                AppLogger.d(TAG, "addDuplicateRule: updating existing rule id=${existingRule.id}")
                rulesCollection.document(existingRule.id)
                    .update("resolution", rule.resolution.name)
                    .await()
                Result.success(existingRule.id)
            } else {
                val docRef = rulesCollection.add(rule.toMap()).await()
                AppLogger.i(TAG, "addDuplicateRule: created new rule id=${docRef.id}")
                Result.success(docRef.id)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "addDuplicateRule: failed", e)
            Result.failure(e)
        }
    }

    suspend fun getDuplicateRule(
        groupId: String,
        bank1Id: String,
        bank2Id: String
    ): DuplicateRule? {
        AppLogger.d(TAG, "getDuplicateRule: groupId=$groupId, bank1=$bank1Id, bank2=$bank2Id")
        return try {
            // bank1Id, bank2Id 순서에 관계없이 규칙 찾기
            var snapshot = rulesCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("bank1Id", bank1Id)
                .whereEqualTo("bank2Id", bank2Id)
                .get()
                .await()

            if (snapshot.isEmpty) {
                AppLogger.d(TAG, "getDuplicateRule: trying reverse order")
                // 반대 순서로도 검색
                snapshot = rulesCollection
                    .whereEqualTo("groupId", groupId)
                    .whereEqualTo("bank1Id", bank2Id)
                    .whereEqualTo("bank2Id", bank1Id)
                    .get()
                    .await()
            }

            val rule = snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { DuplicateRule.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "getDuplicateRule: ${if (rule != null) "found rule id=${rule.id}" else "no rule found"}")
            rule
        } catch (e: Exception) {
            AppLogger.e(TAG, "getDuplicateRule: failed", e)
            null
        }
    }

    suspend fun getAllDuplicateRules(groupId: String): List<DuplicateRule> {
        AppLogger.d(TAG, "getAllDuplicateRules: groupId=$groupId")
        return try {
            val snapshot = rulesCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val rules = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { DuplicateRule.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "getAllDuplicateRules: found ${rules.size} rules")
            rules
        } catch (e: Exception) {
            AppLogger.e(TAG, "getAllDuplicateRules: failed for groupId=$groupId", e)
            emptyList()
        }
    }

    suspend fun deleteDuplicateRule(ruleId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteDuplicateRule: ruleId=$ruleId")
        return try {
            rulesCollection.document(ruleId).delete().await()
            AppLogger.i(TAG, "deleteDuplicateRule: success - ruleId=$ruleId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteDuplicateRule: failed for ruleId=$ruleId", e)
            Result.failure(e)
        }
    }
}
