package com.earlstore.batchsrt.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.earlstore.batchsrt.ui.screens.HomeScreen
import com.earlstore.batchsrt.ui.screens.ProcessingScreen
import com.earlstore.batchsrt.ui.screens.AboutScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("processing") { ProcessingScreen(navController) }
        composable("about") { AboutScreen(navController) }
    }
}
