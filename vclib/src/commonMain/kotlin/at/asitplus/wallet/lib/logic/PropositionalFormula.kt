package at.asitplus.wallet.lib.logic


interface PropositionalFormula<T> : BooleanFormula<T> {
    class Atom<T>(val value: T) : PropositionalFormula<T> {
        override fun evaluate(values: Collection<T>): Boolean {
            return values.contains(value)
        }
    }

    class And<T>(val children: Collection<PropositionalFormula<T>>) : PropositionalFormula<T> {
        override fun evaluate(values: Collection<T>): Boolean {
            return children.all {
                it.evaluate(values)
            }
        }
    }

    class Or<T>(val children: Collection<PropositionalFormula<T>>) : PropositionalFormula<T> {
        override fun evaluate(values: Collection<T>): Boolean {
            return children.any {
                it.evaluate(values)
            }
        }
    }

    class Not<T>(val child: PropositionalFormula<T>) : PropositionalFormula<T> {
        override fun evaluate(values: Collection<T>): Boolean {
            return !child.evaluate(values)
        }
    }
}