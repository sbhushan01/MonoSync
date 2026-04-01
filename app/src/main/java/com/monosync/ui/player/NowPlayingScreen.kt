package com.monosync.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOneOn
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.monosync.model.LyricLine
import com.monosync.model.Resource
import com.monosync.model.Track
import com.monosync.model.findActiveLyricIndex
import com.monosync.ui.components.formatDuration
import com.monosync.ui.components.shimmerLoadingAnimation
import com.monosync.ui.components.swipeToSkipTrack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    currentTrack: Track,
    isPlaying: Boolean,
    streamState: Resource<String>?,
    currentPosition: Long,
    duration: Long,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    lyrics: List<LyricLine>,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier,
    mainContent: @Composable () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val coroutineScope = rememberCoroutineScope()
    var showLyrics by remember { mutableStateOf(false) }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        snackbarHost = { SnackbarHost(scaffoldState.snackbarHostState) },
        sheetPeekHeight = 72.dp,
        sheetContainerColor = colorScheme.surface.copy(alpha = 0.95f),
        sheetContent = {
            // === MINI PLAYER (visible when collapsed) ===
            MiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
                progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                onPlayPauseToggle = onPlayPauseToggle,
                onExpand = { coroutineScope.launch { sheetState.expand() } }
            )

            // === EXPANDED PLAYER ===
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.93f)
                    .fillMaxWidth()
            ) {
                // Blurred album art background
                if (currentTrack.albumArtUrl.isNotEmpty()) {
                    AsyncImage(
                        model = currentTrack.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(radiusX = 80.dp, radiusY = 80.dp)
                    )
                }
                // Dark gradient overlay for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                )

                // Glassmorphism control panel
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Collapse button
                    IconButton(
                        onClick = { coroutineScope.launch { sheetState.partialExpand() } },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Album Art / Lyrics Toggle Area ──
                    AnimatedVisibility(
                        visible = !showLyrics,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300))
                    ) {
                        // Album Art
                        val isResolving = streamState is Resource.Loading
                        Box(
                            modifier = Modifier
                                .size(300.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(colorScheme.surfaceVariant)
                                .shimmerLoadingAnimation(isLoading = isResolving)
                                .swipeToSkipTrack(
                                    onSeekNext = onSkipNext,
                                    onSeekPrevious = onSkipPrevious
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentTrack.albumArtUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = currentTrack.albumArtUrl,
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Album Art",
                                    modifier = Modifier.size(80.dp),
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    // ── Synchronized Lyrics Panel ──
                    AnimatedVisibility(
                        visible = showLyrics,
                        enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { it / 4 },
                        exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 4 }
                    ) {
                        SynchronizedLyricsPanel(
                            lyrics = lyrics,
                            currentPositionMs = currentPosition,
                            onSeekTo = onSeekTo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Track info + error indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (streamState is Resource.Error) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Source Unavailable",
                                tint = colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Seek bar connected to real playback
                    var isSeeking by remember { mutableStateOf(false) }
                    var seekPosition by remember { mutableFloatStateOf(0f) }
                    val displayPosition = if (isSeeking) seekPosition else {
                        if (duration > 0) currentPosition.toFloat() / duration else 0f
                    }

                    Slider(
                        value = displayPosition,
                        onValueChange = { value ->
                            isSeeking = true
                            seekPosition = value
                        },
                        onValueChangeFinished = {
                            onSeekTo((seekPosition * duration).toLong())
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )

                    // Time labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val displayMs = if (isSeeking) (seekPosition * duration).toLong() else currentPosition
                        Text(
                            text = formatDuration(displayMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Playback controls: Shuffle | Previous | Play/Pause | Next | Repeat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle
                        IconButton(onClick = onToggleShuffle) {
                            Icon(
                                imageVector = if (isShuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (isShuffleEnabled) colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Previous
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Play / Pause with crossfade animation
                        FilledIconButton(
                            onClick = onPlayPauseToggle,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Crossfade(targetState = isPlaying, label = "play_pause") { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        // Next
                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Repeat
                        IconButton(onClick = onCycleRepeatMode) {
                            val icon = when (repeatMode) {
                                1 -> Icons.Default.RepeatOn
                                2 -> Icons.Default.RepeatOneOn
                                else -> Icons.Default.Repeat
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Repeat",
                                tint = if (repeatMode > 0) colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Lyrics toggle
                    IconButton(
                        onClick = { showLyrics = !showLyrics },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (showLyrics) colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lyrics,
                                contentDescription = "Lyrics",
                                tint = if (showLyrics) colorScheme.primary else Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Lyrics",
                                color = if (showLyrics) colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Main content behind the bottom sheet (tabs, library, etc.)
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colorScheme.background)
        ) {
            mainContent()
        }
    }

    // Show Snackbar on Error
    LaunchedEffect(streamState) {
        if (streamState is Resource.Error) {
            scaffoldState.snackbarHostState.showSnackbar(
                message = streamState.message,
                duration = SnackbarDuration.Short
            )
        }
    }
}

// ── Synchronized Lyrics Panel ──────────────────────────────────────────────────

/**
 * A LazyColumn-based lyrics panel that highlights the currently active line
 * and smoothly auto-scrolls to keep it centered.
 *
 * - Active line: full white, bold, scaled up slightly.
 * - Upcoming lines: dimmed white.
 * - Past lines: further dimmed.
 * - Tapping a line seeks playback to that timestamp.
 */
@Composable
private fun SynchronizedLyricsPanel(
    lyrics: List<LyricLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    if (lyrics.isEmpty()) {
        // ── Empty state ──
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Lyrics,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No synchronized lyrics available",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Lyrics will appear here when available",
                    color = Color.White.copy(alpha = 0.25f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // ── Active lyric index (derived, recomputes when position changes) ──
    val activeIndex by remember(lyrics) {
        derivedStateOf { findActiveLyricIndex(lyrics, currentPositionMs) }
    }

    val listState = rememberLazyListState()

    // ── Auto-scroll: smoothly animate to keep the active line centered ──
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            // Calculate offset to center the active item in the visible area.
            // We use a negative offset to push the item towards the vertical center.
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val centerOffset = -(viewportHeight / 2) + 40 // 40px approximation for half line height
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = centerOffset
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        // Top + bottom gradient fades for a premium scroll effect
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Leading spacer items so the first lyric can be centered
            item { Spacer(modifier = Modifier.height(100.dp)) }

            itemsIndexed(
                items = lyrics,
                key = { index, line -> "${index}_${line.timestampMs}" }
            ) { index, line ->
                val isActive = index == activeIndex
                val isPast = index < activeIndex

                // Animated color transition
                val textColor by animateColorAsState(
                    targetValue = when {
                        isActive -> Color.White
                        isPast -> Color.White.copy(alpha = 0.3f)
                        else -> Color.White.copy(alpha = 0.5f)
                    },
                    animationSpec = tween(durationMillis = 300),
                    label = "lyric_color_$index"
                )

                // Subtle scale-up on the active line
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "lyric_scale_$index"
                )

                Text(
                    text = line.text.ifEmpty { "♪" },
                    color = textColor,
                    fontSize = if (isActive) 20.sp else 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = if (isActive) 28.sp else 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSeekTo(line.timestampMs) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }

            // Trailing spacer so the last lyric can be centered
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }

        // Top fade gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom fade gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )
    }
}

/**
 * Mini player bar visible when the bottom sheet is collapsed.
 */
@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseToggle: () -> Unit,
    onExpand: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column {
        // Thin progress bar at top
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (track.albumArtUrl.isNotEmpty()) {
                    AsyncImage(
                        model = track.albumArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play/Pause button
            IconButton(onClick = onPlayPauseToggle) {
                Crossfade(targetState = isPlaying, label = "mini_play_pause") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Root composable that connects NowPlayingScreen to the ViewModel.
 * Accepts mainContent to render navigation tabs behind the player sheet.
 */
@Composable
fun NowPlayingScreenRoot(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    mainContent: @Composable () -> Unit = {}
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val streamState by viewModel.streamState.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()

    NowPlayingScreen(
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        streamState = streamState,
        currentPosition = currentPosition,
        duration = duration,
        isShuffleEnabled = isShuffleEnabled,
        repeatMode = repeatMode,
        lyrics = lyrics,
        onPlayPauseToggle = viewModel::togglePlayPause,
        onSkipNext = viewModel::skipNext,
        onSkipPrevious = viewModel::skipPrevious,
        onSeekTo = viewModel::seekTo,
        onToggleShuffle = viewModel::toggleShuffle,
        onCycleRepeatMode = viewModel::cycleRepeatMode,
        modifier = modifier,
        mainContent = mainContent
    )
}
