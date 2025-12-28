package com.ezcorp.fammoney.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.BankConfig
import com.ezcorp.fammoney.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser = uiState.currentUser

    var selectedBankIds by remember(currentUser) {
        mutableStateOf(currentUser?.selectedBankIds?.toSet() ?: emptySet())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("?€???¤ì •") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "?¤ë¡œ ê°€ê¸")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.updateSelectedBanks(selectedBankIds.toList())
                            onNavigateBack()
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "?€")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Text(
                    text = "?Œë¦¼???ë™?¼ë¡œ ?¸ì‹???€?‰ì„ ? íƒ?˜ì„¸??\n? íƒ???€?‰ì˜ ?…ì¶œê¸??Œë¦¼ë§?ê°€ê³„ë???ê¸°ë¡?©ë‹ˆ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(uiState.availableBanks) { bank ->
                BankSettingItem(
                    bank = bank,
                    isSelected = selectedBankIds.contains(bank.bankId),
                    onToggle = { isSelected ->
                        selectedBankIds = if (isSelected) {
                            selectedBankIds + bank.bankId
                        } else {
                            selectedBankIds - bank.bankId
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSettingItem(
    bank: BankConfig,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { onToggle(!isSelected) }
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

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = bank.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "?Œë¦¼ ?ŒìŠ¤: ${bank.packageNames.size}ê°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
