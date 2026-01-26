package com.ezcorp.fammoney.service

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 알림에서 원시 메시지 추출
 *
 * 지원 알림 스타일:
 *   - MessagingStyle (EXTRA_MESSAGES)
 *   - InboxStyle (EXTRA_TEXT_LINES)
 *   - BigTextStyle (EXTRA_BIG_TEXT)
 *   - 일반 알림 (EXTRA_TEXT)
 *
 * 그룹/요약 알림 처리:
 *   - FLAG_GROUP_SUMMARY 감지
 *   - "외 N건", "N개" 집계 표현 감지
 *   - 멀티 거래 분해 (빈줄 기준, 금액 반복 기준)
 */
@Singleton
class NotificationExtractor @Inject constructor() {

    /**
     * 원시 메시지 데이터
     */
    data class RawMessage(
        val source: SourceType,
        val pkg: String?,
        val sender: String?,
        val body: String,
        val receivedAt: Long
    )

    enum class SourceType {
        NOTIFICATION,
        SMS
    }

    companion object {
        // 요약 알림 감지 패턴
        private val SUMMARY_PATTERNS = listOf(
            Regex("""외\s*(\d+)\s*건"""),          // "외 3건"
            Regex("""(\d+)\s*개"""),               // "5개"
            Regex("""(\d+)\s*건의?\s*알림"""),     // "3건의 알림"
            Regex("""(\d+)\s*new\s*messages?""", RegexOption.IGNORE_CASE)
        )

        // 금액 패턴 (멀티 거래 분해용)
        private val AMOUNT_PATTERN = Regex("""(\d{1,3}(?:,\d{3})+|\d+)\s*원""")

        // 거래 트리거 키워드
        private val TRANSACTION_TRIGGERS = listOf(
            "승인", "결제", "출금", "입금", "이체", "송금", "취소", "환불"
        )

        // 최소 메시지 길이 (과분해 방지)
        private const val MIN_MESSAGE_LENGTH = 8
    }

    /**
     * StatusBarNotification에서 RawMessage 목록 추출
     *
     * @param sbn 알림 객체
     * @return 추출된 RawMessage 목록 (멀티 거래인 경우 여러 개)
     */
    fun extractRawMessagesFromSbn(sbn: StatusBarNotification): List<RawMessage> {
        val notification = sbn.notification
        val extras = notification.extras
        val packageName = sbn.packageName
        val receivedAt = sbn.postTime

        // 그룹 요약 알림인 경우 스킵 (개별 알림만 처리)
        if (isGroupSummary(notification, extras)) {
            return emptyList()
        }

        // 원시 텍스트 추출 (우선순위 순)
        val rawTexts = extractRawTexts(extras)

        if (rawTexts.isEmpty()) {
            return emptyList()
        }

        // 멀티 거래 분해
        val decomposedMessages = mutableListOf<RawMessage>()

        for (rawText in rawTexts) {
            val decomposed = decomposeMultiTransaction(rawText)
            for (text in decomposed) {
                decomposedMessages.add(
                    RawMessage(
                        source = SourceType.NOTIFICATION,
                        pkg = packageName,
                        sender = null,
                        body = text,
                        receivedAt = receivedAt
                    )
                )
            }
        }

        return decomposedMessages
    }

    /**
     * 그룹 요약 알림 여부 확인
     */
    private fun isGroupSummary(notification: Notification, extras: Bundle): Boolean {
        // FLAG_GROUP_SUMMARY 확인
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return true
        }

        // 타이틀에서 요약 표현 확인
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        for (pattern in SUMMARY_PATTERNS) {
            if (pattern.containsMatchIn(title)) {
                return true
            }
        }

