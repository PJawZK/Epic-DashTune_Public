package com.buttonbox.ble

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticHistoryCodecTest {
    @Test
    fun missingBlankAndMalformedHistoryDecodeAsEmpty() {
        assertEquals(0, DiagnosticHistoryCodec.decode(null).length())
        assertEquals(0, DiagnosticHistoryCodec.decode("  ").length())
        assertEquals(0, DiagnosticHistoryCodec.decode("not-json").length())
    }

    @Test
    fun validHistoryPreservesStoredOrderAndValues() {
        val decoded = DiagnosticHistoryCodec.decode(
            """[{"id":"first","repeatCount":1},{"id":"second","repeatCount":3}]"""
        )

        assertEquals(2, decoded.length())
        assertEquals("first", decoded.getJSONObject(0).getString("id"))
        assertEquals(3, decoded.getJSONObject(1).getInt("repeatCount"))
    }

    @Test
    fun reversedReturnsNewestFirstWithoutChangingSource() {
        val source = JSONArray()
            .put(JSONObject().put("id", "oldest"))
            .put(JSONObject().put("id", "middle"))
            .put(JSONObject().put("id", "newest"))

        val reversed = DiagnosticHistoryCodec.reversed(source)

        assertEquals("newest", reversed.getJSONObject(0).getString("id"))
        assertEquals("middle", reversed.getJSONObject(1).getString("id"))
        assertEquals("oldest", reversed.getJSONObject(2).getString("id"))
        assertEquals("oldest", source.getJSONObject(0).getString("id"))
        assertEquals("newest", source.getJSONObject(2).getString("id"))
    }
}
