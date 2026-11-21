package com.studioguu.miniaturegarden

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.studioguu.miniaturegarden.ui.GardenScreen
import com.studioguu.miniaturegarden.ui.theme.FixedFontScale
import com.studioguu.miniaturegarden.ui.theme.GardenTheme
import com.studioguu.miniaturegarden.ui.title.TitleScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // The game's words keep their size whatever the device's type setting (FixedFontScale).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FixedFontScale.wrap(newBase))
    }

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
                UprightColumn {
                    Crossfade(targetState = inGarden, label = "screen") { garden ->
                        if (garden) GardenScreen() else TitleScreen(onEnterGarden = { inGarden = true })
                    }
                }
            }
        }
    }
}

/**
 * Android 16 ignores the portrait lock on windows 600dp wide or more (tablets, foldables,
 * split screen), so the game can be handed a landscape window. Then the portrait screen sits in
 * the middle at a phone's shape and the sides are filled with the dark wood. On a phone, or in a
 * portrait window, the content simply fills it.
 */
@Composable
internal fun UprightColumn(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(GardenColors.WoodDark)) {
        val wide = maxWidth >= 600.dp && maxWidth.value / maxHeight.value > MAX_PORTRAIT_ASPECT
        if (wide) {
            Box(
                Modifier.align(Alignment.Center).fillMaxHeight().width(maxHeight * UPRIGHT_ASPECT)
                    // The bars at the sides of the window are not this column's.
                    .consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) { content() }
        } else {
            content()
        }
    }
}

private const val UPRIGHT_ASPECT = 9f / 19.5f
private const val MAX_PORTRAIT_ASPECT = 0.75f
