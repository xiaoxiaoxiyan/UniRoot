package com.uniroot.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.uniroot.R

sealed class BottomNavItem(
    val route: String,
    val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        label = R.string.app_name,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object SuperUser : BottomNavItem(
        route = "superuser",
        label = R.string.superuser_title,
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security
    )

    data object Module : BottomNavItem(
        route = "module",
        label = R.string.module_title,
        selectedIcon = Icons.Filled.Extension,
        unselectedIcon = Icons.Outlined.Extension
    )

    data object Settings : BottomNavItem(
        route = "settings",
        label = R.string.settings_title,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

object Routes {
    const val HOME = "home"
    const val SUPERUSER = "superuser"
    const val MODULE = "module"
    const val SETTINGS = "settings"
    const val INSTALL = "install"
    const val PROVIDER_SELECT = "provider_select/{category}"
    const val PATCH = "patch/{providerId}"
    const val FLASH_AK3 = "flash_ak3"
    const val KPM_MANAGE = "kpm_manage"
    const val REBOOT = "reboot"
}
