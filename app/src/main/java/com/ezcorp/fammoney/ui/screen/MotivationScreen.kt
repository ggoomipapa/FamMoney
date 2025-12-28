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
                title = { Text("재정 건강") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "뒤로")
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
                    text = "🏆 획득한 배지",
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
                            Text("🎯", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "아직 획득한 배지가 없어요",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "흑자를 달성하면 첫 배지를 받을 수 있어요",
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
                    text = "🎯 진행 중인 업적",
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
                        label = "연속 흑자",
                        value = "${consecutiveSurplusMonths}개월",
                        icon = "🔥"
                    )
                    StatItem(
                        label = "저축률",
                        value = "${savingsRate}%",
                        icon = "💰"
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
                                text = "다음 레벨까지",
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
                    text = "이번 달 결산",
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
                            Text("🔥", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${consecutiveMonths}개월 연속",
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
                Text("AI 코칭에서 더 알아보기")
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
            "첫 흑자 달성하기",
            "지출을 조금만 줄이면 흑자를 달성할 수 있어요!",
            "🎯"
        )
        savingsRate < 10 -> Triple(
            "저축률 10% 달성하기",
            "수입의 10%를 저축해보세요. 작은 습관이 큰 변화를 만들어요!",
            "💰"
        )
        consecutiveMonths < 3 -> Triple(
            "3개월 연속 흑자 달성",
            "꾸준함이 습관을 만들어요. ${3 - consecutiveMonths}개월 남았어요!",
            "🔥"
        )
        savingsRate < 20 -> Triple(
            "저축률 20% 달성하기",
            "투자 전문가들이 추천하는 이상적인 저축률이에요!",
            "📊"
        )
        else -> Triple(
            "투자 시작하기",
            "안정적인 저축 습관이 생겼어요. 이제 투자로 자산을 불려보세요!",
            "📈"
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
                    text = "다음 목표",
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
            "힘내세요!",
            "지출을 조금만 줄이면 흑자 달성이 가능해요! 작은 변화부터 시작해보세요!",
            "💪"
        )
        balance >= 0 && consecutiveMonths == 1 -> Triple(
            "축하해요! 🎉",
            "첫 흑자 달성! 이 페이스를 유지하면 목표에 빠르게 도달할 수 있어요!",
            "🎊"
        )
        consecutiveMonths >= 12 -> Triple(
            "투자 마스터! 🏆",
            "1년 내내 흑자를 달성했어요! 이제 투자로 자산을 불려보는 건 어때요?",
            "👑"
        )
        consecutiveMonths >= 6 -> Triple(
            "꾸준함의 힘! 🌟",
            "반년 연속 흑자! 재정관리가 이제 습관이 되었어요!",
            "⭐"
        )
        consecutiveMonths >= 3 -> Triple(
            "습관이 되어가요! 🌱",
            "3개월 연속 성공! 좋은 습관을 만들어가고 있어요!",
            "🌳"
        )
        balance > 500000 -> Triple(
            "잘하고 있어요! 💚",
            "이번 달 ${"%,d".format(balance)}원을 모았어요! 목표 저축에 추가해보세요!",
            "💰"
        )
        balance > 0 -> Triple(
            "잘하고 있어요! ⭐",
            "작은 금액이라도 모으면 커져요. 계속 이어가세요!",
            "✨"
        )
        else -> Triple(
            "다시 시작해요!",
            "실패는 성공의 어머니! 다시 새로운 마음으로 시작해봐요!",
            "💪"
        )
    }
}

