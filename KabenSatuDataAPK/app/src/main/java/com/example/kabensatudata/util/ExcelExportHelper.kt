package com.example.kabensatudata.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.kabensatudata.ui.screen.home.AnalysisRow
import com.example.kabensatudata.ui.screen.home.analysisTableHeaders
import com.example.kabensatudata.ui.screen.home.toColumnValues
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ─── Enriched SPM Row ─────────────────────────────────────────────────────────

data class EnrichedSpmRow(
    val id: Long,
    val name: String?,
    val gender: String?,
    val age: Int?,
    val nik: String?,
    val village: String?,
    val lastVisit: String?,
    val status: String?,
    val birthDate: String?,
    val education: String?,
    val occupation: String?,
    val maritalStatus: String?,
    val religion: String?,
    val hasJkn: Boolean?,
    val isSmoker: Boolean?,
    val hasTbc: Boolean?,
    val hasHypertension: Boolean?,
    val usesContraception: Boolean?,
    val isPregnant: Boolean?,
    val familyNumber: String?,
    val buildingNumber: String?
)

// ─── Export Helper ─── Pure Kotlin XLSX (ZipOutputStream, zero dependency) ───

object ExcelExportHelper {

    // Cell styles index constants
    private const val S_NORMAL = 0
    private const val S_HEADER = 1
    private const val S_TITLE  = 2
    private const val S_SUB    = 3
    private const val S_ALT    = 4

    private val dateLabel: String
        get() = SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id")).format(Date())

