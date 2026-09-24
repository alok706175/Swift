package com.swiftapp.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftapp.R

/**
 * Clean Logo Splash Screen:
 * - Pure White Background
 * - Centered Swift App Logo with subtle elevation
 * - "Swift" title text below the logo
 * - Smooth fade-in entrance and seamless transition into homescreen
 */
@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.92f) }

    LaunchedEffect(Unit) {
        animScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 400,
                easing = FastOutSlowInEasing
            )
        )
    }

    LaunchedEffect(Unit) {
        animAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 350,
                easing = FastOutSlowInEasing
            )
        )
        kotlinx.coroutines.delay(950)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .alpha(animAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.scale(animScale.value)
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = Color(0xFFFF5E38).copy(alpha = 0.35f),
                        ambientColor = Color(0xFFFF5E38).copy(alpha = 0.15f)
                    )
                    .clip(RoundedCornerShape(32.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.swift_icon_clean),
                    contentDescription = "Swift Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Name "Swift" below the logo
            Text(
                text = "Swift",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF1E293B)
                )
            )
        }
    }
}
