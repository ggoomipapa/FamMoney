package com.ezcorp.fammoney.ui.screen

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.ui.viewmodel.MainViewModel
import com.ezcorp.fammoney.util.effectiveSubscription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val billingState by viewModel.billingRepository.billingState.collectAsState()
    val currentSubscription = uiState.currentGroup.effectiveSubscription()
    val context = LocalContext.current
    val activity = context as? Activity

    var showPurchaseResultDialog by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // 결제 결과 다이얼로그
    showPurchaseResultDialog?.let { (success, message) ->
        AlertDialog(
            onDismissRequest = { showPurchaseResultDialog = null },
            title = { Text(if (success) "결제 완료" else "결제 실패") },
            text = {
                Text(
                    if (success) "구독이 성공적으로 완료되었습니다!"
                    else message ?: "알 수 없는 오류가 발생했습니다"
                )
            },
            confirmButton = {
                Button(onClick = { showPurchaseResultDialog = null }) {
                    Text("확인")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("구독 플랜") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 현재 플랜 표시
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "현재 플랜",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when(currentSubscription) {
                                "connect" -> "패머니 커넥트"
                                "connect_plus" -> "패머니 커넥트+"
                                "forever" -> "패머니 포에버"
                                else -> "무료"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 무료 플랜
            item {
                SubscriptionCard(
                    title = "무료",
                    price = "₩0",
                    period = "",
                    features = listOf(
                        "로컬 가계부 (무제한)",
                        "예산 잔고 관리",
                        "파일 백업/복원",
                        "모든 개인 기능"
                    ),
                    limitations = listOf(
                        "가족 공유 불가",
                        "클라우드 동기화 불가"
                    ),
                    isCurrentPlan = currentSubscription == "free",
                    isRecommended = false,
                    onSelect = {}
                )
            }

            // 커넥트 플랜
            item {
                SubscriptionCard(
                    title = "패머니 커넥트",
                    price = "₩4,900",
                    period = "/월",
                    yearlyPrice = "₩49,000/년(17% 할인)",
                    features = listOf(
                        "무료 기능 전체 포함",
                        "가족 공유 (최대 10명)",
                        "실시간 클라우드 동기화",
                        "멀티 디바이스 지원",
                        "그룹 알림"
                    ),
                    limitations = emptyList(),
                    isCurrentPlan = currentSubscription == "connect",
                    isRecommended = true,
                    isProcessing = isProcessing,
                    onSelectMonthly = {
                        activity?.let { act ->
                            isProcessing = true
                            viewModel.purchaseConnectMonthly(act) { success, message ->
                                isProcessing = false
                                showPurchaseResultDialog = success to message
                            }
                        }
                    },
                    onSelectYearly = {
                        activity?.let { act ->
                            isProcessing = true
                            viewModel.purchaseConnectYearly(act) { success, message ->
                                isProcessing = false
                                showPurchaseResultDialog = success to message
                            }
                        }
                    }
                )
            }

            // 커넥트+ 플랜
            item {
                SubscriptionCard(
                    title = "패머니 커넥트+",
                    price = "₩7,900",
                    period = "/월",
                    yearlyPrice = "₩79,000/년(17% 할인)",
                    features = listOf(
                        "커넥트 기능 전체 포함",
                        "가족 공유 (무제한)",
                        "우선 지원",
                        "신규 기능 우선 체험"
                    ),
                    limitations = emptyList(),
                    isCurrentPlan = currentSubscription == "connect_plus",
                    isRecommended = false,
                    isProcessing = isProcessing,
                    onSelectMonthly = {
                        activity?.let { act ->
                            isProcessing = true
                            viewModel.purchaseConnectPlusMonthly(act) { success, message ->
                                isProcessing = false
                                showPurchaseResultDialog = success to message
                            }
                        }
                    },
                    onSelectYearly = {
                        activity?.let { act ->
                            isProcessing = true
                            viewModel.purchaseConnectPlusYearly(act) { success, message ->
                                isProcessing = false
                                showPurchaseResultDialog = success to message
                            }
                        }
                    }
                )
            }

            // 포에버 플랜 (평생 이용권)
            item {
                SubscriptionCard(
                    title = "패머니 포에버",
                    price = "₩99,000",
                    period = " (1회)",
                    features = listOf(
                        "커넥트 기능 전체 포함",
                        "가족 공유 (최대 10명)",
                        "평생 이용권",
                        "추가 결제 없음"
                    ),
                    limitations = emptyList(),
                    isCurrentPlan = currentSubscription == "forever",
                    isRecommended = false,
                    isLifetime = true,
                    isProcessing = isProcessing,
                    onSelectLifetime = {
                        activity?.let { act ->
                            isProcessing = true
                            viewModel.purchaseForever(act) { success, message ->
                                isProcessing = false
                                showPurchaseResultDialog = success to message
                            }
                        }
                    }
                )
            }

            // 안내 문구
            item {
                Text(
                    text = "• 구독은 가계부별 결제입니다\n• 방장만 구독을 관리할 수 있습니다\n• 구독 취소 시 다음 결제일까지 이용 가능합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SubscriptionCard(
    title: String,
    price: String,
    period: String,
    yearlyPrice: String? = null,
    features: List<String>,
    limitations: List<String>,
    isCurrentPlan: Boolean,
    isRecommended: Boolean,
    isLifetime: Boolean = false,
    isProcessing: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onSelectMonthly: (() -> Unit)? = null,
    onSelectYearly: (() -> Unit)? = null,
    onSelectLifetime: (() -> Unit)? = null
) {
    var showPlanDialog by remember { mutableStateOf(false) }

    // 구독 옵션 선택 다이얼로그 (월간/연간)
    if (showPlanDialog && onSelectMonthly != null && onSelectYearly != null) {
        AlertDialog(
            onDismissRequest = { showPlanDialog = false },
            title = { Text("$title 구독") },
            text = {
                Column {
                    Text("결제 주기를 선택하세요", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                Column {
                    Button(
                        onClick = {
                            showPlanDialog = false
                            onSelectMonthly()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("월간 구독 ($price/월)")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showPlanDialog = false
                            onSelectYearly()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    ) {
                        Text(yearlyPrice ?: "연간 구독")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlanDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = if (isRecommended) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            if (isRecommended) {
                AssistChip(
                    onClick = {},
                    label = { Text("추천") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLifetime) {
                AssistChip(
                    onClick = {},
                    label = { Text("평생 이용권") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.AllInclusive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = period,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (yearlyPrice != null) {
                Text(
                    text = yearlyPrice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            features.forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            limitations.forEach { limitation ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = limitation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isCurrentPlan) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false
                ) {
                    Text("현재 플랜")
                }
            } else if (price != "₩0") {
                Button(
                    onClick = {
                        when {
                            isLifetime && onSelectLifetime != null -> onSelectLifetime()
                            onSelectMonthly != null && onSelectYearly != null -> showPlanDialog = true
                            onSelect != null -> onSelect()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (isLifetime) "평생 이용권 구매" else "구독하기")
                    }
                }
            }
        }
    }
}
