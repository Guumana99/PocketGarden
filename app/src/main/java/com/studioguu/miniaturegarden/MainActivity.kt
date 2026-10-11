package com.studioguu.miniaturegarden

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.studioguu.miniaturegarden.ui.GardenScreen
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GardenTheme {
                GardenScreen()
            }
        }
    }
}
