package com.ezcorp.fammoney.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezcorp.fammoney.data.model.*
import com.ezcorp.fammoney.data.repository.TransactionRepository
import com.ezcorp.fammoney.service.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class MotivationUiState(
    val isLoading: Boolean = false,
    val currentLevel: UserLevel = LevelSystem.levels.first(),
    val nextLevel: UserLevel? = LevelSystem.levels.getOrNull(1),
    val progressToNextLevel: Float = 0f,
    val consecutiveSurplusMonths: Int = 0,
    val savingsRate: Int = 0,
    val currentMonthBalance: Long = 0L,
    val improvementFromLastMonth: Long = 0L,
    val totalSavings: Long = 0L,
    val unlockedAchievements: List<Achievement> = emptyList(),
    val inProgressAchievements: List<Achievement> = emptyList(),
    val investmentRecommendation: InvestmentRecommendation = InvestmentGuide.recommendations.first()
)

@HiltViewModel
class MotivationViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MotivationUiState())
    val uiState: StateFlow<MotivationUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val groupId = userPreferences.getGroupId() ?: return@launch
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            val currentMonth = calendar.get(Calendar.MONTH) + 1

            // ìµê·¼ 12ê°ì ?°ì´??ë¡ë
            val monthlyBalances = mutableListOf<Pair<String, Long>>()
            var totalIncome = 0L
            var totalExpense = 0L

            for (i in 0 until 12) {
                val year = if (currentMonth - i <= 0) currentYear - 1 else currentYear
                val month = if (currentMonth - i <= 0) currentMonth - i + 12 else currentMonth - i

                transactionRepository.getTransactionsByMonth(groupId, year, month).first().let { transactions ->
                    var monthIncome = 0L
                    var monthExpense = 0L

                    transactions.forEach { t ->
                        if (t.type == TransactionType.INCOME) {
                            monthIncome += t.amount
                        } else {
                            monthExpense += t.amount
                        }
                    }

                    val balance = monthIncome - monthExpense
                    monthlyBalances.add("$year-${month.toString().padStart(2, '0')}" to balance)

                    if (i == 0) {
                        totalIncome = monthIncome
                        totalExpense = monthExpense
                    }
                }
            }

            // ?´ë² ???ì"
            val currentMonthBalance = monthlyBalances.firstOrNull()?.second ?: 0L

            // ì§?ë¬ ?ë¹?ê°ì 
            val lastMonthBalance = monthlyBalances.getOrNull(1)?.second ?: 0L
            val improvement = currentMonthBalance - lastMonthBalance

            // ?°ì ?ì ê°ì ??ê³ì°
            var consecutiveSurplus = 0
            for ((_, balance) in monthlyBalances) {
                if (balance >= 0) {
                    consecutiveSurplus++
                } else {
                    break
                }
            }

            // ?ì¶ë¥  ê³ì°
            val savingsRate = if (totalIncome > 0) {
                ((currentMonthBalance.toFloat() / totalIncome) * 100).toInt().coerceAtLeast(0)
            } else 0

            // ì´??ì¶ì¡ (?ì ?ì ??
            val totalSavings = monthlyBalances.filter { it.second > 0 }.sumOf { it.second }

            // ?ë²¨ ê³ì°
            val currentLevel = LevelSystem.getLevelForStats(consecutiveSurplus, savingsRate)
            val nextLevel = LevelSystem.getNextLevel(currentLevel)

            // ?¤ì ?ë²¨ ì§í??ê³ì°
            val progressToNextLevel = if (nextLevel != null) {
                val monthProgress = (consecutiveSurplus.toFloat() / nextLevel.minSurplusMonths).coerceAtMost(1f)
                val rateProgress = (savingsRate.toFloat() / nextLevel.minSavingsRate).coerceAtMost(1f)
                (monthProgress + rateProgress) / 2
            } else 1f

            // ?ì  ê³ì°
            val (unlocked, inProgress) = calculateAchievements(
                consecutiveSurplus = consecutiveSurplus,
                totalSavings = totalSavings,
                currentBalance = currentMonthBalance
            )

            // ?¬ì ì¶ì²
            val investmentRec = InvestmentGuide.getRecommendationForSurplus(currentMonthBalance)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentLevel = currentLevel,
                    nextLevel = nextLevel,
                    progressToNextLevel = progressToNextLevel,
                    consecutiveSurplusMonths = consecutiveSurplus,
                    savingsRate = savingsRate,
                    currentMonthBalance = currentMonthBalance,
                    improvementFromLastMonth = improvement,
                    totalSavings = totalSavings,
                    unlockedAchievements = unlocked,
                    inProgressAchievements = inProgress,
                    investmentRecommendation = investmentRec
                )
            }
        }
    }

    private fun calculateAchievements(
        consecutiveSurplus: Int,
        totalSavings: Long,
        currentBalance: Long
    ): Pair<List<Achievement>, List<Achievement>> {
        val unlocked = mutableListOf<Achievement>()
        val inProgress = mutableListOf<Achievement>()

        // ì²??ì
        val firstSurplus = Achievements.getById("first_surplus")!!
        if (consecutiveSurplus >= 1) {
            unlocked.add(firstSurplus.copy(unlockedAt = Date()))
        } else if (currentBalance > -100000) {
            inProgress.add(firstSurplus.copy(progress = ((currentBalance + 100000) / 1000).toInt().coerceAtLeast(0)))
        }

        // ?°ì ?ì ë°°ì"
listOf(
            "surplus_streak_3" to 3,
            "surplus_streak_6" to 6,
            "surplus_streak_12" to 12
        ).forEach { (id, target) ->
            val achievement = Achievements.getById(id)!!
            if (consecutiveSurplus >= target) {
                unlocked.add(achievement.copy(unlockedAt = Date(), progress = target))
            } else if (consecutiveSurplus > 0) {
                inProgress.add(achievement.copy(progress = consecutiveSurplus))
            }
        }

        // ?ì¶?ë§ì¼?¤í¤
        listOf(
            "savings_100k" to 100000L,
            "savings_500k" to 500000L,
            "savings_1m" to 1000000L,
            "savings_5m" to 5000000L,
            "savings_10m" to 10000000L
        ).forEach { (id, target) ->
            val achievement = Achievements.getById(id)!!
            if (totalSavings >= target) {
                unlocked.add(achievement.copy(unlockedAt = Date(), progress = target.toInt()))
            } else {
                inProgress.add(achievement.copy(progress = totalSavings.toInt().coerceAtMost(target.toInt())))
            }
        }

        // ì¤ë³µ ?ê±° ë°?ê°???ì? ?¨ê³ë§??ì
        val filteredUnlocked = unlocked.distinctBy { it.id }
        val filteredInProgress = inProgress
            .distinctBy { it.id }
            .filterNot { ip -> filteredUnlocked.any { u -> u.type == ip.type && u.targetProgress >= ip.targetProgress } }
            .take(4)  // ìµë? 4ê°ë§ ?ì

        return filteredUnlocked to filteredInProgress
    }

    fun refresh() {
        loadData()
    }
}
