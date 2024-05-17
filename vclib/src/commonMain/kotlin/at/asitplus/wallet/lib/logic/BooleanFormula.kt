package at.asitplus.wallet.lib.logic

interface BooleanFormula<T> {
    fun evaluate(values: Collection<T>): Boolean
}