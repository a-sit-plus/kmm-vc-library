package at.asitplus.wallet.lib.agent

import at.asitplus.crypto.datatypes.jws.JwsSigned
import at.asitplus.wallet.lib.data.IsoDocumentParsed
import at.asitplus.wallet.lib.data.KeyBindingJws
import at.asitplus.wallet.lib.data.SelectiveDisclosureItem
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.data.VerifiablePresentationParsed
import at.asitplus.wallet.lib.iso.IssuerSigned


/**
 * Summarizes operations for a Verifier in the sense of the [W3C VC Data Model](https://w3c.github.io/vc-data-model/).
 *
 * It can verify credentials and presentations.
 */
interface Verifier {

    /**
     * The identifier for this agent, typically the `keyId` from the cryptographic key,
     * e.g. `did:key:mAB...` or `urn:ietf:params:oauth:jwk-thumbprint:sha256:...`
     */
    val identifier: String

    /**
     * Set the revocation list to use for validating VCs (from [Issuer.issueRevocationListCredential])
     */
    fun setRevocationList(it: String): Boolean

    /**
     * Verifies a presentation of some credentials that a holder issued with that [challenge] we sent before.
     */
    fun verifyPresentation(it: String, challenge: String): VerifyPresentationResult

    /**
     * Verifies if a presentation contains all required [attributeNames].
     */
    fun verifyPresentationContainsAttributes(it: VerifiablePresentationParsed, attributeNames: List<String>): Boolean

    /**
     * Parse a single VC, checks if subject matches
     */
    fun verifyVcJws(it: String): VerifyCredentialResult

    sealed class VerifyPresentationResult {
        data class Success(val vp: VerifiablePresentationParsed) : VerifyPresentationResult()
        data class SuccessSdJwt(
            val jwsSigned: JwsSigned,
            val sdJwt: VerifiableCredentialSdJwt,
            val disclosures: List<SelectiveDisclosureItem>,
            val isRevoked: Boolean
        ) : VerifyPresentationResult()

        data class SuccessIso(val document: IsoDocumentParsed) : VerifyPresentationResult()
        data class InvalidStructure(val input: String) : VerifyPresentationResult()
        data class NotVerified(val input: String, val challenge: String) : VerifyPresentationResult()
    }

    sealed class VerifyCredentialResult {
        data class SuccessJwt(val jws: VerifiableCredentialJws) : VerifyCredentialResult()
        data class SuccessSdJwt(
            /**
             * Extracted JWS from the input (containing also the disclosures)
             */
            val jwsSigned: JwsSigned,
            val sdJwt: VerifiableCredentialSdJwt,
            val keyBindingJws: JwsSigned?,
            /**
             * Map of original serialized disclosure item to parsed item
             */
            val disclosures: Map<String, SelectiveDisclosureItem?>,
            val isRevoked: Boolean,
        ) : VerifyCredentialResult()

        data class SuccessIso(val issuerSigned: IssuerSigned) : VerifyCredentialResult()
        data class Revoked(val input: String, val jws: VerifiableCredentialJws) : VerifyCredentialResult()
        data class InvalidStructure(val input: String) : VerifyCredentialResult()
    }

}
