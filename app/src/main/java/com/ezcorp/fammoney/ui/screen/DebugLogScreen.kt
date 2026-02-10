package com.ezcorp.fammoney.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ezcorp.fammoney.util.AppLogger
import kotlinx.coroutines.launch

private const val TAG = "DebugLogScreen"

// 로그 레벨별 색상
private val ColorVerbose = Color.Gray
private val ColorDebug = Color.Gray  // 회색
private val ColorInfo = Color(0xFF4CAF50)  // 초록
private val ColorWarning = Color(0xFFFF9800)  // 주황
private val ColorError = Color(0xFFF44336)  // 빨강

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var logs by remember { mutableStateOf(AppLogger.getLogs()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // 화면 진입 로그
    LaunchedEffect(Unit) {
        AppLogger.screenEnter(TAG, "디버그 로그 뷰어")
    }

    // 로그 변경 리스너 (새 인터페이스 사용)
    DisposableEffect(Unit) {
        val listener = AppLogger.addSimpleListener {
            logs = AppLogger.getLogs()
        }
        onDispose {
            AppLogger.removeListener(listener)
        }
    }

    // 필터링된 로그
    val filteredLogs = remember(logs, searchQuery, selectedLevel) {
        AppLogger.filterLogs(
            levels = selectedLevel?.let { setOf(it) },
            query = searchQuery.ifEmpty { null }
        )
    }

    // 자동 스크롤
    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    // 초기화 확인 다이얼로그
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("로그 초기화") },
            text = { Text("모든 로그를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AppLogger.clear()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "로그가 초기화되었습니다", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("디버그 로그")
                        Text(
                            text = "${filteredLogs.size}/${logs.size} lines",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    // 자동 스크롤 토글
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignCenter,
                            contentDescription = if (autoScroll) "자동 스크롤 끄기" else "자동 스크롤 켜기",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 필터 메뉴
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "필터",
                                tint = if (selectedLevel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("전체") },
                                onClick = {
                                    selectedLevel = null
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (selectedLevel == null) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Verbose", color = ColorVerbose) },
                                onClick = {
                                    selectedLevel = "V"
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (selectedLevel == "V") {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Debug", color = ColorDebug) },
                                onClick = {
                                    selectedLevel = "D"
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (selectedLevel == "D") {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Info", color = ColorInfo) },
                                onClick = {
                                    selectedLevel = "I"
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (selectedLevel == "I") {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Warning", color = ColorWarning) },
                                onClick = {
                                    selectedLevel = "W"
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (selectedLevel == "W") {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Error", color = ColorError) },
                                onClick = {
                                    selectedLevel = "E"
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (selectedLevel == "E") {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }

                    // 초기화 버튼
                    IconButton(onClick = { showClearConfirmDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "로그 초기화")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 복사 버튼
                    OutlinedButton(
                        onClick = {
                            AppLogger.userAction(TAG, "복사 버튼 클릭")
                            val text = filteredLogs.joinToString("\n") { it.format() }
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "로그가 클립보드에 복사되었습니다", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("복사")
                    }

                    // 저장 버튼 (저장 후 초기화)
                    OutlinedButton(
                        onClick = {
                            AppLogger.userAction(TAG, "저장 버튼 클릭")
                            scope.launch {
                                val file = AppLogger.saveToFile(context, clearAfterSave = true)
                                if (file != null) {
                                    Toast.makeText(context, "저장됨: ${file.name}\n로그가 초기화되었습니다", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "저장 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("저장")
                    }

                    // 공유 버튼 (공유 후 초기화)
                    Button(
                        onClick = {
                            AppLogger.userAction(TAG, "공유 버튼 클릭")
                            scope.launch {
                                val file = AppLogger.saveToFile(context, clearAfterSave = false)
                                if (file != null) {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "로그 공유"))
                                        // 공유 후 초기화
                                        AppLogger.clear()
                                    } catch (e: Exception) {
                                        // FileProvider 없으면 텍스트로 공유
                                        val text = filteredLogs.joinToString("\n") { it.formatForFile() }
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "로그 공유"))
                                        AppLogger.clear()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("공유")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 검색 바
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                placeholder = { Text("태그 또는 메시지 검색...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "검색어 지우기")
                        }
                    }
                },
                singleLine = true
            )

            // 로그 목록
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty() && selectedLevel == null)
                                "로그가 없습니다"
                            else
                                "검색 결과가 없습니다",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            LogEntryItem(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: AppLogger.LogEntry) {
    // 레벨별 색상: 에러=빨강, 경고=주황, 정보=초록, 디버그=회색
    val levelColor = when (entry.level) {
        "V" -> ColorVerbose
        "D" -> ColorDebug
        "I" -> ColorInfo
        "W" -> ColorWarning
        "E" -> ColorError
        else -> MaterialTheme.colorScheme.onSurface
    }

    // 에러/경고는 배경색으로 강조
    val backgroundColor = when (entry.level) {
        "E" -> Color(0x1AF44336)  // 연한 빨강 배경
        "W" -> Color(0x1AFF9800)  // 연한 주황 배경
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        // 모노스페이스 폰트, 10sp 크기
        Text(
            text = entry.format(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = levelColor,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 12.sp
        )
    }
}
