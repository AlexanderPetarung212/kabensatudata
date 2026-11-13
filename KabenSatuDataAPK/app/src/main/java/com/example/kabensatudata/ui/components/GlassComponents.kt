package com.example.kabensatudata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism card — Light mode variant.
 * Subtle frosted-glass effect over light backgrounds.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        Color.White.copy(alpha = 0.60f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.9f),
                        Color(0xFFE2E8F0).copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(16.dp),
        content = content
    )
}

/**
 * Glassmorphism card — Teal tinted variant for accent sections.
 */
@Composable
fun GlassCardLight(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00B894).copy(alpha = 0.06f),
                        Color(0xFF009B7D).copy(alpha = 0.03f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF00B894).copy(alpha = 0.15f),
                        Color(0xFF00B894).copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(16.dp),
        content = content
    )
}
