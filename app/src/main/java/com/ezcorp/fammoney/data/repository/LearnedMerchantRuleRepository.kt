package com.ezcorp.fammoney.data.repository

import android.util.Log
import com.ezcorp.fammoney.data.local.LearnedMerchantRuleDao
import com.ezcorp.fammoney.data.model.LearnedMerchantRule
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 학습된 사용처 규칙 Repository
 *
 * 정책 3 준수:
 *   1. User-confirmed LearnedRule
 *   2. Signature-based LearnedRule
 *   3. Heuristic parsing
 *
 * 정책 2 준수: 사용자 수정으로 학습하여 재발 방지
 */
@Singleton
class LearnedMerchantRuleRepository @Inject constructor(
    private val dao: LearnedMerchantRuleDao,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "LearnedMerchantRule"
        // 오래된 규칙 정리 기준: 180일
        private const val PURGE_THRESHOLD_DAYS = 180L
        private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L
        // 글로벌 규칙 컬렉션
        private const val GLOBAL_RULES_COLLECTION = "global_merchant_rules"
        // 글로벌 규칙 신뢰 기준 (최소 투표 수)
        private const val GLOBAL_MIN_VOTES = 3
    }

    /**
     * Signature로 학습된 규칙 조회
     * 우선순위: 로컬 (사용자 확정) > 로컬 (자동) > 글로벌
     *
     * @return 매칭된 규칙 (없으면 null)
     */
    suspend fun findBySignature(signature: String): LearnedMerchantRule? {
        // 1. 로컬 조회 (우선)
        val localRule = dao.findBySignature(signature)
        if (localRule != null) {
            return localRule
        }

        // 2. 글로벌 조회 (로컬에 없을 때)
        return findGlobalRule(signature)
    }

    /**
     * 글로벌 규칙 조회 (Firebase)
     */
    private suspend fun findGlobalRule(signature: String): LearnedMerchantRule? {
        return try {
            val docId = signature.hashCode().toString()
            val doc = firestore.collection(GLOBAL_RULES_COLLECTION)
                .document(docId)
                .get()
                .await()

            if (doc.exists()) {
                val voteCount = doc.getLong("voteCount")?.toInt() ?: 0
                // 최소 투표 수 이상이어야 신뢰
                if (voteCount >= GLOBAL_MIN_VOTES) {
                    LearnedMerchantRule(
                        id = docId,
                        groupId = "global",
                        signature = signature,
                        merchant = doc.getString("merchant") ?: "",
                        sourceHint = null,
                        isUserConfirmed = true, // 글로벌 규칙은 여러 사용자가 확정
                        hitCount = voteCount,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: 0
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch global rule", e)
            null
        }
    }

    /**
     * 규칙 저장 또는 업데이트
     *
     * @param groupId 그룹 ID
     * @param signature 패턴 시그니처
     * @param merchant 확정된 사용처
     * @param sourceHint 패키지명 또는 발신자
     * @param isUserConfirmed 사용자가 직접 확정했는지 여부
     */
    suspend fun saveOrUpdate(
        groupId: String,
        signature: String,
        merchant: String,
        sourceHint: String? = null,
        isUserConfirmed: Boolean = false
    ) {
        val now = System.currentTimeMillis()

        if (dao.exists(signature)) {
            // 기존 규칙 업데이트
            dao.updateHit(
                signature = signature,
                merchant = merchant,
                isUserConfirmed = isUserConfirmed,
                updatedAt = now
            )
        } else {
            // 새 규칙 생성
            val newRule = LearnedMerchantRule(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                signature = signature,
                merchant = merchant,
                sourceHint = sourceHint,
                isUserConfirmed = isUserConfirmed,
                hitCount = 1,
                createdAt = now,
                updatedAt = now
            )
            dao.insert(newRule)
        }
    }

    /**
     * 사용자가 사용처를 수정했을 때 호출
     * 정책 2: 사용자 수정으로 학습하여 재발 방지
     * + 글로벌 공유: 다른 사용자도 혜택을 받음
     *
     * @param groupId 그룹 ID
     * @param originalText 원본 알림 텍스트
     * @param pkg 패키지명
     * @param confirmedMerchant 사용자가 확정한 사용처
     */
    suspend fun learnFromUserCorrection(
        groupId: String,
        originalText: String,
        pkg: String?,
        confirmedMerchant: String
    ) {
        val signature = LearnedMerchantRule.generateSignature(originalText, pkg)

        // 1. 로컬 저장
        saveOrUpdate(
            groupId = groupId,
            signature = signature,
            merchant = confirmedMerchant,
            sourceHint = pkg,
            isUserConfirmed = true
        )

        // 2. 글로벌 투표 (비동기, 실패해도 무시)
        voteGlobalRule(signature, confirmedMerchant)
    }

    /**
     * 글로벌 규칙에 투표 (Firebase)
     * 같은 signature + merchant 조합에 투표하면 voteCount 증가
     */
    private suspend fun voteGlobalRule(signature: String, merchant: String) {
        try {
            val docId = signature.hashCode().toString()
            val docRef = firestore.collection(GLOBAL_RULES_COLLECTION).document(docId)

            firestore.runTransaction { transaction ->
                val doc = transaction.get(docRef)

                if (doc.exists()) {
                    val existingMerchant = doc.getString("merchant") ?: ""
                    if (existingMerchant == merchant) {
                        // 같은 매핑이면 투표 증가
                        transaction.update(docRef, mapOf(
                            "voteCount" to FieldValue.increment(1),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                    } else {
                        // 다른 매핑이면 현재 투표 수 확인
                        val currentVotes = doc.getLong("voteCount") ?: 0
                        if (currentVotes < GLOBAL_MIN_VOTES) {
                            // 아직 확정 안 됐으면 새 매핑으로 교체
                            transaction.set(docRef, mapOf(
                                "signature" to signature,
                                "merchant" to merchant,
                                "voteCount" to 1,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            ))
                        } else {
                            // 확정된 규칙은 변경 안 함 (투표 수 유지)
                        }
                    }
                } else {
                    // 새 규칙 생성
                    transaction.set(docRef, mapOf(
                        "signature" to signature,
                        "merchant" to merchant,
                        "voteCount" to 1,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }.await()

            Log.d(TAG, "Global rule voted: $merchant")
        } catch (e: Exception) {
            // 글로벌 저장 실패해도 로컬은 저장됨
            Log.w(TAG, "Failed to vote global rule", e)
        }
    }

    /**
     * 자동 파싱 성공 시 학습 (사용자 확정 아님)
     */
    suspend fun learnFromAutoParsingSuccess(
        groupId: String,
        originalText: String,
        pkg: String?,
        parsedMerchant: String
    ) {
        val signature = LearnedMerchantRule.generateSignature(originalText, pkg)
        saveOrUpdate(
            groupId = groupId,
            signature = signature,
            merchant = parsedMerchant,
            sourceHint = pkg,
            isUserConfirmed = false
        )
    }

    /**
     * 그룹의 모든 학습 규칙 조회 (Flow)
     */
    fun getAllByGroupId(groupId: String): Flow<List<LearnedMerchantRule>> {
        return dao.getAllByGroupId(groupId)
    }

    /**
     * 특정 규칙 삭제
     */
    suspend fun deleteRule(ruleId: String) {
        dao.deleteById(ruleId)
    }

    /**
     * 그룹의 모든 학습 규칙 삭제
     */
    suspend fun deleteAllByGroupId(groupId: String) {
        dao.deleteAllByGroupId(groupId)
    }

    /**
     * 오래된 규칙 정리
     * 180일 이상 + hitCount < 3인 규칙 삭제
     */
    suspend fun purgeOldRules() {
        val threshold = System.currentTimeMillis() - (PURGE_THRESHOLD_DAYS * DAY_IN_MILLIS)
        dao.purgeOld(threshold)
    }

    /**
     * 그룹의 학습 규칙 수 조회
     */
    suspend fun getCountByGroupId(groupId: String): Int {
        return dao.countByGroupId(groupId)
    }
}
