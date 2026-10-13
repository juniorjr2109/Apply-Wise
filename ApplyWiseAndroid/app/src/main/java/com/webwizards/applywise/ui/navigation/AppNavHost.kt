package com.webwizards.applywise.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.webwizards.applywise.ApplyWiseApp
import com.webwizards.applywise.di.AppContainer
import com.webwizards.applywise.ui.account.MyApplicationsScreen
import com.webwizards.applywise.ui.admin.DashboardScreen
import com.webwizards.applywise.ui.apply.ApplyScreen
import com.webwizards.applywise.ui.auth.SignInScreen
import com.webwizards.applywise.ui.home.HomeScreen
import com.webwizards.applywise.ui.results.ResultsScreen
import com.webwizards.applywise.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

/* Type-safe destinations (Navigation Compose 2.8). */
@Serializable object HomeRoute
@Serializable object ApplyRoute
@Serializable data class ResultsRoute(val ref: String)
/** mode: "signin" or "register". next: where to go after success ("dashboard", "my-applications"). */
@Serializable data class SignInRoute(val mode: String = "signin", val reason: String? = null, val next: String? = null)
@Serializable object MyApplicationsRoute
@Serializable object DashboardRoute
@Serializable object SettingsRoute

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as ApplyWiseApp).container

/** Destinations reachable from the Home drawer. */
enum class DrawerDestination { Home, Apply, MyApplications, Dashboard, Settings }

private fun NavHostController.openFromDrawer(d: DrawerDestination) = when (d) {
    DrawerDestination.Home -> Unit
    DrawerDestination.Apply -> navigate(ApplyRoute)
    DrawerDestination.MyApplications -> navigate(MyApplicationsRoute)
    DrawerDestination.Dashboard -> navigate(DashboardRoute)
    DrawerDestination.Settings -> navigate(SettingsRoute)
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigate = { nav.openFromDrawer(it) },
                onOpenResult = { nav.navigate(ResultsRoute(it)) },
                onSignIn = { mode -> nav.navigate(SignInRoute(mode = mode)) }
            )
        }
        composable<ApplyRoute> {
            ApplyScreen(
                onBack = { nav.popBackStack() },
                // Replace the form in the back stack: "Back" from results goes home, not to a submitted form.
                onSubmitted = { ref -> nav.navigate(ResultsRoute(ref)) { popUpTo(HomeRoute) } },
                onSettings = { nav.navigate(SettingsRoute) },
                onSignIn = { nav.navigate(SignInRoute()) }
            )
        }
        composable<ResultsRoute> { entry ->
            ResultsScreen(
                ref = entry.toRoute<ResultsRoute>().ref,
                onBack = { nav.popBackStack() },
                onNewSearch = { nav.navigate(ApplyRoute) { popUpTo(HomeRoute) } }
            )
        }
        composable<SignInRoute> { entry ->
            val route = entry.toRoute<SignInRoute>()
            SignInScreen(
                initialMode = route.mode,
                reason = route.reason,
                onBack = { nav.popBackStack() },
                onSettings = { nav.navigate(SettingsRoute) },
                onSignedIn = { user ->
                    when {
                        route.next == "dashboard" && user.isAdmin -> nav.navigate(DashboardRoute) { popUpTo(HomeRoute) }
                        route.next == "my-applications" -> nav.navigate(MyApplicationsRoute) { popUpTo(HomeRoute) }
                        else -> nav.popBackStack()
                    }
                }
            )
        }
        composable<MyApplicationsRoute> {
            MyApplicationsScreen(
                onBack = { nav.popBackStack() },
                onOpenResult = { nav.navigate(ResultsRoute(it)) },
                onApply = { nav.navigate(ApplyRoute) },
                onSignIn = { nav.navigate(SignInRoute(next = "my-applications")) { popUpTo(HomeRoute) } }
            )
        }
        composable<DashboardRoute> {
            DashboardScreen(
                onSessionEnded = { reason -> nav.navigate(SignInRoute(reason = reason, next = "dashboard")) { popUpTo(HomeRoute) } },
                onSignedOut = { nav.popBackStack(HomeRoute, inclusive = false) },
                onBack = { nav.popBackStack() }
            )
        }
        composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
