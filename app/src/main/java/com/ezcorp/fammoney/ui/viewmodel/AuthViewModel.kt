package com.ezcorp.fammoney.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Group
import com.ezcorp.fammoney.data.model.User
import com.ezcorp.fammoney.data.repository.AuthRepository
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.service.UserPreferences
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val isAnonymous: Boolean = true,
    val currentUser: User? = null,
    val firebaseUser: FirebaseUser? = null,
    val needsSetup: Boolean = false,
    val setupComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val userPreferences: UserPreferences,
    private val firebaseMessaging: FirebaseMessaging
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authStateFlow().collect { firebaseUser ->
                if (firebaseUser != null) {
                    handleAuthenticatedUser(firebaseUser)
                } else {
                    signInAnonymously()
                }
            }
        }
    }

    private suspend fun handleAuthenticatedUser(firebaseUser: FirebaseUser) {
        val existingUser = userRepository.getUserByAuthUid(firebaseUser.uid)

        if (existingUser != null) {
            userPreferences.saveFullUserData(
                userId = existingUser.id,
                groupId = existingUser.groupId,
                userName = existingUser.name,
                authUid = firebaseUser.uid
            )

            updateFcmToken(existingUser.id)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isAuthenticated = true,
                isAnonymous = firebaseUser.isAnonymous,
                currentUser = existingUser,
                firebaseUser = firebaseUser,
                needsSetup = false
            )
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isAuthenticated = true,
                isAnonymous = firebaseUser.isAnonymous,
                firebaseUser = firebaseUser,
                needsSetup = true
            )
        }
    }

    private fun signInAnonymously() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authRepository.signInAnonymously()
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun getGoogleSignInIntent(): Intent = authRepository.getGoogleSignInIntent()

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authRepository.handleGoogleSignInResult(data)

            result.onSuccess { firebaseUser ->
                val currentUserId = userPreferences.getUserId()

                if (currentUserId != null) {
                    userRepository.updateAuthInfo(
                        userId = currentUserId,
                        authUid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        isAnonymous = false
                    )

                    userPreferences.saveAuthUid(firebaseUser.uid)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAnonymous = false,
                    firebaseUser = firebaseUser
                )
            }

            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun createUserAndGroup(userName: String, groupName: String, deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val firebaseUser = authRepository.currentUser
                    ?: throw Exception("인증 정보가 없습니다")

                val fcmToken = try {
                    firebaseMessaging.token.await()
                } catch (e: Exception) {
                    null
                }

                val user = User(
                    authUid = firebaseUser.uid,
                    name = userName,
                    email = firebaseUser.email,
                    isOwner = true,
                    isAnonymous = firebaseUser.isAnonymous,
                    fcmToken = fcmToken,
                    deviceId = deviceId
                )

                val userResult = userRepository.createUser(user)
                val userId = userResult.getOrThrow()

                val group = Group(
                    name = groupName,
                    ownerUserId = userId,
                    memberIds = listOf(userId)
                )

                val groupResult = userRepository.createGroup(group)
                val groupId = groupResult.getOrThrow()

                val updatedUser = user.copy(id = userId, groupId = groupId)
                userRepository.updateUser(updatedUser)

                userPreferences.saveFullUserData(
                    userId = userId,
                    groupId = groupId,
                    userName = userName,
                    authUid = firebaseUser.uid
                )

                fcmToken?.let { userPreferences.saveFcmToken(it) }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUser = updatedUser,
                    needsSetup = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun joinGroupWithCode(userName: String, inviteCode: String, deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val firebaseUser = authRepository.currentUser
                    ?: throw Exception("인증 정보가 없습니다")

                if (firebaseUser.isAnonymous) {
                    throw Exception("그룹 참여를 위해서는 로그인이 필요합니다")
                }

                val group = userRepository.getGroupByInviteCode(inviteCode)
                    ?: throw Exception("유효하지 않은 초대 코드입니다")

                val fcmToken = try {
                    firebaseMessaging.token.await()
                } catch (e: Exception) {
                    null
                }

                val user = User(
                    authUid = firebaseUser.uid,
                    name = userName,
                    email = firebaseUser.email,
                    groupId = group.id,
                    isOwner = false,
                    isAnonymous = false,
                    fcmToken = fcmToken,
                    deviceId = deviceId
                )

                val userResult = userRepository.createUser(user)
                val userId = userResult.getOrThrow()

                userRepository.joinGroup(userId, group.id)

                userPreferences.saveFullUserData(
                    userId = userId,
                    groupId = group.id,
                    userName = userName,
                    authUid = firebaseUser.uid
                )

                fcmToken?.let { userPreferences.saveFcmToken(it) }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUser = user.copy(id = userId),
                    needsSetup = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private suspend fun updateFcmToken(userId: String) {
        try {
            val token = firebaseMessaging.token.await()
            userRepository.updateFcmToken(userId, token)
            userPreferences.saveFcmToken(token)
        } catch (e: Exception) {
            // FCM 토큰 업데이트 실패 시 무시
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            userPreferences.clearUserData()
            _uiState.value = AuthUiState()
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun requiresLoginForFeature(featureName: String): Boolean {
        return _uiState.value.isAnonymous && featureName in listOf(
            "share_group",
            "join_group",
            "backup",
            "multi_device"
        )
    }
}
