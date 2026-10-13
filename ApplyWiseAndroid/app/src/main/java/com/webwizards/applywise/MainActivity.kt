package com.webwizards.applywise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.webwizards.applywise.ui.navigation.AppNavHost
import com.webwizards.applywise.ui.theme.ApplyWiseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ApplyWiseTheme {
                AppNavHost()
            }
        }
    }
}
