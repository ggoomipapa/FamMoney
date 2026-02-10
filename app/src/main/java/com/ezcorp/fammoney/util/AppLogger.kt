package com.ezcorp.fammoney.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 앱 내부 로그 관리자 (LogManager)
 *
 * 핵심: 앱의 모든 동작을 시간순으로 추적할 수 있도록 상세하게 기록
 * 문제 발생 시 로그만 보면 어디서 뭐가 잘못됐는지 바로 알 수 있어야 함
 *
 * - 링버퍼로 최신 1500줄만 유지
 * - 앱 내부 로그만 저장 (시스템 로그 제외)
 * - 텍스트 파일로 저장 가능
 * - LogListener 인터페이스로 실시간 업데이트 지원
 *
 * 로그 형식: [14:30:15.123] [D] Tag: Message
 */
object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LINES = 1500
    private val idCounter = AtomicLong(0)

    // 스레드 안전 리스트
    private val logBuffer = CopyOnWriteArrayList<LogEntry>()

    // 로그 변경 리스너
    private val listeners = CopyOnWriteArrayList<LogListener>()

    /**
     * 로그 변경 리스너 인터페이스
     */
    interface LogListener {
        fun onLogAdded(entry: LogEntry)
        fun onLogsCleared()
    }

    /**
     * 로그 엔트리 데이터 클래스
     */
    data class LogEntry(
        val id: Long = idCounter.getAndIncrement(),
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        /**
         * 화면 표시용 형식: [14:30:15.123] [D] Tag: Message
         */
        fun format(): String {
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            val time = dateFormat.format(Date(timestamp))
            return "[$time] [$level] $tag: $message"
        }

        /**
         * 파일 저장용 형식: [2024-01-15 14:30:15.123] [D] Tag: Message
         */
        fun formatForFile(): String {
            val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val time = fullDateFormat.format(Date(timestamp))
            return "[$time] [$level] $tag: $message"
        }
    }

    /**
     * 로그 추가
     */
    private fun addLog(level: String, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )

        logBuffer.add(entry)

        // 최대 줄 수 초과 시 오래된 로그 삭제
        while (logBuffer.size > MAX_LINES) {
            logBuffer.removeAt(0)
        }

        // 리스너에게 알림
        listeners.forEach { it.onLogAdded(entry) }
    }

    // ==================== 로그 레벨별 메서드 ====================

    /**
     * Verbose 로그 - 상세 디버깅 정보
     */
    fun v(tag: String, message: String) {
        addLog("V", tag, message)
        if (DebugConfig.isDebugBuild) {
            Log.v(tag, message)
        }
    }

    /**
     * Debug 로그 - 디버깅 정보
     * 예: [D] VoiceFlow: Starting flow: type=TALKTALK, source=widget
     */
    fun d(tag: String, message: String) {
        addLog("D", tag, message)
        if (DebugConfig.isDebugBuild) {
            Log.d(tag, message)
        }
    }

    /**
     * Info 로그 - 일반 정보성 메시지
     * 예: [I] API: Sending message to userId=abc123, content=안녕...
     */
    fun i(tag: String, message: String) {
        addLog("I", tag, message)
        if (DebugConfig.isDebugBuild) {
            Log.i(tag, message)
        }
    }

    /**
     * Warning 로그 - 경고 (정상 동작하지만 주의 필요)
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        addLog("W", tag, fullMessage)
        if (DebugConfig.isDebugBuild) {
            Log.w(tag, message, throwable)
        }
    }

    /**
     * Error 로그 - 에러 (문제 발생)
     * 예: [E] API: Send failed: 사용자를 찾을 수 없습니다
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        addLog("E", tag, fullMessage)
        if (DebugConfig.isDebugBuild) {
            Log.e(tag, message, throwable)
        }
    }

    // ==================== API/네트워크 로깅 헬퍼 ====================

    /**
     * API 호출 시작 로그
     */
    fun apiStart(tag: String, method: String, params: String = "") {
        val paramStr = if (params.isNotEmpty()) " params=$params" else ""
        d(tag, "API 호출 시작: $method$paramStr")
    }

    /**
     * API 호출 성공 로그
     */
    fun apiSuccess(tag: String, method: String, result: String = "") {
        val resultStr = if (result.isNotEmpty()) " result=$result" else ""
        i(tag, "API 호출 성공: $method$resultStr")
    }

    /**
     * API 호출 실패 로그
     */
    fun apiError(tag: String, method: String, error: String, throwable: Throwable? = null) {
        e(tag, "API 호출 실패: $method - $error", throwable)
    }

    // ==================== 사용자 액션 로깅 헬퍼 ====================

    /**
     * 사용자 액션 로그 (버튼 클릭, 화면 전환 등)
     */
    fun userAction(tag: String, action: String, details: String = "") {
        val detailStr = if (details.isNotEmpty()) " ($details)" else ""
        d(tag, "사용자 액션: $action$detailStr")
    }

    /**
     * 화면 진입 로그
     */
    fun screenEnter(tag: String, screenName: String) {
        i(tag, "========== $screenName 진입 ==========")
    }

    /**
     * 화면 종료 로그
     */
    fun screenExit(tag: String, screenName: String) {
        d(tag, "========== $screenName 종료 ==========")
    }

    // ==================== 상태 변경 로깅 헬퍼 ====================

    /**
     * 상태 변경 로그
     */
    fun stateChange(tag: String, stateName: String, oldValue: Any?, newValue: Any?) {
        d(tag, "상태 변경: $stateName = $oldValue → $newValue")
    }

    /**
     * 데이터 로드 완료 로그
     */
    fun dataLoaded(tag: String, dataType: String, count: Int, details: String = "") {
        val detailStr = if (details.isNotEmpty()) " ($details)" else ""
        i(tag, "데이터 로드 완료: $dataType ${count}건$detailStr")
    }

    // ==================== 로그 조회/관리 ====================

    /**
     * 현재 로그 목록 가져오기
     */
    fun getLogs(): List<LogEntry> = logBuffer.toList()

    /**
     * 로그 개수
     */
    fun getLogCount(): Int = logBuffer.size

    /**
     * 로그 초기화
     */
    fun clear() {
        logBuffer.clear()
        listeners.forEach { it.onLogsCleared() }
        d(TAG, "로그 버퍼 초기화됨")
    }

    /**
     * 로그를 텍스트로 변환 (전체 내보내기)
     */
    fun getLogsAsText(): String {
        return logBuffer.joinToString("\n") { it.formatForFile() }
    }

    /**
     * 로그를 파일로 저장
     * 저장 위치: 내장저장공간/만든어플/SelectMoney 로그/
     * @param clearAfterSave true면 저장 후 로그 초기화
     * @return 저장된 파일 경로, 실패 시 null
     */
    fun saveToFile(context: Context, clearAfterSave: Boolean = false): File? {
        return try {
            // 앱 캐시 폴더에 로그 저장 (권한 불필요, FileProvider 공유 가능)
            val logsDir = File(context.cacheDir, "logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            // 파일명: selectmoney_log_yyyyMMdd_HHmmss.txt
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "selectmoney_log_${dateFormat.format(Date())}.txt"
            val file = File(logsDir, fileName)

            // 헤더 추가
            val header = buildString {
                appendLine("========================================")
                appendLine("SelectMoney Debug Log")
                appendLine("========================================")
                appendLine("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                appendLine("Total lines: ${logBuffer.size}")
                appendLine("저장 위치: ${file.absolutePath}")
                appendLine("========================================")
                appendLine()
            }

            file.writeText(header + getLogsAsText())

            i(TAG, "로그 파일 저장 완료: ${file.absolutePath}")

            if (clearAfterSave) {
                clear()
            }

            file
        } catch (e: Exception) {
            e(TAG, "로그 파일 저장 실패", e)
            null
        }
    }

    // ==================== 리스너 관리 ====================

    /**
     * 로그 변경 리스너 등록
     */
    fun addListener(listener: LogListener) {
        listeners.add(listener)
    }

    /**
     * 로그 변경 리스너 해제
     */
    fun removeListener(listener: LogListener) {
        listeners.remove(listener)
    }

    /**
     * 간단한 람다 리스너 등록 (호환성 유지)
     */
    fun addSimpleListener(onChanged: () -> Unit): LogListener {
        val listener = object : LogListener {
            override fun onLogAdded(entry: LogEntry) = onChanged()
            override fun onLogsCleared() = onChanged()
        }
        addListener(listener)
        return listener
    }

    // ==================== 필터링/검색 ====================

    /**
     * 레벨별 필터링
     */
    fun getLogsByLevel(level: String): List<LogEntry> {
        return logBuffer.filter { it.level == level }
    }

    /**
     * 여러 레벨 필터링
     */
    fun getLogsByLevels(levels: Set<String>): List<LogEntry> {
        return logBuffer.filter { it.level in levels }
    }

    /**
     * 태그별 필터링
     */
    fun getLogsByTag(tag: String): List<LogEntry> {
        return logBuffer.filter { it.tag.contains(tag, ignoreCase = true) }
    }

    /**
     * 검색 (태그 + 메시지)
     */
    fun searchLogs(query: String): List<LogEntry> {
        if (query.isBlank()) return logBuffer.toList()
        return logBuffer.filter {
            it.tag.contains(query, ignoreCase = true) ||
            it.message.contains(query, ignoreCase = true)
        }
    }

    /**
     * 복합 필터링 (레벨 + 검색어)
     */
    fun filterLogs(levels: Set<String>? = null, query: String? = null): List<LogEntry> {
        var result = logBuffer.toList()

        if (levels != null && levels.isNotEmpty()) {
            result = result.filter { it.level in levels }
        }

        if (!query.isNullOrBlank()) {
            result = result.filter {
                it.tag.contains(query, ignoreCase = true) ||
                it.message.contains(query, ignoreCase = true)
            }
        }

        return result
    }
}
