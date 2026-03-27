package com.earlstore.batchsrt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.earlstore.batchsrt.ui.navigation.AppNavigation
import com.earlstore.batchsrt.ui.theme.BatchSRTTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BatchSRTTheme {
                AppNavigation()
            }
        }
    }
}
