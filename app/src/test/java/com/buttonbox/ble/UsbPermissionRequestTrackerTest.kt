package com.buttonbox.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UsbPermissionRequestTrackerTest {
    @Test
    fun delayedGrantOrDenialCannotConsumeSupersedingRequest() {
        val tracker = UsbPermissionRequestTracker()
        tracker.replace(deviceId = 7, generation = 1)
        val current = tracker.replace(deviceId = 7, generation = 2)

        assertNull("generation A grant must be ignored", tracker.consume(7, 1))
        assertEquals(current, tracker.snapshot())
        assertNull("generation A denial follows the same ownership check", tracker.consume(7, 1))
        assertEquals(current, tracker.snapshot())
        assertEquals(current, tracker.consume(7, 2))
        assertNull(tracker.snapshot())
    }

    @Test
    fun unrelatedDeviceCallbackIsIgnored() {
        val tracker = UsbPermissionRequestTracker()
        val request = tracker.replace(deviceId = 7, generation = 3)

        assertNull(tracker.consume(deviceId = 8, generation = 3))
        assertEquals(request, tracker.snapshot())
    }

    @Test
    fun disconnectDetachAndShutdownClearOnlyTheirOwnedRequest() {
        listOf("disconnect", "physical detach", "shutdown").forEach { lifecycleEvent ->
            val tracker = UsbPermissionRequestTracker()
            tracker.replace(deviceId = 7, generation = 10)

            assertTrue(lifecycleEvent, tracker.clearOwnedBy(10))
            assertNull(lifecycleEvent, tracker.snapshot())
        }
    }

    @Test
    fun obsoleteInvalidationCannotClearNewerRequest() {
        val tracker = UsbPermissionRequestTracker()
        tracker.replace(deviceId = 7, generation = 20)
        val newer = tracker.replace(deviceId = 7, generation = 21)

        assertFalse(tracker.clearOwnedBy(20))
        assertEquals(newer, tracker.snapshot())
    }

    @Test
    fun repeatedSupersededRequestsRemainBoundedToOneEntry() {
        val tracker = UsbPermissionRequestTracker()
        repeat(10_000) { generation ->
            tracker.replace(deviceId = generation % 3, generation = generation.toLong())
        }

        assertEquals(UsbPermissionRequestTracker.Request(0, 9_999), tracker.snapshot())
        assertEquals(tracker.snapshot(), tracker.consume(0, 9_999))
        assertNull(tracker.snapshot())
    }

    @Test
    fun invalidationAfterDiscoveryCheckPreventsObsoletePublicationAndRequestAction() {
        val authority = UsbGenerationAuthority()
        val tracker = UsbPermissionRequestTracker()
        val coordinator = UsbPermissionRequestCoordinator(authority, tracker)
        val generationA = coordinator.advanceGeneration()
        assertTrue(authority.isCurrent(generationA)) // A passed its earlier discovery check.

        val generationB = coordinator.advanceGeneration()
        val obsoleteActionCalled = AtomicBoolean(false)
        assertFalse(coordinator.publishIfCurrent(7, generationA) { obsoleteActionCalled.set(true) })
        assertFalse(obsoleteActionCalled.get())
        assertNull(tracker.snapshot())

        val currentActionCalled = AtomicBoolean(false)
        assertTrue(coordinator.publishIfCurrent(7, generationB) { currentActionCalled.set(true) })
        assertTrue(currentActionCalled.get())
        assertEquals(UsbPermissionRequestTracker.Request(7, generationB), tracker.snapshot())
    }

    @Test
    fun concurrentInvalidationCannotLeaveObsoletePendingState() {
        val authority = UsbGenerationAuthority()
        val tracker = UsbPermissionRequestTracker()
        val coordinator = UsbPermissionRequestCoordinator(authority, tracker)
        val generation = coordinator.advanceGeneration()
        val publicationEntered = CountDownLatch(1)
        val releasePublication = CountDownLatch(1)
        val invalidationFinished = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        pool.execute {
            coordinator.publishIfCurrent(7, generation) {
                publicationEntered.countDown()
                releasePublication.await()
            }
        }
        assertTrue(publicationEntered.await(5, TimeUnit.SECONDS))
        pool.execute {
            coordinator.advanceGeneration()
            invalidationFinished.countDown()
        }
        assertFalse(invalidationFinished.await(100, TimeUnit.MILLISECONDS))
        releasePublication.countDown()
        assertTrue(invalidationFinished.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertNull(tracker.snapshot())
    }
}
