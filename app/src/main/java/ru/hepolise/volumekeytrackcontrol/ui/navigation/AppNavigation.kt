package ru.hepolise.volumekeytrackcontrol.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ru.hepolise.volumekeytrackcontrol.ui.LocalXposedService
import ru.hepolise.volumekeytrackcontrol.ui.screen.AppFilterScreen
import ru.hepolise.volumekeytrackcontrol.ui.screen.AppProfilePickerScreen
import ru.hepolise.volumekeytrackcontrol.ui.screen.AppProfileScreen
import ru.hepolise.volumekeytrackcontrol.ui.screen.AppProfilesScreen
import ru.hepolise.volumekeytrackcontrol.ui.screen.SettingsScreen
import ru.hepolise.volumekeytrackcontrol.util.AppFilterType
import ru.hepolise.volumekeytrackcontrol.util.SharedPreferencesUtil.getSettingsSharedPreferences

@Composable
fun AppNavigation(
    isHooked: Boolean,
    onRefresh: () -> Unit = {}
) {
    val navController = rememberNavController()
    val xposedService = LocalXposedService.current

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable(
            route = "main",
        ) {
            SettingsScreen(isHooked, navController, onRefresh)
        }

        xposedService?.getSettingsSharedPreferences()?.also { sharedPreferences ->
            composable(
                route = "appFilter/{filterType}",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 300)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 300)
                    )
                }
            ) { backStackEntry ->
                val filterType = AppFilterType.fromKey(
                    backStackEntry.arguments?.getString("filterType")
                )
                AppFilterScreen(
                    filterType = filterType,
                    sharedPreferences = sharedPreferences,
                    navController = navController
                )
            }

            composable(route = "appProfiles") {
                AppProfilesScreen(
                    sharedPreferences = sharedPreferences,
                    navController = navController
                )
            }

            composable(route = "appProfilePicker") {
                AppProfilePickerScreen(
                    sharedPreferences = sharedPreferences,
                    navController = navController
                )
            }

            composable(route = "appProfile/{packageName}") { backStackEntry ->
                AppProfileScreen(
                    packageName = backStackEntry.arguments?.getString("packageName").orEmpty(),
                    sharedPreferences = sharedPreferences,
                    navController = navController
                )
            }
        }
    }
}