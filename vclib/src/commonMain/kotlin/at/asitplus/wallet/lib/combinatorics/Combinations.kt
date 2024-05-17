package at.asitplus.wallet.lib.combinatorics

class Combinations(val total: Int, val select: Int) : Iterable<List<Int>> {
    inner class Iterator : kotlin.collections.Iterator<List<Int>> {
        var nextValue: MutableList<Int>? = if(select < 0) {
            null
        } else if(select > total) {
            null
        } else (0..<select).map {
            it
        }.toMutableList()

        override fun hasNext(): Boolean {
            return nextValue != null
        }

        override fun next(): List<Int> {
            val value = nextValue!!
            val returnValue = value

            if(value.lastIndex == -1) {
                nextValue = null
            } else {
                value[value.lastIndex] += 1
                val nextIndexToCheck = value.lastIndex
                for(index in nextIndexToCheck downTo 0) {
                    if(value[index] >= total && index > 0) {
                        value[index - 1] += 1
                        value[index] = value[index - 1] + 1
                    }
                    if(value[index] >= total) {
                        nextValue = null
                    }
                }
            }

            return returnValue
        }
    }

    override fun iterator(): kotlin.collections.Iterator<List<Int>> {
        return Iterator()
    }
}
