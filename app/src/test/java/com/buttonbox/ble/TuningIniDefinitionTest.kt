package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningIniDefinitionTest {
    @Test
    fun currentIniBuildsCompleteStructureWithoutInventingEcuValues() {
        val profile = UsbTunerStudioProfileParser.parse(ini(), "definition.ini")
        val definition = TuningIniDefinitionBuilder.build(profile)

        assertEquals("ready", definition.getString("status"))
        assertEquals("INI_STRUCTURE", definition.getString("capability"))
        assertEquals("INI_DEFINITION", definition.getString("source"))
        assertTrue(definition.getBoolean("definitionOnly"))
        assertEquals(profile.tuneProfileFingerprint(), definition.getString("profileFingerprint"))

        val scalar = definition.getJSONArray("scalars").getJSONObject(0)
        assertEquals("rpmTarget", scalar.getString("name"))
        assertFalse(scalar.getBoolean("valueAvailable"))
        assertFalse(scalar.getBoolean("writeAllowed"))

        val bit = definition.getJSONArray("bitFields").getJSONObject(0)
        assertEquals("mode", bit.getString("name"))
        assertFalse(bit.getBoolean("valueAvailable"))
        assertEquals(4, bit.getJSONArray("options").length())

        val menu = definition.getJSONArray("menuItems").getJSONObject(0)
        assertEquals("engineDialog", menu.getString("dialogId"))
        val menuState = menu.getJSONObject("conditionState")
        assertTrue(menuState.getBoolean("visible"))
        assertFalse(menuState.getBoolean("enabled"))

        val dialog = definition.getJSONArray("dialogs").getJSONObject(0)
        assertEquals("engineDialog", dialog.getString("id"))
        assertTrue(dialog.getJSONArray("entries").length() >= 2)

        val json = definition.toString()
        assertFalse(json.contains("\"offset\""))
        assertFalse(json.contains("\"pageNumber\""))
        assertFalse(json.contains("\"dataType\""))
        assertFalse(json.contains("rawHex"))
    }

    @Test
    fun changedIniFingerprintProducesChangedStructure() {
        val first = UsbTunerStudioProfileParser.parse(ini(), "definition.ini")
        val second = UsbTunerStudioProfileParser.parse(
            ini().replace("rpmTarget = scalar, U16, 2", "newTarget = scalar, U16, 2")
                .replace("rpmTarget", "newTarget"),
            "definition.ini"
        )

        val firstDefinition = TuningIniDefinitionBuilder.build(first)
        val secondDefinition = TuningIniDefinitionBuilder.build(second)

        assertFalse(firstDefinition.getString("profileFingerprint") == secondDefinition.getString("profileFingerprint"))
        assertEquals("rpmTarget", firstDefinition.getJSONArray("scalars").getJSONObject(0).getString("name"))
        assertEquals("newTarget", secondDefinition.getJSONArray("scalars").getJSONObject(0).getString("name"))
    }

    private fun ini(): String = """
        [TunerStudio]
        queryCommand = "S"
        signature = "rusEFI definition test"

        [Constants]
        messageEnvelopeFormat = msEnvelope_1.0
        endianness = little
        pageIdentifier = "\\x00\\x00"
        pageSize = 64
        pageReadCommand = "R%2i%2o%2c"
        page = 1
        rpmTarget = scalar, U16, 2, "RPM", 1, 0, 0, 9000, 0
        mode = bits, U08, 4, [1:2], "Off", "On", "Auto", "Invalid"

        [OutputChannels]
        ochGetCommand = "O%2o%2c"
        ochBlockSize = 64
        RPMValue = scalar, U16, 0, "RPM", 1, 0

        [Menu]
        menuDialog = main
        menu = "&Setup"
            groupMenu = "Engine"
                groupChildMenu = engineDialog, "Engine Settings", 0, { mode > 0 }

        [UserDefined]
        dialog = engineDialog, "Engine Settings", yAxis
            field = "RPM Target", rpmTarget
            field = "Mode", mode, { rpmTarget > 0 }
    """.trimIndent()
}
