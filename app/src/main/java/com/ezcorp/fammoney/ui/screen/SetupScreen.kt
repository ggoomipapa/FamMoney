package com.ezcorp.fammoney.ui.screen

import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var userName by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var isJoiningGroup by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }

    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleGoogleSignInResult(result.data)
        }
    }

    LaunchedEffect(uiState.setupComplete) {
        if (uiState.setupComplete) {
            onSetupComplete()
        }
    }

    if (showLoginPrompt) {
        LoginRequiredDialog(
            onDismiss = { showLoginPrompt = false },
            onGoogleSignIn = {
                showLoginPrompt = false
                googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("셀렉트머니") },
                actions = {
                    if (uiState.isAnonymous) {
                        TextButton(
                            onClick = {
                                googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
                            }
                        ) {
                            Icon(
                                Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("로그인")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "환영합니다!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "가계부를 시작하기 위해\n정보를 입력해주세요",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!uiState.isAnonymous) {
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(uiState.firebaseUser?.email ?: "로그인됨") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("이름") },
                placeholder = { Text("사용자 이름을 입력하세요") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !isJoiningGroup,
                    onClick = { isJoiningGroup = false },
                    label = { Text("새 가계부") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isJoiningGroup,
                    onClick = {
                        if (uiState.isAnonymous) {
                            showLoginPrompt = true
                        } else {
                            isJoiningGroup = true
                        }
                    },
                    label = { Text("초대 코드로 참여") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isJoiningGroup) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase() },
                    label = { Text("초대 코드") },
                    placeholder = { Text("6자리 코드를 입력하세요") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "그룹 참여는 로그인한 사용자만 가능합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("가계부 이름") },
                    placeholder = { Text("예: 우리집 가계부") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    )
                )

                if (uiState.isAnonymous) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "로그인 없이 시작할 수 있습니다.\n나중에 로그인하면 공유 기능을 사용할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = {
                    if (userName.isBlank()) return@Button

                    if (isJoiningGroup) {
                        if (inviteCode.isBlank()) return@Button
                        viewModel.joinGroupWithCode(userName, inviteCode, deviceId)
                    } else {
                        if (groupName.isBlank()) return@Button
                        viewModel.createUserAndGroup(userName, groupName, deviceId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isLoading && userName.isNotBlank() &&
                        (if (isJoiningGroup) inviteCode.isNotBlank() else groupName.isNotBlank())
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = if (isJoiningGroup) "참여하기" else "시작하기",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun LoginRequiredDialog(
    onDismiss: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("로그인 필요") },
        text = {
            Text("이 기능을 사용하려면 로그인이 필요합니다.\n로그인하면 다른 기기에서도 데이터를 볼 수 있습니다.")
        },
        confirmButton = {
            Button(onClick = onGoogleSignIn) {
                Text("Google로 로그인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
