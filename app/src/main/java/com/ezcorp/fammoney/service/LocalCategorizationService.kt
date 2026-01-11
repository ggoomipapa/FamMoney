package com.ezcorp.fammoney.service

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 없이 로컬에서 동작하는 카테고리 분류 및 가맹점명 추출 서비스
 * - 정규식 기반 가맹점명 추출
 * - 키워드 DB 기반 카테고리 매칭
 */
@Singleton
class LocalCategorizationService @Inject constructor() {

    /**
     * 알림 텍스트에서 가맹점명 추출 (정규식 기반)
     */
    fun extractMerchantName(notificationText: String): String {
        // 일반적인 은행/카드 알림 패턴들 (우선순위 순)
        val patterns = listOf(
            // === 카드 승인/취소 형식 ===
            // [카드사] 승인 금액원 사용처
            Regex("""\[.+?(?:카드|Card)\]\s*(?:승인|취소)\s+[\d,]+원\s+(.+?)(?:\s|$)"""),
            // 카드사 승인 금액원 사용처 (대괄호 없는 형식)
            Regex("""(?:신한|KB국민|현대|삼성|롯데|BC|NH|하나|우리)카드\s*(?:승인|취소)\s+[\d,]+원\s+(.+?)(?:\s|$)"""),

            // === KB국민은행 출금 형식 ===
            Regex("""([가-힣a-zA-Z0-9()]{2,20})\s*(?:체크카드출금|신용카드출금|카드출금)"""),

            // === 법인명 패턴 ===
            Regex("""(\([주유사재]\)[가-힣a-zA-Z0-9]{1,15})"""),

            // === 토스/카카오페이 형식 ===
            Regex("""(?:토스|카카오페이)\s*-\s*결제\s+[\d,]+원\s+(.+?)(?:\s|$)"""),

            // === 일반 카드 형식 ===
            Regex("""(?:승인|결제)\s+[\d,]+원\s+([가-힣a-zA-Z0-9][가-힣a-zA-Z0-9\s]{1,20}?)(?:\s|$)"""),
            Regex("""([가-힣a-zA-Z][가-힣a-zA-Z0-9\s]{1,20}?)\s+[\d,]+원\s*(?:승인|결제)"""),

            // === 기타 형식 ===
            Regex("""계좌번호\s+([가-힣a-zA-Z][가-힣a-zA-Z0-9]{0,15})(?:\s|$)"""),
            Regex("""([가-힣a-zA-Z0-9]{2,15})에서"""),
        )

        for (pattern in patterns) {
            val match = pattern.find(notificationText)
            if (match != null) {
                val merchantName = match.groupValues[1].trim()
                // 필터링: 제외할 키워드, 마스킹된 이름, 계좌번호 형식
                if (!isExcludedKeyword(merchantName) &&
                    !isMaskedOwnerName(merchantName) &&
                    !isAccountNumber(merchantName)) {
                    return cleanMerchantName(merchantName)
                }
            }
        }

        return ""
    }

    /**
     * 마스킹된 본인 이름인지 확인
     */
    private fun isMaskedOwnerName(text: String): Boolean {
        val maskedNamePatterns = listOf(
            Regex("""^[가-힣]\*[가-힣]님?$"""),
            Regex("""^[가-힣]\*[가-힣]{2}님?$"""),
            Regex("""^[가-힣]{2}\*[가-힣]님?$""")
        )
        return maskedNamePatterns.any { it.matches(text) }
    }

    /**
     * 계좌번호 형식인지 확인
     */
    private fun isAccountNumber(text: String): Boolean {
        return Regex("""^\d+\*+\d*$""").matches(text)
    }

