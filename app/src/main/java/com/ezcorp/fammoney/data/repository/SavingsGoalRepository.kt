package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.SavingsContribution
import com.ezcorp.fammoney.data.model.SavingsGoal
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavingsGoalRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val goalsCollection = firestore.collection("savings_goals")
    private val contributionsCollection = firestore.collection("savings_contributions")

    fun getGoalsFlow(groupId: String): Flow<List<SavingsGoal>> = callbackFlow {
        val listener = goalsCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val goals = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { SavingsGoal.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(goals)
            }

        awaitClose { listener.remove() }
    }

    fun getGoalFlow(goalId: String): Flow<SavingsGoal?> = callbackFlow {
        val listener = goalsCollection.document(goalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val goal = snapshot?.data?.let { SavingsGoal.fromMap(snapshot.id, it) }
                trySend(goal)
            }

        awaitClose { listener.remove() }
    }

    fun getContributionsFlow(goalId: String): Flow<List<SavingsContribution>> = callbackFlow {
        val listener = contributionsCollection
            .whereEqualTo("goalId", goalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val contributions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { SavingsContribution.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(contributions)
            }

        awaitClose { listener.remove() }
    }

    suspend fun createGoal(goal: SavingsGoal): Result<String> {
        return try {
            val docRef = goalsCollection.add(goal.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateGoal(goalId: String, name: String, targetAmount: Long, iconEmoji: String): Result<Unit> {
        return try {
            goalsCollection.document(goalId)
                .update(
                    mapOf(
                        "name" to name,
                        "targetAmount" to targetAmount,
                        "iconEmoji" to iconEmoji
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addContribution(
        goalId: String,
        userId: String,
        userName: String,
        amount: Long,
        isAutoDetected: Boolean = false,
        detectedSenderName: String = "",
        matchConfidence: String = "manual",
        originalNotificationText: String = "",
        needsReview: Boolean = false
    ): Result<Unit> {
        return try {
            firestore.runBatch { batch ->
                // ê¸°ì¬ ?´ì­ ì¶ê"
                val contributionRef = contributionsCollection.document()
                batch.set(
                    contributionRef,
                    SavingsContribution(
                        goalId = goalId,
                        userId = userId,
                        userName = userName,
                        amount = amount,
                        isAutoDetected = isAutoDetected,
                        detectedSenderName = detectedSenderName,
                        matchConfidence = matchConfidence,
                        originalNotificationText = originalNotificationText,
                        needsReview = needsReview
                    ).toMap()
                )

                // ëª©í ê¸ì¡ ?ë°?´í¸
                batch.update(
                    goalsCollection.document(goalId),
                    "currentAmount",
                    FieldValue.increment(amount)
                )
            }.await()

            // ëª©í ?¬ì± ?¬ë? ?ì¸
            val goal = goalsCollection.document(goalId).get().await()
            val goalData = goal.data?.let { SavingsGoal.fromMap(goal.id, it) }
            if (goalData != null && goalData.currentAmount >= goalData.targetAmount && !goalData.isCompleted) {
                goalsCollection.document(goalId)
                    .update("isCompleted", true)
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGoal(goalId: String): Result<Unit> {
        return try {
            goalsCollection.document(goalId).delete().await()
            // ê´??ê¸°ì¬ ?´ì­???? 
            val contributions = contributionsCollection
                .whereEqualTo("goalId", goalId)
                .get()
                .await()

            firestore.runBatch { batch ->
                contributions.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ?ë ?ê¸???ì±?ë ëª©í ì¡°í
     */
    suspend fun getAutoDepositEnabledGoals(groupId: String): List<SavingsGoal> {
        return try {
            val snapshot = goalsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("autoDepositEnabled", true)
                .whereEqualTo("isCompleted", false)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { SavingsGoal.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * ê¸°ì¬ ?´ì­ ?ì  (?ê¸??ë³ê²??"
     */
    suspend fun updateContribution(
        contributionId: String,
        newUserId: String,
        newUserName: String,
        newAmount: Long,
        modifiedBy: String
    ): Result<Unit> {
        return try {
            // ê¸°ì¡´ ê¸°ì¬ ?´ì­ ì¡°í
            val contributionDoc = contributionsCollection.document(contributionId).get().await()
            val oldContribution = contributionDoc.data?.let { SavingsContribution.fromMap(contributionId, it) }
                ?: return Result.failure(Exception("ê¸°ì¬ ?´ì­??ì°¾ì ???ìµ?ë¤"))

            val amountDiff = newAmount - oldContribution.amount

            firestore.runBatch { batch ->
                // ê¸°ì¬ ?´ì­ ?ë°?´í¸
                batch.update(
                    contributionsCollection.document(contributionId),
                    mapOf(
                        "userId" to newUserId,
                        "userName" to newUserName,
                        "amount" to newAmount,
                        "isModified" to true,
                        "modifiedBy" to modifiedBy,
                        "modifiedAt" to com.google.firebase.Timestamp.now(),
                        "needsReview" to false
                    )
                )

                // ê¸ì¡??ë³ê²½ë ê²½ì° ëª©í ê¸ì¡???ë°?´í¸
                if (amountDiff != 0L) {
                    batch.update(
                        goalsCollection.document(oldContribution.goalId),
                        "currentAmount",
                        FieldValue.increment(amountDiff)
                    )
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ê¸°ì¬ ?´ì­ ?? 
     */
    suspend fun deleteContribution(contributionId: String): Result<Unit> {
        return try {
            // ê¸°ì¡´ ê¸°ì¬ ?´ì­ ì¡°í
            val contributionDoc = contributionsCollection.document(contributionId).get().await()
            val contribution = contributionDoc.data?.let { SavingsContribution.fromMap(contributionId, it) }
                ?: return Result.failure(Exception("ê¸°ì¬ ?´ì­??ì°¾ì ???ìµ?ë¤"))

            firestore.runBatch { batch ->
                // ê¸°ì¬ ?´ì­ ?? 
                batch.delete(contributionsCollection.document(contributionId))

                // ëª©í ê¸ì¡?ì ì°¨ê°
                batch.update(
                    goalsCollection.document(contribution.goalId),
                    "currentAmount",
                    FieldValue.increment(-contribution.amount)
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ëª©íë³?ë©¤ë² ?µê³ ì¡°í
     */
    suspend fun getMemberStatistics(goalId: String): List<MemberStatistics> {
        return try {
            val contributions = contributionsCollection
                .whereEqualTo("goalId", goalId)
                .get()
                .await()
                .documents
                .mapNotNull { doc -> doc.data?.let { SavingsContribution.fromMap(doc.id, it) } }

            // ë©¤ë²ë³ë¡ ê·¸ë£¹
contributions.groupBy { it.userId }.map { (userId, userContributions) ->
                MemberStatistics(
                    userId = userId,
                    userName = userContributions.firstOrNull()?.userName ?: "",
                    totalAmount = userContributions.sumOf { it.amount },
                    contributionCount = userContributions.size,
                    lastContributionDate = userContributions.maxOfOrNull { it.createdAt ?: com.google.firebase.Timestamp.now() }
                )
            }.sortedByDescending { it.totalAmount }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * ?ì¸???ì??ê¸°ì¬ ?´ì­ ì¡°í
     */
    fun getNeedsReviewContributionsFlow(groupId: String): Flow<List<SavingsContribution>> = callbackFlow {
        val listener = contributionsCollection
            .whereEqualTo("needsReview", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val contributions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { SavingsContribution.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(contributions)
            }

        awaitClose { listener.remove() }
    }
}

/**
 * ë©¤ë²ë³??µê³
 */
data class MemberStatistics(
    val userId: String,
    val userName: String,
    val totalAmount: Long,
    val contributionCount: Int,
    val lastContributionDate: com.google.firebase.Timestamp?)
