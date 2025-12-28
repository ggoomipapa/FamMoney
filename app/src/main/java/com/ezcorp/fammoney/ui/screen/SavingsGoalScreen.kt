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

    // ëª©í ì¶ê? ?¤ì´?¼ë¡ê·?
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
            title = { Text("ëª©í ?? ") },
            text = { Text("'${goal.name}' ëª©íë¥??? ?ìê² ìµ?ê¹?\nëª¨ë  ?ì¶?ê¸°ë¡???¨ê» ?? ?©ë") },
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
                    Text("?? ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("ì·¨ì")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ëª©í ?ì¶") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "?¤ë¡ ê°ê¸")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "ëª©í ì¶ê")
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
                        text = "?±ë¡??ëª©íê° ?ìµ?ë¤",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ê°ì¡±ê³¼ ?¨ê» ?ì¶?ëª©íë¥??¸ìë³´ì¸",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ëª©í ë§ë¤ê¸")
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
                // ì§í ì¤ì¸ ëª©í
                val activeGoals = uiState.savingsGoals.filter { !it.isCompleted }
                val completedGoals = uiState.savingsGoals.filter { it.isCompleted }

                if (activeGoals.isNotEmpty()) {
                    item {
                        Text(
                            text = "ì§í ì¤",
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
                            text = "?¬ì± ?ë£",
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
                                    contentDescription = "?ë ?°ë",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "ëª©í: ${String.format("%,d", goal.targetAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (goal.autoDepositEnabled && goal.linkedBankName.isNotBlank()) {
                            Text(
                                text = "${goal.linkedBankName} ?ë ?°ë",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (goal.isCompleted) {
                    AssistChip(
                        onClick = {},
                        label = { Text("?¬ì±!") },
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
                            contentDescription = "?? ",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ì§íë¥?ë°?
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${String.format("%,d", goal.currentAmount)}",
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
                    Text("?ì¶íê¸")
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
    var selectedEmoji by remember { mutableStateOf("?¯") }
    var autoDepositEnabled by remember { mutableStateOf(false) }
    var linkedAccountNumber by remember { mutableStateOf("") }
    var linkedBankName by remember { mutableStateOf("") }
    var showBankDropdown by remember { mutableStateOf(false) }

    val emojis = listOf("?¯", "? ", "?", "?ï¸", "?", "?±", "?»", "?®", "?", "?¼", "?", "?ï¸")
    val banks = listOf(
        "êµ???", "? í?", "?°ë¦¬?", "?ë?", "?í?",
        "ê¸°ì?", "ì¹´ì¹´?¤ë±", "? ì¤ë±í¬", "ì¼?´ë±", "SC?ì¼?",
        "?¨í°?", "?ë§?ê¸ê³", "? í", "?°ì²´êµ", "ê¸°í"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("???ì¶?ëª©í") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ëª©í ?´ë¦") },
                    placeholder = { Text("?? ?ì£¼???¬í") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("ëª©í ê¸ì¡ (") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("?ì´ì½?? í", style = MaterialTheme.typography.bodyMedium)
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

                // ?ë ?ê¸ ê°ì? ?¤ì 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "?ë ?ê¸ ê°ì",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "ê³ì¢ ?ê¸ ???ë?¼ë¡ ë°ì",
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

                    // ???? í
                ExposedDropdownMenuBox(
                        expanded = showBankDropdown,
                        onExpandedChange = { showBankDropdown = it }
                    ) {
                        OutlinedTextField(
                            value = linkedBankName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("???? í") },
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

                    // ê³ì¢ë²í¸ ?ë ¥
                OutlinedTextField(
                        value = linkedAccountNumber,
                        onValueChange = { linkedAccountNumber = it },
                        label = { Text("?°ë ê³ì¢ë²í¸") },
                        placeholder = { Text("?? 123-456-789012") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "??ê³ì¢ë¡??ê¸?ë©´ ?ë?¼ë¡ ?ì¶??´ì­??ë°ì?©ë",
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
                Text("ë§ë¤ê¸")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ì·¨ì")
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
                    label = { Text("?ì¶?ê¸ì¡ (") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "?¨ì? ê¸ì¡: ${String.format("%,d", remaining)}",
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
                Text("?ì¶íê¸")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ì·¨ì")
            }
        }
    )
}
