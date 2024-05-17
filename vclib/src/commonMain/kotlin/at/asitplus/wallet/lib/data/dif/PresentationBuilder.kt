package at.asitplus.wallet.lib.data.dif

import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.logic.PropositionalFormula
import at.asitplus.wallet.lib.logic.toPropositionalFormulaOverInputDescriptors

class PresentationBuilder(
    val presentationPreparationHelper: PresentationPreparationHelper,
) {
    private var currentSelection: Set<HolderAgent.CandidateInputMatchContainer> = setOf()

    val propositionalFormulaOverInputDescriptors: PropositionalFormula<String>
        get() = presentationPreparationHelper.submissionRequirements?.let { requirements ->
            PropositionalFormula.And(
                requirements.map { requirement ->
                    requirement.toPropositionalFormulaOverInputDescriptors(
                        presentationPreparationHelper.inputDescriptorGroups
                    )
                }
            )
        } ?: PropositionalFormula.And(
            presentationPreparationHelper.inputDescriptorMatches.keys.map { it.id }.map {
                PropositionalFormula.Atom(it)
            }
        )

    init {
        // check formula for satisfiability
    }

    // val satSolver

//    fun findOptions(): Collection<HolderAgent.CandidateInputMatchContainer> {
//        val allRemainingOptions = presentationPreparationHelper.inputDescriptorMatches.flatMap {
//            val isInputDescriptorAlreadySelected = it.value.any { currentSelection.contains(it) }
//            if (isInputDescriptorAlreadySelected) listOf() else it.value
//        }
//        // only yield those options, that do not result in an unsatisfiable state
//        // TODO: find a way to only show options that actually go towards satisfying the submission requirements
//
//    }
}