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
    data object Dualis : Screen("dualis")
    data object Moodle : Screen("moodle")
    data object MoodleLogin : Screen("moodle_login")
    data object MoodleCourseDetail : Screen("moodle_course_detail/{courseId}")
    data object MoodleMaterial : Screen("moodle_material/{contentId}?url={url}&title={title}&type={type}")
    data object Nextcloud : Screen("nextcloud")
    data object NextcloudViewer : Screen("nextcloud_viewer?path={path}&name={name}")
}
