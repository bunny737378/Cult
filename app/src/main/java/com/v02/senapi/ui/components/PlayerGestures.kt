package com.v02.senapi.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultTextPrimary
import kotlinx.coroutines.delay

@Composable
fun PlayerGesturesOverlay(
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit,
    onDoubleTapSeek: (seekDeltaMs: Long) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }

    var showVolumeIndicator by remember { mutableStateOf(false) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }

    var showSeekRipple by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showSeekRipple) {
        if (showSeekRipple != null) {
            delay(750)
            showSeekRipple = null
        }
    }

    LaunchedEffect(showBrightnessIndicator) {
        if (showBrightnessIndicator) {
            delay(1200)
            showBrightnessIndicator = false
        }
    }

    LaunchedEffect(showVolumeIndicator) {
        if (showVolumeIndicator) {
            delay(1200)
            showVolumeIndicator = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { offset ->
                        val isLeft = offset.x < size.width / 2f
                        if (isLeft) {
                            showSeekRipple = "-10s"
                            onDoubleTapSeek(-10000L)
                        } else {
                            showSeekRipple = "+10s"
                            onDoubleTapSeek(10000L)
                        }
                    }
                )
            }
    ) {
        content()

        // Seek Ripple Overlay
        AnimatedVisibility(
            visible = showSeekRipple != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(
                if (showSeekRipple?.startsWith("-") == true) Alignment.CenterStart else Alignment.CenterEnd
            ).padding(horizontal = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CultDarkVoid.copy(alpha = 0.75f))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (showSeekRipple?.startsWith("-") == true)
                            Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = "Seek",
                        tint = CultLuminousCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = showSeekRipple ?: "",
                        color = CultTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Brightness HUD
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .cultFrostedGlass(cornerRadius = 16.dp)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BrightnessMedium,
                        contentDescription = "Brightness",
                        tint = CultLuminousCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(brightnessLevel * 100).toInt()}%",
                        color = CultTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Volume HUD
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .cultFrostedGlass(cornerRadius = 16.dp)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Volume",
                        tint = CultLuminousCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(volumeLevel * 100).toInt()}%",
                        color = CultTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
