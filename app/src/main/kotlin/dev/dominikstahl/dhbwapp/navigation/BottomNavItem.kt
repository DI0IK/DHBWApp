package dev.dominikstahl.dhbwapp.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
)

val bottomNavItems = listOf(
    BottomNavItem("Mensa", Icons.Default.Restaurant, Screen.Mensa),
    BottomNavItem("Lectures", Icons.Default.School, Screen.Lectures),
    BottomNavItem("More", Icons.Default.MoreHoriz, Screen.More),
)
