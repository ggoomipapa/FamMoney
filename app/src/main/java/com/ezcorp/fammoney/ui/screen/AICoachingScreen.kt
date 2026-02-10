package com.ezcorp.fammoney.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.util.DebugConfig
import com.ezcorp.fammoney.ui.screen.components.EncouragementCard
import com.ezcorp.fammoney.ui.theme.ExpenseColor
import com.ezcorp.fammoney.ui.theme.IncomeColor
import com.ezcorp.fammoney.ui.viewmodel.AICoachingViewModel
import com.ezcorp.fammoney.ui.viewmodel.AICoachingUiState
import com.ezcorp.fammoney.util.AppLogger

private const val TAG = "AICoachingScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AICoachingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToGuide: () -> Unit = {},
    viewModel: AICoachingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 화면 진입 로그
    LaunchedEffect(Unit) {
        AppLogger.i(TAG, "========== AI 코칭 화면 진입 ==========")
    }

    // 0: 재정분석, 1: 재산증식, 2: 목표코칭, 3: 상품검색
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI 코칭")
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Beta",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
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
            // API 키 설정 안내
            if (!uiState.isApiKeySet) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "API 키가 필요합니다",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AI 코칭을 사용하려면 Google Gemini API 키를 설정해주세요.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("설정에서 API 키 입력하기")
                        }
                    }
                }
            }

            // 탭 선택 - 스크롤 가능
            // Debug 빌드: 모든 탭 표시 (0: 재정분석, 1: 재산증식, 2: 목표코칭, 3: 상품검색)
            // Release 빌드: 일부 탭만 표시 (0: 재정분석, 2: 목표코칭)
            val tabs = if (DebugConfig.isDebugBuild) {
                listOf(
                    Triple(0, "재정 분석", Icons.Default.Analytics),
                    Triple(1, "재산 증식", Icons.Default.TrendingUp),
                    Triple(2, "목표 코칭", Icons.Default.Flag),
                    Triple(3, "상품 검색", Icons.Default.Search)
                )
            } else {
                listOf(
                    Triple(0, "재정 분석", Icons.Default.Analytics),
                    Triple(2, "목표 코칭", Icons.Default.Flag)
                )
            }

            ScrollableTabRow(
                selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0),
                modifier = Modifier.padding(horizontal = 8.dp),
                edgePadding = 8.dp
            ) {
                tabs.forEach { (tabIndex, title, icon) ->
                    Tab(
                        selected = selectedTab == tabIndex,
                        onClick = { selectedTab = tabIndex },
                        text = { Text(title) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 현재 재정 요약
            FinancialSummaryCard(
                totalIncome = uiState.totalIncome,
                totalExpense = uiState.totalExpense,
                balance = uiState.balance
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 탭 내용
            when (selectedTab) {
                0 -> FinancialAnalysisTab(
                    uiState = uiState,
                    onAnalyze = { viewModel.analyzeFinances() }
                )
                1 -> {
                    // 재산 증식 탭 - Debug 빌드에서만 접근 가능
                if (DebugConfig.isDebugBuild) {
                        InvestmentTab(
                            uiState = uiState,
                            onAnalyze = { risk, period -> viewModel.analyzeInvestment(risk, period) }
                        )
                    }
                }
                2 -> GoalCoachingTab(
                    uiState = uiState,
                    onAnalyze = { goalName, targetAmount, years ->
                        viewModel.analyzeGoalProgress(goalName, targetAmount, years)
                    }
                )
                3 -> {
                    // 상품 검색 탭 - Debug 빌드에서만 접근 가능
                if (DebugConfig.isDebugBuild) {
                        ProductSearchTab(
                            uiState = uiState,
                            onSearchProduct = { productType -> viewModel.searchFinancialProducts(productType) },
                            onNavigateToGuide = onNavigateToGuide
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FinancialSummaryCard(
    totalIncome: Long,
    totalExpense: Long,
    balance: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "이번 달 재정 현황",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("수입", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "+${String.format("%,d", totalIncome)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IncomeColor
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("지출", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "-${String.format("%,d", totalExpense)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseColor
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("잔액", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "${if (balance >= 0) "+" else ""}${String.format("%,d", balance)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) IncomeColor else ExpenseColor
                    )
                }
            }

            // 현황 요약 with 격려 메시지
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = if (balance >= 0)
                    IncomeColor.copy(alpha = 0.1f)
                else
                    ExpenseColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (balance >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (balance >= 0) IncomeColor else ExpenseColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (balance >= 0)
                                "이번 달 ${String.format("%,d", balance)}원 잔액이에요"
                            else
                                "이번 달 ${String.format("%,d", -balance)}원 적자예요",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (balance >= 0) IncomeColor else ExpenseColor
                        )
                    }

                    // 격려 메시지
                Spacer(modifier = Modifier.height(8.dp))
                    val (encouragementIcon, encouragementText) = getQuickEncouragement(balance, totalIncome)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$encouragementIcon $encouragementText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 빠른 격려 메시지 반환
 */
private fun getQuickEncouragement(balance: Long, totalIncome: Long): Pair<String, String> {
    val savingsRate = if (totalIncome > 0) ((balance.toFloat() / totalIncome) * 100).toInt() else 0

    return when {
        balance < -500000 -> "💪" to "조금만 줄이면 분명히 나아질 거예요!"
        balance < 0 -> "💡" to "지출을 조금만 줄이면 곧 흑자가 될 수 있어요!"
        savingsRate >= 30 -> "🏆" to "훌륭해요! 30% 이상 저축 중이시네요!"
        savingsRate >= 20 -> "🎯" to "잘하고 있어요! 건전한 저축률을 유지하고 있어요!"
        savingsRate >= 10 -> "👏" to "좋아요! 꾸준히 저축하고 계시네요!"
        balance > 300000 -> "📈" to "여유 자금이 생겼네요! 재산 증식을 시작해볼까요"
        balance > 0 -> "⭐" to "잔액이 있다는 건 잘하고 있다는 뜻! 이어가세요!"
        else -> "🌱" to "수입과 지출이 균형을 이루고 있어요!"
    }
}

@Composable
fun FinancialAnalysisTab(
    uiState: AICoachingUiState,
    onAnalyze: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isApiKeySet && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI가 분석 중...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI 재정 분석 시작")
                }
            }
        }

        // 에러 표시
        if (uiState.error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // AI 분석 결과
        if (uiState.financialAnalysis != null) {
            item {
                AIResponseCard(
                    title = "AI 재정 분석 결과",
                    content = uiState.financialAnalysis,
                    icon = Icons.Default.Analytics
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentTab(
    uiState: AICoachingUiState,
    onAnalyze: (String, String) -> Unit
) {
    var selectedRisk by remember { mutableStateOf("중립") }
    var selectedPeriod by remember { mutableStateOf("장기") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 투자 성향 선택
        item {
            Text(
                text = "투자 성향 선택",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("안전" to "🛡️", "중립" to "⚖️", "공격" to "🚀").forEach { (risk, emoji) ->
                    FilterChip(
                        selected = selectedRisk == risk,
                        onClick = { selectedRisk = risk },
                        label = { Text("$emoji $risk") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 투자 기간 선택
        item {
            Text(
                text = "투자 기간",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("단기" to "1년 이내", "장기" to "1년 이상").forEach { (period, desc) ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text("$period ($desc)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onAnalyze(selectedRisk, selectedPeriod) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isApiKeySet && !uiState.isLoading && uiState.balance > 0
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI가 분석 중...")
                } else {
                    Icon(Icons.Default.TrendingUp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("재산 증식 추천 받기")
                }
            }

            if (uiState.balance <= 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "* 잔액이 있을 때만 투자 추천을 받을 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // AI 투자 분석 결과
        if (uiState.investmentAnalysis != null) {
            item {
                AIResponseCard(
                    title = "AI 재산 증식 추천",
                    content = uiState.investmentAnalysis,
                    icon = Icons.Default.TrendingUp
                )
            }
        }

        item {
            // 투자 경고
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "투자는 원금 손실의 위험이 있습니다. AI 추천은 참고용이며, 최종 투자 결정은 본인의 판단과 책임 하에 이루어져야 합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalCoachingTab(
    uiState: AICoachingUiState,
    onAnalyze: (String, Long, Int) -> Unit
) {
    var goalName by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var targetYears by remember { mutableStateOf("3") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = goalName,
                onValueChange = { goalName = it },
                label = { Text("목표 이름") },
                placeholder = { Text("예: 제주도 여행, 자동차 구매") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = targetAmount,
                onValueChange = { targetAmount = it.filter { c -> c.isDigit() } },
                label = { Text("목표 금액") },
                placeholder = { Text("예: 5000000") },
                suffix = { Text("원") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Text(
                text = "목표 기간",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("1", "2", "3", "5", "10").forEach { year ->
                    FilterChip(
                        selected = targetYears == year,
                        onClick = { targetYears = year },
                        label = { Text("${year}년") }
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    val amount = targetAmount.toLongOrNull() ?: 0L
                    if (goalName.isNotBlank() && amount > 0) {
                        onAnalyze(goalName, amount, targetYears.toInt())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isApiKeySet && !uiState.isLoading &&
                        goalName.isNotBlank() && (targetAmount.toLongOrNull() ?: 0L) > 0
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI가 분석 중...")
                } else {
                    Icon(Icons.Default.Flag, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("목표 달성 코칭 받기")
                }
            }
        }

        // AI 목표 코칭 결과
        if (uiState.goalAnalysis != null) {
            item {
                AIResponseCard(
                    title = "AI 목표 달성 코칭",
                    content = uiState.goalAnalysis,
                    icon = Icons.Default.Flag
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun AIResponseCard(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductSearchTab(
    uiState: AICoachingUiState,
    onSearchProduct: (String) -> Unit,
    onNavigateToGuide: () -> Unit
) {
    var selectedProduct by remember { mutableStateOf("예금") }
    val productTypes = listOf(
        "예금" to "🏦",
        "적금" to "💰",
        "CMA" to "📊",
        "ETF" to "📈",
        "연금저축" to "🌱"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 연결된 은행 정보
        if (uiState.connectedBanks.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "연결된 은행: ${uiState.connectedBanks.joinToString(", ")}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // 상품 타입 선택
        item {
            Text(
                text = "상품 타입 선택",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(productTypes) { (type, emoji) ->
                    FilterChip(
                        selected = selectedProduct == type,
                        onClick = { selectedProduct = type },
                        label = { Text("$emoji $type") }
                    )
                }
            }
        }

        // 검색 버튼
        item {
            Button(
                onClick = { onSearchProduct(selectedProduct) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isApiKeySet && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI가 검색 중...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$selectedProduct 상품 추천받기")
                }
            }
        }

        // 금융 가이드 바로가기
        item {
            OutlinedCard(
                onClick = onNavigateToGuide,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📚", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "금융 가이드 보기",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CMA, 적금, ETF 등 기초 지식 배우기",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        // 에러 표시
        if (uiState.error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // 검색 결과
        if (uiState.productSearchResult != null) {
            item {
                AIResponseCard(
                    title = "$selectedProduct 추천 상품",
                    content = uiState.productSearchResult,
                    icon = Icons.Default.Search
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
