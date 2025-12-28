package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.User
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ?ê¸???´ë¦ê³?ê·¸ë£¹ ë©¤ë²ë¥?ë§¤ì¹­?ë ?ë¹?? * ?ë¤?? ?¤ëª, ë³ì¹­, ë§ì¤?¹ë ?´ë¦ ???¤ì???í???´ë¦ ë§¤ì¹­ ì§?? */
@Singleton
class MemberMatcher @Inject constructor() {

    /**
     * ë§¤ì¹­ ê²°ê³¼
     */
    sealed class MatchResult {
        /**
         * ?ì? ? ë¢°?ë¡ ë§¤ì¹­??(?ë ì²ë¦¬ ê°??
         */
        data class HighConfidence(
            val userId: String,
            val userName: String,
            val matchedName: String,
            val matchType: MatchType,
            val confidence: Float  // 0.0 ~ 1.0
        ) : MatchResult()

        /**
         * ??? ? ë¢°??(?¬ì©???ì¸ ?ì)
         */
        data class LowConfidence(
            val candidates: List<Candidate>,
            val detectedName: String
        ) : MatchResult()

        /**
         * ë§¤ì¹­ ?¤í¨
         */
        data class NoMatch(
            val detectedName: String
        ) : MatchResult()
    }

    data class Candidate(
        val user: User,
        val matchType: MatchType,
        val confidence: Float
    )

    enum class MatchType {
        NICKNAME_EXACT,      // ?ë¤???í???¼ì¹
        REALNAME_EXACT,      // ?¤ëª ?í???¼ì¹
        ALIAS_EXACT,         // ë³ì¹­ ?í???¼ì¹
        MASKED_NAME,         // ë§ì¤?¹ë ?´ë¦ ë§¤ì¹­ (ê¹*??
        PARTIAL_MATCH        // ë¶ë¶??¼ì¹
    }

    /**
     * ?ê¸???´ë¦ê³?ê·¸ë£¹ ë©¤ë² ë§¤ì¹­
     */
    fun matchSenderToMember(
        senderName: String,
        groupMembers: List<User>
    ): MatchResult {
        if (senderName.isBlank()) {
            return MatchResult.NoMatch("")
        }

        val candidates = mutableListOf<Candidate>()

        for (member in groupMembers) {
            // 1. ?ë¤???í???¼ì¹
            if (senderName == member.name) {
                return MatchResult.HighConfidence(
                    userId = member.id,
                    userName = member.name,
                    matchedName = senderName,
                    matchType = MatchType.NICKNAME_EXACT,
                    confidence = 1.0f
                )
            }

            // 2. ?¤ëª ?í???¼ì¹
            if (member.realName.isNotBlank() && senderName == member.realName) {
                return MatchResult.HighConfidence(
                    userId = member.id,
                    userName = member.name,
                    matchedName = senderName,
                    matchType = MatchType.REALNAME_EXACT,
                    confidence = 1.0f
                )
            }

            // 3. ë³ì¹­ ëª©ë¡???ëì§ ?ì¸
            if (member.aliasNames.contains(senderName)) {
                return MatchResult.HighConfidence(
                    userId = member.id,
                    userName = member.name,
                    matchedName = senderName,
                    matchType = MatchType.ALIAS_EXACT,
                    confidence = 0.95f
                )
            }

            // 4. ë§ì¤?¹ë ?´ë¦ ë§¤ì¹­ (ê¹*??-> ê¹ì² ì)
            if (senderName.contains("*")) {
                if (matchesMaskedName(senderName, member.name)) {
                    candidates.add(Candidate(member, MatchType.MASKED_NAME, 0.7f))
                } else if (member.realName.isNotBlank() && matchesMaskedName(senderName, member.realName)) {
                    candidates.add(Candidate(member, MatchType.MASKED_NAME, 0.75f))
                }
            }

            // 5. ë¶ë¶?ë§¤ì¹­ (?±ì´ ê°ê±°???´ë¦??ê°ì? ê²½ì°)
            val partialConfidence = calculatePartialMatchConfidence(senderName, member)
            if (partialConfidence > 0.3f) {
                candidates.add(Candidate(member, MatchType.PARTIAL_MATCH, partialConfidence))
            }
        }

        // ?ë³´??ê²°ê³¼ ì²ë¦¬
        return when {
            candidates.isEmpty() -> {
                MatchResult.NoMatch(senderName)
            }
            candidates.size == 1 && candidates[0].confidence >= 0.7f -> {
                val candidate = candidates[0]
                MatchResult.HighConfidence(
                    userId = candidate.user.id,
                    userName = candidate.user.name,
                    matchedName = senderName,
                    matchType = candidate.matchType,
                    confidence = candidate.confidence
                )
            }
            else -> {
                // ? ë¢°???ì¼ë¡??ë ¬
                val sortedCandidates = candidates.sortedByDescending { it.confidence }
                MatchResult.LowConfidence(sortedCandidates, senderName)
            }
        }
    }

    /**
     * ë§ì¤?¹ë ?´ë¦ ë§¤ì¹­
     * ?? "ê¹*?? ? "ê¹ì² ì" ë§¤ì¹­
     */
    private fun matchesMaskedName(masked: String, fullName: String): Boolean {
        if (!masked.contains("*")) return false
        if (masked.length != fullName.length) return false

        return masked.mapIndexed { index, char ->
            char == '*' || char == fullName.getOrNull(index)
        }.all { it }
    }

    /**
     * ë¶ë¶?ë§¤ì¹­ ? ë¢°??ê³ì°
     */
    private fun calculatePartialMatchConfidence(senderName: String, member: User): Float {
        var maxConfidence = 0f

        // ?ë¤?ê³¼ ë¹êµ
        maxConfidence = maxOf(maxConfidence, calculateSimilarity(senderName, member.name))

        // ?¤ëªê³?ë¹êµ
        if (member.realName.isNotBlank()) {
            maxConfidence = maxOf(maxConfidence, calculateSimilarity(senderName, member.realName))
        }

        // ë³ì¹­?¤ê³¼ ë¹êµ
        for (alias in member.aliasNames) {
            maxConfidence = maxOf(maxConfidence, calculateSimilarity(senderName, alias))
        }

        return maxConfidence
    }

    /**
     * ë¬¸ì??? ì¬??ê³ì° (ê°ë¨???ê³ ë¦¬ì¦)
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() || s2.isEmpty()) return 0f
        if (s1 == s2) return 1f

        // ??ì²?ê¸????ê°ì?ì§ ?ì¸
        val sameFirstChar = s1.first() == s2.first()

        // ê³µíµ ë¬¸ì ??ê³ì°
        val commonChars = s1.filter { it in s2 }.length
        val maxLength = maxOf(s1.length, s2.length)

        val similarity = commonChars.toFloat() / maxLength

        // ?±ì´ ê°ì¼ë©?ë³´ë
return if (sameFirstChar) {
            (similarity + 0.2f).coerceAtMost(0.6f)
        } else {
            similarity * 0.5f
        }
    }

    /**
     * ë©¤ë² ?´ë¦ ëª©ë¡?ì ?´ë¦ ê²
* ?ë¤?? ?¤ëª, ë³ì¹­ ëª¨ë ê²
*/
    fun findMemberByAnyName(name: String, groupMembers: List<User>): User? {
        return groupMembers.find { member ->
            member.name == name ||
            member.realName == name ||
            member.aliasNames.contains(name)
        }
    }
}
