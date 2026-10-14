package com.studioguu.miniaturegarden

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.studioguu.miniaturegarden.ui.GardenScreen
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import com.studioguu.miniaturegarden.ui.title.TitleScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GardenTheme {
                // The title comes first; once in, the garden stays (also across rotation). Going
                // back from the garden leaves the app, as before.
                var inGarden by rememberSaveable { mutableStateOf(false) }
                Crossfade(targetState = inGarden, label = "screen") { garden ->
                    if (garden) GardenScreen() else TitleScreen(onEnterGarden = { inGarden = true })
                }
            }
        }
    }
}
