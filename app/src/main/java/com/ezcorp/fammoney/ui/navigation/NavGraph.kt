package com.ezcorp.fammoney.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ezcorp.fammoney.ui.screen.*
import com.ezcorp.fammoney.ui.viewmodel.AuthViewModel
import com.ezcorp.fammoney.ui.viewmodel.MainViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsState()
    val mainState by mainViewModel.uiState.collectAsState()

    val startDestination = when {
        authState.isLoading -> Screen.Splash.route
        authState.needsSetup -> Screen.Setup.route
        mainState.isLoggedIn -> Screen.Home.route
        else -> Screen.Setup.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen()
        }

        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                viewModel = authViewModel
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToBankSettings = {
                    navController.navigate(Screen.BankSettings.route)
                },
                onNavigateToStatistics = {
                    navController.navigate(Screen.Statistics.route)
                },
                onNavigateToChildIncome = {
                    navController.navigate(Screen.ChildIncome.route)
                },
                onNavigateToCashManagement = {
                    navController.navigate(Screen.CashManagement.route)
                },
                onNavigateToPendingDuplicates = {
                    navController.navigate(Screen.PendingDuplicates.route)
                },
                onNavigateToTransactionDetail = { transactionId ->
                    navController.navigate(Screen.TransactionDetail.createRoute(transactionId))
                },
                onNavigateToSavingsGoal = {
                    navController.navigate(Screen.SavingsGoal.route)
                },
                onNavigateToAICoaching = {
                    navController.navigate(Screen.AICoaching.route)
                },
                onNavigateToSubscription = {
                    navController.navigate(Screen.Subscription.route)
                },
                viewModel = mainViewModel
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBankSettings = {
                    navController.navigate(Screen.BankSettings.route)
                },
                onNavigateToBackup = {
                    navController.navigate(Screen.Backup.route)
                },
                onNavigateToSubscription = {
                    navController.navigate(Screen.Subscription.route)
                },
                onNavigateToAllowance = {
                    // 용돈 관리 -> 자녀 수입/지출 화면으로 통합
                navController.navigate(Screen.ChildIncome.route)
                },
                onNavigateToSavingsGoal = {
                    navController.navigate(Screen.SavingsGoal.route)
                },
                onNavigateToBankPatterns = {
                    navController.navigate(Screen.BankPatterns.route)
                },
                onNavigateToAICoaching = {
                    navController.navigate(Screen.AICoaching.route)
                },
                onNavigateToMotivation = {
                    navController.navigate(Screen.Motivation.route)
                },
                mainViewModel = mainViewModel,
                authViewModel = authViewModel
            )
        }

        composable(Screen.BankSettings.route) {
            BankSettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = mainViewModel
            )
        }


        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            TransactionDetailScreen(
                transactionId = transactionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ChildIncome.route) {
            ChildIncomeScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Backup.route) {
            BackupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CashManagement.route) {
            CashManagementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }


        composable(Screen.PendingDuplicates.route) {
            PendingDuplicatesScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }


        composable(Screen.SavingsGoal.route) {
            SavingsGoalScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { goalId ->
                    navController.navigate(Screen.SavingsGoalDetail.createRoute(goalId))
                },
                viewModel = mainViewModel
            )
        }

        composable(
            route = Screen.SavingsGoalDetail.route,
            arguments = listOf(navArgument("goalId") { type = NavType.StringType })
        ) { backStackEntry ->
            val goalId = backStackEntry.arguments?.getString("goalId") ?: ""
            SavingsGoalDetailScreen(
                goalId = goalId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Subscription.route) {
            SubscriptionScreen(
                onNavigateBack = { navController.popBackStack() },
                viewModel = mainViewModel
            )
        }

        // 은행 패턴 관리
        composable(Screen.BankPatterns.route) {
            BankPatternScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { patternId ->
                    navController.navigate(Screen.BankPatternEdit.createRoute(patternId))
                }
            )
        }

        composable(
            route = Screen.BankPatternEdit.route,
            arguments = listOf(navArgument("patternId") { type = NavType.StringType })
        ) { backStackEntry ->
            val patternId = backStackEntry.arguments?.getString("patternId") ?: ""
            BankPatternEditScreen(
                patternId = patternId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // AI 코칭
        composable(Screen.AICoaching.route) {
            AICoachingScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSettings = {
                    // 설정 화면으로 이동 (이미 설정에서 왔으므로 popBackStack만 호출)
                navController.popBackStack()
                },
                onNavigateToGuide = {
                    navController.navigate(Screen.FinancialGuide.route)
                }
            )
        }

        // 동기부여 성장 화면
        composable(Screen.Motivation.route) {
            MotivationScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAICoaching = {
                    navController.navigate(Screen.AICoaching.route)
                }
            )
        }

        // 금융 가이드
        composable(Screen.FinancialGuide.route) {
            FinancialGuideScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
