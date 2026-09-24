package com.swiftapp.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Swift 2026 Brand Colors - Vibrant Coral Orange & Modern Off-White Accents
val SwiftCoral = Color(0xFFFE5844)
val SwiftCoralLight = Color(0xFFFFF0EE)
val SwiftCoralDark = Color(0xFFE03E29)
val SwiftCoralGradientStart = Color(0xFFFF6F59)
val SwiftCoralGradientEnd = Color(0xFFE63920)

val PrimaryLight = Color(0xFFFE5844)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFFFF0EE)
val OnPrimaryContainerLight = Color(0xFF670E04)

val SecondaryLight = Color(0xFF2B2D42)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFF1F5F9)
val OnSecondaryContainerLight = Color(0xFF1E293B)

val TertiaryLight = Color(0xFF2563EB)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFDBEAFE)
val OnTertiaryContainerLight = Color(0xFF1E40AF)

val BackgroundLight = Color(0xFFF8F9FC)
val OnBackgroundLight = Color(0xFF191C1E)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF191C1E)
val SurfaceVariantLight = Color(0xFFF0F2F6)
val OnSurfaceVariantLight = Color(0xFF64748B)

// Dark Theme Colors
val PrimaryDark = Color(0xFFFF7A66)
val OnPrimaryDark = Color(0xFF4A0A02)
val PrimaryContainerDark = Color(0xFF6F180E)
val OnPrimaryContainerDark = Color(0xFFFFDAD4)

val SecondaryDark = Color(0xFF94A3B8)
val OnSecondaryDark = Color(0xFF0F172A)
val SecondaryContainerDark = Color(0xFF1E293B)
val OnSecondaryContainerDark = Color(0xFFE2E8F0)

val TertiaryDark = Color(0xFF60A5FA)
val OnTertiaryDark = Color(0xFF1E3A8A)
val TertiaryContainerDark = Color(0xFF1E40AF)
val OnTertiaryContainerDark = Color(0xFFDBEAFE)

val BackgroundDark = Color(0xFF0F1115)
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF181A1F)
val OnSurfaceDark = Color(0xFFF1F5F9)
val SurfaceVariantDark = Color(0xFF24272E)
val OnSurfaceVariantDark = Color(0xFF94A3B8)

// Category & Feature Accent Colors
val AccentEmerald = Color(0xFF10B981)
val AccentEmeraldContainer = Color(0xFFD1FAE5)
val AccentBlue = Color(0xFF3B82F6)
val AccentBlueContainer = Color(0xFFDBEAFE)
val AccentPurple = Color(0xFF8B5CF6)
val AccentPurpleContainer = Color(0xFFEDE9FE)
val AccentAmber = Color(0xFFF59E0B)
val AccentAmberContainer = Color(0xFFFEF3C7)
val AccentRose = Color(0xFFF43F5E)
val AccentRoseContainer = Color(0xFFFFE4E6)
val AccentIndigo = Color(0xFF6366F1)
val AccentIndigoContainer = Color(0xFFE0E7FF)

// Gradient Brushes
val SwiftBrandGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFFF725E), Color(0xFFFE5844), Color(0xFFE03E29))
)

val SwiftSubtleGradientLight = Brush.verticalGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8F9FC))
)

val SwiftSubtleGradientDark = Brush.verticalGradient(
    colors = listOf(Color(0xFF1E2026), Color(0xFF131519))
)
