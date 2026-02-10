package com.ezcorp.fammoney.data.repository

import android.content.Context
import android.net.Uri
import com.ezcorp.fammoney.data.model.BackupData
import com.ezcorp.fammoney.data.model.Child
import com.ezcorp.fammoney.data.model.ChildBackup
import com.ezcorp.fammoney.data.model.ChildIncome
import com.ezcorp.fammoney.data.model.ChildIncomeBackup
import com.ezcorp.fammoney.data.model.SettingsBackup
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionBackup
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackupRepo"

@Singleton
class BackupRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userPreferences: UserPreferences,
    @ApplicationContext private val context: Context
) {
    private val transactionsCollection = firestore.collection("transactions")
    private val childrenCollection = firestore.collection("children")
    private val childIncomesCollection = firestore.collection("childIncomes")
    private val groupsCollection = firestore.collection("groups")

    /**
     * 전체 데이터 백업 생성
     */
    suspend fun createBackup(
        groupId: String,
        userId: String,
        userName: String
    ): Result<BackupData> {
        AppLogger.i(TAG, "createBackup: groupId=$groupId, userId=$userId, userName=$userName")
        return try {
            // 그룹 이름 가져오기
            val groupDoc = groupsCollection.document(groupId).get().await()
            val groupName = groupDoc.getString("name") ?: "가계부"
            AppLogger.d(TAG, "createBackup: groupName=$groupName")

            // 해당 그룹의 모든 거래 가져오기
            val transactionsSnapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val transactions = transactionsSnapshot.documents.mapNotNull { doc ->
                doc.data?.let {
                    TransactionBackup.fromTransaction(Transaction.fromMap(doc.id, it))
                }
            }
            AppLogger.d(TAG, "createBackup: fetched ${transactions.size} transactions")

            // 해당 그룹의 모든 자녀 정보 가져오기
            val childrenSnapshot = childrenCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val children = childrenSnapshot.documents.mapNotNull { doc ->
                doc.data?.let {
                    ChildBackup.fromChild(Child.fromMap(doc.id, it))
                }
            }
            AppLogger.d(TAG, "createBackup: fetched ${children.size} children")

            // 해당 그룹의 모든 자녀 수입 가져오기
            val childIncomesSnapshot = childIncomesCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val childIncomes = childIncomesSnapshot.documents.mapNotNull { doc ->
                doc.data?.let {
                    ChildIncomeBackup.fromChildIncome(ChildIncome.fromMap(doc.id, it))
                }
            }
            AppLogger.d(TAG, "createBackup: fetched ${childIncomes.size} childIncomes")

            // 설정 정보
            val highAmountThreshold = userPreferences.getHighAmountThreshold()
            val userDoc = firestore.collection("users").document(userId).get().await()
            @Suppress("UNCHECKED_CAST")
            val selectedBankIds = userDoc.get("selectedBankIds") as? List<String> ?: emptyList()

            val settings = SettingsBackup(
                highAmountThreshold = highAmountThreshold,
                selectedBankIds = selectedBankIds
            )

            val backupData = BackupData(
                groupId = groupId,
                groupName = groupName,
                userId = userId,
                userName = userName,
                transactions = transactions,
                children = children,
                childIncomes = childIncomes,
                settings = settings
            )

            AppLogger.i(TAG, "createBackup: success - transactions=${transactions.size}, children=${children.size}, childIncomes=${childIncomes.size}")
            Result.success(backupData)
        } catch (e: Exception) {
            AppLogger.e(TAG, "createBackup: failed for groupId=$groupId", e)
            Result.failure(e)
        }
    }

    /**
     * 백업 파일을 Uri로 저장
     */
    suspend fun saveBackupToUri(backupData: BackupData, uri: Uri): Result<Unit> {
        AppLogger.d(TAG, "saveBackupToUri: uri=$uri")
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val jsonString = backupData.toJson().toString(2)
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            AppLogger.i(TAG, "saveBackupToUri: success")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveBackupToUri: failed", e)
            Result.failure(e)
        }
    }

    /**
     * Uri에서 백업 파일 읽기
     */
    suspend fun readBackupFromUri(uri: Uri): Result<BackupData> {
        AppLogger.d(TAG, "readBackupFromUri: uri=$uri")
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("파일을 읽을 수 없습니다")

            val json = JSONObject(jsonString)
            val backupData = BackupData.fromJson(json)
            AppLogger.i(TAG, "readBackupFromUri: success - parsed backup data")
            Result.success(backupData)
        } catch (e: Exception) {
            AppLogger.e(TAG, "readBackupFromUri: failed", e)
            Result.failure(e)
        }
    }

    /**
     * 백업 데이터 복원 (현 그룹에 데이터 추가)
     */
    suspend fun restoreBackup(
        backupData: BackupData,
        targetGroupId: String,
        targetUserId: String,
        targetUserName: String
    ): Result<RestoreResult> {
        AppLogger.i(TAG, "restoreBackup: targetGroupId=$targetGroupId, targetUserId=$targetUserId")
        return try {
            var transactionCount = 0
            var childCount = 0
            var childIncomeCount = 0

            // 거래 복원
            for (transactionBackup in backupData.transactions) {
                val transaction = transactionBackup.toTransaction(
                    groupId = targetGroupId,
                    userId = targetUserId,
                    userName = targetUserName
                )
                transactionsCollection.add(transaction.toMap()).await()
                transactionCount++
            }
            AppLogger.d(TAG, "restoreBackup: restored $transactionCount transactions")

            // 자녀 복원 (ID 매핑 필요)
            val childIdMapping = mutableMapOf<String, String>() // 기존 ID -> 새 ID
            for (childBackup in backupData.children) {
                val child = childBackup.toChild(groupId = targetGroupId)
                val docRef = childrenCollection.add(child.toMap()).await()
                childIdMapping[childBackup.id] = docRef.id
                childCount++
            }
            AppLogger.d(TAG, "restoreBackup: restored $childCount children")

            // 자녀 수입 복원 (자녀 ID 매핑 사용)
            for (incomeBackup in backupData.childIncomes) {
                val newChildId = childIdMapping[incomeBackup.childId] ?: incomeBackup.childId
                val income = incomeBackup.copy(childId = newChildId).toChildIncome(groupId = targetGroupId)
                childIncomesCollection.add(income.toMap()).await()
                childIncomeCount++
            }
            AppLogger.d(TAG, "restoreBackup: restored $childIncomeCount childIncomes")

            // 설정 복원
            userPreferences.saveHighAmountThreshold(backupData.settings.highAmountThreshold)

            if (backupData.settings.selectedBankIds.isNotEmpty()) {
                firestore.collection("users").document(targetUserId)
                    .update("selectedBankIds", backupData.settings.selectedBankIds)
                    .await()
            }
            AppLogger.d(TAG, "restoreBackup: settings restored")

            val result = RestoreResult(
                transactionCount = transactionCount,
                childCount = childCount,
                childIncomeCount = childIncomeCount
            )
            AppLogger.i(TAG, "restoreBackup: success - total=${result.getTotalCount()} items restored")
            Result.success(result)
        } catch (e: Exception) {
            AppLogger.e(TAG, "restoreBackup: failed for targetGroupId=$targetGroupId", e)
            Result.failure(e)
        }
    }

    /**
     * 백업 파일 이름 생성
     */
    fun generateBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
        val dateStr = sdf.format(Date())
        val fileName = "selectmoney_backup_$dateStr.json"
        AppLogger.d(TAG, "generateBackupFileName: $fileName")
        return fileName
    }
}

data class RestoreResult(
    val transactionCount: Int,
    val childCount: Int,
    val childIncomeCount: Int
) {
    fun getTotalCount(): Int = transactionCount + childCount + childIncomeCount
}
