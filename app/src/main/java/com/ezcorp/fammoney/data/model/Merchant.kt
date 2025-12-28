package com.ezcorp.fammoney.data.model

/**
 * 사용처 (가맹점/서비스)
 */
data class Merchant(
    val id: String,
    val displayName: String,
    val keywords: List<String>,  // 알림에서 찾을 문자열 목록
    val defaultCategory: SpendingCategory,
    val icon: String = ""
) {
    companion object {
        fun getDefaultMerchants(): List<Merchant> = listOf(
            // 온라인 쇼핑
            Merchant("coupang", "쿠팡", listOf("쿠팡", "COUPANG", "로켓배송"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDCE6"),
            Merchant("naver_shopping", "네이버쇼핑", listOf("네이버페이", "NAVERPAY", "네이버쇼핑"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("gmarket", "G마켓", listOf("G마켓", "GMARKET", "지마켓"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("auction", "옥션", listOf("옥션", "AUCTION"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("11st", "11번가", listOf("11번가", "11ST"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("tmon", "티몬", listOf("티몬", "TMON", "티켓몬스터"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("wemakeprice", "위메프", listOf("위메프", "WEMAKEPRICE"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("ssg", "SSG닷컴", listOf("SSG", "쓱닷컴", "신세계몰"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("lotte_on", "롯데온", listOf("롯데온", "LOTTE ON"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDED2"),
            Merchant("aliexpress", "알리익스프레스", listOf("알리익스프레스", "ALIEXPRESS", "알리"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("temu", "테무", listOf("테무", "TEMU"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("amazon", "아마존", listOf("아마존", "AMAZON", "AMZN"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDCE6"),
            Merchant("ebay", "이베이", listOf("이베이", "EBAY"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("shein", "쉬인", listOf("쉬인", "SHEIN"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("iherb", "아이허브", listOf("아이허브", "IHERB"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("shopee", "쇼피", listOf("쇼피", "SHOPEE"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("lazada", "라자다", listOf("라자다", "LAZADA"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("wish", "위시", listOf("위시", "WISH"), SpendingCategory.ONLINE_SHOPPING, "\u2B50"),
            Merchant("farfetch", "파페치", listOf("파페치", "FARFETCH"), SpendingCategory.CLOTHING, "\uD83D\uDC5C"),
            Merchant("ssense", "센스", listOf("SSENSE", "센스"), SpendingCategory.CLOTHING, "\uD83D\uDC5C"),
            Merchant("mytheresa", "마이테레사", listOf("MYTHERESA", "마이테레사"), SpendingCategory.CLOTHING, "\uD83D\uDC5C"),
            Merchant("matchesfashion", "매치스패션", listOf("MATCHES", "매치스"), SpendingCategory.CLOTHING, "\uD83D\uDC57"),
            Merchant("asos", "에이소스", listOf("ASOS", "에이소스"), SpendingCategory.CLOTHING, "\uD83D\uDC5C"),
            Merchant("zappos", "자포스", listOf("ZAPPOS", "자포스"), SpendingCategory.SHOES_BAG, "\uD83D\uDC5F"),
            Merchant("stockx", "스톡엑스", listOf("STOCKX", "스톡엑스"), SpendingCategory.SHOES_BAG, "\uD83D\uDC5F"),
            Merchant("etsy", "엣시", listOf("ETSY", "엣시"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDFA8"),
            Merchant("banggood", "뱅굿", listOf("BANGGOOD", "뱅굿"), SpendingCategory.ONLINE_SHOPPING, "\uD83D\uDCF1"),
            Merchant("gearbest", "기어베스트", listOf("GEARBEST", "기어베스트"), SpendingCategory.ELECTRONICS, "\uD83E\uDDF0"),
            Merchant("dhgate", "디에이치게이트", listOf("DHGATE"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDF0F"),
            Merchant("taobao", "타오바오", listOf("타오바오", "TAOBAO", "淘宝"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDDE8\uD83C\uDDF3"),
            Merchant("jd", "징동", listOf("징동", "JD.COM", "京东"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDDE8\uD83C\uDDF3"),
            Merchant("rakuten", "라쿠텐", listOf("라쿠텐", "RAKUTEN", "楽天"), SpendingCategory.ONLINE_SHOPPING, "\uD83C\uDDEF\uD83C\uDDF5"),

            // 배달
            Merchant("baemin", "배달의민족", listOf("배달의민족", "배민", "BAEMIN"), SpendingCategory.DELIVERY, "\uD83D\uDCF5"),
            Merchant("yogiyo", "요기요", listOf("요기요", "YOGIYO"), SpendingCategory.DELIVERY, "\uD83D\uDCF5"),
            Merchant("coupang_eats", "쿠팡이츠", listOf("쿠팡이츠", "COUPANGEATS"), SpendingCategory.DELIVERY, "\uD83C\uDF5C"),

            // OTT / 구독
            Merchant("netflix", "넷플릭스", listOf("넷플릭스", "NETFLIX"), SpendingCategory.OTT, "\uD83C\uDFAC"),
            Merchant("youtube", "유튜브 프리미엄", listOf("유튜브", "YOUTUBE", "GOOGLE *YouTube"), SpendingCategory.OTT, "\u25B6\uFE0F"),
            Merchant("disney_plus", "디즈니플러스", listOf("디즈니플러스", "DISNEY+", "DISNEY PLUS"), SpendingCategory.OTT, "\uD83D\uDC2D"),
            Merchant("wavve", "웨이브", listOf("웨이브", "WAVVE"), SpendingCategory.OTT, "\uD83C\uDF0A"),
            Merchant("tving", "티빙", listOf("티빙", "TVING"), SpendingCategory.OTT, "\uD83D\uDCFA"),
            Merchant("watcha", "왓챠", listOf("왓챠", "WATCHA"), SpendingCategory.OTT, "\uD83C\uDFAC"),
            Merchant("apple_tv", "Apple TV+", listOf("APPLE TV", "애플TV"), SpendingCategory.OTT, "\uD83C\uDF4E"),

            // 음악
            Merchant("spotify", "스포티파이", listOf("스포티파이", "SPOTIFY"), SpendingCategory.MUSIC, "\uD83C\uDFB5"),
            Merchant("melon", "멜론", listOf("멜론", "MELON"), SpendingCategory.MUSIC, "\uD83C\uDF48"),
            Merchant("genie", "지니뮤직", listOf("지니", "GENIE"), SpendingCategory.MUSIC, "\uD83E\uDDDE"),
            Merchant("flo", "플로", listOf("플로", "FLO"), SpendingCategory.MUSIC, "\uD83C\uDF36"),
            Merchant("apple_music", "Apple Music", listOf("APPLE MUSIC", "애플뮤직"), SpendingCategory.MUSIC, "\uD83C\uDF4E"),
            Merchant("youtube_music", "유튜브뮤직", listOf("YOUTUBE MUSIC"), SpendingCategory.MUSIC, "\uD83C\uDFB5"),

            // 카페
            Merchant("starbucks", "스타벅스", listOf("스타벅스", "STARBUCKS"), SpendingCategory.CAFE_SNACK, "\u2615"),
            Merchant("twosome", "투썸플레이스", listOf("투썸", "TWOSOME", "A TWOSOME"), SpendingCategory.CAFE_SNACK, "\u2615"),
            Merchant("ediya", "이디야", listOf("이디야", "EDIYA"), SpendingCategory.CAFE_SNACK, "\u2615"),
            Merchant("mega_coffee", "메가커피", listOf("메가", "MEGA", "메가커피"), SpendingCategory.CAFE_SNACK, "\u2615"),
            Merchant("compose", "컴포즈커피", listOf("컴포즈", "COMPOSE"), SpendingCategory.CAFE_SNACK, "\u2615"),
            Merchant("paik_coffee", "빽다방", listOf("빽다방", "PAIK"), SpendingCategory.CAFE_SNACK, "\u2615"),

            // 편의점
            Merchant("cu", "CU", listOf("CU", "씨유"), SpendingCategory.CAFE_SNACK, "\uD83C\uDFEA"),
            Merchant("gs25", "GS25", listOf("GS25", "지에스25"), SpendingCategory.CAFE_SNACK, "\uD83C\uDFEA"),
            Merchant("seveneleven", "세븐일레븐", listOf("세븐일레븐", "7ELEVEN"), SpendingCategory.CAFE_SNACK, "\uD83C\uDFEA"),
            Merchant("emart24", "이마트24", listOf("이마트24", "EMART24"), SpendingCategory.CAFE_SNACK, "\uD83C\uDFEA"),

            // 마트
            Merchant("emart", "이마트", listOf("이마트", "EMART"), SpendingCategory.GROCERY, "\uD83D\uDED2"),
            Merchant("homeplus", "홈플러스", listOf("홈플러스", "HOMEPLUS"), SpendingCategory.GROCERY, "\uD83D\uDED2"),
            Merchant("lotte_mart", "롯데마트", listOf("롯데마트", "LOTTEMART"), SpendingCategory.GROCERY, "\uD83D\uDED2"),
            Merchant("costco", "코스트코", listOf("코스트코", "COSTCO"), SpendingCategory.GROCERY, "\uD83D\uDED2"),
            Merchant("traders", "트레이더스", listOf("트레이더스", "TRADERS"), SpendingCategory.GROCERY, "\uD83D\uDED2"),

            // 패스트푸드
            Merchant("mcdonalds", "맥도날드", listOf("맥도날드", "MCDONALD", "맥날"), SpendingCategory.DINING_OUT, "\uD83C\uDF54"),
            Merchant("burgerking", "버거킹", listOf("버거킹", "BURGERKING"), SpendingCategory.DINING_OUT, "\uD83C\uDF54"),
            Merchant("lotteria", "롯데리아", listOf("롯데리아", "LOTTERIA"), SpendingCategory.DINING_OUT, "\uD83C\uDF54"),
            Merchant("kfc", "KFC", listOf("KFC", "케이에프씨"), SpendingCategory.DINING_OUT, "\uD83C\uDF57"),
            Merchant("subway", "서브웨이", listOf("서브웨이", "SUBWAY"), SpendingCategory.DINING_OUT, "\uD83E\uDD6A"),
            Merchant("dominos", "도미노피자", listOf("도미노", "DOMINO"), SpendingCategory.DINING_OUT, "\uD83C\uDF55"),
            Merchant("pizzahut", "피자헛", listOf("피자헛", "PIZZAHUT"), SpendingCategory.DINING_OUT, "\uD83C\uDF55"),

            // 교통
            Merchant("kakao_taxi", "카카오택시", listOf("카카오택시", "KAKAOT"), SpendingCategory.TAXI, "\uD83D\uDE95"),
            Merchant("uber", "우버", listOf("우버", "UBER"), SpendingCategory.TAXI, "\uD83D\uDE95"),
            Merchant("tada", "타다", listOf("타다", "TADA"), SpendingCategory.TAXI, "\uD83D\uDE95"),
            Merchant("korail", "코레일", listOf("코레일", "KORAIL", "KTX"), SpendingCategory.TRANSPORTATION, "\uD83D\uDE84"),
            Merchant("srt", "SRT", listOf("SRT", "에스알티"), SpendingCategory.TRANSPORTATION, "\uD83D\uDE84"),

            // 주유
            Merchant("sk_energy", "SK에너지", listOf("SK에너지", "SK주유"), SpendingCategory.CAR, "\u26FD"),
            Merchant("gs_caltex", "GS칼텍스", listOf("GS칼텍스", "지에스칼텍스"), SpendingCategory.CAR, "\u26FD"),
            Merchant("hyundai_oilbank", "현대오일뱅크", listOf("현대오일뱅크", "오일뱅크"), SpendingCategory.CAR, "\u26FD"),
            Merchant("soil", "S-OIL", listOf("S-OIL", "에쓰오일"), SpendingCategory.CAR, "\u26FD"),

            // 게임
            Merchant("google_play", "구글 플레이", listOf("GOOGLE PLAY", "구글 플레이"), SpendingCategory.GAME, "\uD83C\uDFAE"),
            Merchant("apple_appstore", "앱스토어", listOf("APPLE.COM", "앱스토어", "APP STORE"), SpendingCategory.GAME, "\uD83C\uDF4E"),
            Merchant("steam", "스팀", listOf("STEAM", "스팀"), SpendingCategory.GAME, "\uD83C\uDFAE"),
            Merchant("nexon", "넥슨", listOf("넥슨", "NEXON"), SpendingCategory.GAME, "\uD83C\uDFAE"),
            Merchant("nc", "엔씨소프트", listOf("엔씨", "NCSOFT"), SpendingCategory.GAME, "\uD83C\uDFAE"),

            // 패션
            Merchant("musinsa", "무신사", listOf("무신사", "MUSINSA"), SpendingCategory.CLOTHING, "\uD83D\uDC55"),
            Merchant("zigzag", "지그재그", listOf("지그재그", "ZIGZAG"), SpendingCategory.CLOTHING, "\uD83D\uDC57"),
            Merchant("ably", "에이블리", listOf("에이블리", "ABLY"), SpendingCategory.CLOTHING, "\uD83D\uDC57"),
            Merchant("w_concept", "W컨셉", listOf("W컨셉", "WCONCEPT"), SpendingCategory.CLOTHING, "\uD83D\uDC57"),
            Merchant("uniqlo", "유니클로", listOf("유니클로", "UNIQLO"), SpendingCategory.CLOTHING, "\uD83D\uDC55"),
            Merchant("zara", "자라", listOf("자라", "ZARA"), SpendingCategory.CLOTHING, "\uD83D\uDC57"),
            Merchant("hm", "H&M", listOf("H&M", "에이치앤엠"), SpendingCategory.CLOTHING, "\uD83D\uDC55"),
            Merchant("nike", "나이키", listOf("나이키", "NIKE"), SpendingCategory.SHOES_BAG, "\uD83D\uDC5F"),
            Merchant("adidas", "아디다스", listOf("아디다스", "ADIDAS"), SpendingCategory.SHOES_BAG, "\uD83D\uDC5F"),

            // 뷰티
            Merchant("olive_young", "올리브영", listOf("올리브영", "OLIVEYOUNG"), SpendingCategory.BEAUTY, "\uD83D\uDC84"),
            Merchant("lalavla", "랄라블라", listOf("랄라블라", "LALAVLA"), SpendingCategory.BEAUTY, "\uD83D\uDC84"),
            Merchant("aritaum", "아리따움", listOf("아리따움", "ARITAUM"), SpendingCategory.BEAUTY, "\uD83D\uDC84"),

            // 통신
            Merchant("skt", "SKT", listOf("SK텔레콤", "SKT"), SpendingCategory.INTERNET_PHONE, "\uD83D\uDCF1"),
            Merchant("kt", "KT", listOf("KT", "케이티"), SpendingCategory.INTERNET_PHONE, "\uD83D\uDCF1"),
            Merchant("lgu", "LG U+", listOf("LG U+", "유플러스", "LGU"), SpendingCategory.INTERNET_PHONE, "\uD83D\uDCF1"),

            // 교육/서적
            Merchant("yes24", "예스24", listOf("예스24", "YES24"), SpendingCategory.BOOK, "\uD83D\uDCDA"),
            Merchant("kyobo", "교보문고", listOf("교보문고", "KYOBO"), SpendingCategory.BOOK, "\uD83D\uDCDA"),
            Merchant("aladin", "알라딘", listOf("알라딘", "ALADIN"), SpendingCategory.BOOK, "\uD83D\uDCDA"),
            Merchant("class101", "클래스101", listOf("클래스101", "CLASS101"), SpendingCategory.ONLINE_COURSE, "\uD83D\uDCBB"),
            Merchant("fastcampus", "패스트캠퍼스", listOf("패스트캠퍼스", "FASTCAMPUS"), SpendingCategory.ONLINE_COURSE, "\uD83D\uDCBB"),

            // 기타 서비스
            Merchant("kakaopay", "카카오페이", listOf("카카오페이", "KAKAOPAY"), SpendingCategory.TRANSFER, "\uD83D\uDCB3"),
            Merchant("toss", "토스", listOf("토스", "TOSS"), SpendingCategory.TRANSFER, "\uD83D\uDCB3"),
            Merchant("payco", "페이코", listOf("페이코", "PAYCO"), SpendingCategory.TRANSFER, "\uD83D\uDCB3"),

            // 미분류
            Merchant("other", "기타", listOf(), SpendingCategory.OTHER, "\uD83D\uDCB0")
        )

        /**
         * 알림 텍스트에서 사용처를 자동 감지
         */
        fun detectMerchant(text: String): Merchant? {
            val upperText = text.uppercase()
            return getDefaultMerchants().find { merchant ->
                merchant.keywords.any { keyword ->
                    upperText.contains(keyword.uppercase())
                }
            }
        }
    }
}
