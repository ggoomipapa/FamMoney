package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.SavingsGoal
import com.ezcorp.fammoney.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showContributeDialog by remember { mutableStateOf<SavingsGoal?>(null) }
    var showDeleteDialog by remember { mutableStateOf<SavingsGoal?>(null) }

    // 목표 추가 다이얼로그
    if (showAddDialog) {
        AddSavingsGoalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, amount, emoji, autoDepositEnabled, linkedAccountNumber, linkedBankName ->
                viewModel.createSavingsGoal(
                    name = name,
                    targetAmount = amount,
                    iconEmoji = emoji,
                    autoDepositEnabled = autoDepositEnabled,
                    linkedAccountNumber = linkedAccountNumber,
                    linkedBankName = linkedBankName
                )
                showAddDialog = false
            }
        )
    }

    // 저축 추가 다이얼로그
    showContributeDialog?.let { goal ->
        ContributeDialog(
            goal = goal,
            onDismiss = { showContributeDialog = null },
            onConfirm = { amount ->
                viewModel.addSavingsContribution(goal.id, amount)
                showContributeDialog = null
            }
        )
    }

    // 삭제 확인 다이얼로그
    showDeleteDialog?.let { goal ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("목표 삭제") },
            text = { Text("'${goal.name}' 목표를 삭제하시겠습니까?\n모든 저축 기록도 함께 삭제됩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSavingsGoal(goal.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("목표 저축") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "목표 추가")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.savingsGoals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Savings,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "등록된 목표가 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "가족과 함께 저축 목표를 세워보세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("목표 만들기")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 진행 중인 목표
                val activeGoals = uiState.savingsGoals.filter { !it.isCompleted }
                val completedGoals = uiState.savingsGoals.filter { it.isCompleted }

                if (activeGoals.isNotEmpty()) {
                    item {
                        Text(
                            text = "진행 중",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(activeGoals) { goal ->
                        SavingsGoalCard(
                            goal = goal,
                            onClick = { onNavigateToDetail(goal.id) },
                            onContribute = { showContributeDialog = goal },
                            onDelete = { showDeleteDialog = goal }
                        )
                    }
                }

                if (completedGoals.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "달성 완료",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(completedGoals) { goal ->
                        SavingsGoalCard(
                            goal = goal,
                            onClick = { onNavigateToDetail(goal.id) },
                            onContribute = null,
                            onDelete = { showDeleteDialog = goal }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    onClick: () -> Unit,
    onContribute: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = goal.iconEmoji,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = goal.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (goal.autoDepositEnabled) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "자동 연동",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "목표: ${String.format("%,d", goal.targetAmount)}원",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (goal.autoDepositEnabled && goal.linkedBankName.isNotBlank()) {
                            Text(
                                text = "${goal.linkedBankName} 자동 연동",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (goal.isCompleted) {
                    AssistChip(
                        onClick = {},
                        label = { Text("달성!") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 진행률 바
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${String.format("%,d", goal.currentAmount)}원",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(goal.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = goal.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }

            if (onContribute != null && !goal.isCompleted) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onContribute,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("저축하기")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSavingsGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String, Boolean, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("🎯") }
    var autoDepositEnabled by remember { mutableStateOf(false) }
    var linkedAccountNumber by remember { mutableStateOf("") }
    var linkedBankName by remember { mutableStateOf("") }
    var showBankDropdown by remember { mutableStateOf(false) }

    val emojis = listOf("🎯", "🏠", "✈️", "🚗", "💍", "📱", "💻", "🎓", "👶", "🏥", "💰", "🎁")
    val banks = listOf(
        "국민은행", "신한은행", "우리은행", "하나은행", "농협은행",
        "기업은행", "카카오뱅크", "토스뱅크", "케이뱅크", "SC제일은행",
        "씨티은행", "새마을금고", "신협", "수협", "기타"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 저축 목표") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("목표 이름") },
                    placeholder = { Text("예: 제주도 여행") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("목표 금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("아이콘 선택", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojis.take(6).forEach { emoji ->
                        FilterChip(
                            selected = selectedEmoji == emoji,
                            onClick = { selectedEmoji = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    emojis.drop(6).forEach { emoji ->
                        FilterChip(
                            selected = selectedEmoji == emoji,
                            onClick = { selectedEmoji = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // 자동 입금 감지 설정
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "자동 입금 감지",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "계좌 입금 시 자동으로 반영",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoDepositEnabled,
                        onCheckedChange = { autoDepositEnabled = it }
                    )
                }

                if (autoDepositEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 은행 선택
                    ExposedDropdownMenuBox(
                        expanded = showBankDropdown,
                        onExpandedChange = { showBankDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = linkedBankName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("은행 선택") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showBankDropdown)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = showBankDropdown,
                            onDismissRequest = { showBankDropdown = false }
                        ) {
                            banks.forEach { bank ->
                                DropdownMenuItem(
                                    text = { Text(bank) },
                                    onClick = {
                                        linkedBankName = bank
                                        showBankDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 계좌번호 입력
                    OutlinedTextField(
                        value = linkedAccountNumber,
                        onValueChange = { linkedAccountNumber = it },
                        label = { Text("연동 계좌번호") },
                        placeholder = { Text("예: 123-456-789012") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "해당 계좌로 입금되면 자동으로 저축 내역에 반영됩니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.toLongOrNull() ?: 0L
                    if (name.isNotBlank() && amount > 0) {
                        onConfirm(
                            name,
                            amount,
                            selectedEmoji,
                            autoDepositEnabled,
                            linkedAccountNumber,
                            linkedBankName
                        )
                    }
                },
                enabled = name.isNotBlank() &&
                    amountInput.isNotBlank() &&
                    (amountInput.toLongOrNull() ?: 0L) > 0 &&
                    (!autoDepositEnabled || (linkedAccountNumber.isNotBlank() && linkedBankName.isNotBlank()))
            ) {
                Text("만들기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun ContributeDialog(
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var amountInput by remember { mutableStateOf("") }
    val remaining = goal.targetAmount - goal.currentAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${goal.iconEmoji} ${goal.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("저축 금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "남은 금액: ${String.format("%,d", remaining)}원",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.toLongOrNull() ?: 0L
                    if (amount > 0) {
                        onConfirm(amount)
                    }
                },
                enabled = amountInput.isNotBlank() && (amountInput.toLongOrNull() ?: 0L) > 0
            ) {
                Text("저축하기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
