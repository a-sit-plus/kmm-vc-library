package at.asitplus.wallet.lib.combinatorics

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.sequences.shouldHaveSize

class CombinationsTest : FreeSpec ({
    "given a total of 0 elements" - {
        val total = 0
        "when selecting 0 elements" - {
            val select = 0
            val combinations = Combinations(select = select, total = total)

            "then should have 1 solution" - {
                combinations.iterator().asSequence().shouldHaveSize(1)
            }
        }
        "when selecting 1 elements" - {
            val select = 1
            val combinations = Combinations(select = select, total = total)

            "then should have 0 solutions" - {
                combinations.iterator().asSequence().shouldHaveSize(0)
            }
        }
    }
    "given a total of 1 elements" - {
        val total = 1
        "when selecting 0 elements" - {
            val select = 0
            val combinations = Combinations(select = select, total = total)

            "then should have 1 solution" - {
                combinations.iterator().asSequence().shouldHaveSize(1)
            }
        }
        "when selecting 1 elements" - {
            val select = 1
            val combinations = Combinations(select = select, total = total)

            "then should have 1 solutions" - {
                combinations.iterator().asSequence().shouldHaveSize(1)
            }
        }
        "when selecting 2 elements" - {
            val select = 2
            val combinations = Combinations(select = select, total = total)

            "then should have 0 solutions" - {
                combinations.iterator().asSequence().shouldHaveSize(0)
            }
        }
    }
    "given a total of 2 elements" - {
        val total = 2
        "when selecting 0 elements" - {
            val select = 0
            val combinations = Combinations(select = select, total = total)

            "then should have 1 solution" - {
                combinations.iterator().asSequence().shouldHaveSize(1)
            }
        }
        "when selecting 1 elements" - {
            val select = 1
            val combinations = Combinations(select = select, total = total)

            "then should have 2 solutions" - {
                combinations.iterator().asSequence().shouldHaveSize(2)
            }
        }
        "when selecting 2 elements" - {
            val select = 2
            val combinations = Combinations(select = select, total = total)

            "then should have 1 solutions" - {
                combinations.iterator().asSequence().shouldHaveSize(1)
            }
        }
        "when selecting 3 elements" - {
            val select = 3
            val combinations = Combinations(select = select, total = total)

            "then should have 0 solutions" - {
                combinations.iterator().asSequence().shouldHaveSize(0)
            }
        }
    }
})