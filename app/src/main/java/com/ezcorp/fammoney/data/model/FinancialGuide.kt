package com.ezcorp.fammoney.data.model

/**
 * 금융 가이드 콘텐츠 모델
 */
data class FinancialGuide(
    val id: String,
    val category: GuideCategory,
    val title: String,
    val subtitle: String,
    val icon: String,
    val content: List<GuideSection>,
    val tips: List<String> = emptyList(),
    val relatedLinks: List<GuideLink> = emptyList()
)

data class GuideSection(
    val title: String,
    val content: String,
    val bulletPoints: List<String> = emptyList()
)

data class GuideLink(
    val title: String,
    val url: String,
    val description: String
)

enum class GuideCategory(val displayName: String, val icon: String) {
    SAVINGS("적금/예금", "🏦"),
    CMA("CMA 계좌", "💰"),
    ETF("ETF 투자", "📈"),
    STOCK("주식 입문", "📊"),
    FUND("펀드", "💼"),
    TAX("절세 상품", "📋"),
    INSURANCE("보험", "🛡️")
}

/**
 * 금융 가이드 콘텐츠 팩토리
 */
object FinancialGuides {

    val allGuides = listOf(
        // CMA 계좌 가이드
        FinancialGuide(
            id = "cma_intro",
            category = GuideCategory.CMA,
            title = "CMA 계좌란",
            subtitle = "파킹통장보다 높은 이자, 입출금 자유",
            icon = "💰",
            content = listOf(
                GuideSection(
                    title = "CMA란 무엇인가요",
                    content = "CMA(Cash Management Account)는 증권사에서 만드는 종합자산관리계좌입니다. 일반 입출금 예금보다 높은 금리를 제공하면서도 입출금이 자유롭습니다."
                ),
                GuideSection(
                    title = "CMA의 장점",
                    content = "입출금 예금 대비 여러 장점이 있습니다.",
                    bulletPoints = listOf(
                        "높은 금리: 일반 예금 대비 2~3배 높은 이자",
                        "입출금 자유: 언제든 입고 출금 가능",
                        "5천만원까지 예금자보호",
                        "증권계좌로 주식/ETF 투자도 가능"
                    )
                ),
                GuideSection(
                    title = "CMA 계좌 만드는 방법",
                    content = "증권사 앱에서 비대면으로 쉽게 개설할 수 있습니다.",
                    bulletPoints = listOf(
                        "1. 증권사 앱 다운로드 (삼성증권, 미래에셋, NH투자 등)",
                        "2. 회원가입 및 본인인증",
                        "3. CMA 계좌 개설 선택",
                        "4. 약관 동의 후 개설 완료 (5분 소요)"
                    )
                ),
                GuideSection(
                    title = "CMA 종류",
                    content = "CMA에는 여러 종류가 있습니다.",
                    bulletPoints = listOf(
                        "RP형: 가장 안전, 금리 보통",
                        "MMF형: 금리 높음, 기간에 따라 변동 가능",
                        "MMW형: RP와 MMF 혼합"
                    )
                )
            ),
            tips = listOf(
                "비상금을 CMA에 넣어두면 이자도 받고 급할 때 바로 출금 가능",
                "증권사마다 금리가 다르니 비교 후 선택하세요",
                "CMA도 예금자보호 대상입니다 (5천만원까지)"
            ),
            relatedLinks = listOf(
                GuideLink("삼성증권 CMA", "https://www.samsungpop.com", "삼성증권 앱에서 CMA 개설"),
                GuideLink("미래에셋증권", "https://www.miraeasset.com", "미래에셋 CMA 개설"),
                GuideLink("NH투자증권", "https://www.nhqv.com", "NH투자증권 CMA")
            )
        ),

        // 적금 가이드
        FinancialGuide(
            id = "savings_intro",
            category = GuideCategory.SAVINGS,
            title = "적금 vs 예금 차이",
            subtitle = "목돈 모으기 vs 목돈 굴리기",
            icon = "🏦",
            content = listOf(
                GuideSection(
                    title = "적금이란",
                    content = "매월 일정 금액을 저축하며 목돈을 만드는 상품입니다. 정기적으로 저축하는 습관을 기르기 좋습니다."
                ),
                GuideSection(
                    title = "예금이란",
                    content = "이미 가지고 있는 목돈을 맡겨두고 이자를 받는 상품입니다. 적금보다 금리가 더 높은 경우가 많습니다."
                ),
                GuideSection(
                    title = "적금 선택 팁",
                    content = "좋은 적금을 고르는 방법입니다.",
                    bulletPoints = listOf(
                        "금리 비교: 저축은행이 시중은행보다 금리 높음",
                        "우대금리 조건 확인: 급여이체, 카드사용 등",
                        "중도해지 이율 확인: 급하게 해지하면 이자 손해",
                        "자동이체 설정: 월급날 자동이체로 강제 저축"
                    )
                ),
                GuideSection(
                    title = "고금리 적금 찾는 팁",
                    content = "여러 방법으로 고금리 적금을 찾을 수 있습니다.",
                    bulletPoints = listOf(
                        "금융감독원 금융상품통합비교공시 사이트 이용",
                        "뱅크샐러드, 토스 등 핀테크 앱에서 비교",
                        "저축은행 앱 적금 우대금리",
                        "신규 가입자 우대 상품 확인"
                    )
                )
            ),
            tips = listOf(
                "월급의 20% 이상을 적금에 넣는 것이 이상적",
                "자동이체 설정하면 저축 성공률이 3배 올라가요",
                "1년 적금 만기 후 예금으로 넣으면 복리 효과"
            ),
            relatedLinks = listOf(
                GuideLink("금융상품 비교", "https://finlife.fss.or.kr", "금융감독원 금융상품 비교"),
                GuideLink("뱅크샐러드", "https://banksalad.com", "적금 금리 비교")
            )
        ),

        // ETF 가이드
        FinancialGuide(
            id = "etf_intro",
            category = GuideCategory.ETF,
            title = "ETF 입문 가이드",
            subtitle = "소액으로 분산투자 시작하기",
            icon = "📈",
            content = listOf(
                GuideSection(
                    title = "ETF란",
                    content = "ETF(상장지수펀드)는 주식처럼 거래되는 펀드입니다. 한 종목으로 여러 기업에 분산투자하는 효과가 있습니다."
                ),
                GuideSection(
                    title = "ETF의 장점",
                    content = "개별 주식 투자보다 여러 장점이 있습니다.",
                    bulletPoints = listOf(
                        "분산투자: 한 종목으로 수십~수백 개 기업에 투자",
                        "낮은 수수료: 일반 펀드보다 운용보수 저렴",
                        "쉬운 거래: 주식처럼 실시간 매매 가능",
                        "소액 투자: 1주 단위로 구매 가능(1만원대~)"
                    )
                ),
                GuideSection(
                    title = "초보자 추천 ETF 종류",
                    content = "처음 시작한다면 이런 ETF를 고려해보세요.",
                    bulletPoints = listOf(
                        "KODEX 200: 코스피 200 지수 추종 (한국 대표 기업)",
                        "TIGER 미국S&P500: 미국 대표 500개 기업",
                        "KODEX 미국나스닥100: 미국 기술주 중심",
                        "TIGER 미국배당다우존스: 배당주 중심, 안정적"
                    )
                ),
                GuideSection(
                    title = "ETF 투자 시작하기",
                    content = "ETF 투자를 시작하는 방법입니다.",
                    bulletPoints = listOf(
                        "1. 증권 계좌 개설 (증권사 앱)",
                        "2. 계좌에 돈 입금",
                        "3. 원하는 ETF 검색 (예: KODEX 200)",
                        "4. 매수 버튼 클릭, 수량 입력, 주문",
                        "5. 장기 보유하며 적립식 투자 권장"
                    )
                )
            ),
            tips = listOf(
                "처음엔 월 10만원씩 적립식으로 시작해보세요",
                "단기 하락에 일희일비하지 말고 장기 투자!",
                "해외 ETF는 환율 변동도 수익에 영향을 줘요",
                "배당 ETF는 분기/반년마다 배당금을 받을 수 있어요"
            ),
            relatedLinks = listOf(
                GuideLink("ETF 검색", "https://www.etfcheck.co.kr", "국내 ETF 정보 검색"),
                GuideLink("증권사 비교", "https://www.kisrating.com", "증권사 수수료 비교")
            )
        ),

        // 절세 상품 가이드
        FinancialGuide(
            id = "tax_saving",
            category = GuideCategory.TAX,
            title = "절세 금융상품 총정리",
            subtitle = "세금 아끼면서 똑똑하게 투자하기",
            icon = "📋",
            content = listOf(
                GuideSection(
                    title = "ISA (개인종합자산관리계좌)",
                    content = "다양한 금융상품을 한 계좌에서 관리하며 비과세 혜택을 받는 계좌입니다.",
                    bulletPoints = listOf(
                        "연간 2,000만원까지 납입 가능",
                        "3년 유지 시 200만원(서민형 400만원)까지 비과세",
                        "예금, 펀드, ETF 등 다양한 상품 편입 가능",
                        "2024년부터 국내주식도 편입 가능"
                    )
                ),
                GuideSection(
                    title = "연금저축",
                    content = "노후 대비 + 세액공제 혜택을 동시에",
                    bulletPoints = listOf(
                        "연 600만원까지 세액공제 (13.2~16.5%)",
                        "연 최대 79.2~99만원 세금 환급",
                        "55세 이후 연금으로 수령",
                        "연금저축펀드로 ETF 투자도 가능"
                    )
                ),
                GuideSection(
                    title = "IRP (개인형퇴직연금)",
                    content = "퇴직금 + 추가 납입으로 세액공제 받기",
                    bulletPoints = listOf(
                        "연금저축과 합산 연 900만원까지 세액공제",
                        "퇴직금 수령 시 퇴직소득세 이연",
                        "55세 이후 연금 수령 시 저율 과세"
                    )
                )
            ),
            tips = listOf(
                "연말정산 전에 연금저축 납입하면 환급 챙기기",
                "ISA 만기 후 연금저축으로 이전하면 추가 세액공제",
                "총급여 5,500만원 이하면 서민형 ISA 가입 가능"
            ),
            relatedLinks = listOf(
                GuideLink("ISA 안내", "https://www.fss.or.kr", "금융감독원 ISA 안내")
            )
        ),

        // 주식 입문
        FinancialGuide(
            id = "stock_intro",
            category = GuideCategory.STOCK,
            title = "주식 투자 입문",
            subtitle = "처음 시작하는 주식 투자",
            icon = "📊",
            content = listOf(
                GuideSection(
                    title = "주식이란",
                    content = "기업의 소유권 일부를 사는 것입니다. 주가가 오르면 수익, 내리면 손실을 봅니다."
                ),
                GuideSection(
                    title = "주식 투자 시작하기",
                    content = "주식 투자를 시작하는 단계입니다.",
                    bulletPoints = listOf(
                        "1. 증권 계좌 개설 (비대면 가능)",
                        "2. 투자할 금액 입금",
                        "3. 관심 있는 기업 분석",
                        "4. 소액으로 첫 매수",
                        "5. 꾸준히 공부하며 투자"
                    )
                ),
                GuideSection(
                    title = "초보자 주의사항",
                    content = "처음 투자할 때 주의할 점입니다.",
                    bulletPoints = listOf(
                        "여유자금으로만 투자 (생활비 X)",
                        "한 종목에 몰빵 금지 - 분산투자",
                        "단타보다 장기투자 권장",
                        "손실이 날 수 있음을 인지",
                        "남의 말만 듣지 말고 직접 분석"
                    )
                )
            ),
            tips = listOf(
                "처음엔 ETF로 시작하는 것도 좋은 방법!",
                "투자 일지를 쓰면 실력이 늘어요",
                "하락장에 겁먹지 말고, 좋은 기업은 버텨요"
            ),
            relatedLinks = listOf(
                GuideLink("한국거래소", "https://www.krx.co.kr", "주식 시세 확인")
            )
        )
    )

