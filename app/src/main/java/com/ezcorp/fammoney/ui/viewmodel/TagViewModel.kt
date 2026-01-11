package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionTag
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.data.repository.TagRepository
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.service.UserPreferences
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagUiState(
    val isLoading: Boolean = true,
    val tags: List<TransactionTag> = emptyList(),
    val activeTagId: String? = null,
    val activeTagName: String? = null,
    val selectedTag: TransactionTag? = null,
    val tagTransactions: List<Transaction> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class TagViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagUiState())
    val uiState: StateFlow<TagUiState> = _uiState.asStateFlow()

    init {
        loadActiveTag()
        loadTags()
    }

    private fun loadActiveTag() {
        viewModelScope.launch {
            val activeTagId = userPreferences.getActiveTagId()
            val activeTagName = userPreferences.getActiveTagName()
            _uiState.value = _uiState.value.copy(
                activeTagId = activeTagId,
                activeTagName = activeTagName
            )
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch

            tagRepository.getTagsByGroup(groupId).collect { tags ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    tags = tags
                )
            }
        }
    }

    /**
     * 새 태그 생성
     */
    fun createTag(name: String, color: String, icon: String) {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch

            val newTag = TransactionTag(
                groupId = groupId,
                name = name,
                color = color,
                icon = icon,
                isActive = false,
                createdAt = Timestamp.now()
            )

            tagRepository.createTag(newTag)
        }
    }

    /**
     * 태그 활성화/비활성화
     */
    fun toggleTagActive(tag: TransactionTag) {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch
            val newActiveState = !tag.isActive

            // DB 업데이트
            tagRepository.setTagActive(tag.id, groupId, newActiveState)

            // UserPreferences 업데이트
            if (newActiveState) {
                userPreferences.saveActiveTag(tag.id, tag.name)
                _uiState.value = _uiState.value.copy(
                    activeTagId = tag.id,
                    activeTagName = tag.name
                )
            } else {
                userPreferences.clearActiveTag()
                _uiState.value = _uiState.value.copy(
                    activeTagId = null,
                    activeTagName = null
                )
            }
        }
    }

    /**
     * 태그 삭제
     */
    fun deleteTag(tag: TransactionTag) {
        viewModelScope.launch {
            // 활성 태그인 경우 비활성화
            if (tag.isActive) {
                userPreferences.clearActiveTag()
                _uiState.value = _uiState.value.copy(
                    activeTagId = null,
                    activeTagName = null
                )
            }

            tagRepository.deleteTag(tag.id)
        }
    }

    /**
     * 태그 상세 보기 (해당 태그의 거래내역 로드)
     */
    fun selectTag(tag: TransactionTag) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedTag = tag)
            loadTagTransactions(tag.id)
        }
    }

    /**
     * 태그 상세 닫기
     */
    fun clearSelectedTag() {
        _uiState.value = _uiState.value.copy(
            selectedTag = null,
            tagTransactions = emptyList()
        )
    }

    /**
     * 태그된 거래내역 로드
     */
    private fun loadTagTransactions(tagId: String) {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch

            // 모든 거래 중 해당 태그가 붙은 것만 필터링
            transactionRepository.getTransactionsByGroup(groupId).collect { transactions ->
                val filtered = transactions.filter { it.tagId == tagId }
                    .sortedByDescending { it.transactionDate?.toDate()?.time ?: 0 }

                _uiState.value = _uiState.value.copy(tagTransactions = filtered)

                // 태그 통계 업데이트
                updateTagStats(tagId, filtered)
            }
        }
    }

    /**
     * 태그 통계 업데이트
     */
    private suspend fun updateTagStats(tagId: String, transactions: List<Transaction>) {
        val totalExpense = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val totalIncome = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }

        tagRepository.updateTagStats(
            tagId = tagId,
            transactionCount = transactions.size,
            totalExpense = totalExpense,
            totalIncome = totalIncome
        )
    }

    /**
     * 거래내역에 태그 추가
     */
    fun addTagToTransactions(transactionIds: List<String>, tag: TransactionTag) {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch

            transactionRepository.getTransactionsByGroup(groupId).first().let { allTransactions ->
                transactionIds.forEach { txId ->
                    val transaction = allTransactions.find { it.id == txId }
                    if (transaction != null) {
                        val updatedTransaction = transaction.copy(
                            tagId = tag.id,
                            tagName = tag.name
                        )
                        transactionRepository.updateTransaction(updatedTransaction)
                    }
                }
            }
        }
    }

    /**
     * 거래내역에서 태그 제거
     */
    fun removeTagFromTransaction(transactionId: String) {
        viewModelScope.launch {
            val groupId = userPreferences.getGroupId() ?: return@launch

            transactionRepository.getTransactionsByGroup(groupId).first().let { allTransactions ->
                val transaction = allTransactions.find { it.id == transactionId }
                if (transaction != null) {
                    val updatedTransaction = transaction.copy(
                        tagId = "",
                        tagName = ""
                    )
                    transactionRepository.updateTransaction(updatedTransaction)
                }
            }
        }
    }
}
