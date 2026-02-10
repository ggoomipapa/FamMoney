package com.ezcorp.fammoney.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Group
import com.ezcorp.fammoney.data.model.User
import com.ezcorp.fammoney.data.repository.AuthRepository
import com.ezcorp.fammoney.data.repository.UserRepository
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "AuthViewModel"

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
        AppLogger.d(TAG, "인증 상태 관찰 시작")
        viewModelScope.launch {
            authRepository.authStateFlow().collect { firebaseUser ->
                if (firebaseUser != null) {
                    AppLogger.i(TAG, "인증된 사용자 감지: uid=${firebaseUser.uid.take(8)}..., isAnonymous=${firebaseUser.isAnonymous}")
                    handleAuthenticatedUser(firebaseUser)
                } else {
                    AppLogger.d(TAG, "인증되지 않음 - 익명 로그인 시도")
                    signInAnonymously()
                }
            }
        }
    }

    private suspend fun handleAuthenticatedUser(firebaseUser: FirebaseUser) {
        AppLogger.d(TAG, "인증된 사용자 처리: uid=${firebaseUser.uid.take(8)}...")
        val existingUser = userRepository.getUserByAuthUid(firebaseUser.uid)

        if (existingUser != null) {
            AppLogger.i(TAG, "기존 사용자 발견: ${existingUser.name}, groupId=${existingUser.groupId}")
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
            AppLogger.i(TAG, "새 사용자 - 설정 필요")
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
            AppLogger.d(TAG, "익명 로그인 시도")
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authRepository.signInAnonymously()
            result.onSuccess {
                AppLogger.i(TAG, "익명 로그인 성공")
            }
            result.onFailure { e ->
                AppLogger.e(TAG, "익명 로그인 실패: ${e.message}", e)
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
            AppLogger.userAction(TAG, "Google 로그인 결과 처리")
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authRepository.handleGoogleSignInResult(data)

            result.onSuccess { firebaseUser ->
                AppLogger.i(TAG, "Google 로그인 성공: email=${firebaseUser.email}")
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
                AppLogger.e(TAG, "Google 로그인 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun createUserAndGroup(userName: String, groupName: String, deviceId: String) {
        viewModelScope.launch {
            AppLogger.userAction(TAG, "사용자 및 가계부 생성", "userName=$userName, groupName=$groupName")
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val firebaseUser = authRepository.currentUser
                    ?: throw Exception("인증 정보가 없습니다")

                val fcmToken = try {
                    firebaseMessaging.token.await()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "FCM 토큰 가져오기 실패", e)
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
                AppLogger.i(TAG, "사용자 생성 완료: userId=$userId")

                val group = Group(
                    name = groupName,
                    ownerUserId = userId,
                    memberIds = listOf(userId)
                )

                val groupResult = userRepository.createGroup(group)
                val groupId = groupResult.getOrThrow()
                AppLogger.i(TAG, "가계부 생성 완료: groupId=$groupId")

                val updatedUser = user.copy(id = userId, groupId = groupId)
                userRepository.updateUser(updatedUser)

                userPreferences.saveFullUserData(
                    userId = userId,
                    groupId = groupId,
                    userName = userName,
                    authUid = firebaseUser.uid
                )

                fcmToken?.let { userPreferences.saveFcmToken(it) }

                AppLogger.i(TAG, "초기 설정 완료!")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUser = updatedUser,
                    needsSetup = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                AppLogger.e(TAG, "사용자/가계부 생성 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun joinGroupWithCode(userName: String, inviteCode: String, deviceId: String) {
        viewModelScope.launch {
            AppLogger.userAction(TAG, "초대 코드로 가계부 참여", "userName=$userName, inviteCode=$inviteCode")
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val firebaseUser = authRepository.currentUser
                    ?: throw Exception("인증 정보가 없습니다")

                if (firebaseUser.isAnonymous) {
                    throw Exception("그룹 참여를 위해서는 로그인이 필요합니다")
                }

                val group = userRepository.getGroupByInviteCode(inviteCode)
                    ?: throw Exception("유효하지 않은 초대 코드입니다")

                AppLogger.i(TAG, "가계부 발견: ${group.name}")

                val fcmToken = try {
                    firebaseMessaging.token.await()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "FCM 토큰 가져오기 실패", e)
                    null
                }

                val user = User(
                    authUid = firebaseUser.uid,
                    name = userName,
                    email = firebaseUser.email,
                    groupId = group.id,
                    groupIds = listOf(group.id),
                    activeGroupId = group.id,
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

                AppLogger.i(TAG, "가계부 참여 완료: ${group.name}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentUser = user.copy(id = userId),
                    needsSetup = false,
                    setupComplete = true
                )

            } catch (e: Exception) {
                AppLogger.e(TAG, "가계부 참여 실패: ${e.message}", e)
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
            AppLogger.d(TAG, "FCM 토큰 업데이트: userId=$userId")
            userRepository.updateFcmToken(userId, token)
            userPreferences.saveFcmToken(token)
        } catch (e: Exception) {
            AppLogger.w(TAG, "FCM 토큰 업데이트 실패", e)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            AppLogger.userAction(TAG, "로그아웃")
            authRepository.signOut()
            userPreferences.clearUserData()
            _uiState.value = AuthUiState()
            AppLogger.i(TAG, "로그아웃 완료")
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
