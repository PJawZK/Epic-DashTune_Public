package com.buttonbox.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UsbGenerationAuthorityTest {
    @Test
    fun currentSnapshotUsesTheSameGenerationBoundaryAsPublication() {
        val authority = UsbGenerationAuthority(41L)

        val observed = authority.currentSnapshot { generation -> generation }

        assertEquals(41L, observed)
        assertEquals(42L, authority.next())
        assertEquals(42L, authority.currentSnapshot { generation -> generation })
    }

    @Test
    fun disconnectReconnectAndOldCallbacksRemainOrdered() {
        val authority = UsbGenerationAuthority()
        val connected = authority.next()
        val invalidated = authority.next()
        val reconnected = authority.next()

        assertTrue(invalidated > connected)
        assertTrue(reconnected > invalidated)
        assertFalse(authority.isCurrent(connected))
        assertFalse(authority.isCurrent(invalidated))
        assertTrue(authority.isCurrent(reconnected))
    }

    @Test
    fun failedAndCancelledAttemptsNeverReuseGeneration() {
        val authority = UsbGenerationAuthority()
        val failed = authority.next()
        val cancelled = authority.next()
        val successful = authority.next()

        assertEquals(listOf(1L, 2L, 3L), listOf(failed, cancelled, successful))
        assertTrue(authority.isCurrent(successful))
    }

    @Test
    fun repeatedPhysicalAndManualCyclesStayMonotonic() {
        val authority = UsbGenerationAuthority()
        val observed = buildList {
            repeat(3) {
                add(authority.next()) // connected attempt
                add(authority.next()) // physical removal invalidation
            }
            add(authority.next()) // manual reconnect
            add(authority.next()) // manual disconnect
            add(authority.next()) // final reconnect
        }

        assertTrue(observed.zipWithNext().all { (left, right) -> right > left })
        assertEquals(observed.last(), authority.current())
    }

    @Test
    fun concurrentAllocationsHaveOneAtomicCurrentAuthority() {
        val authority = UsbGenerationAuthority()
        val workers = 12
        val allocationsPerWorker = 2_000
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val observed = ConcurrentHashMap.newKeySet<Long>()
        val pool = Executors.newFixedThreadPool(workers)

        repeat(workers) {
            pool.execute {
                ready.countDown()
                start.await()
                repeat(allocationsPerWorker) {
                    observed += authority.next()
                }
                done.countDown()
            }
        }

        assertTrue("workers did not become ready", ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue("allocations did not finish", done.await(10, TimeUnit.SECONDS))
        pool.shutdownNow()

        val expected = workers * allocationsPerWorker
        assertEquals(expected, observed.size)
        assertEquals(expected.toLong(), authority.current())
        assertEquals(1L, observed.minOrNull())
        assertEquals(expected.toLong(), observed.maxOrNull())
    }

    @Test
    fun invalidationMakesEveryCapturedOlderCallbackIneligible() {
        val authority = UsbGenerationAuthority()
        val connectionAttempt = authority.next()
        val permissionCallback = connectionAttempt
        val retryCallback = connectionAttempt
        val mailboxCallback = connectionAttempt
        val invalidation = authority.next()

        assertTrue(authority.isCurrent(invalidation))
        assertFalse(authority.isCurrent(permissionCallback))
        assertFalse(authority.isCurrent(retryCallback))
        assertFalse(authority.isCurrent(mailboxCallback))

        val reconnect = authority.next()
        assertTrue(reconnect > invalidation)
        assertTrue(authority.isCurrent(reconnect))
        assertFalse(authority.isCurrent(invalidation))
    }

    @Test
    fun invalidationCannotSplitGenerationCheckFromPublication() {
        val authority = UsbGenerationAuthority()
        val attempt = authority.next()
        val publicationEntered = CountDownLatch(1)
        val releasePublication = CountDownLatch(1)
        val invalidationFinished = CountDownLatch(1)
        val published = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)

        pool.execute {
            authority.runIfCurrent(attempt) {
                publicationEntered.countDown()
                releasePublication.await()
                published.set(true)
            }
        }
        assertTrue(publicationEntered.await(5, TimeUnit.SECONDS))

        pool.execute {
            authority.next()
            invalidationFinished.countDown()
        }
        assertFalse(
            "invalidation advanced inside a generation-owned publication",
            invalidationFinished.await(100, TimeUnit.MILLISECONDS)
        )

        releasePublication.countDown()
        assertTrue(invalidationFinished.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertTrue(published.get())
        assertFalse(authority.isCurrent(attempt))
        assertFalse(authority.runIfCurrent(attempt) { published.set(false) })
        assertTrue(published.get())
    }
}
