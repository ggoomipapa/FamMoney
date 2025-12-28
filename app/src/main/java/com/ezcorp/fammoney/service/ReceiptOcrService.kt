package com.ezcorp.fammoney.service

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "ReceiptOcrService"

/**
 * 영수증 항목 파싱 결과
 */
data class ParsedReceiptItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Long,
    val totalPrice: Long,
    val rawText: String
)

/**
 * OCR 결과
 */
data class OcrResult(
    val rawText: String,
    val items: List<ParsedReceiptItem>,
    val totalAmount: Long)

@Singleton
class ReceiptOcrService @Inject constructor() {

    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    /**
     * 비트맵 이미지에서 텍스트 인식
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        return processImage(inputImage)
    }

    /**
     * URI에서 텍스트 인식
     */
    suspend fun recognizeText(uri: Uri, context: android.content.Context): OcrResult {
        val inputImage = InputImage.fromFilePath(context, uri)
        return processImage(inputImage)
    }

    private suspend fun processImage(inputImage: InputImage): OcrResult {
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val rawText = visionText.text
                    Log.d(TAG, "OCR Raw Text:\n$rawText")

                    val items = parseReceiptItems(rawText)
                    val totalAmount = extractTotalAmount(rawText)

                    continuation.resume(OcrResult(rawText, items, totalAmount ?: 0L))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed", e)
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * 영수증 텍스트에서 항목 파싱
     */
    private fun parseReceiptItems(text: String): List<ParsedReceiptItem> {
        val items = mutableListOf<ParsedReceiptItem>()
        val lines = text.split("\n")

        // 가격 패턴: 숫자,숫자 또는 숫자원 형태
        val pricePattern = Regex("""(\d{1,3}(?:,\d{3})*|\d+)\s*(?:원)?$""")
        // 수량 패턴: 숫자개, 숫자EA, x숫자 등
        val quantityPattern = Regex("""(\d+)\s*(?:개|EA|ea|x|X|@)""")

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue

            // 헤더, 푸터, 합계 등 제외
            if (shouldSkipLine(trimmedLine)) continue

            // 가격이 포함된 줄인지 확인
            val priceMatch = pricePattern.find(trimmedLine)
            if (priceMatch != null) {
                val priceStr = priceMatch.groupValues[1].replace(",", "")
                val price = priceStr.toLongOrNull() ?: continue

                // 너무 작은 금액(100원 미만)이나 너무 큰 금액(천만원 이상)은 제외
                if (price < 100 || price > 10_000_000) continue

                // 항목명 추출 (가격 앞 부분)
                var itemName = trimmedLine.substring(0, priceMatch.range.first).trim()

                // 수량 추출
                var quantity = 1
                val quantityMatch = quantityPattern.find(itemName)
                if (quantityMatch != null) {
                    quantity = quantityMatch.groupValues[1].toIntOrNull() ?: 1
                    // 수량 부분 제거
                itemName = itemName.replace(quantityMatch.value, "").trim()
                }

                // 항목명 정리
                itemName = cleanItemName(itemName)

                // 유효한 항목명인지 확인
                if (itemName.length >= 2) {
                    items.add(
                        ParsedReceiptItem(
                            name = itemName,
                            quantity = quantity,
                            unitPrice = if (quantity > 1) price / quantity else price,
                            totalPrice = price,
                            rawText = trimmedLine
                        )
                    )
                }
            }
        }

        return items
    }

    /**
     * 건너뛸 줄인지 확인 (합계, 헤더 등)
     */
    private fun shouldSkipLine(line: String): Boolean {
        val skipKeywords = listOf(
            "합계", "총합", "결제", "카드", "현금", "거스름돈", "영수증",
            "사업자", "상호", "주소", "TEL", "FAX", "부가세", "과세",
            "면세", "사업자", "할인", "쿠폰", "적립", "POS", "단말기",
            "감사", "방문", "이용", "수량", "주문", "번호", "거래",
            "일시", "시간", "전자", "TABLE", "테이블"
        )
        return skipKeywords.any { line.contains(it, ignoreCase = true) }
    }

    /**
     * 항목명 정리
     */
    private fun cleanItemName(name: String): String {
        // 특수문자 제거
        var cleaned = name.replace(Regex("""[*#@\[\](){}]"""), "")
        // 숫자로만 시작하는 부분 제거 (상품코드 등)
        cleaned = cleaned.replace(Regex("""^\d+\s*"""), "")
        // 연속 공백 제거
        cleaned = cleaned.replace(Regex("""\s+"""), " ")
        return cleaned.trim()
    }

    /**
     * 총 금액 추출
     */
    private fun extractTotalAmount(text: String): Long? {
        val totalPatterns = listOf(
            Regex("""(?:합계|총합|결제금액|총액)\s*[:：]?\s*(\d{1,3}(?:,\d{3})*|\d+)\s*(?:원)""", RegexOption.IGNORE_CASE),
            Regex("""(\d{1,3}(?:,\d{3})*|\d+)\s*(?:원)?\s*(?:합계|총합)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in totalPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                return amountStr.toLongOrNull()
            }
        }

        return null
    }
}
