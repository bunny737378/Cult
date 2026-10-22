package com.v02.senapi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CultDarkVoid
import com.example.ui.theme.CultElevatedCard
import com.example.ui.theme.CultGlassBorder
import com.example.ui.theme.CultLuminousCyan
import com.example.ui.theme.CultMidnightSurface
import com.example.ui.theme.CultTextMuted
import com.example.ui.theme.CultTextPrimary
import com.example.ui.theme.CultTextSecondary
import com.example.ui.theme.CultVividCoral
import com.v02.senapi.core.js.ProviderManager
import com.v02.senapi.ui.components.ExpressiveMorphLoadingIndicator
import com.v02.senapi.ui.components.cultFrostedGlass
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSheet(
    providerManager: ProviderManager,
    onDismiss: () -> Unit,
    onProviderChanged: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val activeId = providerManager.getActiveProviderId()
    val providers = providerManager.getInstalledProviders()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CultMidnightSurface,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Provider Ecosystem",
                        color = CultTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dynamic QuickJS Scrapers & Catalog Engines",
                        color = CultTextSecondary,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = CultLuminousCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Installed Providers List
            providers.forEach { p ->
                val isSelected = p.id == activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cultFrostedGlass(
                            cornerRadius = 16.dp,
                            containerColor = if (isSelected) CultElevatedCard else CultMidnightSurface
                        )
                        .clickable {
                            providerManager.setActiveProviderId(p.id)
                            onProviderChanged(p.id)
                            onDismiss()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CultElevatedCard)
                                .border(1.dp, CultGlassBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.name.take(2).uppercase(),
                                color = CultLuminousCyan,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = p.name,
                                color = if (isSelected) CultLuminousCyan else CultTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "v${p.version} · Cached Locally",
                                color = CultTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(CultLuminousCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = CultDarkVoid,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusMessage ?: "",
                    color = CultLuminousCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        isCheckingUpdates = true
                        statusMessage = "Checking remote repository..."
                        coroutineScope.launch {
                            val updated = providerManager.checkForUpdates()
                            isCheckingUpdates = false
                            statusMessage = if (updated) "All plugins synchronized with GitHub!" else "Local plugins are up to date."
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("check_updates_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CultElevatedCard,
                        contentColor = CultTextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isCheckingUpdates) {
                        ExpressiveMorphLoadingIndicator(size = 20.dp, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = CultLuminousCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Check Updates", fontSize = 13.sp)
                    }
                }

                OutlinedButton(
                    onClick = {
                        providerManager.clearCache()
                        statusMessage = "Plugin cache cleared & reinitialized."
                        onProviderChanged(providerManager.getActiveProviderId())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("clear_cache_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CultVividCoral
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Cache",
                        tint = CultVividCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Clear Cache", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
