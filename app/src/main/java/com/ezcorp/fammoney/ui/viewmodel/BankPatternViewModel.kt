package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.CustomBankPattern
import com.ezcorp.fammoney.data.model.PatternTestResult
import com.ezcorp.fammoney.data.repository.BankPatternRepository
import com.ezcorp.fammoney.util.AppLogger
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

    companion object {
        private const val TAG = "BankPatternVM"
    }

    private val _uiState = MutableStateFlow(BankPatternUiState())
    val uiState: StateFlow<BankPatternUiState> = _uiState.asStateFlow()

    init {
        AppLogger.i(TAG, "ViewModel 초기화")
        loadPatterns()
    }

    private fun loadPatterns() {
        AppLogger.d(TAG, "패턴 목록 로드 시작")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                bankPatternRepository.patternsFlow.collect { patterns ->
                    AppLogger.dataLoaded(TAG, "은행 패턴", patterns.size)
                    _uiState.value = _uiState.value.copy(
                        patterns = patterns,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "패턴 로드 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun loadPattern(patternId: String) {
        AppLogger.d(TAG, "패턴 로드: patternId=$patternId")
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
        AppLogger.d(TAG, "편집 패턴 업데이트: id=${pattern.id}, name=${pattern.displayName}")
        _uiState.value = _uiState.value.copy(editingPattern = pattern)
    }

    fun savePattern() {
        val pattern = _uiState.value.editingPattern ?: return
        AppLogger.userAction(TAG, "패턴 저장", "id=${pattern.id}, bank=${pattern.displayName}")

        viewModelScope.launch {
            try {
                bankPatternRepository.savePattern(pattern)
                AppLogger.apiSuccess(TAG, "savePattern", "패턴 저장 성공")
                _uiState.value = _uiState.value.copy(saveSuccess = true)
            } catch (e: Exception) {
                AppLogger.e(TAG, "패턴 저장 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deletePattern(patternId: String) {
        AppLogger.userAction(TAG, "패턴 삭제", "patternId=$patternId")
        viewModelScope.launch {
            try {
                bankPatternRepository.deletePattern(patternId)
                AppLogger.apiSuccess(TAG, "deletePattern", "패턴 삭제 성공: $patternId")
            } catch (e: Exception) {
                AppLogger.e(TAG, "패턴 삭제 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun togglePatternEnabled(patternId: String) {
        AppLogger.userAction(TAG, "패턴 활성화 토글", "patternId=$patternId")
        viewModelScope.launch {
            try {
                bankPatternRepository.togglePatternEnabled(patternId)
                AppLogger.d(TAG, "패턴 활성화 토글 완료: $patternId")
            } catch (e: Exception) {
                AppLogger.e(TAG, "패턴 토글 실패: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun resetToDefaults() {
        AppLogger.userAction(TAG, "패턴 기본값 초기화")
        viewModelScope.launch {
            try {
                bankPatternRepository.resetToDefaults()
                AppLogger.apiSuccess(TAG, "resetToDefaults", "기본값 초기화 완료")
            } catch (e: Exception) {
                AppLogger.e(TAG, "기본값 초기화 실패: ${e.message}", e)
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
        AppLogger.userAction(TAG, "패턴 테스트", "bank=${pattern.displayName}, textLen=${testText.length}")

        if (testText.isBlank()) {
            _uiState.value = _uiState.value.copy(
                testResult = PatternTestResult(
                    success = false,
                    errorMessage = "?뚯뒪?명븷 臾몄옄瑜??낅젰?댁＜?몄슂"
                )
            )
            return
        }

        val result = bankPatternRepository.testPattern(pattern, testText)
        AppLogger.d(TAG, "패턴 테스트 결과: success=${result.success}")
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
