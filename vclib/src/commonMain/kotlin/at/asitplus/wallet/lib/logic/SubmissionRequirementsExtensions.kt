package at.asitplus.wallet.lib.logic

import at.asitplus.wallet.lib.combinatorics.Combinations
import at.asitplus.wallet.lib.data.dif.SubmissionRequirement
import at.asitplus.wallet.lib.data.dif.SubmissionRequirementRuleEnum

fun SubmissionRequirement.toPropositionalFormulaOverInputDescriptors(inputDescriptorGroups: Map<String, String>): PropositionalFormula<String> {
    return when (rule) {
        SubmissionRequirementRuleEnum.ALL -> when {
            from != null -> {
                PropositionalFormula.And(
                    inputDescriptorGroups.filter {
                        it.value == from
                    }.map {
                        PropositionalFormula.Atom(it.key)
                    }
                )
            }

            fromNested != null -> {
                PropositionalFormula.And(
                    fromNested.map {
                        it.toPropositionalFormulaOverInputDescriptors(
                            inputDescriptorGroups
                        )
                    }
                )
            }

            else -> TODO()
        }

        SubmissionRequirementRuleEnum.PICK -> when {
            from != null -> {
                inputDescriptorGroups.filter {
                    it.value == from
                }.map {
                    PropositionalFormula.Atom(it.key)
                }
            }

            fromNested != null -> {
                fromNested.map {
                    it.toPropositionalFormulaOverInputDescriptors(
                        inputDescriptorGroups
                    )
                }
            }

            else -> TODO()
        }.let { childFormulas ->
            PropositionalFormula.And(
                listOfNotNull(
                    this.count?.let { count ->
                        PropositionalFormula.Or(
                            childFormulas.combinations(count).map {
                                PropositionalFormula.And(it)
                            }
                        )
                    },
                    this.min?.let { min ->
                        PropositionalFormula.Or(
                            (min..childFormulas.size).flatMap { count ->
                                childFormulas.combinations(count).map {
                                    PropositionalFormula.And(it)
                                }
                            }
                        )
                    },
                    this.max?.let {
                        PropositionalFormula.Or(
                            (0 .. max).flatMap { count ->
                                childFormulas.combinations(count).map {
                                    PropositionalFormula.And(it)
                                }
                            }
                        )
                    },
                )
            )
        }

        else -> TODO()
    }
}

fun <T> List<T>.combinations(select: Int): List<List<T>> {
    return Combinations(select = select, total = this.size).map { combination ->
        combination.map {
            this[it]
        }
    }
}