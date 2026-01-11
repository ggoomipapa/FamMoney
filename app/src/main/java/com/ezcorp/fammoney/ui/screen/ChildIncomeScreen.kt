package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.Child
import com.ezcorp.fammoney.data.model.ChildExpense
import com.ezcorp.fammoney.data.model.ChildIncome
import com.ezcorp.fammoney.data.model.ExpenseCategory
import com.ezcorp.fammoney.data.model.IncomeGiverType
import com.ezcorp.fammoney.ui.theme.ExpenseColor
import com.ezcorp.fammoney.ui.theme.IncomeColor
import com.ezcorp.fammoney.ui.viewmodel.ChildIncomeViewModel
import com.ezcorp.fammoney.ui.viewmodel.ChildTransactionTab
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildIncomeScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChildIncomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showAddChildDialog by remember { mutableStateOf(false) }
    var showAddIncomeDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showEditChildDialog by remember { mutableStateOf(false) }
    var showDeleteChildDialog by remember { mutableStateOf(false) }
    var showDeleteIncomeDialog by remember { mutableStateOf<ChildIncome?>(null) }
    var showDeleteExpenseDialog by remember { mutableStateOf<ChildExpense?>(null) }

    // 용돈 관리??¤ì´?¼ë¡ê·??í
    var showSetAllowanceDialog by remember { mutableStateOf(false) }
    var showStartAllowanceDialog by remember { mutableStateOf(false) }
    var showGiveAllowanceDialog by remember { mutableStateOf(false) }
    var showCancelAllowanceDialog by remember { mutableStateOf(false) }

    var showErrorSnackbar by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            showAddIncomeDialog = false
            showAddExpenseDialog = false
            viewModel.clearSaveSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            errorMessage = uiState.error!!
            showErrorSnackbar = true
            viewModel.clearError()
        }
    }

    if (showErrorSnackbar) {
        AlertDialog(
            onDismissRequest = { showErrorSnackbar = false },
            title = { Text("자녀") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { showErrorSnackbar = false }) {
                    Text("주기")
                }
            }
        )
    }

    if (showAddChildDialog) {
        AddChildDialog(
            onDismiss = { showAddChildDialog = false },
            onConfirm = { name ->
                viewModel.addChild(name)
                showAddChildDialog = false
            }
        )
    }

    if (showEditChildDialog && uiState.selectedChild != null) {
        EditChildDialog(
            child = uiState.selectedChild!!,
            onDismiss = { showEditChildDialog = false },
            onConfirm = { updatedChild ->
                viewModel.updateChild(updatedChild)
                showEditChildDialog = false
            }
        )
    }

    if (showDeleteChildDialog && uiState.selectedChild != null) {
        AlertDialog(
            onDismissRequest = { showDeleteChildDialog = false },
            title = { Text("자녀 삭제") },
            text = { Text("'${uiState.selectedChild!!.name}'의 수입 기록을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChild(uiState.selectedChild!!.id)
                        showDeleteChildDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChildDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    showDeleteIncomeDialog?.let { income ->
        AlertDialog(
            onDismissRequest = { showDeleteIncomeDialog = null },
            title = { Text("수입 삭제") },
            text = { Text("이 수입 기록을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChildIncome(income.id, income.childId, income.amount)
                        showDeleteIncomeDialog = null
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteIncomeDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    showDeleteExpenseDialog?.let { expense ->
        AlertDialog(
            onDismissRequest = { showDeleteExpenseDialog = null },
            title = { Text("지출?삭제") },
            text = { Text("이 지출 기록을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChildExpense(expense.id, expense.childId, expense.amount)
                        showDeleteExpenseDialog = null
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteExpenseDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (showAddIncomeDialog && uiState.selectedChild != null) {
        AddChildIncomeDialog(
            child = uiState.selectedChild!!,
            onDismiss = { showAddIncomeDialog = false },
            onConfirm = { amount, giverType, giverName, memo ->
                viewModel.addChildIncome(
                    childId = uiState.selectedChild!!.id,
                    childName = uiState.selectedChild!!.name,
                    amount = amount,
                    giverType = giverType,
                    giverName = giverName,
                    memo = memo
                )
            }
        )
    }

    if (showAddExpenseDialog && uiState.selectedChild != null) {
        AddChildExpenseDialog(
            child = uiState.selectedChild!!,
            onDismiss = { showAddExpenseDialog = false },
            onConfirm = { amount, category, description, memo ->
                viewModel.addChildExpense(
                    childId = uiState.selectedChild!!.id,
                    childName = uiState.selectedChild!!.name,
                    amount = amount,
                    category = category,
                    description = description,
                    memo = memo
                )
            }
        )
    }

    // 용돈 설정 ?¤ì´?¼ë¡ê·"
    if (showSetAllowanceDialog && uiState.selectedChild != null) {
        SetAllowanceDialog(
            child = uiState.selectedChild!!,
            onDismiss = { showSetAllowanceDialog = false },
            onConfirm = { amount, frequency ->
                viewModel.setAllowance(uiState.selectedChild!!.id, amount, frequency)
                showSetAllowanceDialog = false
            }
        )
    }

    // 용돈 연결 주기 ?¤ì´?¼ë¡ê·?
    if (showStartAllowanceDialog && uiState.selectedChild != null) {
        StartAllowanceConfirmDialog(
            child = uiState.selectedChild!!,
            onDismiss = { showStartAllowanceDialog = false },
            onConfirm = {
                viewModel.startAllowance(uiState.selectedChild!!.id)
                showStartAllowanceDialog = false
            }
        )
    }

    // 용돈 지급 ?¤ì´?¼ë¡ê·?
    if (showGiveAllowanceDialog && uiState.selectedChild != null) {
        GiveAllowanceDialog(
            child = uiState.selectedChild!!,
            onDismiss = { showGiveAllowanceDialog = false },
            onConfirm = { amount ->
                viewModel.giveAllowance(uiState.selectedChild!!.id, amount)
                showGiveAllowanceDialog = false
            }
        )
    }

    // 용돈 취소 주기 ?¤ì´?¼ë¡ê·?
    if (showCancelAllowanceDialog && uiState.selectedChild != null) {
        AlertDialog(
            onDismissRequest = { showCancelAllowanceDialog = false },
            title = { Text("용돈 관리?취소") },
            text = { Text("용돈 관리를 취소하시겠습니까? 모든 다음 달 기록은 확인 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cancelAllowance(uiState.selectedChild!!.id)
                        showCancelAllowanceDialog = false
                    }
                ) {
                    Text("주기", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelAllowanceDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("자녀 용돈 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddChildDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "자녀 추가")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedChild != null) {
                FloatingActionButton(
                    onClick = {
                        if (uiState.currentTab == ChildTransactionTab.INCOME) {
                            showAddIncomeDialog = true
                        } else {
                            showAddExpenseDialog = true
                        }
                    },
                    containerColor = if (uiState.currentTab == ChildTransactionTab.INCOME) IncomeColor else ExpenseColor
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (uiState.currentTab == ChildTransactionTab.INCOME) "수입 추가" else "지출?추가"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.children.isEmpty()) {
                EmptyChildrenMessage(onAddChild = { showAddChildDialog = true })
            } else {
                // 자녀 선택 칩
                ChildSelectionChips(
                    children = uiState.children,
                    selectedChild = uiState.selectedChild,
                    onChildSelected = viewModel::selectChild,
                    onSelectAll = { viewModel.selectChild(null) }
                )

                // 요약 카드 및 탭
                if (uiState.selectedChild != null) {
                    val child = uiState.selectedChild!!

                    // 용돈 체계일 경우 다음 달 고정 적립금 카드 표시
                    if (child.isAllowanceActive && child.preSavingsAmount > 0) {
                        PreSavingsCard(child = child)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 선택된 자녀 정보 카드 (용돈 관리 버튼 포함)
                    ChildSummaryCardWithAllowance(
                        child = child,
                        onEdit = { showEditChildDialog = true },
                        onDelete = { showDeleteChildDialog = true },
                        onSetAllowance = { showSetAllowanceDialog = true },
                        onStartAllowance = { showStartAllowanceDialog = true },
                        onGiveAllowance = { showGiveAllowanceDialog = true },
                        onCancelAllowance = { showCancelAllowanceDialog = true }
                    )
                } else {
                    // "전체" 선택 시 요약 카드
                    AllChildrenSummaryCard(children = uiState.children)
                }

                // 수입/지출 탭
                TabRow(
                    selectedTabIndex = if (uiState.currentTab == ChildTransactionTab.INCOME) 0 else 1,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Tab(
                        selected = uiState.currentTab == ChildTransactionTab.INCOME,
                        onClick = { viewModel.setCurrentTab(ChildTransactionTab.INCOME) },
                        text = { Text("수입") },
                        selectedContentColor = IncomeColor,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Tab(
                        selected = uiState.currentTab == ChildTransactionTab.EXPENSE,
                        onClick = { viewModel.setCurrentTab(ChildTransactionTab.EXPENSE) },
                        text = { Text("지출") },
                        selectedContentColor = ExpenseColor,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 탭에 따른 내역 리스트
                if (uiState.currentTab == ChildTransactionTab.INCOME) {
                    val incomes = if (uiState.selectedChild == null) uiState.allChildIncomes else uiState.childIncomes
                    if (incomes.isEmpty()) {
                        EmptyIncomesMessage()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(incomes) { income ->
                                ChildIncomeItem(
                                    income = income,
                                    onDelete = { showDeleteIncomeDialog = income }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                } else {
                    val expenses = if (uiState.selectedChild == null) uiState.allChildExpenses else uiState.childExpenses
                    if (expenses.isEmpty()) {
                        EmptyExpensesMessage()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(expenses) { expense ->
                                ChildExpenseItem(
                                    expense = expense,
                                    onDelete = { showDeleteExpenseDialog = expense }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildSelectionChips(
    children: List<Child>,
    selectedChild: Child?,
    onChildSelected: (Child) -> Unit,
    onSelectAll: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedChild == null,
                onClick = onSelectAll,
                label = { Text("전체") },
                leadingIcon = if (selectedChild == null) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
        items(children) { child ->
            FilterChip(
                selected = selectedChild?.id == child.id,
                onClick = { onChildSelected(child) },
                label = { Text(child.name) },
                leadingIcon = if (selectedChild?.id == child.id) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
    }
}

@Composable
fun AllChildrenSummaryCard(children: List<Child>) {
    val totalIncome = children.sumOf { it.totalIncome }
    val totalExpense = children.sumOf { it.totalExpense }
    val totalBalance = children.sumOf { it.balance }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "모든 자녀",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 총 수입
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "총 수입",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+${String.format("%,d", totalIncome)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeColor
                    )
                }

                // 총 지출
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "총 지출",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "-${String.format("%,d", totalExpense)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseColor
                    )
                }

                // 총 잔액
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "총 잔액",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%,d", totalBalance)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (totalBalance >= 0) IncomeColor else ExpenseColor
                    )
                }
            }
        }
    }
}

@Composable
fun ChildSummaryCard(
    child: Child,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChildCare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = child.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "수정", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // ?ì
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "수입",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+${String.format("%,d", child.totalIncome)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeColor
                    )
                }

                // 지출?
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "지출",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "-${String.format("%,d", child.totalExpense)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseColor
                    )
                }

                // 금액
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "금액",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%,d", child.balance)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (child.balance >= 0) IncomeColor else ExpenseColor
                    )
                }
            }
        }
    }
}

@Composable
fun ChildIncomeItem(
    income: ChildIncome,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDelete() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(IncomeColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = income.giverType.icon,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (income.giverType == IncomeGiverType.OTHER && income.giverName.isNotBlank()) {
                        income.giverName
                    } else {
                        income.giverType.displayName
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (income.memo.isNotBlank()) {
                    Text(
                        text = income.memo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                income.incomeDate?.let { timestamp ->
                    val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                    Text(
                        text = dateFormat.format(timestamp.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "+${String.format("%,d", income.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = IncomeColor
            )
        }
    }
}

@Composable
fun EmptyChildrenMessage(onAddChild: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ChildCare,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "수입 내역이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "자녀를 먼저 추가한 후에 수입을 기록할 수 있습니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddChild) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("자녀 추가")
        }
    }
}

@Composable
fun EmptyIncomesMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Savings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "수입 내역이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "최근 수입 + 반복용돈으로 수입을 추가할 수 있습니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChildDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("자녀 추가") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("닫기") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChildDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: (Child) -> Unit
) {
    var name by remember { mutableStateOf(child.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("자녀 정보 수정") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("닫기") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(child.copy(name = name)) },
                enabled = name.isNotBlank()
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChildIncomeDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: (Long, IncomeGiverType, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedGiverType by remember { mutableStateOf<IncomeGiverType?>(null) }
    var customGiverName by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var showGiverTypeSelector by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "${child.name} 수입 추가",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                label = { Text("금액") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text("") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 지급에서 받으세요 - ?ë¡­?¤ì´ 확인??ì§ì  ? í UI
            Text(
                text = "지급에서 받으세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!showGiverTypeSelector) {
                OutlinedCard(
                    onClick = { showGiverTypeSelector = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedGiverType?.let { "${it.icon} ${it.displayName}" } ?: "? í?ì¸",
                            color = if (selectedGiverType == null) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
            } else {
                // ê°ë¨??? í UI
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        IncomeGiverType.allTypes.forEach { type ->
                            GiverTypeSelectItem(type) {
                                selectedGiverType = type
                                showGiverTypeSelector = false
                            }
                        }
                    }
                }
            }

            if (selectedGiverType == IncomeGiverType.OTHER) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customGiverName,
                    onValueChange = { customGiverName = it },
                    label = { Text("닫기 입력") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("메모 (선택)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("취소")
                }
                Button(
                    onClick = {
                        val amount = amountText.toLongOrNull() ?: 0
                        if (amount > 0 && selectedGiverType != null) {
                            onConfirm(amount, selectedGiverType!!, customGiverName, memo)
                        }
                    },
                    enabled = amountText.isNotBlank() &&
                              (amountText.toLongOrNull() ?: 0) > 0 &&
                              selectedGiverType != null &&
                              (selectedGiverType != IncomeGiverType.OTHER || customGiverName.isNotBlank()),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("확인")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun GiverTypeSelectItem(
    type: IncomeGiverType,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = type.icon, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = type.displayName, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiverTypeBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (IncomeGiverType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "지급에서 받으세요",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            IncomeGiverType.allTypes.forEach { type ->
                GiverTypeItem(type = type, onClick = { onSelect(type) })
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun GiverTypeItem(
    type: IncomeGiverType,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = type.icon,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = type.displayName,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ===== 지출?ê´??ì»´í¬?í¸ =====

@Composable
fun ChildExpenseItem(
    expense: ChildExpense,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDelete() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ExpenseColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = expense.category.icon,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (expense.description.isNotBlank()) {
                    Text(
                        text = expense.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                expense.expenseDate?.let { timestamp ->
                    val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                    Text(
                        text = dateFormat.format(timestamp.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "-${String.format("%,d", expense.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ExpenseColor
            )
        }
    }
}

@Composable
fun EmptyExpensesMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "지출 내역이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "최근 지출으로 추가할 수 있습니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

// ?ë? 지출?추가? ?ë©´ ?í
private enum class ChildExpenseScreen {
    MAIN,
    CATEGORY
}

/**
 * ?ë? 지출?추가? ë°í??í¸
 * AlertDialog 확인??ModalBottomSheetë¥??¬ì©?ì¬ ?´ë? ? í UI확인??z-index ì¶©ë ë°©ì"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChildExpenseDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: (Long, ExpenseCategory, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var description by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var currentScreen by remember { mutableStateOf(ChildExpenseScreen.MAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        when (currentScreen) {
            ChildExpenseScreen.MAIN -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "${child.name} 지출?추가",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("금액") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        suffix = { Text("") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedCard(
                        onClick = { currentScreen = ChildExpenseScreen.CATEGORY },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedCategory?.let { "${it.icon} ${it.displayName}" } ?: "다운로드 확인하세요",
                                color = if (selectedCategory == null) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("사용처(선택)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = memo,
                        onValueChange = { memo = it },
                        label = { Text("메모 (선택)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("취소")
                        }
                        Button(
                            onClick = {
                                val amount = amountText.toLongOrNull() ?: 0
                                if (amount > 0 && selectedCategory != null) {
                                    onConfirm(amount, selectedCategory!!, description, memo)
                                }
                            },
                            enabled = amountText.isNotBlank() &&
                                      (amountText.toLongOrNull() ?: 0) > 0 &&
                                      selectedCategory != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("확인")
                        }
                    }
                }
            }

            ChildExpenseScreen.CATEGORY -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentScreen = ChildExpenseScreen.MAIN }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                        }
                        Text(
                            text = "다운로드 확인하세요",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 먹거리
                Text(
                        text = "먹거리",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    ExpenseCategory.foodGroup.forEach { category ->
                        ExpenseCategoryItem(category = category, onClick = {
                            selectedCategory = category
                            currentScreen = ChildExpenseScreen.MAIN
                        })
                    }

                    // 확인에 취소"
                Text(
                        text = "확인에 취소",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    ExpenseCategory.hobbyGroup.forEach { category ->
                        ExpenseCategoryItem(category = category, onClick = {
                            selectedCategory = category
                            currentScreen = ChildExpenseScreen.MAIN
                        })
                    }

                    // 일정
                Text(
                        text = "일정",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    ExpenseCategory.lifestyleGroup.forEach { category ->
                        ExpenseCategoryItem(category = category, onClick = {
                            selectedCategory = category
                            currentScreen = ChildExpenseScreen.MAIN
                        })
                    }

                    // 저축 기록"
                Text(
                        text = "저축 기록",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    ExpenseCategory.otherGroup.forEach { category ->
                        ExpenseCategoryItem(category = category, onClick = {
                            selectedCategory = category
                            currentScreen = ChildExpenseScreen.MAIN
                        })
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ExpenseCategoryItem(
    category: ExpenseCategory,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.icon,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ===== 용돈 관리?ì»´í¬?í¸ =====

/**
 * 다음 달?ê³ ì  ì¹´ë
 * 용돈 ?¨ê³????이전 달다음 달ì ê³ ì  ?ì
 */
@Composable
fun PreSavingsCard(child: Child) {
    val dateFormat = SimpleDateFormat("yyyy.MM", Locale.getDefault())
    val startDate = child.preSavingsStartDate?.toDate()?.let { dateFormat.format(it) } ?: ""
    val endDate = child.preSavingsEndDate?.toDate()?.let { dateFormat.format(it) } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "다음 달",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
                    Text(
                        text = "$startDate ~ $endDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            Text(
                text = "${String.format("%,d", child.preSavingsAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

/**
 * ?ë? 정보 ì¹´ë (용돈 관리?ë²í¼ ?¬í¨)
 */
@Composable
fun ChildSummaryCardWithAllowance(
    child: Child,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetAllowance: () -> Unit,
    onStartAllowance: () -> Unit,
    onGiveAllowance: () -> Unit,
    onCancelAllowance: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // ?¤ë: ?ë? 닫기 + ?¸ì§/삭제 ë²í¼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChildCare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = child.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        // ?ì¬ ?¨ê³ ?ì
                Text(
                            text = if (child.isAllowanceActive) "용돈 관리중" else "저립 중",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (child.isAllowanceActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "수정", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 금액 정보
            if (child.isAllowanceActive) {
                // 용돈 ?¨ê³: 이번 용돈 금액ë§??¬ê² ?ì
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "이번 용돈 금액",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%,d", child.allowanceBalance)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (child.allowanceBalance >= 0) IncomeColor else ExpenseColor
                    )
                    if (child.allowanceAmount > 0) {
                        Text(
                            text = "총 용돈: ${String.format("%,d", child.allowanceAmount)}원/${if (child.allowanceFrequency == "weekly") "주" else "월"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // ?ë¦½ ?¨ê³: 수입/지출?금액 ?ì
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "수입",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "+${String.format("%,d", child.totalIncome)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IncomeColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "지출",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "-${String.format("%,d", child.totalExpense)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "금액",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format("%,d", child.balance)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (child.balance >= 0) IncomeColor else ExpenseColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 용돈 관리?ë²í¼
if (child.isAllowanceActive) {
                // 용돈 ?¨ê³: 용돈 설정, 용돈 지급, 취소
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSetAllowance,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("설정", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = onGiveAllowance,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("용돈 지급", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = onCancelAllowance,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("취소", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // ?ë¦½ ?¨ê³: 용돈 설정 + 용돈 연결
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSetAllowance,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("용돈 설정")
                    }
                    Button(
                        onClick = onStartAllowance,
                        modifier = Modifier.weight(1f),
                        enabled = child.savingsBalance > 0 || child.allowanceAmount > 0
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("용돈 연결")
                    }
                }
            }
        }
    }
}

/**
 * 용돈 설정 ?¤ì´?¼ë¡ê·? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetAllowanceDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var amountText by remember { mutableStateOf(if (child.allowanceAmount > 0) child.allowanceAmount.toString() else "") }
    var frequency by remember { mutableStateOf(child.allowanceFrequency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${child.name} 용돈 설정") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("용돈 금액") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text("") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "지급 기록",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = frequency == "weekly",
                        onClick = { frequency = "weekly" },
                        label = { Text("매주") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = frequency == "monthly",
                        onClick = { frequency = "monthly" },
                        label = { Text("매월") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0
                    if (amount > 0) {
                        onConfirm(amount, frequency)
                    }
                },
                enabled = amountText.isNotBlank() && (amountText.toLongOrNull() ?: 0) > 0
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 용돈 연결 주기 ?¤ì´?¼ë¡ê·? */
@Composable
fun StartAllowanceConfirmDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val currentBalance = child.savingsBalance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("용돈 관리?연결") },
        text = {
            Column {
                Text(
                    text = "이번 기간 지난 달 ${String.format("%,d", currentBalance)}?ì 다음 달ì¼ë¡?확인?¥íê³? 용돈 관리¬ë? 연결?©ë",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "다음 달에 용돈이 지급됩니다\n\n용돈 금액 확인: 0원\n연결됩니다\n\n자녀마다 용돈을 금액을 추가하세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (child.allowanceAmount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "설정??총 용돈: ${String.format("%,d", child.allowanceAmount)}원/${if (child.allowanceFrequency == "weekly") "주" else "월"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("연결하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 용돈 지급 ?¤ì´?¼ë¡ê·? */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiveAllowanceDialog(
    child: Child,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var amountText by remember { mutableStateOf(if (child.allowanceAmount > 0) child.allowanceAmount.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${child.name}의 용돈 지급") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("금액") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text("") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (child.allowanceAmount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { amountText = child.allowanceAmount.toString() },
                            label = { Text("${String.format("%,d", child.allowanceAmount)}") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "이번 용돈 금액: ${String.format("%,d", child.allowanceBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0
                    if (amount > 0) {
                        onConfirm(amount)
                    }
                },
                enabled = amountText.isNotBlank() && (amountText.toLongOrNull() ?: 0) > 0
            ) {
                Text("주기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
