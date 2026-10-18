package com.studioguu.miniaturegarden.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.studioguu.miniaturegarden.BuildConfig
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.domain.model.GardenFeature
import com.studioguu.miniaturegarden.domain.model.GardenItem
import com.studioguu.miniaturegarden.domain.model.GridCell
import com.studioguu.miniaturegarden.domain.model.GridObject
import com.studioguu.miniaturegarden.domain.model.InventoryFilter
import com.studioguu.miniaturegarden.domain.model.InventorySlot
import com.studioguu.miniaturegarden.domain.model.NotificationAccess
import com.studioguu.miniaturegarden.domain.model.PlantStatus
import com.studioguu.miniaturegarden.domain.model.PlantSurroundings
import com.studioguu.miniaturegarden.domain.model.StructureEffect
import com.studioguu.miniaturegarden.domain.model.TutorialStep
import com.studioguu.miniaturegarden.domain.model.WeatherType
import com.studioguu.miniaturegarden.ui.collection.CollectionDialog
import com.studioguu.miniaturegarden.ui.components.GardenCanvas
import com.studioguu.miniaturegarden.ui.components.GardenToastHost
import com.studioguu.miniaturegarden.ui.components.GardenToastState
import com.studioguu.miniaturegarden.ui.components.RewardConfirmDialog
import com.studioguu.miniaturegarden.ui.components.WoodButton
import com.studioguu.miniaturegarden.ui.components.findActivity
import com.studioguu.miniaturegarden.ui.components.pressScale
import com.studioguu.miniaturegarden.ui.components.withTick
import com.studioguu.miniaturegarden.ui.debug.DebugMenuDialog
import com.studioguu.miniaturegarden.ui.effects.FloatingEffectsHost
import com.studioguu.miniaturegarden.ui.effects.RainOverlay
import com.studioguu.miniaturegarden.ui.effects.GardenBackdrop
import com.studioguu.miniaturegarden.ui.hud.LevelUpDialog
import com.studioguu.miniaturegarden.ui.hud.ObjectStatusDialog
import com.studioguu.miniaturegarden.ui.hud.QuickActionBar
import com.studioguu.miniaturegarden.ui.hud.RemoveConfirmDialog
import com.studioguu.miniaturegarden.ui.hud.TopStatusBar
import com.studioguu.miniaturegarden.ui.inventory.InventoryBottomSheet
import com.studioguu.miniaturegarden.ui.notifications.NotificationInviteDialog
import com.studioguu.miniaturegarden.ui.notifications.hasNotificationPermission
import com.studioguu.miniaturegarden.ui.notifications.rememberNotificationPermissionRequest
import com.studioguu.miniaturegarden.ui.onboarding.FeatureRevealCard
import com.studioguu.miniaturegarden.ui.post.PostDialog
import com.studioguu.miniaturegarden.ui.onboarding.RevealBadge
import com.studioguu.miniaturegarden.ui.onboarding.StarterCrateDialog
import com.studioguu.miniaturegarden.ui.onboarding.TutorialDoneDialog
import com.studioguu.miniaturegarden.ui.onboarding.revealGlow
import com.studioguu.miniaturegarden.ui.onboarding.TutorialHint
import com.studioguu.miniaturegarden.ui.onboarding.tutorialHint
import com.studioguu.miniaturegarden.ui.render.Occlusion
import com.studioguu.miniaturegarden.ui.settings.SettingsDialog
import com.studioguu.miniaturegarden.ui.share.CAMERA_MARGIN
import com.studioguu.miniaturegarden.ui.share.CAMERA_RESERVED_BOTTOM
import com.studioguu.miniaturegarden.ui.share.CameraFrame
import com.studioguu.miniaturegarden.ui.share.CameraOverlay
import com.studioguu.miniaturegarden.ui.share.cropToFrame
import com.studioguu.miniaturegarden.ui.share.shareGardenImage
import com.studioguu.miniaturegarden.ui.shop.ShopDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often plants are advanced while the garden is on screen. */
private const val SYNC_INTERVAL_MILLIS = 30_000L

