package com.earlstore.subforge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.earlstore.subforge.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // App header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(RedPrimary, RedDark)))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎬", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("SubForge", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("v1.0.0", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "AI-Powered Subtitle Generator",
                            color = GoldAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Earl Store branding
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🏪", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Earl Store", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Developed & Published by Earl Store",
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Building innovative open-source Android applications\nfor the global community 🌍",
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // What it does
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("🎯 What SubForge Does", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SubForge automatically generates subtitle files from any video using AI speech recognition. " +
                        "Perfect for content creators, filmmakers, and anyone who needs subtitles quickly.",
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Features
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("✨ Features", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))

                    FeatureRow(Icons.Default.AutoAwesome, "AI Speech Recognition", "Accurate speech-to-text conversion")
                    FeatureRow(Icons.Default.Edit, "Built-in Editor", "Edit, merge & delete subtitles")
                    FeatureRow(Icons.Default.FileDownload, "Export SRT & VTT", "Industry-standard formats")
                    FeatureRow(Icons.Default.Language, "20 Languages", "Multi-language support")
                    FeatureRow(Icons.Default.Speed, "Fast Processing", "Quick subtitle generation")
                    FeatureRow(Icons.Default.Share, "Easy Sharing", "Share subtitles directly")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tech Stack
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("🛠️ Tech Stack", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))

                    TechRow("Language", "Kotlin 2.0")
                    TechRow("UI Framework", "Jetpack Compose + Material 3")
                    TechRow("Speech Engine", "Android SpeechRecognizer")
                    TechRow("Translation", "Google ML Kit (Offline)")
                    TechRow("Min SDK", "26 (Android 8.0)")
                    TechRow("Target SDK", "35 (Android 15)")
                    TechRow("Architecture", "MVVM + Foreground Service")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Open Source
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(
                    Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📖 Open Source", fontWeight = FontWeight.Bold, color = GoldAccent, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This app is open source under the MIT License.\n" +
                        "Contributions, issues, and feature requests are welcome!",
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Made with ❤️ by Earl Store",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
