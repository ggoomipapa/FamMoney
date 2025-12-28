package com.ezcorp.fammoney.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ezcorp.fammoney.data.model.*
import com.ezcorp.fammoney.ui.screen.components.EncouragementCard
import com.ezcorp.fammoney.ui.screen.components.ProgressCard
import com.ezcorp.fammoney.ui.viewmodel.MotivationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotivationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAICoaching: () -> Unit,
    viewModel: MotivationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("?ì ?±ì¥") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "?¤ë¡")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ?ë²¨ ì¹´ë
            item {
                LevelCard(
                    currentLevel = uiState.currentLevel,
                    nextLevel = uiState.nextLevel,
                    progressToNextLevel = uiState.progressToNextLevel,
                    consecutiveSurplusMonths = uiState.consecutiveSurplusMonths,
                    savingsRate = uiState.savingsRate
                )
            }

            // ?´ë² ???ì½
            item {
                MonthSummaryCard(
                    balance = uiState.currentMonthBalance,
                    isSurplus = uiState.currentMonthBalance >= 0,
                    consecutiveMonths = uiState.consecutiveSurplusMonths,
                    improvementFromLastMonth = uiState.improvementFromLastMonth
                )
            }

            // ê²©ë ¤ ë©ìì§
            item {
                val (title, message, icon) = getEncouragementMessage(
                    balance = uiState.currentMonthBalance,
                    consecutiveMonths = uiState.consecutiveSurplusMonths
                )
                EncouragementCard(
                    title = title,
                    message = message,
                    icon = icon
                )
            }

            // ?¬ì ì¶ì² ?¹ì
            item {
                InvestmentRecommendationCard(
                    recommendation = uiState.investmentRecommendation,
                    onLearnMore = onNavigateToAICoaching
                )
            }

            // ?ì  ?¹ì
            item {
                Text(
                    text = "? ?ë??ë°°ì",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                if (uiState.unlockedAchievements.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("?¯", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "?ì§ ?ë??ë°°ì?ê° ?ì´",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "?ìë¥??¬ì±?ë©´ ì²?ë°°ì?ë¥?ë°ì ???ì´",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.unlockedAchievements) { achievement ->
                            AchievementBadge(achievement = achievement)
                        }
                    }
                }
            }

            // ì§í ì¤ì¸ ?ì 
            item {
                Text(
                    text = "? ì§í ì¤ì¸ ?ì ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.inProgressAchievements) { achievement ->
                AchievementProgressCard(achievement = achievement)
            }

            // ?¤ì ?¨ê³ ?ë´
            item {
                NextStepCard(
                    currentBalance = uiState.currentMonthBalance,
                    savingsRate = uiState.savingsRate,
                    consecutiveMonths = uiState.consecutiveSurplusMonths
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun LevelCard(
    currentLevel: UserLevel,
    nextLevel: UserLevel?,
    progressToNextLevel: Float,
    consecutiveSurplusMonths: Int,
    savingsRate: Int
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressToNextLevel,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label = "levelProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Lv.${currentLevel.level}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentLevel.icon,
                                fontSize = 32.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentLevel.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // ?ë²¨ ë±ì"
                Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentLevel.icon,
                            fontSize = 36.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ?ì¬ ?í
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = "?°ì ?ì",
                        value = "${consecutiveSurplusMonths}ê°ì",
                        icon = "?¥"
                    )
                    StatItem(
                        label = "?ì¶ë¥ ",
                        value = "${savingsRate}%",
                        icon = "?°"
                    )
                }

                if (nextLevel != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // ?¤ì ?ë²¨ ì§í
Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "?¤ì ?ë²¨ê¹ì",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "${nextLevel.icon} ${nextLevel.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = animatedProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 20.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun MonthSummaryCard(
    balance: Long,
    isSurplus: Boolean,
    consecutiveMonths: Int,
    improvementFromLastMonth: Long
) {
    val backgroundColor = if (isSurplus) {
        Color(0xFF4CAF50).copy(alpha = 0.1f)
    } else {
        Color(0xFFF44336).copy(alpha = 0.1f)
    }

    val textColor = if (isSurplus) {
        Color(0xFF2E7D32)
    } else {
        Color(0xFFC62828)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "?´ë² ???ì",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (consecutiveMonths > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF9800).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("?¥", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${consecutiveMonths}ê°ì ?°ì",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSurplus) "+" else "",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "%,d".format(balance),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor
                )
            }

            if (improvementFromLastMonth != 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                val isImproved = improvementFromLastMonth > 0
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isImproved) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isImproved) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "지난달 대비 ${if (isImproved) "+" else ""}${String.format("%,d", improvementFromLastMonth)}원",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isImproved) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
        }
    }
}

