package com.github.soundpod.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.core.ui.LocalAppearance
import com.github.soundpod.models.LocalMenuState
import com.github.soundpod.ui.components.CircleDragHandle
import com.github.soundpod.ui.components.MusicBars
import com.github.soundpod.ui.components.Overlay
import com.github.soundpod.ui.components.PlaylistHeader
import com.github.soundpod.ui.components.QueuedMediaItemMenu
import com.github.soundpod.ui.items.MediaSongItem
import com.github.soundpod.ui.styling.onOverlay
import com.github.soundpod.viewmodels.PlaylistViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import com.github.soundpod.R
import com.github.soundpod.db
import com.github.soundpod.enums.PlaylistSortBy
import com.github.soundpod.enums.SortOrder
import com.github.soundpod.models.Playlist
import com.github.soundpod.models.SongPlaylistMap
import com.github.soundpod.transaction
import com.github.soundpod.ui.components.TextFieldDialog
import com.github.soundpod.utils.playlistSortByKey
import com.github.soundpod.utils.playlistSortOrderKey
import com.github.soundpod.utils.rememberPreference
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlaylistOverlay(
    viewModel: PlaylistViewModel,
    onGoToAlbum: (String) -> Unit,
    onGoToArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val menuState = LocalMenuState.current
    val (colorPalette) = LocalAppearance.current

    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (state.currentIndex > 0) state.currentIndex else 0
    )

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveItem(from.index, to.index)
    }

    var isEditMode by remember { mutableStateOf(false) }
    var selectedUids by remember { mutableStateOf(emptySet<String>()) }

    val isAllSelected = remember(selectedUids, state.windows) {
        state.windows.isNotEmpty() && selectedUids.size == state.windows.size
    }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
        selectedUids = emptySet()
    }

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    if (showAddToPlaylistDialog) {
        val selectedMediaItems = state.windows
            .filter { selectedUids.contains(it.uid.toString()) }
            .map { it.mediaItem }
        AddSelectedSongsToPlaylistDialog(
            mediaItems = selectedMediaItems,
            onDismiss = {
                showAddToPlaylistDialog = false
                isEditMode = false
                selectedUids = emptySet()
            }
        )
    }

    Overlay(
        modifier = modifier,
        lazyListState = lazyListState,
        headerContent = {
            PlaylistHeader(
                isEditMode = isEditMode,
                isAllSelected = isAllSelected,
                onSelectAllToggle = {
                    selectedUids = if (isAllSelected) {
                        emptySet()
                    } else {
                        state.windows.map { it.uid.toString() }.toSet()
                    }
                },
                onClearQueue = {
                    viewModel.clearQueue()

                    isEditMode = false
                    selectedUids = emptySet()
                },
                onAddToPlaylist = {
                    showAddToPlaylistDialog = true
                }
            )
        }
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            itemsIndexed(
                items = state.windows,
                key = { _, window -> window.uid.toString() }
            ) { index, window ->
                val isSelectedInPlayer = state.currentIndex == index
                val isChecked = selectedUids.contains(window.uid.toString())

                ReorderableItem(
                    state = reorderableState,
                    key = window.uid.toString()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = isEditMode,
                            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isChecked) MaterialTheme.colorScheme.primary else colorPalette.text.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        selectedUids = if (isChecked) {
                                            selectedUids - window.uid.toString()
                                        } else {
                                            selectedUids + window.uid.toString()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CustomAnimatedVisibility(
                                    visible = isChecked,
                                    enter = scaleIn() + fadeIn(),
                                    exit = scaleOut() + fadeOut()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            MediaSongItem(
                                song = window.mediaItem,
                                onClick = {
                                    if (isEditMode) {
                                        selectedUids = if (isChecked) {
                                            selectedUids - window.uid.toString()
                                        } else {
                                            selectedUids + window.uid.toString()
                                        }
                                    } else {
                                        viewModel.handleItemClick(index)
                                    }
                                },
                                onLongClick = {
                                    if (!isEditMode) {
                                        isEditMode = true
                                        selectedUids = setOf(window.uid.toString())
                                    }
                                },
                                onThumbnailContent = {
                                    CustomAnimatedVisibility(
                                        visible = isSelectedInPlayer,
                                        enter = fadeIn(tween(320)),
                                        exit = fadeOut(tween(200)),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    color = Color.Black.copy(alpha = 0.25F),
                                                    shape = MaterialTheme.shapes.medium
                                                )
                                        ) {
                                            if (state.isPlaying) {
                                                MusicBars(
                                                    color = MaterialTheme.colorScheme.onOverlay,
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = MaterialTheme.colorScheme.onOverlay
                                                )
                                            }
                                        }
                                    }
                                },
                                trailingContent = {
                                    if (isEditMode) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .draggableHandle(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircleDragHandle()
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                menuState.display {
                                                    QueuedMediaItemMenu(
                                                        mediaItem = window.mediaItem,
                                                        indexInQueue = if (isSelectedInPlayer) null else index,
                                                        onDismiss = menuState::hide,
                                                        onGoToAlbum = onGoToAlbum,
                                                        onGoToArtist = onGoToArtist
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreVert,
                                                contentDescription = "Menu",
                                                tint = colorPalette.text
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomAnimatedVisibility(
    visible: Boolean,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        content = content
    )
}
@Composable
fun AddSelectedSongsToPlaylistDialog(
    mediaItems: List<MediaItem>,
    onDismiss: () -> Unit
) {
    val (colorPalette) = LocalAppearance.current
    val sortBy by rememberPreference(playlistSortByKey, PlaylistSortBy.DateAdded)
    val sortOrder by rememberPreference(playlistSortOrderKey, SortOrder.Descending)

    val playlistPreviews by remember {
        db.playlistPreviews(sortBy, sortOrder)
    }.collectAsState(initial = emptyList(), context = Dispatchers.IO)

    var isCreatingNewPlaylist by rememberSaveable { mutableStateOf(false) }

    fun addToPlaylist(playlist: Playlist, startPosition: Int) {
        transaction {
            val playlistId = db.insert(playlist).takeIf { it != -1L } ?: playlist.id
            mediaItems.forEachIndexed { index, mediaItem ->
                db.insert(mediaItem)
                db.insert(
                    SongPlaylistMap(
                        songId = mediaItem.mediaId,
                        playlistId = playlistId,
                        position = startPosition + index
                    )
                )
            }
        }
        onDismiss()
    }

    if (isCreatingNewPlaylist) {
        TextFieldDialog(
            title = stringResource(id = R.string.new_playlist),
            hintText = stringResource(id = R.string.playlist_name_hint),
            onDismiss = { isCreatingNewPlaylist = false },
            onDone = { text ->
                addToPlaylist(Playlist(name = text), 0)
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(id = R.string.add_to_playlist),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorPalette.text
                )
                FilledTonalButton(onClick = { isCreatingNewPlaylist = true }) {
                    Text(text = stringResource(id = R.string.new_playlist))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(playlistPreviews) { playlistPreview ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addToPlaylist(playlistPreview.playlist, playlistPreview.songCount) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(text = playlistPreview.playlist.name, color = colorPalette.text)
                    }
                }
            }
        }
    }
}
