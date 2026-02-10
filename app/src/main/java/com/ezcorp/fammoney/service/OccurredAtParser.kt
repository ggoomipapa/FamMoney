package com.ezcorp.fammoney.service

import com.ezcorp.fammoney.util.AppLogger
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OccurredAtParser"

/**
 * 알림 본문에서 거래 시각(occurredAt) 추출
 *
 * 지원 패턴:
 *   1. MM/dd HH:mm (예: 01/20 14:03)
 *   2. yyyy-MM-dd HH:mm (예: 2026-01-20 14:03)
 *   3. HH:mm만 있는 경우 (날짜는 receivedAt 사용)
 *   4. 상대 표현 (오늘, 어제)
 *
 * 안전 장치:
 *   - 숫자 경계 lookaround로 승인번호 오인 방지
 *   - receivedAt 대비 ±45일 범위 벗어나면 무효
 *   - "잔액" 근처 시간은 감점
 */
@Singleton
class OccurredAtParser @Inject constructor() {

    /**
     * 파싱 결과
     */
    data class ParseResult(
        val occurredAt: Long?,       // 추출된 거래 시각 (null이면 추출 실패)
        val confidence: Double,       // 신뢰도 (0.0 ~ 1.0)
        val source: TimeSource        // 시간 정보 출처
    )

    enum class TimeSource {
        FULL_DATETIME,    // yyyy-MM-dd HH:mm
        SHORT_DATETIME,   // MM/dd HH:mm
        TIME_ONLY,        // HH:mm (날짜는 receivedAt에서)
        RELATIVE,         // 오늘/어제
        NOT_FOUND         // 추출 실패
    }

    companion object {
        // 유효 범위: receivedAt 기준 ±45일
        private const val VALID_RANGE_DAYS = 45L
        private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

        // 전체 날짜+시간 패턴 (yyyy-MM-dd HH:mm 또는 yyyy.MM.dd HH:mm)
        private val FULL_DATETIME_PATTERN = Regex(
            """(?<!\d)(\d{4})[./\-](\d{1,2})[./\-](\d{1,2})\s+(\d{1,2}):(\d{2})(?!\d)"""
        )

        // 짧은 날짜+시간 패턴 (MM/dd HH:mm 또는 MM.dd HH:mm)
        private val SHORT_DATETIME_PATTERN = Regex(
            """(?<!\d)(\d{1,2})[./\-](\d{1,2})\s+(\d{1,2}):(\d{2})(?!\d)"""
        )

        // 시간만 있는 패턴 (HH:mm)
        private val TIME_ONLY_PATTERN = Regex(
            """(?<!\d)(\d{1,2}):(\d{2})(?!\d)"""
        )

        // 상대 시간 패턴
        private val RELATIVE_TODAY_PATTERN = Regex(
            """오늘\s*(\d{1,2}):(\d{2})"""
        )
        private val RELATIVE_YESTERDAY_PATTERN = Regex(
            """어제\s*(\d{1,2}):(\d{2})"""
        )

        // 시간 라벨 근접 패턴 (신뢰도 가점)
        private val TIME_LABEL_PATTERN = Regex(
            """(?:일시|시간|승인|결제|거래)\s*[:：]?\s*(?<!\d)(\d{1,2}):(\d{2})(?!\d)"""
        )

        // 잔액 근처 패턴 (신뢰도 감점)
        private val BALANCE_TIME_PATTERN = Regex(
            """잔액.{0,10}(\d{1,2}):(\d{2})"""
        )

        // 승인번호 패턴 (제외용)
        private val APPROVAL_NUMBER_PATTERN = Regex(
            """(?:승인번호|승인|인증)\s*[:：]?\s*\d+"""
        )
    }

