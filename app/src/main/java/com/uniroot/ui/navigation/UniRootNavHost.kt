package com.uniroot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.uniroot.ui.screen.*

@Composable
fun UniRootNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onInstallClick = { navController.navigate(Routes.INSTALL) },
                onSuperUserClick = { navController.navigate(Routes.SUPERUSER) },
                onModuleClick = { navController.navigate(Routes.MODULE) },
                onRebootClick = { navController.navigate(Routes.REBOOT) }
            )
        }

        composable(Routes.SUPERUSER) {
            SuperUserScreen()
        }

        composable(Routes.MODULE) {
            ModuleScreen(
                onFlashAK3 = { navController.navigate(Routes.FLASH_AK3) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onRebootClick = { navController.navigate(Routes.REBOOT) }
            )
        }

        composable(Routes.INSTALL) {
            InstallScreen(
                onCategorySelect = { category ->
                    navController.navigate("provider_select/$category")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PROVIDER_SELECT,
            arguments = listOf(navArgument("category") { type = NavType.StringType })
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category") ?: ""
            ProviderSelectScreen(
                category = category,
                onProviderSelect = { providerId ->
                    navController.navigate("patch/$providerId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PATCH,
            arguments = listOf(navArgument("providerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val providerId = backStackEntry.arguments?.getString("providerId") ?: ""
            PatchScreen(
                providerId = providerId,
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack(Routes.HOME, false) }
            )
        }

        composable(Routes.FLASH_AK3) {
            FlashAK3Screen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REBOOT) {
            RebootDialog(
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}
