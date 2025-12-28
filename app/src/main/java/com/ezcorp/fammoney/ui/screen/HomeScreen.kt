package com.ezcorp.fammoney.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.Child
import com.ezcorp.fammoney.data.model.Merchant
import com.ezcorp.fammoney.data.model.SpendingCategory
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.ui.screen.components.AIInsightCard
import com.ezcorp.fammoney.ui.screen.components.AILockedCard
import com.ezcorp.fammoney.ui.screen.components.AITeaserCard
import com.ezcorp.fammoney.ui.screen.components.SpendingPredictionCard
import com.ezcorp.fammoney.ui.theme.ExpenseColor
import com.ezcorp.fammoney.ui.theme.IncomeColor
import com.ezcorp.fammoney.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToBankSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit = {},
    onNavigateToChildIncome: () -> Unit = {},
    onNavigateToCashManagement: () -> Unit = {},
    onNavigateToPendingDuplicates: () -> Unit = {},
    onNavigateToTransactionDetail: (String) -> Unit = {},
    onNavigateToSavingsGoal: () -> Unit = {},
    onNavigateToAICoaching: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showHighAmountDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var isAITeaserExpanded by remember { mutableStateOf(false) }

    // ?ë©´ ?¬ê¸° ê°ì?
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isCompactScreen = screenWidth < 400 // Z?´ë ?í ?í ??ì¢ì? ?ë©´

    LaunchedEffect(uiState.pendingHighAmountTransaction) {
        showHighAmountDialog = uiState.pendingHighAmountTransaction != null
    }

    // AI ê¸°ë¥ ë¡ë
    LaunchedEffect(uiState.totalExpense, uiState.totalIncome) {
        viewModel.loadAllAIFeatures()
    }

    if (showHighAmountDialog && uiState.pendingHighAmountTransaction != null) {
        HighAmountConfirmDialog(
            transaction = uiState.pendingHighAmountTransaction!!,
            onConfirm = {
                viewModel.confirmHighAmountTransaction(uiState.pendingHighAmountTransaction!!.id)
                showHighAmountDialog = false
            },
            onDismiss = {
                viewModel.dismissHighAmountTransaction()
                showHighAmountDialog = false
            }
        )
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            children = uiState.children,
            childIncomeEnabled = uiState.currentGroup?.childIncomeEnabled == true,
            onDismiss = { showAddTransactionDialog = false },
            onConfirm = { type, amount, description, category, merchant, merchantName, memo, linkedChildId, linkedChildName ->
                viewModel.addTransaction(type, amount, description, category, merchant, merchantName, memo, linkedChildId, linkedChildName)
                showAddTransactionDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.currentGroup?.name ?: "팸머니") },
                actions = {
                    // 중복 거래 ?ë¦¼ ë°°ì? (중복 거래ê° ?ì ?ë§ ?ì) - ?? ì²?ë²ì§¸???ì
                if (uiState.pendingDuplicatesCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable(onClick = onNavigateToPendingDuplicates),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "중복 거래",
                                modifier = Modifier.size(24.dp)
                            )
                            // ë°°ì?ë¥?????ë³´ì´ê²??¤ë¥¸ìª??ì ?ì
                Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 6.dp, y = (-6).dp)
                                    .size(18.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.pendingDuplicatesCount > 9) "9+" else "${uiState.pendingDuplicatesCount}",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (isCompactScreen) {
                        // ì¢ì? ?ë©´: 통계확인 설정ë§?ì§ì  ?ì, ?ë¨¸ì§???¤ë²?ë¡??ë©ë´
                IconButton(onClick = onNavigateToStatistics) {
                            Icon(Icons.Default.BarChart, contentDescription = "통계")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                        // ?¤ë²?ë¡??ë©ë´
                Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "더보기")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                if (uiState.cashManagementEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("현금 관리") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToCashManagement()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Payments, contentDescription = null)
                                        }
                                    )
                                }
                                if (uiState.currentGroup?.childIncomeEnabled == true) {
                                    DropdownMenuItem(
                                        text = { Text("용돈 관리") },
                                        onClick = {
                                            showOverflowMenu = false
                                            onNavigateToChildIncome()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.ChildCare, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // ?ì? ?ë©´: ëª¨ë  ?ì´ì½??ì
                if (uiState.cashManagementEnabled) {
                            IconButton(onClick = onNavigateToCashManagement) {
                                Icon(Icons.Default.Payments, contentDescription = "현금 관리")
                            }
                        }
                        if (uiState.currentGroup?.childIncomeEnabled == true) {
                            IconButton(onClick = onNavigateToChildIncome) {
                                Icon(Icons.Default.ChildCare, contentDescription = "용돈 관리")
                            }
                        }
                        IconButton(onClick = onNavigateToStatistics) {
                            Icon(Icons.Default.BarChart, contentDescription = "통계")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTransactionDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "거래 추가")
            }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    MonthSelector(
                        year = uiState.currentYear,
                        month = uiState.currentMonth,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth
                    )
                }

                item {
                    SummaryCard(
                        totalIncome = uiState.totalIncome,
                        totalExpense = uiState.totalExpense,
                        balanceEnabled = uiState.currentGroup?.balanceEnabled ?: false,
                        currentBalance = uiState.currentGroup?.currentBalance ?: 0L
                    )
                }

                // AI ?¸ì¬?´í¸ ?¹ì (ì»¤ë¥??AI ?ì©)
                item {
                    if (uiState.isAIEnabled) {
                        // êµ¬ë?? 인사이트 AI ê¸°ë¥ ?ì
                Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 지출??ì¸¡ ì¹´ë
                SpendingPredictionCard(
                                prediction = uiState.spendingPrediction,
                                currentExpense = uiState.totalExpense
                            )

                            // AI ?¸ì¬?´í¸ ì¹´ë
                AIInsightCard(
                                insights = uiState.aiInsights,
                                isLoading = uiState.isLoadingAI,
                                onSeeMore = onNavigateToAICoaching
                            )
                        }
                    } else {
                        // ë¬´ë£ ?¬ì©?? ?í ?°ì? (?´ë¦­?ë©´ ?¼ì¹¨)
                Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .animateContentSize()
                        ) {
                            AITeaserCard(
                                onToggle = { isAITeaserExpanded = !isAITeaserExpanded },
                                isExpanded = isAITeaserExpanded,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // ?¼ì³ì§??í?????ì¸ 내용 ?ì
                androidx.compose.animation.AnimatedVisibility(
                                visible = isAITeaserExpanded
                            ) {
                                AILockedCard(
                                    onSubscribe = onNavigateToSubscription,
                                    onDismiss = { isAITeaserExpanded = false },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }

                // 목표 저축?ì¹´ë (목표ê° ?ì ?ë§ ?ì)
                if (uiState.savingsGoals.isNotEmpty()) {
                    item {
                        SavingsGoalCard(
                            goals = uiState.savingsGoals,
                            onClick = onNavigateToSavingsGoal
                        )
                    }
                }

                item {
                    UserFilterChips(
                        users = uiState.groupMembers,
                        selectedUserId = uiState.selectedUserFilter ?: "",
                        onUserSelected = viewModel::setUserFilter
                    )
                }

                if (uiState.transactions.isEmpty()) {
                    item {
                        EmptyTransactionsMessage()
                    }
                } else {
                    items(
                        items = uiState.transactions,
                        key = { it.id }
                    ) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onClick = { onNavigateToTransactionDetail(transaction.id) },
                            onDelete = { viewModel.deleteTransaction(transaction.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun MonthSelector(
    year: Int,
    month: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
        }

        Text(
            text = "${year}년 ${month}월",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
        }
    }
}

@Composable
fun SummaryCard(
    totalIncome: Long,
    totalExpense: Long,
    balanceEnabled: Boolean = false,
    currentBalance: Long = 0L
) {
    // ?ë©´ ?¬ê¸°???°ë¼ ?ì¤???¬ê¸° ì¡°ì 
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 400

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isCompactScreen) 12.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "수입",
                    style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${String.format("%,d", totalIncome)}",
                    style = if (isCompactScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = IncomeColor,
                    maxLines = 1
                )
            }

            Divider(
                modifier = Modifier
                    .height(if (isCompactScreen) 40.dp else 50.dp)
                    .width(1.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "지출",
                    style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "-${String.format("%,d", totalExpense)}",
                    style = if (isCompactScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseColor,
                    maxLines = 1
                )
            }

            Divider(
                modifier = Modifier
                    .height(if (isCompactScreen) 40.dp else 50.dp)
                    .width(1.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // 잔고 ê¸°ë¥???ì±?ëë©??ì¬ 잔고 ?ì, ?ëë©?합계 ?ì
                if (balanceEnabled) {
                    Text(
                        text = "잔고",
                        style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format("%,d", currentBalance),
                        style = if (isCompactScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (currentBalance >= 0) IncomeColor else ExpenseColor,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "합계",
                        style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val balance = totalIncome - totalExpense
                    Text(
                        text = "${if (balance >= 0) "+" else ""}${String.format("%,d", balance)}",
                        style = if (isCompactScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) IncomeColor else ExpenseColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SavingsGoalCard(
    goals: List<com.ezcorp.fammoney.data.model.SavingsGoal>,
    onClick: () -> Unit
) {
    val activeGoals = goals.filter { !it.isCompleted }.take(2)
    if (activeGoals.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "목표 저축",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "더보기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            activeGoals.forEach { goal ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = goal.iconEmoji,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.name,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = goal.progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(goal.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (goal != activeGoals.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFilterChips(
    users: List<com.ezcorp.fammoney.data.model.User>,
    selectedUserId: String,
    onUserSelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedUserId == null,
                onClick = { onUserSelected(null) },
                label = { Text("전체") }
            )
        }
        items(users) { user ->
            FilterChip(
                selected = selectedUserId == user.id,
                onClick = { onUserSelected(user.id) },
                label = { Text(user.name) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    onClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ?ë©´ ?¬ê¸°???°ë¼ ?ì´?ì ì¡°ì 
    val configuration = LocalConfiguration.current
    val isCompactScreen = configuration.screenWidthDp < 400

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("삭제 확인") },
            text = { Text("이 거래 내역을 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // ì»´í©?¸í ?ì´ë¸??ì ë¦¬ì¤???ì´
Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteDialog = true }
            )
            .padding(horizontal = if (isCompactScreen) 12.dp else 16.dp, vertical = if (isCompactScreen) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ?¼ìª½: ? ì§/?ê°
        Column(
            modifier = Modifier.width(if (isCompactScreen) 42.dp else 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            transaction.transactionDate?.let { timestamp ->
                val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(
                    text = dateFormat.format(timestamp.toDate()),
                    style = if (isCompactScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = timeFormat.format(timestamp.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isCompactScreen) 8.dp else 12.dp))

        // ê°?´ë°: ?¬ì©ì²?(merchantName ?°ì , ?ì¼ë©?description, ?ì¼ë©?bankName)
        Text(
            text = transaction.merchantName.ifBlank {
                transaction.description.ifBlank { transaction.bankName }
            },
            style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(if (isCompactScreen) 8.dp else 12.dp))

        // ?¤ë¥¸ìª? 금액
        Text(
            text = "${if (transaction.type == TransactionType.INCOME) "+" else "-"}${String.format("%,d", transaction.amount)}",
            style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (transaction.type == TransactionType.INCOME) IncomeColor else ExpenseColor
        )
    }

    // êµ¬ë¶
Divider(
        modifier = Modifier.padding(horizontal = if (isCompactScreen) 12.dp else 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
fun EmptyTransactionsMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "거래 내역이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "알림을 통해 자동으로 기록됩니다\n또는 직접 입력해보세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HighAmountConfirmDialog(
    transaction: Transaction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("고액 거래 확인") },
        text = {
            Column {
                Text(
                    text = "${String.format("%,d", transaction.amount)}?ì´ ê°ì??ì?µë",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "이 거래 내역을 삭제하시겠습니까",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "내용: ${transaction.description}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "확인?? ${transaction.bankName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
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
 * 거래 추가? ë°í??í¸
 * AlertDialog 확인??ModalBottomSheetë¥??¬ì©?ì¬ ?´ë? ? í UI확인??z-index ì¶©ë ë°©ì"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    children: List<Child> = emptyList(),
    childIncomeEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (TransactionType, Long, String, String, String, String, String, String, String) -> Unit
) {
    var transactionType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var selectedMerchant by remember { mutableStateOf("") }
    var selectedMerchantName by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }

    // ?ë? 용돈 ?°ë
    var linkedChildId by remember { mutableStateOf("") }
    var linkedChildName by remember { mutableStateOf("") }

    // ?ì¬ ?ì???ë©´ ?í
    var currentScreen by remember { mutableStateOf(AddTransactionScreen.MAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        when (currentScreen) {
            AddTransactionScreen.MAIN -> {
                // ë©ì¸ 거래 ?ë ¥ ?ë©´
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "거래 추가",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 수입/지출?? í
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = transactionType == TransactionType.INCOME,
                            onClick = { transactionType = TransactionType.INCOME },
                            label = { Text("수입") },
                            leadingIcon = if (transactionType == TransactionType.INCOME) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IncomeColor.copy(alpha = 0.2f),
                                selectedLabelColor = IncomeColor
                            )
                        )
                        FilterChip(
                            selected = transactionType == TransactionType.EXPENSE,
                            onClick = { transactionType = TransactionType.EXPENSE },
                            label = { Text("지출") },
                            leadingIcon = if (transactionType == TransactionType.EXPENSE) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExpenseColor.copy(alpha = 0.2f),
                                selectedLabelColor = ExpenseColor
                            )
                        )
                    }

                    // 금액 ?ë ¥
                OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("금액") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        suffix = { Text("") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 내용 ?ë ¥
                OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("내용") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 카테고리 선택
                OutlinedCard(
                        onClick = { currentScreen = AddTransactionScreen.CATEGORY },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedCategory.isNotBlank()) {
                                // ?ë? 용돈 가이드 목록??ê²½ì° ?¹ë³ ?ì
                if (linkedChildId.isNotEmpty()) {
                                    Text("👶 $linkedChildName 용돈")
                                } else {
                                    val category = SpendingCategory.fromString(selectedCategory)
                                    Text("${category.icon} ${category.displayName}")
                                }
                            } else {
                                Text(
                                    "카테고리 선택",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    // 사용처 선택 (지출ì¼ ?ë§)
                if (transactionType == TransactionType.EXPENSE) {
                        OutlinedCard(
                            onClick = { currentScreen = AddTransactionScreen.MERCHANT },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedMerchantName.isNotBlank()) {
                                    Text(selectedMerchantName)
                                } else {
                                    Text(
                                        "사용처 선택",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }

                    // 메모 ?ë ¥
                OutlinedTextField(
                        value = memo,
                        onValueChange = { memo = it },
                        label = { Text("메모 (선택)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ë²í¼
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
                                if (amount > 0) {
                                    onConfirm(transactionType, amount, description, selectedCategory, selectedMerchant, selectedMerchantName, memo, linkedChildId, linkedChildName)
                                }
                            },
                            enabled = amountText.isNotBlank() && (amountText.toLongOrNull() ?: 0) > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("확인")
                        }
                    }
                }
            }

            AddTransactionScreen.CATEGORY -> {
                // 가이드 목록 ? í ?ë©´
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentScreen = AddTransactionScreen.MAIN }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                        }
                        Text(
                            text = "카테고리 선택",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 450.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ?ë? 용돈 가이드 목록 (ë§??ì ?ì)
                if (childIncomeEnabled && children.isNotEmpty()) {
                            item {
                                ChildAllowanceCategoryGroup(
                                    children = children,
                                    onChildSelected = { childId, childName ->
                                        selectedCategory = "CHILD_$childId"
                                        linkedChildId = childId
                                        linkedChildName = childName
                                        currentScreen = AddTransactionScreen.MAIN
                                    }
                                )
                            }
                        }
                        item { CategoryGroup("?½ï¸??ë¹", SpendingCategory.foodGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("?  ì£¼ê±°", SpendingCategory.housingGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("? êµíµ", SpendingCategory.transportGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("?ï¸??¼í", SpendingCategory.shoppingGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("?¬ ë¬¸í/?¬ê", SpendingCategory.cultureGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("?¥ 일정", SpendingCategory.livingGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("?° ê¸ìµ", SpendingCategory.financeGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("좋아요 êµì¡", SpendingCategory.educationGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("? ê²½ì¡°", SpendingCategory.eventGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("? ê¸°í", SpendingCategory.otherGroup) {
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            AddTransactionScreen.MERCHANT -> {
                // 사용처 선택 ?ë©´
                var searchQuery by remember { mutableStateOf("") }
                val merchants = remember { Merchant.getDefaultMerchants() }
                val filteredMerchants = remember(searchQuery) {
                    if (searchQuery.isBlank()) merchants
                    else merchants.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentScreen = AddTransactionScreen.MAIN }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                        }
                        Text(
                            text = "사용처 선택",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("검색") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredMerchants) { merchant ->
                            ListItem(
                                headlineContent = { Text("${merchant.icon} ${merchant.displayName}") },
                                supportingContent = { Text(merchant.defaultCategory.displayName) },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMerchant = merchant.id
                                        selectedMerchantName = merchant.displayName
                                        if (selectedCategory.isBlank()) {
                                            selectedCategory = merchant.defaultCategory.name
                                        }
                                        currentScreen = AddTransactionScreen.MAIN
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// 거래 추가? ?ë©´ ?í
private enum class AddTransactionScreen {
    MAIN,
    CATEGORY,
    MERCHANT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryGroup(
    title: String,
    categories: List<SpendingCategory>,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                FilterChip(
                    selected = false,
                    onClick = { onSelect(category.name) },
                    label = { Text("${category.icon} ${category.displayName}") }
                )
            }
        }
    }
}

/**
 * ?ë? 용돈 가이드 목록 ê·¸ë£¹
 * ?ë? ëª©ë¡?ì ?ì ?¼ë¡ ?ì±?ë 용돈 가이드 목록
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildAllowanceCategoryGroup(
    children: List<Child>,
    onChildSelected: (String, String) -> Unit
) {
    Column {
        Text(
            text = "👶 자녀 용돈",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(children) { child ->
                FilterChip(
                    selected = false,
                    onClick = { onChildSelected(child.id, child.name) },
                    label = { Text("👶 ${child.name} 용돈") }
                )
            }
        }
    }
}
