package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Allowance
import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.data.model.Child
import com.ezcorp.fammoney.data.model.Group
import com.ezcorp.fammoney.data.model.InputSource
import com.ezcorp.fammoney.data.model.SavingsGoal
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.model.User
import com.google.firebase.Timestamp
import android.app.Activity
import com.ezcorp.fammoney.data.model.TransactionTag
import com.ezcorp.fammoney.data.repository.AllowanceRepository
import com.ezcorp.fammoney.data.repository.ChildIncomeRepository
import com.ezcorp.fammoney.data.repository.BillingRepository
import com.ezcorp.fammoney.data.repository.DuplicateRepository
import com.ezcorp.fammoney.data.repository.SavingsGoalRepository
import com.ezcorp.fammoney.data.repository.TagRepository
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.service.AIFeatureService
import com.ezcorp.fammoney.service.GoalPrediction
import com.ezcorp.fammoney.service.MonthlyFinancialData
import com.ezcorp.fammoney.service.SmartInsight
import com.ezcorp.fammoney.service.SpendingPrediction
import com.ezcorp.fammoney.service.TransactionMigrationService
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.effectiveMaxMembers
import com.ezcorp.fammoney.util.effectiveSubscriptionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isOnboardingCompleted: Boolean = true,
    val currentUser: User? = null,
    val currentGroup: Group? = null,
    val groupMembers: List<User> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val selectedUserFilter: String? = null,
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val currentMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val availableBanks: List<BankConfig> = BankConfig.getDefaultBanks(),
    val pendingHighAmountTransaction: Transaction? = null,
    val highAmountThreshold: Long = UserPreferences.DEFAULT_HIGH_AMOUNT_THRESHOLD,
    val cashManagementEnabled: Boolean = false,
    val pendingDuplicatesCount: Int = 0,
    // 다중 가계부 지원
    val userGroups: List<Group> = emptyList(),
    // 용돈 관리
    val allowances: List<Allowance> = emptyList(),
    // 목표 저축
    val savingsGoals: List<SavingsGoal> = emptyList(),
    // 자녀 목록 (용돈 카테고리용)
    val children: List<Child> = emptyList(),
    val error: String? = null,
    // AI 기능 상태
    val isAIEnabled: Boolean = false,
    val aiInsights: List<SmartInsight> = emptyList(),
    val spendingPrediction: SpendingPrediction? = null,
    val goalPredictions: Map<String, GoalPrediction> = emptyMap(),
    val isLoadingAI: Boolean = false,
    val categoryExpenses: Map<String, Long> = emptyMap(),
    val duplicatePreference: String = UserPreferences.DUPLICATE_PREF_ASK,
    // 마이그레이션 상태
    val isMigrating: Boolean = false,
    val migrationCompleted: Boolean = false,
    val migrationResult: String? = null,
    // 태그 목록
    val tags: List<TransactionTag> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val duplicateRepository: DuplicateRepository,
    private val allowanceRepository: AllowanceRepository,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val childIncomeRepository: ChildIncomeRepository,
    private val tagRepository: TagRepository,
    private val userPreferences: UserPreferences,
    val billingRepository: BillingRepository,
    private val aiFeatureService: AIFeatureService,
    private val migrationService: TransactionMigrationService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 거래내역 로딩 Job (중복 호출 방지용)
    private var transactionsJob: kotlinx.coroutines.Job? = null

    // Gemini API 키 Flow
    val geminiApiKeyFlow = userPreferences.geminiApiKeyFlow

    init {
        checkLoginStatus()
        loadHighAmountThreshold()
        loadCashManagementSetting()
        loadOnboardingStatus()
        loadDuplicatePreference()
    }

    private fun loadOnboardingStatus() {
        viewModelScope.launch {
            userPreferences.onboardingCompletedFlow.collect { completed ->
                _uiState.value = _uiState.value.copy(isOnboardingCompleted = completed)
            }
        }
    }

    // Gemini API 키 저장
    fun saveGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            userPreferences.saveGeminiApiKey(apiKey)
        }
    }

    fun loadPendingDuplicatesCount() {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch
            duplicateRepository.getPendingDuplicatesFlow(groupId).collect { duplicates ->
                _uiState.value = _uiState.value.copy(pendingDuplicatesCount = duplicates.size)
            }
        }
    }

    private fun loadHighAmountThreshold() {
        viewModelScope.launch {
            userPreferences.highAmountThresholdFlow.collect { threshold ->
                _uiState.value = _uiState.value.copy(highAmountThreshold = threshold)
            }
        }
    }

    private fun loadCashManagementSetting() {
        viewModelScope.launch {
            userPreferences.cashManagementEnabledFlow.collect { enabled ->
                _uiState.value = _uiState.value.copy(cashManagementEnabled = enabled)
            }
        }
    }

    fun updateCashManagementEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.saveCashManagementEnabled(enabled)
        }
    }

    private fun loadDuplicatePreference() {
        viewModelScope.launch {
            userPreferences.duplicatePreferenceFlow.collect { preference ->
                _uiState.value = _uiState.value.copy(duplicatePreference = preference)
            }
        }
    }

    fun updateDuplicatePreference(preference: String) {
        viewModelScope.launch {
            userPreferences.saveDuplicatePreference(preference)
        }
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            val userId = userPreferences.getUserId()
            if (userId != null) {
                loadUserData(userId)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isLoggedIn = false
                )
            }
        }
    }

    private fun loadUserData(userId: String) {
        viewModelScope.launch {
            try {
                userRepository.getUserFlow(userId).collect { user ->
                    if (user != null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentUser = user
                        )
                        loadGroupData(user.groupId)
                        loadTransactions(user.groupId)
                        loadPendingDuplicatesCount()
                        loadAllowances(user.groupId)
                        loadSavingsGoals(user.groupId)
                        loadChildren(user.groupId)
                        loadUserGroups(user.groupIds)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadGroupData(groupId: String) {
        viewModelScope.launch {
            userRepository.getGroupFlow(groupId).collect { group ->
                _uiState.value = _uiState.value.copy(currentGroup = group)
            }
        }

        viewModelScope.launch {
            userRepository.getGroupMembersFlow(groupId).collect { members ->
                _uiState.value = _uiState.value.copy(groupMembers = members)
            }
        }
    }

    private fun loadUserGroups(groupIds: List<String>) {
        viewModelScope.launch {
            val groups = mutableListOf<Group>()
            groupIds.forEach { groupId ->
                userRepository.getGroupFlow(groupId).first()?.let { groups.add(it) }
            }
            _uiState.value = _uiState.value.copy(userGroups = groups)
        }
    }

    private fun loadAllowances(groupId: String) {
        viewModelScope.launch {
            allowanceRepository.getAllowancesFlow(groupId).collect { allowances ->
                _uiState.value = _uiState.value.copy(allowances = allowances)
            }
        }
    }

    private fun loadSavingsGoals(groupId: String) {
        viewModelScope.launch {
            savingsGoalRepository.getGoalsFlow(groupId).collect { goals ->
                _uiState.value = _uiState.value.copy(savingsGoals = goals)
            }
        }
    }

    private fun loadChildren(groupId: String) {
        viewModelScope.launch {
            childIncomeRepository.getChildrenByGroup(groupId).collect { children ->
                _uiState.value = _uiState.value.copy(children = children)
            }
        }
    }

    private fun loadTransactions(groupId: String) {
        // 이전 Job 취소하여 중복 collect 방지
        transactionsJob?.cancel()
        transactionsJob = viewModelScope.launch {
            val year = _uiState.value.currentYear
            val month = _uiState.value.currentMonth
            transactionRepository.getTransactionsByMonth(
                groupId,
                year,
                month
            ).collect { transactions ->
                // collect 블록 안에서 최신 state 읽기
                val state = _uiState.value

                // 사용자 필터 적용
                val userFilteredTransactions = if (state.selectedUserFilter != null) {
                    transactions.filter { it.userId == state.selectedUserFilter }
                } else {
                    transactions
                }

                // 공유 범위 필터링 적용
                val currentUserId = state.currentUser?.id
                val groupMembers = state.groupMembers

                val visibleTransactions = userFilteredTransactions.filter { transaction ->
                    // 본인 거래는 항상 보임
                    if (transaction.userId == currentUserId) {
                        true
                    } else {
                        // 다른 사람의 거래는 그 사람의 공유 범위 설정 확인
                        val transactionOwner = groupMembers.find { it.id == transaction.userId }
                        if (transactionOwner != null) {
                            val shareFromDate = transactionOwner.shareFromDate
                            val hiddenIds = transactionOwner.hiddenTransactionIds

                            // 숨김 목록에 있는 거래는 안 보임
                            if (hiddenIds.contains(transaction.id)) {
                                false
                            }
                            // 공유 시작일 이전 거래는 안 보임
                            else if (shareFromDate != null && transaction.transactionDate != null) {
                                transaction.transactionDate >= shareFromDate
                            } else {
                                true
                            }
                        } else {
                            true
                        }
                    }
                }

                val totalIncome = visibleTransactions
                    .filter { it.type == TransactionType.INCOME && it.isConfirmed }
                    .sumOf { it.amount }

                val totalExpense = visibleTransactions
                    .filter { it.type == TransactionType.EXPENSE && it.isConfirmed }
                    .sumOf { it.amount }

                // 카테고리별 지출 계산 (AI 분석용)
                val categoryExpenses = visibleTransactions
                    .filter { it.type == TransactionType.EXPENSE && it.isConfirmed }
                    .groupBy { it.category.ifBlank { "기타" } }
                    .mapValues { entry -> entry.value.sumOf { it.amount } }

                _uiState.value = _uiState.value.copy(
                    transactions = visibleTransactions,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    categoryExpenses = categoryExpenses
                )
            }
        }
    }

    fun setUserFilter(userId: String?) {
        _uiState.value = _uiState.value.copy(selectedUserFilter = userId)
        _uiState.value.currentUser?.groupId?.let { loadTransactions(it) }
    }

    fun changeMonth(year: Int, month: Int) {
        _uiState.value = _uiState.value.copy(
            currentYear = year,
            currentMonth = month
        )
        _uiState.value.currentUser?.groupId?.let { loadTransactions(it) }
    }

    fun previousMonth() {
        val state = _uiState.value
        var newYear = state.currentYear
        var newMonth = state.currentMonth - 1
        if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }
        changeMonth(newYear, newMonth)
    }

    fun nextMonth() {
        val state = _uiState.value
        var newYear = state.currentYear
        var newMonth = state.currentMonth + 1
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }
        changeMonth(newYear, newMonth)
    }

    fun updateSelectedBanks(bankIds: List<String>) {
        viewModelScope.launch {
            val userId = _uiState.value.currentUser?.id ?: return@launch
            userRepository.updateSelectedBanks(userId, bankIds)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferences.setOnboardingCompleted(true)
        }
    }

    fun confirmHighAmountTransaction(transactionId: String) {
        viewModelScope.launch {
            transactionRepository.confirmTransaction(transactionId)
            _uiState.value = _uiState.value.copy(pendingHighAmountTransaction = null)
        }
    }

    fun dismissHighAmountTransaction() {
        viewModelScope.launch {
            val transaction = _uiState.value.pendingHighAmountTransaction ?: return@launch
            transactionRepository.deleteTransaction(transaction.id)
            _uiState.value = _uiState.value.copy(pendingHighAmountTransaction = null)
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transactionId)
        }
    }

    fun updateNotificationSettings(notifyGroup: Boolean, receiveNotifications: Boolean) {
        viewModelScope.launch {
            val userId = _uiState.value.currentUser?.id ?: return@launch
            userRepository.updateNotificationSettings(
                userId = userId,
                notifyGroupOnTransaction = notifyGroup,
                receiveGroupNotifications = receiveNotifications
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun updateHighAmountThreshold(amount: Long) {
        viewModelScope.launch {
            userPreferences.saveHighAmountThreshold(amount)
        }
    }

    fun updateChildIncomeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            userRepository.updateChildIncomeEnabled(groupId, enabled)
        }
    }

    fun updateSharingScope(shareFromDate: Timestamp?, hiddenTransactionIds: List<String>) {
        viewModelScope.launch {
            val userId = _uiState.value.currentUser?.id ?: return@launch
            userRepository.updateSharingScope(userId, shareFromDate, hiddenTransactionIds)
        }
    }

    fun addTransaction(
        type: TransactionType,
        amount: Long,
        description: String,
        category: String = "",
        merchant: String = "",
        merchantName: String = "",
        memo: String = "",
        linkedChildId: String = "",
        linkedChildName: String = ""
    ) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser ?: return@launch
            val transaction = Transaction(
                groupId = user.groupId,
                userId = user.id,
                userName = user.name,
                type = type,
                amount = amount,
                description = description,
                category = category,
                merchant = merchant,
                merchantName = merchantName,
                memo = memo,
                source = InputSource.MANUAL_ENTRY,
                transactionDate = Timestamp.now(),
                isConfirmed = true,
                linkedChildId = linkedChildId,
                linkedChildName = linkedChildName
            )
            transactionRepository.addTransaction(transaction)

            // 자녀 용돈 연동: 자녀 잔액 업데이트
            if (linkedChildId.isNotEmpty()) {
                if (type == TransactionType.EXPENSE) {
                    // 지출이면 자녀 용돈에서 차감
                childIncomeRepository.subtractFromAllowanceBalance(linkedChildId, amount)
                } else {
                    // 수입이면 자녀 용돈에 추가
                childIncomeRepository.addToAllowanceBalance(linkedChildId, amount)
                }
            }
        }
    }

    // ========== 가계부 이름/내 이름 변경 ==========
    fun updateGroupName(newName: String) {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            userRepository.updateGroupName(groupId, newName)
        }
    }

    fun updateUserName(newName: String) {
        viewModelScope.launch {
            val userId = _uiState.value.currentUser?.id ?: return@launch
            userRepository.updateUserName(userId, newName)
        }
    }

    // ========== 멤버 강퇴 ==========
    fun removeMember(userId: String) {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            userRepository.removeMemberFromGroup(groupId, userId)
        }
    }

    // ========== 통장 잔고 설정 ==========
    fun updateBalanceSettings(enabled: Boolean, initialBalance: Long) {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            userRepository.updateBalanceSettings(groupId, enabled, initialBalance)
        }
    }

    // ========== 그룹 설정 ==========
    fun updateGroupSettings(
        cashManagementEnabled: Boolean,
        highAmountThreshold: Long,
        childIncomeEnabled: Boolean
    ) {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            userRepository.updateGroupSettings(
                groupId,
                cashManagementEnabled,
                highAmountThreshold,
                childIncomeEnabled
            )
        }
    }

    // ========== 가계부 전환 ==========
    fun switchGroup(groupId: String) {
        viewModelScope.launch {
            val userId = _uiState.value.currentUser?.id ?: return@launch
            userRepository.setActiveGroup(userId, groupId)
            userPreferences.saveGroupId(groupId)
            loadGroupData(groupId)
            loadTransactions(groupId)
            loadAllowances(groupId)
            loadSavingsGoals(groupId)
        }
    }

    // ========== 새 가계부 생성 ==========
    fun createNewGroup(name: String) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser ?: return@launch
            val newGroup = Group(
                name = name,
                ownerUserId = user.id,
                memberIds = listOf(user.id)
            )
            val result = userRepository.createGroup(newGroup)
            result.onSuccess { groupId ->
                userRepository.addGroupToUser(user.id, groupId)
                switchGroup(groupId)
            }
        }
    }

    // ========== 초대 코드로 참가 ==========
    fun joinGroupByInviteCode(inviteCode: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser ?: run {
                onResult(false, "사용자 정보를 찾을 수 없습니다")
                return@launch
            }
            val group = userRepository.getGroupByInviteCode(inviteCode)
            if (group != null) {
                // 멤버 수 제한 확인 (구독 비례 제한 적용)
                if (group.effectiveSubscriptionType == "free" ||
                    (group.effectiveSubscriptionType != "connect_plus" && group.memberIds.size >= group.effectiveMaxMembers)) {
                    if (group.memberIds.size >= group.effectiveMaxMembers) {
                        onResult(false, "가계부 멤버 수가 최대입니다")
                        return@launch
                    }
                }
                val result = userRepository.joinGroup(user.id, group.id)
                result.onSuccess {
                    userRepository.addGroupToUser(user.id, group.id)
                    switchGroup(group.id)
                    onResult(true, null)
                }.onFailure {
                    onResult(false, it.message)
                }
            } else {
                onResult(false, "유효하지 않은 초대 코드입니다")
            }
        }
    }

    // ========== 용돈 관리 ==========
    fun createAllowance(childUserId: String, childName: String, amount: Long, frequency: String) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser ?: return@launch
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            val allowance = Allowance(
                groupId = groupId,
                childUserId = childUserId,
                childName = childName,
                parentUserId = user.id,
                amount = amount,
                frequency = frequency,
                balance = 0
            )
            allowanceRepository.createAllowance(allowance)
        }
    }

    fun giveAllowance(allowanceId: String, currentBalance: Long, amount: Long) {
        viewModelScope.launch {
            allowanceRepository.giveAllowance(allowanceId, currentBalance, amount)
        }
    }

    // ========== 목표 저축 ==========
    fun createSavingsGoal(
        name: String,
        targetAmount: Long,
        iconEmoji: String,
        autoDepositEnabled: Boolean = false,
        linkedAccountNumber: String = "",
        linkedBankName: String = ""
    ) {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            val goal = SavingsGoal(
                groupId = groupId,
                name = name,
                targetAmount = targetAmount,
                iconEmoji = iconEmoji,
                autoDepositEnabled = autoDepositEnabled,
                linkedAccountNumber = linkedAccountNumber,
                linkedBankName = linkedBankName
            )
            savingsGoalRepository.createGoal(goal)
        }
    }

    fun addSavingsContribution(goalId: String, amount: Long) {
        viewModelScope.launch {
            val user = _uiState.value.currentUser ?: return@launch
            savingsGoalRepository.addContribution(
                goalId = goalId,
                userId = user.id,
                userName = user.name,
                amount = amount
            )
        }
    }

    fun deleteSavingsGoal(goalId: String) {
        viewModelScope.launch {
            savingsGoalRepository.deleteGoal(goalId)
        }
    }

    // ========== 구독/결제 ==========
    fun purchaseConnectMonthly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        billingRepository.purchaseConnectMonthly(activity) { success, result ->
            if (success) {
                updateGroupSubscription("connect")
            }
            onComplete(success, result)
        }
    }

    fun purchaseConnectYearly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        billingRepository.purchaseConnectYearly(activity) { success, result ->
            if (success) {
                updateGroupSubscription("connect")
            }
            onComplete(success, result)
        }
    }

    fun purchaseConnectPlusMonthly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        billingRepository.purchaseConnectPlusMonthly(activity) { success, result ->
            if (success) {
                updateGroupSubscription("connect_plus")
            }
            onComplete(success, result)
        }
    }

    fun purchaseConnectPlusYearly(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        billingRepository.purchaseConnectPlusYearly(activity) { success, result ->
            if (success) {
                updateGroupSubscription("connect_plus")
            }
            onComplete(success, result)
        }
    }

    fun purchaseForever(activity: Activity, onComplete: (Boolean, String?) -> Unit) {
        billingRepository.purchaseForever(activity) { success, result ->
            if (success) {
                updateGroupSubscription("forever")
            }
            onComplete(success, result)
        }
    }

    private fun updateGroupSubscription(subscriptionType: String) {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch
            userRepository.updateSubscription(groupId, subscriptionType)
        }
    }

    fun refreshBillingStatus() {
        billingRepository.refreshPurchases()
    }

    // ========== AI 기능 ==========

    /**
     * AI 기능 사용 가능 여부 확인 및 상태 업데이트
     */
    fun checkAIEnabled() {
        val enabled = aiFeatureService.isAIEnabled()
        _uiState.value = _uiState.value.copy(isAIEnabled = enabled)
    }

    /**
     * AI 인사이트 로드
     */
    fun loadAIInsights() {
        if (!aiFeatureService.isAIEnabled()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingAI = true)

            val state = _uiState.value
            val currentMonthData = MonthlyFinancialData(
                year = state.currentYear,
                month = state.currentMonth,
                totalIncome = state.totalIncome,
                totalExpense = state.totalExpense,
                balance = state.totalIncome - state.totalExpense,
                categoryExpenses = state.categoryExpenses,
                topMerchants = emptyList()
            )

            // 저축 목표 정보
            val goals = state.savingsGoals.map { it.name to it.targetAmount }

            val result = aiFeatureService.generateSmartInsights(
                currentMonth = currentMonthData,
                previousMonth = null, // TODO: 이전 달 데이터 로드
                savingsGoals = goals
            )

            result.fold(
                onSuccess = { insights ->
                    _uiState.value = _uiState.value.copy(
                        aiInsights = insights,
                        isLoadingAI = false
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(isLoadingAI = false)
                }
            )
        }
    }

    /**
     * 지출 예측 로드
     */
    fun loadSpendingPrediction() {
        if (!aiFeatureService.isAIEnabled()) return

        viewModelScope.launch {
            val state = _uiState.value
            val calendar = Calendar.getInstance()
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            val result = aiFeatureService.predictMonthlySpending(
                currentMonthExpense = state.totalExpense,
                dayOfMonth = dayOfMonth,
                daysInMonth = daysInMonth,
                categoryExpenses = state.categoryExpenses
            )

            result.fold(
                onSuccess = { prediction ->
                    _uiState.value = _uiState.value.copy(spendingPrediction = prediction)
                },
                onFailure = { /* 무시 */ }
            )
        }
    }

    /**
     * 목표 달성 예측 로드
     */
    fun loadGoalPredictions() {
        if (!aiFeatureService.isAIEnabled()) return

        viewModelScope.launch {
            val goals = _uiState.value.savingsGoals
            val predictions = mutableMapOf<String, GoalPrediction>()

            goals.forEach { goal ->
                // 목표 기한: deadline 또는 1년
                val targetDate = goal.deadline?.toDate()?.time
                    ?: (System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)

                // 평균 월 기여금 추정 (현재 금액 / 경과 개월 수)
                val createdAtTime = goal.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                val elapsedMonths = ((System.currentTimeMillis() - createdAtTime) / (30L * 24 * 60 * 60 * 1000)).coerceAtLeast(1)
                val averageMonthlyContribution = goal.currentAmount / elapsedMonths

                val result = aiFeatureService.predictGoalAchievement(
                    goalName = goal.name,
                    targetAmount = goal.targetAmount,
                    currentAmount = goal.currentAmount,
                    targetDate = targetDate,
                    averageMonthlyContribution = averageMonthlyContribution,
                    recentContributions = emptyList() // 기여 이력은 별도 조회 필요
                )

                result.onSuccess { prediction ->
                    predictions[goal.id] = prediction
                }
            }

            _uiState.value = _uiState.value.copy(goalPredictions = predictions)
        }
    }

    /**
     * 모든 AI 기능 로드
     */
    fun loadAllAIFeatures() {
        checkAIEnabled()
        if (_uiState.value.isAIEnabled) {
            loadAIInsights()
            loadSpendingPrediction()
            loadGoalPredictions()
        }
    }

    /**
     * AI 인사이트 비활성화 사유 (구독 안내용)
     */
    fun getAIDisabledReason(): String {
        return aiFeatureService.getDisabledReason()
    }

    // ========== 마이그레이션 ==========

    /**
     * 거래내역 마이그레이션 실행
     * 기존 거래의 수입/지출 유형을 재판정
     */
    fun runMigration() {
        viewModelScope.launch {
            val groupId = _uiState.value.currentGroup?.id ?: return@launch

            _uiState.value = _uiState.value.copy(
                isMigrating = true,
                migrationResult = null
            )

            try {
                val result = migrationService.migrateTransactions(groupId)

                val message = buildString {
                    append("마이그레이션 완료!\n")
                    append("총 ${result.totalCount}건 중 ${result.updatedCount}건 수정됨\n")
                    if (result.incomeFixedCount > 0) {
                        append("- 수입으로 변경: ${result.incomeFixedCount}건\n")
                    }
                    if (result.expenseFixedCount > 0) {
                        append("- 지출로 변경: ${result.expenseFixedCount}건\n")
                    }
                    if (result.errors.isNotEmpty()) {
                        append("오류: ${result.errors.size}건")
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isMigrating = false,
                    migrationCompleted = true,
                    migrationResult = message
                )

                // 거래내역 새로고침
                loadTransactions(groupId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMigrating = false,
                    migrationResult = "마이그레이션 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * 마이그레이션 결과 초기화
     */
    fun clearMigrationResult() {
        _uiState.value = _uiState.value.copy(
            migrationResult = null,
            migrationCompleted = false
        )
    }

    // ========== 태그 기능 ==========

    /**
     * 태그 목록 로드
     */
    fun loadTags() {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch
            tagRepository.getTagsByGroup(groupId).collect { tags ->
                _uiState.value = _uiState.value.copy(tags = tags)
            }
        }
    }

    /**
     * 선택한 거래들에 태그 적용
     */
    fun applyTagToTransactions(transactionIds: List<String>, tagId: String, tagName: String) {
        viewModelScope.launch {
            transactionIds.forEach { txId ->
                val transaction = _uiState.value.transactions.find { it.id == txId }
                if (transaction != null) {
                    val updatedTransaction = transaction.copy(
                        tagId = tagId,
                        tagName = tagName
                    )
                    transactionRepository.updateTransaction(updatedTransaction)
                }
            }
        }
    }

    /**
     * 거래에서 태그 제거
     */
    fun removeTagFromTransaction(transactionId: String) {
        viewModelScope.launch {
            val transaction = _uiState.value.transactions.find { it.id == transactionId }
            if (transaction != null) {
                val updatedTransaction = transaction.copy(
                    tagId = "",
                    tagName = ""
                )
                transactionRepository.updateTransaction(updatedTransaction)
            }
        }
    }

    /**
     * 새 태그 생성 후 거래에 적용
     */
    fun createTagAndApply(
        tagName: String,
        tagColor: String,
        transactionIds: List<String>,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch

            // 새 태그 생성
            val newTag = TransactionTag(
                groupId = groupId,
                name = tagName,
                color = tagColor,
                icon = "label",
                isActive = false,
                createdAt = Timestamp.now()
            )

            val result = tagRepository.createTag(newTag)
            result.onSuccess { tagId ->
                // 거래에 태그 적용
                transactionIds.forEach { txId ->
                    val transaction = _uiState.value.transactions.find { it.id == txId }
                    if (transaction != null) {
                        val updatedTransaction = transaction.copy(
                            tagId = tagId,
                            tagName = tagName
                        )
                        transactionRepository.updateTransaction(updatedTransaction)
                    }
                }

                // 태그 목록 새로고침
                loadTags()
                onComplete()
            }
        }
    }
}
