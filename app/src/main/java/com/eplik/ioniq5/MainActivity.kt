package com.eplik.ioniq5

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eplik.ioniq5.ui.Ioniq5App
import com.eplik.ioniq5.ui.theme.Ioniq5Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Ioniq5Theme {
                Ioniq5App()
            }
        }
    }
}