        return false
    }

    /**
     * 알림 extras에서 원시 텍스트 추출 (우선순위 순)
     */
    @Suppress("DEPRECATION")
    private fun extractRawTexts(extras: Bundle): List<String> {
        val results = mutableListOf<String>()

        // 1. EXTRA_MESSAGES (MessagingStyle) - 가장 구조화된 형식
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (messages != null && messages.isNotEmpty()) {
                for (msg in messages) {
                    val bundle = msg as? Bundle
                    val text = bundle?.getCharSequence("text")?.toString()
                    if (!text.isNullOrBlank()) {
                        results.add(text)
                    }
                }
                if (results.isNotEmpty()) {
                    return results
                }
            }
        }

        // 2. EXTRA_TEXT_LINES (InboxStyle)
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (textLines != null && textLines.isNotEmpty()) {
            for (line in textLines) {
                val text = line?.toString()
                if (!text.isNullOrBlank()) {
                    results.add(text)
                }
            }
            if (results.isNotEmpty()) {
                return results
            }
        }

        // 3. EXTRA_BIG_TEXT (BigTextStyle)
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) {
            results.add(bigText)
            return results
        }

        // 4. EXTRA_TEXT (일반)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            results.add(text)
            return results
        }

        // 5. EXTRA_TITLE (최후 수단)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!title.isNullOrBlank()) {
            results.add(title)
            return results
        }

        return results
    }

    /**
     * 멀티 거래 텍스트 분해
     *
     * 분해 기준:
     *   1. 빈줄 기준 (\n\s*\n)
     *   2. 거래 트리거 키워드 + 금액 반복
     *
     * 과분해 방지: 결과가 MIN_MESSAGE_LENGTH 미만이면 원문 유지
     */
    private fun decomposeMultiTransaction(rawText: String): List<String> {
        // 1. 빈줄 기준 분리 시도
        val byBlankLine = rawText.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (byBlankLine.size > 1) {
            // 각 파트에 금액이 포함되어 있는지 확인
            val validParts = byBlankLine.filter { part ->
                AMOUNT_PATTERN.containsMatchIn(part) && part.length >= MIN_MESSAGE_LENGTH
            }

            if (validParts.size > 1) {
                return validParts
            }
        }

        // 2. 금액 반복 패턴 기준 분리 시도
        val amountMatches = AMOUNT_PATTERN.findAll(rawText).toList()
        if (amountMatches.size > 1) {
            // 트리거 키워드도 반복되는지 확인
            val triggerCount = TRANSACTION_TRIGGERS.sumOf { trigger ->
                Regex(Regex.escape(trigger)).findAll(rawText).count()
            }

            // 금액 수와 트리거 수가 비슷하면 멀티 거래로 판단
            if (triggerCount >= amountMatches.size) {
                val parts = splitByAmountPattern(rawText, amountMatches)
                val validParts = parts.filter { it.length >= MIN_MESSAGE_LENGTH }
                if (validParts.size > 1) {
                    return validParts
                }
            }
        }

        // 분해 실패 또는 단일 거래 - 원문 반환
        return listOf(rawText)
    }

    /**
     * 금액 패턴 기준으로 텍스트 분리
     */
    private fun splitByAmountPattern(text: String, amountMatches: List<MatchResult>): List<String> {
        if (amountMatches.size < 2) {
            return listOf(text)
        }

        val parts = mutableListOf<String>()
        var lastEnd = 0

        for (i in 1 until amountMatches.size) {
            val currentMatch = amountMatches[i]

            // 현재 금액 전까지의 텍스트에서 트리거 키워드 위치 찾기
            val searchStart = amountMatches[i - 1].range.last + 1
            val searchEnd = currentMatch.range.first
            val searchArea = text.substring(searchStart, searchEnd)

            // 트리거 키워드 중 가장 앞에 있는 것 찾기
            var splitPoint = -1
            for (trigger in TRANSACTION_TRIGGERS) {
                val idx = searchArea.indexOf(trigger)
                if (idx >= 0 && (splitPoint < 0 || idx < splitPoint)) {
                    splitPoint = idx
                }
            }

            if (splitPoint >= 0) {
                val absoluteSplitPoint = searchStart + splitPoint
                parts.add(text.substring(lastEnd, absoluteSplitPoint).trim())
                lastEnd = absoluteSplitPoint
            }
        }

        // 마지막 파트 추가
        if (lastEnd < text.length) {
            parts.add(text.substring(lastEnd).trim())
        }

        return parts.filter { it.isNotBlank() }
    }

    /**
     * SMS에서 RawMessage 생성
     *
     * @param sender 발신자 번호/이름
     * @param body SMS 본문
     * @param receivedAt 수신 시각
     */
    fun createRawMessageFromSms(
        sender: String,
        body: String,
        receivedAt: Long
    ): RawMessage {
        return RawMessage(
            source = SourceType.SMS,
            pkg = null,
            sender = sender,
            body = body,
            receivedAt = receivedAt
        )
    }

    /**
     * 거래 관련 알림인지 확인
     */
    fun isTransactionNotification(text: String): Boolean {
        // 금액 패턴 존재
        if (!AMOUNT_PATTERN.containsMatchIn(text)) {
            return false
        }

        // 트리거 키워드 존재
        return TRANSACTION_TRIGGERS.any { trigger ->
            text.contains(trigger)
        }
    }

    /**
     * rawHash 생성 (중복 제거용)
     */
    fun generateRawHash(body: String, receivedAt: Long): String {
        // 금액과 숫자를 제거하고 패턴만 추출하여 해시 생성
        val normalized = body
            .replace(AMOUNT_PATTERN, " ")
            .replace(Regex("""\d+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()

        // 시간 버킷 (60초 단위)
        val timeBucket = receivedAt / 60000

        return "$normalized|$timeBucket".hashCode().toString(16)
    }
}
