package dev.bastian.pixela.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.pixela.ui.editor.EditorScreen
import dev.bastian.pixela.ui.gallery.GalleryScreen
import kotlinx.serialization.Serializable

@Serializable
data object GalleryRoute

@Serializable
data class EditorRoute(val size: Int)

@Composable
fun PixelaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = GalleryRoute) {
        composable<GalleryRoute> {
            GalleryScreen(onNewCanvas = { size -> navController.navigate(EditorRoute(size)) })
        }
        composable<EditorRoute> { entry ->
            EditorScreen(size = entry.toRoute<EditorRoute>().size, onBack = { navController.popBackStack() })
        }
    }
}
