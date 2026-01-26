package com.ezcorp.fammoney.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ezcorp.fammoney.data.model.LearnedMerchantRule
import kotlinx.coroutines.flow.Flow

/**
 * 학습된 사용처 규칙 DAO
 *
 * 정책 3 준수: 우선순위 기반 조회
 *   - User-confirmed > Signature-based > Heuristic
 */
@Dao
interface LearnedMerchantRuleDao {

    /**
     * Signature로 규칙 조회
     * 가장 최신 업데이트된 것 반환
     */
    @Query("""
        SELECT * FROM learned_merchant_rules
        WHERE signature = :signature
        ORDER BY isUserConfirmed DESC, updatedAt DESC
        LIMIT 1
    """)
    suspend fun findBySignature(signature: String): LearnedMerchantRule?

    /**
     * 그룹의 모든 규칙 조회
     */
    @Query("SELECT * FROM learned_merchant_rules WHERE groupId = :groupId ORDER BY updatedAt DESC")
    fun getAllByGroupId(groupId: String): Flow<List<LearnedMerchantRule>>

    /**
     * 새 규칙 삽입
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: LearnedMerchantRule)

    /**
     * 기존 규칙 업데이트 (hitCount 증가, merchant 갱신)
     */
    @Query("""
        UPDATE learned_merchant_rules
        SET merchant = :merchant,
            hitCount = hitCount + 1,
            isUserConfirmed = :isUserConfirmed,
            updatedAt = :updatedAt
        WHERE signature = :signature
    """)
    suspend fun updateHit(
        signature: String,
        merchant: String,
        isUserConfirmed: Boolean,
        updatedAt: Long
    )

    /**
     * 오래된 규칙 정리 (180일 이상 + hitCount < 3)
     */
    @Query("""
        DELETE FROM learned_merchant_rules
        WHERE updatedAt < :threshold AND hitCount < 3
    """)
    suspend fun purgeOld(threshold: Long)

    /**
     * 특정 규칙 삭제
     */
    @Query("DELETE FROM learned_merchant_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 그룹의 모든 규칙 삭제
     */
    @Query("DELETE FROM learned_merchant_rules WHERE groupId = :groupId")
    suspend fun deleteAllByGroupId(groupId: String)

    /**
     * 규칙 존재 여부 확인
     */
    @Query("SELECT COUNT(*) > 0 FROM learned_merchant_rules WHERE signature = :signature")
    suspend fun exists(signature: String): Boolean

    /**
     * 전체 규칙 수 조회 (통계용)
     */
    @Query("SELECT COUNT(*) FROM learned_merchant_rules WHERE groupId = :groupId")
    suspend fun countByGroupId(groupId: String): Int
}
