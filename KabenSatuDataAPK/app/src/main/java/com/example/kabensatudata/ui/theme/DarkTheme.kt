package com.example.kabensatudata.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── App Light Color Palette (Teal / GoPay-Dana Style) ──

object AppColors {
    // Primary Palette (Teal - kesehatan/terpercaya)
    val Primary       = Color(0xFF00B894)   // Teal green
    val PrimaryDark   = Color(0xFF009B7D)   // Darker teal (headers)
    val PrimaryLight  = Color(0xFFE8F8F5)   // Very light teal bg

    // Background & Surface
    val Background    = Color(0xFFF7F8FA)   // Off-white
    val Surface       = Color(0xFFFFFFFF)   // White cards

    // Accent
    val Accent        = Color(0xFF00B894)   // Teal (primary accent)
    val AccentOrange  = Color(0xFFFF9F43)   // Warm orange (secondary accent)
    val AccentBlue    = Color(0xFF4A90D9)   // Blue accent

    // Neutral / Gray
    val Secondary     = Color(0xFFA0AEC0)   // Cool gray

    // Text
    val TextPrimary   = Color(0xFF1A1D29)   // Near-black
    val TextSecondary = Color(0xFF6C7A89)   // Medium gray
    val TextTertiary  = Color(0xFFA0AEC0)   // Light gray
    val TextOnDark    = Color.White

    // States
    val Success       = Color(0xFF00B894)   // Teal green
    val Error         = Color(0xFFFF6B6B)   // Coral red
    val Warning       = Color(0xFFFFC107)   // Amber
    val Info          = Color(0xFF4A90D9)   // Blue

    // Derived
    val CardBorder    = Color(0xFFE2E8F0)
    val DividerColor  = Color(0xFFE2E8F0)
    val TopBarBg      = Color(0xFF009B7D)   // Teal dark
    val NavBarBg      = Color(0xFFFFFFFF)   // White
    val NavInactive   = Color(0xFFA0AEC0)   // Gray
    val NavActive     = Color(0xFF00B894)   // Teal
    val NavFabBg      = Color(0xFF00B894)   // Teal FAB

    // Header gradient (lebih dalam untuk glossy)
    val HeaderGradientStart = Color(0xFF00C49A)
    val HeaderGradientEnd   = Color(0xFF006B5E)

    // Service grid icon background colors
    val ServiceBlue      = Color(0xFF4A90D9)
    val ServicePurple    = Color(0xFF9B59B6)
    val ServiceOrange    = Color(0xFFFF9F43)
    val ServiceTeal      = Color(0xFF00B894)
    val ServiceCoral     = Color(0xFFFF6B6B)
    val ServiceTurquoise = Color(0xFF1ABC9C)
    val ServiceIndigo    = Color(0xFF6C5CE7)
    val ServiceAmber     = Color(0xFFFFC107)

    // ─── Glossy System Tokens ─────────────────────────────
    val GlossHighlight    = Color(0x55FFFFFF)  // White 33% — specular highlight
    val GlossHighlightTop = Color(0x40FFFFFF)  // White 25% — top stripe thin
    val GlossBackground   = Color(0x1AFFFFFF)  // White 10% — frosted bg
    val GlossBorder       = Color(0x33FFFFFF)  // White 20% — liquid border
    val GlossStripe       = Color(0x60FFFFFF)  // White 37% — stripe di atas card
    val GlowAlpha         = 0.35f              // Intensitas colored shadow
}

// ─── Top App Bar ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppColors.TopBarBg,
            titleContentColor = AppColors.TextOnDark,
            navigationIconContentColor = AppColors.TextOnDark,
            actionIconContentColor = AppColors.TextOnDark
        )
    )
}

// ─── Wave Top Bar dengan Glossy Shimmer ─────────────────

@Composable
fun WaveTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {}
) {
    val waveHeight = if (subtitle.isEmpty()) 78.dp else 96.dp

    // ── Shimmer bergerak horizontal ──────────────────────
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(waveHeight)
    ) {
        // ── Layer 1: Gradient teal dalam + wave ─────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Gradient lebih dalam (#00C49A → #006B5E)
            val mainGradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00C49A),
                    Color(0xFF006B5E)
                )
            )
            drawRect(brush = mainGradient)

            // Wave path di bawah
            val wavePath = Path().apply {
                moveTo(0f, h * 0.80f)
                cubicTo(
                    w * 0.20f, h * 0.62f,
                    w * 0.55f, h * 1.02f,
                    w * 0.82f, h * 0.78f
                )
                cubicTo(
                    w * 0.91f, h * 0.70f,
                    w * 0.96f, h * 0.68f,
                    w, h * 0.76f
                )
                lineTo(w, 0f)
                lineTo(0f, 0f)
                close()
            }
            drawPath(wavePath, mainGradient)
        }

        // ── Layer 2: Shimmer overlay bergerak ───────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                start = Offset(shimmerOffset * w, 0f),
                end   = Offset((shimmerOffset + 0.45f) * w, h)
            )
            drawRect(brush = shimmerBrush)
        }

        // ── Layer 3: Specular top edge (garis putih atas) ─
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(0.45f),
                        Color.White.copy(0.70f),
                        Color.White.copy(0.45f),
                        Color.Transparent
                    )
                )
            )
        }

        // ── Layer 4: Konten teks + tombol ───────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Kembali",
                    tint = AppColors.TextOnDark
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextOnDark
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = AppColors.TextOnDark.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            Row(content = actions)
        }
    }
}



// ─── List Item Card ─────────────────────────────────────

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = AppColors.Surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, AppColors.CardBorder),
        content = { Column(modifier = Modifier.padding(14.dp), content = content) }
    )
}

// ─── Text Field Colors ──────────────────────────────────

@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppColors.Surface,
    unfocusedContainerColor = AppColors.Surface,
    focusedBorderColor = AppColors.Accent,
    unfocusedBorderColor = AppColors.Secondary.copy(alpha = 0.4f),
    cursorColor = AppColors.Accent,
    focusedTextColor = AppColors.TextPrimary,
    unfocusedTextColor = AppColors.TextPrimary,
    focusedLabelColor = AppColors.Accent,
    unfocusedLabelColor = AppColors.TextSecondary,
    focusedPlaceholderColor = AppColors.TextTertiary,
    unfocusedPlaceholderColor = AppColors.TextTertiary
)

// ─── Switch Colors ──────────────────────────────────────

@Composable
fun appSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = AppColors.Accent,
    checkedThumbColor = Color.White,
    uncheckedTrackColor = AppColors.Secondary.copy(alpha = 0.2f),
    uncheckedThumbColor = AppColors.Secondary,
    uncheckedBorderColor = AppColors.Secondary.copy(alpha = 0.3f)
)
