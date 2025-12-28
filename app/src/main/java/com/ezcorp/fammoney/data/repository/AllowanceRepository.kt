package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.Allowance
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AllowanceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val allowancesCollection = firestore.collection("allowances")

    fun getAllowancesFlow(groupId: String): Flow<List<Allowance>> = callbackFlow {
        val listener = allowancesCollection
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val allowances = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Allowance.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(allowances)
            }

        awaitClose { listener.remove() }
    }

    fun getChildAllowanceFlow(childUserId: String): Flow<Allowance?> = callbackFlow {
        val listener = allowancesCollection
            .whereEqualTo("childUserId", childUserId)
            .whereEqualTo("isActive", true)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val allowance = snapshot?.documents?.firstOrNull()?.let { doc ->
                    doc.data?.let { Allowance.fromMap(doc.id, it) }
                }
                trySend(allowance)
            }

        awaitClose { listener.remove() }
    }

    suspend fun createAllowance(allowance: Allowance): Result<String> {
        return try {
            val docRef = allowancesCollection.add(allowance.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAllowance(allowanceId: String, amount: Long, frequency: String): Result<Unit> {
        return try {
            allowancesCollection.document(allowanceId)
                .update(
                    mapOf(
                        "amount" to amount,
                        "frequency" to frequency
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBalance(allowanceId: String, newBalance: Long): Result<Unit> {
        return try {
            allowancesCollection.document(allowanceId)
                .update("balance", newBalance)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun giveAllowance(allowanceId: String, currentBalance: Long, amount: Long): Result<Unit> {
        return try {
            allowancesCollection.document(allowanceId)
                .update(
                    mapOf(
                        "balance" to currentBalance + amount,
                        "nextPaymentDate" to Timestamp.now()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllowance(allowanceId: String): Result<Unit> {
        return try {
            allowancesCollection.document(allowanceId)
                .update("isActive", false)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
