package com.ezcorp.fammoney.data.model

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 백업 데이터 구조
 */
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val groupId: String = "",
    val groupName: String = "",
    val userId: String = "",
    val userName: String = "",
    val transactions: List<TransactionBackup> = emptyList(),
    val children: List<ChildBackup> = emptyList(),
    val childIncomes: List<ChildIncomeBackup> = emptyList(),
    val settings: SettingsBackup = SettingsBackup()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("createdAt", createdAt)
            put("groupId", groupId)
            put("groupName", groupName)
            put("userId", userId)
            put("userName", userName)
            put("transactions", JSONArray(transactions.map { it.toJson() }))
            put("children", JSONArray(children.map { it.toJson() }))
            put("childIncomes", JSONArray(childIncomes.map { it.toJson() }))
            put("settings", settings.toJson())
        }
    }

    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.KOREA)
        return sdf.format(Date(createdAt))
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun fromJson(json: JSONObject): BackupData {
            val transactionsArray = json.optJSONArray("transactions") ?: JSONArray()
            val childrenArray = json.optJSONArray("children") ?: JSONArray()
            val childIncomesArray = json.optJSONArray("childIncomes") ?: JSONArray()

            return BackupData(
                version = json.optInt("version", 1),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                groupId = json.optString("groupId", ""),
                groupName = json.optString("groupName", ""),
                userId = json.optString("userId", ""),
                userName = json.optString("userName", ""),
                transactions = (0 until transactionsArray.length()).map {
                    TransactionBackup.fromJson(transactionsArray.getJSONObject(it))
                },
                children = (0 until childrenArray.length()).map {
                    ChildBackup.fromJson(childrenArray.getJSONObject(it))
                },
                childIncomes = (0 until childIncomesArray.length()).map {
                    ChildIncomeBackup.fromJson(childIncomesArray.getJSONObject(it))
                },
                settings = SettingsBackup.fromJson(json.optJSONObject("settings") ?: JSONObject())
            )
        }
    }
}

/**
 * 거래 백업 데이터 */
data class TransactionBackup(
    val id: String = "",
    val type: String = "EXPENSE",
    val amount: Long = 0,
    val bankId: String = "",
    val bankName: String = "",
    val description: String = "",
    val category: String = "",
    val incomeSubType: String = "",
    val expenseSubType: String = "",
    val merchant: String = "",
    val merchantName: String = "",
    val memo: String = "",
    val source: String = "NOTIFICATION",
    val originalText: String = "",
    val createdAt: Long? = null,
    val transactionDate: Long? = null,
    val isConfirmed: Boolean = true
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", type)
            put("amount", amount)
            put("bankId", bankId)
            put("bankName", bankName)
            put("description", description)
            put("category", category)
            put("incomeSubType", incomeSubType)
            put("expenseSubType", expenseSubType)
            put("merchant", merchant)
            put("merchantName", merchantName)
            put("memo", memo)
            put("source", source)
            put("originalText", originalText)
            createdAt?.let { put("createdAt", it) }
            transactionDate?.let { put("transactionDate", it) }
            put("isConfirmed", isConfirmed)
        }
    }

    fun toTransaction(groupId: String, userId: String, userName: String): Transaction {
        return Transaction(
            id = id,
            groupId = groupId,
            userId = userId,
            userName = userName,
            type = TransactionType.valueOf(type),
            amount = amount,
            bankId = bankId,
            bankName = bankName,
            description = description,
            category = category,
            incomeSubType = incomeSubType,
            expenseSubType = expenseSubType,
            merchant = merchant,
            merchantName = merchantName,
            memo = memo,
            source = InputSource.valueOf(source),
            originalText = originalText,
            createdAt = createdAt?.let { Timestamp(Date(it)) },
            transactionDate = transactionDate?.let { Timestamp(Date(it)) },
            isConfirmed = isConfirmed
        )
    }

    companion object {
        fun fromTransaction(transaction: Transaction): TransactionBackup {
            return TransactionBackup(
                id = transaction.id,
                type = transaction.type.name,
                amount = transaction.amount,
                bankId = transaction.bankId,
                bankName = transaction.bankName,
                description = transaction.description,
                category = transaction.category,
                incomeSubType = transaction.incomeSubType,
                expenseSubType = transaction.expenseSubType,
                merchant = transaction.merchant,
                merchantName = transaction.merchantName,
                memo = transaction.memo,
                source = transaction.source.name,
                originalText = transaction.originalText,
                createdAt = transaction.createdAt?.toDate()?.time,
                transactionDate = transaction.transactionDate?.toDate()?.time,
                isConfirmed = transaction.isConfirmed
            )
        }

        fun fromJson(json: JSONObject): TransactionBackup {
            return TransactionBackup(
                id = json.optString("id", ""),
                type = json.optString("type", "EXPENSE"),
                amount = json.optLong("amount", 0),
                bankId = json.optString("bankId", ""),
                bankName = json.optString("bankName", ""),
                description = json.optString("description", ""),
                category = json.optString("category", ""),
                incomeSubType = json.optString("incomeSubType", ""),
                expenseSubType = json.optString("expenseSubType", ""),
                merchant = json.optString("merchant", ""),
                merchantName = json.optString("merchantName", ""),
                memo = json.optString("memo", ""),
                source = json.optString("source", "NOTIFICATION"),
                originalText = json.optString("originalText", ""),
                createdAt = if (json.has("createdAt")) json.optLong("createdAt") else null,
                transactionDate = if (json.has("transactionDate")) json.optLong("transactionDate") else null,
                isConfirmed = json.optBoolean("isConfirmed", true)
            )
        }
    }
}

