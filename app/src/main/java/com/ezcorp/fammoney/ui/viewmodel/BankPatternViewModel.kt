package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.CustomBankPattern
import com.ezcorp.fammoney.data.model.PatternTestResult
import com.ezcorp.fammoney.data.repository.BankPatternRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BankPatternUiState(
    val patterns: List<CustomBankPattern> = emptyList(),
    val isLoading: Boolean = true,
    val editingPattern: CustomBankPattern? = null,
    val testResult: PatternTestResult? = null,
    val testText: String = "",
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class BankPatternViewModel @Inject constructor(
    private val bankPatternRepository: BankPatternRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankPatternUiState())
    val uiState: StateFlow<BankPatternUiState> = _uiState.asStateFlow()

    init {
        loadPatterns()
    }

    private fun loadPatterns() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                bankPatternRepository.patternsFlow.collect { patterns ->
                    _uiState.value = _uiState.value.copy(
                        patterns = patterns,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun loadPattern(patternId: String) {
        viewModelScope.launch {
            val pattern = if (patternId == "new") {
                bankPatternRepository.createNewPatternTemplate()
            } else {
                bankPatternRepository.getPattern(patternId)
            }
            _uiState.value = _uiState.value.copy(editingPattern = pattern)
        }
    }

    fun updateEditingPattern(pattern: CustomBankPattern) {
        _uiState.value = _uiState.value.copy(editingPattern = pattern)
    }

    fun savePattern() {
        val pattern = _uiState.value.editingPattern ?: return

        viewModelScope.launch {
            try {
                bankPatternRepository.savePattern(pattern)
                _uiState.value = _uiState.value.copy(saveSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deletePattern(patternId: String) {
        viewModelScope.launch {
            try {
                bankPatternRepository.deletePattern(patternId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun togglePatternEnabled(patternId: String) {
        viewModelScope.launch {
            try {
                bankPatternRepository.togglePatternEnabled(patternId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                bankPatternRepository.resetToDefaults()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateTestText(text: String) {
        _uiState.value = _uiState.value.copy(testText = text)
    }

    fun testPattern() {
        val pattern = _uiState.value.editingPattern ?: return
        val testText = _uiState.value.testText

        if (testText.isBlank()) {
            _uiState.value = _uiState.value.copy(
                testResult = PatternTestResult(
                    success = false,
                    errorMessage = "?åÏä§?∏Ìï† Î¨∏ÏûêÎ•??ÖÎ†•?¥Ï£º?∏Ïöî"
                )
            )
            return
        }

        val result = bankPatternRepository.testPattern(pattern, testText)
        _uiState.value = _uiState.value.copy(testResult = result)
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}
