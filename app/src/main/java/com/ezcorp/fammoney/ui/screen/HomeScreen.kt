package com.ezcorp.fammoney.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.Child
import com.ezcorp.fammoney.data.model.Merchant
import com.ezcorp.fammoney.data.model.SpendingCategory
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionTag
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
)
{
    val uiState by viewModel.uiState.collectAsState()
    var showHighAmountDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var isAITeaserExpanded by remember { mutableStateOf(false) }

    // 선택 모드 상태
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTransactionIds by remember { mutableStateOf(setOf<String>()) }
    var showTagPickerDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 드래그 선택 상태
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var isDragThresholdMet by remember { mutableStateOf(false) }
    val dragThreshold = 50f // 실제 드래그 의도가 있을 때만 반응하기 위한 threshold (픽셀)

    val listState = rememberLazyListState()

    // 태그 로드
    LaunchedEffect(Unit) {
        viewModel.loadTags()
    }

    // 화면 크기 감지
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isCompactScreen = screenWidth < 400 // 접히는 화면 또는 작은 화면

    LaunchedEffect(uiState.pendingHighAmountTransaction) {
        showHighAmountDialog = uiState.pendingHighAmountTransaction != null
    }

    // AI 기능 로드
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

    // 태그 선택 다이얼로그
    if (showTagPickerDialog && selectedTransactionIds.isNotEmpty()) {
        TagPickerDialog(
            tags = uiState.tags,
            onDismiss = { showTagPickerDialog = false },
            onTagSelected = { tag ->
                viewModel.applyTagToTransactions(
                    selectedTransactionIds.toList(),
                    tag.id,
                    tag.name
                )
                showTagPickerDialog = false
                isSelectionMode = false
                selectedTransactionIds = emptySet()
            },
            onCreateNewTag = { name, color ->
                viewModel.createTagAndApply(
                    tagName = name,
                    tagColor = color,
                    transactionIds = selectedTransactionIds.toList(),
                    onComplete = {
                        showTagPickerDialog = false
                        isSelectionMode = false
                        selectedTransactionIds = emptySet()
                    }
                )
            }
        )
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirmDialog && selectedTransactionIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("삭제 확인") },
            text = {
                Column {
                    Text("${selectedTransactionIds.size}개의 거래 내역을 삭제하시겠습니까?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "삭제된 거래는 30일간 휴지통에 보관됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedTransactions(selectedTransactionIds) { count ->
                            // 삭제 완료
                        }
                        showDeleteConfirmDialog = false
                        isSelectionMode = false
                        selectedTransactionIds = emptySet()
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // 선택 모드 TopAppBar
                TopAppBar(
                    title = { Text("${selectedTransactionIds.size}개 선택됨") },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedTransactionIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "선택 취소")
                        }
                    },
                    actions = {
                        // 전체 선택
                        IconButton(onClick = {
                            selectedTransactionIds = if (selectedTransactionIds.size == uiState.transactions.size) {
                                emptySet()
                            } else {
                                uiState.transactions.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                if (selectedTransactionIds.size == uiState.transactions.size)
                                    Icons.Default.CheckBox
                                else
                                    Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = "전체 선택"
                            )
                        }
                        // 태그 적용 버튼
                        IconButton(
                            onClick = { showTagPickerDialog = true },
                            enabled = selectedTransactionIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.LocalOffer, contentDescription = "태그 적용")
                        }
                        // 삭제 버튼
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = selectedTransactionIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "삭제",
                                tint = if (selectedTransactionIds.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                // 일반 TopAppBar (선택 모드와 동일한 레이아웃 유지를 위해 navigationIcon 공간 확보)
                TopAppBar(
                    title = { Text(uiState.currentGroup?.name ?: "팸머니") },
                    navigationIcon = {
                        // 선택 모드 TopAppBar의 IconButton과 동일한 크기 유지
                        Box(modifier = Modifier.size(48.dp))
                    },
                    actions = {
                    // 중복 거래 알림 뱃지 (중복 거래가 있을 때만 표시) - FAB 위 첫 번째 위치
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
                            // 뱃지 숫자 다른 아이콘 위에 표시
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
                        // 콤팩트 화면: 통계/설정만 직접 표시, 나머지는 오버플로우 메뉴
                        IconButton(onClick = onNavigateToStatistics) {
                            Icon(Icons.Default.BarChart, contentDescription = "통계")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                        // 오버플로우 메뉴
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
                        // 넓은 화면: 모든 아이콘 표시
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
            } // end of else (normal TopAppBar)
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
            // 트랜잭션 아이템 이전의 고정 아이템 수 계산
            // MonthSelector(1) + SummaryCard(1) + AI섹션(1) + UserFilterChips(1) + 저축목표(조건부)
            val headerItemCount = 4 + (if (uiState.savingsGoals.isNotEmpty()) 1 else 0)

            // 드래그 중 인덱스 변경 시 선택 업데이트 (threshold를 넘었을 때만)
            LaunchedEffect(isDragging, dragStartIndex, dragCurrentIndex, isDragThresholdMet) {
                if (isDragging && isDragThresholdMet && dragStartIndex >= 0 && dragCurrentIndex >= 0 && uiState.transactions.isNotEmpty()) {
                    val startIdx = minOf(dragStartIndex, dragCurrentIndex).coerceIn(0, uiState.transactions.lastIndex)
                    val endIdx = maxOf(dragStartIndex, dragCurrentIndex).coerceIn(0, uiState.transactions.lastIndex)
                    val rangeIds = uiState.transactions
                        .subList(startIdx, endIdx + 1)
                        .map { it.id }
                        .toSet()
                    selectedTransactionIds = rangeIds
                }
            }

            LazyColumn(
                state = listState,
                userScrollEnabled = !isDragging,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .pointerInput(uiState.transactions, headerItemCount) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                // 드래그 시작 전 상태 초기화
                                dragStartIndex = -1
                                dragCurrentIndex = -1
                                dragStartY = offset.y
                                isDragThresholdMet = false

                                // 드래그 시작점에서 어떤 아이템인지 찾기
                                val visibleItems = listState.layoutInfo.visibleItemsInfo
                                val draggedItem = visibleItems.find { itemInfo ->
                                    offset.y >= itemInfo.offset && offset.y < itemInfo.offset + itemInfo.size
                                }

                                if (draggedItem != null) {
                                    val transactionIndex = draggedItem.index - headerItemCount
                                    if (transactionIndex >= 0 && transactionIndex < uiState.transactions.size) {
                                        val selectedId = uiState.transactions[transactionIndex].id
                                        dragStartIndex = transactionIndex
                                        dragCurrentIndex = transactionIndex
                                        selectedTransactionIds = setOf(selectedId)
                                        isSelectionMode = true
                                        isDragging = true
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                if (isDragging) {
                                    change.consume()

                                    // threshold 확인: 시작 위치에서 충분히 움직였는지
                                    val distanceY = abs(change.position.y - dragStartY)
                                    if (!isDragThresholdMet && distanceY > dragThreshold) {
                                        isDragThresholdMet = true
                                    }

                                    // threshold를 넘었을 때만 드래그 인덱스 업데이트
                                    if (isDragThresholdMet) {
                                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                                        val currentItem = visibleItems.find { itemInfo ->
                                            change.position.y >= itemInfo.offset &&
                                                    change.position.y < itemInfo.offset + itemInfo.size
                                        }

                                        if (currentItem != null) {
                                            val transactionIndex = currentItem.index - headerItemCount
                                            if (transactionIndex >= 0 && transactionIndex < uiState.transactions.size) {
                                                dragCurrentIndex = transactionIndex
                                            }
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                isDragThresholdMet = false
                            },
                            onDragCancel = {
                                isDragging = false
                                isDragThresholdMet = false
                                dragStartIndex = -1
                                dragCurrentIndex = -1
                            }
                        )
                    }
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

                // AI 인사이트 섹션 (컬러링/AI 활용)
                item {
                    if (uiState.isAIEnabled) {
                        // 구독형 인사이트 AI 기능 표시
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 지출 예측 카드
                            SpendingPredictionCard(
                                prediction = uiState.spendingPrediction,
                                currentExpense = uiState.totalExpense
                            )

                            // AI 인사이트 카드
                            AIInsightCard(
                                insights = uiState.aiInsights,
                                isLoading = uiState.isLoadingAI,
                                onSeeMore = onNavigateToAICoaching
                            )
                        }
                    } else {
                        // 무료 사용자는 숨겨진 AI 티저 (클릭하면 펼침)
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

                            // 펼쳐진 상태일 때만 상세 내용 표시
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

                // 목표 저축 카드 (목표가 있을 때만 표시)
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
                        selectedUserId = uiState.selectedUserFilter,
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
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedTransactionIds.contains(transaction.id),
                            onClick = {
                                if (isSelectionMode) {
                                    // 선택 모드: 선택/해제
                                    selectedTransactionIds = if (selectedTransactionIds.contains(transaction.id)) {
                                        selectedTransactionIds - transaction.id
                                    } else {
                                        selectedTransactionIds + transaction.id
                                    }
                                } else {
                                    onNavigateToTransactionDetail(transaction.id)
                                }
                            },
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
    // 화면 크기에 따라 텍스트 크기 조절
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
                // 잔고 기능이 활성화되면 현재 잔고 표시, 아니면 합계 표시
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
    selectedUserId: String?,
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

@Composable
fun TransactionItem(
    transaction: Transaction,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 화면 크기에 따라 아이템 너비 조절
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

    // 콤팩트한 아이템 형식 리스트 레이아웃
    // 롱클릭은 LazyColumn의 detectDragGesturesAfterLongPress에서 처리
    // 최소 높이를 고정하여 체크박스 유무에 관계없이 동일한 높이 유지
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (isCompactScreen) 12.dp else 16.dp, vertical = if (isCompactScreen) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 체크박스 영역 (선택 모드가 아니어도 공간 유지하여 레이아웃 시프트 방지)
        Box(
            modifier = Modifier.width(if (isSelectionMode) 48.dp else 0.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }
                )
            }
        }
        // 왼쪽: 날짜/시간
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
                    style = if (isCompactScreen) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isCompactScreen) 8.dp else 12.dp))

        // 가운데: 사용처(merchantName 우선, 없으면 description, 없으면 bankName)
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

        // 오른쪽: 금액
        Text(
            text = "${if (transaction.type == TransactionType.INCOME) "+" else "-"}${String.format("%,d", transaction.amount)}",
            style = if (isCompactScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (transaction.type == TransactionType.INCOME) IncomeColor else ExpenseColor
        )
    }

    // 구분선
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
                    text = "${String.format("%,d", transaction.amount)}원이 감지되었습니다",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "이 거래 내역을 확정하시겠습니까?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "내용: ${transaction.description}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "은행: ${transaction.bankName}",
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
 * 거래 추가 바텀시트
 * AlertDialog는 ModalBottomSheet를 사용해야 z-index 충돌 방지
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

    // 자녀 용돈 연동
    var linkedChildId by remember { mutableStateOf("") }
    var linkedChildName by remember { mutableStateOf("") }

    // 현재 표시 화면 상태
    var currentScreen by remember { mutableStateOf(AddTransactionScreen.MAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        when (currentScreen) {
            AddTransactionScreen.MAIN -> {
                // 메인 거래 입력 화면
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

                    // 수입/지출 선택
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

                    // 금액 입력
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("금액") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        suffix = { Text("원") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 내용 입력
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
                                // 자녀 용돈 카테고리일 경우 특별 표시
                                if (linkedChildId.isNotEmpty()) {
                                    Text("👶 $linkedChildName 용돈")
                                }
                                 else {
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

                    // 사용처 선택 (지출일 때만)
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
                                }
                                 else {
                                    Text(
                                        "사용처 선택",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }

                    // 메모 입력
                    OutlinedTextField(
                        value = memo,
                        onValueChange = { memo = it },
                        label = { Text("메모 (선택)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 버튼
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
                // 카테고리 목록 선택 화면
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
                        // 자녀 용돈 카테고리 (해당하는 경우만 표시)
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
                        item { CategoryGroup("🍚 식비", SpendingCategory.foodGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("🏡 주거", SpendingCategory.housingGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("🚌 교통", SpendingCategory.transportGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("🛍️ 쇼핑", SpendingCategory.shoppingGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("🎨 문화/여가", SpendingCategory.cultureGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("🛒 생활", SpendingCategory.livingGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("💰 금융", SpendingCategory.financeGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("🎓 교육", SpendingCategory.educationGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("💐 경조사", SpendingCategory.eventGroup) { 
                            selectedCategory = it
                            linkedChildId = ""
                            linkedChildName = ""
                            currentScreen = AddTransactionScreen.MAIN
                        } }
                        item { CategoryGroup("📝 기타", SpendingCategory.otherGroup) { 
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
                // 사용처 선택 화면
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

// 거래 추가 화면 상태
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
 * 자녀 용돈 카테고리 그룹
 * 자녀 목록에서 동적으로 생성되는 용돈 카테고리 목록
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

/**
 * 태그 선택 다이얼로그
 * 선택한 거래에 적용할 태그를 선택하거나 새로 생성
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerDialog(
    tags: List<TransactionTag>,
    onDismiss: () -> Unit,
    onTagSelected: (TransactionTag) -> Unit,
    onCreateNewTag: (name: String, color: String) -> Unit
) {
    var showCreateMode by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }

    val tagColors = listOf(
        "#4CAF50", // 초록
        "#2196F3", // 파랑
        "#FF9800", // 주황
        "#E91E63", // 분홍
        "#9C27B0", // 보라
        "#00BCD4", // 청록
        "#FF5722", // 주황빨강
        "#795548"  // 갈색
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (showCreateMode) "새 태그 만들기" else "태그 선택")
        },
        text = {
            if (showCreateMode) {
                // 새 태그 생성 모드
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text("태그 이름") },
                        placeholder = { Text("예: 강릉 여행") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "색상 선택",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tagColors.size) { index ->
                            val color = tagColors[index]
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        androidx.compose.ui.graphics.Color(
                                            android.graphics.Color.parseColor(color)
                                        )
                                    )
                                    .clickable { selectedColorIndex = index },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColorIndex == index) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "선택됨",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 태그 선택 모드
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 새 태그 만들기 버튼
                    OutlinedCard(
                        onClick = { showCreateMode = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "새 태그 만들기",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "기존 태그",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tags) { tag ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTagSelected(tag) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                androidx.compose.ui.graphics.Color(
                                                    android.graphics.Color.parseColor(tag.color)
                                                )
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateMode) {
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            onCreateNewTag(newTagName, tagColors[selectedColorIndex])
                        }
                    },
                    enabled = newTagName.isNotBlank()
                ) {
                    Text("만들기")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showCreateMode) {
                    showCreateMode = false
                    newTagName = ""
                } else {
                    onDismiss()
                }
            }) {
                Text(if (showCreateMode) "뒤로" else "취소")
            }
        }
    )
}