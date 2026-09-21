package com.aipos.aipospm.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// AIPOS Material 3 Premium Security Palette
//
// Modern security design system built on Material You / M3 foundation:
//   Primary   → Luminous Cyan/Teal (trust, encryption, safety)
//   Secondary → Soft Indigo Accent (vault keys & developer credentials)
//   Tertiary  → Warm Gold/Amber    (favorites & TOTP 2FA highlights)
// ─────────────────────────────────────────────────────────────

// ── Light Theme ──────────────────────────────────────────────

// Primary — Deep Teal
val PrimaryLight = Color(0xFF0F766E)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFCCFBF1)
val OnPrimaryContainerLight = Color(0xFF115E59)

// Secondary — Soft Indigo
val SecondaryLight = Color(0xFF4F46E5)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE0E7FF)
val OnSecondaryContainerLight = Color(0xFF3730A3)

// Tertiary — Warm Amber Gold
val TertiaryLight = Color(0xFFD97706)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFEF3C7)
val OnTertiaryContainerLight = Color(0xFF92400E)

// Error
val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF991B1B)

// Surface / Background — Crisp, clean slate
val BackgroundLight = Color(0xFFF8FAFC)
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val OnSurfaceVariantLight = Color(0xFF475569)
val SurfaceContainerLowLight = Color(0xFFF8FAFC)
val OutlineLight = Color(0xFFCBD5E1)
val OutlineVariantLight = Color(0xFFE2E8F0)

// Glassmorphism Light Tokens
val GlassBorderLight = Color(0x1F000000)

// ── Dark Theme ───────────────────────────────────────────────

// Primary — Luminous Teal Cyan
val PrimaryDark = Color(0xFF2DD4BF)
val OnPrimaryDark = Color(0xFF003735)
val PrimaryContainerDark = Color(0xFF114B4E)
val OnPrimaryContainerDark = Color(0xFF99F6E4)

// Secondary — Soft Indigo Glow
val SecondaryDark = Color(0xFF818CF8)
val OnSecondaryDark = Color(0xFF1E1B4B)
val SecondaryContainerDark = Color(0xFF312E81)
val OnSecondaryContainerDark = Color(0xFFC7D2FE)

// Tertiary — Golden Sunbeam
val TertiaryDark = Color(0xFFFBBF24)
val OnTertiaryDark = Color(0xFF451A03)
val TertiaryContainerDark = Color(0xFF78350F)
val OnTertiaryContainerDark = Color(0xFFFDE68A)

// Error
val ErrorDark = Color(0xFFFCA5A5)
val OnErrorDark = Color(0xFF7F1D1D)
val ErrorContainerDark = Color(0xFF7F1D1D)
val OnErrorContainerDark = Color(0xFFFEE2E2)

// Surface / Background — Deep Obsidian Dark
val BackgroundDark = Color(0xFF0B0F19)
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF111827)
val OnSurfaceDark = Color(0xFFF1F5F9)
val SurfaceVariantDark = Color(0xFF1F2937)
val OnSurfaceVariantDark = Color(0xFF9CA3AF)
val SurfaceContainerLowDark = Color(0xFF161F30)
val OutlineDark = Color(0xFF374151)
val OutlineVariantDark = Color(0xFF1F2937)

// Glassmorphism Dark Tokens
val GlassBorderDark = Color(0x2BFFFFFF)
val GlowTealDark = Color(0x262DD4BF)
val GlowIndigoDark = Color(0x26818CF8)

// ── Semantic Security Colors ─────────────────────────────────
val SecurityGreen = Color(0xFF10B981)       // Vault score 100 / verified safe
val SecurityGreenLight = Color(0xFF34D399)  // Dark-theme variant
val WarningAmber = Color(0xFFF59E0B)        // Medium strength / caution
val WarningAmberLight = Color(0xFFFBBF24)   // Dark-theme variant
val DangerRed = Color(0xFFEF4444)           // Breached / weak password
val DangerRedLight = Color(0xFFF87171)      // Dark-theme variant