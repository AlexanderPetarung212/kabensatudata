package com.example.kabensatudata.ui.screen.poa

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kabensatudata.data.remote.dto.*
import com.example.kabensatudata.ui.screen.calendar.computeParticipantLimit
import com.example.kabensatudata.ui.theme.AppColors
import java.time.LocalDate
import java.time.YearMonth

private val MONTH_NAMES = listOf("Januari","Februari","Maret","April","Mei","Juni","Juli","Agustus","September","Oktober","November","Desember")
private val WEEKDAY_NAMES = listOf("Min","Sen","Sel","Rab","Kam","Jum","Sab")

private enum class CalMode(val label: String, val color: Color) {
    HOLIDAY("Tanggal Merah", Color(0xFFDC2626)),
    KEGIATAN_SPPT("Kegiatan SPPT", Color(0xFF4F46E5)),
    KEGIATAN_SPPD("Kegiatan SPPD", Color(0xFF16A34A)),
    SURAT_SPPT("Surat Keluar SPPT", Color(0xFF2563EB)),
    SURAT_SPPD("Surat Keluar SPPD", Color(0xFF0D9488))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoaMonthMetaSheet(
    month: Int,
    detail: PoaDetailResponse,
    employees: List<EmployeeDto>,
    onDismiss: () -> Unit,
    onSave: (MonthMetaRequest) -> Unit
) {
    val cs = detail.claim_status[month.toString()] ?: return
    val year = detail.year
    val monthName = MONTH_NAMES[month - 1]

    // ─── State ─────────────────────────────────────────────────────
    var noSuratSppt by remember { mutableStateOf(cs.no_surat_sppt ?: "") }
    var noSuratSppd by remember { mutableStateOf(cs.no_surat_sppd ?: "") }
    var tglKegiatanSppt by remember { mutableStateOf(cs.tanggal_kegiatan_sppt ?: "") }
    var tglKegiatanSppd by remember { mutableStateOf(cs.tanggal_kegiatan_sppd ?: "") }
    var tglSuratSppt by remember { mutableStateOf(cs.tanggal_surat_sppt ?: "") }
    var tglSuratSppd by remember { mutableStateOf(cs.tanggal_surat_sppd ?: "") }

    // Holiday set (day numbers)
    val initialHolidays = remember {
        (cs.holidays ?: "").split(",").mapNotNull {
            val s = it.trim()
            // Support YYYY-MM-DD or plain day number
            if (s.contains("-")) {
                try { LocalDate.parse(s).dayOfMonth } catch (_: Exception) { null }
            } else s.toIntOrNull()
        }.toSet()
    }
    var holidayDays by remember { mutableStateOf(initialHolidays) }

    // Selected kegiatan dates
    var spptDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var sppdDays by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Calendar mode
    var mode by remember { mutableStateOf(CalMode.KEGIATAN_SPPT) }
    var warnMsg by remember { mutableStateOf("") }

    // Auto surat flags
    var spptSuratAuto by remember { mutableStateOf(true) }
    var sppdSuratAuto by remember { mutableStateOf(true) }

    // RAB limits for destination villages
    val limitSppt = (detail.rab_targets["darat"] ?: 0)
    val limitSppd = (detail.rab_targets["seberang"] ?: 0)

    // RAB participant limit from RAB items (factor "orang")
    val rabParticipantLimit = remember { computeParticipantLimit(detail.rab_items) }

    // Desa selection
    val daratVillages = detail.villages.filter { it.akses.equals("DARAT", true) }
    val seberangVillages = detail.villages.filter { it.akses.equals("SEBERANG", true) }
    var selectedDarat by remember { mutableStateOf(cs.darat_village_ids.toSet()) }
    var selectedSeberang by remember { mutableStateOf(cs.seberang_village_ids.toSet()) }

    // Participants
    var selectedParticipants by remember { mutableStateOf(cs.participant_ids.toSet()) }
    var participantSearch by remember { mutableStateOf("") }
    var showParticipantPicker by remember { mutableStateOf(false) }

    // Borrowed map
    var borrowedMap by remember { mutableStateOf(cs.borrowed_map.mapKeys { it.key.toLongOrNull() ?: 0L }) }

    // Calendar data
    val ym = remember { YearMonth.of(year, month) }
    val daysInMonth = ym.lengthOfMonth()
    val firstDayOfWeek = ym.atDay(1).dayOfWeek.value % 7 // 0=Sun

    // ─── Auto computation helpers ──────────────────────────────────
    fun prevWorkday(startDay: Int): String {
        var d = LocalDate.of(year, month, startDay).minusDays(1)
        repeat(400) {
            val dayNum = d.dayOfMonth
            val sameMonth = d.monthValue == month && d.year == year
            val isHoliday = if (sameMonth) holidayDays.contains(dayNum) else false
            if (!isHoliday) {
                val mn = MONTH_NAMES[d.monthValue - 1]
                return "${d.dayOfMonth} $mn ${d.year}"
            }
            d = d.minusDays(1)
        }
        return ""
    }

    fun formatKegiatan(days: Set<Int>): String {
        val sorted = days.sorted()
        if (sorted.isEmpty()) return ""
        if (sorted.size == 1) return "${sorted[0]} $monthName $year"
        if (sorted.size == 2) return "${sorted[0]} dan ${sorted[1]} $monthName $year"
        val isConsecutive = sorted.windowed(2).all { (a, b) -> b == a + 1 }
        return if (isConsecutive) {
            "${sorted.first()} s/d ${sorted.last()} $monthName $year"
        } else {
            val head = sorted.dropLast(1).joinToString(", ")
            "$head dan ${sorted.last()} $monthName $year"
        }
    }

    fun recomputeAutoSurat(type: String, days: Set<Int>) {
        if (days.isEmpty()) return
        val minDay = days.min()
        val autoText = prevWorkday(minDay)
        if (type == "sppt" && spptSuratAuto) tglSuratSppt = autoText
        if (type == "sppd" && sppdSuratAuto) tglSuratSppd = autoText
    }

    // ─── Sheet ─────────────────────────────────────────────────────
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {
            // Header
            Text(
                "⚙️ Setting $monthName $year",
                fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(12.dp))

            // ─── No Surat Fields ───────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaField("No. Surat SPPT", noSuratSppt, Modifier.weight(1f)) { noSuratSppt = it }
                MetaField("No. Surat SPPD", noSuratSppd, Modifier.weight(1f)) { noSuratSppd = it }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Mode Toggle Buttons ───────────────────
            Text("📅 Kalender Bulan Ini", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Klik mode → klik tanggal di kalender", fontSize = 11.sp, color = AppColors.TextSecondary)
            Spacer(Modifier.height(8.dp))

            // Mode chips - wrap in two rows for mobile
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalMode.entries.take(3).forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label, fontSize = 10.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = m.color.copy(alpha = 0.15f),
                                selectedLabelColor = m.color
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (mode == m) m.color else Color.Gray.copy(0.3f),
                                selectedBorderColor = m.color,
                                enabled = true, selected = mode == m
                            )
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CalMode.entries.drop(3).forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label, fontSize = 10.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = m.color.copy(alpha = 0.15f),
                                selectedLabelColor = m.color
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (mode == m) m.color else Color.Gray.copy(0.3f),
                                selectedBorderColor = m.color,
                                enabled = true, selected = mode == m
                            )
                        )
                    }
                }
            }

            // Legend
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegendDot(Color(0xFFE0E7FF), Color(0xFF818CF8), "SPPT")
                LegendDot(Color(0xFFD1FAE5), Color(0xFF34D399), "SPPD")
                LegendDot(Color(0xFFFEE2E2), Color(0xFFF87171), "Merah")
            }

            // Warning
            if (warnMsg.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("⚠️ $warnMsg", fontSize = 11.sp, color = Color(0xFFDC2626))
                LaunchedEffect(warnMsg) {
                    kotlinx.coroutines.delay(3000)
                    warnMsg = ""
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Calendar Grid ─────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                border = BorderStroke(1.dp, AppColors.CardBorder.copy(0.3f))
            ) {
                Column(Modifier.padding(8.dp)) {
                    // Weekday headers
                    Row(Modifier.fillMaxWidth()) {
                        WEEKDAY_NAMES.forEach { wd ->
                            Text(
                                wd, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = if (wd == "Min") Color(0xFFDC2626) else AppColors.TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    // Day cells
                    var cellIndex = 0
                    val totalCells = firstDayOfWeek + daysInMonth
                    val rows = (totalCells + 6) / 7

                    for (row in 0 until rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (col in 0..6) {
                                val idx = row * 7 + col
                                val day = if (idx < firstDayOfWeek || idx - firstDayOfWeek >= daysInMonth) null
                                          else idx - firstDayOfWeek + 1

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(cellBg(day, mode, holidayDays, spptDays, sppdDays))
                                        .border(
                                            1.dp,
                                            cellBorder(day, mode, holidayDays, spptDays, sppdDays),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable(enabled = day != null) {
                                            day?.let {
                                                when (mode) {
                                                    CalMode.HOLIDAY -> {
                                                        holidayDays = if (holidayDays.contains(it)) holidayDays - it else holidayDays + it
                                                    }
                                                    CalMode.KEGIATAN_SPPT -> {
                                                        if (sppdDays.contains(it)) {
                                                            warnMsg = "Tanggal ini sudah dipakai SPPD"
                                                        } else if (spptDays.contains(it)) {
                                                            spptDays = spptDays - it
                                                            tglKegiatanSppt = formatKegiatan(spptDays)
                                                            recomputeAutoSurat("sppt", spptDays)
                                                        } else {
                                                            if (limitSppt > 0 && spptDays.size >= limitSppt) {
                                                                warnMsg = "Maks $limitSppt tanggal SPPT"
                                                            } else {
                                                                spptDays = spptDays + it
                                                                tglKegiatanSppt = formatKegiatan(spptDays)
                                                                recomputeAutoSurat("sppt", spptDays)
                                                            }
                                                        }
                                                    }
                                                    CalMode.KEGIATAN_SPPD -> {
                                                        if (spptDays.contains(it)) {
                                                            warnMsg = "Tanggal ini sudah dipakai SPPT"
                                                        } else if (sppdDays.contains(it)) {
                                                            sppdDays = sppdDays - it
                                                            tglKegiatanSppd = formatKegiatan(sppdDays)
                                                            recomputeAutoSurat("sppd", sppdDays)
                                                        } else {
                                                            if (limitSppd > 0 && sppdDays.size >= limitSppd) {
                                                                warnMsg = "Maks $limitSppd tanggal SPPD"
                                                            } else {
                                                                sppdDays = sppdDays + it
                                                                tglKegiatanSppd = formatKegiatan(sppdDays)
                                                                recomputeAutoSurat("sppd", sppdDays)
                                                            }
                                                        }
                                                    }
                                                    CalMode.SURAT_SPPT -> {
                                                        if (!holidayDays.contains(it)) {
                                                            tglSuratSppt = "$it $monthName $year"
                                                            spptSuratAuto = false
                                                        } else warnMsg = "Tanggal merah tidak bisa dipilih"
                                                    }
                                                    CalMode.SURAT_SPPD -> {
                                                        if (!holidayDays.contains(it)) {
                                                            tglSuratSppd = "$it $monthName $year"
                                                            sppdSuratAuto = false
                                                        } else warnMsg = "Tanggal merah tidak bisa dipilih"
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day != null) {
                                        Text(
                                            "$day", fontSize = 12.sp,
                                            fontWeight = if (holidayDays.contains(day)) FontWeight.Bold else FontWeight.Normal,
                                            color = cellTextColor(day, holidayDays, spptDays, sppdDays)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Summary below calendar
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Ringkas Kegiatan", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text("SPPT: ${if (spptDays.isNotEmpty()) formatKegiatan(spptDays) else "-"}", fontSize = 10.sp, color = Color(0xFF4F46E5))
                            Text("SPPD: ${if (sppdDays.isNotEmpty()) formatKegiatan(sppdDays) else "-"}", fontSize = 10.sp, color = Color(0xFF16A34A))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Rekomendasi Surat", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Text("SPPT: ${tglSuratSppt.ifBlank { "-" }}", fontSize = 10.sp, color = Color(0xFF2563EB))
                            Text("SPPD: ${tglSuratSppd.ifBlank { "-" }}", fontSize = 10.sp, color = Color(0xFF0D9488))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Tanggal Fields (editable override) ────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaField("Tgl Kegiatan SPPT", tglKegiatanSppt, Modifier.weight(1f)) { tglKegiatanSppt = it }
                MetaField("Tgl Kegiatan SPPD", tglKegiatanSppd, Modifier.weight(1f)) { tglKegiatanSppd = it }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaField("Tgl Surat SPPT", tglSuratSppt, Modifier.weight(1f)) { tglSuratSppt = it; spptSuratAuto = false }
                MetaField("Tgl Surat SPPD", tglSuratSppd, Modifier.weight(1f)) { tglSuratSppd = it; sppdSuratAuto = false }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Desa Selection ────────────────────────
            if (daratVillages.isNotEmpty()) {
                SectionHeader("🏘️ Desa Darat (${selectedDarat.size})")
                daratVillages.forEach { v ->
                    CompactCheckbox(v.nama, selectedDarat.contains(v.id)) {
                        selectedDarat = if (selectedDarat.contains(v.id)) selectedDarat - v.id else selectedDarat + v.id
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (seberangVillages.isNotEmpty()) {
                SectionHeader("⛵ Desa Seberang (${selectedSeberang.size})")
                seberangVillages.forEach { v ->
                    CompactCheckbox(v.nama, selectedSeberang.contains(v.id)) {
                        selectedSeberang = if (selectedSeberang.contains(v.id)) selectedSeberang - v.id else selectedSeberang + v.id
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ─── Peserta Multi-Select ──────────────────
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader("👥 Peserta Bulan (${selectedParticipants.size})")
                // RAB limit badge
                if (rabParticipantLimit > 0) {
                    val exceeded = selectedParticipants.size > rabParticipantLimit
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (exceeded) Color(0xFFEF4444).copy(alpha = 0.12f) else Color(0xFF22C55E).copy(alpha = 0.1f)
                    ) {
                        Text(
                            "Maks RAB: $rabParticipantLimit orang",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (exceeded) Color(0xFFEF4444) else Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // RAB limit warning
            val participantsExceedLimit = rabParticipantLimit > 0 && selectedParticipants.size > rabParticipantLimit
            if (participantsExceedLimit) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Jumlah peserta (${selectedParticipants.size}) melebihi batas RAB ($rabParticipantLimit orang). Kurangi peserta untuk bisa menyimpan.",
                            fontSize = 11.sp,
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Selected chips
            if (selectedParticipants.isNotEmpty()) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    selectedParticipants.forEach { pid ->
                        val emp = employees.find { it.id == pid }
                        val name = emp?.nama ?: "ID $pid"
                        InputChip(
                            selected = true,
                            onClick = { selectedParticipants = selectedParticipants - pid },
                            label = { Text(name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Picker button
            OutlinedButton(
                onClick = { showParticipantPicker = !showParticipantPicker },
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Pilih Pegawai (${selectedParticipants.size} dipilih)", fontSize = 12.sp)
            }

            // Dropdown list
            if (showParticipantPicker) {
                Card(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AppColors.CardBorder.copy(0.3f))
                ) {
                    Column {
                        OutlinedTextField(
                            value = participantSearch,
                            onValueChange = { participantSearch = it },
                            placeholder = { Text("Cari pegawai...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                            modifier = Modifier.fillMaxWidth().padding(4.dp),
                            singleLine = true, shape = RoundedCornerShape(6.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        val filtered = employees.filter {
                            participantSearch.isBlank() ||
                            it.nama.contains(participantSearch, true) ||
                            (it.jabatan ?: "").contains(participantSearch, true)
                        }
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            filtered.forEach { emp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedParticipants = if (selectedParticipants.contains(emp.id))
                                                selectedParticipants - emp.id
                                            else selectedParticipants + emp.id
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedParticipants.contains(emp.id),
                                        onCheckedChange = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(emp.nama, fontSize = 12.sp)
                                        if (!emp.jabatan.isNullOrBlank()) {
                                            Text(emp.jabatan, fontSize = 10.sp, color = AppColors.TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── Pinjam Nama ───────────────────────────
            if (selectedParticipants.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionHeader("🔄 Pinjam Nama per Peserta")
                Text("Nama dokumen akan memakai pegawai terpilih", fontSize = 10.sp, color = AppColors.TextSecondary)
                Spacer(Modifier.height(4.dp))

                selectedParticipants.forEach { pid ->
                    val empName = employees.find { it.id == pid }?.nama ?: "ID $pid"
                    val currentBorrowed = borrowedMap[pid]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(empName, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("→", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        // Simple dropdown-like
                        var expanded by remember { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth().height(30.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                val borrowedName = if (currentBorrowed != null) employees.find { it.id == currentBorrowed }?.nama ?: "-" else "Tidak Pinjam"
                                Text(borrowedName, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Tidak Pinjam", fontSize = 12.sp) },
                                    onClick = {
                                        borrowedMap = borrowedMap - pid
                                        expanded = false
                                    }
                                )
                                employees.forEach { emp ->
                                    DropdownMenuItem(
                                        text = { Text(emp.nama, fontSize = 12.sp) },
                                        onClick = {
                                            borrowedMap = borrowedMap + (pid to emp.id)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Save Button ───────────────────────────
            // Blocked if participants exceed RAB limit
            val canSave = !(rabParticipantLimit > 0 && selectedParticipants.size > rabParticipantLimit)

            if (!canSave) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚫", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tidak bisa menyimpan: peserta melebihi batas RAB ($rabParticipantLimit orang)",
                            fontSize = 12.sp,
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val holidayCsv = holidayDays.sorted().joinToString(",") { d ->
                        "%04d-%02d-%02d".format(year, month, d)
                    }
                    val borrowedStr = if (borrowedMap.isNotEmpty()) {
                        borrowedMap.entries.joinToString(",") { "${it.key}:${it.value}" }
                    } else null

                    onSave(MonthMetaRequest(
                        month = month,
                        no_surat_sppt = noSuratSppt.ifBlank { null },
                        no_surat_sppd = noSuratSppd.ifBlank { null },
                        tanggal_kegiatan_sppt = tglKegiatanSppt.ifBlank { null },
                        tanggal_kegiatan_sppd = tglKegiatanSppd.ifBlank { null },
                        tanggal_surat_sppt = tglSuratSppt.ifBlank { null },
                        tanggal_surat_sppd = tglSuratSppd.ifBlank { null },
                        darat_village_ids = selectedDarat.joinToString(",").ifBlank { null },
                        seberang_village_ids = selectedSeberang.joinToString(",").ifBlank { null },
                        participant_ids = selectedParticipants.joinToString(",").ifBlank { null },
                        holidays = holidayCsv.ifBlank { null },
                        borrowed_map = borrowedStr
                    ))
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canSave) AppColors.Accent else Color.Gray,
                    disabledContainerColor = Color(0xFF9CA3AF)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("💾 Simpan Pengaturan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

// ─── Helper Composables ──────────────────────────────────────────────────────

@Composable
private fun MetaField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, fontSize = 10.sp) },
        modifier = modifier.padding(vertical = 3.dp),
        singleLine = true, shape = RoundedCornerShape(8.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Accent,
            unfocusedBorderColor = AppColors.CardBorder.copy(alpha = 0.4f)
        )
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextPrimary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun CompactCheckbox(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun LegendDot(bg: Color, border: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(bg).border(1.dp, border, CircleShape)
        )
        Spacer(Modifier.width(3.dp))
        Text(label, fontSize = 9.sp, color = AppColors.TextSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        content = { content() }
    )
}

// ─── Cell Color Helpers ──────────────────────────────────────────────────────

private fun cellBg(day: Int?, mode: CalMode, holidays: Set<Int>, sppt: Set<Int>, sppd: Set<Int>): Color {
    if (day == null) return Color.Transparent
    val isSppt = sppt.contains(day)
    val isSppd = sppd.contains(day)
    val isHol = holidays.contains(day)
    return when {
        isSppt && isSppd -> Color(0xFFFEF3C7) // amber overlay
        isSppt -> Color(0xFFE0E7FF)             // indigo
        isSppd -> Color(0xFFD1FAE5)             // green
        isHol && mode == CalMode.HOLIDAY -> Color(0xFFFEE2E2) // red
        isHol -> Color(0xFFFFF7ED)               // subtle red hint
        else -> Color.White
    }
}

private fun cellBorder(day: Int?, mode: CalMode, holidays: Set<Int>, sppt: Set<Int>, sppd: Set<Int>): Color {
    if (day == null) return Color.Transparent
    val isSppt = sppt.contains(day)
    val isSppd = sppd.contains(day)
    val isHol = holidays.contains(day)
    return when {
        isSppt && isSppd -> Color(0xFFFBBF24)
        isSppt -> Color(0xFF818CF8)
        isSppd -> Color(0xFF34D399)
        isHol -> Color(0xFFF87171)
        else -> Color(0xFFE5E7EB)
    }
}

private fun cellTextColor(day: Int, holidays: Set<Int>, sppt: Set<Int>, sppd: Set<Int>): Color {
    val isSppt = sppt.contains(day)
    val isSppd = sppd.contains(day)
    val isHol = holidays.contains(day)
    return when {
        isSppt && isSppd -> Color(0xFF92400E)
        isSppt -> Color(0xFF4338CA)
        isSppd -> Color(0xFF059669)
        isHol -> Color(0xFFDC2626)
        else -> Color(0xFF374151)
    }
}
