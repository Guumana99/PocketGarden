package com.studioguu.miniaturegarden

import android.os.Bundle
import android.view.WindowManager
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
        // The title's sample garden and the garden itself are things to watch as much as to touch,
        // so the screen must not dim or sleep on either. The window flag holds only while the app is
        // visible and needs no permission; setting it once here also keeps the crossfade between
        // the two screens from switching it off.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
