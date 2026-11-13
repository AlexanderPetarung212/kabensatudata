package com.example.kabensatudata.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.PoaDetailResponse
import com.example.kabensatudata.data.remote.dto.RabItemDto
import com.example.kabensatudata.ui.theme.AppColors

private fun formatRupiah(amount: Double): String {
    return "Rp " + String.format("%,.0f", amount).replace(",", ".")
}

/** Format angka faktor: bilangan bulat tanpa desimal, pecahan dengan 1-2 digit */
private fun formatFactorValue(raw: Any?): String {
    if (raw == null) return ""
    val d = when (raw) {
        is Double -> raw
        is Float  -> raw.toDouble()
        is Int    -> raw.toDouble()
        is Long   -> raw.toDouble()
        is String -> raw.toDoubleOrNull() ?: return raw
        else      -> raw.toString().toDoubleOrNull() ?: return raw.toString()
    }
    return if (d == kotlin.math.floor(d) && !d.isInfinite()) d.toInt().toString() else d.toBigDecimal().stripTrailingZeros().toPlainString()
}

/**
 * Hitung unit_price dari item RAB.
 * 1. Jika unit_price field tersedia & > 0 → gunakan langsung.
 * 2. Fallback: subtotal ÷ product_of_all_factors (selalu akurat).
 */
fun computeUnitPriceFromItem(item: RabItemDto): Double {
    val directPrice = item.unit_price ?: 0.0
    if (directPrice > 0.0) return directPrice
    // Fallback: hitung dari subtotal / (perkalian semua faktor)
    val factors = item.factors ?: return item.subtotal
    val product = factors.fold(1.0) { acc, f ->
        val v = when (val raw = f["value"]) {
            is Double -> raw
            is Int    -> raw.toDouble()
            is Long   -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: 1.0
            else      -> raw?.toString()?.toDoubleOrNull() ?: 1.0
        }
        if (v > 0) acc * v else acc
    }
    return if (product > 0 && item.subtotal > 0) item.subtotal / product else item.subtotal
}

/**
 * Bottom sheet menampilkan detail item-item RAB dari suatu kegiatan.
 * READ-ONLY untuk semua user.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RabDetailSheet(
    isLoading: Boolean,
    poaDetail: PoaDetailResponse?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "📋 Detail RAB",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = AppColors.TextPrimary
                    )
                    if (poaDetail != null) {
                        Text(
                            poaDetail.kegiatan,
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary,
                            maxLines = 2
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Tutup", tint = AppColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))
            Divider(color = AppColors.CardBorder.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AppColors.Accent, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Memuat detail RAB...", fontSize = 13.sp, color = AppColors.TextSecondary)
                        }
                    }
                }

                poaDetail == null -> {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Data tidak tersedia", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                }

                else -> {
                    // Participant limit badge
                    if (poaDetail.rab_items.isNotEmpty()) {
                        val limit = computeParticipantLimit(poaDetail.rab_items)
                        if (limit > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4F46E5).copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("👥", fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Maks peserta sesuai RAB: $limit orang",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // RAB items table
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (poaDetail.rab_items.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Tidak ada item RAB", fontSize = 13.sp, color = AppColors.TextSecondary)
                            }
                        } else {
                            // Table header
                            RabTableHeader()
                            Divider(color = AppColors.CardBorder.copy(0.3f), thickness = 0.5.dp)

                            var totalAll = 0.0
                            poaDetail.rab_items.forEachIndexed { idx, item ->
                                RabTableRow(index = idx + 1, item = item)
                                Divider(color = AppColors.CardBorder.copy(0.15f), thickness = 0.5.dp)
                                totalAll += item.subtotal
                            }

                            // Total row
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.Accent.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Total RAB",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        formatRupiah(totalAll),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = AppColors.Accent
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RabTableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AppColors.Secondary.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("No", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary, modifier = Modifier.width(24.dp))
        Text("Item", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary, modifier = Modifier.weight(1f))
        Text("Faktor", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
        Text("Harga", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
        Text("Subtotal", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary, modifier = Modifier.width(80.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun RabTableRow(index: Int, item: RabItemDto) {
    val factors = item.factors ?: emptyList()
    val factorText = if (factors.isEmpty()) "-" else
        factors.joinToString(" \u00d7 ") { f ->
            val v = formatFactorValue(f["value"])
            val lbl = (f["label"] ?: f["key"])?.toString()?.trim() ?: ""
            if (v.isNotEmpty()) "$v $lbl".trim() else lbl
        }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("$index", fontSize = 11.sp, color = AppColors.TextSecondary, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.label ?: "-",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
            if (item.type != null) {
                Text(item.type, fontSize = 9.sp, color = AppColors.TextTertiary)
            }
        }
        Text(
            factorText,
            fontSize = 9.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
        Text(
            formatRupiah(computeUnitPriceFromItem(item)),
            fontSize = 10.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End
        )
        Text(
            formatRupiah(item.subtotal),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.End
        )
    }
}

/** Compute participant limit from RAB items (max value of factor labeled "orang") */
fun computeParticipantLimit(items: List<RabItemDto>): Int {
    var limit = 0
    for (item in items) {
        val factors = item.factors ?: continue
        for (f in factors) {
            val label = ((f["label"] ?: f["key"])?.toString())?.lowercase() ?: continue
            if (label.contains("orang")) {
                val v = when (val raw = f["value"]) {
                    is Double -> raw.toInt()
                    is Int -> raw
                    is Long -> raw.toInt()
                    is String -> raw.toIntOrNull() ?: 0
                    else -> raw?.toString()?.toDoubleOrNull()?.toInt() ?: 0
                }
                if (v > limit) limit = v
            }
        }
    }
    return limit
}
