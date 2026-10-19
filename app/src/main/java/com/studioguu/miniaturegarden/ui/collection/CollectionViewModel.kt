package com.studioguu.miniaturegarden.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.studioguu.miniaturegarden.data.asset.AssetRepository
import com.studioguu.miniaturegarden.data.asset.SpriteAtlas
import com.studioguu.miniaturegarden.data.asset.TerrainTextures
import com.studioguu.miniaturegarden.data.audio.AudioManager
import com.studioguu.miniaturegarden.data.audio.SoundEffect
import com.studioguu.miniaturegarden.domain.model.CollectionEntry
import com.studioguu.miniaturegarden.domain.model.CollectionProgress
import com.studioguu.miniaturegarden.domain.model.Creature
import com.studioguu.miniaturegarden.domain.model.NatureNotes
import com.studioguu.miniaturegarden.domain.repository.GardenRepository
import com.studioguu.miniaturegarden.domain.usecase.GrantRewardUseCase
import com.studioguu.miniaturegarden.domain.usecase.OpenNatureNoteUseCase
import com.studioguu.miniaturegarden.domain.usecase.RevealCollectionEntryUseCase
import com.studioguu.miniaturegarden.domain.usecase.RewardResult
import com.studioguu.miniaturegarden.ui.components.todayEpochDay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CollectionViewModel @Inject constructor(
    gardenRepository: GardenRepository,
    assetRepository: AssetRepository,
    private val revealEntry: RevealCollectionEntryUseCase,
    private val grantReward: GrantRewardUseCase,
    private val openNatureNote: OpenNatureNoteUseCase,
    private val audio: AudioManager,
) : ViewModel() {

    /**
     * Null until the stored pages have been read, so nothing flashes up as locked meanwhile.
     * Kept up to date while the book is shut too: the book decides where to open from the very
     * first value it sees, and that must not be one left over from the last time it was open.
     */
    val progress: StateFlow<CollectionProgress?> = gardenRepository.observeCollection()
        .map(::CollectionProgress)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The nature notes, for the book's last chapter; null until read. */
    val natureNotes: StateFlow<NatureNotes?> = gardenRepository.observeNatureNotes()
        .map(::NatureNotes)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The same sprites and ground textures the garden draws with, for the book's pictures. */
    private val _atlas = MutableStateFlow(SpriteAtlas.EMPTY)
    val atlas: StateFlow<SpriteAtlas> = _atlas.asStateFlow()

    private val _terrain = MutableStateFlow(TerrainTextures.EMPTY)
    val terrain: StateFlow<TerrainTextures> = _terrain.asStateFlow()

    init {
        viewModelScope.launch { _atlas.value = assetRepository.loadAtlas() }
        viewModelScope.launch { _terrain.value = assetRepository.loadTerrainTextures() }
    }

    /**
     * The book was opened. The first time each day that pays a few coins; after that it does
     * nothing until tomorrow, however often the book is opened. The coins chime; nothing is
     * written over the book about it.
     */
    fun onOpened() {
        viewModelScope.launch {
            if (grantReward.bookOpen(todayEpochDay()) == RewardResult.GRANTED) audio.play(SoundEffect.COIN)
        }
    }

    /**
     * The player asked for a ready page to be filled in. Its coins chime as the picture comes
     * in; the page itself is the news, so no "登録しました +N" is written over it.
     */
    fun reveal(entry: CollectionEntry) {
        viewModelScope.launch {
            revealEntry(entry) ?: return@launch
            audio.play(SoundEffect.COIN)
        }
    }

    /** The player turned to [creature]'s page in the nature notes: it is no longer new. */
    fun openNote(creature: Creature) {
        viewModelScope.launch { openNatureNote(creature) }
    }
}
