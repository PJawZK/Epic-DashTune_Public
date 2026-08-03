package com.buttonbox.ble

import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local authority for USB connection attempts and invalidations.
 *
 * Every attempt consumes a generation, including attempts that fail or are cancelled. Callbacks
 * must carry their captured generation so consumers can reject work from an obsolete attempt.
 */
class UsbGenerationAuthority(initialGeneration: Long = 0L) {
    private val allocator = AtomicLong(initialGeneration)
    private val publicationLock = Any()

    fun next(): Long = synchronized(publicationLock) {
        allocator.incrementAndGet()
    }

    /** Advances authority and performs cleanup for the invalidated generation under the publication lock. */
    fun next(onInvalidated: (Long) -> Unit): Long = synchronized(publicationLock) {
        val previous = allocator.get()
        val next = allocator.incrementAndGet()
        onInvalidated(previous)
        next
    }

    fun current(): Long = allocator.get()

    fun isCurrent(generation: Long): Boolean = generation == allocator.get()

    /** Reads generation-owned state under the same lock used by publication and invalidation. */
    fun <T> currentSnapshot(action: (Long) -> T): T = synchronized(publicationLock) {
        action(allocator.get())
    }

    /**
     * Publishes generation-owned work atomically with respect to [next].
     *
     * An invalidation can therefore never occur between the current-generation check and the
     * callback/state mutation it protects.
     */
    fun runIfCurrent(generation: Long, action: () -> Unit): Boolean = synchronized(publicationLock) {
        if (generation != allocator.get()) {
            false
        } else {
            action()
            true
        }
    }
}
