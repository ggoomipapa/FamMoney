package com.ezcorp.fammoney.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.Child
import com.ezcorp.fammoney.data.model.ChildExpense
import com.ezcorp.fammoney.data.model.ChildIncome
import com.ezcorp.fammoney.data.model.ExpenseCategory
import com.ezcorp.fammoney.data.model.IncomeGiverType
import com.ezcorp.fammoney.data.repository.ChildIncomeRepository
import com.ezcorp.fammoney.service.UserPreferences
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

// 수입/지출 탭 구분
enum class ChildTransactionTab {
    INCOME, EXPENSE
}

private const val TAG = "ChildIncomeViewModel"

data class ChildIncomeUiState(
    val isLoading: Boolean = true,
    val children: List<Child> = emptyList(),
    val selectedChild: Child? = null,
    val childIncomes: List<ChildIncome> = emptyList(),
    val childExpenses: List<ChildExpense> = emptyList(),
    val allChildIncomes: List<ChildIncome> = emptyList(),
    val allChildExpenses: List<ChildExpense> = emptyList(),
    val currentTab: ChildTransactionTab = ChildTransactionTab.INCOME,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

@HiltViewModel
class ChildIncomeViewModel @Inject constructor(
    private val childIncomeRepository: ChildIncomeRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildIncomeUiState())
    val uiState: StateFlow<ChildIncomeUiState> = _uiState.asStateFlow()

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
            Log.d(TAG, "loadUserInfo: groupId=$currentGroupId, userId=$currentUserId, userName=$currentUserName")
            currentGroupId?.let { loadChildren(it) }
        }
    }

    private fun loadChildren(groupId: String) {
        viewModelScope.launch {
            childIncomeRepository.getChildrenByGroup(groupId).collect { children ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    children = children
                )
            }
        }

        viewModelScope.launch {
            childIncomeRepository.getChildIncomesByGroup(groupId).collect { incomes ->
                _uiState.value = _uiState.value.copy(
                    allChildIncomes = incomes
                )
            }
        }

        viewModelScope.launch {
            childIncomeRepository.getChildExpensesByGroup(groupId).collect { expenses ->
                _uiState.value = _uiState.value.copy(
                    allChildExpenses = expenses
                )
            }
        }
    }

    fun selectChild(child: Child?) {
        _uiState.value = _uiState.value.copy(selectedChild = child)
        if (child != null) {
            loadChildIncomes(child.id)
            loadChildExpenses(child.id)
        }
    }

    fun setCurrentTab(tab: ChildTransactionTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    private fun loadChildIncomes(childId: String) {
        viewModelScope.launch {
            childIncomeRepository.getChildIncomesByChild(childId).collect { incomes ->
                _uiState.value = _uiState.value.copy(childIncomes = incomes)
            }
        }
    }

    private fun loadChildExpenses(childId: String) {
        viewModelScope.launch {
            childIncomeRepository.getChildExpensesByChild(childId).collect { expenses ->
                _uiState.value = _uiState.value.copy(childExpenses = expenses)
            }
        }
    }

    fun addChild(name: String) {
        viewModelScope.launch {
            Log.d(TAG, "addChild 호출됨: name=$name")
            val groupId = currentGroupId
            if (groupId == null) {
                Log.e(TAG, "addChild 실패: groupId가 null입니다")
                _uiState.value = _uiState.value.copy(error = "그룹 정보를 찾을 수 없습니다")
                return@launch
            }
            Log.d(TAG, "addChild: groupId=$groupId 로 자녀 추가 시도")
            val child = Child(
                groupId = groupId,
                name = name,
                totalIncome = 0,
                createdAt = Timestamp.now()
            )
            val result = childIncomeRepository.addChild(child)
            if (result.isSuccess) {
                Log.d(TAG, "addChild 성공: ${result.getOrNull()}")
                // FIX: Manually update the state for immediate UI feedback
                val newChildId = result.getOrNull()
                if (newChildId != null) {
                    val newChild = child.copy(id = newChildId)
                    _uiState.value = _uiState.value.copy(
                        children = _uiState.value.children + newChild,
                        selectedChild = newChild
                    )
                }
            } else {
                Log.e(TAG, "addChild 실패: ${result.exceptionOrNull()?.message}")
                _uiState.value = _uiState.value.copy(
                    error = "자녀 추가 실패: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun updateChild(child: Child) {
        viewModelScope.launch {
            childIncomeRepository.updateChild(child)
        }
    }

    fun deleteChild(childId: String) {
        viewModelScope.launch {
            childIncomeRepository.deleteChild(childId)
            if (_uiState.value.selectedChild?.id == childId) {
                _uiState.value = _uiState.value.copy(selectedChild = null, childIncomes = emptyList())
            }
        }
    }

    fun addChildIncome(
        childId: String,
        childName: String,
        amount: Long,
        giverType: IncomeGiverType,
        giverName: String,
        memo: String,
        incomeDate: Date = Date()
    ) {
        viewModelScope.launch {
            val groupId = currentGroupId ?: return@launch
            val userId = currentUserId ?: return@launch
            val userName = currentUserName ?: ""

            val income = ChildIncome(
                groupId = groupId,
                childId = childId,
                childName = childName,
                amount = amount,
                giverType = giverType,
                giverName = if (giverType == IncomeGiverType.OTHER) giverName else "",
                memo = memo,
                recordedByUserId = userId,
                recordedByUserName = userName,
                incomeDate = Timestamp(incomeDate)
            )

            val result = childIncomeRepository.addChildIncome(income)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(saveSuccess = true)
                // 선택된 자녀의 잔액 업데이트
                refreshSelectedChild(childId)
            } else {
                _uiState.value = _uiState.value.copy(error = "저장에 실패했습니다")
            }
        }
    }

    private fun refreshSelectedChild(childId: String) {
        viewModelScope.launch {
            val child = childIncomeRepository.getChildById(childId)
            if (child != null) {
                _uiState.value = _uiState.value.copy(selectedChild = child)
            }
        }
    }

    fun deleteChildIncome(incomeId: String, childId: String, amount: Long) {
        viewModelScope.launch {
            childIncomeRepository.deleteChildIncome(incomeId, childId, amount)
        }
    }

    // ===== 자녀 지출 관련 =====

    fun addChildExpense(
        childId: String,
        childName: String,
        amount: Long,
        category: ExpenseCategory,
        description: String,
        memo: String,
        expenseDate: Date = Date()
    ) {
        viewModelScope.launch {
            val groupId = currentGroupId ?: return@launch
            val userId = currentUserId ?: return@launch
            val userName = currentUserName ?: ""

            val expense = ChildExpense(
                groupId = groupId,
                childId = childId,
                childName = childName,
                amount = amount,
                category = category,
                description = description,
                memo = memo,
                recordedByUserId = userId,
                recordedByUserName = userName,
                expenseDate = Timestamp(expenseDate)
            )

            val result = childIncomeRepository.addChildExpense(expense)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(saveSuccess = true)
                // 선택된 자녀의 잔액 업데이트
                refreshSelectedChild(childId)
            } else {
                _uiState.value = _uiState.value.copy(error = "저장에 실패했습니다")
            }
        }
    }

    fun deleteChildExpense(expenseId: String, childId: String, amount: Long) {
        viewModelScope.launch {
            childIncomeRepository.deleteChildExpense(expenseId, childId, amount)
        }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // 수입원 기준 사람별 통계
    fun getIncomeByGiver(): Map<IncomeGiverType, Long> {
        val selectedChild = _uiState.value.selectedChild ?: return emptyMap()
        return _uiState.value.childIncomes
            .filter { it.childId == selectedChild.id }
            .groupBy { it.giverType }
            .mapValues { (_, incomes) -> incomes.sumOf { it.amount } }
    }

    // 지출 카테고리별 통계
    fun getExpenseByCategory(): Map<ExpenseCategory, Long> {
        val selectedChild = _uiState.value.selectedChild ?: return emptyMap()
        return _uiState.value.childExpenses
            .filter { it.childId == selectedChild.id }
            .groupBy { it.category }
            .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
    }

    // ===== 용돈 관련 (Allowance 통합) =====

    /**
     * 용돈 설정
     * @param childId 자녀 ID
     * @param amount 정기 용돈 금액
     * @param frequency 지급 주기 ("weekly" | "monthly")
     */
    fun setAllowance(childId: String, amount: Long, frequency: String) {
        viewModelScope.launch {
            Log.d(TAG, "setAllowance: childId=$childId, amount=$amount, frequency=$frequency")
            val result = childIncomeRepository.setAllowance(childId, amount, frequency)
            if (result.isSuccess) {
                refreshSelectedChild(childId)
            } else {
                _uiState.value = _uiState.value.copy(error = "용돈 설정 실패: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * 용돈 시작 (적립 단계 → 용돈 단계 전환)
     * 현재까지 모은 금액을 적립금으로 고정하고 용돈 관리를 시작합니다
     * @param childId 자녀 ID
     */
    fun startAllowance(childId: String) {
        viewModelScope.launch {
            Log.d(TAG, "startAllowance: childId=$childId")
            val result = childIncomeRepository.startAllowance(childId)
            if (result.isSuccess) {
                refreshSelectedChild(childId)
            } else {
                _uiState.value = _uiState.value.copy(error = "용돈 시작 실패: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * 용돈 주기 (부모가 자녀에게 용돈 지급)
     * @param childId 자녀 ID
     * @param amount 지급 금액
     */
    fun giveAllowance(childId: String, amount: Long) {
        viewModelScope.launch {
            val userId = currentUserId ?: ""
            val userName = currentUserName ?: ""

            Log.d(TAG, "giveAllowance: childId=$childId, amount=$amount")
            val result = childIncomeRepository.giveAllowance(childId, amount, userId, userName)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(saveSuccess = true)
                refreshSelectedChild(childId)
            } else {
                _uiState.value = _uiState.value.copy(error = "용돈 지급 실패: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * 용돈 취소 (용돈 단계 → 적립 단계로 되돌리기)
     * @param childId 자녀 ID
     */
    fun cancelAllowance(childId: String) {
        viewModelScope.launch {
            Log.d(TAG, "cancelAllowance: childId=$childId")
            val result = childIncomeRepository.cancelAllowance(childId)
            if (result.isSuccess) {
                refreshSelectedChild(childId)
            } else {
                _uiState.value = _uiState.value.copy(error = "용돈 취소 실패: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
