package com.example.kabensatudata.ui.screen.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.MediaPlayer
import com.example.kabensatudata.R
import com.example.kabensatudata.data.local.entity.*
import com.example.kabensatudata.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Period

// ═══════════════════════════════════════════════════════
// ─── Data Models ──────────────────────────────────────
// ═══════════════════════════════════════════════════════

data class AnalysisRow(
    val villageName: String,
    val buildingNumber: String,
    val familyNumber: String,
    val nik: String,
    val name: String,
    val birthDate: String,
    val gender: String,
    val age: String,
    val relationship: String,
    val education: String,
    val maritalStatus: String,
    val religion: String,
    val occupation: String,
    val isPregnant: String,
    val hasJkn: String,
    val isSmoker: String,
    val hasTbc: String,
    val takesTbMed: String,
    val hasChronicCough: String,
    val hasHypertension: String,
    val takesHypertensionMed: String,
    val usesContraception: String,
    val gaveBirthInFaskes: String,
    val exclusiveBreastfeeding: String,
    val completeImmunization: String,
    val growthMonitoring: String,
    val hasCleanWater: String,
    val useWater: String,
    val isWaterProtected: String,
    val hasToilet: String,
    val isToiletSanitary: String,
    val useToilet: String,
    // Raw booleans for cross-tab
    val rawGender: String = "",
    val rawHasJkn: Boolean = false,
    val rawIsSmoker: Boolean = false,
    val rawHasTbc: Boolean = false,
    val rawHasHypertension: Boolean = false,
    val rawUsesContraception: Boolean = false,
    val rawIsPregnant: Boolean = false,
    val rawHasCleanWater: Boolean = false,
    val rawHasToilet: Boolean = false,
    val rawEducation: String = "",
    val rawOccupation: String = "",
    val rawVillageName: String = "",
    val rawAgeGroup: String = ""
)

data class AnalysisState(
    val filters: Map<String, String> = emptyMap(),
    val selectedVillageId: Long? = null,
    val results: List<AnalysisRow> = emptyList(),
    val resultCount: Int = 0,
    val isAnalyzing: Boolean = false,
    val hasAnalyzed: Boolean = false,
    val expandedGroups: Set<String> = emptySet(),
    // Pagination
    val currentPage: Int = 0,
    val pageSize: Int = 50,
    // Success notification
    val showSuccessNotif: Boolean = false,
    // Cross-tab
    val crossTabVar1: String = "",
    val crossTabVar2: String = "",
    val crossTabHeaders: List<String> = emptyList(),
    val crossTabRows: List<List<String>> = emptyList(),
    val showCrossTab: Boolean = false,
    // Villages list for filter
    val villages: List<Pair<Long, String>> = emptyList(),
    // Export
    val isExporting: Boolean = false
) {
    val totalPages: Int get() = if (results.isEmpty()) 0 else ((results.size - 1) / pageSize) + 1
    val pagedResults: List<AnalysisRow> get() {
        val start = currentPage * pageSize
        val end = minOf(start + pageSize, results.size)
        return if (start < results.size) results.subList(start, end) else emptyList()
    }
}

// ═══════════════════════════════════════════════════════
// ─── Analysis Engine ──────────────────────────────────
// ═══════════════════════════════════════════════════════

object AnalysisEngine {

    private fun boolStr(v: Boolean?): String = when (v) {
        true -> "Ya"
        false -> "Tidak"
        null -> "-"
    }