@Composable
fun InvestmentRecommendationCard(
    recommendation: InvestmentRecommendation,
    onLearnMore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recommendation.icon,
                    fontSize = 32.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Level ${recommendation.level}: ${recommendation.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = recommendation.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recommendation.recommendations.forEach { rec ->
                    Row(
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = rec,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onLearnMore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI ì½ì¹­?ì ???ìë³´ê¸°")
            }
        }
    }
}

@Composable
fun AchievementBadge(achievement: Achievement) {
    Card(
        modifier = Modifier.size(100.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = achievement.icon,
                fontSize = 36.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun AchievementProgressCard(achievement: Achievement) {
    val animatedProgress by animateFloatAsState(
        targetValue = achievement.progressPercent,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label = "achievementProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ë¯¸í´ê¸??ì´ì½?(ë°í¬ëª"
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = achievement.icon,
                    fontSize = 24.sp,
                    color = Color.Gray.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${achievement.progress} / ${achievement.targetProgress}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NextStepCard(
    currentBalance: Long,
    savingsRate: Int,
    consecutiveMonths: Int
) {
    val (stepTitle, stepDescription, stepIcon) = when {
        currentBalance < 0 -> Triple(
            "ì²??ì ?¬ì±?ê¸°",
            "ì§ì¶ì ì¡°ê¸ë§?ì¤ì´ë©??ìë¥??¬ì±?????ì´",
            "?¯"
        )
        savingsRate < 10 -> Triple(
            "?ì¶ë¥  10% ?¬ì±?ê¸°",
            "?ì??10%ë¥??ì¶í´ë³´ì¸?? ?ì? ?ì????ë³?ë? ë§ë¤?´ì!",
            "?°"
        )
        consecutiveMonths < 3 -> Triple(
            "3ê°ì ?°ì ?ì ?ì ",
            "ê¾¸ì??¨ì´ ?µê???ë§ë¤?´ì. ${3 - consecutiveMonths}ê°ìë§",
            "?¥"
        )
        savingsRate < 20 -> Triple(
            "?ì¶ë¥  20% ?¬ì±?ê¸°",
            "?¬ì  ?ë¬¸ê°?¤ì´ ì¶ì²?ë ?´ì?ì¸ ?ì¶ë¥ ?´ì",
            "?"
        )
        else -> Triple(
            "?¬ì ?ì?ê¸°",
            "?ì ?ì¸ ?ì¶??µê????ê²¼?´ì. ?´ì  ?¬ìë¡??ì°??ë¶ë ¤ë³´ì¸",
            "?"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stepIcon,
                fontSize = 40.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "?¤ì ëª©í",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stepDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun getEncouragementMessage(balance: Long, consecutiveMonths: Int): Triple<String, String, String> {
    return when {
        balance < 0 && consecutiveMonths == 0 -> Triple(
            "?ë´?¸ì!",
            "ì§ì¶ì ì¡°ê¸ë§?ì¤ì´ë©??ì ?í??ê°?¥í´?? ?ì? ë³?ë????ì?´ë³´?¸ì!",
            "?ª"
        )
        balance >= 0 && consecutiveMonths == 1 -> Triple(
            "ì¶í?´ì! ?",
            "ì²??ì ?¬ì±! ???ì´?¤ë? ? ì??ë©´ ëª©í??ë¹ ë¥´ê²??ë¬?????ì´",
            "?"
        )
        consecutiveMonths >= 12 -> Triple(
            "?¬ì  ë§ì¤?? ?",
            "1???´ë´ ?ì?¼ë ?ë§ ??¨í´?? ?´ì  ?¬ìë¡??ì°??ë¶ë ¤ë³´ë ê±??´ë",
            "?"
        )
        consecutiveMonths >= 6 -> Triple(
            "ê¾¸ì??¨ì ?? ?",
            "ë°ë ?°ì ?ì! ??ê´ë¦¬ê? ?´ì  ?µê????ì?¤ì!",
            "")
        consecutiveMonths >= 3 -> Triple(
            "?µê????ì´ê°?? ?¿",
            "3ê°ì ?°ì ?±ê³µ! ì¢ì? ?µê????ë¦¬?¡ê³  ?ì´",
            "?±"
        )
        balance > 500000 -> Triple(
            "??¨í´?? ",
            "?´ë² ??${"%,d".format(balance)}?ì ëª¨ì?´ì! ëª©í ?ì¶ì ?ì ?´ë³´?¸ì!",
            "?°"
        )
        balance > 0 -> Triple(
            "?íê³??ì´?? â­",
            "?ì ? ì? ì¤? ??ëª¨ë©???ê³ì ?´ì´ê°?¸ì!",
            "?"
        )
        else -> Triple(
            "?¤ì ?ì?´ì!",
            "?ë²????ì??ì?? ?¤ì ?ìë¡??ìê°????ì´",
            "?ª"
        )
    }
}
