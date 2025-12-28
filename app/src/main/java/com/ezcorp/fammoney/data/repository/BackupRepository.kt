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
     * ?ì²´ ?°ì´??ë°±ì ?ì±
     */
    suspend fun createBackup(
        groupId: String,
        userId: String,
        userName: String
    ): Result<BackupData> {
        return try {
            // ê·¸ë£¹ ?´ë¦ ê°?¸ì¤ê¸?
            val groupDoc = groupsCollection.document(groupId).get().await()
            val groupName = groupDoc.getString("name") ?: "ê°ê³ë"

            // ?´ë¹ ê·¸ë£¹??ëª¨ë  ê±°ë ê°?¸ì¤ê¸?
            val transactionsSnapshot = transactionsCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val transactions = transactionsSnapshot.documents.mapNotNull { doc ->
                doc.data?.let {
                    TransactionBackup.fromTransaction(Transaction.fromMap(doc.id, it))
                }
            }

            // ?´ë¹ ê·¸ë£¹??ëª¨ë  ?ë? ?ë³´ ê°?¸ì¤ê¸?
            val childrenSnapshot = childrenCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val children = childrenSnapshot.documents.mapNotNull { doc ->
                doc.data?.let {
                    ChildBackup.fromChild(Child.fromMap(doc.id, it))
                }
            }

            // ?´ë¹ ê·¸ë£¹??ëª¨ë  ?ë? ?ì ê°?¸ì¤ê¸?
            val childIncomesSnapshot = childIncomesCollection
                .whereEqualTo("groupId", groupId)
                .get()
                .await()

            val childIncomes = childIncomesSnapshot.documents.mapNotNull { doc ->
                doc.data?.let {
                    ChildIncomeBackup.fromChildIncome(ChildIncome.fromMap(doc.id, it))
                }
            }

            // ?¤ì  ?ë³´
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

            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ë°±ì ?ì¼??Urië¡??
*/
    suspend fun saveBackupToUri(backupData: BackupData, uri: Uri): Result<Unit> {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                val jsonString = backupData.toJson().toString(2)
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uri?ì ë°±ì ?ì¼ ?½ê¸°
     */
    suspend fun readBackupFromUri(uri: Uri): Result<BackupData> {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("?ì¼???½ì ???ìµ?ë¤")

            val json = JSONObject(jsonString)
            val backupData = BackupData.fromJson(json)
            Result.success(backupData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ë°±ì ?°ì´??ë³µì (??ê·¸ë£¹???°ì´??ì¶ê")
     */
    suspend fun restoreBackup(
        backupData: BackupData,
        targetGroupId: String,
        targetUserId: String,
        targetUserName: String
    ): Result<RestoreResult> {
        return try {
            var transactionCount = 0
            var childCount = 0
            var childIncomeCount = 0

            // ê±°ë ë³µì
            for (transactionBackup in backupData.transactions) {
                val transaction = transactionBackup.toTransaction(
                    groupId = targetGroupId,
                    userId = targetUserId,
                    userName = targetUserName
                )
                transactionsCollection.add(transaction.toMap()).await()
                transactionCount++
            }

            // ?ë? ë³µì (ID ë§¤í ?ì)
            val childIdMapping = mutableMapOf<String, String>() // ê¸°ì¡´ ID -> ??ID
            for (childBackup in backupData.children) {
                val child = childBackup.toChild(groupId = targetGroupId)
                val docRef = childrenCollection.add(child.toMap()).await()
                childIdMapping[childBackup.id] = docRef.id
                childCount++
            }

            // ?ë? ?ì ë³µì (?ë? ID ë§¤í ?ì©)
            for (incomeBackup in backupData.childIncomes) {
                val newChildId = childIdMapping[incomeBackup.childId] ?: incomeBackup.childId
                val income = incomeBackup.copy(childId = newChildId).toChildIncome(groupId = targetGroupId)
                childIncomesCollection.add(income.toMap()).await()
                childIncomeCount++
            }

            // ?¤ì  ë³µì
            userPreferences.saveHighAmountThreshold(backupData.settings.highAmountThreshold)

            if (backupData.settings.selectedBankIds.isNotEmpty()) {
                firestore.collection("users").document(targetUserId)
                    .update("selectedBankIds", backupData.settings.selectedBankIds)
                    .await()
            }

            Result.success(
                RestoreResult(
                    transactionCount = transactionCount,
                    childCount = childCount,
                    childIncomeCount = childIncomeCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ë°±ì ?ì¼ ?´ë¦ ?ì±
     */
    fun generateBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA)
        val dateStr = sdf.format(Date())
        return "selectmoney_backup_$dateStr.json"
    }
}

data class RestoreResult(
    val transactionCount: Int,
    val childCount: Int,
    val childIncomeCount: Int
) {
    fun getTotalCount(): Int = transactionCount + childCount + childIncomeCount
}
