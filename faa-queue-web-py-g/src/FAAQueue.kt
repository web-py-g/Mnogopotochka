import kotlinx.atomicfu.*

class FAAQueue<T> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: T) {
        while (true) {
            val curTail = this.tail.value;
            val enqIdx = curTail.enqIdx.getAndIncrement();
            if (curTail.next.value != null) {
                this.tail.compareAndSet(curTail, curTail.next.value!!);
                continue;
            }

            if (enqIdx < SEGMENT_SIZE && curTail.elements[enqIdx].compareAndSet(null, x))
                return

            if (enqIdx >= SEGMENT_SIZE) {
                if (curTail.next.value == null && curTail.next.compareAndSet(null, Segment(x)))
                    return;
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): T? {
        while (true) {
            val curHead = this.head.value;
            val deqIdx = curHead.deqIdx.getAndIncrement();
            if (deqIdx >= SEGMENT_SIZE) {
                if (curHead.next.value == null) return null;
                this.head.compareAndSet(curHead, curHead.next.value!!);
            } else {
                val res = curHead.elements[deqIdx].getAndSet(DONE) ?: continue;
                return res as T?;
            }
        }
    }

    /**
     * Returns `true` if this queue is empty;
     * `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                if (head.value.isEmpty) {
                    if (head.value.next.value == null) return true
                    head.value = head.value.next.value!!
                    continue
                } else {
                    return false
                }
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0) // index for the next enqueue operation
    val deqIdx = atomic(0) // index for the next dequeue operation
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    constructor() // for the first segment creation

    constructor(x: Any?) { // each next new segment should be constructed with an element
        enqIdx.getAndSet(1)
        elements[0].getAndSet(x)
    }

    val isEmpty: Boolean get() = deqIdx.value >= enqIdx.value || deqIdx.value >= SEGMENT_SIZE

}

private val DONE = Any() // Marker for the "DONE" slot state; to avoid memory leaks
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS