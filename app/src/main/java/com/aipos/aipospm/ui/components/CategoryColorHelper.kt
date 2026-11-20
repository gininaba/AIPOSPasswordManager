package com.aipos.aipospm.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Deterministically computes a visually balanced (containerColor, contentColor) pair
 * from a category name. Uses bit-masking rather than absoluteValue to avoid negative hue
 * when hashCode() == Int.MIN_VALUE.
 */
@Composable
fun getCategoryColors(name: String): Pair<Color, Color> {
    val hue = ((name.hashCode() and 0x7FFFFFFF) % 360).toFloat()
    val isDark = isSystemInDarkTheme()
    val containerColor = Color.hsv(hue, 0.15f, if (isDark) 0.15f else 0.95f)
    val contentColor = Color.hsv(hue, 0.8f, if (isDark) 0.9f else 0.4f)
    return Pair(containerColor, contentColor)
}

