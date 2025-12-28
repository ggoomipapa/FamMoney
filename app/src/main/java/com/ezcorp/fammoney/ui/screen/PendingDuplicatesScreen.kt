package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.DuplicateResolution
import com.ezcorp.fammoney.data.model.PendingDuplicate
import com.ezcorp.fammoney.ui.theme.ExpenseColor
import com.ezcorp.fammoney.ui.theme.IncomeColor
import com.ezcorp.fammoney.ui.viewmodel.PendingDuplicatesViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingDuplicatesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PendingDuplicatesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showInfoDialog by remember { mutableStateOf(false) }

    // ì²ì ì§ì???ë´ ?¤ì´?¼ë¡ê·??ì
    LaunchedEffect(Unit) {
        if (uiState.duplicates.isNotEmpty()) {
            showInfoDialog = true
        }
    }

    if (showInfoDialog) {
        DuplicateInfoDialog(
            onDismiss = { showInfoDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("중복 거래 주기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "정보")
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
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.duplicates.isEmpty()) {
                // ì²ë¦¬??중복 거래ê° ?ì
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "처리할 중복 거래가 없습니다",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.resolvedCount > 0) {
                            Text(
                                text = "${uiState.resolvedCount}건의 중복 거래를 처리했습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            } else {
                // 중복 거래 ëª©ë¡
                Text(
                    text = "${uiState.duplicates.size}건의 중복 거래가 감지되었습니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn {
                    items(uiState.duplicates) { duplicate ->
                        DuplicateCard(
                            duplicate = duplicate,
                            onResolve = { resolution, applyToFuture ->
                                viewModel.resolveDuplicate(duplicate, resolution, applyToFuture)
                            }
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

@Composable
private fun DuplicateInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Info, contentDescription = null)
        },
        title = {
            Text("중복 거래확인")
        },
        text = {
            Column {
                Text(
                    text = "체크카드를 사용하면 카드사와 은행에서 확인되므로 알림이 올 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "같은 금액과 거래일 기준으로 감지되면 중복으로 확인 요청합니다",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "\"앞으로도 같이 적용\"??? í?ë©´ ê°ì? ì¹´ë+확인??ì¡°í©?ì???ë?¼ë¡ ì²ë¦¬?©ë",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("주기")
            }
        }
    )
}

@Composable
private fun DuplicateCard(
    duplicate: PendingDuplicate,
    onResolve: (DuplicateResolution, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var applyToFuture by remember { mutableStateOf(false) }
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.KOREA)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 금액 ?ì
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "중복 감지",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = "${numberFormat.format(duplicate.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            // 거래 1
            TransactionInfoRow(
                label = "1",
                bankName = duplicate.transaction1.bankName,
                description = duplicate.transaction1.description,
                time = dateFormat.format(duplicate.transaction1.notificationTime.toDate())
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 거래 2
            TransactionInfoRow(
                label = "2",
                bankName = duplicate.transaction2.bankName,
                description = duplicate.transaction2.description,
                time = dateFormat.format(duplicate.transaction2.notificationTime.toDate())
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 앞으로도 같이 적용 ì²´í¬ë°ì¤
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = applyToFuture,
                    onCheckedChange = { applyToFuture = it }
                )
                Text(
                    text = "앞으로도 같이 적용",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ? í ë²í¼
Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onResolve(DuplicateResolution.KEEP_BOTH, applyToFuture) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("둘 다 유지", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { onResolve(DuplicateResolution.KEEP_FIRST, applyToFuture) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("1만 유지", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { onResolve(DuplicateResolution.KEEP_SECOND, applyToFuture) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("2만 유지", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TransactionInfoRow(
    label: String,
    bankName: String,
    description: String,
    time: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ë²í¸ ?ì
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bankName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
