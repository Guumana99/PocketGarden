package com.studioguu.miniaturegarden.ui.title

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.components.GardenCanvas
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.popIn
import com.studioguu.miniaturegarden.ui.effects.GardenBackdrop
import com.studioguu.miniaturegarden.ui.theme.GardenColors
import kotlinx.coroutines.delay

/** How long one beat of the sample garden's growing lasts. */
private const val SAMPLE_BEAT_MILLIS = 1_200L

/**
 * The first thing the app shows: the sky and meadow behind the garden, a wooden sign with the
 * name, a small live garden drawn by the real renderer, and the two ways in.
 *
 * "Start" makes a new garden; when one is saved already it asks first, since that garden is
 * then thrown away. "Continue" opens the saved garden and is greyed out when there is none.
 *
 * @param onEnterGarden called once the garden is ready to be shown.
 */
@Composable
fun TitleScreen(onEnterGarden: () -> Unit, viewModel: TitleViewModel = hiltViewModel()) {
    val hasSavedGarden by viewModel.hasSavedGarden.collectAsStateWithLifecycle()
    val atlas by viewModel.atlas.collectAsStateWithLifecycle()
    val terrain by viewModel.terrain.collectAsStateWithLifecycle()
    var confirmStartOver by rememberSaveable { mutableStateOf(false) }

    // The sample garden's flowers grow a step at every beat.
    val beat by produceState(0) {
        while (true) {
            delay(SAMPLE_BEAT_MILLIS)
            value++
        }
    }
    val sample = remember(beat) { TitleSampleGarden.objects(beat) }

    Box(modifier = Modifier.fillMaxSize()) {
        GardenBackdrop(WeatherType.SUNNY, Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            TitleSign()
            val sampleLabel = stringResource(R.string.title_sample_garden)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { contentDescription = sampleLabel },
            ) {
                GardenCanvas(
                    gridObjects = sample,
                    selectedTile = null,
                    onTileTapped = {},
                    atlas = atlas,
                    terrain = terrain,
                    showGrid = false,
                    weather = WeatherType.SUNNY,
                    modifier = Modifier.fillMaxSize(),
                )
                // Laid over the sample so that it cannot be dragged, pinched or tapped: it is
                // only to be looked at.
                Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 40.dp),
            ) {
                val known = hasSavedGarden != null
                WoodButton(
                    text = stringResource(R.string.title_start),
                    onClick = { if (hasSavedGarden == true) confirmStartOver = true else onEnterGarden() },
                    enabled = known,
                    modifier = Modifier.width(220.dp),
                )
                WoodButton(
                    text = stringResource(R.string.title_continue),
                    onClick = onEnterGarden,
                    enabled = hasSavedGarden == true,
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }

    if (confirmStartOver) {
        AlertDialog(
            modifier = Modifier.popIn(),
            onDismissRequest = { confirmStartOver = false },
            title = { Text(stringResource(R.string.title_reset_title)) },
            text = { Text(stringResource(R.string.title_reset_text)) },
            confirmButton = {
                WoodButton(
                    text = stringResource(R.string.title_reset_ok),
                    onClick = {
                        confirmStartOver = false
                        viewModel.startOver(onEnterGarden)
                    },
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmStartOver = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** The name on a wooden sign that sways gently, as if hung from a branch. */
@Composable
private fun TitleSign() {
    val sway by rememberInfiniteTransition(label = "sign").animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway",
    )
    Surface(
        modifier = Modifier.graphicsLayer {
            rotationZ = sway
            transformOrigin = TransformOrigin(0.5f, 0f)
        },
        shape = RoundedCornerShape(18.dp),
        color = GardenColors.Wood,
        border = BorderStroke(3.dp, GardenColors.WoodDark),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🌷 " + stringResource(R.string.app_name) + " 🌱",
                color = GardenColors.Cream,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.title_subtitle),
                color = GardenColors.WoodLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
        }
    }
}
