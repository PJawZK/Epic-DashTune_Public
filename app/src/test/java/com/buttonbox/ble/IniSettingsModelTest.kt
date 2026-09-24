package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IniSettingsModelTest {
    private fun ini(modeLabels: String = "\"Off\", \"On\", \"Auto\", \"Invalid\""): String = """
        [TunerStudio]
        queryCommand = "S"
        signature = "rusEFI dialog test"

        [Constants]
        messageEnvelopeFormat = msEnvelope_1.0
        endianness = little
        pageIdentifier = "\\x00\\x00"
        pageSize = 64
        pageReadCommand = "R%2i%2o%2c"
        burnCommand = "B%2i"
        #define mode_options=$modeLabels
        page = 1
        rpmTarget = scalar, U16, 2, "RPM", 1, 0, 0, 9000, 0
        mode = bits, U08, 4, [1:2], @MODE_OPTIONS@
        unreferencedMode = bits, U08, 5, [0:1], "A", "B", "C", "D"

        [OutputChannels]
        ochGetCommand = "O%2o%2c"
        ochBlockSize = 64
        RPMValue = scalar, U16, 0, "RPM", 1, 0

        [Menu]
        menuDialog = main
        menu = "&Setup"
            groupMenu = "Engine"
                groupChildMenu = engineDialog, "Engine Settings", 0, { mode > 0 }
            subMenu = directDialog, "Direct Settings"

        [UserDefined]
        dialog = engineDialog, "Engine Settings", yAxis
            topicHelp = "engineHelp"
            field = "RPM Target", rpmTarget
            field = "Mode", mode, { rpmTarget > 0 }
            field = "Read this help text"
            panel = advancedPanel, { mode == 2 }
            commandButton = "Unsafe command", cmd_unsafe

        dialog = advancedPanel, "Advanced"
            field = "RPM Target", rpmTarget

        dialog = directDialog, "Direct"
            field = "RPM Target", rpmTarget
    """.trimIndent().replace("@MODE_OPTIONS@", "$" + "mode_options")

    @Test
    fun parserPreservesMenuDialogAndReferencedBitSemantics() {
        val profile = UsbTunerStudioProfileParser.parse(ini(), "dialogs.ini")

        assertEquals(1, profile.tuneBitFields.size)
        val bit = profile.tuneBitFields.single()
        assertEquals("mode", bit.name)
        assertEquals(1, bit.bitStart)
        assertEquals(2, bit.bitEnd)
        assertEquals(listOf("Off", "On", "Auto", "Invalid"), bit.options.map { it.label })

        assertEquals(2, profile.tuneMenuItems.size)
        val grouped = profile.tuneMenuItems.first { it.dialogId == "engineDialog" }
        assertEquals("&Setup", grouped.menu)
        assertEquals("Engine", grouped.group)
        assertEquals(listOf("mode > 0"), grouped.conditions)

        val dialog = profile.tuneDialogs.first { it.id == "engineDialog" }
        assertEquals("Engine Settings", dialog.title)
        assertEquals("yAxis", dialog.layout)
        assertEquals("engineHelp", dialog.topicHelp)
        assertTrue(dialog.entries.any { it.kind == "field" && it.target == "rpmTarget" })
        assertTrue(dialog.entries.any { it.kind == "field" && it.target == "mode" && it.conditions == listOf("rpmTarget > 0") })
        assertTrue(dialog.entries.any { it.kind == "text" && it.label == "Read this help text" })
        assertTrue(dialog.entries.any { it.kind == "panel" && it.target == "advancedPanel" })
        assertTrue(dialog.entries.any { it.kind == "command" && it.target == "cmd_unsafe" })

        val roundTrip = UsbTunerStudioProfile.fromJson(profile.toJson())
        assertEquals(profile.tuneBitFields, roundTrip.tuneBitFields)
        assertEquals(profile.tuneMenuItems, roundTrip.tuneMenuItems)
        assertEquals(profile.tuneDialogs, roundTrip.tuneDialogs)
    }

    @Test
    fun parserInheritsMenuAndGroupConditionsIntoChildItems() {
        val conditionalIni = ini()
            .replace("menu = \"&Setup\"", "menu = \"&Setup\", { 1 }, { mode == 2 }")
            .replace("groupMenu = \"Engine\"", "groupMenu = \"Engine\", { rpmTarget > 0 }")
        val profile = UsbTunerStudioProfileParser.parse(conditionalIni, "inherited-conditions.ini")

        val grouped = profile.tuneMenuItems.first { it.dialogId == "engineDialog" }
        assertEquals(
            listOf(
                "(1) && (rpmTarget > 0) && (mode > 0)",
                "mode == 2"
            ),
            grouped.conditions
        )

        val direct = profile.tuneMenuItems.first { it.dialogId == "directDialog" }
        assertEquals(listOf("1", "mode == 2"), direct.conditions)
    }

    @Test
    fun workspaceDecodesBitValueButNeverLeaksPhysicalAddressMetadata() {
        val profile = UsbTunerStudioProfileParser.parse(ini(), "dialogs.ini")
        val bytes = ByteArray(64)
        bytes[2] = 0xE8.toByte()
        bytes[3] = 0x03
        bytes[4] = 0x04 // bits [1:2] => 2 => Auto

        val snapshot = TuneSnapshot.create(
            ecuSignature = profile.signature,
            profileFingerprint = profile.tuneProfileFingerprint(),
            pages = listOf(TunePageSnapshot(1, 0, 64, UsbTuneReadCodec.SUPPORTED_READ_COMMAND, bytes)),
            generation = 4L,
            capturedAtEpochMs = 1234L
        )

        val workspace = TuningWorkspaceBuilder.build(profile, snapshot, 4L)
        assertEquals(1, workspace.bitFields.size)
        assertEquals(2, workspace.bitFields.single().value)
        assertEquals("Auto", workspace.bitFields.single().valueLabel)
        assertEquals(2, workspace.menuItems.size)
        assertEquals(3, workspace.dialogs.size)

        val json = workspace.toJson().toString()
        assertTrue(json.contains("\"engineDialog\""))
        assertTrue(json.contains("\"valueLabel\":\"Auto\""))
        assertFalse(json.contains("\"offset\""))
        assertFalse(json.contains("\"pageNumber\""))
        assertFalse(json.contains("\"bitStart\""))
        assertFalse(json.contains("\"bitEnd\""))
    }

    @Test
    fun tuneProfileIdentityIncludesReferencedBitSchema() {
        val original = UsbTunerStudioProfileParser.parse(ini())
        val changed = UsbTunerStudioProfileParser.parse(ini("\"Off\", \"Enabled\", \"Auto\", \"Invalid\""))
        assertNotEquals(original.tuneProfileFingerprint(), changed.tuneProfileFingerprint())
    }
}
