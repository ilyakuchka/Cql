@file:OptIn(ExperimentalMaterial3Api::class)

package me.senkuro.unofficial.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.senkuro.unofficial.data.MangaItem

private object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val LIBRARY = "library"
    const val MANGA = "manga/{id}"
    const val READER = "reader/{mangaId}/{chapterId}"

    fun manga(id: String) = "manga/$id"
    fun reader(mangaId: String, chapterId: String) = "reader/$mangaId/$chapterId"
}

private class TopLevel(val route: String, val title: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, "Главная", Icons.Filled.Home),
    TopLevel(Routes.CATALOG, "Каталог", Icons.Filled.Search),
    TopLevel(Routes.LIBRARY, "Библиотека", Icons.Filled.Favorite),
)

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = topLevel.any { it.route == route }

    val openManga: (MangaItem) -> Unit = { nav.navigate(Routes.manga(it.id)) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) { HomeScreen(onManga = openManga) }
            composable(Routes.CATALOG) { CatalogScreen(onManga = openManga) }
            composable(Routes.LIBRARY) { LibraryScreen(onManga = openManga) }

            composable(
                route = Routes.MANGA,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStack ->
                val id = backStack.arguments?.getString("id").orEmpty()
                DetailsScreen(
                    mangaId = id,
                    onBack = { nav.popBackStack() },
                    onRead = { chapter -> nav.navigate(Routes.reader(id, chapter.id)) },
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
    }
}