    private fun ageGroup(birthDate: String?): String {
        if (birthDate.isNullOrBlank()) return "-"
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            val age = Period.between(birth, LocalDate.now()).years
            when {
                age <= 5 -> "Balita (0-5)"
                age <= 12 -> "Anak (6-12)"
                age <= 17 -> "Remaja (13-17)"
                age <= 45 -> "Dewasa (18-45)"
                else -> "Lansia (>45)"
            }
        } catch (_: Exception) { "-" }
    }

    private fun ageYears(birthDate: String?): Int? {
        if (birthDate.isNullOrBlank()) return null
        return try {
            val birth = LocalDate.parse(birthDate.take(10))
            Period.between(birth, LocalDate.now()).years
        } catch (_: Exception) { null }
    }

    private fun ageDisplay(birthDate: String?): String {
        val yrs = ageYears(birthDate) ?: return "-"
        if (yrs == 0) {
            try {
                val birth = LocalDate.parse(birthDate!!.take(10))
                val months = java.time.temporal.ChronoUnit.MONTHS.between(birth, LocalDate.now())
                return "${months} bulan"
            } catch (_: Exception) {}
        }
        return "$yrs tahun"
    }

    fun buildRows(
        villages: List<VillageEntity>,
        buildings: List<BuildingEntity>,
        families: List<FamilyEntity>,
        members: List<FamilyMemberEntity>
    ): List<AnalysisRow> {
        val villageMap = villages.associateBy { it.id }
        val buildingMap = buildings.associateBy { it.id }
        val familyMap = families.associateBy { it.id }
        // Map buildingId -> villageId
        val buildingVillageMap = buildings.associate { it.id to it.villageId }
        // Map familyId -> buildingId
        val familyBuildingMap = families.associate { it.id to it.buildingId }

        return members.map { m ->
            val family = familyMap[m.familyId]
            val buildingId = familyBuildingMap[m.familyId]
            val building = buildingId?.let { buildingMap[it] }
            val villageId = buildingId?.let { buildingVillageMap[it] }
            val village = villageId?.let { villageMap[it] }
            val vName = village?.name ?: "-"
            val bNum = building?.buildingNumber?.let { "B-${it.toString().padStart(3, '0')}" } ?: "-"
            val fNum = family?.familyNumber?.let { "KK-${it.toString().padStart(3, '0')}" } ?: "-"

            AnalysisRow(
                villageName = vName,
                buildingNumber = bNum,
                familyNumber = fNum,
                nik = m.nik ?: "-",
                name = m.name,
                birthDate = m.birthDate?.take(10) ?: "-",
                gender = m.gender ?: "-",
                age = ageDisplay(m.birthDate),
                relationship = m.relationship ?: "-",
                education = m.education ?: "-",
                maritalStatus = m.maritalStatus ?: "-",
                religion = m.religion ?: "-",
                occupation = m.occupation ?: "-",
                isPregnant = boolStr(m.isPregnant),
                hasJkn = boolStr(m.hasJkn),
                isSmoker = boolStr(m.isSmoker),
                hasTbc = boolStr(m.hasTuberculosis),
                takesTbMed = boolStr(m.takesTbMedicationRegularly),
                hasChronicCough = boolStr(m.hasChronicCough),
                hasHypertension = boolStr(m.hasHypertension),
                takesHypertensionMed = boolStr(m.takesHypertensionMedicationRegularly),
                usesContraception = boolStr(m.usesContraception),
                gaveBirthInFaskes = boolStr(m.gaveBirthInHealthFacility),
                exclusiveBreastfeeding = boolStr(m.exclusiveBreastfeeding),
                completeImmunization = boolStr(m.completeImmunization),
                growthMonitoring = boolStr(m.growthMonitoring),
                hasCleanWater = boolStr(family?.hasCleanWater),
                useWater = boolStr(m.useWater),
                isWaterProtected = boolStr(family?.isWaterProtected),
                hasToilet = boolStr(family?.hasToilet),
                isToiletSanitary = boolStr(family?.isToiletSanitary),
                useToilet = boolStr(m.useToilet),
                // Raw values for cross-tab
                rawGender = m.gender ?: "-",
                rawHasJkn = m.hasJkn == true,
                rawIsSmoker = m.isSmoker == true,
                rawHasTbc = m.hasTuberculosis == true,
                rawHasHypertension = m.hasHypertension == true,
                rawUsesContraception = m.usesContraception == true,
                rawIsPregnant = m.isPregnant == true,
                rawHasCleanWater = family?.hasCleanWater == true,
                rawHasToilet = family?.hasToilet == true,
                rawEducation = m.education ?: "-",
                rawOccupation = m.occupation ?: "-",
                rawVillageName = vName,
                rawAgeGroup = ageGroup(m.birthDate)
            )
        }
    }

    fun applyFilters(rows: List<AnalysisRow>, filters: Map<String, String>): List<AnalysisRow> {
        var result = rows
        filters.forEach { (key, value) ->
            if (value.isBlank()) return@forEach
            result = when (key) {
                "gender" -> result.filter { it.rawGender == value }
                "education" -> result.filter { it.education == value }
                "marital_status" -> result.filter { it.maritalStatus == value }
                "religion" -> result.filter { it.religion == value }
                "occupation" -> result.filter { it.occupation == value }
                "is_pregnant" -> result.filter { it.isPregnant == if (value == "1") "Ya" else "Tidak" }
                "has_jkn" -> result.filter { it.hasJkn == if (value == "1") "Ya" else "Tidak" }
                "is_smoker" -> result.filter { it.isSmoker == if (value == "1") "Ya" else "Tidak" }
                "has_tbc" -> result.filter { it.hasTbc == if (value == "1") "Ya" else "Tidak" }
                "takes_tb_med" -> result.filter { it.takesTbMed == if (value == "1") "Ya" else "Tidak" }
                "has_chronic_cough" -> result.filter { it.hasChronicCough == if (value == "1") "Ya" else "Tidak" }
                "has_hypertension" -> result.filter { it.hasHypertension == if (value == "1") "Ya" else "Tidak" }
                "takes_hypertension_med" -> result.filter { it.takesHypertensionMed == if (value == "1") "Ya" else "Tidak" }
                "uses_contraception" -> result.filter { it.usesContraception == if (value == "1") "Ya" else "Tidak" }
                "gave_birth_faskes" -> result.filter { it.gaveBirthInFaskes == if (value == "1") "Ya" else "Tidak" }
                "exclusive_breastfeeding" -> result.filter { it.exclusiveBreastfeeding == if (value == "1") "Ya" else "Tidak" }
                "complete_immunization" -> result.filter { it.completeImmunization == if (value == "1") "Ya" else "Tidak" }
                "growth_monitoring" -> result.filter { it.growthMonitoring == if (value == "1") "Ya" else "Tidak" }
                "has_clean_water" -> result.filter { it.hasCleanWater == if (value == "1") "Ya" else "Tidak" }
                "use_water" -> result.filter { it.useWater == if (value == "1") "Ya" else "Tidak" }
                "is_water_protected" -> result.filter { it.isWaterProtected == if (value == "1") "Ya" else "Tidak" }
                "has_toilet" -> result.filter { it.hasToilet == if (value == "1") "Ya" else "Tidak" }
                "is_toilet_sanitary" -> result.filter { it.isToiletSanitary == if (value == "1") "Ya" else "Tidak" }
                "use_toilet" -> result.filter { it.useToilet == if (value == "1") "Ya" else "Tidak" }
                "building_number" -> result.filter { it.buildingNumber.contains(value) }
                "family_number" -> result.filter { it.familyNumber.contains(value) }
                "age_range" -> {
                    if (value == "custom") result // User belum isi range, skip filter
                    else {
                        val parts = value.split("-")
                        if (parts.size == 2) {
                            val min = parts[0].replace("+", "").toIntOrNull() ?: 0
                            val max = parts[1].toIntOrNull() ?: 200
                            result.filter {
                                val yrs = it.age.replace(" tahun", "").replace(" bulan", "").toIntOrNull()
                                yrs != null && yrs in min..max
                            }
                        } else result
                    }
                }
                "is_head_of_family" -> when (value) {
                    "1" -> result.filter { it.relationship.contains("Kepala", true) }
                    "0" -> result.filter { !it.relationship.contains("Kepala", true) }
                    else -> result
                }
                else -> result
            }
        }
        return result
    }

    // ─── Cross-Tab Computation ────────────────────────
    val crossTabVariables = listOf(
        "Jenis Kelamin" to "gender",
        "Kelompok Usia" to "age_group",
        "JKN" to "jkn",
        "Perokok" to "smoker",
        "TBC" to "tbc",
        "Hipertensi" to "hypertension",
        "KB" to "kb",
        "Hamil" to "pregnant",
        "Air Bersih" to "clean_water",
        "Jamban" to "toilet",
        "Pendidikan" to "education",
        "Pekerjaan" to "occupation",
        "Desa" to "village"
    )

    private fun getCrossTabValue(row: AnalysisRow, variable: String): String {
        return when (variable) {
            "gender" -> row.rawGender
            "age_group" -> row.rawAgeGroup
            "jkn" -> if (row.rawHasJkn) "Ya" else "Tidak"
            "smoker" -> if (row.rawIsSmoker) "Ya" else "Tidak"
            "tbc" -> if (row.rawHasTbc) "Ya" else "Tidak"
            "hypertension" -> if (row.rawHasHypertension) "Ya" else "Tidak"
            "kb" -> if (row.rawUsesContraception) "Ya" else "Tidak"
            "pregnant" -> if (row.rawIsPregnant) "Ya" else "Tidak"
            "clean_water" -> if (row.rawHasCleanWater) "Ya" else "Tidak"
            "toilet" -> if (row.rawHasToilet) "Ya" else "Tidak"
            "education" -> row.rawEducation
            "occupation" -> row.rawOccupation
            "village" -> row.rawVillageName
            else -> "-"
        }
    }

    fun computeCrossTab(
        rows: List<AnalysisRow>,
        var1: String,
        var2: String
    ): Pair<List<String>, List<List<String>>> {
        if (var1.isBlank() || var2.isBlank() || var1 == var2) return emptyList<String>() to emptyList()

        // Get unique values for each variable
        val var1Values = rows.map { getCrossTabValue(it, var1) }.distinct().sorted()
        val var2Values = rows.map { getCrossTabValue(it, var2) }.distinct().sorted()

        // Build matrix
        val matrix = mutableMapOf<Pair<String, String>, Int>()
        rows.forEach { row ->
            val v1 = getCrossTabValue(row, var1)
            val v2 = getCrossTabValue(row, var2)
            matrix[v1 to v2] = (matrix[v1 to v2] ?: 0) + 1
        }

        // Build header row
        val headers = mutableListOf("").apply {
            addAll(var2Values)
            add("Total")
        }

        // Build data rows
        val dataRows = var1Values.map { v1 ->
            val row = mutableListOf(v1)
            var rowTotal = 0
            var2Values.forEach { v2 ->
                val count = matrix[v1 to v2] ?: 0
                row.add(count.toString())
                rowTotal += count
            }
            row.add(rowTotal.toString())
            row
        }

        // Add total row
        val totalRow = mutableListOf("Total")
        var grandTotal = 0
        var2Values.forEach { v2 ->
            val colTotal = var1Values.sumOf { v1 -> matrix[v1 to v2] ?: 0 }
            totalRow.add(colTotal.toString())
            grandTotal += colTotal
        }
        totalRow.add(grandTotal.toString())

        val allRows = dataRows + listOf(totalRow)
        return headers to allRows
    }
}

