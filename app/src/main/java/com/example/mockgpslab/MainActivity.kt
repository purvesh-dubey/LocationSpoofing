package com.example.mockgpslab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mockgpslab.ui.MockGpsLabApp
import com.example.mockgpslab.ui.theme.MockGpsLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MockGpsLabTheme {
                MockGpsLabApp()
            }
        }
    }
}