    /**
     * 제외할 키워드 체크
     */
    private fun isExcludedKeyword(text: String): Boolean {
        val excludeKeywords = listOf(
            "승인", "결제", "출금", "입금", "취소", "사용", "잔액",
            "KB", "신한", "우리", "하나", "농협", "국민", "카드",
            "체크", "신용", "일시불", "할부", "계좌", "이체"
        )
        return excludeKeywords.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 가맹점명 정리
     */
    private fun cleanMerchantName(name: String): String {
        return name
            .replace(Regex("""[\d,]+원?"""), "")
            .replace(Regex("""\d{1,2}[/.-]\d{1,2}"""), "")
            .replace(Regex("""\d{2}:\d{2}"""), "")
            .trim()
            .take(20) // 최대 20자
    }

    /**
     * 가맹점명 기반 카테고리 자동 분류 (키워드 매칭)
     */
    fun categorize(merchantName: String, amount: Long = 0): AutoCategoryResult {
        val lowerName = merchantName.lowercase()

        // 키워드 매칭으로 카테고리 찾기
        for ((category, keywords) in categoryKeywords) {
            for (keyword in keywords) {
                if (lowerName.contains(keyword.lowercase())) {
                    return AutoCategoryResult(
                        category = category,
                        confidence = 0.85f,
                        reason = "키워드 매칭: $keyword"
                    )
                }
            }
        }

        // 금액 기반 추론 (매칭 실패 시)
        return when {
            amount in 1000..10000 -> AutoCategoryResult("CAFE_SNACK", 0.4f, "소액 결제")
            amount in 10000..50000 -> AutoCategoryResult("DINING_OUT", 0.3f, "일반 결제")
            amount > 100000 -> AutoCategoryResult("ONLINE_SHOPPING", 0.3f, "고액 결제")
            else -> AutoCategoryResult("OTHER", 0.2f, "분류 불확실")
        }
    }

    companion object {
        /**
         * 카테고리별 키워드 데이터베이스
         */
        val categoryKeywords = mapOf(
            // 카페/간식
            "CAFE_SNACK" to listOf(
                "스타벅스", "투썸", "이디야", "메가커피", "컴포즈", "빽다방", "할리스",
                "파스쿠찌", "카페베네", "엔제리너스", "탐앤탐스", "커피빈", "폴바셋",
                "블루보틀", "커피", "카페", "cafe", "coffee", "던킨", "크리스피",
                "배스킨라빈스", "베스킨", "아이스크림", "설빙", "빙수", "GS25", "CU", "세븐일레븐",
                "이마트24", "미니스톱", "편의점"
            ),
            // 외식
            "DINING_OUT" to listOf(
                "맥도날드", "버거킹", "롯데리아", "KFC", "맘스터치", "노브랜드버거",
                "피자헛", "도미노", "파파존스", "미스터피자", "피자",
                "김밥천국", "본죽", "죽이야기", "한솥", "놀부", "새마을식당", "백종원",
                "빕스", "애슐리", "아웃백", "뷔페", "고기", "삼겹살", "갈비",
                "치킨", "BBQ", "BHC", "굽네", "교촌", "네네치킨", "호식이",
                "식당", "레스토랑", "맛집"
            ),
            // 배달
            "DELIVERY" to listOf(
                "배달의민족", "배민", "요기요", "쿠팡이츠", "위메프오", "땡겨요"
            ),
            // 마트/식료품
            "GROCERY" to listOf(
                "이마트", "홈플러스", "롯데마트", "코스트코", "트레이더스",
                "하나로마트", "농협마트", "GS슈퍼", "롯데슈퍼", "마트",
                "슈퍼", "시장", "청과", "정육"
            ),
            // 온라인쇼핑
            "ONLINE_SHOPPING" to listOf(
                "쿠팡", "coupang", "네이버페이", "naverpay", "카카오페이", "kakaopay",
                "G마켓", "gmarket", "옥션", "auction", "11번가", "위메프", "티몬",
                "인터파크", "SSG", "신세계몰", "롯데온", "무신사", "29cm", "지그재그",
                "에이블리", "브랜디", "번개장터", "당근마켓", "중고나라",
                "아마존", "amazon", "알리익스프레스", "aliexpress", "테무", "temu"
            ),
            // 의류
            "CLOTHING" to listOf(
                "유니클로", "ZARA", "H&M", "자라", "에잇세컨즈", "탑텐",
                "스파오", "미쏘", "폴로", "나이키", "아디다스", "뉴발란스",
                "푸마", "휠라", "의류", "옷", "패션"
            ),
            // 대중교통
            "TRANSPORTATION" to listOf(
                "버스", "지하철", "전철", "코레일", "KTX", "SRT", "ITX",
                "티머니", "캐시비", "교통카드", "철도"
            ),
            // 택시
            "TAXI" to listOf(
                "카카오택시", "타다", "우버", "택시", "TAXI"
            ),
            // 자동차
            "CAR" to listOf(
                "주유소", "GS칼텍스", "SK에너지", "현대오일뱅크", "S-OIL", "알뜰주유소",
                "세차", "정비", "타이어", "오토", "자동차", "하이패스"
            ),
            // 주차
            "PARKING" to listOf(
                "주차", "파킹", "parking"
            ),
            // OTT/구독
            "OTT" to listOf(
                "넷플릭스", "netflix", "유튜브", "youtube", "웨이브", "wavve",
                "티빙", "tving", "왓챠", "watcha", "디즈니", "disney",
                "애플TV", "appletv", "쿠팡플레이", "아마존프라임"
            ),
            // 음악
            "MUSIC" to listOf(
                "멜론", "melon", "지니", "genie", "플로", "flo", "벅스", "bugs",
                "스포티파이", "spotify", "애플뮤직", "applemusic", "유튜브뮤직"
            ),
            // 게임
            "GAME" to listOf(
                "구글플레이", "googleplay", "앱스토어", "appstore", "넥슨", "nexon",
                "넷마블", "netmarble", "NC", "엔씨", "스팀", "steam", "게임", "game"
            ),
            // 영화
            "MOVIE" to listOf(
                "CGV", "메가박스", "롯데시네마", "영화", "시네마", "cinema"
            ),
            // 여행
            "TRAVEL" to listOf(
                "야놀자", "여기어때", "호텔", "모텔", "펜션", "에어비앤비", "airbnb",
                "아고다", "부킹닷컴", "트립닷컴", "호텔스닷컴", "여행", "항공"
            ),
            // 건강/의료
            "HEALTH" to listOf(
                "병원", "의원", "클리닉", "약국", "pharmacy", "헬스", "gym",
                "필라테스", "요가", "PT", "피트니스"
            ),
            // 미용
            "BEAUTY" to listOf(
                "미용실", "헤어", "hair", "네일", "nail", "피부과", "성형",
                "올리브영", "롭스", "화장품", "뷰티"
            ),
            // 교육
            "EDUCATION" to listOf(
                "학원", "학교", "대학", "어학", "영어", "수학", "과외",
                "인강", "클래스101", "탈잉", "숨고"
            ),
            // 보험
            "INSURANCE" to listOf(
                "삼성생명", "한화생명", "교보생명", "메리츠", "DB손해", "현대해상",
                "보험", "insurance"
            ),
            // 통신
            "INTERNET_PHONE" to listOf(
                "SKT", "KT", "LG유플러스", "알뜰폰", "통신", "인터넷"
            ),
            // 공과금
            "UTILITIES" to listOf(
                "한국전력", "전기", "가스", "수도", "관리비", "공과금"
            ),
            // 이체/송금
            "TRANSFER" to listOf(
                "이체", "송금", "입금", "출금"
            ),
            // ATM
            "ATM" to listOf(
                "ATM", "현금", "인출"
            )
        )
    }
}