    /**
     * 거래 시각 파싱
     *
     * @param text 알림 본문
     * @param receivedAt 알림 수신 시각 (기준 시간)
     * @return ParseResult
     */
    fun parse(text: String, receivedAt: Long): ParseResult {
        AppLogger.d(TAG, "parse 시작: receivedAt=$receivedAt, text=${text.take(100)}")

        // 1. 상대 시간 패턴 (오늘/어제) - 가장 명확
        parseRelativeTime(text, receivedAt)?.let {
            AppLogger.i(TAG, "parse 결과: source=${it.source}, occurredAt=${it.occurredAt}, confidence=${it.confidence}")
            return it
        }

        // 2. 전체 날짜+시간 패턴
        parseFullDatetime(text, receivedAt)?.let {
            AppLogger.i(TAG, "parse 결과: source=${it.source}, occurredAt=${it.occurredAt}, confidence=${it.confidence}")
            return it
        }

        // 3. 짧은 날짜+시간 패턴
        parseShortDatetime(text, receivedAt)?.let {
            AppLogger.i(TAG, "parse 결과: source=${it.source}, occurredAt=${it.occurredAt}, confidence=${it.confidence}")
            return it
        }

        // 4. 시간만 있는 패턴 (라벨 근접 확인)
        parseTimeOnly(text, receivedAt)?.let {
            AppLogger.i(TAG, "parse 결과: source=${it.source}, occurredAt=${it.occurredAt}, confidence=${it.confidence}")
            return it
        }

        // 추출 실패
        AppLogger.d(TAG, "parse 결과: NOT_FOUND, 시간 패턴 미감지")
        return ParseResult(
            occurredAt = null,
            confidence = 0.0,
            source = TimeSource.NOT_FOUND
        )
    }

