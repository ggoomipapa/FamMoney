package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.Allowance
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AllowanceRepo"

@Singleton
class AllowanceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val allowancesCollection = firestore.collection("allowances")

    fun getAllowancesFlow(groupId: String): Flow<List<Allowance>> = callbackFlow {
        AppLogger.d(TAG, "getAllowancesFlow: groupId=$groupId")
        val listener = allowancesCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getAllowancesFlow: snapshot error for groupId=$groupId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val allowances = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Allowance.fromMap(doc.id, it) }
                } ?: emptyList()

                AppLogger.d(TAG, "getAllowancesFlow: received ${allowances.size} allowances")
                trySend(allowances)
            }

        awaitClose {
            AppLogger.d(TAG, "getAllowancesFlow: listener removed for groupId=$groupId")
            listener.remove()
        }
    }

    fun getChildAllowanceFlow(childUserId: String): Flow<Allowance?> = callbackFlow {
        AppLogger.d(TAG, "getChildAllowanceFlow: childUserId=$childUserId")
        val listener = allowancesCollection
            .whereEqualTo("childUserId", childUserId)
            .whereEqualTo("isActive", true)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e(TAG, "getChildAllowanceFlow: snapshot error for childUserId=$childUserId", error)
                    close(error)
                    return@addSnapshotListener
                }

                val allowance = snapshot?.documents?.firstOrNull()?.let { doc ->
                    doc.data?.let { Allowance.fromMap(doc.id, it) }
                }
                AppLogger.d(TAG, "getChildAllowanceFlow: allowance=${if (allowance != null) "found" else "null"}")
                trySend(allowance)
            }

        awaitClose {
            AppLogger.d(TAG, "getChildAllowanceFlow: listener removed for childUserId=$childUserId")
            listener.remove()
        }
    }

    suspend fun createAllowance(allowance: Allowance): Result<String> {
        AppLogger.d(TAG, "createAllowance: childUserId=${allowance.childUserId}, amount=${allowance.amount}")
        return try {
            val docRef = allowancesCollection.add(allowance.toMap()).await()
            AppLogger.i(TAG, "createAllowance: success - id=${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            AppLogger.e(TAG, "createAllowance: failed", e)
            Result.failure(e)
        }
    }

    suspend fun updateAllowance(allowanceId: String, amount: Long, frequency: String): Result<Unit> {
        AppLogger.d(TAG, "updateAllowance: id=$allowanceId, amount=$amount, frequency=$frequency")
        return try {
            allowancesCollection.document(allowanceId)
                .update(
                    mapOf(
                        "amount" to amount,
                        "frequency" to frequency
                    )
                )
                .await()
            AppLogger.i(TAG, "updateAllowance: success - id=$allowanceId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateAllowance: failed for id=$allowanceId", e)
            Result.failure(e)
        }
    }

    suspend fun updateBalance(allowanceId: String, newBalance: Long): Result<Unit> {
        AppLogger.d(TAG, "updateBalance: id=$allowanceId, newBalance=$newBalance")
        return try {
            allowancesCollection.document(allowanceId)
                .update("balance", newBalance)
                .await()
            AppLogger.i(TAG, "updateBalance: success - id=$allowanceId, balance=$newBalance")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "updateBalance: failed for id=$allowanceId", e)
            Result.failure(e)
        }
    }

    suspend fun giveAllowance(allowanceId: String, currentBalance: Long, amount: Long): Result<Unit> {
        AppLogger.d(TAG, "giveAllowance: id=$allowanceId, currentBalance=$currentBalance, amount=$amount")
        return try {
            allowancesCollection.document(allowanceId)
                .update(
                    mapOf(
                        "balance" to currentBalance + amount,
                        "nextPaymentDate" to Timestamp.now()
                    )
                )
                .await()
            AppLogger.i(TAG, "giveAllowance: success - id=$allowanceId, newBalance=${currentBalance + amount}")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "giveAllowance: failed for id=$allowanceId", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAllowance(allowanceId: String): Result<Unit> {
        AppLogger.d(TAG, "deleteAllowance: id=$allowanceId")
        return try {
            allowancesCollection.document(allowanceId)
                .update("isActive", false)
                .await()
            AppLogger.i(TAG, "deleteAllowance: success (deactivated) - id=$allowanceId")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "deleteAllowance: failed for id=$allowanceId", e)
            Result.failure(e)
        }
    }
}
