package com.studioguu.miniaturegarden.ui.title

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
 * How much closer than "all of it on screen" the sample garden is shown. Its far corners run a
 * little off the sides, so that the garden fills the view instead of floating small in it.
 */
private const val SAMPLE_ZOOM = 1.22f

/** How long the sample garden takes to fade in once its artwork has been read. */
private const val SAMPLE_FADE_IN_MILLIS = 250

/**
 * The first thing the app shows: the sky and meadow behind the garden, a wooden sign with the
 * name hanging from above, a large live garden in full swing drawn by the real renderer, and the
 * two ways in. The garden's music plays here already.
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
    val artLoaded by viewModel.artLoaded.collectAsStateWithLifecycle()
    var confirmStartOver by rememberSaveable { mutableStateOf(false) }

    // Music while the title is in front. Leaving for the garden is not leaving the foreground:
    // the tune carries on into the garden.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onForeground()
                Lifecycle.Event.ON_PAUSE -> viewModel.onBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TitleSign(Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp))
            val sampleLabel = stringResource(R.string.title_sample_garden)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { contentDescription = sampleLabel },
            ) {
                // Fades in once the artwork is read, rather than flashing up in stand-in shapes.
                val sampleAlpha by animateFloatAsState(if (artLoaded) 1f else 0f, tween(SAMPLE_FADE_IN_MILLIS), label = "sampleIn")
                if (artLoaded) GardenCanvas(
                    gridObjects = sample,
                    selectedTile = null,
                    onTileTapped = {},
                    atlas = atlas,
                    terrain = terrain,
                    gardenMap = TitleSampleGarden.MAP,
                    showGrid = false,
                    weather = WeatherType.SUNNY,
                    visitors = TitleSampleGarden.visitors,
                    initialZoom = SAMPLE_ZOOM,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = sampleAlpha },
                )
                // Laid over the sample so that it cannot be dragged, pinched or tapped: it is
                // only to be looked at.
                Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 28.dp),
            ) {
                val known = hasSavedGarden != null
                WoodButton(
                    text = stringResource(R.string.title_start),
                    // A new garden, even with none saved, starts from a clean view: no zoom is carried over.
                    onClick = { if (hasSavedGarden == true) confirmStartOver = true else viewModel.startOver(onEnterGarden) },
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
                WoodButton(text = stringResource(R.string.action_cancel), onClick = { confirmStartOver = false })
            },
        )
    }
}

/**
 * The name on a wooden sign hung from above on two ropes, swaying gently as if from a branch.
 * The board is planed wood with its grain showing, a groove routed round its edge and a nail
 * where each rope is tied; the name is set large in cream, as if burnt in and painted over, with
 * the English name below it between two sprigs.
 */
@Composable
private fun TitleSign(modifier: Modifier = Modifier) {
    val sway by rememberInfiniteTransition(label = "sign").animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = sway
                // Swung from where the ropes are tied, well above the board.
                transformOrigin = TransformOrigin(0.5f, -1.2f)
            }
            .drawBehind {
                // The ropes, from the nails up past the top of the screen.
                val rope = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                for (x in listOf(size.width * SIGN_NAIL_X, size.width * (1f - SIGN_NAIL_X))) {
                    drawLine(GardenColors.WoodDark, Offset(x, SIGN_NAIL_Y.toPx()), Offset(x, -size.height), strokeWidth = rope.width, cap = rope.cap)
                }
            },
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GardenColors.Wood,
            border = BorderStroke(3.dp, GardenColors.WoodDark),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier
                    .drawBehind { drawSignBoard() }
                    .padding(start = 34.dp, end = 34.dp, top = 22.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = TextStyle(
                        color = GardenColors.Cream,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                        shadow = Shadow(GardenColors.WoodDark, offset = Offset(0f, 4f), blurRadius = 3f),
                    ),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Sprig(mirrored = false)
                    Text(
                        text = stringResource(R.string.title_subtitle).uppercase(),
                        color = GardenColors.WoodLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                    )
                    Sprig(mirrored = true)
                }
            }
        }
    }
}

/** Where the ropes are tied: this far in from each side, and this far down from the top. */
private const val SIGN_NAIL_X = 0.16f
private val SIGN_NAIL_Y = 9.dp

/** The board's grain, the groove round its edge, and the two nails. */
private fun DrawScope.drawSignBoard() {
    val grain = GardenColors.WoodDark.copy(alpha = 0.16f)
    val lines = 7
    for (i in 1 until lines) {
        val y = size.height * i / lines
        val path = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = i % 2 == 0
            while (x < size.width) {
                val step = size.width / 5f
                quadraticTo(x + step / 2f, y + (if (up) -2.5f else 2.5f) * density, x + step, y)
                x += step
                up = !up
            }
        }
        drawPath(path, grain, style = Stroke(width = 1.2f * density))
    }
    val inset = 6f * density
    drawRoundRect(
        color = GardenColors.WoodLight.copy(alpha = 0.45f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(11f * density),
        style = Stroke(width = 1.5f * density),
    )
    for (x in listOf(size.width * SIGN_NAIL_X, size.width * (1f - SIGN_NAIL_X))) {
        val nail = Offset(x, SIGN_NAIL_Y.toPx())
        drawCircle(GardenColors.WoodDark, radius = 3.5f * density, center = nail)
        drawCircle(GardenColors.WoodLight.copy(alpha = 0.7f), radius = 1.4f * density, center = nail - Offset(density, density))
    }
}

/** A small sprig of leaves on a stem, drawn; [mirrored] for the right-hand one. */
@Composable
private fun Sprig(mirrored: Boolean) {
    Canvas(Modifier.size(width = 34.dp, height = 12.dp).graphicsLayer { scaleX = if (mirrored) -1f else 1f }) {
        val mid = size.height / 2f
        drawLine(GardenColors.WoodLight, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
        val leaf = Size(7f * density, 3.6f * density)
        for ((i, x) in listOf(0.38f, 0.62f, 0.86f).withIndex()) {
            val above = i % 2 == 0
            drawOval(
                color = GardenColors.Leaf,
                topLeft = Offset(size.width * x - leaf.width / 2f, if (above) mid - leaf.height - 0.5f * density else mid + 0.5f * density),
                size = leaf,
            )
        }
    }
}
