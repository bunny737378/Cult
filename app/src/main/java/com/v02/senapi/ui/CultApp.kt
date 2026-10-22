package com.v02.senapi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultGlassBorder
import com.example.ui.theme.CultGlassRim
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultTextMuted
import com.example.ui.theme.CultTextPrimary
import com.example.ui.theme.CultTextSecondary
import com.v02.senapi.core.js.MediaItem
import com.v02.senapi.core.js.ProviderManager
import com.v02.senapi.ui.components.cultFrostedGlass
import com.v02.senapi.ui.screens.DetailScreen
import com.v02.senapi.ui.screens.HomeScreen
import com.v02.senapi.ui.screens.PlayerScreen
import com.v02.senapi.ui.screens.ProviderSheet
import com.v02.senapi.ui.screens.SearchScreen

sealed class NavDestination {
    object Home : NavDestination()
    object Search : NavDestination()
    data class Details(val item: MediaItem) : NavDestination()
    data class Player(val item: MediaItem, val episodeId: String?) : NavDestination()
}

@Composable
fun CultApp() {
    val context = LocalContext.current
    val providerManager = remember { ProviderManager(context) }

    var currentScreen by remember { mutableStateOf<NavDestination>(NavDestination.Home) }
    var showProviderSheet by remember { mutableStateOf(false) }

    // Root container with deep ambient glow
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CultDarkVoid)
    ) {
        // Screen Navigation
        when (val screen = currentScreen) {
            is NavDestination.Home -> {
                HomeScreen(
                    providerManager = providerManager,
                    onNavigateToDetails = { item -> currentScreen = NavDestination.Details(item) },
                    onNavigateToPlayer = { item -> currentScreen = NavDestination.Player(item, null) },
                    onOpenSearch = { currentScreen = NavDestination.Search },
                    onOpenProviderSheet = { showProviderSheet = true }
                )
            }
            is NavDestination.Search -> {
                BackHandler { currentScreen = NavDestination.Home }
                SearchScreen(
                    providerManager = providerManager,
                    onNavigateBack = { currentScreen = NavDestination.Home },
                    onNavigateToDetails = { item -> currentScreen = NavDestination.Details(item) }
                )
            }
            is NavDestination.Details -> {
                BackHandler { currentScreen = NavDestination.Home }
                DetailScreen(
                    mediaItem = screen.item,
                    providerManager = providerManager,
                    onNavigateBack = { currentScreen = NavDestination.Home },
                    onPlayMedia = { item, epId -> currentScreen = NavDestination.Player(item, epId) }
                )
            }
            is NavDestination.Player -> {
                PlayerScreen(
                    mediaItem = screen.item,
                    episodeId = screen.episodeId,
                    providerManager = providerManager,
                    onNavigateBack = {
                        currentScreen = NavDestination.Details(screen.item)
                    }
                )
            }
        }

        // Floating Frosted Bottom Navigation Bar (visible on Home & Search only)
        val showBottomBar = currentScreen is NavDestination.Home || currentScreen is NavDestination.Search

        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .cultFrostedGlass(
                        cornerRadius = 32.dp,
                        containerColor = Color(0xD90D121F),
                        strokeColor = CultGlassRim,
                        blurRadius = 28.dp
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingNavItem(
                        title = "Home",
                        selected = currentScreen is NavDestination.Home,
                        selectedIcon = Icons.Filled.Home,
                        unselectedIcon = Icons.Outlined.Home,
                        testTag = "nav_home",
                        onClick = { currentScreen = NavDestination.Home }
                    )

                    FloatingNavItem(
                        title = "Explore",
                        selected = currentScreen is NavDestination.Search,
                        selectedIcon = Icons.Filled.Search,
                        unselectedIcon = Icons.Outlined.Search,
                        testTag = "nav_search",
                        onClick = { currentScreen = NavDestination.Search }
                    )

                    FloatingNavItem(
                        title = "Plugins",
                        selected = showProviderSheet,
                        selectedIcon = Icons.Filled.Extension,
                        unselectedIcon = Icons.Outlined.Extension,
                        testTag = "nav_providers",
                        onClick = { showProviderSheet = true }
                    )
                }
            }
        }

        // Provider Management Bottom Sheet Modal
        if (showProviderSheet) {
            ProviderSheet(
                providerManager = providerManager,
                onDismiss = { showProviderSheet = false },
                onProviderChanged = {
                    currentScreen = NavDestination.Home
                }
            )
        }
    }
}

@Composable
private fun FloatingNavItem(
    title: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (selected) CultLuminousCyan.copy(alpha = 0.15f) else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = title,
            tint = if (selected) CultLuminousCyan else CultTextMuted,
            modifier = Modifier.size(20.dp)
        )

        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = CultLuminousCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
