package com.example.kabensatudata.ui.screen.poa

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.PoaListItem
import com.example.kabensatudata.data.remote.dto.PoaMonthSummary
import com.example.kabensatudata.ui.theme.AppColors

private val monthLabels = listOf("J","F","M","A","M","J","J","A","S","O","N","D")

/**
 * Home screen preview section for POA — shows first 3 POAs.
 * Only visible for admin/superadmin users.
 */
@Composable
fun PoaSection(
    viewModel: PoaViewModel,
    onViewAll: () -> Unit,
    onPoaClick: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isBokAdmin) return // Only show for admin

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📋 POA", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            TextButton(onClick = onViewAll) {
                Text("Lihat Semua", fontSize = 12.sp, color = AppColors.Accent)
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = AppColors.Accent)
            }
        }
        Spacer(Modifier.height(4.dp))

        if (uiState.isLoading && uiState.poaList.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AppColors.Accent)
            }
        } else if (uiState.poaList.isEmpty()) {
            Text("Belum ada POA", fontSize = 12.sp, color = AppColors.TextTertiary)
        } else {
            uiState.poaList.take(3).forEach { poa ->
                PoaPreviewCard(poa, onClick = { onPoaClick(poa.id) })
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PoaPreviewCard(poa: PoaListItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        border = BorderStroke(1.dp, AppColors.CardBorder.copy(alpha = 0.15f)),
        shadowElevation = 1.dp,
        onClick = onClick
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(poa.kegiatan, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            // Mini month row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (m in 1..12) {
                    val ms = poa.months[m.toString()] ?: PoaMonthSummary()
                    Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(
                        when { ms.claimed -> Color(0xFF22C55E).copy(alpha = 0.2f); ms.has_activity -> Color(0xFFEAB308).copy(alpha = 0.15f); else -> Color.Transparent }
                    ).then(if (ms.claimed) Modifier.border(1.dp, Color(0xFF22C55E), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center) {
                        Text(if (ms.claimed) "✓" else monthLabels[m - 1], fontSize = 7.sp, fontWeight = FontWeight.Medium,
                            color = when { ms.claimed -> Color(0xFF22C55E); ms.has_activity -> Color(0xFFEAB308); else -> AppColors.TextTertiary })
                    }
                }
            }
        }
    }
}
