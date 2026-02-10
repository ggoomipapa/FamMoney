package com.ezcorp.fammoney.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.BackupData
import com.ezcorp.fammoney.data.repository.BackupRepository
import com.ezcorp.fammoney.data.repository.RestoreResult
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val backupData: BackupData? = null,
    val backupSuccess: Boolean = false,
    val restoreSuccess: Boolean = false,
    val restoreResult: RestoreResult? = null,
    val previewBackupData: BackupData? = null,
    val error: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "BackupVM"
    }

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var currentGroupId: String? = null
    private var currentUserId: String? = null
    private var currentUserName: String? = null

    init {
        AppLogger.i(TAG, "ViewModel 초기화")
        loadUserInfo()
    }

    private fun loadUserInfo() {
        AppLogger.d(TAG, "사용자 정보 로드 시작")
        viewModelScope.launch {
            currentGroupId = userPreferences.getGroupId()
            currentUserId = userPreferences.getUserId()
            currentUserName = userPreferences.getUserName()
            AppLogger.d(TAG, "사용자 정보 로드 완료: userId=$currentUserId, groupId=$currentGroupId")
        }
    }

    /**
     * ë°±ì ?°ì´???ì±
     */
    fun createBackup() {
        AppLogger.userAction(TAG, "백업 생성 요청")
        viewModelScope.launch {
            val groupId = currentGroupId
            val userId = currentUserId
            val userName = currentUserName
            AppLogger.d(TAG, "백업 생성: groupId=$groupId, userId=$userId")

            if (groupId == null || userId == null) {
                AppLogger.w(TAG, "백업 생성 실패: 로그인 정보 없음")
                _uiState.value = _uiState.value.copy(error = "ë¡ê·¸???ë³´ë¥?ì°¾ì ???ìµ?ë¤")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = backupRepository.createBackup(
                groupId = groupId,
                userId = userId,
                userName = userName ?: ""
            )

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "createBackup", "백업 데이터 생성 완료")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    backupData = result.getOrNull()
                )
            } else {
                AppLogger.apiError(TAG, "createBackup", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "ë°±ì ?ì± ?¤í¨: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * ë°±ì ?°ì´?°ë? ?ì¼ë¡??
*/
    fun saveBackupToUri(uri: Uri) {
        AppLogger.userAction(TAG, "백업 파일 저장", "uri=$uri")
        viewModelScope.launch {
            val backupData = _uiState.value.backupData
            if (backupData == null) {
                AppLogger.w(TAG, "백업 저장 실패: 백업 데이터 없음")
                _uiState.value = _uiState.value.copy(error = "ë°±ì ?°ì´?°ê? ?ìµ?ë¤")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = backupRepository.saveBackupToUri(backupData, uri)

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "saveBackupToUri", "파일 저장 성공")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    backupSuccess = true
                )
            } else {
                AppLogger.apiError(TAG, "saveBackupToUri", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "?ì¼ ????¤í¨: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * ë°±ì ?ì¼ ë¯¸ë¦¬ë³´ê¸° (ë³µì ???ì¸)
     */
    fun previewBackupFile(uri: Uri) {
        AppLogger.userAction(TAG, "백업 파일 미리보기", "uri=$uri")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = backupRepository.readBackupFromUri(uri)

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "previewBackupFile", "백업 파일 읽기 성공")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    previewBackupData = result.getOrNull()
                )
            } else {
                AppLogger.apiError(TAG, "previewBackupFile", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "?ì¼ ?½ê¸° ?¤í¨: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * ë°±ì ?°ì´??ë³µì
     */
    fun restoreBackup() {
        AppLogger.userAction(TAG, "백업 복원 시작")
        viewModelScope.launch {
            val backupData = _uiState.value.previewBackupData
            val groupId = currentGroupId
            val userId = currentUserId
            val userName = currentUserName

            if (backupData == null) {
                AppLogger.w(TAG, "복원 실패: 백업 데이터 없음")
                _uiState.value = _uiState.value.copy(error = "ë³µì??ë°±ì ?°ì´?°ê? ?ìµ?ë¤")
                return@launch
            }

            if (groupId == null || userId == null) {
                AppLogger.w(TAG, "복원 실패: 로그인 정보 없음")
                _uiState.value = _uiState.value.copy(error = "ë¡ê·¸???ë³´ë¥?ì°¾ì ???ìµ?ë¤")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)
            AppLogger.apiStart(TAG, "restoreBackup", "groupId=$groupId, userId=$userId")

            val result = backupRepository.restoreBackup(
                backupData = backupData,
                targetGroupId = groupId,
                targetUserId = userId,
                targetUserName = userName ?: ""
            )

            if (result.isSuccess) {
                AppLogger.apiSuccess(TAG, "restoreBackup", "복원 성공")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    restoreSuccess = true,
                    restoreResult = result.getOrNull()
                )
            } else {
                AppLogger.apiError(TAG, "restoreBackup", result.exceptionOrNull()?.message ?: "Unknown error")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "ë³µì ?¤í¨: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * ë°±ì ?ì¼ ?´ë¦ ?ì±
     */
    fun generateBackupFileName(): String {
        val fileName = backupRepository.generateBackupFileName()
        AppLogger.d(TAG, "백업 파일명 생성: $fileName")
        return fileName
    }

    fun clearBackupSuccess() {
        AppLogger.d(TAG, "백업 성공 상태 초기화")
        _uiState.value = _uiState.value.copy(
            backupSuccess = false,
            backupData = null
        )
    }

    fun clearRestoreSuccess() {
        AppLogger.d(TAG, "복원 성공 상태 초기화")
        _uiState.value = _uiState.value.copy(
            restoreSuccess = false,
            restoreResult = null,
            previewBackupData = null
        )
    }

    fun clearPreview() {
        _uiState.value = _uiState.value.copy(previewBackupData = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
