package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.data.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS + Notification 병합 엔진
 *
 * 동일 거래가 SMS와 알림 양쪽으로 올 수 있음.
 * 중복을 제거하고, 더 정확한 정보를 채택.
 *
 * Merge Key 생성:
 *   - amount + isCancel + 시간 버킷 + channelHint
 *
 * 병합 로직:
 *   1. rawHash로 Observation 중복 제거
 *   2. 느슨한 매칭: amount + isCancel + 시간 ±180초
 *   3. 후보 중 confidence 높은 쪽에서 merchant 채택
 */
@Singleton
class MergeEngine @Inject constructor(
    private val notificationExtractor: NotificationExtractor
) {

    /**
     * 관측 데이터 (SMS 또는 알림에서 추출된 원시 데이터)
     */
    data class Observation(
        val id: String,                           // UUID
        val source: NotificationExtractor.SourceType,
        val pkg: String?,                         // 패키지명 (알림)
        val sender: String?,                      // 발신자 (SMS)
        val body: String,                         // 원본 텍스트
        val receivedAt: Long,                     // 수신 시각

        // 파싱 결과
        val amount: Long?,                        // 금액
        val isCancel: Boolean = false,            // 취소 여부
        val merchant: String?,                    // 추출된 사용처
        val confidence: Double,                   // 신뢰도
        val signature: String,                    // 학습용 시그니처
        val rawHash: String                       // 중복 제거용 해시
    )

    /**
     * 병합된 거래
     */
    data class MergedTransaction(
        val amount: Long,
        val type: TransactionType,
        val merchant: String?,
        val confidence: Double,
        val observationIds: List<String>,         // 연결된 Observation ID들
        val mergeKey: String,
        val chosenSource: NotificationExtractor.SourceType,
        val occurredAt: Long                      // 거래 시각
    )

    companion object {
        // 시간 윈도우: 180초 (3분)
        private const val TIME_WINDOW_SEC = 180L

        // 시간 버킷 크기 (초)
        private const val BUCKET_SIZE_SEC = 60L
    }

    /**
     * Merge Key 생성
     *
     * @param amount 금액
     * @param isCancel 취소 여부
     * @param approxTime 대략적 시간 (밀리초)
     * @param channelHint 채널 힌트 (패키지명 또는 발신자)
     * @return merge key 문자열
     */
    fun generateMergeKey(
        amount: Long,
        isCancel: Boolean,
        approxTime: Long,
        channelHint: String?
    ): String {
        val bucket = (approxTime / 1000L) / BUCKET_SIZE_SEC
        val hint = channelHint?.lowercase()?.take(64) ?: "unknown"
        return "a=$amount|c=$isCancel|b=$bucket|h=$hint"
    }

    /**
     * Observation 목록에서 중복 제거
     *
     * @param observations 원시 Observation 목록
     * @return 중복 제거된 목록
     */
    fun deduplicateByRawHash(observations: List<Observation>): List<Observation> {
        val seen = mutableSetOf<String>()
        return observations.filter { obs ->
            if (seen.contains(obs.rawHash)) {
                false
            } else {
                seen.add(obs.rawHash)
                true
            }
        }
    }

    /**
     * 느슨한 매칭으로 병합 후보 찾기
     *
     * @param observations 중복 제거된 Observation 목록
     * @return 병합 그룹 (같은 거래로 판단된 Observation들)
     */
    fun findMergeGroups(observations: List<Observation>): List<List<Observation>> {
        if (observations.isEmpty()) return emptyList()

        val used = mutableSetOf<String>()
        val groups = mutableListOf<List<Observation>>()

        for (obs in observations) {
            if (used.contains(obs.id)) continue

            // 같은 거래로 보이는 Observation들 찾기
            val group = observations.filter { other ->
                !used.contains(other.id) && isLooseMatch(obs, other)
            }

            if (group.isNotEmpty()) {
                groups.add(group)
                group.forEach { used.add(it.id) }
            }
        }

        return groups
    }

    /**
     * 느슨한 매칭 조건 확인
     *
     * 조건:
     *   - 금액 동일
     *   - 취소 여부 동일
     *   - 시간 차이 ±180초 이내
     */
    private fun isLooseMatch(a: Observation, b: Observation): Boolean {
        // 금액이 둘 다 있어야 비교 가능
        if (a.amount == null || b.amount == null) return false

        // 금액 동일
        if (a.amount != b.amount) return false

        // 취소 여부 동일
        if (a.isCancel != b.isCancel) return false

        // 시간 차이 확인
        val timeDiff = kotlin.math.abs(a.receivedAt - b.receivedAt)
        if (timeDiff > TIME_WINDOW_SEC * 1000) return false

        return true
    }

    /**
     * 병합 그룹에서 최적의 정보 선택하여 MergedTransaction 생성
     *
     * @param group 같은 거래로 판단된 Observation 그룹
     * @return 병합된 거래
     */
    fun mergeGroup(group: List<Observation>): MergedTransaction? {
        if (group.isEmpty()) return null

        // 금액이 있는 것만 필터
        val withAmount = group.filter { it.amount != null }
        if (withAmount.isEmpty()) return null

        // confidence 높은 순으로 정렬
        val sorted = withAmount.sortedByDescending { it.confidence }
        val best = sorted.first()

        // 거래 유형 결정
        val type = if (best.isCancel) TransactionType.INCOME else TransactionType.EXPENSE

        // merchant는 confidence 높은 것에서 가져오되, null이면 다음 후보에서
        val merchant = sorted.firstOrNull { !it.merchant.isNullOrBlank() }?.merchant

        // 전체 confidence는 가장 높은 것
        val confidence = best.confidence

        // 발생 시각은 가장 이른 것
        val occurredAt = group.minOf { it.receivedAt }

        // merge key
        val mergeKey = generateMergeKey(
            amount = best.amount!!,
            isCancel = best.isCancel,
            approxTime = occurredAt,
            channelHint = best.pkg ?: best.sender
        )

        return MergedTransaction(
            amount = best.amount,
            type = type,
            merchant = merchant,
            confidence = confidence,
            observationIds = group.map { it.id },
            mergeKey = mergeKey,
            chosenSource = best.source,
            occurredAt = occurredAt
        )
    }

    /**
     * 전체 병합 프로세스 실행
     *
     * @param observations 원시 Observation 목록 (SMS + 알림 혼합)
     * @return 병합된 거래 목록
     */
    fun merge(observations: List<Observation>): List<MergedTransaction> {
        // 1. rawHash로 중복 제거
        val deduplicated = deduplicateByRawHash(observations)

        // 2. 느슨한 매칭으로 병합 그룹 찾기
        val groups = findMergeGroups(deduplicated)

        // 3. 각 그룹을 MergedTransaction으로 변환
        return groups.mapNotNull { group -> mergeGroup(group) }
    }

    /**
     * 기존 Transaction과 새 Observation 매칭 시도
     *
     * @param existingMergeKey 기존 거래의 merge key
     * @param newObservation 새로 들어온 Observation
     * @return 매칭 여부
     */
    fun matchesExisting(existingMergeKey: String, newObservation: Observation): Boolean {
        if (newObservation.amount == null) return false

        // merge key 파싱
        val parts = existingMergeKey.split("|").associate { part ->
            val (key, value) = part.split("=", limit = 2)
            key to value
        }

        val existingAmount = parts["a"]?.toLongOrNull() ?: return false
        val existingIsCancel = parts["c"]?.toBooleanStrictOrNull() ?: return false
        val existingBucket = parts["b"]?.toLongOrNull() ?: return false

        // 금액 비교
        if (existingAmount != newObservation.amount) return false

        // 취소 여부 비교
        if (existingIsCancel != newObservation.isCancel) return false

        // 시간 버킷 비교 (±3 버킷 = ±3분)
        val newBucket = (newObservation.receivedAt / 1000L) / BUCKET_SIZE_SEC
        val bucketDiff = kotlin.math.abs(existingBucket - newBucket)
        if (bucketDiff > 3) return false

        return true
    }

    /**
     * Observation에서 rawHash 생성
     */
    fun generateRawHash(body: String, receivedAt: Long): String {
        return notificationExtractor.generateRawHash(body, receivedAt)
    }

    /**
     * 취소 거래 여부 판단
     */
    fun isCancel(text: String): Boolean {
        val cancelKeywords = listOf(
            "취소", "CANCEL", "CANCELLED", "환불", "REFUND", "출금취소"
        )
        return cancelKeywords.any { text.contains(it, ignoreCase = true) }
    }
}
