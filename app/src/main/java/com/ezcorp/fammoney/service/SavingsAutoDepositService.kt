package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.LearnedDepositPattern
import com.ezcorp.fammoney.data.model.SavingsContribution
import com.ezcorp.fammoney.data.model.SavingsGoal
import com.ezcorp.fammoney.data.model.User
import com.ezcorp.fammoney.data.repository.LearnedPatternRepository
import com.ezcorp.fammoney.data.repository.SavingsGoalRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ëª©í ?ì¶??ë ?ê¸ ê°ì? ?ë¹?? * ?ê¸ ?ë¦¼??ë¶ì?ì¬ ëª©í ?ì¶ì ?ë?¼ë¡ ê¸°ì¬ ì¶ê?
 */
@Singleton
class SavingsAutoDepositService @Inject constructor(
    private val savingsGoalRepository: SavingsGoalRepository,
    private val learnedPatternRepository: LearnedPatternRepository,
    private val memberMatcher: MemberMatcher
) {
    /**
     * ?ê¸ ì²ë¦¬ ê²°ê³¼
     */
    sealed class DepositProcessResult {
        /**
         * ?ë ì²ë¦¬ ?ë£
         */
        data class AutoProcessed(
            val savingsGoal: SavingsGoal,
            val contribution: SavingsContribution,
            val matchedUser: User
        ) : DepositProcessResult()

        /**
         * ?¬ì©???ì¸ ?ì
         */
        data class NeedsConfirmation(
            val savingsGoal: SavingsGoal,
            val amount: Long,
            val detectedSenderName: String,
            val candidates: List<MemberMatcher.Candidate>,
            val originalText: String
        ) : DepositProcessResult()

        /**
         * ?ë ?ë ¥ ?ì
         */
        data class NeedsManualInput(
            val savingsGoal: SavingsGoal,
            val amount: Long?,
            val originalText: String,
            val reason: String
        ) : DepositProcessResult()

        /**
         * ?´ë¹ ?ì (?°ë??ëª©íê° ?ê±°??ê³ì¢ ë¶ì¼ì¹?
         */
        object NotApplicable : DepositProcessResult()
    }

    /**
     * ?ê¸ ?ë¦¼ ì²ë¦¬
     * @param parsedTransaction ?ì±??ê±°ë ?ë³´
     * @param groupId ê·¸ë£¹ ID
     * @param groupMembers ê·¸ë£¹ ë©¤ë² ëª©ë¡
     * @return ì²ë¦¬ ê²°ê³¼ ëª©ë¡ (?¬ë¬ ëª©í???´ë¹?????ì)
     */
    suspend fun processDepositNotification(
        amount: Long,
        senderName: String,
        accountNumber: String,
        originalText: String,
        groupId: String,
        groupMembers: List<User>
    ): List<DepositProcessResult> {
        val results = mutableListOf<DepositProcessResult>()

        // 1. ?ë ?ê¸???ì±?ë ëª©í ì¡°í
        val autoDepositGoals = savingsGoalRepository.getAutoDepositEnabledGoals(groupId)

        if (autoDepositGoals.isEmpty()) {
            return listOf(DepositProcessResult.NotApplicable)
        }

        for (goal in autoDepositGoals) {
            // 2. ê³ì¢ë²í¸ ë§¤ì¹­ ?ì¸
            if (!matchesAccountNumber(accountNumber, originalText, goal.linkedAccountNumber)) {
                continue
            }

            // 3. ?ê¸???´ë¦ ë§¤ì¹­
            val matchResult = if (senderName.isNotBlank()) {
                memberMatcher.matchSenderToMember(senderName, groupMembers)
            } else {
                // ?´ë¦ ?ì± ?¤í¨ - ?ìµ???¨í´?¼ë¡ ?¬ì
tryLearnedPatterns(originalText, goal.id, groupMembers)
            }

            // 4. ë§¤ì¹­ ê²°ê³¼???°ë¥¸ ì²ë¦¬
            val result = when (matchResult) {
                is MemberMatcher.MatchResult.HighConfidence -> {
                    // ?ë ì²ë¦¬
                val contribution = createContribution(
                        goal = goal,
                        userId = matchResult.userId,
                        userName = matchResult.userName,
                        amount = amount,
                        detectedSenderName = senderName,
                        matchConfidence = when {
                            matchResult.confidence >= 0.95f -> "high"
                            matchResult.confidence >= 0.7f -> "medium"
                            else -> "low"
                        },
                        originalText = originalText
                    )

                    val matchedUser = groupMembers.find { it.id == matchResult.userId }
                    if (matchedUser != null) {
                        DepositProcessResult.AutoProcessed(goal, contribution, matchedUser)
                    } else {
                        DepositProcessResult.NeedsManualInput(
                            goal, amount, originalText,
                            "ë§¤ì¹­???¬ì©?ë? ì°¾ì ???ìµ?ë¤"
                        )
                    }
                }

                is MemberMatcher.MatchResult.LowConfidence -> {
                    // ?¬ì©???ì¸ ?ì
                DepositProcessResult.NeedsConfirmation(
                        savingsGoal = goal,
                        amount = amount,
                        detectedSenderName = matchResult.detectedName,
                        candidates = matchResult.candidates,
                        originalText = originalText
                    )
                }

                is MemberMatcher.MatchResult.NoMatch -> {
                    // ?ë ?ë ¥ ?ì
                DepositProcessResult.NeedsManualInput(
                        savingsGoal = goal,
                        amount = amount,
                        originalText = originalText,
                        reason = if (senderName.isBlank()) {
                            "?ê¸???´ë¦??ì¶ì¶?????ìµ?ë¤"
                        } else {
                            "'$senderName'? ?¼ì¹?ë ë©¤ë²ê° ?ìµ?ë¤"
                        }
                    )
                }
            }

            results.add(result)
        }

        return results.ifEmpty { listOf(DepositProcessResult.NotApplicable) }
    }

    /**
     * ê³ì¢ë²í¸ ë§¤ì¹­ ?ì¸
     */
    private fun matchesAccountNumber(
        parsedAccountNumber: String,
        originalText: String,
        linkedAccountNumber: String
    ): Boolean {
        if (linkedAccountNumber.isBlank()) return false

        // ?í??ê³ì¢ë²í¸ ë§¤ì¹­
        if (parsedAccountNumber.isNotBlank()) {
            // ?«ìë§?ì¶ì¶?ì¬ ë¹êµ
            val parsedDigits = parsedAccountNumber.filter { it.isDigit() }
            val linkedDigits = linkedAccountNumber.filter { it.isDigit() }

            if (parsedDigits == linkedDigits) return true

            // ?ìë¦?4?ë¦¬ ë¹êµ (ë§ì¤?¹ë ê²½ì°)
            if (parsedDigits.length >= 4 && linkedDigits.length >= 4) {
                if (parsedDigits.takeLast(4) == linkedDigits.takeLast(4)) return true
            }
        }

        // ?ë³¸ ?ì¤?¸ì??ê³ì¢ë²í¸ ê²
val linkedDigits = linkedAccountNumber.filter { it.isDigit() }
        if (linkedDigits.length >= 4) {
            // ?ìë¦?4?ë¦¬ê° ?¬í¨?ì´ ?ëì§ ?ì¸
            if (originalText.contains(linkedDigits.takeLast(4))) return true
        }

        return false
    }

    /**
     * ?ìµ???¨í´?¼ë¡ ?´ë¦ ì¶ì¶ ?ë
     */
    private suspend fun tryLearnedPatterns(
        originalText: String,
        savingsGoalId: String,
        groupMembers: List<User>
    ): MemberMatcher.MatchResult {
        val patterns = learnedPatternRepository.getActivePatterns(savingsGoalId)

        for (pattern in patterns) {
            if (pattern.senderNameRegex.isBlank()) continue

            try {
                val regex = Regex(pattern.senderNameRegex)
                val match = regex.find(originalText)
                if (match != null) {
                    val extractedName = match.groupValues.getOrNull(1)?.trim() ?: continue

                    // ì¶ì¶???´ë¦?¼ë¡ ë©¤ë² ë§¤ì¹­ ?ë
                val result = memberMatcher.matchSenderToMember(extractedName, groupMembers)
                    if (result !is MemberMatcher.MatchResult.NoMatch) {
                        // ?¨í´ ?±ê³µ ì¹´ì´??ì¦ê"
                learnedPatternRepository.incrementSuccessCount(pattern.id)
                        return result
                    }
                }
            } catch (e: Exception) {
                // ?ê·???¤ë¥ - ?¨í´ ?¤í¨ ì¹´ì´??ì¦ê?
                learnedPatternRepository.incrementFailCount(pattern.id)
            }
        }

        return MemberMatcher.MatchResult.NoMatch("")
    }

    /**
     * ê¸°ì¬ ?´ì­ ?ì±
     */
    private fun createContribution(
        goal: SavingsGoal,
        userId: String,
        userName: String,
        amount: Long,
        detectedSenderName: String,
        matchConfidence: String,
        originalText: String
    ): SavingsContribution {
        return SavingsContribution(
            goalId = goal.id,
            userId = userId,
            userName = userName,
            amount = amount,
            isAutoDetected = true,
            detectedSenderName = detectedSenderName,
            matchConfidence = matchConfidence,
            originalNotificationText = originalText,
            needsReview = matchConfidence != "high"
        )
    }

    /**
     * ?ë ì²ë¦¬??ê¸°ì¬ ?
*/
    suspend fun saveAutoContribution(
        contribution: SavingsContribution
    ): Result<Unit> {
        return savingsGoalRepository.addContribution(
            goalId = contribution.goalId,
            userId = contribution.userId,
            userName = contribution.userName,
            amount = contribution.amount,
            isAutoDetected = contribution.isAutoDetected,
            detectedSenderName = contribution.detectedSenderName,
            matchConfidence = contribution.matchConfidence,
            originalNotificationText = contribution.originalNotificationText,
            needsReview = contribution.needsReview
        )
    }

    /**
     * ?ë ?ì¸??ê¸°ì¬ ?
*/
    suspend fun saveManualContribution(
        goalId: String,
        userId: String,
        userName: String,
        amount: Long,
        originalText: String,
        learnPattern: Boolean,
        confirmedSenderName: String,
        groupId: String,
        bankName: String
    ): Result<Unit> {
        // ê¸°ì¬ ?
val result = savingsGoalRepository.addContribution(
            goalId = goalId,
            userId = userId,
            userName = userName,
            amount = amount,
            isAutoDetected = false,
            detectedSenderName = confirmedSenderName,
            matchConfidence = "manual",
            originalNotificationText = originalText,
            needsReview = false
        )

        // ?¨í´ ?ìµ
        if (learnPattern && result.isSuccess) {
            learnedPatternRepository.learnPatternFromUserInput(
                groupId = groupId,
                savingsGoalId = goalId,
                originalText = originalText,
                confirmedAmount = amount,
                confirmedSenderName = confirmedSenderName,
                bankName = bankName
            )
        }

        return result
    }
}
