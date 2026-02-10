package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.SavingsContribution
import com.ezcorp.fammoney.data.model.SavingsGoal
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SavingsGoalRepo"

@Singleton
class SavingsGoalRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val goalsCollection = firestore.collection("savings_goals")
    private val contributionsCollection = firestore.collection("savings_contributions")

    fun getGoalsFlow(groupId: String): Flow<List<SavingsGoal>> = callbackFlow {
        AppLogger.d(TAG, "getGoalsFlow: groupId=$groupId")
        val listener = goalsCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getGoalsFlow: snapshot error for groupId=$groupId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val goals = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { SavingsGoal.fromMap(doc.id, it) }
                } ?: emptyList()

                AppLogger.d(TAG, "getGoalsFlow: received ${goals.size} goals")
                trySend(goals)
            }

        awaitClose {
            AppLogger.d(TAG, "getGoalsFlow: listener removed for groupId=$groupId")
            listener.remove()
        }
    }

    fun getGoalFlow(goalId: String): Flow<SavingsGoal?> = callbackFlow {
        AppLogger.d(TAG, "getGoalFlow: goalId=$goalId")
        val listener = goalsCollection.document(goalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getGoalFlow: snapshot error for goalId=$goalId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val goal = snapshot?.data?.let { SavingsGoal.fromMap(snapshot.id, it) }
                AppLogger.d(TAG, "getGoalFlow: goal=${if (goal != null) goal.name else "null"}")
                trySend(goal)
            }

        awaitClose {
            AppLogger.d(TAG, "getGoalFlow: listener removed for goalId=$goalId")
            listener.remove()
        }
    }

    fun getContributionsFlow(goalId: String): Flow<List<SavingsContribution>> = callbackFlow {
        AppLogger.d(TAG, "getContributionsFlow: goalId=$goalId")
        val listener = contributionsCollection
            .whereEqualTo("goalId", goalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getContributionsFlow: snapshot error for goalId=$goalId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val contributions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { SavingsContribution.fromMap(doc.id, it) }
                } ?: emptyList()

                AppLogger.d(TAG, "getContributionsFlow: received ${contributions.size} contributions")
                trySend(contributions)
            }

        awaitClose {
            AppLogger.d(TAG, "getContributionsFlow: listener removed for goalId=$goalId")
            listener.remove()
        }
    }

    suspend fun createGoal(goal: SavingsGoal): Result<String> {
        AppLogger.d(TAG, "createGoal: name=${goal.name}, targetAmount=${goal.targetAmount}")
        return try {
            val docRef = goalsCollection.add(goal.toMap()).await()
            AppLogger.i(TAG, "createGoal: success - id=${docRef.id}, name=${goal.name}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "createGoal: failed for name=${goal.name}", e)
            Result.failure(e)
        }
    }

    suspend fun updateGoal(goalId: String, name: String, targetAmount: Long, iconEmoji: String): Result<Unit> {
        AppLogger.d(TAG, "updateGoal: goalId=$goalId, name=$name, targetAmount=$targetAmount")
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
            AppLogger.i(TAG, "updateGoal: success - goalId=$goalId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateGoal: failed for goalId=$goalId", e)
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
        AppLogger.d(TAG, "addContribution: goalId=$goalId, userId=$userId, amount=$amount, auto=$isAutoDetected, confidence=$matchConfidence")
        return try {
            firestore.runBatch { batch ->
                // 기여 이력 추가
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

                // 목표 금액 업데이트
                batch.update(
                    goalsCollection.document(goalId),
                    "currentAmount",
                    FieldValue.increment(amount)
                )
            }.await()
            AppLogger.d(TAG, "addContribution: batch committed, checking goal completion")

            // 목표 달성 여부 확인
            val goal = goalsCollection.document(goalId).get().await()
            val goalData = goal.data?.let { SavingsGoal.fromMap(goal.id, it) }
            if (goalData != null && goalData.currentAmount >= goalData.targetAmount && !goalData.isCompleted) {
                AppLogger.i(TAG, "addContribution: goal completed! goalId=$goalId, current=${goalData.currentAmount}, target=${goalData.targetAmount}")
                goalsCollection.document(goalId)
                    .update("isCompleted", true)
                    .await()
            }

            AppLogger.i(TAG, "addContribution: success - goalId=$goalId, amount=$amount")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "addContribution: failed for goalId=$goalId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteGoal(goalId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteGoal: goalId=$goalId")
        return try {
            goalsCollection.document(goalId).delete().await()
            AppLogger.d(TAG, "deleteGoal: goal document deleted, now deleting contributions")
            // 관련 기여 이력도 삭제
            val contributions = contributionsCollection
                .whereEqualTo("goalId", goalId)
                .get()
                .await()

            val count = contributions.documents.size
            firestore.runBatch { batch ->
                contributions.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
            }.await()

            AppLogger.i(TAG, "deleteGoal: success - goalId=$goalId, deleted $count contributions")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteGoal: failed for goalId=$goalId", e)
            Result.failure(e)
        }
    }

    /**
     * 자동 입금 감지 활성화된 목표 조회
     */
    suspend fun getAutoDepositEnabledGoals(groupId: String): List<SavingsGoal> {
        AppLogger.d(TAG, "getAutoDepositEnabledGoals: groupId=$groupId")
        return try {
            val snapshot = goalsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("autoDepositEnabled", true)
                .whereEqualTo("isCompleted", false)
                .get()
                .await()

            val goals = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { SavingsGoal.fromMap(doc.id, it) }
            }
            AppLogger.d(TAG, "getAutoDepositEnabledGoals: found ${goals.size} auto-deposit goals")
            goals
        } catch (e: Exception) {
            AppLogger.e(TAG, "getAutoDepositEnabledGoals: failed for groupId=$groupId", e)
            emptyList()
        }
    }

    /**
     * 기여 이력 수정 (입금자 변경 등)
     */
    suspend fun updateContribution(
        contributionId: String,
        newUserId: String,
        newUserName: String,
        newAmount: Long,
        modifiedBy: String
    ): Result<Unit> {
        AppLogger.d(TAG, "updateContribution: id=$contributionId, newUser=$newUserId, newAmount=$newAmount, modifiedBy=$modifiedBy")
        return try {
            // 기존 기여 이력 조회
            val contributionDoc = contributionsCollection.document(contributionId).get().await()
            val oldContribution = contributionDoc.data?.let { SavingsContribution.fromMap(contributionId, it) }
                ?: run {
                    AppLogger.w(TAG, "updateContribution: contribution not found - id=$contributionId")
                    return Result.failure(Exception("기여 이력을 찾을 수 없습니다"))
                }

            val amountDiff = newAmount - oldContribution.amount
            AppLogger.d(TAG, "updateContribution: oldAmount=${oldContribution.amount}, newAmount=$newAmount, diff=$amountDiff")

            firestore.runBatch { batch ->
                // 기여 이력 업데이트
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

                // 금액이 변경된 경우 목표 금액도 업데이트
                if (amountDiff != 0L) {
                    AppLogger.d(TAG, "updateContribution: adjusting goal amount by $amountDiff")
                    batch.update(
                        goalsCollection.document(oldContribution.goalId),
                        "currentAmount",
                        FieldValue.increment(amountDiff)
                    )
                }
            }.await()

            AppLogger.i(TAG, "updateContribution: success - id=$contributionId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateContribution: failed for id=$contributionId", e)
            Result.failure(e)
        }
    }

    /**
     * 기여 이력 삭제
     */
    suspend fun deleteContribution(contributionId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteContribution: id=$contributionId")
        return try {
            // 기존 기여 이력 조회
            val contributionDoc = contributionsCollection.document(contributionId).get().await()
            val contribution = contributionDoc.data?.let { SavingsContribution.fromMap(contributionId, it) }
                ?: run {
                    AppLogger.w(TAG, "deleteContribution: contribution not found - id=$contributionId")
                    return Result.failure(Exception("기여 이력을 찾을 수 없습니다"))
                }

            AppLogger.d(TAG, "deleteContribution: amount=${contribution.amount}, goalId=${contribution.goalId}")

            firestore.runBatch { batch ->
                // 기여 이력 삭제
                batch.delete(contributionsCollection.document(contributionId))

                // 목표 금액에서 차감
                batch.update(
                    goalsCollection.document(contribution.goalId),
                    "currentAmount",
                    FieldValue.increment(-contribution.amount)
                )
            }.await()

            AppLogger.i(TAG, "deleteContribution: success - id=$contributionId, subtracted ${contribution.amount}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteContribution: failed for id=$contributionId", e)
            Result.failure(e)
        }
    }

    /**
     * 목표별 멤버 통계 조회
     */
    suspend fun getMemberStatistics(goalId: String): List<MemberStatistics> {
        AppLogger.d(TAG, "getMemberStatistics: goalId=$goalId")
        return try {
            val contributions = contributionsCollection
                .whereEqualTo("goalId", goalId)
                .get()
                .await()
                .documents
                .mapNotNull { doc -> doc.data?.let { SavingsContribution.fromMap(doc.id, it) } }

            AppLogger.d(TAG, "getMemberStatistics: found ${contributions.size} contributions")

            // 멤버별로 그룹핑
            val stats = contributions.groupBy { it.userId }.map { (userId, userContributions) ->
                MemberStatistics(
                    userId = userId,
                    userName = userContributions.firstOrNull()?.userName ?: "",
                    totalAmount = userContributions.sumOf { it.amount },
                    contributionCount = userContributions.size,
                    lastContributionDate = userContributions.maxOfOrNull { it.createdAt ?: com.google.firebase.Timestamp.now() }
                )
            }.sortedByDescending { it.totalAmount }
            AppLogger.d(TAG, "getMemberStatistics: ${stats.size} members with contributions")
            stats
        } catch (e: Exception) {
            AppLogger.e(TAG, "getMemberStatistics: failed for goalId=$goalId", e)
            emptyList()
        }
    }

    /**
     * 확인 필요한 기여 이력 조회
     */
    fun getNeedsReviewContributionsFlow(groupId: String): Flow<List<SavingsContribution>> = callbackFlow {
        AppLogger.d(TAG, "getNeedsReviewContributionsFlow: groupId=$groupId")
        val listener = contributionsCollection
            .whereEqualTo("needsReview", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getNeedsReviewContributionsFlow: snapshot error", error)
                    close(error)
                    return@addSnapshotListener
                }

                val contributions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { SavingsContribution.fromMap(doc.id, it) }
                } ?: emptyList()

                AppLogger.d(TAG, "getNeedsReviewContributionsFlow: received ${contributions.size} contributions needing review")
                trySend(contributions)
            }

        awaitClose {
            AppLogger.d(TAG, "getNeedsReviewContributionsFlow: listener removed")
            listener.remove()
        }
    }
}

/**
 * 멤버별 통계
 */
data class MemberStatistics(
    val userId: String,
    val userName: String,
    val totalAmount: Long,
    val contributionCount: Int,
    val lastContributionDate: com.google.firebase.Timestamp?)
