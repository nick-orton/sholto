package com.nickorton.sholto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nickorton.sholto.ui.home.HomeScreen
import com.nickorton.sholto.ui.theme.SholtoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SholtoTheme {
                HomeScreen()
            }
        }
    }
}
