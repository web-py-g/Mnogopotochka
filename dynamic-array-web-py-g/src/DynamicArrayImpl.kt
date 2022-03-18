import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
class DynamicArrayImpl<E> : DynamicArray<E> {
    private val sz = atomic(0)
    private val core = atomic(Core<E>(INITIAL_CAPACITY))

    override fun get(index: Int): E {
        if (index >= size) throw IllegalArgumentException()
        while (true) {
            val core = core.value
            val value = core.array[index].value
            if (needMove(core, value) && value is NotNeedMove<*>) {
                return value.value as E
            }
        }
    }

    override fun put(index: Int, element: E) {
        if (index >= size) throw IllegalArgumentException()
        while (true) {
            val core = core.value
            val value = core.array[index].value
            if (needMove(core, value) && notNeedMove(index, core, element, value)) {
                return
            }
        }
    }

    override fun pushBack(element: E) {
        while (true) {
            val curSize = size
            val core = core.value
            if (core.capacity <= curSize) {
                move(core)
            } else if (notNeedMove(curSize, core, element)) {
                sz.incrementAndGet()
                return
            }
        }
    }

    override val size: Int
        get() {
            return sz.value
        }

    private fun notNeedMove(i : Int, core: Core<E>, value: Any?, expect: Any? = null): Boolean =
        core.array[i].compareAndSet(expect, NotNeedMove(value))

    private fun needMove(core: Core<E>, value: Any?): Boolean =
        if (value is NeedMove<*>) {
            move(core)
            false
        } else true

    private fun move(core: Core<E>) {
        core.next.value ?: core.next.compareAndSet(null, Core(2 * core.capacity))
        val next = core.next.value ?: return
        (0 until core.capacity).forEach { num ->
            var value: Any?

            do value = core.array[num].value
            while (value is NotNeedMove<*> && !core.array[num].compareAndSet(value, NeedMove(value.value)))

            if (value is NeedMove<*>) notNeedMove(num, next, value.value)
            if (value is NotNeedMove<*>) notNeedMove(num, next, value.value)
        }
        this.core.compareAndSet(core, next)
    }
}

private class NeedMove<E>(val value: E)
private class NotNeedMove<E>(val value: E)

private class Core<E>(
    val capacity: Int
) {
    val array = atomicArrayOfNulls<Any>(capacity)
    val next: AtomicRef<Core<E>?> = atomic(null)
}

private const val INITIAL_CAPACITY = 1 // DO NOT CHANGE ME