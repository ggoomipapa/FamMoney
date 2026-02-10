package com.ezcorp.fammoney.ui.viewmodel

import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Group
import com.ezcorp.fammoney.data.model.User
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
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

    companion object {
        private const val TAG = "SetupVM"
    }

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var deviceId: String = ""

    fun setDeviceId(id: String) {
        AppLogger.d(TAG, "디바이스 ID 설정: $id")
        deviceId = id
    }

    fun updateUserName(name: String) {
        AppLogger.d(TAG, "사용자 이름 업데이트: $name")
        _uiState.value = _uiState.value.copy(userName = name)
    }

    fun updateGroupName(name: String) {
        AppLogger.d(TAG, "그룹 이름 업데이트: $name")
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun updateInviteCode(code: String) {
        AppLogger.d(TAG, "초대 코드 업데이트")
        _uiState.value = _uiState.value.copy(inviteCode = code.uppercase())
    }

    fun setJoiningGroup(joining: Boolean) {
        AppLogger.stateChange(TAG, "isJoiningGroup", !joining, joining)
        _uiState.value = _uiState.value.copy(isJoiningGroup = joining)
    }

    fun createNewGroup() {
        AppLogger.userAction(TAG, "새 그룹 생성 요청")
        val state = _uiState.value
        if (state.userName.isBlank()) {
            AppLogger.w(TAG, "그룹 생성 실패: 이름 미입력")
            _uiState.value = state.copy(error = "이름을 입력해주세요")
            return
        }
        if (state.groupName.isBlank()) {
            AppLogger.w(TAG, "그룹 생성 실패: 가계부 이름 미입력")
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
                AppLogger.d(TAG, "사용자 생성 완료: userId=$userId")

                val group = Group(
                    name = state.groupName,
                    ownerUserId = userId,
                    memberIds = listOf(userId)
                )

                val groupResult = userRepository.createGroup(group)
                val groupId = groupResult.getOrThrow()
                AppLogger.d(TAG, "그룹 생성 완료: groupId=$groupId")

                val updatedUser = user.copy(id = userId, groupId = groupId)
                userRepository.updateUser(updatedUser)

                userPreferences.saveUserData(userId, groupId, state.userName)

                AppLogger.i(TAG, "새 그룹 생성 성공: userId=$userId, groupId=$groupId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                AppLogger.e(TAG, "새 그룹 생성 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "오류가 발생했습니다"
                )
            }
        }
    }

    fun joinExistingGroup() {
        AppLogger.userAction(TAG, "기존 그룹 참여 요청")
        val state = _uiState.value
        if (state.userName.isBlank()) {
            AppLogger.w(TAG, "그룹 참여 실패: 이름 미입력")
            _uiState.value = state.copy(error = "이름을 입력해주세요")
            return
        }
        if (state.inviteCode.isBlank()) {
            AppLogger.w(TAG, "그룹 참여 실패: 초대 코드 미입력")
            _uiState.value = state.copy(error = "초대 코드를 입력해주세요")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val group = userRepository.getGroupByInviteCode(state.inviteCode)
                AppLogger.d(TAG, "초대 코드 조회 결과: group=${group?.name ?: "null"}")
                if (group == null) {
                    AppLogger.w(TAG, "그룹 참여 실패: 유효하지 않은 초대 코드")
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
                AppLogger.d(TAG, "사용자 생성 완료: userId=$userId")

                userRepository.joinGroup(userId, group.id)
                AppLogger.d(TAG, "그룹 참여 완료: userId=$userId, groupId=${group.id}")

                userPreferences.saveUserData(userId, group.id, state.userName)

                AppLogger.i(TAG, "기존 그룹 참여 성공: userId=$userId, groupId=${group.id}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                AppLogger.e(TAG, "기존 그룹 참여 실패: ${e.message}", e)
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
