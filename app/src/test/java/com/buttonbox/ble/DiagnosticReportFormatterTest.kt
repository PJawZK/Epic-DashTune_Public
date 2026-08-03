package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class DiagnosticReportFormatterTest {
    @Test fun `full report matches existing text format`() {
        val input = DiagnosticReportInput(
            generatedAtMs = 1_750_000_000_000L,
            timeZone = TimeZone.getTimeZone("UTC"),
            versionName = "0.11.12-stale1-jz",
            versionCode = 1114L,
            androidRelease = "12",
            androidApi = 31,
            deviceManufacturer = "samsung",
            deviceModel = "SM-T500",
            bleJson = "{\n  \"connected\": true\n}",
            usbJson = "{\n  \"state\": \"streaming\"\n}",
            lifecycleJson = "{\n  \"processGeneration\": \"p-test\"\n}",
            dashboardJson = "{\n  \"source\": \"LIVE\"\n}",
            events = listOf(
                DiagnosticReportHistoryEntry(
                    timestampMs = 1_750_000_000_123L,
                    category = "USB",
                    title = "",
                    message = "Streaming established",
                    repeatCount = 1,
                    source = ""
                )
            ),
            warnings = listOf(
                DiagnosticReportHistoryEntry(
                    timestampMs = 1_750_000_001_456L,
                    category = "ecu-stale",
                    title = "ECU DATA STALE",
                    message = "No current packet",
                    repeatCount = 3,
                    source = "LIVE"
                )
            )
        )

        assertEquals(
            """EpicDash JZ diagnostic report
Generated: 2025-06-15 15:06:40 +0000
App: 0.11.12-stale1-jz (1114)
Android: 12 / API 31
Device: samsung SM-T500

BLE / SESSION
{
  "connected": true
}

USB ECU / READ-ONLY TRANSPORT
{
  "state": "streaming"
}

LIFECYCLE / OWNER TRACE
{
  "processGeneration": "p-test"
}

DASHBOARD / DATA SOURCE
{
  "source": "LIVE"
}

RECENT EVENTS
2025-06-15 15:06:40.123  [USB] Streaming established

WARNING HISTORY
2025-06-15 15:06:41.456  [ecu-stale] ECU DATA STALE: No current packet (repeated 3×) [source=LIVE]
""",
            DiagnosticReportFormatter.format(input)
        )
    }

    @Test fun `empty histories and missing dashboard retain legacy empty text`() {
        val report = DiagnosticReportFormatter.format(
            DiagnosticReportInput(
                generatedAtMs = 0L,
                timeZone = TimeZone.getTimeZone("UTC"),
                versionName = null,
                versionCode = 0L,
                androidRelease = "unknown",
                androidApi = 0,
                deviceManufacturer = "unknown",
                deviceModel = "unknown",
                bleJson = "{}",
                usbJson = "{}",
                lifecycleJson = "{}",
                dashboardJson = null,
                events = emptyList(),
                warnings = emptyList()
            )
        )

        assertEquals(
            """EpicDash JZ diagnostic report
Generated: 1970-01-01 00:00:00 +0000
App: null (0)
Android: unknown / API 0
Device: unknown unknown

BLE / SESSION
{}

USB ECU / READ-ONLY TRANSPORT
{}

LIFECYCLE / OWNER TRACE
{}

DASHBOARD / DATA SOURCE
(no dashboard runtime snapshot)

RECENT EVENTS
(none)

WARNING HISTORY
(none)
""",
            report
        )
    }

    @Test fun `supplied timezone controls generated and history timestamps`() {
        val report = DiagnosticReportFormatter.format(
            DiagnosticReportInput(
                generatedAtMs = 0L,
                timeZone = TimeZone.getTimeZone("GMT+02:00"),
                versionName = "test",
                versionCode = 1L,
                androidRelease = "test",
                androidApi = 1,
                deviceManufacturer = "test",
                deviceModel = "test",
                bleJson = "{}",
                usbJson = "{}",
                lifecycleJson = "{}",
                dashboardJson = "{}",
                events = listOf(
                    DiagnosticReportHistoryEntry(1L, "EVENT", "", "message", 1, "")
                ),
                warnings = emptyList()
            )
        )

        assertEquals(true, report.contains("Generated: 1970-01-01 02:00:00 +0200"))
        assertEquals(true, report.contains("1970-01-01 02:00:00.001  [EVENT] message"))
    }

    @Test fun `history order and unknown timestamps are preserved`() {
        val report = DiagnosticReportFormatter.format(
            DiagnosticReportInput(
                generatedAtMs = 0L,
                timeZone = TimeZone.getTimeZone("UTC"),
                versionName = "test",
                versionCode = 1L,
                androidRelease = "test",
                androidApi = 1,
                deviceManufacturer = "test",
                deviceModel = "test",
                bleJson = "{}",
                usbJson = "{}",
                lifecycleJson = "{}",
                dashboardJson = "{}",
                events = listOf(
                    DiagnosticReportHistoryEntry(0L, "FIRST", "", "one", 1, ""),
                    DiagnosticReportHistoryEntry(2L, "SECOND", "Two", "two", 2, "MSL")
                ),
                warnings = emptyList()
            )
        )

        val first = report.indexOf("unknown  [FIRST] one")
        val second = report.indexOf("1970-01-01 00:00:00.002  [SECOND] Two: two (repeated 2×) [source=MSL]")
        assertEquals(true, first >= 0)
        assertEquals(true, second > first)
    }
}
