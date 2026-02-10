package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.ezcorp.fammoney.data.repository.DuplicateRepository
import com.ezcorp.fammoney.service.DuplicateDetectionService
import com.ezcorp.fammoney.service.UserPreferences
import com.ezcorp.fammoney.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingDuplicatesUiState(
    val duplicates: List<PendingDuplicate> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val resolvedCount: Int = 0
)

@HiltViewModel
class PendingDuplicatesViewModel @Inject constructor(
    private val duplicateRepository: DuplicateRepository,
    private val duplicateDetectionService: DuplicateDetectionService,
    private val userPreferences: UserPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "PendingDupVM"
    }

    private val _uiState = MutableStateFlow(PendingDuplicatesUiState())
    val uiState: StateFlow<PendingDuplicatesUiState> = _uiState.asStateFlow()

    init {
        AppLogger.i(TAG, "ViewModel 초기화")
        loadPendingDuplicates()
    }

    fun loadPendingDuplicates() {
        AppLogger.d(TAG, "중복 거래 목록 로드 시작")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val groupId = userPreferences.getGroupId() ?: run {
                    AppLogger.w(TAG, "중복 목록 로드 중단: groupId 없음")
                    return@launch
                }
                val duplicates = duplicateRepository.getUnresolvedDuplicates(groupId)
                AppLogger.dataLoaded(TAG, "미해결 중복 거래", duplicates.size)
                _uiState.value = _uiState.value.copy(
                    duplicates = duplicates,
                    isLoading = false
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "중복 목록 로드 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "遺덈윭?ㅺ린 ?ㅽ뙣: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun resolveDuplicate(
        duplicate: PendingDuplicate,
        resolution: DuplicateResolution,
        applyToFuture: Boolean
    ) {
        AppLogger.userAction(TAG, "중복 해결", "duplicateId=${duplicate.id}, resolution=$resolution, applyToFuture=$applyToFuture")
        viewModelScope.launch {
            try {
                duplicateDetectionService.resolveDuplicate(
                    duplicateId = duplicate.id,
                    resolution = resolution,
                    transaction1Id = duplicate.transaction1.transactionId,
                    transaction2Id = duplicate.transaction2.transactionId,
                    bank1Id = duplicate.transaction1.bankId,
                    bank2Id = duplicate.transaction2.bankId,
                    groupId = duplicate.groupId,
                    applyToFuture = applyToFuture
                )

                // 紐⑸줉?먯꽌 ?닿껐????ぉ ?쒓굅
                AppLogger.apiSuccess(TAG, "resolveDuplicate", "중복 해결 완료: ${duplicate.id}")
                val updatedDuplicates = _uiState.value.duplicates.filter { it.id != duplicate.id }
                _uiState.value = _uiState.value.copy(
                    duplicates = updatedDuplicates,
                    resolvedCount = _uiState.value.resolvedCount + 1
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "중복 해결 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    error = "泥섎━ ?ㅽ뙣: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