// ═══════════════════════════════════════════════════════
// ─── Filter Definitions ───────────────────────────────
// ═══════════════════════════════════════════════════════

data class FilterOption(val key: String, val label: String)
data class FilterDef(val key: String, val label: String, val type: String, val options: List<FilterOption> = emptyList())
data class FilterGroup(val key: String, val title: String, val icon: @Composable () -> Unit, val filters: List<FilterDef>)

fun getFilterGroups(): List<FilterGroup> = listOf(
    FilterGroup("location", "Informasi Lokasi", { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp)) }, listOf(
        FilterDef("building_number", "Nomor Bangunan", "text"),
        FilterDef("family_number", "Nomor KK", "text")
    )),
    FilterGroup("personal", "Informasi Personal", { Icon(Icons.Default.Person, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp)) }, listOf(
        FilterDef("gender", "Jenis Kelamin", "select", listOf(FilterOption("Laki-laki", "Laki-laki"), FilterOption("Perempuan", "Perempuan"))),
        FilterDef("is_head_of_family", "Status dalam Keluarga", "select", listOf(FilterOption("1", "Kepala Keluarga"), FilterOption("0", "Bukan Kepala"), FilterOption("all", "Semua"))),
        FilterDef("age_range", "Kelompok Usia", "select", listOf(
            FilterOption("0-5", "Balita (0-5)"), FilterOption("6-12", "Anak (6-12)"),
            FilterOption("13-17", "Remaja (13-17)"), FilterOption("18-45", "Dewasa (18-45)"),
            FilterOption("46-200", "Lansia (>45)"),
            FilterOption("custom", "Kustom...")
        )),
        FilterDef("education", "Pendidikan", "select", listOf(
            FilterOption("Tidak Pernah Sekolah", "Tidak Pernah Sekolah"), FilterOption("Tamat SD/MI", "Tamat SD/MI"),
            FilterOption("Tamat SMP/MTs", "Tamat SMP/MTs"), FilterOption("Tamat SMA/MA/SMK", "Tamat SMA/MA/SMK"),
            FilterOption("Tamat D1/D2/D3", "Tamat D1/D2/D3"), FilterOption("Tamat D4/S1", "Tamat D4/S1"), FilterOption("Tamat S2/S3", "Tamat S2/S3")
        )),
        FilterDef("marital_status", "Status Perkawinan", "select", listOf(
            FilterOption("Belum Kawin", "Belum Kawin"), FilterOption("Kawin", "Kawin"),
            FilterOption("Cerai Hidup", "Cerai Hidup"), FilterOption("Cerai Mati", "Cerai Mati")
        )),
        FilterDef("religion", "Agama", "select", listOf(
            FilterOption("Islam", "Islam"), FilterOption("Kristen", "Kristen"), FilterOption("Katolik", "Katolik"),
            FilterOption("Hindu", "Hindu"), FilterOption("Buddha", "Buddha"), FilterOption("Konghucu", "Konghucu")
        )),
        FilterDef("occupation", "Pekerjaan", "select", listOf(
            FilterOption("Tidak Kerja", "Tidak Kerja"), FilterOption("Sekolah", "Sekolah"),
            FilterOption("ASN", "ASN"), FilterOption("TNI/Polri", "TNI/Polri"),
            FilterOption("Pegawai Swasta", "Pegawai Swasta"), FilterOption("Nelayan", "Nelayan"),
            FilterOption("Petani", "Petani"), FilterOption("IRT", "IRT"), FilterOption("Lainnya", "Lainnya")
        ))
    )),
    FilterGroup("health", "Kesehatan", { Icon(Icons.Default.LocalHospital, null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp)) }, listOf(
        FilterDef("is_pregnant", "Status Kehamilan", "boolean"),
        FilterDef("has_jkn", "Memiliki JKN", "boolean"),
        FilterDef("is_smoker", "Perokok", "boolean"),
        FilterDef("has_tbc", "Menderita TBC", "boolean"),
        FilterDef("takes_tb_med", "Minum Obat TBC Teratur", "boolean"),
        FilterDef("has_chronic_cough", "Batuk Kronis", "boolean"),
        FilterDef("has_hypertension", "Hipertensi", "boolean"),
        FilterDef("takes_hypertension_med", "Minum Obat Hipertensi", "boolean")
    )),
    FilterGroup("maternal", "Kesehatan Ibu & Anak", { Icon(Icons.Default.ChildCare, null, tint = Color(0xFFEC4899), modifier = Modifier.size(20.dp)) }, listOf(
        FilterDef("uses_contraception", "Menggunakan KB", "boolean"),
        FilterDef("gave_birth_faskes", "Melahirkan di Faskes", "boolean"),
        FilterDef("exclusive_breastfeeding", "ASI Eksklusif", "boolean"),
        FilterDef("complete_immunization", "Imunisasi Lengkap", "boolean"),
        FilterDef("growth_monitoring", "Pemantauan Pertumbuhan", "boolean")
    )),
    FilterGroup("sanitation", "Sanitasi", { Icon(Icons.Default.Water, null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(20.dp)) }, listOf(
        FilterDef("has_clean_water", "Sarana Air Bersih", "boolean"),
        FilterDef("is_water_protected", "Air Terlindungi", "boolean"),
        FilterDef("use_water", "Menggunakan Air Bersih", "boolean"),
        FilterDef("has_toilet", "Tersedia Jamban", "boolean"),
        FilterDef("is_toilet_sanitary", "Jamban Saniter", "boolean"),
        FilterDef("use_toilet", "BAB di Jamban", "boolean")
    ))
)

