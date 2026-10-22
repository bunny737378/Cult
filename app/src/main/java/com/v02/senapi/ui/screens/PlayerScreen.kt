package com.v02.senapi.ui.screens

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultElevatedCard
import com.example.ui.theme.CultGlassBorder
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultMidnightSurface
import com.example.ui.theme.CultNeonAmber
import com.example.ui.theme.CultTextPrimary
import com.example.ui.theme.CultTextSecondary
import com.v02.senapi.core.js.MediaItem
import com.v02.senapi.core.js.ProviderManager
import com.v02.senapi.core.js.StreamServer
import com.v02.senapi.player.CultPlayer
import com.v02.senapi.player.CultSurfaceView
import com.v02.senapi.player.SubtitleTrack
import com.v02.senapi.ui.components.ExpressiveMorphLoadingIndicator
import com.v02.senapi.ui.components.PlayerGesturesOverlay
import com.v02.senapi.ui.components.cultFrostedGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaItem: MediaItem,
    episodeId: String?,
    providerManager: ProviderManager,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cultPlayer = remember { CultPlayer(context) }
    var cultSurfaceView by remember { mutableStateOf<CultSurfaceView?>(null) }

    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentSubtitleText by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }

    var serversList by remember { mutableStateOf<List<StreamServer>>(emptyList()) }
    var selectedServerIndex by remember { mutableIntStateOf(0) }
    var selectedQuality by remember { mutableStateOf("1080p") }
    var selectedAudioTrack by remember { mutableStateOf("Japanese Sub") }
    var selectedSubtitleIndex by remember { mutableIntStateOf(0) }

    var showServerSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(CultSurfaceView.ScaleMode.FIT_PARENT) }

    // Enforce immersive fullscreen landscape
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            cultPlayer.release()
        }
    }

    BackHandler {
        cultPlayer.release()
        onNavigateBack()
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Load available servers and streams from provider
    LaunchedEffect(mediaItem.id, episodeId) {
        isBuffering = true
        coroutineScope.launch {
            val targetId = episodeId ?: mediaItem.id
            val servers = providerManager.getStreams(targetId, mediaItem.type)
            serversList = servers
            if (servers.isNotEmpty()) {
                val activeServer = servers.first()
                cultPlayer.setDataSource(activeServer.url, activeServer.headers)

                // Setup external subtitles
                val tracks = activeServer.externalSubtitles.map {
                    SubtitleTrack(it.label, it.url)
                }
                cultPlayer.setSubtitles(tracks)
                if (tracks.isNotEmpty()) {
                    cultPlayer.selectSubtitle(0)
                }
                cultPlayer.play()
            }
        }
    }

    // Player event listeners
    DisposableEffect(cultPlayer) {
        cultPlayer.setOnPreparedListener { _, dur, w, h ->
            durationMs = dur
            isBuffering = false
            isPlaying = true
            cultSurfaceView?.setVideoDimensions(w, h)
        }
        cultPlayer.setOnPlaybackPositionListener { _, pos, dur ->
            currentPositionMs = pos
            durationMs = dur
        }
        cultPlayer.setOnBufferingUpdateListener { _, percent ->
            isBuffering = percent < 100
        }
        cultPlayer.setOnSubtitleCueListener { _, text ->
            currentSubtitleText = text
        }
        cultPlayer.setOnErrorListener { _, _ ->
            isBuffering = false
        }
        cultPlayer.setOnCompletionListener {
            isPlaying = false
        }
        onDispose {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                CultSurfaceView(ctx).apply {
                    cultSurfaceView = this
                    cultPlayer.attachSurfaceView(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Subtitle Overlay
        if (!currentSubtitleText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showControls) 90.dp else 40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.5.dp, CultGlassBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = currentSubtitleText ?: "",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Gesture Overlay
        PlayerGesturesOverlay(
            onSingleTap = { showControls = !showControls },
            onDoubleTapSeek = { delta ->
                val target = (currentPositionMs + delta).coerceIn(0L, durationMs)
                cultPlayer.seekTo(target)
                currentPositionMs = target
            }
        ) {
            // Buffering Indicator
            if (isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ExpressiveMorphLoadingIndicator(size = 56.dp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Buffering Stream...",
                            color = CultTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // HUD Overlays
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(CultDarkVoid.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        cultPlayer.release()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("player_back_button")
                                        .cultFrostedGlass(cornerRadius = 19.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = CultTextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = mediaItem.title,
                                        color = CultTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val serverName = serversList.getOrNull(selectedServerIndex)?.name ?: "Yuki CDN"
                                    Text(
                                        text = "$serverName · $selectedQuality · $selectedAudioTrack",
                                        color = CultLuminousCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Top Right Action Buttons
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Server Selector Button
                                IconButton(
                                    onClick = { showServerSheet = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("player_server_button")
                                        .cultFrostedGlass(cornerRadius = 19.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = "Server",
                                        tint = CultLuminousCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Aspect ratio toggle
                                IconButton(
                                    onClick = {
                                        scaleMode = if (scaleMode == CultSurfaceView.ScaleMode.FIT_PARENT)
                                            CultSurfaceView.ScaleMode.CROP_ZOOM else CultSurfaceView.ScaleMode.FIT_PARENT
                                        cultSurfaceView?.setScaleMode(scaleMode)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .cultFrostedGlass(cornerRadius = 19.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "Aspect Ratio",
                                        tint = CultTextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Center Play / Pause Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val target = (currentPositionMs - 10000L).coerceAtLeast(0L)
                                cultPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .cultFrostedGlass(cornerRadius = 25.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = CultTextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Play/Pause Main Button
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    cultPlayer.pause()
                                    isPlaying = false
                                } else {
                                    cultPlayer.play()
                                    isPlaying = true
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .testTag("player_play_pause_button")
                                .clip(CircleShape)
                                .background(CultLuminousCyan)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = CultDarkVoid,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val target = (currentPositionMs + 10000L).coerceAtMost(durationMs)
                                cultPlayer.seekTo(target)
                                currentPositionMs = target
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .cultFrostedGlass(cornerRadius = 25.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = CultTextPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Bottom Control Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, CultDarkVoid.copy(alpha = 0.9f))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Column {
                            // Timeline Slider & Timestamps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTimestamp(currentPositionMs),
                                    color = CultTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Slider(
                                    value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
                                    onValueChange = { fraction ->
                                        val target = (fraction * durationMs).toLong()
                                        currentPositionMs = target
                                        cultPlayer.seekTo(target)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp)
                                        .testTag("player_seek_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = CultLuminousCyan,
                                        activeTrackColor = CultLuminousCyan,
                                        inactiveTrackColor = CultTextSecondary.copy(alpha = 0.3f)
                                    )
                                )

                                Text(
                                    text = formatTimestamp(durationMs),
                                    color = CultTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Quick Toolbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Audio Switcher Pill
                                    Row(
                                        modifier = Modifier
                                            .cultFrostedGlass(cornerRadius = 10.dp)
                                            .clickable { showAudioSheet = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Audiotrack,
                                            contentDescription = "Audio",
                                            tint = CultLuminousCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = selectedAudioTrack,
                                            color = CultTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Subtitle Switcher Pill
                                    Row(
                                        modifier = Modifier
                                            .cultFrostedGlass(cornerRadius = 10.dp)
                                            .clickable { showSubtitleSheet = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Subtitles,
                                            contentDescription = "Subtitles",
                                            tint = CultLuminousCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (selectedSubtitleIndex >= 0) "English Sub" else "Subtitles Off",
                                            color = CultTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Text(
                                    text = "Custom FFmpeg JNI Pipeline · Zero ExoPlayer",
                                    color = CultTextSecondary.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Server Bottom Sheet
        if (showServerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showServerSheet = false },
                containerColor = CultMidnightSurface,
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Select Streaming Server",
                        color = CultTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    serversList.forEachIndexed { idx, server ->
                        val isSelected = selectedServerIndex == idx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cultFrostedGlass(
                                    cornerRadius = 14.dp,
                                    containerColor = if (isSelected) CultElevatedCard else CultMidnightSurface
                                )
                                .clickable {
                                    selectedServerIndex = idx
                                    showServerSheet = false
                                    isBuffering = true
                                    cultPlayer.setDataSource(server.url, server.headers)
                                    cultPlayer.play()
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = server.name,
                                    color = if (isSelected) CultLuminousCyan else CultTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Protocol: ${server.type.uppercase()} · Referer Injected",
                                    color = CultTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            if (isSelected) {
                                Text(
                                    text = "ACTIVE",
                                    color = CultLuminousCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Subtitle Bottom Sheet
        if (showSubtitleSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSubtitleSheet = false },
                containerColor = CultMidnightSurface,
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Subtitle Preferences",
                        color = CultTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val options = listOf("English", "Spanish", "Subtitles Off")
                    options.forEachIndexed { idx, label ->
                        val isSelected = if (label == "Subtitles Off") selectedSubtitleIndex == -1 else selectedSubtitleIndex == idx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cultFrostedGlass(cornerRadius = 12.dp)
                                .clickable {
                                    if (label == "Subtitles Off") {
                                        selectedSubtitleIndex = -1
                                        cultPlayer.selectSubtitle(-1)
                                    } else {
                                        selectedSubtitleIndex = idx
                                        cultPlayer.selectSubtitle(idx)
                                    }
                                    showSubtitleSheet = false
                                }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) CultLuminousCyan else CultTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isSelected) {
                                Text(
                                    text = "SELECTED",
                                    color = CultLuminousCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Audio Bottom Sheet
        if (showAudioSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAudioSheet = false },
                containerColor = CultMidnightSurface,
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Audio Language & Track",
                        color = CultTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    listOf("Japanese Sub", "English Dub", "Original Stereo 5.1").forEach { track ->
                        val isSelected = selectedAudioTrack == track
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cultFrostedGlass(cornerRadius = 12.dp)
                                .clickable {
                                    selectedAudioTrack = track
                                    showAudioSheet = false
                                }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = track,
                                color = if (isSelected) CultLuminousCyan else CultTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isSelected) {
                                Text(
                                    text = "ACTIVE",
                                    color = CultLuminousCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
