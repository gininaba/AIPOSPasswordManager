package com.aipos.aipospm.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// AIPOS Premium Security Palette
//
// Designed to convey trust, calm, and professionalism:
//   Primary   → Deep Teal Blue   (trust & safety)
//   Secondary → Soft Indigo      (premium accent)
//   Tertiary  → Amber Gold       (warmth: favorites, TOTP highlights)
// ─────────────────────────────────────────────────────────────

// ── Light Theme ──────────────────────────────────────────────

// Primary — Deep Teal
val PrimaryLight = Color(0xFF0D7377)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFB2F5EA)
val OnPrimaryContainerLight = Color(0xFF00201E)

// Secondary — Soft Indigo
val SecondaryLight = Color(0xFF4F46E5)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE0E7FF)
val OnSecondaryContainerLight = Color(0xFF1E1B4B)

// Tertiary — Amber Gold
val TertiaryLight = Color(0xFFD97706)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFEF3C7)
val OnTertiaryContainerLight = Color(0xFF451A03)

// Error
val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFEE2E2)
val OnErrorContainerLight = Color(0xFF450A0A)

// Surface / Background — Cool whites
val BackgroundLight = Color(0xFFF8FAFC)
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFE2E8F0)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFF94A3B8)
val OutlineVariantLight = Color(0xFFCBD5E1)

// ── Dark Theme ───────────────────────────────────────────────

// Primary — Luminous Teal
val PrimaryDark = Color(0xFF5EEAD4)
val OnPrimaryDark = Color(0xFF003735)
val PrimaryContainerDark = Color(0xFF0A5C5E)
val OnPrimaryContainerDark = Color(0xFFB2F5EA)

// Secondary — Light Indigo
val SecondaryDark = Color(0xFFA5B4FC)
val OnSecondaryDark = Color(0xFF1E1B4B)
val SecondaryContainerDark = Color(0xFF3730A3)
val OnSecondaryContainerDark = Color(0xFFE0E7FF)

// Tertiary — Warm Amber
val TertiaryDark = Color(0xFFFCD34D)
val OnTertiaryDark = Color(0xFF451A03)
val TertiaryContainerDark = Color(0xFF92400E)
val OnTertiaryContainerDark = Color(0xFFFEF3C7)

// Error
val ErrorDark = Color(0xFFFCA5A5)
val OnErrorDark = Color(0xFF7F1D1D)
val ErrorContainerDark = Color(0xFF991B1B)
val OnErrorContainerDark = Color(0xFFFEE2E2)

// Surface / Background — Deep blue-black
val BackgroundDark = Color(0xFF0F172A)
val OnBackgroundDark = Color(0xFFE2E8F0)
val SurfaceDark = Color(0xFF1E293B)
val OnSurfaceDark = Color(0xFFE2E8F0)
val SurfaceVariantDark = Color(0xFF334155)
val OnSurfaceVariantDark = Color(0xFFCBD5E1)
val OutlineDark = Color(0xFF64748B)
val OutlineVariantDark = Color(0xFF334155)

// ── Semantic Security Colors ─────────────────────────────────
// Used directly in vault health indicators, strength bars, etc.

val SecurityGreen = Color(0xFF059669)       // "All clear" — vault health 100
val SecurityGreenLight = Color(0xFF6EE7B7)  // Dark-theme variant
val WarningAmber = Color(0xFFF59E0B)        // "Attention needed"
val WarningAmberLight = Color(0xFFFCD34D)   // Dark-theme variant
val DangerRed = Color(0xFFEF4444)           // "Critical / breached"
val DangerRedLight = Color(0xFFFCA5A5)      // Dark-theme variant