    fun getByCategory(category: GuideCategory): List<FinancialGuide> {
        return allGuides.filter { it.category == category }
    }

    fun getById(id: String): FinancialGuide? {
        return allGuides.find { it.id == id }
    }
}

/**
 * 은행별 앱 정보
 */
data class BankAppInfo(
    val bankName: String,
    val packageName: String,
    val playStoreUrl: String,
    val features: List<String>
)

object BankApps {
    val apps = listOf(
        BankAppInfo(
            bankName = "KB국민",
            packageName = "com.kbstar.kbbank",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.kbstar.kbbank",
            features = listOf("KB스타뱅킹", "예금/적금", "대출", "환전")
        ),
        BankAppInfo(
            bankName = "신한은행",
            packageName = "com.shinhan.sbanking",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.shinhan.sbanking",
            features = listOf("쏠(SOL)", "예금/적금", "투자", "보험")
        ),
        BankAppInfo(
            bankName = "우리은행",
            packageName = "com.wooribank.smart.npib",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.wooribank.smart.npib",
            features = listOf("우리WON뱅킹", "예금/적금", "대출")
        ),
        BankAppInfo(
            bankName = "하나은행",
            packageName = "com.kebhana.hanapush",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.kebhana.hanapush",
            features = listOf("하나원큐", "예금/적금", "투자")
        ),
        BankAppInfo(
            bankName = "카카오뱅크",
            packageName = "com.kakaobank.channel",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.kakaobank.channel",
            features = listOf("간편송금", "26주적금", "모임통장")
        ),
        BankAppInfo(
            bankName = "토스",
            packageName = "viva.republica.toss",
            playStoreUrl = "https://play.google.com/store/apps/details?id=viva.republica.toss",
            features = listOf("간편송금", "투자", "보험", "대출비교")
        ),
        BankAppInfo(
            bankName = "삼성증권",
            packageName = "com.samsung.android.mPOP",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.samsung.android.mPOP",
            features = listOf("mPOP", "CMA", "주식/ETF", "연금")
        ),
        BankAppInfo(
            bankName = "미래에셋증권",
            packageName = "com.miraeasset.trade",
            playStoreUrl = "https://play.google.com/store/apps/details?id=com.miraeasset.trade",
            features = listOf("M-STOCK", "CMA", "해외주식", "연금")
        )
    )

    fun getByBankName(bankName: String): BankAppInfo? {
        return apps.find { bankName.contains(it.bankName) || it.bankName.contains(bankName) }
    }
}
