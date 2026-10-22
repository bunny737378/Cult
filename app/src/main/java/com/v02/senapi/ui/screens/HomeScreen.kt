package com.v02.senapi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultElevatedCard
import com.example.ui.theme.CultGlassBorder
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultMidnightSurface
import com.example.ui.theme.CultNeonAmber
import com.example.ui.theme.CultTextMuted
import com.example.ui.theme.CultTextPrimary
import com.example.ui.theme.CultTextSecondary
import com.v02.senapi.core.js.CatalogItem
import com.v02.senapi.core.js.MediaItem
import com.v02.senapi.core.js.ProviderManager
import com.v02.senapi.ui.components.ExpressiveMorphLoadingIndicator
import com.v02.senapi.ui.components.HeroBannerSlider
import com.v02.senapi.ui.components.MediaCarousel
import com.v02.senapi.ui.components.cultFrostedGlass
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    providerManager: ProviderManager,
    modifier: Modifier = Modifier,
    onNavigateToDetails: (MediaItem) -> Unit,
    onNavigateToPlayer: (MediaItem) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProviderSheet: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var heroItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var categoryTrays by remember { mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()) }
    var activeProviderName by remember { mutableStateOf("MX Player") }

    val loadFeed: () -> Unit = {
        isLoading = true
        coroutineScope.launch {
            try {
                val activeId = providerManager.getActiveProviderId()
                activeProviderName = providerManager.getInstalledProviders()
                    .find { it.id == activeId }?.name ?: "MX Player"

                val catalog = providerManager.getCatalog()
                val loadedTrays = mutableListOf<Pair<String, List<MediaItem>>>()

                for ((sectionTitle, items) in catalog) {
                    for (catItem in items) {
                        if (catItem.i.startsWith("slug-")) {
                            if (heroItems.isEmpty()) {
                                heroItems = providerManager.getHomeCategory(catItem.i, page = 1)
                            }
                        } else {
                            val mediaList = providerManager.getHomeCategory(catItem.i, page = 1)
                            if (mediaList.isNotEmpty()) {
                                loadedTrays.add(Pair(catItem.nm, mediaList))
                            }
                        }
                    }
                }

                if (heroItems.isEmpty()) {
                    heroItems = providerManager.getHomeCategory("slug-trending", page = 1)
                }

                categoryTrays = loadedTrays
            } catch (ignored: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(providerManager.getActiveProviderId()) {
        loadFeed()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CultDarkVoid)
    ) {
        // Deep ambient lighting orbs
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CultLuminousCyan.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Spacer for Top App Bar
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ExpressiveMorphLoadingIndicator(size = 56.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading $activeProviderName Feed...",
                                color = CultTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Spotlight Hero Slider
                if (heroItems.isNotEmpty()) {
                    item {
                        HeroBannerSlider(
                            items = heroItems,
                            onItemClick = onNavigateToDetails,
                            onPlayClick = onNavigateToPlayer
                        )
                    }
                }

                // Horizontal Content Trays
                items(categoryTrays.size) { index ->
                    val tray = categoryTrays[index]
                    MediaCarousel(
                        title = tray.first,
                        items = tray.second,
                        onItemClick = onNavigateToDetails
                    )
                }
            }
        }

        // Frosted Glass Top App Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .cultFrostedGlass(cornerRadius = 24.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Mark
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CultLuminousCyan, Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "C",
                            color = CultDarkVoid,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "CULT",
                        color = CultTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }

                // Provider Switcher Pill & Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Active Provider Pill
                    Row(
                        modifier = Modifier
                            .testTag("provider_switcher_button")
                            .clip(RoundedCornerShape(12.dp))
                            .background(CultElevatedCard)
                            .border(1.dp, CultGlassBorder, RoundedCornerShape(12.dp))
                            .clickable { onOpenProviderSheet() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "Provider",
                            tint = CultLuminousCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeProviderName,
                            color = CultTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Change",
                            tint = CultTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Search Button
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("search_icon_button")
                            .clip(CircleShape)
                            .background(CultElevatedCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CultTextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
