package com.ezcorp.fammoney.data.repository

import android.util.Log
import com.ezcorp.fammoney.data.model.Child
import com.ezcorp.fammoney.data.model.ChildExpense
import com.ezcorp.fammoney.data.model.ChildIncome
import com.ezcorp.fammoney.data.model.IncomeGiverType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChildIncomeRepository"

@Singleton
class ChildIncomeRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val childrenCollection = firestore.collection("children")
    private val childIncomesCollection = firestore.collection("childIncomes")
    private val childExpensesCollection = firestore.collection("childExpenses")

    // ===== 자녀 관리 =====

    suspend fun addChild(child: Child): Result<String> {
        return try {
            Log.d(TAG, "addChild 시도: name=${child.name}, groupId=${child.groupId}")
            val docRef = childrenCollection.add(child.toMap()).await()
            Log.d(TAG, "addChild 성공: id=${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "addChild 실패", e)
            Result.failure(e)
        }
    }

    suspend fun updateChild(child: Child): Result<Unit> {
        return try {
            childrenCollection.document(child.id)
                .set(child.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChild(childId: String): Result<Unit> {
        return try {
            // 자녀 삭제
            childrenCollection.document(childId).delete().await()
            // 해당 자녀의 수입 기록도 삭제
            val incomes = childIncomesCollection
                .whereEqualTo("childId", childId)
                .get()
                .await()
            incomes.documents.forEach { doc ->
                childIncomesCollection.document(doc.id).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getChildrenByGroup(groupId: String): Flow<List<Child>> = callbackFlow {
        Log.d(TAG, "getChildrenByGroup 시작: groupId=$groupId")
        val listener = childrenCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getChildrenByGroup 에러: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val children = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Child.fromMap(doc.id, it) }
                } ?: emptyList()

                Log.d(TAG, "getChildrenByGroup 결과: ${children.size}명")
                trySend(children)
            }

        awaitClose { listener.remove() }
    }

    suspend fun getChildById(childId: String): Child? {
        return try {
            val doc = childrenCollection.document(childId).get().await()
            doc.data?.let { Child.fromMap(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    // ===== 자녀 수입 관리 =====

    suspend fun addChildIncome(income: ChildIncome): Result<String> {
        return try {
            val docRef = childIncomesCollection.add(income.toMap()).await()
            // 자녀 총 수입 업데이트
            updateChildTotalIncome(income.childId, income.amount)
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChildIncome(income: ChildIncome, previousAmount: Long): Result<Unit> {
        return try {
            childIncomesCollection.document(income.id)
                .set(income.toMap())
                .await()
            // 자녀 총 수입 업데이트 (기존 금액 빼고 새 금액 더하기)
            val difference = income.amount - previousAmount
            updateChildTotalIncome(income.childId, difference)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChildIncome(incomeId: String, childId: String, amount: Long): Result<Unit> {
        return try {
            childIncomesCollection.document(incomeId).delete().await()
            // 자녀 총 수입 업데이트 (금액 빼기)
            updateChildTotalIncome(childId, -amount)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateChildTotalIncome(childId: String, amountDelta: Long) {
        try {
            val child = getChildById(childId)
            if (child != null) {
                val newTotal = child.totalIncome + amountDelta
                childrenCollection.document(childId)
                    .update("totalIncome", newTotal)
                    .await()
            }
        } catch (e: Exception) {
            // 업데이트 실패 시 무시
        }
    }

    fun getChildIncomesByGroup(groupId: String): Flow<List<ChildIncome>> = callbackFlow {
        val listener = childIncomesCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("incomeDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { ChildIncome.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(incomes)
            }

        awaitClose { listener.remove() }
    }

    fun getChildIncomesByChild(childId: String): Flow<List<ChildIncome>> = callbackFlow {
        val listener = childIncomesCollection
            .whereEqualTo("childId", childId)
            .orderBy("incomeDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val incomes = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { ChildIncome.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(incomes)
            }

        awaitClose { listener.remove() }
    }

    suspend fun getChildIncomeById(incomeId: String): ChildIncome? {
        return try {
            val doc = childIncomesCollection.document(incomeId).get().await()
            doc.data?.let { ChildIncome.fromMap(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    // ===== 자녀 지출 관리 =====

    suspend fun addChildExpense(expense: ChildExpense): Result<String> {
        return try {
            val docRef = childExpensesCollection.add(expense.toMap()).await()
            // 자녀 총 지출 업데이트
            updateChildTotalExpense(expense.childId, expense.amount)
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateChildExpense(expense: ChildExpense, previousAmount: Long): Result<Unit> {
        return try {
            childExpensesCollection.document(expense.id)
                .set(expense.toMap())
                .await()
            // 자녀 총 지출 업데이트 (기존 금액 빼고 새 금액 더하기)
            val difference = expense.amount - previousAmount
            updateChildTotalExpense(expense.childId, difference)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChildExpense(expenseId: String, childId: String, amount: Long): Result<Unit> {
        return try {
            childExpensesCollection.document(expenseId).delete().await()
            // 자녀 총 지출 업데이트 (금액 빼기)
            updateChildTotalExpense(childId, -amount)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateChildTotalExpense(childId: String, amountDelta: Long) {
        try {
            val child = getChildById(childId)
            if (child != null) {
                val newTotal = child.totalExpense + amountDelta
                childrenCollection.document(childId)
                    .update("totalExpense", newTotal)
                    .await()
            }
        } catch (e: Exception) {
            // 업데이트 실패 시 무시
        }
    }

    fun getChildExpensesByGroup(groupId: String): Flow<List<ChildExpense>> = callbackFlow {
        val listener = childExpensesCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("expenseDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { ChildExpense.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(expenses)
            }

        awaitClose { listener.remove() }
    }

    fun getChildExpensesByChild(childId: String): Flow<List<ChildExpense>> = callbackFlow {
        val listener = childExpensesCollection
            .whereEqualTo("childId", childId)
            .orderBy("expenseDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val expenses = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { ChildExpense.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(expenses)
            }

        awaitClose { listener.remove() }
    }

    suspend fun getChildExpenseById(expenseId: String): ChildExpense? {
        return try {
            val doc = childExpensesCollection.document(expenseId).get().await()
            doc.data?.let { ChildExpense.fromMap(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    // 자녀 삭제 시 관련 지출 기록도 삭제
    suspend fun deleteChildExpensesByChild(childId: String): Result<Unit> {
        return try {
            val expenses = childExpensesCollection
                .whereEqualTo("childId", childId)
                .get()
                .await()
            expenses.documents.forEach { doc ->
                childExpensesCollection.document(doc.id).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== 용돈 관리 (Allowance 통합) =====

    /**
     * 용돈 설정
     * @param childId 자녀 ID
     * @param amount 정기 용돈 금액
     * @param frequency 지급 주기 ("weekly" | "monthly")
     */
    suspend fun setAllowance(childId: String, amount: Long, frequency: String): Result<Unit> {
        return try {
            Log.d(TAG, "setAllowance: childId=$childId, amount=$amount, frequency=$frequency")
            childrenCollection.document(childId)
                .update(
                    mapOf(
                        "allowanceAmount" to amount,
                        "allowanceFrequency" to frequency
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "setAllowance 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 용돈 시작 (적립 체계 -> 용돈 체계 전환)
     * 현재까지 모은 금액을 적립금으로 확정하고 용돈 관리를 시작합니다
     * @param childId 자녀 ID
     */
    suspend fun startAllowance(childId: String): Result<Unit> {
        return try {
            val child = getChildById(childId) ?: return Result.failure(Exception("Child not found"))
            val currentBalance = child.totalIncome - child.totalExpense
            val now = Timestamp.now()

            Log.d(TAG, "startAllowance: childId=$childId, preSavingsAmount=$currentBalance")

            childrenCollection.document(childId)
                .update(
                    mapOf(
                        "allowanceStatus" to "active",
                        "allowanceStartDate" to now,
                        "allowanceBalance" to 0L,  // 용돈은 0원에서 시작
                        "preSavingsAmount" to currentBalance,
                        "preSavingsStartDate" to child.createdAt,
                        "preSavingsEndDate" to now
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "startAllowance 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 용돈 주기 (부모가 자녀에게 용돈 지급)
     * 용돈 잔액에 금액을 추가하고, 수입 기록을 생성합니다
     * @param childId 자녀 ID
     * @param amount 지급 금액
     * @param recordedByUserId 기록한 사용자 ID
     * @param recordedByUserName 기록한 사용자 이름
     */
    suspend fun giveAllowance(
        childId: String,
        amount: Long,
        recordedByUserId: String = "",
        recordedByUserName: String = ""
    ): Result<Unit> {
        return try {
            val child = getChildById(childId) ?: return Result.failure(Exception("Child not found"))

            Log.d(TAG, "giveAllowance: childId=$childId, amount=$amount, currentBalance=${child.allowanceBalance}")

            // 용돈 잔액 업데이트
            val newBalance = child.allowanceBalance + amount
            childrenCollection.document(childId)
                .update("allowanceBalance", newBalance)
                .await()

            // 수입 기록 생성 (ALLOWANCE 타입)
            val income = ChildIncome(
                groupId = child.groupId,
                childId = childId,
                childName = child.name,
                amount = amount,
                giverType = IncomeGiverType.ALLOWANCE,
                giverName = "",
                memo = "정기 용돈",
                recordedByUserId = recordedByUserId,
                recordedByUserName = recordedByUserName,
                incomeDate = Timestamp.now()
            )
            childIncomesCollection.add(income.toMap()).await()

            // totalIncome도 업데이트
            updateChildTotalIncome(childId, amount)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "giveAllowance 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 용돈 잔액에 금액 추가 (거래 연동 - 수입)
     * HomeScreen에서 자녀 용돈 카테고리로 수입 거래 추가 시 호출
     * @param childId 자녀 ID
     * @param amount 추가할 금액
     */
    suspend fun addToAllowanceBalance(childId: String, amount: Long): Result<Unit> {
        return try {
            val child = getChildById(childId) ?: return Result.failure(Exception("Child not found"))

            Log.d(TAG, "addToAllowanceBalance: childId=$childId, amount=$amount")

            if (child.isAllowanceActive) {
                // 용돈 체계: allowanceBalance에 추가
                val newBalance = child.allowanceBalance + amount
                childrenCollection.document(childId)
                    .update("allowanceBalance", newBalance)
                    .await()
            }
            // 적립 체계: totalIncome은 addChildIncome에서 처리되므로 별도 처리 불필요
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addToAllowanceBalance 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 용돈 잔액에서 금액 차감 (거래 연동 - 지출)
     * HomeScreen에서 자녀 용돈 카테고리로 지출 거래 추가 시 호출
     * @param childId 자녀 ID
     * @param amount 차감할 금액
     */
    suspend fun subtractFromAllowanceBalance(childId: String, amount: Long): Result<Unit> {
        return try {
            val child = getChildById(childId) ?: return Result.failure(Exception("Child not found"))

            Log.d(TAG, "subtractFromAllowanceBalance: childId=$childId, amount=$amount")

            if (child.isAllowanceActive) {
                // 용돈 체계: allowanceBalance에서 차감
                val newBalance = child.allowanceBalance - amount
                childrenCollection.document(childId)
                    .update("allowanceBalance", newBalance)
                    .await()
            }
            // 적립 체계: totalExpense는 addChildExpense에서 처리되므로 별도 처리 불필요
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "subtractFromAllowanceBalance 실패", e)
            Result.failure(e)
        }
    }

    /**
     * 용돈 취소 (용돈 체계 -> 적립 체계로 되돌리기)
     * 주의: 적립금 정보가 초기화됩니다.
     * @param childId 자녀 ID
     */
    suspend fun cancelAllowance(childId: String): Result<Unit> {
        return try {
            Log.d(TAG, "cancelAllowance: childId=$childId")
            childrenCollection.document(childId)
                .update(
                    mapOf(
                        "allowanceStatus" to "saving",
                        "allowanceStartDate" to null,
                        "allowanceBalance" to 0L,
                        "preSavingsAmount" to 0L,
                        "preSavingsStartDate" to null,
                        "preSavingsEndDate" to null
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "cancelAllowance 실패", e)
            Result.failure(e)
        }
    }
}
