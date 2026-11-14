package com.ankitt.thefoodhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ankitt.thefoodhub.ui.FoodHubApp
import com.ankitt.thefoodhub.ui.theme.TheFoodHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TheFoodHubTheme {
                FoodHubApp()
            }
        }
    }
}
