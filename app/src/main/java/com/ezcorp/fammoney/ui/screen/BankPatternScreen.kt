package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.CustomBankPattern
import com.ezcorp.fammoney.ui.viewmodel.BankPatternViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankPatternScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: BankPatternViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS ?�싱 ?�턴 관�") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "?�로 가�")
                    }
                },
                actions = {
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "초기")
                    }
                    IconButton(onClick = { onNavigateToEdit("new") }) {
                        Icon(Icons.Default.Add, contentDescription = "???�턴")
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "?�턴 관�??�내",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "?�??카드??SMS ?�식??변경되�??�기???�턴???�정?????�습?�다.\n" +
                                "?�턴???�못 ?�정?�면 거래가 ?�식?��? ?�을 ???�으??주의?�세",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // 기본 ?�턴 (?�정 불�", 비활?�화�?가?"
                item {
                    Text(
                        "기본 ?�턴",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = uiState.patterns.filter { !it.isCustom },
                    key = { it.id }
                ) { pattern ->
                    PatternListItem(
                        pattern = pattern,
                        onToggle = { viewModel.togglePatternEnabled(pattern.id) },
                        onEdit = { onNavigateToEdit(pattern.id) },
                        onDelete = null // 기본 패턴은 삭제 불가
                    )
                }

                // ?�용???�의 ?�턴
                val customPatterns = uiState.patterns.filter { it.isCustom }
                if (customPatterns.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "?�용???�의 ?�턴",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(
                        items = customPatterns,
                        key = { it.id }
                    ) { pattern ->
                        PatternListItem(
                            pattern = pattern,
                            onToggle = { viewModel.togglePatternEnabled(pattern.id) },
                            onEdit = { onNavigateToEdit(pattern.id) },
                            onDelete = { showDeleteDialog = pattern.id }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // 초기???�인 ?�이?�로�"
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("?�턴 초기") },
            text = { Text("모든 ?�턴??기본값으�??�돌리시겠습?�까?\n?�용???�의 ?�턴??모두 ??��?�니") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("초기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // ??�� ?�인 ?�이?�로�?
    showDeleteDialog?.let { patternId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("?�턴 ??��") },
            text = { Text("???�턴????��?�시겠습?�까") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePattern(patternId)
                        showDeleteDialog = null
                    }
                ) {
                    Text("??��")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternListItem(
    pattern: CustomBankPattern,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = pattern.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pattern.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pattern.isCustom) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "커스?�",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "?�키지: ${pattern.packageNames.size}�?| " +
                            "?�입 ?�워?? ${pattern.incomeKeywords.size}�?| " +
                            "지�??�워?? ${pattern.expenseKeywords.size}�",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "금액 ?�턴: ${pattern.amountRegex}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "??��",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "?�집",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
