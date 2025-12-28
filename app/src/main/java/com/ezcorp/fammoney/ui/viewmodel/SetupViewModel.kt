package com.ezcorp.fammoney.ui.viewmodel

import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Group
import com.ezcorp.fammoney.data.model.User
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.service.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val isLoading: Boolean = false,
    val userName: String = "",
    val groupName: String = "",
    val inviteCode: String = "",
    val isJoiningGroup: Boolean = false,
    val setupComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var deviceId: String = ""

    fun setDeviceId(id: String) {
        deviceId = id
    }

    fun updateUserName(name: String) {
        _uiState.value = _uiState.value.copy(userName = name)
    }

    fun updateGroupName(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun updateInviteCode(code: String) {
        _uiState.value = _uiState.value.copy(inviteCode = code.uppercase())
    }

    fun setJoiningGroup(joining: Boolean) {
        _uiState.value = _uiState.value.copy(isJoiningGroup = joining)
    }

    fun createNewGroup() {
        val state = _uiState.value
        if (state.userName.isBlank()) {
            _uiState.value = state.copy(error = "이름을 입력해주세요")
            return
        }
        if (state.groupName.isBlank()) {
            _uiState.value = state.copy(error = "가계부 이름을 입력해주세요")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val user = User(
                    name = state.userName,
                    isOwner = true,
                    deviceId = deviceId
                )

                val userResult = userRepository.createUser(user)
                val userId = userResult.getOrThrow()

                val group = Group(
                    name = state.groupName,
                    ownerUserId = userId,
                    memberIds = listOf(userId)
                )

                val groupResult = userRepository.createGroup(group)
                val groupId = groupResult.getOrThrow()

                val updatedUser = user.copy(id = userId, groupId = groupId)
                userRepository.updateUser(updatedUser)

                userPreferences.saveUserData(userId, groupId, state.userName)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "오류가 발생했습니다"
                )
            }
        }
    }

    fun joinExistingGroup() {
        val state = _uiState.value
        if (state.userName.isBlank()) {
            _uiState.value = state.copy(error = "이름을 입력해주세요")
            return
        }
        if (state.inviteCode.isBlank()) {
            _uiState.value = state.copy(error = "초대 코드를 입력해주세요")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val group = userRepository.getGroupByInviteCode(state.inviteCode)
                if (group == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "유효하지 않은 초대 코드입니다"
                    )
                    return@launch
                }

                val user = User(
                    name = state.userName,
                    groupId = group.id,
                    isOwner = false,
                    deviceId = deviceId
                )

                val userResult = userRepository.createUser(user)
                val userId = userResult.getOrThrow()

                userRepository.joinGroup(userId, group.id)

                userPreferences.saveUserData(userId, group.id, state.userName)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "오류가 발생했습니다"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
