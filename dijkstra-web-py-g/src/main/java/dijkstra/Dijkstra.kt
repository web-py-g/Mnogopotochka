package dijkstra

import java.util.*
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.random.Random
import java.lang.Integer.min


private val NODE_DISTANCE_COMPARATOR = Comparator<Node> { o1, o2 -> o1!!.distance.compareTo(o2!!.distance) }

class MultiQueue<T>(queuesCount: Int, private val comparator: Comparator<T>) {

    private val queues = Array(2 * queuesCount) { PriorityQueue(comparator) }
    private val locks = Array(2 * queuesCount) { ReentrantLock(true) }

    fun add(element: T) {
        while (true) {
            val randId = Random.nextInt(0, queues.size);
            val randQueue = queues[randId];

            if (locks[randId].tryLock()) {
                try {
                    randQueue.add(element);
                    return;
                } finally {
                    locks[randId].unlock();
                }
            }
        }
    }

    fun extractMin(): T? {
        while (true) {
            val randId1 = Random.nextInt(0, queues.size);
            val randId2 = Random.nextInt(0, queues.size);

            val minId = min(randId1, randId2);
            val maxId = max(randId1, randId2);

            val randQueue1 = queues[minId];
            val randQueue2 = queues[maxId];

            if (locks[minId].tryLock()) {
                if (locks[maxId].tryLock()) {
                    try {
                        val minNode1 = randQueue1.peek();
                        val minNode2 = randQueue2.peek();

                        return if (minNode1 == null && minNode2 == null) null;
                        else if (minNode1 != null)
                            randQueue1.poll();
                        else if (minNode2 != null)
                            randQueue2.poll();
                        else
                            randQueue1.poll();

                    } finally {
                        locks[maxId].unlock();
                        locks[minId].unlock();
                    }
                } else
                    return randQueue1.poll();
            }
        }
    }
}
// Returns `Integer.MAX_VALUE` if a path has not been found.
fun shortestPathParallel(start: Node) {
    val workers = Runtime.getRuntime().availableProcessors()
    start.distance = 0
    val q = MultiQueue(workers, NODE_DISTANCE_COMPARATOR)
    q.add(start)
    val counter = AtomicInteger(1); // Active Nodes
    val onFinish = Phaser(workers + 1)
    repeat(workers) {
        thread {
            while (counter.get() > 0) {
                val cur = q.extractMin();

                cur?.run {
                    for (el in cur.outgoingEdges) {
                        while (true) {
                            val d1 = el.to.distance
                            val d2 = cur.distance + el.weight

                            if (el.to.distance > cur.distance + el.weight) {
                                if (el.to.casDistance(d1, d2)) {
                                    q.add(el.to)
                                    counter.incrementAndGet()
                                    break
                                } else continue
                            } else break
                        }
                    }
                    counter.decrementAndGet()
                }
            }
            onFinish.arrive()
        }
    }
    onFinish.arriveAndAwaitAdvance()
}