@Composable
fun GardenScreen(viewModel: GardenViewModel = hiltViewModel()) {
    val gridObjects by viewModel.gridObjects.collectAsStateWithLifecycle()
    val selectedTile by viewModel.selectedTile.collectAsStateWithLifecycle()
    val selectedObject by viewModel.selectedObject.collectAsStateWithLifecycle()
    val placingSlot by viewModel.placingSlot.collectAsStateWithLifecycle()
    val placingFlipped by viewModel.placingFlipped.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val atlas by viewModel.atlas.collectAsStateWithLifecycle()
    val terrain by viewModel.terrain.collectAsStateWithLifecycle()
    val effects by viewModel.effects.collectAsStateWithLifecycle()
    val rainRemaining by viewModel.rainRemaining.collectAsStateWithLifecycle()
    val adReady by viewModel.adReady.collectAsStateWithLifecycle()
    val blessedRainFalling by viewModel.blessedRainFalling.collectAsStateWithLifecycle()
    val collectionReady by viewModel.collectionReady.collectAsStateWithLifecycle()
    val hoeActive by viewModel.hoeActive.collectAsStateWithLifecycle()
    val gardenMap by viewModel.gardenMap.collectAsStateWithLifecycle()
    val notificationInviteDue by viewModel.notificationInviteDue.collectAsStateWithLifecycle()
    val visitors by viewModel.visitors.collectAsStateWithLifecycle()
    val movingObject by viewModel.movingObject.collectAsStateWithLifecycle()
    val tutorialStep by viewModel.tutorialStep.collectAsStateWithLifecycle()
    val hintCells by viewModel.hintCells.collectAsStateWithLifecycle()
    val openingCrate by viewModel.openingCrate.collectAsStateWithLifecycle()
    val season by viewModel.season.collectAsStateWithLifecycle()
    val letters by viewModel.letters.collectAsStateWithLifecycle()
    val inventorySlots by viewModel.inventorySlots.collectAsStateWithLifecycle()
    val savedView by viewModel.savedView.collectAsStateWithLifecycle()
    val inventorySort by viewModel.inventorySort.collectAsStateWithLifecycle()
    val levelUp by viewModel.levelUp.collectAsStateWithLifecycle()

    var showInventory by rememberSaveable { mutableStateOf(false) }
    // The bag's tab is kept while the app is open; its sort order is kept in the settings.
    var inventoryFilter by rememberSaveable { mutableStateOf(InventoryFilter.ALL) }
    var showShop by rememberSaveable { mutableStateOf(false) }
    var showDebugMenu by rememberSaveable { mutableStateOf(false) }
    var showPost by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCollection by rememberSaveable { mutableStateOf(false) }
    var confirmRain by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    // Camera mode: nothing on screen but the garden, a viewfinder and a shutter.
    var cameraMode by rememberSaveable { mutableStateOf(false) }
    var shots by remember { mutableStateOf(0) }
    val toast = remember { GardenToastState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Everything drawn inside the garden box below is also recorded into this layer, which is
    // what a shared screenshot is made from: the garden and the sky, none of the controls.
    val gardenLayer = rememberGraphicsLayer()

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
        viewModel.messages.collect { toast.show(context.getString(it)) }
    }
    LaunchedEffect(viewModel) {
        viewModel.seasonArrived.collect { season ->
            toast.show(context.getString(R.string.msg_season_arrived, seasonEmoji(season), context.getString(seasonLabel(season))))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    gardenLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(gardenLayer)
                }
        ) {
            // Sky, hills and meadow behind the garden. Inside the recorded box, so a shared
            // picture has them too.
            GardenBackdrop(weather, Modifier.fillMaxSize(), season = season)
            // The reach of the selected building's effect, if it has one and is finished.
            val aura = remember(selectedObject) {
                selectedObject?.takeUnless { it.isWorking }
                    ?.let { building -> StructureEffect.forObjectId(building.objectId)?.cellsInReach(building.gridX, building.gridY) }
                    ?: emptySet()
            }
            GardenCanvas(
                gridObjects = gridObjects,
                // In camera mode the garden is only looked at: no selection, no grid, and taps
                // do nothing, though it can still be dragged and pinched into place.
                selectedTile = if (cameraMode) null else selectedTile,
                onTileTapped = { tile -> if (!cameraMode) viewModel.onTileTapped(tile) },
                // While a cell is being looked for, taps read the ground only.
                editing = placingSlot != null || hoeActive || movingObject != null,
                atlas = atlas,
                terrain = terrain,
                gardenMap = gardenMap,
                weather = weather,
                showGrid = !cameraMode,
                auraCells = if (cameraMode) emptySet() else aura,
                hintCells = if (cameraMode) emptySet() else hintCells,
                // The visitors stay in camera mode: they belong in a picture of the garden.
                visitors = visitors,
                savedView = savedView,
                onViewChanged = viewModel::saveView,
                paused = showInventory,
                season = season,
                // Buildings turn see-through while a cell is being looked for (placing, moving,
                // the hoe), or when the selected cell is behind one of them.
                fadedObjectIds = remember(gridObjects, selectedTile, placingSlot, hoeActive, movingObject, cameraMode) {
                    if (cameraMode) {
                        emptySet()
                    } else {
                        Occlusion.fadedBuildings(
                            gridObjects,
                            selectedTile?.let { GridCell(it.x, it.y) },
                            editing = placingSlot != null || hoeActive || movingObject != null,
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Inside the recorded box, so a shared picture shows the rain too.
            if (weather == WeatherType.RAINY || blessedRainFalling) RainOverlay(Modifier.fillMaxSize())
        }

        if (cameraMode) {
            CameraOverlay(
                shots = shots,
                onShutter = {
                    scope.launch {
                        // The picture is the garden box as it is drawn, cut to the viewfinder:
                        // the frame, the dimming and the buttons are not part of that box.
                        val image = gardenLayer.toImageBitmap().asAndroidBitmap()
                        val frame = with(density) {
                            CameraFrame.rect(
                                image.width.toFloat(), image.height.toFloat(),
                                CAMERA_MARGIN.toPx(), CAMERA_RESERVED_BOTTOM.toPx(),
                            )
                        }
                        shots++
                        val shared = shareGardenImage(context, cropToFrame(image, frame))
                        if (!shared) toast.show(context.getString(R.string.share_failed))
                    }
                },
                onClose = { cameraMode = false },
            )
            GardenToastHost(
                state = toast,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 24.dp, start = 24.dp, end = 24.dp),
            )
        } else {
            TopStatusBar(
                coins = profile?.coins ?: 0L,
                playerLevel = profile?.playerLevel ?: 1,
                currentXp = profile?.currentXp ?: 0L,
                weather = weather,
                season = season,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp),
            )

            // Greyed out when today's rains are used up or none is ready to be shown. Put away
            // during the first steps, and handed over after them.
            RevealedFeature(
                visible = tutorialStep?.reveals(GardenFeature.BLESSED_RAIN),
                introducing = tutorialStep?.introducing == GardenFeature.BLESSED_RAIN,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 58.dp),
            ) {
                WoodButton(
                    text = stringResource(R.string.reward_rain_button),
                    onClick = {
                        if (viewModel.hasGrowingPlants()) confirmRain = true else viewModel.showMessage(R.string.msg_no_growing_plants)
                    },
                    enabled = rainRemaining > 0 && adReady,
                )
            }

            Column(
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 112.dp, end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SideButton("⚙️", stringResource(R.string.settings_title)) { showSettings = true }
                RevealedFeature(
                    visible = tutorialStep?.reveals(GardenFeature.COLLECTION),
                    introducing = tutorialStep?.introducing == GardenFeature.COLLECTION,
                ) {
                    Box {
                        SideButton("📖", stringResource(R.string.collection_title)) { showCollection = true }
                        // Pages waiting to be filled in.
                        if (collectionReady > 0) {
                            CountBadge(collectionReady, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
                        }
                    }
                }
                RevealedFeature(
                    visible = tutorialStep?.reveals(GardenFeature.CAMERA),
                    introducing = tutorialStep?.introducing == GardenFeature.CAMERA,
                ) {
                    SideButton("📷", stringResource(R.string.camera_button)) {
                        // Whatever was in hand is put down, so that nothing of it is in the picture.
                        viewModel.enterCameraMode()
                        cameraMode = true
                    }
                }
                RevealedFeature(
                    visible = tutorialStep?.reveals(GardenFeature.POST),
                    introducing = tutorialStep?.introducing == GardenFeature.POST,
                ) {
                    Box {
                        SideButton("📮", stringResource(R.string.post_button)) { showPost = true }
                        // Letters not read yet.
                        val unread = letters.count { !it.read }
                        if (unread > 0) {
                            CountBadge(unread, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    SideButton("🛠", stringResource(R.string.debug_menu_title)) { showDebugMenu = true }
                }
            }

            FloatingEffectsHost(
                effects = effects,
                onFinished = viewModel::onEffectFinished,
                modifier = Modifier.align(Alignment.Center),
            )


            // Short messages, laid over the garden below the buttons at the top. They take no room
            // in the layout, so nothing shifts when one comes or goes.
            GardenToastHost(
                state = toast,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 124.dp, start = 24.dp, end = 24.dp),
            )

            // The tool bar sits at the bottom and never moves. Whatever belongs to the current mode
            // (the selected thing's panel, or the banner of the tool in hand) is laid over the garden
            // just above it: one swaps for another by fading, in place, and none of them pushes
            // anything else about.
            var barHeightPx by remember { mutableStateOf(0) }
            val barHeight = with(LocalDensity.current) { barHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                val placing = placingSlot
                val target = selectedObject
                // The panels fade away rather than vanish, so they keep showing the last thing they
                // held while they leave.
                var lastPlacing by remember { mutableStateOf<InventorySlot?>(null) }
                var lastTarget by remember { mutableStateOf<GridObject?>(null) }
                if (placing != null) lastPlacing = placing
                if (target != null) lastTarget = target

                QuickActionBar(
                    modifier = Modifier.onSizeChanged { barHeightPx = it.height },
                    hoeActive = hoeActive,
                    onToggleHoe = viewModel::toggleHoe,
                    canCare = placing == null && !hoeActive && target?.isPlant == true && target.status != PlantStatus.WITHERED,
                    onWater = viewModel::waterSelected,
                    onFertilize = viewModel::fertilizeSelected,
                    onOpenInventory = { showInventory = true },
                    onOpenShop = { showShop = true },
                )
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = barHeight + 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val moving = movingObject
                    var lastMoving by remember { mutableStateOf<GridObject?>(null) }
                    if (moving != null) lastMoving = moving
                    AnimatedVisibility(
                        visible = moving != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        lastMoving?.let {
                            ToolBanner(
                                stringResource(R.string.move_banner, objectDisplayName(it.objectId)),
                                onCancel = viewModel::cancelMoving,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = hoeActive,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        ToolBanner(stringResource(R.string.hoe_banner), onCancel = viewModel::toggleHoe)
                    }
                    AnimatedVisibility(
                        visible = placing != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        lastPlacing?.let {
                            PlacingBanner(it, flipped = placingFlipped, onFlip = viewModel::togglePlacingFlip, onCancel = viewModel::cancelPlacing)
                        }
                    }
                    AnimatedVisibility(
                        visible = placing == null && !hoeActive && moving == null && target != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        lastTarget?.let {
                            ObjectStatusDialog(
                                gridObject = it,
                                onWater = viewModel::waterSelected,
                                onHarvest = viewModel::harvestSelected,
                                onRemove = { confirmRemove = true },
                                onRevive = viewModel::reviveSelected,
                                onMove = viewModel::startMovingSelected,
                                onDismiss = viewModel::clearSelection,
                                onFlip = viewModel::flipSelected,
                                modifier = Modifier.fillMaxWidth(),
                                surroundings = remember(it, gridObjects) { PlantSurroundings.of(it, gridObjects) },
                                gardenSize = gardenMap.size,
                                playerLevel = profile?.playerLevel ?: 1,
                                onExpand = viewModel::expand,
                            )
                        }
                    }
                    // The first steps say what to do next, only while nothing else is showing here.
                    val hint = tutorialHint(tutorialStep)
                    var lastHint by remember { mutableStateOf(hint) }
                    if (hint != null) lastHint = hint
                    AnimatedVisibility(
                        visible = hint != null && placing == null && !hoeActive && moving == null && target == null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        lastHint?.let { TutorialHint(it) }
                    }

                }
            }

            // A feature that has just been handed over: what it is and where it lives, beside the
            // button that lights up for it. Laid last, over the panels above the tool bar. Gone at the player's tap, and the next one follows.
            val introducing = tutorialStep?.introducing
            if (introducing != null) {
                key(introducing) {
                    FeatureRevealCard(
                        feature = introducing,
                        onClose = { viewModel.acknowledgeTutorial(introducing.revealedAt) },
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    )
                }
            }
        }
    }

    if (showInventory) {
        InventoryBottomSheet(
            slots = inventorySlots,
            sort = inventorySort,
            onSortChange = viewModel::setInventorySort,
            filter = inventoryFilter,
            onFilterChange = { inventoryFilter = it },
            season = season,
            onSlotSelected = { slotIndex ->
                viewModel.startPlacing(slotIndex)
                showInventory = false
            },
            onDismiss = { showInventory = false },
        )
    }
    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }
    if (openingCrate != null) {
        StarterCrateDialog(onReceive = viewModel::openCrate, onDismiss = viewModel::closeCrate)
    }
    // The first steps are over: a board that stays until the player goes on.
    if (tutorialStep == TutorialStep.FINISHED && !cameraMode) {
        TutorialDoneDialog(onClose = { viewModel.acknowledgeTutorial(TutorialStep.FINISHED) })
    }
    // A level-up waits on its board until OK; it holds back while another board is up, so two
    // never stack.
    val levelUpNow = levelUp?.takeIf { openingCrate == null && tutorialStep != TutorialStep.FINISHED && !cameraMode }
    if (levelUpNow != null) {
        LevelUpDialog(notice = levelUpNow, onOk = viewModel::acknowledgeLevelUp)
    }
    // Once, after the first steps, and only where the device needs the player's permission.
    val requestNotificationPermission = rememberNotificationPermissionRequest { }
    val inviteNow = notificationInviteDue && openingCrate == null && !cameraMode && levelUpNow == null &&
        NotificationAccess.shouldInvite(Build.VERSION.SDK_INT, context.hasNotificationPermission(), enabledInGame = true, alreadyInvited = false)
    if (inviteNow) {
        NotificationInviteDialog(
            onAccept = {
                viewModel.markNotificationInviteShown()
                requestNotificationPermission()
            },
            onLater = viewModel::markNotificationInviteShown,
        )
    }
    if (confirmRain) {
        RewardConfirmDialog(
            message = stringResource(R.string.reward_rain_confirm),
            confirmLabel = stringResource(R.string.reward_rain_confirm_ok),
            onConfirm = {
                confirmRain = false
                context.findActivity()?.let(viewModel::claimBlessedRain)
            },
            onDismiss = { confirmRain = false },
        )
    }
    // Nothing leaves the garden on a single tap: the choice is made here first.
    val removing = selectedObject
    if (confirmRemove && removing != null) {
        RemoveConfirmDialog(
            gridObject = removing,
            onStore = {
                confirmRemove = false
                viewModel.storeSelected()
            },
            onDiscard = {
                confirmRemove = false
                viewModel.removeSelected()
            },
            onDismiss = { confirmRemove = false },
        )
    }
    // If the selection goes away under the dialog, the question goes with it.
    LaunchedEffect(removing == null) { if (removing == null) confirmRemove = false }
    if (showCollection) {
        CollectionDialog(onDismiss = { showCollection = false })
    }
    if (showDebugMenu) {
        DebugMenuDialog(
            onSkipTime = viewModel::debugSkipTime,
            onCycleWeather = viewModel::debugCycleWeather,
            onBoost = viewModel::debugBoost,
            onNextSeason = viewModel::debugNextSeason,
            onDismiss = { showDebugMenu = false },
        )
    }
    if (showPost) {
        PostDialog(letters = letters, onRead = viewModel::markLetterRead, onDismiss = { showPost = false })
    }
    if (showShop) {
        ShopDialog(onDismiss = { showShop = false })
    }
}

/**
 * A control that is put away during the first steps. It comes in with a small pop when it is
 * handed over, and while it is being introduced it glows and wears a "NEW" tag. [visible] is
 * null while that is not known yet; once it is, a control that is simply there from the start
 * shows at once, without the pop.
 */
@Composable
private fun RevealedFeature(
    visible: Boolean?,
    introducing: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (visible == null) return
    val state = remember { MutableTransitionState(visible) }
    state.targetState = visible
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.revealGlow(introducing)) { content() }
            if (introducing) RevealBadge(Modifier.align(Alignment.TopStart).offset(x = (-10).dp, y = (-10).dp))
        }
    }
}

