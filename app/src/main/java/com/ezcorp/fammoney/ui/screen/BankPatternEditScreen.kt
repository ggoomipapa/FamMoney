package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.CustomBankPattern
import com.ezcorp.fammoney.ui.viewmodel.BankPatternViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankPatternEditScreen(
    patternId: String,
    onNavigateBack: () -> Unit,
    viewModel: BankPatternViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(patternId) {
        viewModel.loadPattern(patternId)
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveSuccess()
            onNavigateBack()
        }
    }

    val pattern = uiState.editingPattern

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (patternId == "new") "새 패턴 추가" else "패턴 편집")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.savePattern() },
                        enabled = pattern != null
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "저장")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (pattern == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // 기본 정보
                SectionTitle("기본 정보")

                OutlinedTextField(
                    value = pattern.displayName,
                    onValueChange = {
                        viewModel.updateEditingPattern(pattern.copy(displayName = it))
                    },
                    label = { Text("패턴 이름") },
                    placeholder = { Text("예: KB국민카드") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 패키지명
                SectionTitle("알림 패키지명")
                Text(
                    "은행/카드사의 앱 패키지명을 입력하세요. (줄바꿈으로 구분)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pattern.packageNames.joinToString("\n"),
                    onValueChange = { text ->
                        val packages = text.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        viewModel.updateEditingPattern(pattern.copy(packageNames = packages))
                    },
                    label = { Text("패키지명 목록") },
                    placeholder = { Text("com.kbstar.kbbank\ncom.kakao.talk") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 금액 정규식
                SectionTitle("금액 추출 정규식")
                Text(
                    "금액을 추출할 정규식입니다. 캡처 그룹 ()으로 금액 부분을 감싸세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pattern.amountRegex,
                    onValueChange = {
                        viewModel.updateEditingPattern(pattern.copy(amountRegex = it))
                    },
                    label = { Text("금액 정규식") },
                    placeholder = { Text("([0-9,]+)\\s*원") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 수입 키워드
                SectionTitle("수입 키워드")
                Text(
                    "입금 거래를 인식할 키워드입니다. (줄바꿈으로 구분)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pattern.incomeKeywords.joinToString("\n"),
                    onValueChange = { text ->
                        val keywords = text.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        viewModel.updateEditingPattern(pattern.copy(incomeKeywords = keywords))
                    },
                    label = { Text("수입 키워드") },
                    placeholder = { Text("입금\n받으셨\n이체받음") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 지출 키워드
                SectionTitle("지출 키워드")
                Text(
                    "출금/결제 거래를 인식할 키워드입니다. (줄바꿈으로 구분)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pattern.expenseKeywords.joinToString("\n"),
                    onValueChange = { text ->
                        val keywords = text.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        viewModel.updateEditingPattern(pattern.copy(expenseKeywords = keywords))
                    },
                    label = { Text("지출 키워드") },
                    placeholder = { Text("출금\n결제\n승인\n이체") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 사용처 추출 정규식
                SectionTitle("사용처 추출 정규식 (선택)")
                Text(
                    "사용처(가맹점)를 추출할 정규식입니다. 여러 개 입력 가능합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pattern.merchantRegexList.joinToString("\n"),
                    onValueChange = { text ->
                        val regexList = text.split("\n")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        viewModel.updateEditingPattern(pattern.copy(merchantRegexList = regexList))
                    },
                    label = { Text("사용처 정규식 목록") },
                    placeholder = { Text("\\(([가-힣a-zA-Z0-9\\s]+)\\)\\s*승인") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 10
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 패턴 테스트 섹션
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                SectionTitle("패턴 테스트")
                Text(
                    "실제 SMS/알림 내용을 붙여넣어 패턴이 올바르게 작동하는지 테스트하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.testText,
                    onValueChange = { viewModel.updateTestText(it) },
                    label = { Text("테스트할 문자 내용") },
                    placeholder = { Text("[KB국민] 12/25 쿠팡 승인 50,000원 잔액 1,234,567원") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.testPattern() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("테스트 실행")
                }

                // 테스트 결과 표시
                uiState.testResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.success)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (result.success)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (result.success) "테스트 성공" else "테스트 실패",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (result.success)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (result.success) {
                                TestResultRow("금액", "${result.amount?.let { String.format("%,d", it) } ?: "-"}원")
                                TestResultRow("거래 유형", when (result.transactionType) {
                                    "INCOME" -> "수입"
                                    "EXPENSE" -> "지출"
                                    else -> result.transactionType ?: "-"
                                })
                                TestResultRow("사용처", result.merchantName ?: "(추출 안됨)")
                            } else {
                                Text(
                                    result.errorMessage ?: "알 수 없는 오류",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 에러 스낵바
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show snackbar
            viewModel.clearError()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun TestResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
