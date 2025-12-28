package com.ezcorp.fammoney.ui.screen

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.ui.viewmodel.AuthViewModel
import com.ezcorp.fammoney.ui.viewmodel.MainViewModel
import com.ezcorp.fammoney.util.DebugConfig
import com.ezcorp.fammoney.util.subscriptionDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBankSettings: () -> Unit,
    onNavigateToBackup: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onNavigateToAllowance: () -> Unit = {},
    onNavigateToSavingsGoal: () -> Unit = {},
    onNavigateToBankPatterns: () -> Unit = {},
    onNavigateToAICoaching: () -> Unit = {},
    onNavigateToMotivation: () -> Unit = {},
    mainViewModel: MainViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var showCopiedSnackbar by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSharingScopeDialog by remember { mutableStateOf(false) }
    var notifyGroup by remember(uiState.currentUser) {
        mutableStateOf(uiState.currentUser?.notifyGroupOnTransaction ?: true)
    }
    var receiveNotifications by remember(uiState.currentUser) {
        mutableStateOf(uiState.currentUser?.receiveGroupNotifications ?: true)
    }

    var highAmountThreshold by remember(uiState.highAmountThreshold) {
        mutableStateOf(uiState.highAmountThreshold)
    }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var thresholdInput by remember { mutableStateOf("") }
    var cashManagementEnabled by remember(uiState.cashManagementEnabled) {
        mutableStateOf(uiState.cashManagementEnabled)
    }

    // 프로필 다이얼로그 상태
    var showEditGroupNameDialog by remember { mutableStateOf(false) }
    var showEditUserNameDialog by remember { mutableStateOf(false) }
    var showBalanceSettingsDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showGroupSelectorDialog by remember { mutableStateOf(false) }
    var showKickMemberDialog by remember { mutableStateOf<String?>(null) }

    var editNameInput by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var newGroupNameInput by remember { mutableStateOf("") }
    var balanceEnabled by remember(uiState.currentGroup) {
        mutableStateOf(uiState.currentGroup?.balanceEnabled ?: false)
    }
    var initialBalanceInput by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }

    // AI 코칭 상태 (구독자만 사용 가능)
    val isAIAvailable = remember { com.ezcorp.fammoney.util.AIFeatureConfig.hasApiKey() }
    val isPremiumUser = remember { com.ezcorp.fammoney.util.DebugConfig.isDebugBuild } // 실제로는 구독 상태 체크

    // 그룹 이름 수정 다이얼로그
    if (showEditGroupNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditGroupNameDialog = false },
            title = { Text("가계부 이름 변경") },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("새 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            mainViewModel.updateGroupName(editNameInput)
                            showEditGroupNameDialog = false
                        }
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditGroupNameDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 사용자 이름 수정 다이얼로그
    if (showEditUserNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditUserNameDialog = false },
            title = { Text("내 이름 변경") },
            text = {
                OutlinedTextField(
                    value = editNameInput,
                    onValueChange = { editNameInput = it },
                    label = { Text("새 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameInput.isNotBlank()) {
                            mainViewModel.updateUserName(editNameInput)
                            showEditUserNameDialog = false
                        }
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUserNameDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 통장 잔고 설정 다이얼로그
    if (showBalanceSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showBalanceSettingsDialog = false },
            title = { Text("통장 잔고 설정") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("잔고 기능 사용")
                        Switch(
                            checked = balanceEnabled,
                            onCheckedChange = { balanceEnabled = it }
                        )
                    }
                    if (balanceEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = initialBalanceInput,
                            onValueChange = { initialBalanceInput = it.filter { c -> c.isDigit() } },
                            label = { Text("초기 잔고 (원)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val balance = initialBalanceInput.toLongOrNull() ?: 0L
                        mainViewModel.updateBalanceSettings(balanceEnabled, balance)
                        showBalanceSettingsDialog = false
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBalanceSettingsDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 초대 코드로 참여 다이얼로그
    if (showJoinGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinGroupDialog = false
                joinError = null
            },
            title = { Text("초대 코드로 참여") },
            text = {
                Column {
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase() },
                        label = { Text("초대 코드") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (joinError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = joinError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteCodeInput.isNotBlank()) {
                            mainViewModel.joinGroupByInviteCode(inviteCodeInput) { success, error ->
                                if (success) {
                                    showJoinGroupDialog = false
                                    inviteCodeInput = ""
                                    joinError = null
                                } else {
                                    joinError = error
                                }
                            }
                        }
                    }
                ) {
                    Text("참여")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showJoinGroupDialog = false
                    joinError = null
                }) {
                    Text("취소")
                }
            }
        )
    }

    // 새 가계부 생성 다이얼로그
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("새 가계부 만들기") },
            text = {
                OutlinedTextField(
                    value = newGroupNameInput,
                    onValueChange = { newGroupNameInput = it },
                    label = { Text("가계부 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupNameInput.isNotBlank()) {
                            mainViewModel.createNewGroup(newGroupNameInput)
                            showCreateGroupDialog = false
                            newGroupNameInput = ""
                        }
                    }
                ) {
                    Text("만들기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 가계부 선택 다이얼로그
    if (showGroupSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showGroupSelectorDialog = false },
            title = { Text("가계부 선택") },
            text = {
                Column {
                    uiState.userGroups.forEach { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            leadingContent = {
                                RadioButton(
                                    selected = group.id == uiState.currentGroup?.id,
                                    onClick = {
                                        mainViewModel.switchGroup(group.id)
                                        showGroupSelectorDialog = false
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Divider()
                    ListItem(
                        headlineContent = { Text("새 가계부 만들기") },
                        leadingContent = {
                            Icon(Icons.Default.Add, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupSelectorDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    // 멤버 강퇴 확인 다이얼로그
    if (showKickMemberDialog != null) {
        val memberToKick = uiState.groupMembers.find { it.id == showKickMemberDialog }
        AlertDialog(
            onDismissRequest = { showKickMemberDialog = null },
            title = { Text("멤버 강퇴") },
            text = { Text("${memberToKick?.name}님을 가계부에서 내보내시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        showKickMemberDialog?.let { mainViewModel.removeMember(it) }
                        showKickMemberDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("강퇴")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickMemberDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    // 공유 범위 설정 다이얼로그
    if (showSharingScopeDialog) {
        SharingScopeDialog(
            onDismiss = { showSharingScopeDialog = false },
            onConfirm = { shareFromDate ->
                mainViewModel.updateSharingScope(shareFromDate, emptyList())
                showSharingScopeDialog = false
                // 초대 코드 복사
                uiState.currentGroup?.inviteCode?.let { code ->
                    clipboardManager.setText(AnnotatedString(code))
                    showCopiedSnackbar = true
                }
            }
        )
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authViewModel.handleGoogleSignInResult(result.data)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃") },
            text = { Text("로그아웃 하시겠습니까?\n익명 계정은 데이터가 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.signOut()
                        showLogoutDialog = false
                    }
                ) {
                    Text("로그아웃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    if (showThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text("고액 거래 기준 설정") },
            text = {
                Column {
                    Text(
                        text = "설정한 금액 이상의 거래는 확인 요청됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { input ->
                            thresholdInput = input.filter { it.isDigit() }
                        },
                        label = { Text("금액 (원)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newThreshold = thresholdInput.toLongOrNull()
                        if (newThreshold != null && newThreshold > 0) {
                            highAmountThreshold = newThreshold
                            mainViewModel.updateHighAmountThreshold(newThreshold)
                        }
                        showThresholdDialog = false
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        },
        snackbarHost = {
            if (showCopiedSnackbar) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { showCopiedSnackbar = false }) {
                            Text("확인")
                        }
                    }
                ) {
                    Text("초대 코드가 복사되었습니다.")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 계정 섹션
            item {
                Text(
                    text = "계정",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (authState.isAnonymous) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "로그인 상태",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "익명 사용 중",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Button(
                                    onClick = {
                                        googleSignInLauncher.launch(authViewModel.getGoogleSignInIntent())
                                    }
                                ) {
                                    Text("Google 로그인")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "로그인하면 다른 기기에서도 데이터가 유지되고,\n가족과 가계부를 공유할 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "로그인 계정",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = authState.firebaseUser?.email ?: "-",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                TextButton(
                                    onClick = { showLogoutDialog = true }
                                ) {
                                    Text("로그아웃")
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        // 구독 상태
                Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "구독 상태",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.currentGroup?.subscriptionDisplayName ?: "무료",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            TextButton(onClick = onNavigateToSubscription) {
                                Text("업그레이드")
                            }
                        }
                    }
                }
            }

            // 가계부 정보
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "가계부 정보",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        IconButton(onClick = { showGroupSelectorDialog = true }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "가계부 전환")
                        }
                        IconButton(onClick = { showCreateGroupDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "가계부 추가")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 가계부 이름 (편집 가능)
                Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "가계부 이름",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.currentGroup?.name ?: "-",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            IconButton(
                                onClick = {
                                    editNameInput = uiState.currentGroup?.name ?: ""
                                    showEditGroupNameDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "수정",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 초대 코드
                Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "초대 코드",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (authState.isAnonymous) "로그인 필요"
                                           else uiState.currentGroup?.inviteCode ?: "-",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (authState.isAnonymous)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (!authState.isAnonymous) {
                                    IconButton(
                                        onClick = {
                                            // 초대 시 공유 범위 설정 다이얼로그 표시
                showSharingScopeDialog = true
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "복사",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 내 이름 (편집 가능)
                Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "내 이름",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.currentUser?.name ?: "-",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            IconButton(
                                onClick = {
                                    editNameInput = uiState.currentUser?.name ?: ""
                                    showEditUserNameDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "수정",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "참여 멤버",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.groupMembers.size}명",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // 초대 코드로 참여 버튼
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = { showJoinGroupDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.GroupAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "초대 코드로 참여",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // 멤버 목록 (방장에게 강퇴 버튼 표시)
            if (uiState.groupMembers.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "멤버 목록",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            val isCurrentUserOwner = uiState.currentUser?.isOwner == true
                            uiState.groupMembers.forEachIndexed { index, user ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = user.name,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            if (user.role == "child") {
                                                Text(
                                                    text = "자녀",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Row {
                                        if (user.isOwner) {
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("방장") }
                                            )
                                        } else if (isCurrentUserOwner && user.id != uiState.currentUser?.id) {
                                            // 방장만 다른 멤버를 강퇴할 수 있음
                IconButton(
                                                onClick = { showKickMemberDialog = user.id }
                                            ) {
                                                Icon(
                                                    Icons.Default.PersonRemove,
                                                    contentDescription = "강퇴",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                                if (index < uiState.groupMembers.lastIndex) {
                                    Divider(modifier = Modifier.padding(vertical = 12.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 통장 잔고 설정
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "통장 잔고",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = {
                        balanceEnabled = uiState.currentGroup?.balanceEnabled ?: false
                        initialBalanceInput = (uiState.currentGroup?.initialBalance ?: 0L).toString()
                        showBalanceSettingsDialog = true
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "통장 잔고 설정",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (uiState.currentGroup?.balanceEnabled == true)
                                        "현재 잔고: ${String.format("%,d", uiState.currentGroup?.currentBalance ?: 0)}원"
                                    else "사용 안 함",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // 가족 기능
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "가족 기능",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onNavigateToAllowance
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ChildCare,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "용돈 관리",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "자녀 용돈을 설정하고 관리합니다",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onNavigateToSavingsGoal
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Savings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "목표 저축",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "가족 목표를 설정하고 함께 저축합니다",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            // 그룹 알림 설정
            if (uiState.groupMembers.size > 1) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "그룹 알림",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "내 사용내역 알림 보내기",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "내 거래 내역을 그룹 멤버에게 알림",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = notifyGroup,
                                    onCheckedChange = { checked ->
                                        notifyGroup = checked
                                        mainViewModel.updateNotificationSettings(
                                            notifyGroup = checked,
                                            receiveNotifications = receiveNotifications
                                        )
                                    }
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "그룹 알림 받기",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "다른 멤버의 거래 내역 알림 수신",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = receiveNotifications,
                                    onCheckedChange = { checked ->
                                        receiveNotifications = checked
                                        mainViewModel.updateNotificationSettings(
                                            notifyGroup = notifyGroup,
                                            receiveNotifications = checked
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 은행 선택 섹션
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "은행 선택",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onNavigateToBankSettings
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
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "알림 파싱 은행 설정",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${uiState.currentUser?.selectedBankIds?.size ?: 0}개 은행 선택됨",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }
            }

            // SMS 패턴 관리 (Debug 빌드에서만 표시)
            if (DebugConfig.isDebugBuild) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onClick = onNavigateToBankPatterns
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
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "SMS 패턴 관리",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "은행별 SMS 파싱 패턴 설정 및 테스트",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null
                            )
                        }
                    }
                }
            }

            // 거래 설정
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "거래 설정",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 현금 사용 내역 별도 관리
                Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "현금 사용 내역 별도 관리",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (cashManagementEnabled)
                                        "현금 거래를 별도 화면에서 관리합니다"
                                    else
                                        "현금 거래를 메인 화면에 함께 표시합니다",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cashManagementEnabled,
                                onCheckedChange = { checked ->
                                    cashManagementEnabled = checked
                                    mainViewModel.updateCashManagementEnabled(checked)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = {
                        thresholdInput = highAmountThreshold.toString()
                        showThresholdDialog = true
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "고액 거래 확인 기준",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "${String.format("%,d", highAmountThreshold)}원 이상",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "설정 금액 이상의 거래가 감지되면 자동 저장 전에 확인을 요청합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 자녀 수입 설정
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "자녀 수입",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "자녀 수입 기능 사용",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "자녀가 받은 용돈/세뱃돈 등을 기록합니다",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.currentGroup?.childIncomeEnabled ?: false,
                                onCheckedChange = { checked ->
                                    mainViewModel.updateChildIncomeEnabled(checked)
                                }
                            )
                        }
                    }
                }
            }

            // 데이터 관리
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "데이터 관리",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onNavigateToBackup
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
                                Icons.Default.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "백업 및 복원",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "데이터를 백업하거나 복원합니다",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }
            }

            // 동의 확장 & AI 코칭
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "확장 & AI 코칭",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 동의 확장 (배지, 레벨, 랭킹보드)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onNavigateToMotivation
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
                                Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "동기 부여 확장",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "배지, 레벨, 절약자 리더보드 확인하기",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }
            }

            // AI 재정 코칭
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = onNavigateToAICoaching
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
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "AI 재정 코칭",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "Beta",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "AI가 지출 분석 및 절약 조언을 드립니다",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = if (isAIAvailable && isPremiumUser)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI 코칭",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = when {
                                        !isAIAvailable -> "서비스 준비 중"
                                        isPremiumUser -> "사용 가능"
                                        else -> "구독 필요"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        !isAIAvailable -> MaterialTheme.colorScheme.onSurfaceVariant
                                        isPremiumUser -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                        if (!isPremiumUser && isAIAvailable) {
                            TextButton(onClick = onNavigateToSubscription) {
                                Text("구독하기")
                            }
                        }
                    }
                }
            }

            // 권한 설정
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "권한",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    }
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
                                Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "알림 접근 권한",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "알림을 읽으려면 권한이 필요합니다",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
