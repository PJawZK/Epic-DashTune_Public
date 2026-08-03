package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CopyOnWriteArraySet

class GpsListenerDispatcherCharacterizationTest {
    @Test fun `zero listeners produce zero fan out`() {
        assertEquals(GpsListenerDispatcher.Result(0, 0, 0), GpsListenerDispatcher.dispatch(emptyList<Int>()) { })
    }

    @Test fun `one and multiple listeners receive in iteration order`() {
        val delivered = mutableListOf<Int>()
        val result = GpsListenerDispatcher.dispatch(listOf(1, 2, 3)) { delivered += it }
        assertEquals(listOf(1, 2, 3), delivered)
        assertEquals(GpsListenerDispatcher.Result(3, 3, 0), result)
    }

    @Test fun `isolates throwing GPS listener and continues later delivery`() {
        val delivered = mutableListOf<Int>()
        val result = GpsListenerDispatcher.dispatch(listOf(1, 2, 3)) {
            if (it == 2) throw IllegalStateException("characterized listener failure")
            delivered += it
        }
        assertEquals(listOf(1, 3), delivered)
        assertEquals(GpsListenerDispatcher.Result(3, 2, 1), result)
    }

    @Test fun `copy on write registration remains idempotent and removable`() {
        val listener = Any()
        val listeners = CopyOnWriteArraySet<Any>()
        listeners += listener
        listeners += listener
        assertEquals(1, listeners.size)
        listeners -= listener
        assertEquals(0, listeners.size)
    }

    @Test fun `copy on write snapshot preserves current dispatch when listeners mutate set`() {
        val delivered = mutableListOf<Int>()
        val listeners = CopyOnWriteArraySet(listOf(1, 2, 3))
        val result = GpsListenerDispatcher.dispatch(listeners) { listener ->
            delivered += listener
            if (listener == 1) {
                listeners -= 2
                listeners += 4
            }
        }

        assertEquals(listOf(1, 2, 3), delivered)
        assertEquals(listOf(1, 3, 4), listeners.toList())
        assertEquals(GpsListenerDispatcher.Result(3, 3, 0), result)
    }
}
