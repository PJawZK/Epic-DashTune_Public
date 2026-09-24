package com.buttonbox.ble

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Regression coverage for the firmware/profile-driven ECU compatibility boundary.
 *
 * The private repository carries a real generated Mega144H7 mainController.ini fixture and parses it
 * end-to-end. That generated vehicle/ECU artifact is deliberately excluded from the curated public
 * source export, so the real-fixture test is skipped when the file is unavailable here. The other
 * cases intentionally use reduced generated-style fixtures carrying only fields verified during the
 * 2026-09-22 cross-board generated-definition audit. They prove that differing signatures and memory
 * geometry remain imported profile data; they are not substitutes for full generated INIs when real
 * artifacts are available.
 */
class GeneratedIniCrossBoardCompatibilityTest {
    @Test
    fun realMega144H7GeneratedIniParsesWithItsDistinctGeometry() {
        val fixture = findRepositoryFixture("reference/ecu/mainController-msf1000000548.ini")
        assumeTrue(
            "Private real Mega144H7 generated-INI fixture is intentionally unavailable in the curated public source",
            fixture != null
        )
        val ini = requireNotNull(fixture).readText()
        val profile = UsbTunerStudioProfileParser.parse(ini, "mainController-msf1000000548.ini")

        assertEquals("rusEFI master.2026.05.24.MEGA144H7.3684405155", profile.signature)
        assertEquals("S", profile.queryCommand)
        assertEquals("O%2o%2c", profile.outputCommand)
        assertEquals(3716, profile.outputBlockSize)
        assertTrue(profile.envelopeFormat.equals("msEnvelope_1.0", ignoreCase = true))
        assertTrue(profile.endianness.equals("little", ignoreCase = true))
        assertEquals(listOf(54116, 5000), profile.tunePages.map { it.size })
        assertTrue(profile.channels.isNotEmpty())
        assertTrue(profile.tuneScalars.isNotEmpty())
        assertTrue(profile.tuneArrays.isNotEmpty())

        val definition = TuningIniDefinitionBuilder.build(profile)
        assertEquals(profile.signature, definition.getString("profileSignature"))
        assertEquals(profile.tuneProfileFingerprint(), definition.getString("profileFingerprint"))
    }

    @Test
    fun auditedCrossBoardGeometryRemainsImportedProfileData() {
        val cases = listOf(
            GeneratedProfileContract(
                name = "MEGA144F7",
                signature = "rusEFI master.2026.09.09.mega144-f7.126804072",
                outputBlockSize = 2216,
                pageSizes = listOf(18508, 256, 2048, 1268, 6000)
            ),
            GeneratedProfileContract(
                name = "Proteus F7",
                signature = "rusEFI master.2026.09.22.proteus_f7.2068737802",
                outputBlockSize = 2216,
                pageSizes = listOf(16900, 256, 2048, 1268, 48000)
            ),
            GeneratedProfileContract(
                name = "uaEFI Pro H7",
                signature = "rusEFI master.2026.09.22.uaefi_pro_h7.2775659437",
                outputBlockSize = 2216,
                pageSizes = listOf(16820, 256, 2048, 1268, 48000)
            ),
            GeneratedProfileContract(
                name = "AlphaX 4-channel F7",
                signature = "rusEFI master.2026.09.22.alphax-4chan_f7.682794145",
                outputBlockSize = 2216,
                pageSizes = listOf(16896, 256, 2048, 1268, 8000)
            ),
            GeneratedProfileContract(
                name = "microRusEFI F7",
                signature = "rusEFI master.2026.09.22.mre_f7.226827363",
                outputBlockSize = 2216,
                pageSizes = listOf(16764, 256, 2048, 1268, 10000)
            )
        )

        cases.forEach { contract ->
            val profile = UsbTunerStudioProfileParser.parse(
                reducedGeneratedStyleIni(contract),
                "${contract.name}.ini"
            )

            assertEquals(contract.name, contract.signature, profile.signature)
            assertEquals(contract.name, contract.outputBlockSize, profile.outputBlockSize)
            assertEquals(contract.name, contract.pageSizes, profile.tunePages.map { it.size })
            assertEquals(contract.name, contract.pageSizes.size, profile.tunePages.size)
            assertEquals(contract.name, "S", profile.queryCommand)
            assertEquals(contract.name, "O%2o%2c", profile.outputCommand)
            assertTrue(contract.name, profile.envelopeFormat.equals("msEnvelope_1.0", ignoreCase = true))
            assertTrue(contract.name, profile.endianness.equals("little", ignoreCase = true))
            assertTrue(contract.name, profile.tunePages.all { it.readCommand == "R%2i%2o%2c" })

            val definition = TuningIniDefinitionBuilder.build(profile)
            assertEquals(contract.name, contract.signature, definition.getString("profileSignature"))
        }
    }

    private fun reducedGeneratedStyleIni(contract: GeneratedProfileContract): String {
        val identifiers = contract.pageSizes.indices.joinToString(", ") { index ->
            val low = index and 0xff
            val high = (index ushr 8) and 0xff
            "\"\\\\x%02x\\\\x%02x\"".format(low, high)
        }
        val sizes = contract.pageSizes.joinToString(", ")
        val readCommands = contract.pageSizes.joinToString(", ") { "\"R%2i%2o%2c\"" }

        return """
            [TunerStudio]
            queryCommand = "S"
            signature = "${contract.signature}"

            [Constants]
            messageEnvelopeFormat = msEnvelope_1.0
            endianness = little
            pageIdentifier = $identifiers
            pageSize = $sizes
            pageReadCommand = $readCommands
            page = 1
            compatibilityProbe = scalar, U16, 0, "", 1, 0, 0, 65535, 0

            [OutputChannels]
            ochGetCommand = "O%2o%2c"
            ochBlockSize = ${contract.outputBlockSize}
            RPMValue = scalar, U16, 0, "RPM", 1, 0
        """.trimIndent()
    }

    private fun findRepositoryFixture(relativePath: String): File? {
        val start = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(start) { it.parentFile }
            .take(8)
            .map { root -> File(root, relativePath) }
            .firstOrNull { candidate -> candidate.isFile }
    }

    private data class GeneratedProfileContract(
        val name: String,
        val signature: String,
        val outputBlockSize: Int,
        val pageSizes: List<Int>
    )
}
