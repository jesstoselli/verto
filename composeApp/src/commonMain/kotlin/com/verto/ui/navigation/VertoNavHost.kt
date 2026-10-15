package com.verto.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.verto.ui.account.AccountScreen
import com.verto.ui.editor.AnchorEditorScreen
import com.verto.ui.editor.RoutineEditorScreen
import com.verto.ui.editor.TaskEditorScreen
import com.verto.ui.inbox.InboxScreen
import com.verto.ui.plan.PlanScreen
import com.verto.ui.today.TodayScreen
import kotlin.reflect.KClass

private enum class Tab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector) {
    Today(TodayRoute, TodayRoute::class, "Hoje", Icons.Filled.Home),
    Inbox(InboxRoute, InboxRoute::class, "Caçamba", Icons.Filled.Create),
    Plan(PlanRoute, PlanRoute::class, "Planejar", Icons.AutoMirrored.Filled.List),
}

@Composable
fun VertoNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showTabs = Tab.entries.any { destination?.hasRoute(it.routeClass) == true }

    // Editors bring their own Scaffold; tab screens pad the status bar themselves.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hasRoute(tab.routeClass) == true,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding),
            // Low-stimulus: screens swap without motion.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<TodayRoute> { TodayScreen(onOpenTask = { nav.navigate(TaskEditorRoute(it)) }) }
            composable<InboxRoute> {
                InboxScreen(onMakeRoutine = { inboxId -> nav.navigate(RoutineEditorRoute(fromInboxId = inboxId)) })
            }
            composable<PlanRoute> {
                PlanScreen(
                    onOpenTask = { nav.navigate(TaskEditorRoute(it)) },
                    onOpenRoutine = { nav.navigate(RoutineEditorRoute(routineId = it)) },
                    onOpenAnchor = { nav.navigate(AnchorEditorRoute(it)) },
                    onOpenAccount = { nav.navigate(AccountRoute) },
                )
            }
            composable<TaskEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<TaskEditorRoute>()
                TaskEditorScreen(route.taskId, onClose = { nav.popBackStack() })
            }
            composable<RoutineEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<RoutineEditorRoute>()
                RoutineEditorScreen(
                    routineId = route.routineId,
                    fromInboxId = route.fromInboxId,
                    onNewAnchor = { nav.navigate(AnchorEditorRoute()) },
                    onClose = { nav.popBackStack() },
                )
            }
            composable<AnchorEditorRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AnchorEditorRoute>()
                AnchorEditorScreen(route.anchorId, onClose = { nav.popBackStack() })
            }
            composable<AccountRoute> { AccountScreen(onClose = { nav.popBackStack() }) }
        }
    }
}
