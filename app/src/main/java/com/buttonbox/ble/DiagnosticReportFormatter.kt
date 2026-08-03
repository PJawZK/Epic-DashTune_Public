package com.buttonbox.ble

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class DiagnosticReportInput(
    val generatedAtMs: Long,
    val timeZone: TimeZone,
    val versionName: String?,
    val versionCode: Long,
    val androidRelease: String,
    val androidApi: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val bleJson: String,
    val usbJson: String,
    val lifecycleJson: String,
    val dashboardJson: String?,
    val events: List<DiagnosticReportHistoryEntry>,
    val warnings: List<DiagnosticReportHistoryEntry>
)

internal data class DiagnosticReportHistoryEntry(
    val timestampMs: Long,
    val category: String,
    val title: String,
    val message: String,
    val repeatCount: Int,
    val source: String
)

internal object DiagnosticReportFormatter {
    fun format(input: DiagnosticReportInput): String {
        val report = StringBuilder()
        report.appendLine("EpicDash JZ diagnostic report")
        report.appendLine("Generated: ${formatTime(input.generatedAtMs, "yyyy-MM-dd HH:mm:ss Z", input.timeZone)}")
        report.appendLine("App: ${input.versionName} (${input.versionCode})")
        report.appendLine("Android: ${input.androidRelease} / API ${input.androidApi}")
        report.appendLine("Device: ${input.deviceManufacturer} ${input.deviceModel}")
        report.appendLine()
        report.appendLine("BLE / SESSION")
        report.appendLine(input.bleJson)
        report.appendLine()
        report.appendLine("USB ECU / READ-ONLY TRANSPORT")
        report.appendLine(input.usbJson)
        report.appendLine()
        report.appendLine("LIFECYCLE / OWNER TRACE")
        report.appendLine(input.lifecycleJson)
        report.appendLine()
        report.appendLine("DASHBOARD / DATA SOURCE")
        report.appendLine(input.dashboardJson?.takeIf { it.isNotBlank() } ?: "(no dashboard runtime snapshot)")
        report.appendLine()
        report.appendLine("RECENT EVENTS")
        appendHistory(report, input.events, input.timeZone)
        report.appendLine()
        report.appendLine("WARNING HISTORY")
        appendHistory(report, input.warnings, input.timeZone)
        return report.toString()
    }

    private fun appendHistory(
        target: StringBuilder,
        entries: List<DiagnosticReportHistoryEntry>,
        timeZone: TimeZone
    ) {
        if (entries.isEmpty()) {
            target.appendLine("(none)")
            return
        }
        entries.forEach { item ->
            val time = if (item.timestampMs > 0L) {
                formatTime(item.timestampMs, "yyyy-MM-dd HH:mm:ss.SSS", timeZone)
            } else {
                "unknown"
            }
            target.append(time).append("  [").append(item.category).append("] ")
            if (item.title.isNotBlank()) target.append(item.title).append(": ")
            target.append(item.message)
            if (item.repeatCount > 1) target.append(" (repeated ").append(item.repeatCount).append("×)")
            if (item.source.isNotBlank()) target.append(" [source=").append(item.source).append("]")
            target.appendLine()
        }
    }

    private fun formatTime(timestampMs: Long, pattern: String, timeZone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { this.timeZone = timeZone }.format(Date(timestampMs))
}