// ═══════════════════════════════════════════════════════
// ─── Column Headers for Table ─────────────────────────
// ═══════════════════════════════════════════════════════

val analysisTableHeaders = listOf(
    "Desa", "No. Bng", "No. KK", "NIK", "Nama", "Tgl Lahir", "Gender", "Usia",
    "Status Kel.", "Pendidikan", "Perkawinan", "Agama", "Pekerjaan",
    "Hamil", "JKN", "Perokok", "TBC", "Obat TBC", "Batuk Kronis",
    "Hipertensi", "Obat HT", "KB", "Lahir Faskes", "ASI",
    "Imunisasi", "Tumbuh", "Air Bersih", "Pakai Air", "Air Lindung",
    "Jamban", "Jamban Snt", "BAB Jamban"
)

fun AnalysisRow.toColumnValues(): List<String> = listOf(
    villageName, buildingNumber, familyNumber, nik, name, birthDate, gender, age,
    relationship, education, maritalStatus, religion, occupation,
    isPregnant, hasJkn, isSmoker, hasTbc, takesTbMed, hasChronicCough,
    hasHypertension, takesHypertensionMed, usesContraception, gaveBirthInFaskes, exclusiveBreastfeeding,
    completeImmunization, growthMonitoring, hasCleanWater, useWater, isWaterProtected,
    hasToilet, isToiletSanitary, useToilet
)

