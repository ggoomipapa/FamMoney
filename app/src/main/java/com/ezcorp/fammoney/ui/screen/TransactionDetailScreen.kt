package com.ezcorp.fammoney.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.*
import com.ezcorp.fammoney.service.ParsedReceiptItem
import com.ezcorp.fammoney.ui.theme.ExpenseColor
import com.ezcorp.fammoney.ui.theme.IncomeColor
import com.ezcorp.fammoney.ui.viewmodel.TransactionDetailViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onNavigateBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val transaction by viewModel.transaction.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaved by viewModel.isSaved.collectAsState()
    val merchantSuggestions by viewModel.merchantSuggestions.collectAsState()
    val receiptItems by viewModel.receiptItems.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanError by viewModel.scanError.collectAsState()

    var selectedCategory by remember { mutableStateOf("") }
    var selectedMerchantName by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showMerchantSuggestions by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }

    // 카메라 이미지 URI
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // 갤러리에서 이미지 선택
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.scanReceiptFromUri(it, context)
        }
    }

    // 카메라로 촬영
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraImageUri?.let { uri ->
                viewModel.scanReceiptFromUri(uri, context)
            }
        }
    }

    // 카메라 권한 요청
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 임시 파일 생성
            val photoFile = File.createTempFile(
                "receipt_${System.currentTimeMillis()}",
                ".jpg",
                context.cacheDir
            )
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }

    LaunchedEffect(transaction) {
        transaction?.let {
            selectedCategory = it.category
            selectedMerchantName = it.merchantName
            memo = it.memo
        }
    }

    // 자동완성 필터링
    val filteredSuggestions = remember(selectedMerchantName, merchantSuggestions) {
        if (selectedMerchantName.isBlank()) {
            merchantSuggestions.take(10)
        } else {
            merchantSuggestions.filter {
                it.contains(selectedMerchantName, ignoreCase = true)
            }.take(10)
        }
    }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            onNavigateBack()
        }
    }

    // 이미지 소스 선택 다이얼로그
    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("영수증 스캔") },
            text = { Text("영수증 이미지를 어디서 가져올까요") },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    // 카메라 권한 확인 후 실행
                if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val photoFile = File.createTempFile(
                            "receipt_${System.currentTimeMillis()}",
                            ".jpg",
                            context.cacheDir
                        )
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            photoFile
                        )
                        cameraImageUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }) {
                    Text("카메라로 촬영")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    galleryLauncher.launch("image/*")
                }) {
                    Text("갤러리에서 선택")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("거래 상세") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    // 영수증 스캔 버튼
                IconButton(
                        onClick = { showImageSourceDialog = true },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Receipt, contentDescription = "영수증 스캔")
                        }
                    }
                    // 저장 버튼
                TextButton(
                        onClick = {
                            transaction?.let {
                                viewModel.updateTransaction(
                                    it.copy(
                                        category = selectedCategory,
                                        merchantName = selectedMerchantName,
                                        memo = memo
                                    )
                                )
                            }
                        }
                    ) {
                        Text("저장")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            transaction?.let { tx ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 금액 ?�시
                item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (tx.type == TransactionType.INCOME) "수입" else "지출",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${if (tx.type == TransactionType.INCOME) "+" else "-"}${String.format("%,d", tx.amount)}원",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (tx.type == TransactionType.INCOME) IncomeColor else ExpenseColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                tx.transactionDate?.let { timestamp ->
                                    val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault())
                                    Text(
                                        text = dateFormat.format(timestamp.toDate()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 기본 ?�보
                item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (tx.description.isNotBlank()) {
                                    DetailRow(label = "?�용", value = tx.description)
                                }
                                if (tx.bankName.isNotBlank()) {
                                    DetailRow(label = "?�??카드", value = tx.bankName)
                                }
                                DetailRow(label = "?�력방식", value = when(tx.source) {
                                    InputSource.NOTIFICATION -> "?�림 ?�동?�력"
                                    InputSource.MANUAL_TEXT_INPUT -> "?�스???�력"
                                    InputSource.MANUAL_ENTRY -> "직접 ?�력"
                                })
                            }
                        }
                    }

                    // ?�비?�형 ?�택
                item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCategorySheet = true },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "?�비?�형",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (selectedCategory.isNotBlank()) {
                                        val category = SpendingCategory.fromString(selectedCategory)
                                        Text(
                                            text = "${category.icon} ${category.displayName}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else {
                                        Text(
                                            text = "선택하세요",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 사용처 입력 (자동완성)
                item {
                        Column {
                            OutlinedTextField(
                                value = selectedMerchantName,
                                onValueChange = {
                                    selectedMerchantName = it
                                    showMerchantSuggestions = it.isNotBlank() && filteredSuggestions.isNotEmpty()
                                },
                                label = { Text("사용처") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = {
                                    if (selectedMerchantName.isNotBlank()) {
                                        IconButton(onClick = { selectedMerchantName = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "지우기")
                                        }
                                    }
                                }
                            )

                            // 자동완성 제안 목록
                if (showMerchantSuggestions && filteredSuggestions.isNotEmpty()) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column {
                                        filteredSuggestions.forEach { suggestion ->
                                            Text(
                                                text = suggestion,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedMerchantName = suggestion
                                                        showMerchantSuggestions = false
                                                    }
                                                    .padding(12.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 메모 ?�력
                item {
                        OutlinedTextField(
                            value = memo,
                            onValueChange = { memo = it },
                            label = { Text("메모") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    // ?�캔 ?�러 ?�시
                scanError?.let { error ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { viewModel.clearScanError() }) {
                                        Icon(Icons.Default.Close, contentDescription = "?�기")
                                    }
                                }
                            }
                        }
                    }

                    // ?�수�??�목 ?�시
                if (receiptItems.isNotEmpty()) {
                        item {
                            ReceiptItemsSection(
                                items = receiptItems,
                                onClear = { viewModel.clearReceiptItems() }
                            )
                        }
                    }
                }
            }
        }
    }

    // 카테고리 선택 바텀시트
    if (showCategorySheet) {
        CategoryBottomSheet(
            selectedCategory = selectedCategory,
            isIncome = transaction?.type == TransactionType.INCOME,
            onCategorySelected = { category ->
                selectedCategory = category
                showCategorySheet = false
            },
            onDismiss = { showCategorySheet = false }
        )
    }

}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBottomSheet(
    selectedCategory: String,
    isIncome: Boolean,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (isIncome) "?�입 ?�형 ?�택" else "?�비 ?�형 ?�택",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isIncome) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(IncomeCategory.values().toList()) { category ->
                        CategoryChip(
                            icon = category.icon,
                            name = category.displayName,
                            isSelected = selectedCategory == category.name,
                            onClick = { onCategorySelected(category.name) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 그룹별로 ?�시
                item {
                        Text("?���??�비", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.foodGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("?�� 주거", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.housingGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("?�� 교통", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.transportGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("?���??�핑", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.shoppingGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("?�� 문화/?��", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.cultureGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("?�� ?�활", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.livingGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("?�� 금융", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.financeGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("\uD83C\uDF93 교육", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.educationGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("\uD83D\uDC90 경조사", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.eventGroup, selectedCategory, onCategorySelected)
                    }
                    item {
                        Text("\uD83D\uDCDD 기타", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    }
                    item {
                        CategoryFlowRow(SpendingCategory.otherGroup, selectedCategory, onCategorySelected)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFlowRow(
    categories: List<SpendingCategory>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category.name,
                onClick = { onCategorySelected(category.name) },
                label = { Text("${category.icon} ${category.displayName}") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChip(
    icon: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text("$icon $name") },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantBottomSheet(
    selectedMerchant: String,
    onMerchantSelected: (Merchant) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val merchants = remember { Merchant.getDefaultMerchants() }
    val filteredMerchants = remember(searchQuery) {
        if (searchQuery.isBlank()) merchants
        else merchants.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "?�용�??�택",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("검색") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredMerchants) { merchant ->
                    ListItem(
                        headlineContent = { Text("${merchant.icon} ${merchant.displayName}") },
                        supportingContent = { Text(merchant.defaultCategory.displayName) },
                        trailingContent = {
                            if (selectedMerchant == merchant.id) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMerchantSelected(merchant) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 영수증 항목 표시 섹션
 */
@Composable
fun ReceiptItemsSection(
    items: List<ParsedReceiptItem>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "영수증 항목 (${items.size}개)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider()

            Spacer(modifier = Modifier.height(12.dp))

            // 항목 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "항목명",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = "수량",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    text = "금액",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 항목 목록
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(2f),
                        maxLines = 1
                    )
                    Text(
                        text = "${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        text = "${String.format("%,d", item.totalPrice)}원",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider()

            Spacer(modifier = Modifier.height(8.dp))

            // 합계
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "합계",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%,d", items.sumOf { it.totalPrice })}원",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
