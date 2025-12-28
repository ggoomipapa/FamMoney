package com.ezcorp.fammoney.data.repository

import com.ezcorp.fammoney.data.model.LearnedDepositPattern
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 학습된 입금 패턴 저장소
 * 사용자가 수동으로 입력한 입금 알림 패턴을 저장하고 관리
 */
@Singleton
class LearnedPatternRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val patternsCollection = firestore.collection("learned_deposit_patterns")

    /**
     * 특정 그룹의 모든 학습된 패턴 조회
     */
    fun getPatternsFlow(groupId: String): Flow<List<LearnedDepositPattern>> = callbackFlow {
        val listener = patternsCollection
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val patterns = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { LearnedDepositPattern.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(patterns)
            }

        awaitClose { listener.remove() }
    }

    /**
     * 특정 목표 저축에 연결된 패턴 조회
     */
    fun getPatternsByGoalFlow(savingsGoalId: String): Flow<List<LearnedDepositPattern>> = callbackFlow {
        val listener = patternsCollection
            .whereEqualTo("savingsGoalId", savingsGoalId)
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val patterns = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { LearnedDepositPattern.fromMap(doc.id, it) }
                } ?: emptyList()

                trySend(patterns)
            }

        awaitClose { listener.remove() }
    }

    /**
     * 특정 그룹의 활성화된 패턴 목록 조회 (일회성)
     */
    suspend fun getActivePatterns(groupId: String): List<LearnedDepositPattern> {
        return try {
            val snapshot = patternsCollection
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("isActive", true)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { LearnedDepositPattern.fromMap(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 새 패턴 저장
     */
    suspend fun savePattern(pattern: LearnedDepositPattern): Result<String> {
        return try {
            val docRef = patternsCollection.add(pattern.toMap()).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 패턴 사용 통계 업데이트 (성공)
     */
    suspend fun incrementSuccessCount(patternId: String): Result<Unit> {
        return try {
            val docRef = patternsCollection.document(patternId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentCount = (snapshot.getLong("successCount") ?: 0) + 1
                transaction.update(docRef, mapOf(
                    "successCount" to currentCount,
                    "lastUsedAt" to Timestamp.now()
                ))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 패턴 사용 통계 업데이트 (실패)
     */
    suspend fun incrementFailCount(patternId: String): Result<Unit> {
        return try {
            val docRef = patternsCollection.document(patternId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentCount = (snapshot.getLong("failCount") ?: 0) + 1
                transaction.update(docRef, "failCount", currentCount)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 패턴 비활성화
     */
    suspend fun deactivatePattern(patternId: String): Result<Unit> {
        return try {
            patternsCollection.document(patternId)
                .update("isActive", false)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 패턴 삭제
     */
    suspend fun deletePattern(patternId: String): Result<Unit> {
        return try {
            patternsCollection.document(patternId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자 입력으로부터 패턴 학습 및 저장
     */
    suspend fun learnPatternFromUserInput(
        groupId: String,
        savingsGoalId: String,
        originalText: String,
        confirmedAmount: Long,
        confirmedSenderName: String,
        bankName: String
    ): Result<String> {
        // 금액 추출 정규식 생성
        val amountRegex = buildAmountRegex(originalText, confirmedAmount)

        // 이름 추출 정규식 생성
        val senderNameRegex = buildSenderNameRegex(originalText, confirmedSenderName)

        // 계좌번호 패턴 추출
        val accountNumberPattern = extractAccountNumberPattern(originalText)

        val pattern = LearnedDepositPattern(
            groupId = groupId,
            savingsGoalId = savingsGoalId,
            sampleNotificationText = originalText,
            bankName = bankName,
            accountNumberPattern = accountNumberPattern,
            senderNameRegex = senderNameRegex,
            amountRegex = amountRegex,
            isActive = true
        )

        return savePattern(pattern)
    }

    /**
     * 금액 추출 정규식 생성
     */
    private fun buildAmountRegex(text: String, amount: Long): String {
        val formattedAmount = String.format("%,d", amount)
        val plainAmount = amount.toString()

        // 텍스트에서 금액 형식 찾기
        return when {
            text.contains(formattedAmount) -> {
                // 콤마가 있는 형식: "1,000,000원"
                "([0-9,]+)\\s*원"
            }
            text.contains(plainAmount) -> {
                // 콤마 없는 형식: "1000000원"
                "([0-9]+)\\s*원"
            }
            else -> {
                // 기본 패턴
                "([0-9,]+)\\s*원"
            }
        }
    }

    /**
     * 송금자 이름 추출 정규식 생성
     */
    private fun buildSenderNameRegex(text: String, senderName: String): String {
        if (senderName.isBlank()) return ""

        val nameIndex = text.indexOf(senderName)
        if (nameIndex < 0) return ""

        // 이름 주변 컨텍스트 분석
        val beforeContext = text.substring(maxOf(0, nameIndex - 10), nameIndex)
        val afterContext = text.substring(
            minOf(nameIndex + senderName.length, text.length),
            minOf(nameIndex + senderName.length + 10, text.length)
        )

        // 컨텍스트 기반 정규식 생성
        return when {
            beforeContext.contains("입금") -> {
                // "입금 ... 이름" 패턴
                "입금[^가-힣]*([가-힣]{2,4})"
            }
            afterContext.contains("님") -> {
                // "이름님" 패턴
                "([가-힣]{2,4})님"
            }
            afterContext.contains("입금") -> {
                // "이름 ... 입금" 패턴
                "([가-힣]{2,4})[^가-힣]*입금"
            }
            else -> {
                // 기본: 2-4글자 한글 이름
                "([가-힣]{2,4})"
            }
        }
    }

    /**
     * 계좌번호 패턴 추출
     */
    private fun extractAccountNumberPattern(text: String): String {
        val patterns = listOf(
            Regex("(\\d{3,4}-\\d{2,4}-\\d{4,6})"),
            Regex("(\\*{2,4}-\\*{2,4}-\\d{4,6})"),
            Regex("(\\d{3,4}\\*{2,4}\\d{3,6})")
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.value
            }
        }

        return ""
    }
}
