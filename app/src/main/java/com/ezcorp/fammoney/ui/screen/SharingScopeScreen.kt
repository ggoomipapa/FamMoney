package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezcorp.fammoney.data.model.Transaction
import com.ezcorp.fammoney.data.model.TransactionType
import com.ezcorp.fammoney.ui.theme.ExpenseColor
import com.ezcorp.fammoney.ui.theme.IncomeColor
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

/**
 * 공유 범위 설정 화면
 *
 * @param transactions 사용자의 전체 거래 내역
 * @param currentShareFromDate 현재 공유 시작일
 * @param currentHiddenIds 현재 숨겨진 거래 ID 목록
 * @param onConfirm 확인 버튼 클릭 시 콜백 (시작일, 숨길 거래 ID 목록)
 * @param onCancel 취소 버튼 클릭 시 콜백
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScopeScreen(
    transactions: List<Transaction>,
    currentShareFromDate: Timestamp?,
    currentHiddenIds: List<String>,
    onConfirm: (Timestamp?, List<String>) -> Unit,
    onCancel: () -> Unit
) {
    var selectedDate by remember { mutableStateOf(currentShareFromDate) }
    var hiddenTransactionIds by remember { mutableStateOf(currentHiddenIds.toMutableSet()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(SharingSelectionMode.DATE) }

    // 날짜 선택기
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.toDate()?.time ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Timestamp(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("공유 범위 설정") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onConfirm(selectedDate, hiddenTransactionIds.toList())
                        }
                    ) {
                        Text("완료")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 안내 텍스트
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "다른 멤버에게 공유할 거래 범위를 설정하세요.\n선택하지 않은 거래는 본인만 볼 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 선택 모드
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectionMode == SharingSelectionMode.DATE,
                    onClick = { selectionMode = SharingSelectionMode.DATE },
                    label = { Text("날짜 기준") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = selectionMode == SharingSelectionMode.TRANSACTION,
                    onClick = { selectionMode = SharingSelectionMode.TRANSACTION },
                    label = { Text("거래 선택") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectionMode) {
                SharingSelectionMode.DATE -> {
                    DateSelectionContent(
                        selectedDate = selectedDate,
                        onDateClick = { showDatePicker = true },
                        onClearDate = { selectedDate = null }
                    )
                }
                SharingSelectionMode.TRANSACTION -> {
                    TransactionSelectionContent(
                        transactions = transactions,
                        shareFromDate = selectedDate,
                        hiddenIds = hiddenTransactionIds,
                        onToggleHidden = { transactionId ->
                            hiddenTransactionIds = if (hiddenTransactionIds.contains(transactionId)) {
                                hiddenTransactionIds.toMutableSet().apply { remove(transactionId) }
                            } else {
                                hiddenTransactionIds.toMutableSet().apply { add(transactionId) }
                            }
                        }
                    )
                }
            }
        }
    }
}

enum class SharingSelectionMode {
    DATE,
    TRANSACTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionContent(
    selectedDate: Timestamp?,
    onDateClick: () -> Unit,
    onClearDate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "공유 시작일",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "선택한 날짜 이후의 거래만 다른 멤버에게 공유됩니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onDateClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    if (selectedDate != null) {
                        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
                        Text(
                            text = dateFormat.format(selectedDate.toDate()),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = "모든 거래 공유 (제한 없음)",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (selectedDate != null) {
                    IconButton(onClick = onClearDate) {
                        Icon(Icons.Default.Clear, contentDescription = "초기화")
                    }
                } else {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        if (selectedDate != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault())
                    Text(
                        text = "${dateFormat.format(selectedDate.toDate())} 이후 거래만 공유됩니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionSelectionContent(
    transactions: List<Transaction>,
    shareFromDate: Timestamp?,
    hiddenIds: Set<String>,
    onToggleHidden: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 안내
        Text(
            text = "숨기고 싶은 거래를 선택하세요",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "선택한 거래는 다른 멤버에게 보이지 않습니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 숨김 카운트
        if (hiddenIds.isNotEmpty()) {
            AssistChip(
                onClick = {},
                label = { Text("${hiddenIds.size}개 거래 숨김") },
                leadingIcon = {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "거래 내역이 없습니다",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // 날짜 기준으로 이미 필터링된 거래 표시
            val filteredTransactions = if (shareFromDate != null) {
                transactions.filter {
                    it.transactionDate?.let { date ->
                        date >= shareFromDate
                    } ?: false
                }
            } else {
                transactions
            }

            LazyColumn {
                items(filteredTransactions) { transaction ->
                    val isHidden = hiddenIds.contains(transaction.id)
                    SelectableTransactionItem(
                        transaction = transaction,
                        isHidden = isHidden,
                        onToggle = { onToggleHidden(transaction.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun SelectableTransactionItem(
    transaction: Transaction,
    isHidden: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHidden)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isHidden,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (transaction.type == TransactionType.INCOME)
                            IncomeColor.copy(alpha = 0.1f)
                        else
                            ExpenseColor.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (transaction.type == TransactionType.INCOME)
                        Icons.Default.ArrowDownward
                    else
                        Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (transaction.type == TransactionType.INCOME)
                        IncomeColor
                    else
                        ExpenseColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description.ifBlank { transaction.bankName },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                transaction.transactionDate?.let { timestamp ->
                    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                    Text(
                        text = dateFormat.format(timestamp.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${if (transaction.type == TransactionType.INCOME) "+" else "-"}${String.format("%,d", transaction.amount)}원",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == TransactionType.INCOME)
                    IncomeColor
                else
                    ExpenseColor
            )

            if (isHidden) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = "숨김",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 공유 범위 설정 다이얼로그 (간단한 버전)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScopeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Timestamp?) -> Unit
) {
    var selectedDate by remember { mutableStateOf<Timestamp?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Timestamp(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("공유 범위 설정") },
        text = {
            Column {
                Text(
                    text = "다른 멤버에게 공유할 거래 범위를 설정하세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedCard(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            if (selectedDate != null) {
                                val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
                                Text(dateFormat.format(selectedDate!!.toDate()))
                            } else {
                                Text(
                                    "모든 거래 공유",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                if (selectedDate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { selectedDate = null }) {
                        Text("모든 거래 공유로 변경")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDate) }) {
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