// ═══════════════════════════════════════════════════════
// ─── UI Composables ───────────────────────────────────
// ═══════════════════════════════════════════════════════

fun LazyListScope.analysisSection(
    state: AnalysisState,
    onToggleGroup: (String) -> Unit,
    onFilterChanged: (String, String) -> Unit,
    onVillageSelected: (Long?) -> Unit,
    onAnalyze: () -> Unit,
    onClear: () -> Unit,
    onToggleCrossTab: () -> Unit,
    onCrossTabVar1Changed: (String) -> Unit,
    onCrossTabVar2Changed: (String) -> Unit,
    onRunCrossTab: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onDismissSuccessNotif: () -> Unit,
    onExport: () -> Unit = {}
) {
    // ── Section Header ───────────────────────────────
    item {
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF1E293B)
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Analytics, null, tint = Color(0xFF818CF8), modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Analisis Data", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    Text("Filter & analisis data secara real-time", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }

    // ── Village Filter Chips ─────────────────────────
    item {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = AppColors.Surface,
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Desa", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = state.selectedVillageId == null,
                        onClick = { onVillageSelected(null) },
                        label = { Text("Semua", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6366F1),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                    state.villages.forEach { (id, name) ->
                        FilterChip(
                            selected = state.selectedVillageId == id,
                            onClick = { onVillageSelected(id) },
                            label = { Text(name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6366F1),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }
        }
    }

    // ── Filter Groups ────────────────────────────────
    val filterGroups = getFilterGroups()
    filterGroups.forEach { group ->
        item(key = "filter_group_${group.key}") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = AppColors.Surface,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column {
                    // Group header - clickable to expand
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onToggleGroup(group.key) },
                        color = Color.Transparent
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            group.icon()
                            Spacer(Modifier.width(12.dp))
                            Text(
                                group.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = AppColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            // Show active filter count
                            val activeCount = group.filters.count { f -> state.filters[f.key]?.isNotBlank() == true }
                            if (activeCount > 0) {
                                Box(
                                    Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF6366F1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$activeCount", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(
                                if (group.key in state.expandedGroups) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Divider(color = AppColors.Background, thickness = 1.dp)

                    // Expanded filter inputs
                    AnimatedVisibility(
                        visible = group.key in state.expandedGroups,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            group.filters.forEach { filterDef ->
                                FilterInput(
                                    filterDef = filterDef,
                                    currentValue = state.filters[filterDef.key] ?: "",
                                    onValueChanged = { onFilterChanged(filterDef.key, it) }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Action Buttons ───────────────────────────────
    item {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = AppColors.Surface,
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAnalyze,
                    enabled = !state.isAnalyzing,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isAnalyzing) {
                        AnalysisLoadingIndicator()
                        Spacer(Modifier.width(8.dp))
                        Text("Menganalisis...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Default.BarChart, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analisis Data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp), tint = AppColors.TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", fontSize = 12.sp, color = AppColors.TextSecondary)
                }
                // ── Tombol Export Excel ─────────────────────────────
                if (state.hasAnalyzed && state.results.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onExport,
                        enabled = !state.isExporting && !state.isAnalyzing,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!state.isExporting) Color(0xFF10B981).copy(alpha = 0.6f)
                            else Color(0xFF10B981).copy(alpha = 0.3f)
                        )
                    ) {
                        if (state.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF10B981)
                            )
                        } else {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Export Excel",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF10B981)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (state.isExporting) "Menyimpan..." else "Excel",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }

    // ── Loading Animation ────────────────────────────
    if (state.isAnalyzing) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = AppColors.Surface,
                shape = RoundedCornerShape(0.dp)
            ) {
                AnalysisLoadingSection()
            }
        }
    }

    // ── Success Notification ─────────────────────────
    if (state.showSuccessNotif) {
        item {
            AnalysisSuccessNotification(
                resultCount = state.resultCount,
                onDismiss = onDismissSuccessNotif
            )
        }
    }

    // ── Results ──────────────────────────────────────
    if (state.hasAnalyzed && !state.isAnalyzing) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = AppColors.Surface,
                shape = RoundedCornerShape(0.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hasil Analisis", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF6366F1).copy(alpha = 0.1f)
                        ) {
                            Text(
                                "${state.resultCount} data",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6366F1)
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (state.results.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, tint = AppColors.TextTertiary, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Tidak ada data yang sesuai", fontSize = 13.sp, color = AppColors.TextTertiary)
                            }
                        }
                    } else {
                        // Paginated table
                        AnalysisResultTable(state.pagedResults)
                        Spacer(Modifier.height(12.dp))

                        // Pagination controls
                        AnalysisPaginationControls(
                            currentPage = state.currentPage,
                            totalPages = state.totalPages,
                            totalItems = state.resultCount,
                            pageSize = state.pageSize,
                            onPageChanged = onPageChanged
                        )
                    }
                }
            }
        }
    }

    // ── Cross-Tab Section ────────────────────────────
    item {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = AppColors.Surface,
            shape = RoundedCornerShape(0.dp)
        ) {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onToggleCrossTab() },
                    color = Color.Transparent
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GridOn, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Cross-Tab Analysis", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                        Icon(
                            if (state.showCrossTab) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = state.showCrossTab,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Pilih 2 variabel untuk analisis silang:", fontSize = 12.sp, color = AppColors.TextTertiary)
                        Spacer(Modifier.height(10.dp))

                        // Variable 1
                        CrossTabVarPicker(
                            label = "Variabel 1 (Baris)",
                            selected = state.crossTabVar1,
                            onSelected = onCrossTabVar1Changed,
                            exclude = state.crossTabVar2
                        )
                        Spacer(Modifier.height(8.dp))

                        // Variable 2
                        CrossTabVarPicker(
                            label = "Variabel 2 (Kolom)",
                            selected = state.crossTabVar2,
                            onSelected = onCrossTabVar2Changed,
                            exclude = state.crossTabVar1
                        )
                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = onRunCrossTab,
                            enabled = state.crossTabVar1.isNotBlank() && state.crossTabVar2.isNotBlank() && state.crossTabVar1 != state.crossTabVar2,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.GridOn, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hitung Cross-Tab", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Cross-tab results
                        if (state.crossTabHeaders.isNotEmpty() && state.crossTabRows.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            CrossTabResultTable(state.crossTabHeaders, state.crossTabRows)
                        }
                    }
                }
            }
        }
    }

    // ── Info Note ─────────────────────────────────────
    item {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = Color(0xFFF0F9FF),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Untuk export data ke Excel, silakan gunakan website.",
                    fontSize = 12.sp, color = Color(0xFF1E40AF), lineHeight = 16.sp
                )
            }
        }
    }

    // ── App Version Footer ────────────────────────────
    item {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("KabenSatuData", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF22C55E).copy(alpha = 0.3f)
                    ) {
                        Text(
                            "v.2",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ADE80),
                            letterSpacing = 1.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "All In One · PKM Ranap Kaben",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
        // Extra spacing to avoid bottom navigation overlap on small screens
        Spacer(Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════
// ─── Filter Input Composable ──────────────────────────
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterInput(filterDef: FilterDef, currentValue: String, onValueChanged: (String) -> Unit) {
    // Preset keys untuk age_range (digunakan untuk deteksi mode kustom)
    val agePresets = setOf("0-5", "6-12", "13-17", "18-45", "46-200", "", "custom")

    Column {
        Text(filterDef.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))

        when (filterDef.type) {
            "select" -> {
                // Deteksi apakah sedang mode kustom (khusus age_range)
                val isAgeFilter = filterDef.key == "age_range"
                val isCustomMode = isAgeFilter && currentValue.isNotBlank() && currentValue !in agePresets

                // Tentukan display label dropdown
                val displayLabel = when {
                    isCustomMode -> "Kustom..."
                    else -> filterDef.options.find { it.key == currentValue }?.label ?: "Semua"
                }

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = displayLabel,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Semua", fontSize = 12.sp) }, onClick = { onValueChanged(""); expanded = false })
                        filterDef.options.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    if (option.key == "custom") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Tune, null, Modifier.size(16.dp), tint = Color(0xFF6366F1))
                                            Spacer(Modifier.width(6.dp))
                                            Text(option.label, fontSize = 12.sp, color = Color(0xFF6366F1), fontWeight = FontWeight.Medium)
                                        }
                                    } else {
                                        Text(option.label, fontSize = 12.sp)
                                    }
                                },
                                onClick = {
                                    if (option.key == "custom") {
                                        onValueChanged("custom")
                                    } else {
                                        onValueChanged(option.key)
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // ── Custom Age Range Input ─────────────────────────
                if (isAgeFilter && (currentValue == "custom" || isCustomMode)) {
                    // Parse existing custom value
                    val existingParts = if (isCustomMode) currentValue.split("-") else emptyList()
                    var customMin by remember(currentValue) {
                        mutableStateOf(existingParts.getOrNull(0)?.takeIf { it.toIntOrNull() != null } ?: "")
                    }
                    var customMax by remember(currentValue) {
                        mutableStateOf(existingParts.getOrNull(1)?.takeIf { it.toIntOrNull() != null } ?: "")
                    }

                    val minVal = customMin.toIntOrNull()
                    val maxVal = customMax.toIntOrNull()
                    val hasError = minVal != null && maxVal != null && minVal > maxVal

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Min field
                        OutlinedTextField(
                            value = customMin,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(3)
                                customMin = filtered
                                val newMin = filtered.toIntOrNull()
                                val newMax = customMax.toIntOrNull()
                                if (newMin != null && newMax != null && newMin <= newMax) {
                                    onValueChanged("$newMin-$newMax")
                                } else if (newMin != null && newMax == null) {
                                    onValueChanged("custom") // Tunggu max diisi
                                }
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
                            placeholder = { Text("Min", fontSize = 12.sp, color = AppColors.TextTertiary) },
                            label = { Text("Dari (thn)", fontSize = 10.sp) },
                            isError = hasError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                errorBorderColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )

                        // Separator
                        Text(
                            "—",
                            fontSize = 16.sp,
                            color = AppColors.TextTertiary,
                            modifier = Modifier.padding(top = 20.dp)
                        )

                        // Max field
                        OutlinedTextField(
                            value = customMax,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(3)
                                customMax = filtered
                                val newMin = customMin.toIntOrNull()
                                val newMax = filtered.toIntOrNull()
                                if (newMin != null && newMax != null && newMin <= newMax) {
                                    onValueChanged("$newMin-$newMax")
                                } else if (newMin == null && newMax != null) {
                                    onValueChanged("custom") // Tunggu min diisi
                                }
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
                            placeholder = { Text("Max", fontSize = 12.sp, color = AppColors.TextTertiary) },
                            label = { Text("Sampai (thn)", fontSize = 10.sp) },
                            isError = hasError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                unfocusedBorderColor = Color(0xFFE2E8F0),
                                errorBorderColor = Color(0xFFEF4444)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                    }

                    // Error hint
                    if (hasError) {
                        Text(
                            "Usia minimum tidak boleh lebih besar dari maksimum",
                            fontSize = 10.sp,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }

                    // Active range indicator
                    if (minVal != null && maxVal != null && !hasError) {
                        Surface(
                            modifier = Modifier.padding(top = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF6366F1).copy(alpha = 0.08f)
                        ) {
                            Text(
                                "Filter aktif: $minVal – $maxVal tahun",
                                fontSize = 10.sp,
                                color = Color(0xFF6366F1),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            "boolean" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "Semua", "1" to "Ya", "0" to "Tidak").forEach { (key, label) ->
                        FilterChip(
                            selected = currentValue == key,
                            onClick = { onValueChanged(key) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6366F1),
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
            }
            "text" -> {
                OutlinedTextField(
                    value = currentValue,
                    onValueChange = onValueChanged,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    placeholder = { Text("Masukkan nomor...", fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Analysis Result Table ────────────────────────────
// ═══════════════════════════════════════════════════════

@Composable
private fun AnalysisResultTable(results: List<AnalysisRow>) {
    val scrollState = rememberScrollState()
    val columnWidths = listOf(
        80.dp, 60.dp, 65.dp, 130.dp, 120.dp, 85.dp, 60.dp, 65.dp,
        75.dp, 100.dp, 75.dp, 60.dp, 80.dp,
        45.dp, 40.dp, 52.dp, 40.dp, 60.dp, 60.dp,
        65.dp, 60.dp, 38.dp, 70.dp, 40.dp,
        62.dp, 50.dp, 65.dp, 60.dp, 68.dp,
        50.dp, 68.dp, 68.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
    ) {
        // Scrollable content
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                // Header row
                Row(modifier = Modifier.background(Color(0xFFF1F5F9))) {
                    analysisTableHeaders.forEachIndexed { idx, header ->
                        Box(
                            modifier = Modifier
                                .width(columnWidths[idx])
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                header,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // Data rows
                results.forEachIndexed { rowIdx, row ->
                    Row(
                        modifier = Modifier.background(
                            if (rowIdx % 2 == 0) Color.White else Color(0xFFF8FAFC)
                        )
                    ) {
                        val values = row.toColumnValues()
                        values.forEachIndexed { colIdx, cellValue ->
                            Box(
                                modifier = Modifier
                                    .width(columnWidths[colIdx])
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    cellValue,
                                    fontSize = 10.sp,
                                    color = when {
                                        cellValue == "Ya" -> Color(0xFF059669)
                                        cellValue == "Tidak" -> Color(0xFFDC2626)
                                        else -> Color(0xFF334155)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (colIdx == 4) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (rowIdx < results.lastIndex) {
                        Divider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Cross-Tab Components ─────────────────────────────
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrossTabVarPicker(label: String, selected: String, onSelected: (String) -> Unit, exclude: String) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = AnalysisEngine.crossTabVariables.find { it.second == selected }?.first ?: "Pilih..."

    Column {
        Text(label, fontSize = 11.sp, color = AppColors.TextTertiary)
        Spacer(Modifier.height(2.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF59E0B),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AnalysisEngine.crossTabVariables
                    .filter { it.second != exclude }
                    .forEach { (varLabel, varKey) ->
                        DropdownMenuItem(
                            text = { Text(varLabel, fontSize = 12.sp) },
                            onClick = { onSelected(varKey); expanded = false }
                        )
                    }
            }
        }
    }
}

@Composable
private fun CrossTabResultTable(headers: List<String>, rows: List<List<String>>) {
    val scrollState = rememberScrollState()
    val colWidth = 70.dp
    val firstColWidth = 90.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
    ) {
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                // Header
                Row(modifier = Modifier.background(Color(0xFFFEF3C7))) {
                    headers.forEachIndexed { idx, h ->
                        Box(
                            modifier = Modifier
                                .width(if (idx == 0) firstColWidth else colWidth)
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = if (idx == 0) Alignment.CenterStart else Alignment.Center
                        ) {
                            Text(
                                h.ifBlank { "─" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = if (idx == 0) TextAlign.Start else TextAlign.Center
                            )
                        }
                    }
                }
                Divider(color = Color(0xFFE2E8F0))

                // Data rows
                rows.forEachIndexed { rowIdx, row ->
                    val isLast = rowIdx == rows.lastIndex
                    Row(
                        modifier = Modifier.background(
                            when {
                                isLast -> Color(0xFFF0FDF4)
                                rowIdx % 2 == 0 -> Color.White
                                else -> Color(0xFFFFFBEB)
                            }
                        )
                    ) {
                        row.forEachIndexed { colIdx, cell ->
                            Box(
                                modifier = Modifier
                                    .width(if (colIdx == 0) firstColWidth else colWidth)
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                contentAlignment = if (colIdx == 0) Alignment.CenterStart else Alignment.Center
                            ) {
                                Text(
                                    cell,
                                    fontSize = 10.sp,
                                    color = if (isLast || colIdx == 0) Color(0xFF1E293B) else Color(0xFF475569),
                                    fontWeight = if (isLast || colIdx == 0 || colIdx == row.lastIndex) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = if (colIdx == 0) TextAlign.Start else TextAlign.Center
                                )
                            }
                        }
                    }
                    if (rowIdx < rows.lastIndex) {
                        Divider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Loading Indicator (Pulsing Dots) ─────────────────
// ═══════════════════════════════════════════════════════

@Composable
fun AnalysisLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val delayedAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 150, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(delayedAlpha)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Loading Section (Shimmer Skeleton) ───────────────
// ═══════════════════════════════════════════════════════

@Composable
fun AnalysisLoadingSection() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE2E8F0),
            Color(0xFFF1F5F9),
            Color(0xFFE2E8F0)
        ),
        start = androidx.compose.ui.geometry.Offset(shimmerOffset * 300f, 0f),
        end = androidx.compose.ui.geometry.Offset((shimmerOffset + 1f) * 300f, 0f)
    )

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shimmer skeleton rows
        repeat(5) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .weight(0.3f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    Modifier
                        .weight(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                Box(
                    Modifier
                        .weight(0.2f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Animated text
        val dotCount by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "dots"
        )
        Text(
            "Memproses data" + ".".repeat(dotCount.toInt()),
            fontSize = 12.sp,
            color = AppColors.TextTertiary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════
// ─── Success Notification with Sound ──────────────────
// ═══════════════════════════════════════════════════════

@Composable
fun AnalysisSuccessNotification(resultCount: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }

    // Play sound and show notification
    LaunchedEffect(Unit) {
        // Play notification sound
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.notif_sound)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (_: Exception) { /* fallback silently */ }

        visible = true
        // Auto-dismiss after 3 seconds
        delay(3000)
        visible = false
        delay(300)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(300)),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(300))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = Color(0xFF059669),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Analisis Berhasil!", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                    Text(
                        "$resultCount data berhasil dianalisis",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// ─── Pagination Controls ──────────────────────────────
// ═══════════════════════════════════════════════════════

@Composable
fun AnalysisPaginationControls(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    pageSize: Int,
    onPageChanged: (Int) -> Unit
) {
    if (totalPages <= 1) return

    val startItem = currentPage * pageSize + 1
    val endItem = minOf((currentPage + 1) * pageSize, totalItems)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Page info
        Text(
            "Menampilkan $startItem-$endItem dari $totalItems data",
            fontSize = 11.sp,
            color = AppColors.TextTertiary
        )
        Spacer(Modifier.height(8.dp))

        // Navigation buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // First page
            IconButton(
                onClick = { onPageChanged(0) },
                enabled = currentPage > 0,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.FirstPage, null, Modifier.size(18.dp),
                    tint = if (currentPage > 0) Color(0xFF6366F1) else Color(0xFFCBD5E1))
            }

            // Previous
            OutlinedButton(
                onClick = { onPageChanged(currentPage - 1) },
                enabled = currentPage > 0,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, if (currentPage > 0) Color(0xFF6366F1) else Color(0xFFE2E8F0))
            ) {
                Icon(Icons.Default.ChevronLeft, null, Modifier.size(16.dp))
                Spacer(Modifier.width(2.dp))
                Text("Prev", fontSize = 11.sp)
            }

            Spacer(Modifier.width(8.dp))

            // Page indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF6366F1)
            ) {
                Text(
                    "${currentPage + 1} / $totalPages",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.width(8.dp))

            // Next
            OutlinedButton(
                onClick = { onPageChanged(currentPage + 1) },
                enabled = currentPage < totalPages - 1,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, if (currentPage < totalPages - 1) Color(0xFF6366F1) else Color(0xFFE2E8F0))
            ) {
                Text("Next", fontSize = 11.sp)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }

            // Last page
            IconButton(
                onClick = { onPageChanged(totalPages - 1) },
                enabled = currentPage < totalPages - 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.LastPage, null, Modifier.size(18.dp),
                    tint = if (currentPage < totalPages - 1) Color(0xFF6366F1) else Color(0xFFCBD5E1))
            }
        }
    }
}
