package dev.dominikstahl.dhbwapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.dominikstahl.dhbwapp.data.local.UserPreferences
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.navigation.Screen
import dev.dominikstahl.dhbwapp.navigation.bottomNavItems
import dev.dominikstahl.dhbwapp.ui.mensa.MensaScreen
import dev.dominikstahl.dhbwapp.ui.more.MoreScreen
import dev.dominikstahl.dhbwapp.ui.onboarding.OnboardingScreen
import dev.dominikstahl.dhbwapp.ui.parking.ParkingScreen
import dev.dominikstahl.dhbwapp.ui.rooms.RoomAvailabilityScreen
import dev.dominikstahl.dhbwapp.ui.settings.SettingsScreen
import dev.dominikstahl.dhbwapp.ui.theme.DhbwAppTheme
import dev.dominikstahl.dhbwapp.ui.lectures.LecturesScreen
import dev.dominikstahl.dhbwapp.ui.directory.DirectoryScreen
import dev.dominikstahl.dhbwapp.ui.directory.EntityTimetableScreen
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

@Composable
fun DhbwApp(httpClient: HttpClient, userPreferences: UserPreferences) {
    val selectedSite by userPreferences.selectedSite.collectAsState(initial = null)
    val selectedCourse by userPreferences.selectedCourse.collectAsState(initial = null)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val apiClient = remember { ApiClient(httpClient) }
    val calendarRepository = remember {
        dev.dominikstahl.dhbwapp.data.repository.CalendarRepository(
            apiClient = apiClient,
            enrichers = listOf(
                dev.dominikstahl.dhbwapp.data.repository.RaplaScraperCalendarEnricher(httpClient)
            )
        )
    }
    val scope = rememberCoroutineScope()

    val mainRoutes = listOf(Screen.Mensa.route, Screen.Lectures.route, Screen.More.route)
    val detailRoutes = listOf(
        Screen.Parking.route,
        Screen.Rooms.route,
        Screen.Settings.route,
        Screen.Directory.route,
        Screen.EntityTimetable.route
    )
    val showBottomBar = selectedSite != null && currentRoute in mainRoutes + detailRoutes

    DhbwAppTheme {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.screen.route,
                                 onClick = {
                                     if (currentRoute != item.screen.route) {
                                         if (item.screen == Screen.More) {
                                             navController.navigate(Screen.More.route) {
                                                 popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                 launchSingleTop = true
                                             }
                                         } else {
                                             // For other navigation, use original logic
                                             navController.navigate(item.screen.route) {
                                                 popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                 launchSingleTop = true
                                                 restoreState = true
                                             }
                                         }
                                     }
                                 },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (selectedSite != null) Screen.Mensa.route else Screen.Onboarding.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        apiClient = apiClient,
                        onSiteSelected = { site ->
                            scope.launch {
                                userPreferences.setSelectedSite(site)
                            }
                        },
                        onCourseSelected = { course ->
                            scope.launch {
                                userPreferences.setSelectedCourse(course)
                            }
                        },
                    )
                }
                composable(Screen.Mensa.route) {
                    MensaScreen(
                        apiClient = apiClient,
                        site = selectedSite ?: "",
                    )
                }
                composable(Screen.Lectures.route) {
                    LecturesScreen(
                        calendarRepository = calendarRepository,
                        course = selectedCourse ?: "",
                    )
                }
                composable(Screen.More.route) {
                    MoreScreen(
                        onParkingClick = {
                            navController.navigate(Screen.Parking.route)
                        },
                        onRoomsClick = {
                            navController.navigate(Screen.Rooms.route)
                        },
                        onSettingsClick = {
                            navController.navigate(Screen.Settings.route)
                        },
                        onDirectoryClick = {
                            navController.navigate(Screen.Directory.route)
                        }
                    )
                }
                composable(Screen.Parking.route) {
                    ParkingScreen(
                        apiClient = apiClient,
                        site = selectedSite ?: "",
                    )
                }
                composable(Screen.Rooms.route) {
                    RoomAvailabilityScreen(
                        apiClient = apiClient,
                        site = selectedSite ?: "",
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        apiClient = apiClient,
                        currentSite = selectedSite,
                        currentCourse = selectedCourse,
                        onSiteSelected = { site ->
                            scope.launch {
                                userPreferences.setSelectedSite(site)
                                userPreferences.clearSelectedCourse()
                            }
                        },
                        onCourseSelected = { course ->
                            scope.launch {
                                userPreferences.setSelectedCourse(course)
                            }
                        },
                    )
                }
                composable(Screen.Directory.route) {
                    DirectoryScreen(
                        apiClient = apiClient,
                        site = selectedSite ?: "",
                        onBackClick = { navController.popBackStack() },
                        onEntityClick = { type, name ->
                            navController.navigate("entity_timetable/$type/$name")
                        }
                    )
                }
                composable(Screen.EntityTimetable.route) { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type") ?: ""
                    val name = backStackEntry.arguments?.getString("name") ?: ""
                    EntityTimetableScreen(
                        calendarRepository = calendarRepository,
                        site = selectedSite ?: "",
                        type = type,
                        name = name,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}