    /**
     * 상대 시간 파싱 (오늘/어제)
     */
    private fun parseRelativeTime(text: String, receivedAt: Long): ParseResult? {
        // 오늘
        RELATIVE_TODAY_PATTERN.find(text)?.let { match ->
            AppLogger.d(TAG, "상대시간 패턴 감지 (오늘): matched=${match.value}")
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: return@let

            if (!isValidTime(hour, minute)) return@let

            val calendar = Calendar.getInstance().apply {
                timeInMillis = receivedAt
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return ParseResult(
                occurredAt = calendar.timeInMillis,
                confidence = 0.9,
                source = TimeSource.RELATIVE
            )
        }

        // 어제
        RELATIVE_YESTERDAY_PATTERN.find(text)?.let { match ->
            AppLogger.d(TAG, "상대시간 패턴 감지 (어제): matched=${match.value}")
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: return@let

            if (!isValidTime(hour, minute)) return@let

            val calendar = Calendar.getInstance().apply {
                timeInMillis = receivedAt
                add(Calendar.DAY_OF_MONTH, -1)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            return ParseResult(
                occurredAt = calendar.timeInMillis,
                confidence = 0.85,
                source = TimeSource.RELATIVE
            )
        }

        return null
    }

    /**
     * 전체 날짜+시간 파싱 (yyyy-MM-dd HH:mm)
     */
    private fun parseFullDatetime(text: String, receivedAt: Long): ParseResult? {
        FULL_DATETIME_PATTERN.find(text)?.let { match ->
            AppLogger.d(TAG, "전체 날짜+시간 패턴 감지: matched=${match.value}")
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull() ?: return@let
            val day = match.groupValues[3].toIntOrNull() ?: return@let
            val hour = match.groupValues[4].toIntOrNull() ?: return@let
            val minute = match.groupValues[5].toIntOrNull() ?: return@let

            if (!isValidDate(year, month, day) || !isValidTime(hour, minute)) return@let

            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val occurredAt = calendar.timeInMillis

            // 유효 범위 체크
            if (!isWithinValidRange(occurredAt, receivedAt)) return@let

            return ParseResult(
                occurredAt = occurredAt,
                confidence = 0.95,
                source = TimeSource.FULL_DATETIME
            )
        }

        return null
    }

    /**
     * 짧은 날짜+시간 파싱 (MM/dd HH:mm)
     */
    private fun parseShortDatetime(text: String, receivedAt: Long): ParseResult? {
        SHORT_DATETIME_PATTERN.find(text)?.let { match ->
            AppLogger.d(TAG, "짧은 날짜+시간 패턴 감지: matched=${match.value}")
            val month = match.groupValues[1].toIntOrNull() ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val hour = match.groupValues[3].toIntOrNull() ?: return@let
            val minute = match.groupValues[4].toIntOrNull() ?: return@let

            if (!isValidTime(hour, minute)) return@let
            if (month < 1 || month > 12 || day < 1 || day > 31) return@let

            // receivedAt의 연도 사용
            val receivedCalendar = Calendar.getInstance().apply {
                timeInMillis = receivedAt
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, receivedCalendar.get(Calendar.YEAR))
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            var occurredAt = calendar.timeInMillis

            // 미래 날짜인 경우 전년도로 조정
            if (occurredAt > receivedAt + DAY_IN_MILLIS) {
                calendar.add(Calendar.YEAR, -1)
                occurredAt = calendar.timeInMillis
            }

            // 유효 범위 체크
            if (!isWithinValidRange(occurredAt, receivedAt)) return@let

            return ParseResult(
                occurredAt = occurredAt,
                confidence = 0.85,
                source = TimeSource.SHORT_DATETIME
            )
        }

        return null
    }

    /**
     * 시간만 파싱 (HH:mm)
     */
    private fun parseTimeOnly(text: String, receivedAt: Long): ParseResult? {
        // 잔액 근처의 시간은 제외
        if (BALANCE_TIME_PATTERN.containsMatchIn(text)) {
            AppLogger.d(TAG, "잔액 근처 시간 패턴 감지, 스킵")
            return null
        }

        // 라벨 근처 시간 우선
        TIME_LABEL_PATTERN.find(text)?.let { match ->
            AppLogger.d(TAG, "시간 라벨 패턴 감지: matched=${match.value}")
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: return@let

            if (!isValidTime(hour, minute)) return@let

            val calendar = Calendar.getInstance().apply {
                timeInMillis = receivedAt
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 미래 시간이면 전날로 조정
            if (calendar.timeInMillis > receivedAt) {
                calendar.add(Calendar.DAY_OF_MONTH, -1)
            }

            return ParseResult(
                occurredAt = calendar.timeInMillis,
                confidence = 0.75,
                source = TimeSource.TIME_ONLY
            )
        }

        // 일반 시간 패턴
        val matches = TIME_ONLY_PATTERN.findAll(text).toList()
        AppLogger.d(TAG, "일반 시간 패턴 검색: ${matches.size}건 발견")

        // 여러 시간이 있으면 첫 번째 것 사용 (보통 거래 시간이 먼저 나옴)
        // 단, 승인번호 근처는 제외
        for (match in matches) {
            // 승인번호 근처인지 확인
            val startIndex = maxOf(0, match.range.first - 20)
            val context = text.substring(startIndex, match.range.last + 1)
            if (APPROVAL_NUMBER_PATTERN.containsMatchIn(context)) {
                continue
            }

            val hour = match.groupValues[1].toIntOrNull() ?: continue
            val minute = match.groupValues[2].toIntOrNull() ?: continue

            if (!isValidTime(hour, minute)) continue

            val calendar = Calendar.getInstance().apply {
                timeInMillis = receivedAt
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 미래 시간이면 전날로 조정
            if (calendar.timeInMillis > receivedAt) {
                calendar.add(Calendar.DAY_OF_MONTH, -1)
            }

            return ParseResult(
                occurredAt = calendar.timeInMillis,
                confidence = 0.6,
                source = TimeSource.TIME_ONLY
            )
        }

        return null
    }

    /**
     * 유효한 시간인지 확인
     */
    private fun isValidTime(hour: Int, minute: Int): Boolean {
        return hour in 0..23 && minute in 0..59
    }

    /**
     * 유효한 날짜인지 확인
     */
    private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
        if (year < 2000 || year > 2100) return false
        if (month < 1 || month > 12) return false
        if (day < 1 || day > 31) return false
        return true
    }

    /**
     * 유효 범위 내인지 확인 (±45일)
     */
    private fun isWithinValidRange(occurredAt: Long, receivedAt: Long): Boolean {
        val diff = kotlin.math.abs(occurredAt - receivedAt)
        return diff <= VALID_RANGE_DAYS * DAY_IN_MILLIS
    }
}