    private val fileStamp: String
        get() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())

    private fun boolStr(v: Boolean?) = when (v) { true -> "Ya"; false -> "Tidak"; null -> "-" }

    // ─── Data structures ──────────────────────────────────────────────────────

    private data class Cell(val value: String, val style: Int = S_NORMAL)
    private data class SheetDef(
        val name: String,
        val colWidths: List<Int>,   // dalam karakter
        val rows: List<List<Cell>>,
        val merges: List<Int>,      // baris (1-indexed) yang di-merge full width
        val freezeRow: Int = 0
    )

    // ─── FUNGSI 1: Export Analisis ────────────────────────────────────────────

    fun exportAnalysis(
        context: Context,
        rows: List<AnalysisRow>,
        crossTabHeaders: List<String>,
        crossTabRows: List<List<String>>,
        filters: Map<String, String>,
        villageName: String?
    ): Uri {
        val activeFilters = filters.entries.filter { it.value.isNotBlank() }
            .joinToString(" | ") { "${it.key}=${it.value}" }.ifBlank { "Semua data" }

        val analysisColWidths = listOf(
            12,10,10,18,22,12,10,6,14,14,14,10,16,8,6,8,6,
            10,12,12,14,6,12,14,12,16,10,10,14,8,14,14
        )

        val dataRows = mutableListOf<List<Cell>>()
        dataRows += listOf(Cell("Laporan Analisis Data Kependudukan — Puskesmas Kaben Satu", S_TITLE))
        dataRows += listOf(Cell("Desa: ${villageName ?: "Semua"} | Filter: $activeFilters | Total: ${rows.size} data", S_SUB))
        dataRows += listOf(Cell("Tanggal Export: $dateLabel WIT", S_SUB))
        dataRows += listOf(Cell(""))
        dataRows += analysisTableHeaders.map { Cell(it, S_HEADER) }

        rows.forEachIndexed { idx, row ->
            val style = if (idx % 2 == 0) S_NORMAL else S_ALT
            dataRows += row.toColumnValues().map { Cell(it, style) }
        }

        val sheets = mutableListOf<SheetDef>()
        sheets += SheetDef(
            name = "Data Kependudukan",
            colWidths = analysisColWidths,
            rows = dataRows,
            merges = listOf(1, 2, 3),
            freezeRow = 5
        )

        if (crossTabHeaders.isNotEmpty() && crossTabRows.isNotEmpty()) {
            val ctRows = mutableListOf<List<Cell>>()
            ctRows += listOf(Cell("Analisis Tabulasi Silang (Cross-Tab)", S_TITLE))
            ctRows += listOf(Cell(""))
            ctRows += crossTabHeaders.map { Cell(it, S_HEADER) }
            crossTabRows.forEachIndexed { idx, row ->
                val style = if (idx % 2 == 0) S_NORMAL else S_ALT
                ctRows += row.map { Cell(it, style) }
            }
            val ctWidths = listOf(20) + List(maxOf(crossTabHeaders.size - 1, 0)) { 10 }
            sheets += SheetDef(
                name = "Cross-Tab",
                colWidths = ctWidths,
                rows = ctRows,
                merges = listOf(1),
                freezeRow = 3
            )
        }

        val bytes = generateXlsx(sheets)
        return saveFile(context, bytes, "Analisis_$fileStamp.xlsx")
    }

    // ─── FUNGSI 2: Export SPM Sub-Detail ─────────────────────────────────────

    fun exportSpmSubDetail(
        context: Context,
        subName: String,
        subCode: String,
        year: Int,
        month: Int?,
        achieved: List<EnrichedSpmRow>,
        potential: List<EnrichedSpmRow>
    ): Uri {
        val periodLabel = if (month != null) {
            val names = listOf("","Januari","Februari","Maret","April","Mei","Juni",
                "Juli","Agustus","September","Oktober","November","Desember")
            "${names.getOrElse(month) { "Bln $month" }} $year"
        } else "Tahun $year"

        val headers = listOf(
            "No.","Nama","Gender","Usia","NIK","Desa","No. Bangunan","No. KK",
            "Tgl Lahir","Pendidikan","Pekerjaan","Status Kawin","Agama","JKN",
            "Perokok","TBC","Hipertensi","KB","Hamil","Kunjungan Terakhir","Status"
        )
        val spmColWidths = listOf(
            5,22,10,6,18,12,12,12,12,14,16,14,10,6,8,6,10,6,8,14,14
        )

        fun EnrichedSpmRow.toRow(no: Int, style: Int) = listOf(
            no.toString(),name?:"-",gender?:"-",age?.toString()?:"-",nik?:"-",village?:"-",
            buildingNumber?:"-",familyNumber?:"-",birthDate?:"-",education?:"-",occupation?:"-",
            maritalStatus?:"-",religion?:"-",boolStr(hasJkn),boolStr(isSmoker),boolStr(hasTbc),
            boolStr(hasHypertension),boolStr(usesContraception),boolStr(isPregnant),
            lastVisit?:"-",status?:"-"
        ).map { Cell(it, style) }

        fun buildSheet(name: String, list: List<EnrichedSpmRow>, caption: String): SheetDef {
            val rows = mutableListOf<List<Cell>>()
            rows += listOf(Cell("$subCode — $subName", S_TITLE))
            rows += listOf(Cell("$periodLabel | $caption", S_SUB))
            rows += listOf(Cell("Diekspor: $dateLabel WIT", S_SUB))
            rows += listOf(Cell(""))
            rows += headers.map { Cell(it, S_HEADER) }
            list.forEachIndexed { idx, row ->
                rows += row.toRow(idx + 1, if (idx % 2 == 0) S_NORMAL else S_ALT)
            }
            return SheetDef(name, spmColWidths, rows, listOf(1,2,3), freezeRow = 5)
        }

        val sheets = listOf(
            buildSheet("Tercatat (${achieved.size})", achieved, "Capaian: ${achieved.size} pasien tercatat"),
            buildSheet("Sasaran Potensial (${potential.size})", potential, "Sasaran: ${potential.size} orang")
        )

        val safeCode = subCode.replace(Regex("[/\\\\:*?\"<>|]"), "-")
        val bytes = generateXlsx(sheets)
        return saveFile(context, bytes, "SPM_${safeCode}_${fileStamp}.xlsx")
    }

    // ─── XLSX Generator ───────────────────────────────────────────────────────

    private fun generateXlsx(sheets: List<SheetDef>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putEntry("[Content_Types].xml", contentTypesXml(sheets.size))
            zip.putEntry("_rels/.rels", rootRelsXml())
            zip.putEntry("xl/workbook.xml", workbookXml(sheets))
            zip.putEntry("xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            zip.putEntry("xl/styles.xml", stylesXml())
            sheets.forEachIndexed { i, sheet ->
                zip.putEntry("xl/worksheets/sheet${i+1}.xml", worksheetXml(sheet, sheets.size))
            }
        }
        return baos.toByteArray()
    }

    private fun ZipOutputStream.putEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ─── XML Builders ─────────────────────────────────────────────────────────

    private fun contentTypesXml(sheetCount: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount)
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun rootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbookXml(sheets: List<SheetDef>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        sheets.forEachIndexed { i, s ->
            append("""<sheet name="${s.name.xmlEsc()}" sheetId="${i+1}" r:id="rId${i+1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelsXml(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count)
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${count+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="4"><font><sz val="9"/><name val="Calibri"/></font><font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="12"/><color rgb="FF006064"/><name val="Calibri"/></font><font><sz val="9"/><color rgb="FF757575"/><name val="Calibri"/></font></fonts><fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF00897B"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE0F7FA"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFB2EBF2"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><bottom><color rgb="FFCCCCCC"/></bottom></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0"><alignment vertical="center"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"><alignment horizontal="center" vertical="center"/></xf><xf numFmtId="0" fontId="2" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1"><alignment vertical="center"/></xf><xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"><alignment vertical="center"/></xf><xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1"><alignment vertical="center"/></xf></cellXfs></styleSheet>"""

    private fun worksheetXml(sheet: SheetDef, totalSheets: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

        // Freeze pane
        if (sheet.freezeRow > 0) {
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="${sheet.freezeRow}" topLeftCell="A${sheet.freezeRow+1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        }

        // Column widths
        if (sheet.colWidths.isNotEmpty()) {
            append("<cols>")
            sheet.colWidths.forEachIndexed { i, w ->
                append("""<col min="${i+1}" max="${i+1}" width="$w" customWidth="1"/>""")
            }
            append("</cols>")
        }

        // Max columns (untuk merge)
        val maxCols = sheet.rows.maxOfOrNull { it.size } ?: 1

        // Sheet data
        append("<sheetData>")
        sheet.rows.forEachIndexed { rowIdx, cells ->
            val rowNum = rowIdx + 1
            val isMergeRow = rowNum in sheet.merges
            val firstStyle = cells.firstOrNull()?.style ?: S_NORMAL
            val ht = if (firstStyle == S_HEADER || firstStyle == S_TITLE) """ ht="22" customHeight="1"""" else ""
            append("""<row r="$rowNum"$ht>""")
            if (isMergeRow) {
                // Hanya tulis sel pertama untuk merged row
                val cell = cells.firstOrNull() ?: Cell("")
                val sAttr = if (cell.style != S_NORMAL) """ s="${cell.style}"""" else ""
                append("""<c r="A$rowNum" t="inlineStr"$sAttr><is><t>${cell.value.xmlEsc()}</t></is></c>""")
            } else {
                cells.forEachIndexed { colIdx, cell ->
                    val ref = "${colLetter(colIdx)}$rowNum"
                    val sAttr = if (cell.style != S_NORMAL) """ s="${cell.style}"""" else ""
                    append("""<c r="$ref" t="inlineStr"$sAttr><is><t>${cell.value.xmlEsc()}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")

        // Merged cells
        if (sheet.merges.isNotEmpty()) {
            val lastCol = colLetter(maxCols - 1)
            append("""<mergeCells count="${sheet.merges.size}">""")
            sheet.merges.forEach { rowNum ->
                append("""<mergeCell ref="A$rowNum:$lastCol$rowNum"/>""")
            }
            append("</mergeCells>")
        }

        append("</worksheet>")
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun colLetter(colIdx: Int): String {
        var n = colIdx + 1
        var result = ""
        while (n > 0) {
            val rem = (n - 1) % 26
            result = ('A' + rem) + result
            n = (n - 1) / 26
        }
        return result
    }

    private fun String.xmlEsc() = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun saveFile(context: Context, bytes: ByteArray, fileName: String): Uri {
        val dir = File(context.getExternalFilesDir(null), "exports").also { it.mkdirs() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
