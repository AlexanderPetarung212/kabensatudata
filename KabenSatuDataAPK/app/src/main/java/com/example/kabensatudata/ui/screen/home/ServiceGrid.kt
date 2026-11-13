package com.example.kabensatudata.ui.screen.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.ui.theme.AppColors
import com.example.kabensatudata.ui.theme.PlusJakartaSansFamily

data class ServiceItem(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun ServiceGrid(
    onNavigateToVillages: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToMedicalHub: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToPoaList: () -> Unit,
    onNavigateToDataHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val services = listOf(
        ServiceItem("Data Desa",   Icons.Default.LocationCity,   AppColors.ServiceBlue,     onNavigateToVillages),
        ServiceItem("Rekam Medis", Icons.Default.MedicalServices, AppColors.ServiceCoral,   onNavigateToMedicalHub),
        ServiceItem("Peta",        Icons.Default.Map,             AppColors.ServiceTurquoise, onNavigateToMap),
        ServiceItem("Kalender",    Icons.Default.CalendarMonth,   AppColors.ServiceIndigo,   onNavigateToCalendar),
        ServiceItem("POA",         Icons.Default.Assignment,      AppColors.ServicePurple,   onNavigateToPoaList),
        ServiceItem("Analisis",    Icons.Default.Analytics,       AppColors.ServiceOrange,   onNavigateToAnalysis),
        ServiceItem("Sinkronisasi",Icons.Default.CloudSync,       AppColors.ServiceAmber,    onNavigateToSync),
        ServiceItem("Manajemen",   Icons.Default.People,          AppColors.ServiceTeal,     onNavigateToDataHub),
    )

    // ── G5: Glossy Container ───────────────────────────
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = AppColors.Primary.copy(0.22f),
                ambientColor = AppColors.Primary.copy(0.10f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.GlossBorder)
    ) {
        Column {
            // Glossy header strip di atas container
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AppColors.Primary.copy(0.5f),
                                Color.White.copy(0.65f),
                                AppColors.Primary.copy(0.3f),
                                Color.White.copy(0.50f),
                                AppColors.Primary.copy(0.2f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier.padding(vertical = 18.dp, horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    services.take(4).forEach { item ->
                        ServiceGridItem(item, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    services.drop(4).forEach { item ->
                        ServiceGridItem(item, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceGridItem(item: ServiceItem, modifier: Modifier = Modifier) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "ServiceItemScale"
    )

    // Shadow elevation animasi saat pressed (glow melebar)
    val shadowElevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "shadowElev"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .pointerInput(item.label) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        item.onClick()
                    }
                )
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── G2: Glossy Icon Circle ───────────────────────
        Box(
            modifier = Modifier
                .size(60.dp)
                .shadow(
                    elevation = shadowElevation.dp,
                    shape = CircleShape,
                    spotColor   = item.color.copy(alpha = AppColors.GlowAlpha),
                    ambientColor = item.color.copy(alpha = 0.15f)
                )
                .clip(CircleShape)
                .background(item.color.copy(alpha = 0.14f)), // Layer 1: Base
            contentAlignment = Alignment.Center
        ) {
            // Layer 2: Inner ring (depth)
            Box(
                modifier = Modifier
                    .size(55.dp)
                    .clip(CircleShape)
                    .background(item.color.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                // Layer 3: Icon
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = item.color,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Layer 4: Specular highlight (atas 50% lingkaran)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.50f)
                    .align(Alignment.TopCenter)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.38f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            item.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PlusJakartaSansFamily,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
