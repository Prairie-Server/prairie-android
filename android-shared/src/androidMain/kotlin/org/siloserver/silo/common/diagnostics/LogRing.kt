package org.siloserver.silo.common.diagnostics

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReferenceArray

data class LogSnapshot(
    val lines: List<String>,
    val droppedCount: Long,
    val tornCount: Long,
    val generation: Long,
)

interface DiagnosticsLogBuffer : DiagnosticsLogSink {
    val currentGeneration: Long
    fun rotateGeneration(): Long
    fun snapshot(expectedGeneration: Long = currentGeneration): LogSnapshot
    fun clear()
}

/** A lock-free, bounded, newest-preserving diagnostics buffer. */
class LogRing(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) : DiagnosticsLogBuffer {
    private val slots: AtomicReferenceArray<Entry?>
    private val generation = AtomicLong(0)
    private val epoch = AtomicLong(0)
    private val nextSequence = AtomicLong(0)
    private val evictionSequence = AtomicLong(0)
    private val byteCount = AtomicLong(0)
    private val droppedCount = AtomicLong(0)
    private val tornCount = AtomicLong(0)

    init {
        require(capacity > 0) { "capacity must be positive" }
        require(maxBytes > 0) { "maxBytes must be positive" }
        slots = AtomicReferenceArray(capacity)
    }

    override val currentGeneration: Long
        get() = generation.get()

    override fun offer(renderedJsonLine: String) {
        val offeredGeneration = generation.get()
        val offeredEpoch = epoch.get()
        if (renderedJsonLine.isEmpty() || renderedJsonLine.indexOfAny(charArrayOf('\r', '\n')) >= 0) {
            if (offeredGeneration == generation.get() && offeredEpoch == epoch.get()) {
                tornCount.incrementAndGet()
            }
            return
        }
        val encodedBytes = renderedJsonLine.encodeToByteArray().size
        if (encodedBytes > maxBytes) {
            if (offeredGeneration == generation.get() && offeredEpoch == epoch.get()) {
                droppedCount.incrementAndGet()
            }
            return
        }

        val sequence = nextSequence.getAndIncrement()
        val entry = Entry(sequence, offeredGeneration, offeredEpoch, renderedJsonLine, encodedBytes)
        val index = (sequence % capacity).toInt()
        while (true) {
            if (offeredGeneration != generation.get() || offeredEpoch != epoch.get()) return
            val previous = slots.get(index)
            if (previous != null && previous.sequence >= sequence) {
                droppedCount.incrementAndGet()
                return
            }
            if (!slots.compareAndSet(index, previous, entry)) continue

            val previousBytes = previous
                ?.takeIf { it.generation == offeredGeneration && it.epoch == offeredEpoch }
                ?.bytes
                ?: 0
            byteCount.addAndGet((encodedBytes - previousBytes).toLong())
            if (previous != null && previous.generation == offeredGeneration && previous.epoch == offeredEpoch) {
                droppedCount.incrementAndGet()
                advanceEvictionFloor(previous.sequence + 1)
            }

            if (offeredGeneration != generation.get() || offeredEpoch != epoch.get()) {
                if (slots.compareAndSet(index, entry, null)) byteCount.addAndGet(-encodedBytes.toLong())
                return
            }
            evictToByteLimit(offeredGeneration, offeredEpoch)
            return
        }
    }

    override fun snapshot(expectedGeneration: Long): LogSnapshot {
        val activeGeneration = generation.get()
        val activeEpoch = epoch.get()
        if (expectedGeneration != activeGeneration) {
            return LogSnapshot(emptyList(), 0, 0, expectedGeneration)
        }
        val entries = ArrayList<Entry>(capacity)
        repeat(capacity) { index ->
            slots.get(index)?.takeIf {
                it.generation == expectedGeneration && it.epoch == activeEpoch
            }?.let(entries::add)
        }
        entries.sortBy(Entry::sequence)

        // Defense against accounting races: retain the newest suffix within the byte budget.
        var retainedBytes = 0L
        var firstRetained = entries.size
        for (index in entries.lastIndex downTo 0) {
            val nextBytes = retainedBytes + entries[index].bytes
            if (nextBytes > maxBytes) break
            retainedBytes = nextBytes
            firstRetained = index
        }
        val snapshotEntries = if (firstRetained == entries.size) emptyList() else entries.subList(firstRetained, entries.size)
        val defensivelyDropped = (entries.size - snapshotEntries.size).toLong()
        return LogSnapshot(
            lines = snapshotEntries.map(Entry::line),
            droppedCount = droppedCount.get() + defensivelyDropped,
            tornCount = tornCount.get(),
            generation = expectedGeneration,
        )
    }

    override fun rotateGeneration(): Long {
        generation.incrementAndGet()
        clearStorage()
        return generation.get()
    }

    override fun clear() {
        clearStorage()
    }

    private fun clearStorage() {
        epoch.incrementAndGet()
        repeat(capacity) { index -> slots.set(index, null) }
        byteCount.set(0)
        droppedCount.set(0)
        tornCount.set(0)
        evictionSequence.set(nextSequence.get())
    }

    private fun evictToByteLimit(expectedGeneration: Long, expectedEpoch: Long) {
        while (byteCount.get() > maxBytes) {
            val sequence = evictionSequence.getAndIncrement()
            val index = (sequence % capacity).toInt()
            val candidate = slots.get(index) ?: continue
            if (
                candidate.sequence != sequence ||
                candidate.generation != expectedGeneration ||
                candidate.epoch != expectedEpoch
            ) {
                continue
            }
            if (slots.compareAndSet(index, candidate, null)) {
                byteCount.addAndGet(-candidate.bytes.toLong())
                droppedCount.incrementAndGet()
            }
        }
    }

    private fun advanceEvictionFloor(minimum: Long) {
        while (true) {
            val current = evictionSequence.get()
            if (current >= minimum || evictionSequence.compareAndSet(current, minimum)) return
        }
    }

    private data class Entry(
        val sequence: Long,
        val generation: Long,
        val epoch: Long,
        val line: String,
        val bytes: Int,
    )

    private companion object {
        const val DEFAULT_CAPACITY = 4_000
        const val DEFAULT_MAX_BYTES = 1_500 * 1_024
    }
}
