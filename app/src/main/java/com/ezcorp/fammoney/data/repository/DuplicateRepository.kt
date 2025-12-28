package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.DuplicateRule
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val duplicatesCollection = firestore.collection("pending_duplicates")
    private val rulesCollection = firestore.collection("duplicate_rules")

    // ===== Pending Duplicates =====

    suspend fun addPendingDuplicate(duplicate: PendingDuplicate): Result<String> {
        return try {
            val docRef = duplicatesCollection.add(duplicate.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getPendingDuplicatesFlow(groupId: String): Flow<List<PendingDuplicate>> = callbackFlow {
        val listener = duplicatesCollection
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("isResolved", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // ?¸ë±???¤ë¥ ??ë°ì ??ë¹?ëª©ë¡ ë°í (?¬ë??ë°©ì")
                trySend(emptyList())
                    return@addSnapshotListener
                }

                val duplicates = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { PendingDuplicate.fromMap(doc.id, it) }
                }?.sortedByDescending { it.createdAt.toDate().time } ?: emptyList()

                trySend(duplicates)
            }

        awaitClose { listener.remove() }
    }

    suspend fun getUnresolvedDuplicates(groupId: String): List<PendingDuplicate> {
        return try {
            val snapshot = duplicatesCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("isResolved", false)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { PendingDuplicate.fromMap(doc.id, it) }
            }.sortedByDescending { it.createdAt.toDate().time }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun resolveDuplicate(
        duplicateId: String,
        resolution: DuplicateResolution
    ): Result<Unit> {
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
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== Duplicate Rules =====

    suspend fun addDuplicateRule(rule: DuplicateRule): Result<String> {
        return try {
            // ê¸°ì¡´ ê·ì¹???ì¼ë©??ë°?´í¸
            val existingRule = getDuplicateRule(rule.groupId, rule.bank1Id, rule.bank2Id)
            if (existingRule != null) {
                rulesCollection.document(existingRule.id)
                    .update("resolution", rule.resolution.name)
                    .await()
                Result.success(existingRule.id)
            } else {
                val docRef = rulesCollection.add(rule.toMap()).await()
                Result.success(docRef.id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDuplicateRule(
        groupId: String,
        bank1Id: String,
        bank2Id: String
    ): DuplicateRule? {
        return try {
            // bank1Id, bank2Id ?ì? ?ê??ì´ ê·ì¹ ì°¾ê¸°
            var snapshot = rulesCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("bank1Id", bank1Id)
                .whereEqualTo("bank2Id", bank2Id)
                .get()
                .await()

            if (snapshot.isEmpty) {
                // ë°ë? ?ìë¡ë ê²
snapshot = rulesCollection
                    .whereEqualTo("groupId", groupId)
                    .whereEqualTo("bank1Id", bank2Id)
                    .whereEqualTo("bank2Id", bank1Id)
                    .get()
                    .await()
            }

            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { DuplicateRule.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAllDuplicateRules(groupId: String): List<DuplicateRule> {
        return try {
            val snapshot = rulesCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { DuplicateRule.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteDuplicateRule(ruleId: String): Result<Unit> {
        return try {
            rulesCollection.document(ruleId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
