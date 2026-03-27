package com.earlstore.subforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.earlstore.subforge.ui.navigation.AppNavigation
import com.earlstore.subforge.ui.theme.SubForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubForgeTheme {
                AppNavigation()
            }
        }
    }
}
