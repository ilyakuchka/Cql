package me.senkuro.unofficial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import me.senkuro.unofficial.data.MangaItem
import me.senkuro.unofficial.debugtools.DebugTools

private object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val LIBRARY = "library"
    const val DEBUG = "debug"
    const val LOGIN = "login"
    const val MANGA = "manga/{id}"
    const val READER = "reader/{mangaId}/{chapterId}"

    fun manga(id: String) = "manga/$id"
    fun reader(mangaId: String, chapterId: String) = "reader/$mangaId/$chapterId"
}

private class TopLevel(val route: String, val title: String, val icon: ImageVector)

// Порядок как в макете: дом, сердце, компас.
private val topLevel = listOf(
    TopLevel(Routes.HOME, "Главная", Icons.Filled.Home),
    TopLevel(Routes.LIBRARY, "Библиотека", Icons.Filled.Favorite),
    TopLevel(Routes.CATALOG, "Каталог", CompassIcon),
)

/** Высота плавающей панели (64) + отступ снизу (16) + запас: на столько экраны поднимают содержимое. */
private val FloatingBarInset = 88.dp

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = topLevel.any { it.route == route }

    val openManga: (MangaItem) -> Unit = { nav.navigate(Routes.manga(it.id)) }
    val navigateTop: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalBottomInset provides if (showBar) FloatingBarInset else 0.dp) {
        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onManga = openManga,
                            onSearch = { navigateTop(Routes.CATALOG) },
                            onMore = { navigateTop(Routes.CATALOG) },
                        )
                    }
                    composable(Routes.CATALOG) { CatalogScreen(onManga = openManga) }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onManga = openManga,
                            onLogin = { nav.navigate(Routes.LOGIN) },
                            // Кнопка есть только в debug-сборке (BuildConfig.DEBUG_TOOLS = false в release).
                            onDebug = if (BuildConfig.DEBUG_TOOLS) ({ nav.navigate(Routes.DEBUG) }) else null,
                        )
                    }
                    composable(Routes.LOGIN) {
                        LoginScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() })
                    }
                    composable(Routes.DEBUG) { DebugTools.Screen(onBack = { nav.popBackStack() }) }

                    composable(
                        route = Routes.MANGA,
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { backStack ->
                        val id = backStack.arguments?.getString("id").orEmpty()
                        DetailsScreen(
                            mangaId = id,
                            onBack = { nav.popBackStack() },
                            onRead = { chapter -> nav.navigate(Routes.reader(id, chapter.id)) },
                            onLogin = { nav.navigate(Routes.LOGIN) },
                        )
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

                if (showBar) {
                    FloatingNavBar(
                        route = route,
                        onSelect = navigateTop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/** Красная плавающая панель-«таблетка» 296×64 с тремя иконками, как в макете. */
@Composable
private fun FloatingNavBar(route: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
            .padding(bottom = 16.dp)
            .widthIn(max = 296.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = AppColors.Accent,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topLevel.forEach { item ->
                val selected = route == item.route
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onSelect(item.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = Color.White.copy(alpha = if (selected) 1f else 0.75f),
                    )
                }
            }
        }
    }
}
