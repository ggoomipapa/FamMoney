package com.ezcorp.fammoney.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ezcorp.fammoney.R
import com.ezcorp.fammoney.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FCMService : FirebaseMessagingService() {

    @Inject
    lateinit var userPreferences: UserPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            userPreferences.saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val type = data["type"]

        when (type) {
            "transaction" -> handleTransactionNotification(data)
            "group_join" -> handleGroupJoinNotification(data)
            else -> handleDefaultNotification(message.notification)
        }
    }

    private fun handleTransactionNotification(data: Map<String, String>) {
        val userName = data["userName"] ?: "멤버"
        val transactionType = data["transactionType"]
        val amount = data["amount"] ?: "0"
        val description = data["description"] ?: ""

        val typeText = if (transactionType == "INCOME") "수입" else "지출"
        val title = "$userName 님의 $typeText"
        val body = "${amount}원 - $description"

        showNotification(
            channelId = CHANNEL_GROUP_TRANSACTION,
            notificationId = System.currentTimeMillis().toInt(),
            title = title,
            body = body
        )
    }

    private fun handleGroupJoinNotification(data: Map<String, String>) {
        val userName = data["userName"] ?: "새 멤버"
        val groupName = data["groupName"] ?: "가계부"

        showNotification(
            channelId = CHANNEL_GROUP_UPDATE,
            notificationId = System.currentTimeMillis().toInt(),
            title = "새 멤버 참여",
            body = "$userName 님이 '$groupName' 가계부에 참여했습니다."
        )
    }

    private fun handleDefaultNotification(notification: RemoteMessage.Notification?) {
        notification?.let {
            showNotification(
                channelId = CHANNEL_DEFAULT,
                notificationId = System.currentTimeMillis().toInt(),
                title = it.title ?: "셀렉트머니",
                body = it.body ?: ""
            )
        }
    }

    private fun showNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val defaultChannel = NotificationChannel(
                CHANNEL_DEFAULT,
                "일반 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            val transactionChannel = NotificationChannel(
                CHANNEL_GROUP_TRANSACTION,
                "그룹 거래 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "그룹 멤버의 거래 내역 알림"
            }

            val updateChannel = NotificationChannel(
                CHANNEL_GROUP_UPDATE,
                "그룹 업데이트",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "그룹 멤버 참여/탈퇴 알림"
            }

            notificationManager.createNotificationChannels(
                listOf(defaultChannel, transactionChannel, updateChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_DEFAULT = "default_channel"
        const val CHANNEL_GROUP_TRANSACTION = "group_transaction_channel"
        const val CHANNEL_GROUP_UPDATE = "group_update_channel"
    }
}
