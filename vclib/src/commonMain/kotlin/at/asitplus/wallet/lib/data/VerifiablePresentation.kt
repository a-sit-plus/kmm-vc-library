package at.asitplus.wallet.lib.data

import com.benasher44.uuid.uuid4
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Verifiable Presentation (see [W3C VC Data Model](https://w3c.github.io/vc-data-model/)), containing one or more [VerifiableCredential]s.
 */
@Serializable
data class VerifiablePresentation(
    @SerialName(VerifiablePresentationConstants.SerialNames.id)
    val id: String,
    @SerialName(VerifiablePresentationConstants.SerialNames.type)
    val type: String,
    @SerialName(VerifiablePresentationConstants.SerialNames.verifiableCredential)
    val verifiableCredential: Collection<String>,
) {

    constructor(verifiableCredential: Collection<String>) : this(
        id = "urn:uuid:${uuid4()}",
        type = "VerifiablePresentation",
        verifiableCredential = verifiableCredential
    )

    fun toJws(challenge: String, issuerId: String, audienceId: String) = VerifiablePresentationJws(
        vp = this,
        challenge = challenge,
        issuer = issuerId,
        audience = audienceId,
        jwtId = id
    )
}

class VerifiablePresentationConstants {
    object SerialNames {
        const val id = "id"
        const val type = "type"
        const val verifiableCredential = "verifiableCredential"
    }
}