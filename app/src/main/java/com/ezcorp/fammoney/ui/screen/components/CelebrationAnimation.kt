package com.ezcorp.fammoney.ui.screen.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ì¶í ì»¨í???í°?? */
data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val size: Float,
    val velocityX: Float,
    val velocityY: Float,
    val rotation: Float,
    val rotationSpeed: Float
)

/**
 * ì¶í ? ëë©ì´???¤ì´?¼ë¡ê·? */
@Composable
fun CelebrationDialog(
    title: String,
    message: String,
    icon: String,
    subMessage: String? = null,
    onDismiss: () -> Unit
) {
    var showConfetti by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(3000)
        showConfetti = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // ì»¨í??? ëë©ì´
if (showConfetti) {
                ConfettiAnimation(
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ë©ì¸ ì¹´ë
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ?ì´ì½?with ?ì¤ ? ëë©ì´
PulsingIcon(icon = icon)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    if (subMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = subMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("?ì¸", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/**
 * ?ì¤ ?¨ê³¼ê° ?ë ?ì´ì½? */
@Composable
fun PulsingIcon(icon: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size((80 * scale).dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = (48 * scale).sp
        )
    }
}

/**
 * ì»¨í??? ëë©ì´?? */
@Composable
fun ConfettiAnimation(
    modifier: Modifier = Modifier,
    particleCount: Int = 100
) {
    val confettiColors = listOf(
        Color(0xFFFF6B6B),  // ë¹¨ê°
        Color(0xFFFFD93D),  // ?¸ë
        Color(0xFF6BCB77),  // ì´ë¡
        Color(0xFF4D96FF),  // ?ë
        Color(0xFFC9B1FF),  // ë³´ë¼
        Color(0xFFFF8E9E),  // ë¶í
        Color(0xFF95E1D3)   // ë¯¼í¸
    )

    var particles by remember {
        mutableStateOf(
            List(particleCount) {
                ConfettiParticle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat() * -1f,  // ?ë©´ ?ì???ì
                color = confettiColors.random(),
                    size = Random.nextFloat() * 10f + 5f,
                    velocityX = (Random.nextFloat() - 0.5f) * 0.02f,
                    velocityY = Random.nextFloat() * 0.01f + 0.005f,
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = (Random.nextFloat() - 0.5f) * 10f
                )
            }
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(16) // ~60fps
            particles = particles.map { p ->
                p.copy(
                    x = p.x + p.velocityX,
                    y = p.y + p.velocityY,
                    rotation = p.rotation + p.rotationSpeed,
                    velocityY = p.velocityY + 0.0002f  // ì¤ë ¥
                )
            }
        }
    }

    Canvas(modifier = modifier) {
        particles.forEach { particle ->
            val x = particle.x * size.width
            val y = particle.y * size.height

            if (y < size.height && y > -100) {
                rotate(particle.rotation, pivot = Offset(x, y)) {
                    drawRect(
                        color = particle.color,
                        topLeft = Offset(x - particle.size / 2, y - particle.size / 2),
                        size = androidx.compose.ui.geometry.Size(particle.size, particle.size * 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * ?ì  ?´ê¸ ?¤ì´?¼ë¡ê·? */
@Composable
fun AchievementUnlockedDialog(
    achievementTitle: String,
    achievementDescription: String,
    achievementIcon: String,
    onDismiss: () -> Unit
) {
    CelebrationDialog(
        title = "? ?ì  ?¬ì±!",
        message = "$achievementIcon $achievementTitle",
        icon = achievementIcon,
        subMessage = achievementDescription,
        onDismiss = onDismiss
    )
}

/**
 * ?ë²¨???¤ì´?¼ë¡ê·? */
@Composable
fun LevelUpDialog(
    newLevel: Int,
    levelTitle: String,
    levelIcon: String,
    nextLevelHint: String? = null,
    onDismiss: () -> Unit
) {
    CelebrationDialog(
        title = "? ?ë²¨ ",
        message = "Lv.$newLevel $levelIcon $levelTitle",
        icon = levelIcon,
        subMessage = nextLevelHint,
        onDismiss = onDismiss
    )
}

/**
 * ?ì ?í ì¶í ?¤ì´?¼ë¡ê·? */
@Composable
fun SurplusAchievedDialog(
    surplusAmount: Long,
    previousDeficit: Long? = null,
    consecutiveMonths: Int = 1,
    onDismiss: () -> Unit
) {
    val formattedSurplus = "%,d".format(surplusAmount)

    val message = when {
        consecutiveMonths == 1 && previousDeficit != null -> {
            "?ì?ì ?ìë¡? $formattedSurplus ?ì ëª¨ì?´ì"
        }
        consecutiveMonths > 1 -> {
            "${consecutiveMonths}ê°ì ?°ì ?ì! ?´ë² ??$formattedSurplus ???ì½"
        }
        else -> {
            "?´ë² ??$formattedSurplus ?ì ëª¨ì?´ì!"
        }
    }

    val icon = when {
        consecutiveMonths >= 12 -> "?"
        consecutiveMonths >= 6 -> "?³"
        consecutiveMonths >= 3 -> "?¿"
        consecutiveMonths >= 1 -> "?±"
        else -> "?"
    }

    val subMessage = when {
        consecutiveMonths >= 6 -> "??¨í´?? ?¬ì  ê´ë¦¬ì ?¬ì¸?´ì?¤ì!"
        consecutiveMonths >= 3 -> "?µê????ì´ê°ê³??ì´?? ê³ì ?ë´?¸ì!"
        previousDeficit != null -> "ì§?ë¬ë³´ë¤ ${"%,d".format(surplusAmount + (previousDeficit ?: 0))}?ì´??ê°ì ?ì´"
        else -> "???ì´?¤ë? ? ì??ë©´ ??ëª©í???¬ì±?????ì´"
    }

    CelebrationDialog(
        title = if (consecutiveMonths == 1 && previousDeficit != null) "? ?ì ?í ?±ê³µ!" else "???´ë² ?¬ë ?ì!",
        message = message,
        icon = icon,
        subMessage = subMessage,
        onDismiss = onDismiss
    )
}

/**
 * ëª©í ?¬ì± ì¶í ?¤ì´?¼ë¡ê·? */
@Composable
fun GoalAchievedDialog(
    goalName: String,
    goalAmount: Long,
    daysToAchieve: Int,
    onDismiss: () -> Unit
) {
    val formattedAmount = "%,d".format(goalAmount)

    CelebrationDialog(
        title = "?¯ ëª©í ?¬ì±!",
        message = "$goalName\n$formattedAmount ??ëª¨ì¼ê¸??ë£!",
        icon = "?",
        subMessage = "${daysToAchieve}??ë§ì ëª©íë¥??¬ì±?ì´",
        onDismiss = onDismiss
    )
}

/**
 * ê²©ë ¤ ë©ìì§ ì¹´ë (? ëë©ì´???ì´ ?¨ì ?ì?"
 */
@Composable
fun EncouragementCard(
    title: String,
    message: String,
    icon: String,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 32.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * ì§íë¥??ì ì¹´ë with ? ëë©ì´?? */
@Composable
fun ProgressCard(
    title: String,
    currentValue: Long,
    targetValue: Long,
    icon: String,
    modifier: Modifier = Modifier
) {
    val progress = (currentValue.toFloat() / targetValue).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = EaseOutCubic),
        label = "progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = icon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = when {
                    progress >= 1f -> Color(0xFF4CAF50)
                    progress >= 0.7f -> Color(0xFF8BC34A)
                    progress >= 0.5f -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "%,d원".format(currentValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%,d원".format(targetValue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
