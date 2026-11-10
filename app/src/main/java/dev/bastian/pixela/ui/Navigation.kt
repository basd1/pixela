package dev.bastian.pixela.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.pixela.ui.gallery.GalleryScreen
import kotlinx.serialization.Serializable

@Serializable
data object GalleryRoute

@Serializable
data class EditorRoute(val projectId: String)

@Composable
fun PixelaNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = GalleryRoute) {
        composable<GalleryRoute> {
            GalleryScreen()
        }
    }
}
