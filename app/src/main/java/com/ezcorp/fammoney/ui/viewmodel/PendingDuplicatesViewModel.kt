package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.ezcorp.fammoney.data.repository.DuplicateRepository
import com.ezcorp.fammoney.service.DuplicateDetectionService
import com.ezcorp.fammoney.service.UserPreferences
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

    private val _uiState = MutableStateFlow(PendingDuplicatesUiState())
    val uiState: StateFlow<PendingDuplicatesUiState> = _uiState.asStateFlow()

    init {
        loadPendingDuplicates()
    }

    fun loadPendingDuplicates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val groupId = userPreferences.getGroupId() ?: return@launch
                val duplicates = duplicateRepository.getUnresolvedDuplicates(groupId)
                _uiState.value = _uiState.value.copy(
                    duplicates = duplicates,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Î∂àÎü¨?§Í∏∞ ?§Ìå®: ${e.message}",
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

                // Î™©Î°ù?êÏÑú ?¥Í≤∞????™© ?úÍ±∞
                val updatedDuplicates = _uiState.value.duplicates.filter { it.id != duplicate.id }
                _uiState.value = _uiState.value.copy(
                    duplicates = updatedDuplicates,
                    resolvedCount = _uiState.value.resolvedCount + 1
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Ï≤òÎ¶¨ ?§Ìå®: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
