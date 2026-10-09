package com.studioguu.miniaturegarden.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studioguu.miniaturegarden.R
import com.studioguu.miniaturegarden.ui.components.GardenCanvas

private val SKY = Color(0xFFDCEFF5)

@Composable
fun GardenScreen(viewModel: GardenViewModel = hiltViewModel()) {
    val gridObjects by viewModel.gridObjects.collectAsStateWithLifecycle()
    val selectedTile by viewModel.selectedTile.collectAsStateWithLifecycle()
    val atlas by viewModel.atlas.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(SKY)) {
        GardenCanvas(
            gridObjects = gridObjects,
            selectedTile = selectedTile,
            onTileTapped = viewModel::onTileTapped,
            atlas = atlas,
            modifier = Modifier.fillMaxSize(),
        )
        selectedTile?.let { tile ->
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 12.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = stringResource(R.string.selected_tile, tile.x, tile.y),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
