package com.ezcorp.fammoney.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.ui.viewmodel.BackupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showBackupGuide by remember { mutableStateOf(false) }
    var showRestoreGuide by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showSafetyGuide by remember { mutableStateOf(false) }

    // 백업 파일 생성용 런처
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.saveBackupToUri(it) }
    }

    // 복원 파일 선택용 런처
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.previewBackupFile(it) }
    }

    // 백업 성공 시 안내
    if (uiState.backupSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.clearBackupSuccess() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("백업 완료") },
            text = {
                Column {
                    Text("백업 파일이 성공적으로 저장되었습니다.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "중요 안내",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "백업 파일을 안전하게 보관하세요.\n" +
                                        "- Google 드라이브에 업로드\n" +
                                        "- PC로 복사\n" +
                                        "- 다른 클라우드 저장소 사용",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearBackupSuccess() }) {
                    Text("확인")
                }
            }
        )
    }

    // 복원 성공 시 안내
    if (uiState.restoreSuccess) {
        val result = uiState.restoreResult
        AlertDialog(
            onDismissRequest = { viewModel.clearRestoreSuccess() },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("복원 완료") },
            text = {
                Column {
                    Text("데이터가 성공적으로 복원되었습니다.")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (result != null) {
                        Text("복원된 데이터:")
                        Text("- 거래 내역: ${result.transactionCount}건")
                        Text("- 자녀 정보: ${result.childCount}명")
                        Text("- 자녀 수입: ${result.childIncomeCount}건")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearRestoreSuccess()
                    onNavigateBack()
                }) {
                    Text("확인")
                }
            }
        )
    }

    // 에러 표시
    if (uiState.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("오류") },
            text = { Text(uiState.error!!) },
            confirmButton = {
                Button(onClick = { viewModel.clearError() }) {
                    Text("확인")
                }
            }
        )
    }

    // 백업 안내 다이얼로그
    if (showBackupGuide) {
        BackupGuideDialog(onDismiss = { showBackupGuide = false })
    }

    // 복원 안내 다이얼로그
    if (showRestoreGuide) {
        RestoreGuideDialog(onDismiss = { showRestoreGuide = false })
    }

    // 앱 초기화 전 안전 보관 안내
    if (showSafetyGuide) {
        SafetyGuideDialog(onDismiss = { showSafetyGuide = false })
    }

    // 백업 미리보기 및 복원 확인
    if (uiState.previewBackupData != null) {
        val backup = uiState.previewBackupData!!
        AlertDialog(
            onDismissRequest = { viewModel.clearPreview() },
            title = { Text("백업 파일 확인") },
            text = {
                Column {
                    Text(
                        text = "이 백업 파일을 복원하시겠습니까",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("백업 정보", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("가계부: ${backup.groupName}", style = MaterialTheme.typography.bodySmall)
                            Text("사용자: ${backup.userName}", style = MaterialTheme.typography.bodySmall)
                            Text("백업 일시: ${backup.getFormattedDate()}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("포함된 데이터", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("거래 내역: ${backup.transactions.size}건", style = MaterialTheme.typography.bodySmall)
                            Text("자녀 정보: ${backup.children.size}명", style = MaterialTheme.typography.bodySmall)
                            Text("자녀 수입: ${backup.childIncomes.size}건", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
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
                                text = "복원 시 현재 가계부에 데이터가 추가됩니다.\n기존 데이터는 유지됩니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.restoreBackup() },
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("복원하기")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearPreview() }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("백업 및 복원") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 상단 안내
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "데이터 보호 안내",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Google 계정으로 로그인하면 데이터가 클라우드에 자동 저장됩니다.\n" +
                                "추가로 백업 파일을 만들어 두면 더 안전하게 데이터를 보호할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // 백업 섹션
            Text(
                text = "백업",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "모든 거래 내역, 자녀 수입 기록, 설정을 백업 파일로 저장합니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showBackupGuide = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("안내 보기")
                        }
                        Button(
                            onClick = {
                                viewModel.createBackup()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.isLoading && uiState.backupData == null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("백업하기")
                            }
                        }
                    }
                }
            }

            // 백업 데이터가 준비되면 저장 버튼 표시
            if (uiState.backupData != null && !uiState.backupSuccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        val backup = uiState.backupData!!
                        Text(
                            text = "백업 준비 완료",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("거래 내역: ${backup.transactions.size}건", style = MaterialTheme.typography.bodySmall)
                        Text("자녀 정보: ${backup.children.size}명", style = MaterialTheme.typography.bodySmall)
                        Text("자녀 수입: ${backup.childIncomes.size}건", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                createDocumentLauncher.launch(viewModel.generateBackupFileName())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("파일로 저장하기")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 복원 섹션
            Text(
                text = "복원",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "이전에 저장한 백업 파일에서 데이터를 복원합니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRestoreGuide = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Help, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("안내 보기")
                        }
                        Button(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isLoading
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("복원하기")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 안전 보관 안내 섹션
            Text(
                text = "폰 초기화 전 안내",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = { showSafetyGuide = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "폰 초기화 전 필독!",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "백업 파일 안전하게 보관하는 방법",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun BackupGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("백업 방법 안내")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                GuideStep(
                    number = 1,
                    title = "백업하기 버튼 클릭",
                    description = "현재 가계부의 모든 데이터를 백업 파일로 준비합니다."
                )
                Spacer(modifier = Modifier.height(12.dp))
                GuideStep(
                    number = 2,
                    title = "파일로 저장하기",
                    description = "백업 준비가 완료되면 '파일로 저장하기' 버튼을 눌러 원하는 위치에 저장합니다."
                )
                Spacer(modifier = Modifier.height(12.dp))
                GuideStep(
                    number = 3,
                    title = "저장 위치 선택",
                    description = "다운로드 폴더, Google 드라이브 등 원하는 위치를 선택합니다."
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Tip: 안전한 백업 보관",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "- Google 드라이브에 저장하면 폰이 바뀌어도 파일에 접근할 수 있습니다\n" +
                                    "- PC로 복사해두면 더 안전합니다\n" +
                                    "- 정기적으로 백업하는 것을 권장합니다",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
fun RestoreGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("복원 방법 안내")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                GuideStep(
                    number = 1,
                    title = "복원하기 버튼 클릭",
                    description = "파일 선택 화면이 열립니다."
                )
                Spacer(modifier = Modifier.height(12.dp))
                GuideStep(
                    number = 2,
                    title = "백업 파일 선택",
                    description = "이전에 저장한 selectmoney_backup_*.json 파일을 선택합니다."
                )
                Spacer(modifier = Modifier.height(12.dp))
                GuideStep(
                    number = 3,
                    title = "내용 확인",
                    description = "백업 파일에 포함된 데이터를 확인합니다."
                )
                Spacer(modifier = Modifier.height(12.dp))
                GuideStep(
                    number = 4,
                    title = "복원 진행",
                    description = "'복원하기' 버튼을 눌러 데이터를 복원합니다."
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "주의사항",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "- 복원 시 데이터가 현재 가계부에 추가됩니다\n" +
                                    "- 기존 데이터는 그대로 유지됩니다\n" +
                                    "- 같은 백업을 여러 번 복원하면 중복 데이터가 생길 수 있습니다",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
fun SafetyGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("폰 초기화 전 체크리스트")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "폰을 초기화하거나 새 폰으로 바꾸기 전에 아래 단계를 꼭 확인하세요!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))

                SafetyCheckItem(
                    icon = Icons.Default.CloudUpload,
                    title = "1. Google 계정 로그인 확인",
                    description = "Google 계정으로 로그인되어 있으면 데이터가 클라우드에 저장되어 있습니다. 새 폰에서 같은 Google 계정으로 로그인하면 자동으로 동기화됩니다."
                )
                Spacer(modifier = Modifier.height(12.dp))

                SafetyCheckItem(
                    icon = Icons.Default.Backup,
                    title = "2. 백업 파일 만들기",
                    description = "만약을 위해 백업 파일을 만들어두세요. 이 화면에서 '백업하기'를 눌러 파일로 저장합니다."
                )
                Spacer(modifier = Modifier.height(12.dp))

                SafetyCheckItem(
                    icon = Icons.Default.CloudDone,
                    title = "3. 백업 파일 클라우드에 업로드",
                    description = "백업 파일을 Google 드라이브, OneDrive, Dropbox 등 클라우드에 업로드하세요. 폰을 초기화하면 폰에 저장된 파일은 삭제됩니다."
                )
                Spacer(modifier = Modifier.height(12.dp))

                SafetyCheckItem(
                    icon = Icons.Default.Computer,
                    title = "4. PC에도 복사 (선택사항)",
                    description = "더 안전하게 보관하려면 PC에도 백업 파일을 복사해두세요. USB 케이블로 폰을 연결하거나 카카오톡 나에게 보내기로 전송할 수 있습니다."
                )
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "새 폰에서 복원하는 방법",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. 셀렉트머니 앱 설치\n" +
                                    "2. Google 계정으로 로그인\n" +
                                    "   → 클라우드 데이터 자동 동기화\n" +
                                    "3. 필요 시 백업 파일로 추가 복원\n" +
                                    "   → 설정 > 백업 및 복원 > 복원하기",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
private fun GuideStep(
    number: Int,
    title: String,
    description: String
) {
    Row {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = number.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SafetyCheckItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
