package ru.rezzo.academy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.rezzo.academy.AppContainer
import ru.rezzo.academy.feature.car.CarScreen
import ru.rezzo.academy.feature.kb.PartScreen
import ru.rezzo.academy.feature.kb.SystemScreen
import ru.rezzo.academy.feature.kb.SystemsScreen
import ru.rezzo.academy.feature.model.ModelScreen
import ru.rezzo.academy.feature.vehicle.VehicleScreen
import ru.rezzo.academy.ui.theme.RezzoColors

private data class Tab(val route: String, val title: String, val icon: ImageVector, val owns: List<String>)

private val tabs = listOf(
    Tab("car", "Автомобиль", Icons.Filled.Build, listOf("car")),
    Tab("systems", "Системы", Icons.AutoMirrored.Filled.List, listOf("systems", "system/{id}", "part/{id}")),
    Tab("vehicle", "Мой Rezzo", Icons.Filled.Settings, listOf("vehicle", "model")),
)

@Composable
fun RezzoNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Scaffold(
        containerColor = RezzoColors.Graphite,
        bottomBar = {
            NavigationBar(containerColor = RezzoColors.Surface1, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route != null && route in tab.owns,
                        onClick = { nav.goTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RezzoColors.Accent,
                            selectedTextColor = RezzoColors.Accent,
                            indicatorColor = RezzoColors.AccentDim,
                            unselectedIconColor = RezzoColors.TextDim,
                            unselectedTextColor = RezzoColors.TextDim,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "car", modifier = Modifier.padding(padding)) {
            composable("car") {
                CarScreen(
                    container = container,
                    onOpenPart = { nav.navigate("part/$it") },
                    onOpenModel = { nav.navigate("model") },
                )
            }
            composable("systems") {
                SystemsScreen(container, onOpenSystem = { nav.navigate("system/$it") })
            }
            composable("system/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                SystemScreen(
                    container = container,
                    systemId = e.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenPart = { nav.navigate("part/$it") },
                )
            }
            composable("part/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                PartScreen(
                    container = container,
                    partId = e.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenPart = { nav.navigate("part/$it") },
                    onShowOnModel = { partId ->
                        container.focusRequest.value = partId
                        nav.goTab("car")
                    },
                )
            }
            composable("vehicle") {
                VehicleScreen(container, onOpenModel = { nav.navigate("model") })
            }
            composable("model") {
                ModelScreen(container, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
