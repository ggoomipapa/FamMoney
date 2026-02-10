package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.SavingsContribution
import com.ezcorp.fammoney.data.model.SavingsGoal
import com.ezcorp.fammoney.data.model.User
import com.ezcorp.fammoney.data.repository.MemberStatistics
import com.ezcorp.fammoney.data.repository.SavingsGoalRepository
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavingsGoalDetailUiState(
    val goal: SavingsGoal? = null,
    val contributions: List<SavingsContribution> = emptyList(),
    val memberStatistics: List<MemberStatistics> = emptyList(),
    val groupMembers: List<User> = emptyList(),
    val currentUser: User? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class SavingsGoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val savingsGoalRepository: SavingsGoalRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SavingsDetailVM"
    }

    private val goalId: String = savedStateHandle.get<String>("goalId") ?: ""

    private val _uiState = MutableStateFlow(SavingsGoalDetailUiState())
    val uiState: StateFlow<SavingsGoalDetailUiState> = _uiState.asStateFlow()

    init {
        AppLogger.i(TAG, "ViewModel 초기화: goalId=$goalId")
        if (goalId.isNotBlank()) {
            loadGoalDetails()
        } else {
            AppLogger.w(TAG, "goalId가 비어있음 - 목표 상세 로드 건너뜀")
        }
    }

    private fun loadGoalDetails() {
        AppLogger.d(TAG, "목표 상세 로드 시작: goalId=$goalId")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // ëª©í ?ë³´ ?¤ìê°??ì 
            launch {
                savingsGoalRepository.getGoalFlow(goalId).collect { goal ->
                    AppLogger.d(TAG, "목표 정보 수신: name=${goal?.name}, target=${goal?.targetAmount}")
                    _uiState.update { it.copy(goal = goal, isLoading = false) }

                    // ëª©íê° ë¡ë?ë©´ ê·¸ë£¹ ë©¤ë²??ë¡ë
                goal?.groupId?.let { groupId ->
                        loadGroupMembers(groupId)
                    }
                }
            }

            // ê¸°ì¬ ?´ì­ ?¤ìê°??ì 
            launch {
                savingsGoalRepository.getContributionsFlow(goalId).collect { contributions ->
                    val sorted = contributions.sortedByDescending { it.createdAt }
                    AppLogger.dataLoaded(TAG, "기여 내역", sorted.size)
                    _uiState.update { it.copy(contributions = sorted) }
                }
            }

            // ë©¤ë²ë³??µê³ ë¡ë
            loadMemberStatistics()
        }
    }

    private fun loadGroupMembers(groupId: String) {
        AppLogger.d(TAG, "그룹 멤버 로드: groupId=$groupId")
        viewModelScope.launch {
            userRepository.getGroupMembersFlow(groupId).collect { members ->
                _uiState.update { it.copy(groupMembers = members) }
            }
        }
    }

    private fun loadMemberStatistics() {
        AppLogger.d(TAG, "멤버 통계 로드 시작")
        viewModelScope.launch {
            val statistics = savingsGoalRepository.getMemberStatistics(goalId)
            AppLogger.dataLoaded(TAG, "멤버 통계", statistics.size)
            _uiState.update { it.copy(memberStatistics = statistics) }
        }
    }

    fun refreshStatistics() {
        AppLogger.userAction(TAG, "통계 새로고침")
        loadMemberStatistics()
    }

    /**
     * ?ë ê¸°ì¬ ì¶ê"
     */
    fun addContribution(
        userId: String,
        userName: String,
        amount: Long
    ) {
        AppLogger.userAction(TAG, "기여 추가", "userId=$userId, userName=$userName, amount=$amount")
        viewModelScope.launch {
            val result = savingsGoalRepository.addContribution(
                goalId = goalId,
                userId = userId,
                userName = userName,
                amount = amount,
                isAutoDetected = false,
                matchConfidence = "manual"
            )

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "addContribution", "기여 추가 성공: amount=$amount")
                loadMemberStatistics()
            } else {
                AppLogger.apiError(TAG, "addContribution", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "?ì¶?ì¶ê????¤í¨?ìµ?ë¤")
                }
            }
        }
    }

    /**
     * ê¸°ì¬ ?´ì­ ?ì  (?ê¸??ë³ê²? ê¸ì¡ ë³ê²"
     */
    fun updateContribution(
        contributionId: String,
        newUserId: String,
        newUserName: String,
        newAmount: Long
    ) {
        AppLogger.userAction(TAG, "기여 수정", "contributionId=$contributionId, newAmount=$newAmount")
        viewModelScope.launch {
            val currentUser = _uiState.value.currentUser ?: return@launch

            val result = savingsGoalRepository.updateContribution(
                contributionId = contributionId,
                newUserId = newUserId,
                newUserName = newUserName,
                newAmount = newAmount,
                modifiedBy = currentUser.id
            )

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "updateContribution", "기여 수정 성공")
                loadMemberStatistics()
            } else {
                AppLogger.apiError(TAG, "updateContribution", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "?ì ???¤í¨?ìµ?ë¤")
                }
            }
        }
    }

    /**
     * ê¸°ì¬ ?´ì­ ?? 
     */
    fun deleteContribution(contributionId: String) {
        AppLogger.userAction(TAG, "기여 삭제", "contributionId=$contributionId")
        viewModelScope.launch {
            val result = savingsGoalRepository.deleteContribution(contributionId)

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "deleteContribution", "기여 삭제 성공: $contributionId")
                loadMemberStatistics()
            } else {
                AppLogger.apiError(TAG, "deleteContribution", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "?? ???¤í¨?ìµ?ë¤")
                }
            }
        }
    }

    /**
     * ëª©í ?ë³´ ?ì 
     */
    fun updateGoal(name: String, targetAmount: Long, iconEmoji: String) {
        AppLogger.userAction(TAG, "목표 수정", "name=$name, targetAmount=$targetAmount")
        viewModelScope.launch {
            val result = savingsGoalRepository.updateGoal(
                goalId = goalId,
                name = name,
                targetAmount = targetAmount,
                iconEmoji = iconEmoji
            )

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "updateGoal", "목표 수정 성공")
            }
            if (result.isFailure) {
                AppLogger.apiError(TAG, "updateGoal", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.update {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "?ì ???¤í¨?ìµ?ë¤")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setCurrentUser(user: User) {
        AppLogger.d(TAG, "현재 사용자 설정: userId=${user.id}, name=${user.name}")
        _uiState.update { it.copy(currentUser = user) }
    }
}
