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

    private val goalId: String = savedStateHandle.get<String>("goalId") ?: ""

    private val _uiState = MutableStateFlow(SavingsGoalDetailUiState())
    val uiState: StateFlow<SavingsGoalDetailUiState> = _uiState.asStateFlow()

    init {
        if (goalId.isNotBlank()) {
            loadGoalDetails()
        }
    }

    private fun loadGoalDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // ëª©í ?ë³´ ?¤ìê°??ì 
            launch {
                savingsGoalRepository.getGoalFlow(goalId).collect { goal ->
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
                    _uiState.update { it.copy(contributions = sorted) }
                }
            }

            // ë©¤ë²ë³??µê³ ë¡ë
            loadMemberStatistics()
        }
    }

    private fun loadGroupMembers(groupId: String) {
        viewModelScope.launch {
            userRepository.getGroupMembersFlow(groupId).collect { members ->
                _uiState.update { it.copy(groupMembers = members) }
            }
        }
    }

    private fun loadMemberStatistics() {
        viewModelScope.launch {
            val statistics = savingsGoalRepository.getMemberStatistics(goalId)
            _uiState.update { it.copy(memberStatistics = statistics) }
        }
    }

    fun refreshStatistics() {
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
                loadMemberStatistics()
            } else {
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
                loadMemberStatistics()
            } else {
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
        viewModelScope.launch {
            val result = savingsGoalRepository.deleteContribution(contributionId)

            if (result.isSuccess) {
                loadMemberStatistics()
            } else {
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
        viewModelScope.launch {
            val result = savingsGoalRepository.updateGoal(
                goalId = goalId,
                name = name,
                targetAmount = targetAmount,
                iconEmoji = iconEmoji
            )

            if (result.isFailure) {
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
        _uiState.update { it.copy(currentUser = user) }
    }
}
