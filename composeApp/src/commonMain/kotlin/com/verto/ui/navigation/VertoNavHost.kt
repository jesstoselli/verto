package com.verto.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.verto.ui.settings.SettingsScreen
import com.verto.ui.today.TodayScreen
import kotlin.reflect.KClass

internal enum class PrimaryTab(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector) {
    Today(TodayRoute, TodayRoute::class, "Agora", Icons.Filled.Home),
    Inbox(InboxRoute, InboxRoute::class, "Rascunho", Icons.Filled.Create),
    Plan(PlanRoute, PlanRoute::class, "Trajeto", Icons.AutoMirrored.Filled.List),
}

@Composable
fun VertoNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val showTabs = PrimaryTab.entries.any { destination?.hasRoute(it.routeClass) == true }

    // Editors bring their own Scaffold; tab screens pad the status bar themselves.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    PrimaryTab.entries.forEach { tab ->
                        val selected = destination?.hasRoute(tab.routeClass) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(tab.icon, contentDescription = null)
                                    Spacer(Modifier.height(3.dp))
                                    Box(
                                        Modifier
                                            .width(18.dp)
                                            .height(2.dp)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                CircleShape,
                                            ),
                                    )
                                }
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
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
                    onOpenSettings = { nav.navigate(SettingsRoute) },
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
            composable<SettingsRoute> {
                SettingsScreen(onOpenAccount = { nav.navigate(AccountRoute) }, onClose = { nav.popBackStack() })
            }
        }
    }
}
