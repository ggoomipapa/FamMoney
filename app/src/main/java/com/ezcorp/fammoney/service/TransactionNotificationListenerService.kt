package com.ezcorp.fammoney.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.ezcorp.fammoney.util.AppLogger
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

    @Inject
    lateinit var parser: NotificationParser // Inject the parser

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val allBanks = BankConfig.getDefaultBanks()

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "========== TransactionNotificationListenerService 생성됨 ==========")
        createNotificationChannel()
    }

    /** 등록된 모든 은행의 패키지명 (빠른 필터링용) */
    private val allBankPackages: Set<String> by lazy {
        allBanks.flatMap { it.packageNames }.toSet()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName

        // 등록된 은행 패키지가 아니면 즉시 무시 (Firestore 호출 방지)
        if (packageName !in allBankPackages) return

        val extras = sbn.notification.extras

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        AppLogger.d(TAG, "===== 알림 원본 필드 =====")
        AppLogger.d(TAG, "  패키지: $packageName")
        AppLogger.d(TAG, "  title: $title")
        AppLogger.d(TAG, "  text: $text")
        AppLogger.d(TAG, "  bigText: $bigText")

        // 카카오톡 알림: title이 금융기관 이름일 때만 파싱 (일반 채팅 무시)
        if (packageName == "com.kakao.talk") {
            val isFinancialSender = BankConfig.KAKAO_TALK_FINANCIAL_SENDERS.any { sender ->
                title.contains(sender)
            }
            if (!isFinancialSender) {
                AppLogger.d(TAG, "카카오톡 일반 채팅 무시 - title: '$title'")
                return
            }
            AppLogger.d(TAG, "카카오톡 금융 알림톡 감지 - title: '$title'")
        }

        val notificationText = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (notificationText.isBlank()) {
            AppLogger.d(TAG, "알림 텍스트가 비어있어 무시함 - 패키지: $packageName")
            return
        }

        serviceScope.launch {
            processNotification(packageName, notificationText)
        }
    }

    private suspend fun processNotification(packageName: String, notificationText: String) {
        try {
            AppLogger.d(TAG, "알림 수신 - 패키지: $packageName")
            AppLogger.d(TAG, "알림 텍스트: $notificationText")

            val userId = userPreferences.getUserId()
            if (userId == null) {
                AppLogger.d(TAG, "userId가 null - 로그인 필요")
                return
            }

            val user = userRepository.getUserFlow(userId).first()
            if (user == null) {
                AppLogger.d(TAG, "user가 null - 사용자 정보 없음")
                return
            }

            AppLogger.d(TAG, "선택된 은행 ID 목록: ${user.selectedBankIds}")

            val selectedBanks = allBanks.filter { bank ->
                user.selectedBankIds.contains(bank.bankId)
            }

            AppLogger.d(TAG, "선택된 은행 수: ${selectedBanks.size}")
            selectedBanks.forEach { bank ->
                AppLogger.d(TAG, "  - ${bank.displayName}: ${bank.packageNames}")
            }

            if (selectedBanks.isEmpty()) {
                AppLogger.d(TAG, "선택된 은행 없음 - 파싱 중단")
                return
            }

            val matchingBank = selectedBanks.find { bank ->
                bank.packageNames.contains(packageName)
            }

            if (matchingBank == null) {
                AppLogger.d(TAG, "패키지명 '$packageName'과 매칭되는 은행 없음")
                AppLogger.d(TAG, "등록된 모든 은행의 패키지명:")
                allBanks.forEach { bank ->
                    if (bank.packageNames.any { it.contains("kb", ignoreCase = true) }) {
                        AppLogger.d(TAG, "  - ${bank.displayName}: ${bank.packageNames}")
                    }
                }
                return
            }

            AppLogger.d(TAG, "매칭된 은행: ${matchingBank.displayName}")

            val parsed = parser.parse(packageName, notificationText, selectedBanks)
            if (parsed == null) {
                AppLogger.d(TAG, "파싱 실패 - 금액 정규식 또는 형식 불일치")
                return
            }

            AppLogger.d(TAG, "파싱 성공 - 금액: ${parsed.amount}, 유형: ${parsed.type}, 가맹점: ${parsed.merchantName}, 송금자: ${parsed.senderName}")

            // 사용자가 설정한 고액 거래 기준 금액 가져오기
            val highAmountThreshold = userPreferences.getHighAmountThreshold()

            // 거래 상대방 이름 결정
            // - 입금(INCOME): 보낸 사람(senderName) 사용
            // - 출금(EXPENSE): 받는 사람/사용처(merchantName) 사용
            var displayName = if (parsed.type == TransactionType.INCOME) {
                parsed.senderName.ifBlank { parsed.merchantName }
            } else {
                parsed.merchantName.ifBlank { parsed.senderName }
            }
            var category = ""

            // 로컬 서비스로 상대방 이름 추출 시도 (파싱에서 못 찾은 경우)
            if (displayName.isBlank()) {
                AppLogger.d(TAG, "파싱에서 상대방 이름 미발견 - AI 추출 시도")
                val extractResult = aiFeatureService.extractMerchantName(notificationText)
                extractResult.onSuccess { name ->
                    AppLogger.d(TAG, "AI 상대방 이름 추출 성공: '$name'")
                    if (name.isNotBlank()) {
                        displayName = name
                    }
                }
                extractResult.onFailure { e ->
                    AppLogger.w(TAG, "AI 상대방 이름 추출 실패: ${e.message}")
                }
            } else {
                AppLogger.d(TAG, "파싱에서 상대방 이름 확보: '$displayName'")
            }

            // 자동 카테고리 분류 (지출 거래인 경우)
            if (parsed.type == TransactionType.EXPENSE && displayName.isNotBlank()) {
                AppLogger.d(TAG, "AI 자동 카테고리 분류 시도 - 가맹점: '$displayName', 금액: ${parsed.amount}")
                val categoryResult = aiFeatureService.autoCategorize(
                    merchantName = displayName,
                    amount = parsed.amount,
                    description = parsed.description
                )
                categoryResult.onSuccess { result ->
                    AppLogger.d(TAG, "AI 카테고리 분류 결과 - 카테고리: '${result.category}', 신뢰도: ${result.confidence}")
                    if (result.confidence >= 0.5f) {
                        category = result.category
                        AppLogger.d(TAG, "카테고리 적용됨: '$category' (신뢰도 >= 0.5)")
                    } else {
                        AppLogger.d(TAG, "카테고리 미적용 - 신뢰도 부족: ${result.confidence} < 0.5")
                    }
                }
                categoryResult.onFailure { e ->
                    AppLogger.w(TAG, "AI 카테고리 분류 실패: ${e.message}")
                }
            } else {
                AppLogger.d(TAG, "카테고리 분류 건너뜀 - type: ${parsed.type}, displayName: '$displayName'")
            }

            // 현재 활성화된 태그 가져오기 (여행/이벤트 등)
            val activeTagId = userPreferences.getActiveTagId() ?: ""
            val activeTagName = userPreferences.getActiveTagName() ?: ""
            if (activeTagId.isNotBlank()) {
                AppLogger.d(TAG, "활성 태그 - ID: '$activeTagId', 이름: '$activeTagName'")
            } else {
                AppLogger.d(TAG, "활성 태그 없음")
            }

            // 사용자가 설정한 고액 거래 기준 금액
            AppLogger.d(TAG, "고액 기준 금액: ${highAmountThreshold}원, 거래 금액: ${parsed.amount}원, 확인 필요: ${parsed.amount > highAmountThreshold}")

            AppLogger.d(TAG, "===== Transaction 객체 생성 =====")
            AppLogger.d(TAG, "  groupId: ${user.groupId}")
            AppLogger.d(TAG, "  userId: $userId, userName: ${user.name}")
            AppLogger.d(TAG, "  type: ${parsed.type}, amount: ${parsed.amount}")
            AppLogger.d(TAG, "  bankId: ${parsed.bankConfig.bankId}, bankName: ${parsed.bankConfig.displayName}")
            AppLogger.d(TAG, "  merchantName: '$displayName', category: '$category'")
            AppLogger.d(TAG, "  source: NOTIFICATION, tagId: '$activeTagId', tagName: '$activeTagName'")
            AppLogger.d(TAG, "  isConfirmed: ${parsed.amount <= highAmountThreshold}")

            val transaction = Transaction(
                groupId = user.groupId,
                userId = userId,
                userName = user.name,
                type = parsed.type,
                amount = parsed.amount,
                bankId = parsed.bankConfig.bankId,
                bankName = parsed.bankConfig.displayName,
                description = parsed.description,
                merchantName = displayName,
                category = category,
                source = InputSource.NOTIFICATION,
                originalText = parsed.originalText,
                tagId = activeTagId,
                tagName = activeTagName,
                confidence = parsed.confidence,
                merchantCandidates = parsed.merchantCandidates,
                transactionDate = Timestamp(Date()),
                isConfirmed = parsed.amount <= highAmountThreshold
            )

            AppLogger.d(TAG, "신뢰도: ${parsed.confidence}, 후보 수: ${parsed.merchantCandidates.size}")

            // 먼저 거래를 저장
            val savedTransaction = transactionRepository.addTransactionAndReturn(transaction)
            AppLogger.d(TAG, "거래 저장 완료 - ID: ${savedTransaction.id}, 금액: ${savedTransaction.amount}")

            // 중복 감지 확인
            val duplicateResult = duplicateDetectionService.checkAndHandleDuplicate(savedTransaction)
            AppLogger.d(TAG, "중복 검사 결과: $duplicateResult")

            when (duplicateResult) {
                is DuplicateCheckResult.DuplicateDetected -> {
                    // 중복이 감지됨 - 사용자에게 알림
                    AppLogger.d(TAG, "중복 거래 감지 - 알림 표시")
                    showDuplicateNotification(savedTransaction)
                }
                is DuplicateCheckResult.SkipSecond -> {
                    // 규칙에 따라 두 번째 거래 스킵
                    AppLogger.d(TAG, "중복 규칙: 두 번째 거래 삭제")
                    transactionRepository.deleteTransaction(savedTransaction.id)
                }
                is DuplicateCheckResult.DeleteBoth -> {
                    // 규칙에 따라 두 거래 모두 삭제
                    AppLogger.d(TAG, "중복 규칙: 두 거래 모두 삭제")
                    transactionRepository.deleteTransaction(savedTransaction.id)
                }
                else -> {
                    // NoDuplicate, KeepBoth, KeepSecond - 거래 유지
                    AppLogger.d(TAG, "거래 유지됨 - 중복 아님 또는 유지 규칙")
                    if (parsed.amount > highAmountThreshold) {
                        showHighAmountNotification(savedTransaction)
                    }

                    // 입금 거래인 경우 목표 저축 자동 연동 처리
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
            AppLogger.e(TAG, "거래 처리 중 오류 발생", e)
            e.printStackTrace()
        }
    }

    private fun showHighAmountNotification(transaction: Transaction) {
        AppLogger.d(TAG, "고액 거래 알림 표시 - ID: ${transaction.id}, 금액: ${transaction.amount}원, 가맹점: '${transaction.merchantName}'")
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
            .setContentTitle("고액 거래 감지")
            .setContentText("${formattedAmount}원이 감지되었습니다. 확인해주세요.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(HIGH_AMOUNT_NOTIFICATION_ID, notification)
    }

    private fun showDuplicateNotification(transaction: Transaction) {
        AppLogger.d(TAG, "중복 거래 알림 표시 - ID: ${transaction.id}, 금액: ${transaction.amount}원, 은행: '${transaction.bankName}'")
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
            .setContentTitle("중복 거래 감지")
            .setContentText("${formattedAmount}원 거래가 중복으로 감지되었습니다. 확인해주세요.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("동일한 금액의 거래가 카드와 계좌에서 감지되었습니다. 앱에서 어느 거래를 유지할지 선택해주세요."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(DUPLICATE_NOTIFICATION_ID, notification)
    }

    /**
     * 목표 저축 자동 입금 처리
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
            AppLogger.d(TAG, "===== 목표 저축 자동 입금 처리 시작 =====")
            AppLogger.d(TAG, "  금액: ${amount}원, 송금자: '$senderName'")
            AppLogger.d(TAG, "  계좌번호: '$accountNumber', 은행: '$bankName'")
            AppLogger.d(TAG, "  그룹ID: '$groupId'")
            AppLogger.d(TAG, "  원본텍스트: '$originalText'")

            // 그룹 멤버 조회
            val groupMembers = userRepository.getGroupMembersFlow(groupId).first()
            AppLogger.d(TAG, "그룹 멤버 수: ${groupMembers.size}")
            groupMembers.forEachIndexed { index, member ->
                AppLogger.d(TAG, "  멤버[$index]: ${member.name} (ID: ${member.id})")
            }

            // 자동 입금 처리
            val results = savingsAutoDepositService.processDepositNotification(
                amount = amount,
                senderName = senderName,
                accountNumber = accountNumber,
                originalText = originalText,
                groupId = groupId,
                groupMembers = groupMembers
            )

            AppLogger.d(TAG, "자동 입금 처리 결과 수: ${results.size}")

            for (result in results) {
                when (result) {
                    is SavingsAutoDepositService.DepositProcessResult.AutoProcessed -> {
                        // 자동으로 저축에 반영
                        AppLogger.d(TAG, "[AutoProcessed] 목표: '${result.savingsGoal.name}', 금액: ${result.contribution.amount}원, 사용자: '${result.matchedUser.name}'")
                        savingsAutoDepositService.saveAutoContribution(result.contribution)
                        showSavingsAutoDepositNotification(
                            goalName = result.savingsGoal.name,
                            amount = result.contribution.amount,
                            userName = result.matchedUser.name
                        )
                    }
                    is SavingsAutoDepositService.DepositProcessResult.NeedsConfirmation -> {
                        // 사용자 확인 필요 알림
                        AppLogger.d(TAG, "[NeedsConfirmation] 목표: '${result.savingsGoal.name}', 금액: ${result.amount}원, 감지된 이름: '${result.detectedSenderName}', 후보 수: ${result.candidates.size}")
                        showSavingsConfirmationNotification(
                            goalName = result.savingsGoal.name,
                            amount = result.amount,
                            detectedName = result.detectedSenderName,
                            candidateCount = result.candidates.size
                        )
                    }
                    is SavingsAutoDepositService.DepositProcessResult.NeedsManualInput -> {
                        // 수동 입력 필요 알림
                        AppLogger.d(TAG, "[NeedsManualInput] 목표: '${result.savingsGoal.name}', 금액: ${result.amount ?: 0}원, 사유: '${result.reason}'")
                        showSavingsManualInputNotification(
                            goalName = result.savingsGoal.name,
                            amount = result.amount ?: 0L,
                            reason = result.reason
                        )
                    }
                    is SavingsAutoDepositService.DepositProcessResult.NotApplicable -> {
                        // 해당 목표 없음 - 무시
                        AppLogger.d(TAG, "[NotApplicable] 해당하는 저축 목표 없음")
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "목표 저축 자동 입금 처리 중 오류", e)
            e.printStackTrace()
        }
    }

    private fun showSavingsAutoDepositNotification(
        goalName: String,
        amount: Long,
        userName: String
    ) {
        AppLogger.d(TAG, "저축 자동 반영 알림 표시 - 목표: '$goalName', 금액: ${amount}원, 사용자: '$userName'")
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
            .setContentTitle("저축 자동 반영")
            .setContentText("$goalName 에 ${userName}님이 ${formattedAmount}원 저축 완료")
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
        AppLogger.d(TAG, "저축 확인 필요 알림 표시 - 목표: '$goalName', 금액: ${amount}원, 감지이름: '$detectedName', 후보: ${candidateCount}명")
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
            .setContentTitle("저축 확인 필요")
            .setContentText("$goalName: '$detectedName' ${formattedAmount}원 입금 - 멤버 확인 필요")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("'$detectedName' 님이 ${formattedAmount}원 입금이 감지되었습니다. ${candidateCount}명의 후보 중 입금자를 선택해주세요."))
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
        AppLogger.d(TAG, "저축 수동 입력 필요 알림 표시 - 목표: '$goalName', 금액: ${amount}원, 사유: '$reason'")
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

        val amountText = if (amount > 0) String.format("%,d", amount) else ""

        val notification = NotificationCompat.Builder(this, SAVINGS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("저축 자동 확인 필요")
            .setContentText("$goalName: ${amountText}원 입금 - $reason")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SAVINGS_MANUAL_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppLogger.d(TAG, "알림 채널 생성 시작 (API ${Build.VERSION.SDK_INT})")
            val notificationManager = getSystemService(NotificationManager::class.java)

            // 고액 거래 알림 채널
            val highAmountChannel = NotificationChannel(
                CHANNEL_ID,
                "고액 거래 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "설정 금액 이상 거래에 대한 확인 알림"
            }
            notificationManager.createNotificationChannel(highAmountChannel)
            AppLogger.d(TAG, "알림 채널 생성: '$CHANNEL_ID' (고액 거래)")

            // 중복 거래 알림 채널
            val duplicateChannel = NotificationChannel(
                DUPLICATE_CHANNEL_ID,
                "중복 거래 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "카드와 계좌에서 감지된 중복 거래 알림"
            }
            notificationManager.createNotificationChannel(duplicateChannel)
            AppLogger.d(TAG, "알림 채널 생성: '$DUPLICATE_CHANNEL_ID' (중복 거래)")

            // 목표 저축 알림 채널
            val savingsChannel = NotificationChannel(
                SAVINGS_CHANNEL_ID,
                "목표 저축 알림",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "목표 저축 자동 입금 연동 알림"
            }
            notificationManager.createNotificationChannel(savingsChannel)
            AppLogger.d(TAG, "알림 채널 생성: '$SAVINGS_CHANNEL_ID' (목표 저축)")
            AppLogger.d(TAG, "알림 채널 생성 완료 - 총 3개 채널")
        } else {
            AppLogger.d(TAG, "알림 채널 생성 건너뜀 (API ${Build.VERSION.SDK_INT} < O)")
        }
    }

    companion object {
        private const val TAG = "TxNotificationService"
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
