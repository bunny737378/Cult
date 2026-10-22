package com.v02.senapi.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultElevatedCard
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultNeonAmber
import com.example.ui.theme.CultTextPrimary
import com.example.ui.theme.CultTextSecondary
import com.v02.senapi.core.js.MediaItem
import kotlinx.coroutines.delay

@Composable
fun HeroBannerSlider(
    items: List<MediaItem>,
    modifier: Modifier = Modifier,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf(CultDarkVoid) }

    // Auto-advance hero banner every 6 seconds
    LaunchedEffect(items.size) {
        while (true) {
            delay(6000)
            if (items.size > 1) {
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    // Dynamic color extraction via Android Palette API for the active slide
    LaunchedEffect(pagerState.currentPage) {
        val currentItem = items.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        try {
            val loader = ImageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(currentItem.img)
                .allowHardware(false)
                .build()
            val result = loader.execute(req)
            if (result is SuccessResult) {
                val bitmap: Bitmap = result.drawable.toBitmap()
                Palette.from(bitmap).generate { palette ->
                    val rgb = palette?.dominantSwatch?.rgb
                        ?: palette?.darkMutedSwatch?.rgb
                    if (rgb != null) {
                        dominantColor = Color(rgb)
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    val animatedGlow by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 800),
        label = "ambientGlow"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .aspectRatio(16f / 10f)
                .cultFrostedGlass(cornerRadius = 24.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = items[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onItemClick(item) }
                ) {
                    AsyncImage(
                        model = item.img,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Atmospheric gradient scrims
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        animatedGlow.copy(alpha = 0.4f),
                                        CultDarkVoid.copy(alpha = 0.95f)
                                    ),
                                    startY = 100f
                                )
                            )
                    )

                    // Slide content
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        // Rating & Tag pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CultNeonAmber.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = item.rating.ifBlank { "★ 8.9" },
                                    color = CultNeonAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "Spotlight Premiere",
                                color = CultLuminousCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.title,
                            color = CultTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (item.plot.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.plot,
                                color = CultTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Actions
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onPlayClick(item) },
                                modifier = Modifier.testTag("hero_play_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CultLuminousCyan,
                                    contentColor = CultDarkVoid
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Watch Now",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            FilledTonalButton(
                                onClick = { onItemClick(item) },
                                modifier = Modifier.testTag("hero_details_button"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CultElevatedCard.copy(alpha = 0.85f),
                                    contentColor = CultTextPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Details",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Details",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Pager indicator dots
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 18.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) CultLuminousCyan else CultTextSecondary.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}
