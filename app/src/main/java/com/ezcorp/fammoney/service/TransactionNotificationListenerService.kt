package com.ezcorp.fammoney.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ezcorp.fammoney.R
import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.data.model.InputSource
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.ui.MainActivity
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class TransactionNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var duplicateDetectionService: DuplicateDetectionService

    @Inject
    lateinit var savingsAutoDepositService: SavingsAutoDepositService

    @Inject
    lateinit var aiFeatureService: AIFeatureService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parser = NotificationParser()

    private val allBanks = BankConfig.getDefaultBanks()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val notificationText = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (notificationText.isBlank()) return

        serviceScope.launch {
            processNotification(packageName, notificationText)
        }
    }

    private suspend fun processNotification(packageName: String, notificationText: String) {
        try {
            val userId = userPreferences.getUserId() ?: return
            val user = userRepository.getUserFlow(userId).first() ?: return

            val selectedBanks = allBanks.filter { bank ->
                user.selectedBankIds.contains(bank.bankId)
            }

            if (selectedBanks.isEmpty()) return

            val matchingBank = selectedBanks.find { bank ->
                bank.packageNames.contains(packageName)
            } ?: return

            val parsed = parser.parse(packageName, notificationText, selectedBanks) ?: return

            // ?¬ì©?ê? ?¤ì ??ê³ ì¡ ê±°ë ê¸°ì? ê¸ì¡ ê°?¸ì¤ê¸?
            val highAmountThreshold = userPreferences.getHighAmountThreshold()

            // AI ?ë ë¶ë¥ (ì»¤ë¥??AI êµ¬ë???ì©)
            var merchantName = parsed.merchantName
            var category = ""

            // AIë¡?ê°ë§¹ì ëª?ì¶ì¶ ?ë (?ê·?ì¼ë¡?ëª?ì°¾ì? ê²½ì°)
            if (merchantName.isBlank()) {
                val extractResult = aiFeatureService.extractMerchantName(notificationText)
                extractResult.onSuccess { name ->
                    if (name.isNotBlank()) {
                        merchantName = name
                    }
                }
            }

            // AI ?ë ì¹´íê³ ë¦¬ ë¶ë¥ (ì§ì¶?ê±°ë??ê²½ì°)
            if (parsed.type == TransactionType.EXPENSE && merchantName.isNotBlank()) {
                val categoryResult = aiFeatureService.autoCategorize(
                    merchantName = merchantName,
                    amount = parsed.amount,
                    description = parsed.description
                )
                categoryResult.onSuccess { result ->
                    if (result.confidence >= 0.5f) {
                        category = result.category
                    }
                }
            }

            val transaction = Transaction(
                groupId = user.groupId,
                userId = userId,
                userName = user.name,
                type = parsed.type,
                amount = parsed.amount,
                bankId = parsed.bankConfig.bankId,
                bankName = parsed.bankConfig.displayName,
                description = parsed.description,
                merchantName = merchantName,
                category = category,
                source = InputSource.NOTIFICATION,
                originalText = parsed.originalText,
                transactionDate = Timestamp(Date()),
                isConfirmed = parsed.amount <= highAmountThreshold
            )

            // ë¨¼ì? ê±°ëë¥??
val savedTransaction = transactionRepository.addTransactionAndReturn(transaction)

            // ì¤ë³µ ê°ì? ?ì¸
            val duplicateResult = duplicateDetectionService.checkAndHandleDuplicate(savedTransaction)

            when (duplicateResult) {
                is DuplicateCheckResult.DuplicateDetected -> {
                    // ì¤ë³µ??ê°ì???- ?¬ì©?ìê²??ë¦¼
                showDuplicateNotification(savedTransaction)
                }
                is DuplicateCheckResult.SkipSecond -> {
                    // ê·ì¹???°ë¼ ??ë²ì§¸ ê±°ë ?? 
                transactionRepository.deleteTransaction(savedTransaction.id)
                }
                is DuplicateCheckResult.DeleteBoth -> {
                    // ê·ì¹???°ë¼ ??ê±°ë???? 
                transactionRepository.deleteTransaction(savedTransaction.id)
                }
                else -> {
                    // NoDuplicate, KeepBoth, KeepSecond - ê±°ë ? ì"
                if (parsed.amount > highAmountThreshold) {
                        showHighAmountNotification(savedTransaction)
                    }

                    // ?ê¸ ê±°ë??ê²½ì° ëª©í ?ì¶??ë ?°ë ì²ë¦¬
                if (parsed.type == TransactionType.INCOME) {
                        processAutoDeposit(
                            amount = parsed.amount,
                            senderName = parsed.senderName,
                            accountNumber = parsed.accountNumber,
                            originalText = parsed.originalText,
                            groupId = user.groupId,
                            bankName = parsed.bankConfig.displayName
                        )
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showHighAmountNotification(transaction: Transaction) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_PENDING_TRANSACTION_ID, transaction.id)
            putExtra(EXTRA_PENDING_AMOUNT, transaction.amount)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = String.format("%,d", transaction.amount)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ê³ ì¡ ê±°ë ê°ì")
            .setContentText("${formattedAmount}?ì´ ê°ì??ì?µë?? ?ì¸???ì?©ë")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(HIGH_AMOUNT_NOTIFICATION_ID, notification)
    }

    private fun showDuplicateNotification(transaction: Transaction) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SHOW_DUPLICATES, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = String.format("%,d", transaction.amount)

        val notification = NotificationCompat.Builder(this, DUPLICATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ì¤ë³µ ê±°ë ê°ì")
            .setContentText("${formattedAmount}??ê±°ëê° ì¤ë³µ?¼ë¡ ê°ì??ì?µë?? ?ì¸?´ì£¼?¸ì.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("?ì¼??ê¸ì¡??ê±°ëê° ì¹´ë? ??ì???ì??ê°ì??ì?µë?? ?±ì???´ë¤ ê±°ëë¥?? ì?? ì? ? í?´ì£¼?¸ì."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(DUPLICATE_NOTIFICATION_ID, notification)
    }

    /**
     * ëª©í ?ì¶??ë ?ê¸ ì²ë¦¬
     */
    private suspend fun processAutoDeposit(
        amount: Long,
        senderName: String,
        accountNumber: String,
        originalText: String,
        groupId: String,
        bankName: String
    ) {
        try {
            // ê·¸ë£¹ ë©¤ë² ì¡°í
            val groupMembers = userRepository.getGroupMembersFlow(groupId).first()

            // ?ë ?ê¸ ì²ë¦¬
            val results = savingsAutoDepositService.processDepositNotification(
                amount = amount,
                senderName = senderName,
                accountNumber = accountNumber,
                originalText = originalText,
                groupId = groupId,
                groupMembers = groupMembers
            )

            for (result in results) {
                when (result) {
                    is SavingsAutoDepositService.DepositProcessResult.AutoProcessed -> {
                        // ?ë?¼ë¡ ?ì¶ì ë°ì
                savingsAutoDepositService.saveAutoContribution(result.contribution)
                        showSavingsAutoDepositNotification(
                            goalName = result.savingsGoal.name,
                            amount = result.contribution.amount,
                            userName = result.matchedUser.name
                        )
                    }
                    is SavingsAutoDepositService.DepositProcessResult.NeedsConfirmation -> {
                        // ?¬ì©???ì¸ ?ì ?ë¦¼
                showSavingsConfirmationNotification(
                            goalName = result.savingsGoal.name,
                            amount = result.amount,
                            detectedName = result.detectedSenderName,
                            candidateCount = result.candidates.size
                        )
                    }
                    is SavingsAutoDepositService.DepositProcessResult.NeedsManualInput -> {
                        // ?ë ?ë ¥ ?ì ?ë¦¼
                showSavingsManualInputNotification(
                            goalName = result.savingsGoal.name,
                            amount = result.amount ?: 0L,
                            reason = result.reason
                        )
                    }
                    is SavingsAutoDepositService.DepositProcessResult.NotApplicable -> {
                        // ?°ë??ëª©í ?ì - ë¬´ì
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSavingsAutoDepositNotification(
        goalName: String,
        amount: Long,
        userName: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SHOW_SAVINGS_GOAL, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = String.format("%,d", amount)

        val notification = NotificationCompat.Builder(this, SAVINGS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("?ì¶??ë ë°ì")
            .setContentText("$goalName ??${userName}??${formattedAmount}???ì¶??ë£")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SAVINGS_NOTIFICATION_ID, notification)
    }

    private fun showSavingsConfirmationNotification(
        goalName: String,
        amount: Long,
        detectedName: String,
        candidateCount: Int
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SHOW_SAVINGS_GOAL, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedAmount = String.format("%,d", amount)

        val notification = NotificationCompat.Builder(this, SAVINGS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("?ì¶??ì¸ ?ì")
            .setContentText("$goalName: '$detectedName' ${formattedAmount}???ê¸ - ë©¤ë² ?ì¸ ?ì")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("'$detectedName' ?ì ${formattedAmount}???ê¸??ê°ì??ì?µë?? ${candidateCount}ëªì ?ë³´ ì¤??ê¸?ë? ? í?´ì£¼?¸ì."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SAVINGS_CONFIRM_NOTIFICATION_ID, notification)
    }

    private fun showSavingsManualInputNotification(
        goalName: String,
        amount: Long,
        reason: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_SHOW_SAVINGS_GOAL, true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountText = amount?.let { String.format("%,d", it) } ?: ""

        val notification = NotificationCompat.Builder(this, SAVINGS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("?ì¶??ë ?ì¸ ?ì")
            .setContentText("$goalName: ${amountText}?ê¸ - $reason")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SAVINGS_MANUAL_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // ê³ ì¡ ê±°ë ?ë¦¼ ì±ë
            val highAmountChannel = NotificationChannel(
                CHANNEL_ID,
                "ê³ ì¡ ê±°ë ?ë¦¼",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "?¤ì  ê¸ì¡ ?´ì ê±°ë ???ì¸ ?ë¦¼"
            }
            notificationManager.createNotificationChannel(highAmountChannel)

            // ì¤ë³µ ê±°ë ?ë¦¼ ì±ë
            val duplicateChannel = NotificationChannel(
                DUPLICATE_CHANNEL_ID,
                "ì¤ë³µ ê±°ë ?ë¦¼",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "ì¹´ë? ??ì???ì??ê°ì???ì¤ë³µ ê±°ë ?ë¦¼"
            }
            notificationManager.createNotificationChannel(duplicateChannel)

            // ëª©í ?ì¶??ë¦¼ ì±ë
            val savingsChannel = NotificationChannel(
                SAVINGS_CHANNEL_ID,
                "ëª©í ?ì¶??ë¦¼",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "ëª©í ?ì¶??ë ?ê¸ ?°ë ?ë¦¼"
            }
            notificationManager.createNotificationChannel(savingsChannel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "high_amount_channel"
        private const val DUPLICATE_CHANNEL_ID = "duplicate_channel"
        private const val SAVINGS_CHANNEL_ID = "savings_channel"
        private const val HIGH_AMOUNT_NOTIFICATION_ID = 1001
        private const val DUPLICATE_NOTIFICATION_ID = 1002
        private const val SAVINGS_NOTIFICATION_ID = 1003
        private const val SAVINGS_CONFIRM_NOTIFICATION_ID = 1004
        private const val SAVINGS_MANUAL_NOTIFICATION_ID = 1005
        const val EXTRA_PENDING_TRANSACTION_ID = "pending_transaction_id"
        const val EXTRA_PENDING_AMOUNT = "pending_amount"
        const val EXTRA_SHOW_DUPLICATES = "show_duplicates"
        const val EXTRA_SHOW_SAVINGS_GOAL = "show_savings_goal"
    }
}

