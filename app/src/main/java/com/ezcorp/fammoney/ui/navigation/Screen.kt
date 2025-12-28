package com.ezcorp.fammoney.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Setup : Screen("setup")
    data object JoinGroup : Screen("join_group")
    data object Home : Screen("home")
    data object Transactions : Screen("transactions")
    data object Settings : Screen("settings")
    data object BankSettings : Screen("bank_settings")
    data object GroupMembers : Screen("group_members")
    data object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: String) = "transaction_detail/$transactionId"
    }
    data object Statistics : Screen("statistics")

    // ?��? ?�입 관???�면
    data object ChildIncome : Screen("child_income")
    data object ChildManagement : Screen("child_management")
    data object AddChildIncome : Screen("add_child_income/{childId}") {
        fun createRoute(childId: String) = "add_child_income/$childId"
    }

    // 백업 �?복원
    data object Backup : Screen("backup")

    // ?�금 관�?
    data object CashManagement : Screen("cash_management")


    // 중복 거래 ?�인
    data object PendingDuplicates : Screen("pending_duplicates")


    // 목표 ?��?
    data object SavingsGoal : Screen("savings_goal")
    data object SavingsGoalDetail : Screen("savings_goal_detail/{goalId}") {
        fun createRoute(goalId: String) = "savings_goal_detail/$goalId"
    }

    // 구독
    data object Subscription : Screen("subscription")

    // ?�???�턴 관�?(관리자/개발??모드)
    data object BankPatterns : Screen("bank_patterns")
    data object BankPatternEdit : Screen("bank_pattern_edit/{patternId}") {
        fun createRoute(patternId: String) = "bank_pattern_edit/$patternId"
    }

    // AI 코칭
    data object AICoaching : Screen("ai_coaching")

    // ?�기부???�장 ?�면
    data object Motivation : Screen("motivation")

    // 금융 가?�드
    data object FinancialGuide : Screen("financial_guide")
}
