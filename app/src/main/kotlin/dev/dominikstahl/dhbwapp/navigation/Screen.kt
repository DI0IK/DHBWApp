package dev.dominikstahl.dhbwapp.navigation

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Mensa : Screen("mensa")
    data object Lectures : Screen("lectures")
    data object More : Screen("more")
    data object Onboarding : Screen("onboarding")
    data object Parking : Screen("parking")
    data object Rooms : Screen("rooms")
    data object Settings : Screen("settings")
    data object Directory : Screen("directory")
    data object EntityTimetable : Screen("entity_timetable/{type}/{name}")
}
