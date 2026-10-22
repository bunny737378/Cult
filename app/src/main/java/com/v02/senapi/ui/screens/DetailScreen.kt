package com.v02.senapi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultElevatedCard
import com.example.ui.theme.CultGlassBorder
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultNeonAmber
import com.example.ui.theme.CultTextMuted
import com.example.ui.theme.CultTextPrimary
import com.example.ui.theme.CultTextSecondary
import com.v02.senapi.core.js.ActorItem
import com.v02.senapi.core.js.EpisodeItem
import com.v02.senapi.core.js.MediaItem
import com.v02.senapi.core.js.MetaDetails
import com.v02.senapi.core.js.ProviderManager
import com.v02.senapi.core.js.SeasonTab
import com.v02.senapi.ui.components.ExpressiveMorphLoadingIndicator
import com.v02.senapi.ui.components.MediaCarousel
import com.v02.senapi.ui.components.cultFrostedGlass
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    mediaItem: MediaItem,
    providerManager: ProviderManager,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onPlayMedia: (MediaItem, episodeId: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var metaDetails by remember { mutableStateOf<MetaDetails?>(null) }
    var seasonTabs by remember { mutableStateOf<List<SeasonTab>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(mediaItem.id) {
        isLoading = true
        coroutineScope.launch {
            try {
                val details = providerManager.getMetaDetails(mediaItem.id, mediaItem.type)
                val (seasons, eps) = providerManager.getSeasonInfo(mediaItem.id)
                metaDetails = details
                seasonTabs = seasons
                episodes = eps
            } catch (ignored: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CultDarkVoid)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpressiveMorphLoadingIndicator(size = 54.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Title Information...",
                        color = CultTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            val details = metaDetails
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                // Collapsing Backdrop Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        AsyncImage(
                            model = details?.bg?.ifBlank { mediaItem.img } ?: mediaItem.img,
                            contentDescription = mediaItem.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Dramatic vertical gradient scrim
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            CultDarkVoid.copy(alpha = 0.4f),
                                            CultDarkVoid.copy(alpha = 0.7f),
                                            CultDarkVoid
                                        ),
                                        startY = 100f
                                    )
                                )
                        )

                        // Title & Meta Info overlaid on backdrop bottom
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            // Unboxed metadata with · separator
                            val tagsText = details?.tags?.joinToString(" · ") ?: "Action · 2026 · 4K"
                            Text(
                                text = tagsText,
                                color = CultLuminousCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = mediaItem.title,
                                color = CultTextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 34.sp
                            )
                        }
                    }
                }

                // Primary Play Action Button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayMedia(mediaItem, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("detail_play_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CultLuminousCyan,
                                contentColor = CultDarkVoid
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Stream Now",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Plot Summary
                item {
                    val plot = details?.plot?.ifBlank {
                        "A cinematic journey crossing extraordinary dimensions and confidential threads across civilization."
                    } ?: ""
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(
                            text = "Storyline",
                            color = CultTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = plot,
                            color = CultTextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                // Cast Rail
                if (!details?.actors.isNullOrEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Top Cast",
                                color = CultTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(details?.actors ?: emptyList()) { actor ->
                                    CastAvatar(actor = actor)
                                }
                            }
                        }
                    }
                }

                // Seasons & Episodes Hub (for TV Series)
                if (episodes.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Episodes",
                                    color = CultTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "${episodes.size} Episodes Available",
                                    color = CultTextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            if (seasonTabs.size > 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                ScrollableTabRow(
                                    selectedTabIndex = selectedSeasonIndex,
                                    containerColor = Color.Transparent,
                                    contentColor = CultLuminousCyan,
                                    edgePadding = 20.dp
                                ) {
                                    seasonTabs.forEachIndexed { idx, tab ->
                                        Tab(
                                            selected = selectedSeasonIndex == idx,
                                            onClick = { selectedSeasonIndex = idx },
                                            text = {
                                                Text(
                                                    text = tab.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (selectedSeasonIndex == idx) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (selectedSeasonIndex == idx) CultLuminousCyan else CultTextSecondary
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Episode Cards
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                episodes.forEach { ep ->
                                    EpisodeRowCard(
                                        episode = ep,
                                        onClick = { onPlayMedia(mediaItem, ep.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Recommendations Tray
                if (!details?.recommend.isNullOrEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        MediaCarousel(
                            title = "More Like This",
                            items = details?.recommend ?: emptyList(),
                            onItemClick = { recItem ->
                                // Trigger load of new item
                            }
                        )
                    }
                }
            }
        }

        // Floating Frosted Back Button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .size(42.dp)
                .testTag("detail_back_button")
                .cultFrostedGlass(cornerRadius = 21.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = CultTextPrimary
            )
        }
    }
}

@Composable
fun CastAvatar(actor: ActorItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CultElevatedCard)
                .border(1.dp, CultGlassBorder, CircleShape)
        ) {
            AsyncImage(
                model = actor.img,
                contentDescription = actor.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actor.name,
            color = CultTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = actor.role,
            color = CultTextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EpisodeRowCard(
    episode: EpisodeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cultFrostedGlass(cornerRadius = 16.dp)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Preview Thumbnail
        Box(
            modifier = Modifier
                .width(110.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(CultElevatedCard)
        ) {
            AsyncImage(
                model = episode.img,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Play indicator
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Episode",
                    tint = CultLuminousCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Episode Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "EP ${episode.epNo}",
                    color = CultLuminousCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                if (episode.tags.isNotEmpty()) {
                    Text(
                        text = episode.tags.first(),
                        color = CultNeonAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = episode.title,
                color = CultTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (episode.plot.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.plot,
                    color = CultTextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
