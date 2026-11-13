package com.example.kabensatudata.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.PlusJakartaSansFamily

// ─── AvatarCircle — Inisial nama dalam lingkaran ─────────

@Composable
fun AvatarCircle(
    name: String,
    size: Dp = 44.dp,
    color: Color = AppColors.Primary,
    modifier: Modifier = Modifier
) {
    val initials = name
        .trim()
        .split(" ")
        .filter { it.isNotEmpty() }
        .take(2)
        .map { it.first().uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        // Inner ring
        Box(
            modifier = Modifier
                .size(size * 0.90f)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = (size.value * 0.33f).sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── EmptyState — Tampilan kosong dengan ikon & teks ─────

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    iconTint: Color = AppColors.TextTertiary
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(
            animationSpec = tween(400),
            initialOffsetY = { it / 5 }
        )
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(AppColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─── StaggeredItem — Animasi masuk bertahap ──────────────

@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val delayMs = (index * 55).coerceAtMost(400)

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            initialOffsetY = { it / 4 }
        ),
        modifier = modifier
    ) {
        content()
    }
}
