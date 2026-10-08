package com.baer.hado.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.baer.hado.ui.home.HomeScreen
import com.baer.hado.ui.login.LoginScreen
import com.baer.hado.ui.login.LoginViewModel
import com.baer.hado.ui.settings.AppSettingsScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ADD_SERVER = "add_server?${LoginViewModel.ARG_ADD_SERVER}={${LoginViewModel.ARG_ADD_SERVER}}"
    const val ADD_SERVER_NAV = "add_server?${LoginViewModel.ARG_ADD_SERVER}=true"
}

/** Restarts the home screen so it loads the newly active server. */
fun NavHostController.reopenHome() {
    navigate(Routes.HOME) {
        popUpTo(graph.id) { inclusive = true }
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(
            Routes.ADD_SERVER,
            arguments = listOf(navArgument(LoginViewModel.ARG_ADD_SERVER) {
                type = NavType.BoolType
                defaultValue = true
            })
        ) {
            LoginScreen(
                onAuthenticated = { navController.reopenHome() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onAccountChanged = { navController.reopenHome() },
                onAddServer = { navController.navigate(Routes.ADD_SERVER_NAV) }
            )
        }
        composable(Routes.SETTINGS) {
            AppSettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onAccountChanged = { navController.reopenHome() },
                onAddServer = { navController.navigate(Routes.ADD_SERVER_NAV) }
            )
        }
    }
}
