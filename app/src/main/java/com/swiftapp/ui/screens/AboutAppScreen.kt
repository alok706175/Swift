package com.swiftapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.HapticManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutAppScreen(
    languageViewModel: LanguageViewModel,
    versionName: String? = null,
    versionCode: Long? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val brandColor = Color(0xFFFF5E38)

    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }
    val resolvedVersionName = versionName ?: packageInfo?.versionName ?: "1.0.0"
    val resolvedVersionCode = versionCode ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode ?: 102L
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toLong() ?: 102L
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        HapticManager.light()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = languageViewModel.getString("settings_about"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Hero Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // App Logo Icon
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(22.dp),
                                spotColor = brandColor.copy(alpha = 0.35f),
                                ambientColor = brandColor.copy(alpha = 0.15f)
                            )
                            .clip(RoundedCornerShape(22.dp))
                    ) {
                        Image(
                            painter = painterResource(id = com.swiftapp.R.drawable.swift_icon_clean),
                            contentDescription = "Swift Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Text(
                        text = languageViewModel.getString("app_name"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = languageViewModel.getString("tagline"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = brandColor.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, brandColor.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "Swift v$resolvedVersionName • Build $resolvedVersionCode",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = brandColor,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Core Capabilities Section Header
            Text(
                text = "Key Features",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Feature 1: Offline Architecture
            AboutFeatureCard(
                icon = Icons.Outlined.VpnLock,
                iconTint = Color(0xFF10B981),
                title = "100% Offline & Private",
                description = "All operations run locally on your device. Your sensitive documents never leave your phone."
            )

            // Feature 2: PDF Toolkit
            AboutFeatureCard(
                icon = Icons.AutoMirrored.Outlined.CallMerge,
                iconTint = Color(0xFFFF5E38),
                title = "Complete PDF Toolkit",
                description = "Merge, Compress, Split, Rotate, Delete Pages, Image to PDF & PDF to Image converter."
            )

            // Feature 3: Smart Scanner
            AboutFeatureCard(
                icon = Icons.Outlined.DocumentScanner,
                iconTint = Color(0xFF8B5CF6),
                title = "Document Scanner",
                description = "High-quality camera scanning with automatic edge detection and intelligent document color filters."
            )

            // Feature 4: Security
            AboutFeatureCard(
                icon = Icons.Outlined.Lock,
                iconTint = Color(0xFF0EA5E9),
                title = "Security & E-Sign",
                description = "AES-128 PDF encryption, password protection, digital signatures, and biometric app lock."
            )

            // Feature 5: Multilingual
            AboutFeatureCard(
                icon = Icons.Outlined.Language,
                iconTint = Color(0xFFF59E0B),
                title = "Multi-Language Support",
                description = "Supports 12 Indian and International languages with instant seamless switching."
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Button(
                onClick = {
                    HapticManager.light()
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out Swift - All-in-one offline PDF tools for Android! Merge, Compress, Scan, E-Sign and Protect PDFs safely: https://github.com/alok706175/Swift"
                        )
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share Swift PDF")
                    context.startActivity(shareIntent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandColor)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = languageViewModel.getString("settings_share_app"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Crafted with care for privacy-first productivity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val infiniteTransition = rememberInfiniteTransition(label = "heart_glow")
                val glowScale by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.35f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow_scale"
                )
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow_alpha"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Made with ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        // Ambient glowing halo ring
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = glowScale
                                    scaleY = glowScale
                                    alpha = glowAlpha * 0.75f
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF1744).copy(alpha = 0.9f),
                                            Color(0xFFFF5252).copy(alpha = 0.35f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                        // Radiant core
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer {
                                    alpha = glowAlpha
                                }
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF5252).copy(alpha = 0.85f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                        // Heart symbol with slight pulse
                        Text(
                            text = "❤️",
                            fontSize = 15.sp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = 1f + (glowScale - 1f) * 0.25f
                                scaleY = 1f + (glowScale - 1f) * 0.25f
                            }
                        )
                    }
                    Text(
                        text = " by ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Alok",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = brandColor
                    )
                }
                Text(
                    text = "© 2026 Swift PDF • All Rights Reserved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AboutFeatureCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
