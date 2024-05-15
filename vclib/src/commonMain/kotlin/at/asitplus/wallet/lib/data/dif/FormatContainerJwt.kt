package at.asitplus.wallet.lib.data.dif

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class for
 * [DIF Presentation Exchange v1.0.0](https://identity.foundation/presentation-exchange/spec/v1.0.0/#presentation-definition)
 */
@Serializable
data class FormatContainerJwt(
    // TODO make this a collection of future Json Web Algorithms?
    @SerialName("alg")
    val algorithms: Collection<String>? = null,
)