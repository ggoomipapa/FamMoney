package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.SavingsContribution
import com.ezcorp.fammoney.data.model.SavingsGoal
import com.ezcorp.fammoney.data.model.User
import com.ezcorp.fammoney.data.repository.MemberStatistics
import com.ezcorp.fammoney.ui.viewmodel.SavingsGoalDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalDetailScreen(
    goalId: String,
    onNavigateBack: () -> Unit,
    viewModel: SavingsGoalDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddContributionDialog by remember { mutableStateOf(false) }
    var showEditContributionDialog by remember { mutableStateOf<SavingsContribution?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    // 에러 메시지 표시
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    // 기여 추가 다이얼로그
    if (showAddContributionDialog) {
        AddContributionDialog(
            members = uiState.groupMembers,
            onDismiss = { showAddContributionDialog = false },
            onConfirm = { userId, userName, amount ->
                viewModel.addContribution(userId, userName, amount)
                showAddContributionDialog = false
            }
        )
    }

    // 기여 수정 다이얼로그
    showEditContributionDialog?.let { contribution ->
        EditContributionDialog(
            contribution = contribution,
            members = uiState.groupMembers,
            onDismiss = { showEditContributionDialog = null },
            onConfirm = { newUserId, newUserName, newAmount ->
                viewModel.updateContribution(
                    contributionId = contribution.id,
                    newUserId = newUserId,
                    newUserName = newUserName,
                    newAmount = newAmount
                )
                showEditContributionDialog = null
            },
            onDelete = {
                viewModel.deleteContribution(contribution.id)
                showEditContributionDialog = null
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.goal?.name ?: "목표 상세") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddContributionDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "입금 추가")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            uiState.goal?.let { goal ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 목표 요약 카드
                item {
                        GoalSummaryCard(goal = goal)
                    }

                    // 탭 선택
                item {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("회원별 통계") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("입금 내역") }
                            )
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            // 회원별 통계
                if (uiState.memberStatistics.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        icon = Icons.Default.People,
                                        message = "아직 입금 내역이 없습니다"
                                    )
                                }
                            } else {
                                items(uiState.memberStatistics) { stats ->
                                    MemberStatisticsCard(
                                        statistics = stats,
                                        totalTarget = goal.targetAmount,
                                        memberCount = uiState.groupMembers.size
                                    )
                                }

                                // 미입금자 표시
                val contributedUserIds = uiState.memberStatistics.map { it.userId }.toSet()
                                val nonContributors = uiState.groupMembers.filter { it.id !in contributedUserIds }
                                if (nonContributors.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "아직 입금하지 않은 멤버",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(nonContributors) { member ->
                                        NonContributorCard(member = member)
                                    }
                                }
                            }
                        }
                        1 -> {
                            // 입금 내역
                if (uiState.contributions.isEmpty()) {
                                item {
                                    EmptyStateCard(
                                        icon = Icons.Default.Receipt,
                                        message = "입금 내역이 없습니다"
                                    )
                                }
                            } else {
                                items(uiState.contributions) { contribution ->
                                    ContributionHistoryCard(
                                        contribution = contribution,
                                        onClick = { showEditContributionDialog = contribution }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalSummaryCard(goal: SavingsGoal) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = goal.iconEmoji,
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (goal.autoDepositEnabled) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("자동연동") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    if (goal.isCompleted) {
                        Text(
                            text = "목표 달성!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 진행률
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${String.format("%,d", goal.currentAmount)}원",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "/ ${String.format("%,d", goal.targetAmount)}원",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = goal.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(goal.progress * 100).toInt()}% 달성",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "남은 금액: ${String.format("%,d", goal.targetAmount - goal.currentAmount)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MemberStatisticsCard(
    statistics: MemberStatistics,
    totalTarget: Long,
    memberCount: Int
) {
    val expectedAmount = if (memberCount > 0) totalTarget / memberCount else totalTarget
    val progressRatio = if (expectedAmount > 0) {
        (statistics.totalAmount.toFloat() / expectedAmount).coerceAtMost(1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth()
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statistics.userName.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = statistics.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${statistics.contributionCount}회 입금",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${String.format("%,d", statistics.totalAmount)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "목표: ${String.format("%,d", expectedAmount)}원",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progressRatio,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (progressRatio >= 1f) {
                    MaterialTheme.colorScheme.primary
                } else if (progressRatio >= 0.5f) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${(progressRatio * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NonContributorCard(member: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.name.take(1),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "아직 입금하지 않음",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ContributionHistoryCard(
    contribution: SavingsContribution,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // 자동/수동 아이콘
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (contribution.isAutoDetected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (contribution.isAutoDetected) Icons.Default.Sync else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (contribution.isAutoDetected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contribution.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (contribution.needsReview) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "확인 필요",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        if (contribution.isModified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(수정됨)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        Text(
                            text = contribution.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (contribution.isAutoDetected) {
                            Text(
                                text = " - 자동 감지",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 원본 입금자 이름 (자동 감지 시)
                if (contribution.isAutoDetected && contribution.detectedSenderName.isNotBlank()) {
                        Text(
                            text = "감지된 이름: ${contribution.detectedSenderName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Text(
                text = "+${String.format("%,d", contribution.amount)}원",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContributionDialog(
    members: List<User>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long) -> Unit
) {
    var selectedMember by remember { mutableStateOf<User?>(null) }
    var amountInput by remember { mutableStateOf("") }
    var showMemberDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("입금 추가") },
        text = {
            Column {
                // 멤버 선택
                ExposedDropdownMenuBox(
                    expanded = showMemberDropdown,
                    onExpandedChange = { showMemberDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedMember?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("입금자 선택") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMemberDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showMemberDropdown,
                        onDismissRequest = { showMemberDropdown = false }
                    ) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(member.name)
                                        if (member.realName.isNotBlank()) {
                                            Text(
                                                text = member.realName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedMember = member
                                    showMemberDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 금액 입력
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val member = selectedMember ?: return@Button
                    val amount = amountInput.toLongOrNull() ?: return@Button
                    if (amount > 0) {
                        onConfirm(member.id, member.name, amount)
                    }
                },
                enabled = selectedMember != null &&
                    amountInput.isNotBlank() &&
                    (amountInput.toLongOrNull() ?: 0L) > 0
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
private fun EditContributionDialog(
    contribution: SavingsContribution,
    members: List<User>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Long) -> Unit,
    onDelete: () -> Unit
) {
    var selectedMember by remember {
        mutableStateOf(members.find { it.id == contribution.userId })
    }
    var amountInput by remember { mutableStateOf(contribution.amount.toString()) }
    var showMemberDropdown by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("삭제 확인") },
            text = { Text("이 입금 내역을 삭제하시겠습니까?\n목표 금액에서 ${String.format("%,d", contribution.amount)}원이 차감됩니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("입금 내역 수정") },
        text = {
            Column {
                // 원본 정보 표시
                if (contribution.isAutoDetected && contribution.originalNotificationText.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "원본 알림",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = contribution.originalNotificationText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 멤버 선택
                ExposedDropdownMenuBox(
                    expanded = showMemberDropdown,
                    onExpandedChange = { showMemberDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedMember?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("입금자") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMemberDropdown)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showMemberDropdown,
                        onDismissRequest = { showMemberDropdown = false }
                    ) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(member.name)
                                        if (member.realName.isNotBlank()) {
                                            Text(
                                                text = member.realName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedMember = member
                                    showMemberDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 금액 입력
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
                    label = { Text("금액 (원)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 삭제 버튼
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("입금 내역 삭제")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val member = selectedMember ?: return@Button
                    val amount = amountInput.toLongOrNull() ?: return@Button
                    if (amount > 0) {
                        onConfirm(member.id, member.name, amount)
                    }
                },
                enabled = selectedMember != null &&
                    amountInput.isNotBlank() &&
                    (amountInput.toLongOrNull() ?: 0L) > 0
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
