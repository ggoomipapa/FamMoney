package com.ezcorp.fammoney.ui.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

enum class OnboardingStep {
    WELCOME,
    NOTIFICATION_PERMISSION,
    BANK_SELECTION,
    COMPLETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var selectedBankIds by remember { mutableStateOf(setOf<String>()) }
    var hasNotificationPermission by remember { mutableStateOf(false) }

    // 알림 권한 상태 확인
    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationPermission = isNotificationListenerEnabled(context)
            delay(1000)
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 진행 표시기
            val progressValue = when (currentStep) {
                OnboardingStep.WELCOME -> 0.25f
                OnboardingStep.NOTIFICATION_PERMISSION -> 0.5f
                OnboardingStep.BANK_SELECTION -> 0.75f
                OnboardingStep.COMPLETE -> 1f
            }
            LinearProgressIndicator(
                progress = progressValue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                },
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep(
                        onNext = { currentStep = OnboardingStep.NOTIFICATION_PERMISSION }
                    )
                    OnboardingStep.NOTIFICATION_PERMISSION -> NotificationPermissionStep(
                        hasPermission = hasNotificationPermission,
                        onRequestPermission = {
                            openNotificationListenerSettings(context)
                        },
                        onNext = { currentStep = OnboardingStep.BANK_SELECTION },
                        onSkip = { currentStep = OnboardingStep.BANK_SELECTION }
                    )
                    OnboardingStep.BANK_SELECTION -> BankSelectionStep(
                        availableBanks = uiState.availableBanks,
                        selectedBankIds = selectedBankIds,
                        onBankToggle = { bankId, isSelected ->
                            selectedBankIds = if (isSelected) {
                                selectedBankIds + bankId
                            } else {
                                selectedBankIds - bankId
                            }
                        },
                        onNext = {
                            viewModel.updateSelectedBanks(selectedBankIds.toList())
                            currentStep = OnboardingStep.COMPLETE
                        },
                        onSkip = { currentStep = OnboardingStep.COMPLETE }
                    )
                    OnboardingStep.COMPLETE -> CompleteStep(
                        onComplete = {
                            viewModel.completeOnboarding()
                            onOnboardingComplete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Savings,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "FamMoney에 오신 것을 환영합니다!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "은행 알림을 자동으로 인식하여\n가계부에 기록해드립니다.\n\n몇 가지 설정만 하면 바로 시작할 수 있어요!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("시작하기")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun NotificationPermissionStep(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = if (hasPermission) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "알림 접근 권한",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (hasPermission) {
                "알림 접근 권한이 설정되었습니다!\n이제 은행 알림을 자동으로 인식할 수 있어요."
            } else {
                "은행 알림을 자동으로 인식하려면\n알림 접근 권한이 필요합니다.\n\n설정에서 FamMoney를 활성화해주세요."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (hasPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "권한 설정 완료",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("알림 접근 설정으로 이동")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (hasPermission) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("다음")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("나중에 하기")
                }
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    enabled = hasPermission
                ) {
                    Text("다음")
                }
            }
        }
    }
}

@Composable
private fun BankSelectionStep(
    availableBanks: List<BankConfig>,
    selectedBankIds: Set<String>,
    onBankToggle: (String, Boolean) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "사용하는 은행 선택",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "선택한 은행의 알림만 자동으로 기록합니다.\n나중에 설정에서 변경할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableBanks) { bank ->
                OnboardingBankItem(
                    bank = bank,
                    isSelected = selectedBankIds.contains(bank.bankId),
                    onToggle = { isSelected ->
                        onBankToggle(bank.bankId, isSelected)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            ) {
                Text("나중에 하기")
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (selectedBankIds.isEmpty()) "건너뛰기" else "다음")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingBankItem(
    bank: BankConfig,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        onClick = { onToggle(!isSelected) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = bank.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CompleteStep(
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "설정 완료!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "이제 은행 알림이 오면\n자동으로 가계부에 기록됩니다.\n\n설정은 언제든지 변경할 수 있어요.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("시작하기")
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.contains(packageName) == true
}

private fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}
