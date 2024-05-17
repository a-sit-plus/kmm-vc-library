package at.asitplus.wallet.lib.logic


sealed interface PropositionalFormula<T> {
    fun evaluate(values: Collection<T>): Boolean
    fun <R> mapValues(transform: (T) -> R): PropositionalFormula<R>

    class And<T>(val children: Collection<PropositionalFormula<T>>) : PropositionalFormula<T> {
        constructor(vararg children: PropositionalFormula<T>) : this(children.toList())
        override fun evaluate(values: Collection<T>): Boolean {
            return children.all {
                it.evaluate(values)
            }
        }

        override fun <R> mapValues(transform: (T) -> R): PropositionalFormula<R> {
            return And(
                this.children.map {
                    it.mapValues(transform)
                }
            )
        }
    }

    class Or<T>(val children: Collection<PropositionalFormula<T>>) : PropositionalFormula<T> {
        constructor(vararg children: PropositionalFormula<T>) : this(children.toList())
        override fun evaluate(values: Collection<T>): Boolean {
            return children.any {
                it.evaluate(values)
            }
        }

        override fun <R> mapValues(transform: (T) -> R): PropositionalFormula<R> {
            return Or(
                this.children.map {
                    it.mapValues(transform)
                }
            )
        }
    }

    class Not<T>(val child: PropositionalFormula<T>) : PropositionalFormula<T> {
        override fun evaluate(values: Collection<T>): Boolean {
            return !child.evaluate(values)
        }

        override fun <R> mapValues(transform: (T) -> R): PropositionalFormula<R> {
            return Not(
                this.child.mapValues(transform)
            )
        }
    }

    class Atom<T>(val value: T) : PropositionalFormula<T> {
        override fun evaluate(values: Collection<T>): Boolean {
            return values.contains(value)
        }

        override fun <R> mapValues(transform: (T) -> R): PropositionalFormula<R> {
            return Atom(transform(this.value))
        }
    }
}