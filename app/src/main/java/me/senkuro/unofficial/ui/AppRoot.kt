package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.senkuro.unofficial.BuildConfig
import me.senkuro.unofficial.data.AppGraph
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.debugtools.DebugTools

private object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val LIBRARY = "library"
    const val DEBUG = "debug"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val MANGA = "manga/{id}"
    const val READER = "reader/{mangaId}/{chapterId}"

    fun manga(id: String) = "manga/$id"
    fun reader(mangaId: String, chapterId: String) = "reader/$mangaId/$chapterId"
}

private class TopLevel(val route: String, val title: String, val icon: ImageVector)

// Порядок: главная, каталог, закладки, настройки.
private val topLevel = listOf(
    TopLevel(Routes.HOME, "Главная", Icons.Filled.Home),
    TopLevel(Routes.CATALOG, "Каталог", CompassIcon),
    TopLevel(Routes.LIBRARY, "Закладки", Icons.Filled.Star),
    TopLevel(Routes.SETTINGS, "Настройки", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val appDark by AppGraph.themeDark.collectAsState()
    val showBar = topLevel.any { it.route == route }

    val openManga: (MangaItem) -> Unit = { nav.navigate(Routes.manga(it.id)) }
    val navigateTop: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalBottomInset provides 0.dp) {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    composable(Routes.HOME) {
                        UnofficialTheme(dark = appDark) {
                            HomeScreen(
                                onManga = openManga,
                                onSearch = { navigateTop(Routes.CATALOG) },
                                onMore = { navigateTop(Routes.CATALOG) },
                                onLogin = { nav.navigate(Routes.LOGIN) },
                                onProfile = { nav.navigate(Routes.PROFILE) },
                                onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                            )
                        }
                    }
                    composable(Routes.CATALOG) { UnofficialTheme(dark = appDark) { CatalogScreen(onManga = openManga) } }
                    composable(Routes.LIBRARY) {
                        UnofficialTheme(dark = appDark) {
                            LibraryScreen(
                                onManga = openManga,
                                onLogin = { nav.navigate(Routes.LOGIN) },
                                // Кнопка есть только в debug-сборке (BuildConfig.DEBUG_TOOLS = false в release).
                                onDebug = if (BuildConfig.DEBUG_TOOLS) ({ nav.navigate(Routes.DEBUG) }) else null,
                            )
                        }
                    }
                    composable(Routes.NOTIFICATIONS) {
                        UnofficialTheme(dark = appDark) {
                            NotificationsScreen(onBack = { nav.popBackStack() }, onManga = openManga)
                        }
                    }
                    composable(Routes.PROFILE) {
                        UnofficialTheme(dark = appDark) { ProfileScreen(onBack = { nav.popBackStack() }) }
                    }
                    composable(Routes.SETTINGS) { UnofficialTheme(dark = appDark) { SettingsScreen() } }
                    composable(Routes.LOGIN) {
                        UnofficialTheme(dark = appDark) {
                            LoginScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
                        }
                    }
                    composable(Routes.DEBUG) {
                        UnofficialTheme(dark = appDark) { DebugTools.Screen(onBack = { nav.popBackStack() }) }
                    }

                    composable(
                        route = Routes.MANGA,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { backStack ->
                        val id = backStack.arguments?.getString("id").orEmpty()
                        UnofficialTheme(dark = true) {
                            DetailsScreen(
                                mangaId = id,
                                onBack = { nav.popBackStack() },
                                onRead = { chapter -> nav.navigate(Routes.reader(id, chapter.id)) },
                                onLogin = { nav.navigate(Routes.LOGIN) },
                            )
                        }
                    }

                    composable(
                        route = Routes.READER,
                        arguments = listOf(
                            navArgument("mangaId") { type = NavType.StringType },
                            navArgument("chapterId") { type = NavType.StringType },
                        ),
                    ) { backStack ->
                        val mangaId = backStack.arguments?.getString("mangaId").orEmpty()
                        val chapterId = backStack.arguments?.getString("chapterId").orEmpty()
                        UnofficialTheme(dark = true) {
                            ReaderScreen(
                                mangaId = mangaId,
                                chapterId = chapterId,
                                onBack = { nav.popBackStack() },
                                onOpenChapter = { chapter ->
                                    nav.navigate(Routes.reader(mangaId, chapter.id)) {
                                        popUpTo(Routes.READER) { inclusive = true }
                                    }
                                },
                            )
                        }
                    }
                }

                if (showBar) BottomBar(route = route, onSelect = navigateTop)
            }
        }
    }
}

/** Нижняя панель в стиле кита: белый фон, линия сверху #E0E0E0, значок и подпись, выбранный пункт тёмный с зелёной чертой. */
@Composable
private fun BottomBar(route: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.8.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topLevel.forEach { item ->
                val selected = route == item.route
                val tint = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(item.icon, contentDescription = item.title, tint = tint, modifier = Modifier.size(22.dp))
                    Text(item.title, color = tint, fontSize = 10.sp)
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .width(16.dp)
                            .height(2.dp)
                            .background(if (selected) AppColors.Accent else Color.Transparent),
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
