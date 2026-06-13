package dev.dominikstahl.dhbwapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import dev.dominikstahl.dhbwapp.data.local.UserPreferences
import dev.dominikstahl.dhbwapp.data.local.DualisCredentialsManager
import dev.dominikstahl.dhbwapp.data.remote.ApiClient
import dev.dominikstahl.dhbwapp.data.remote.DualisClient
import dev.dominikstahl.dhbwapp.navigation.Screen
import dev.dominikstahl.dhbwapp.navigation.bottomNavItems
import dev.dominikstahl.dhbwapp.ui.dashboard.DashboardScreen
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
import dev.dominikstahl.dhbwapp.ui.dualis.DualisScreen
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

@Composable
fun DhbwApp(httpClient: HttpClient, userPreferences: UserPreferences) {
    val selectedSite by userPreferences.selectedSite.collectAsState(initial = null)
    val selectedCourse by userPreferences.selectedCourse.collectAsState(initial = null)
    val currentUserType by userPreferences.userType.collectAsState(initial = null)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val apiClient = remember { ApiClient(httpClient) }
    val dualisClient = remember { DualisClient(httpClient) }
    val dualisCredentialsManager = remember { DualisCredentialsManager(context) }
    val scope = rememberCoroutineScope()

    val mainRoutes = listOf(Screen.Dashboard.route, Screen.Mensa.route, Screen.Lectures.route, Screen.More.route)
    val detailRoutes = listOf(
        Screen.Parking.route,
        Screen.Rooms.route,
        Screen.Settings.route,
        Screen.Directory.route,
        Screen.EntityTimetable.route,
        Screen.Dualis.route
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
                                         navController.navigate(item.screen.route) {
                                             popUpTo(Screen.Dashboard.route) { saveState = true }
                                             launchSingleTop = true
                                             restoreState = true
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (selectedSite != null) Screen.Dashboard.route else Screen.Onboarding.route,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 800.dp),
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
                composable(Screen.Dashboard.route) {
                    DashboardScreen(
                        apiClient = apiClient,
                        site = selectedSite ?: "",
                        course = selectedCourse ?: "",
                        userType = currentUserType,
                        onNavigateToTimetable = { navController.navigate(Screen.Lectures.route) },
                        onNavigateToMensa = { navController.navigate(Screen.Mensa.route) },
                        onNavigateToParking = { navController.navigate(Screen.Parking.route) },
                        onNavigateToRooms = { navController.navigate(Screen.Rooms.route) }
                    )
                }
                composable(Screen.Mensa.route) {
                    MensaScreen(
                        apiClient = apiClient,
                        site = selectedSite ?: "",
                        userType = currentUserType,
                    )
                }
                composable(Screen.Lectures.route) {
                    LecturesScreen(
                        apiClient = apiClient,
                        course = selectedCourse ?: "",
                        onEntityClick = { type, name ->
                            navController.navigate("entity_timetable/$type/$name")
                        }
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
                        },
                        onDualisClick = {
                            navController.navigate(Screen.Dualis.route)
                        }
                    )
                }
                composable(Screen.Dualis.route) {
                    DualisScreen(
                        dualisClient = dualisClient,
                        credentialsManager = dualisCredentialsManager,
                        onBackClick = { navController.popBackStack() }
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
                        onEntityClick = { type, name ->
                            navController.navigate("entity_timetable/$type/$name")
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        apiClient = apiClient,
                        currentSite = selectedSite,
                        currentUserType = currentUserType,
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
                        onUserTypeSelected = { type ->
                            scope.launch {
                                userPreferences.setSelectedUserType(type)
                            }
                        }
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
                        apiClient = apiClient,
                        site = selectedSite ?: "",
                        type = type,
                        name = name,
                        onBackClick = { navController.popBackStack() },
                        onEntityClick = { entityType, entityName ->
                            navController.navigate("entity_timetable/$entityType/$entityName")
                        }
                    )
                }
            }
        }
    }
}
}