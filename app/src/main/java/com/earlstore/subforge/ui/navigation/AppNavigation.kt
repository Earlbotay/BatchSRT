package com.earlstore.subforge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.earlstore.subforge.ui.screens.HomeScreen
import com.earlstore.subforge.ui.screens.EditorScreen
import com.earlstore.subforge.ui.screens.AboutScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("editor") { EditorScreen(navController) }
        composable("about") { AboutScreen(navController) }
    }
}
