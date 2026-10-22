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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultElevatedCard
import com.example.ui.theme.CultGlassBorder
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultTextMuted
import com.example.ui.theme.CultTextPrimary
import com.example.ui.theme.CultTextSecondary
import com.v02.senapi.core.js.MediaItem
import com.v02.senapi.core.js.ProviderManager
import com.v02.senapi.ui.components.ExpressiveMorphLoadingIndicator
import com.v02.senapi.ui.components.MediaPosterCard
import com.v02.senapi.ui.components.cultFrostedGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    providerManager: ProviderManager,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (MediaItem) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    fun performSearch(q: String) {
        if (q.isBlank()) {
            searchResults = emptyList()
            suggestions = emptyList()
            isSearching = false
            return
        }
        isSearching = true
        coroutineScope.launch {
            try {
                // Fetch query suggestions (qsearch) and full results (allsearch)
                val sug = providerManager.querySuggestions(q)
                val results = providerManager.searchAll(q, page = 1)
                suggestions = sug
                searchResults = results
            } catch (ignored: Exception) {
            } finally {
                isSearching = false
            }
        }
    }

    LaunchedEffect(query) {
        debounceJob?.cancel()
        if (query.isNotBlank()) {
            debounceJob = launch {
                delay(300) // 300ms debounce
                performSearch(query)
            }
        } else {
            searchResults = emptyList()
            suggestions = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CultDarkVoid)
            .statusBarsPadding()
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(42.dp)
                    .testTag("search_back_button")
                    .cultFrostedGlass(cornerRadius = 21.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CultTextPrimary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Frosted Search Input Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .cultFrostedGlass(cornerRadius = 18.dp)
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "Search movies, series, anime...",
                            color = CultTextMuted,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CultLuminousCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = CultTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = CultTextPrimary,
                        unfocusedTextColor = CultTextPrimary,
                        cursorColor = CultLuminousCyan
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_text_input")
                )
            }
        }

        // Suggestion Pills (qsearch)
        if (suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { pill ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CultElevatedCard)
                            .border(1.dp, CultGlassBorder, RoundedCornerShape(12.dp))
                            .clickable {
                                query = pill
                                performSearch(pill)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Trend",
                            tint = CultLuminousCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pill,
                            color = CultTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Search Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ExpressiveMorphLoadingIndicator(size = 48.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Searching titles...",
                                color = CultTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                query.isNotEmpty() && searchResults.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No results found for \"$query\"",
                                color = CultTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try searching for popular titles like Dune, Solo Leveling, or Arcane.",
                                color = CultTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                query.isEmpty() -> {
                    // Search zero-state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = CultTextMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Discover Unlimited Cinema",
                                color = CultTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Search across thousands of movies, anime series, and web shows.",
                                color = CultTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                else -> {
                    // 3-Column Results Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(searchResults, key = { it.id }) { item ->
                            MediaPosterCard(
                                item = item,
                                cardWidth = 110,
                                onClick = { onNavigateToDetails(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}
