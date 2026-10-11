package com.studioguu.miniaturegarden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.BuildConfig
import com.studioguu.miniaturegarden.ui.components.GardenCanvas
import com.studioguu.miniaturegarden.ui.debug.DebugMenuDialog
import com.studioguu.miniaturegarden.ui.effects.FloatingEffectsHost
import com.studioguu.miniaturegarden.ui.hud.ObjectStatusDialog
import com.studioguu.miniaturegarden.ui.hud.QuickActionBar
import com.studioguu.miniaturegarden.ui.hud.TopStatusBar
import com.studioguu.miniaturegarden.ui.inventory.InventoryBottomSheet
import com.studioguu.miniaturegarden.ui.shop.ShopDialog
import kotlinx.coroutines.delay

private val SKY = Color(0xFFDCEFF5)

/** How often plants are advanced while the garden is on screen. */
private const val SYNC_INTERVAL_MILLIS = 30_000L

@Composable
fun GardenScreen(viewModel: GardenViewModel = hiltViewModel()) {
    val gridObjects by viewModel.gridObjects.collectAsStateWithLifecycle()
    val selectedTile by viewModel.selectedTile.collectAsStateWithLifecycle()
    val selectedObject by viewModel.selectedObject.collectAsStateWithLifecycle()
    val placingSlot by viewModel.placingSlot.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val atlas by viewModel.atlas.collectAsStateWithLifecycle()
    val effects by viewModel.effects.collectAsStateWithLifecycle()

    var showInventory by rememberSaveable { mutableStateOf(false) }
    var showShop by rememberSaveable { mutableStateOf(false) }
    var showDebugMenu by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Catch up on time away whenever the app returns to the foreground, then keep ticking.
    // Music follows the same window: it plays only while the garden is in front.
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.onForeground()
            try {
                while (true) {
                    viewModel.sync()
                    delay(SYNC_INTERVAL_MILLIS)
                }
            } finally {
                viewModel.onBackground()
            }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(context.getString(it)) }
    }

    Box(modifier = Modifier.fillMaxSize().background(SKY)) {
        GardenCanvas(
            gridObjects = gridObjects,
            selectedTile = selectedTile,
            onTileTapped = viewModel::onTileTapped,
            atlas = atlas,
            gardenMap = viewModel.gardenMap,
            modifier = Modifier.fillMaxSize(),
        )

        TopStatusBar(
            coins = profile?.coins ?: 0L,
            playerLevel = profile?.playerLevel ?: 1,
            currentXp = profile?.currentXp ?: 0L,
            weather = weather,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp),
        )

        if (BuildConfig.DEBUG) {
            TextButton(
                onClick = { showDebugMenu = true },
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 56.dp, end = 4.dp),
            ) { Text("🛠") }
        }

        FloatingEffectsHost(
            effects = effects,
            onFinished = viewModel::onEffectFinished,
            modifier = Modifier.align(Alignment.Center),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SnackbarHost(snackbarHostState)

            val placing = placingSlot
            val target = selectedObject
            if (placing != null) {
                PlacingBanner(placing, onCancel = viewModel::cancelPlacing)
            } else if (target != null) {
                ObjectStatusDialog(
                    gridObject = target,
                    onWater = viewModel::waterSelected,
                    onHarvest = viewModel::harvestSelected,
                    onRemove = viewModel::removeSelected,
                    onDismiss = viewModel::clearSelection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            QuickActionBar(
                canCare = placing == null && target?.isPlant == true && target.status != PlantStatus.WITHERED,
                onWater = viewModel::waterSelected,
                onFertilize = viewModel::fertilizeSelected,
                onOpenInventory = { showInventory = true },
                onOpenShop = { showShop = true },
            )
        }
    }

    if (showInventory) {
        InventoryBottomSheet(
            onSlotSelected = { slotIndex ->
                viewModel.startPlacing(slotIndex)
                showInventory = false
            },
            onDismiss = { showInventory = false },
        )
    }
    if (showDebugMenu) {
        DebugMenuDialog(onSkipTime = viewModel::debugSkipTime, onDismiss = { showDebugMenu = false })
    }
    if (showShop) {
        ShopDialog(onDismiss = { showShop = false })
    }
}

/** Shown while an inventory item is waiting to be placed on a tile. */
@Composable
private fun PlacingBanner(slot: InventorySlot, onCancel: () -> Unit) {
    val name = GardenItem.fromItemId(slot.itemId)?.let { itemDisplayName(it) } ?: slot.itemId
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.placing_banner, name, slot.amount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.placing_cancel)) }
        }
    }
}