/**
 * A small red disc with a number in it. The number is centred on the disc itself: the font's
 * extra top padding is left out, which otherwise pushes it below the middle.
 */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(18.dp).background(MaterialTheme.colorScheme.error, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 11.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}

/** One of the small round buttons down the right-hand edge. */
@Composable
private fun SideButton(emoji: String, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = withTick(onClick),
        interactionSource = interactionSource,
        modifier = Modifier.pressScale(interactionSource).semantics { contentDescription = label },
    ) { Text(emoji, fontSize = 22.sp) }
}

/** Shown while an inventory item is waiting to be placed on a tile. */
@Composable
private fun PlacingBanner(slot: InventorySlot, flipped: Boolean, onFlip: () -> Unit, onCancel: () -> Unit) {
    val item = GardenItem.fromItemId(slot.itemId)
    val name = item?.let { itemDisplayName(it) } ?: slot.itemId
    // A building that can face either way is turned here, before it goes down.
    val canFlip = item != null && GridObject.canFaceEitherWay(item.objectId)
    val facing = stringResource(if (flipped) R.string.facing_flipped else R.string.facing_normal)
    val flipDescription = stringResource(R.string.placing_flip_description, facing)
    ToolBanner(stringResource(R.string.placing_banner, name, slot.amount), onCancel) {
        if (canFlip) {
            TextButton(onClick = onFlip, modifier = Modifier.semantics { contentDescription = flipDescription }) {
                Text(stringResource(R.string.placing_flip), fontWeight = if (flipped) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** Says what the next tap on a tile will do, with a way out, and anything else that goes with it ([extra]). */
@Composable
private fun ToolBanner(text: String, onCancel: () -> Unit, extra: @Composable () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            extra()
            TextButton(onClick = onCancel) { Text(stringResource(R.string.placing_cancel)) }
        }
    }
}