/**
 * 자녀 백업 데이터 */
data class ChildBackup(
    val id: String = "",
    val name: String = "",
    val birthDate: Long? = null,
    val totalIncome: Long = 0,
    val createdAt: Long? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            birthDate?.let { put("birthDate", it) }
            put("totalIncome", totalIncome)
            createdAt?.let { put("createdAt", it) }
        }
    }

    fun toChild(groupId: String): Child {
        return Child(
            id = id,
            groupId = groupId,
            name = name,
            birthDate = birthDate?.let { Timestamp(Date(it)) },
            totalIncome = totalIncome,
            createdAt = createdAt?.let { Timestamp(Date(it)) }
        )
    }

    companion object {
        fun fromChild(child: Child): ChildBackup {
            return ChildBackup(
                id = child.id,
                name = child.name,
                birthDate = child.birthDate?.toDate()?.time,
                totalIncome = child.totalIncome,
                createdAt = child.createdAt?.toDate()?.time
            )
        }

        fun fromJson(json: JSONObject): ChildBackup {
            return ChildBackup(
                id = json.optString("id", ""),
                name = json.optString("name", ""),
                birthDate = if (json.has("birthDate")) json.optLong("birthDate") else null,
                totalIncome = json.optLong("totalIncome", 0),
                createdAt = if (json.has("createdAt")) json.optLong("createdAt") else null
            )
        }
    }
}

/**
 * 자녀 수입 백업 데이터 */
data class ChildIncomeBackup(
    val id: String = "",
    val childId: String = "",
    val childName: String = "",
    val amount: Long = 0,
    val giverType: String = "OTHER",
    val giverName: String = "",
    val memo: String = "",
    val recordedByUserId: String = "",
    val recordedByUserName: String = "",
    val createdAt: Long? = null,
    val incomeDate: Long? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("childId", childId)
            put("childName", childName)
            put("amount", amount)
            put("giverType", giverType)
            put("giverName", giverName)
            put("memo", memo)
            put("recordedByUserId", recordedByUserId)
            put("recordedByUserName", recordedByUserName)
            createdAt?.let { put("createdAt", it) }
            incomeDate?.let { put("incomeDate", it) }
        }
    }

    fun toChildIncome(groupId: String): ChildIncome {
        return ChildIncome(
            id = id,
            groupId = groupId,
            childId = childId,
            childName = childName,
            amount = amount,
            giverType = try {
                IncomeGiverType.valueOf(giverType)
            } catch (e: Exception) {
                IncomeGiverType.OTHER
            },
            giverName = giverName,
            memo = memo,
            recordedByUserId = recordedByUserId,
            recordedByUserName = recordedByUserName,
            createdAt = createdAt?.let { Timestamp(Date(it)) },
            incomeDate = incomeDate?.let { Timestamp(Date(it)) }
        )
    }

    companion object {
        fun fromChildIncome(income: ChildIncome): ChildIncomeBackup {
            return ChildIncomeBackup(
                id = income.id,
                childId = income.childId,
                childName = income.childName,
                amount = income.amount,
                giverType = income.giverType.name,
                giverName = income.giverName,
                memo = income.memo,
                recordedByUserId = income.recordedByUserId,
                recordedByUserName = income.recordedByUserName,
                createdAt = income.createdAt?.toDate()?.time,
                incomeDate = income.incomeDate?.toDate()?.time
            )
        }

        fun fromJson(json: JSONObject): ChildIncomeBackup {
            return ChildIncomeBackup(
                id = json.optString("id", ""),
                childId = json.optString("childId", ""),
                childName = json.optString("childName", ""),
                amount = json.optLong("amount", 0),
                giverType = json.optString("giverType", "OTHER"),
                giverName = json.optString("giverName", ""),
                memo = json.optString("memo", ""),
                recordedByUserId = json.optString("recordedByUserId", ""),
                recordedByUserName = json.optString("recordedByUserName", ""),
                createdAt = if (json.has("createdAt")) json.optLong("createdAt") else null,
                incomeDate = if (json.has("incomeDate")) json.optLong("incomeDate") else null
            )
        }
    }
}

/**
 * 설정 백업 데이터 */
data class SettingsBackup(
    val highAmountThreshold: Long = 1_000_000L,
    val selectedBankIds: List<String> = emptyList()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("highAmountThreshold", highAmountThreshold)
            put("selectedBankIds", JSONArray(selectedBankIds))
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SettingsBackup {
            val bankIdsArray = json.optJSONArray("selectedBankIds") ?: JSONArray()
            return SettingsBackup(
                highAmountThreshold = json.optLong("highAmountThreshold", 1_000_000L),
                selectedBankIds = (0 until bankIdsArray.length()).map {
                    bankIdsArray.getString(it)
                }
            )
        }
    }
}
