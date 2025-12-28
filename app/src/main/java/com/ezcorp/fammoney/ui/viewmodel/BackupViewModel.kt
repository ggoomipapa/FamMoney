package com.ezcorp.fammoney.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.BackupData
import com.ezcorp.fammoney.data.repository.BackupRepository
import com.ezcorp.fammoney.data.repository.RestoreResult
import com.ezcorp.fammoney.service.UserPreferences
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

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var currentGroupId: String? = null
    private var currentUserId: String? = null
    private var currentUserName: String? = null

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            currentGroupId = userPreferences.getGroupId()
            currentUserId = userPreferences.getUserId()
            currentUserName = userPreferences.getUserName()
        }
    }

    /**
     * ë°±ì ?°ì´???ì±
     */
    fun createBackup() {
        viewModelScope.launch {
            val groupId = currentGroupId
            val userId = currentUserId
            val userName = currentUserName

            if (groupId == null || userId == null) {
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
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    backupData = result.getOrNull()
                )
            } else {
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
        viewModelScope.launch {
            val backupData = _uiState.value.backupData
            if (backupData == null) {
                _uiState.value = _uiState.value.copy(error = "ë°±ì ?°ì´?°ê? ?ìµ?ë¤")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = backupRepository.saveBackupToUri(backupData, uri)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    backupSuccess = true
                )
            } else {
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = backupRepository.readBackupFromUri(uri)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    previewBackupData = result.getOrNull()
                )
            } else {
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
        viewModelScope.launch {
            val backupData = _uiState.value.previewBackupData
            val groupId = currentGroupId
            val userId = currentUserId
            val userName = currentUserName

            if (backupData == null) {
                _uiState.value = _uiState.value.copy(error = "ë³µì??ë°±ì ?°ì´?°ê? ?ìµ?ë¤")
                return@launch
            }

            if (groupId == null || userId == null) {
                _uiState.value = _uiState.value.copy(error = "ë¡ê·¸???ë³´ë¥?ì°¾ì ???ìµ?ë¤")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = backupRepository.restoreBackup(
                backupData = backupData,
                targetGroupId = groupId,
                targetUserId = userId,
                targetUserName = userName ?: ""
            )

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    restoreSuccess = true,
                    restoreResult = result.getOrNull()
                )
            } else {
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
        return backupRepository.generateBackupFileName()
    }

    fun clearBackupSuccess() {
        _uiState.value = _uiState.value.copy(
            backupSuccess = false,
            backupData = null
        )
    }

    fun clearRestoreSuccess() {
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
