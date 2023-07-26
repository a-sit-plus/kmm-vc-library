package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.iso.IssuerSignedList
import at.asitplus.wallet.lib.iso.MobileSecurityObject
import kotlinx.datetime.Instant

/**
 * Provides data for credentials to be issued.
 */
fun interface IssuerCredentialDataProvider {

    /**
     * Gets called with a list of credential types, i.e. some of
     * [at.asitplus.wallet.lib.data.ConstantIndex.CredentialScheme.vcType]
     */
    fun getCredentialWithType(
        subjectId: String,
        attributeTypes: Collection<String>
    ): KmmResult<List<CredentialToBeIssued>>

}

sealed class CredentialToBeIssued {
    data class Vc(
        val subject: CredentialSubject,
        val expiration: Instant,
        val attributeType: String,
        val attachments: List<Issuer.Attachment>? = null
    ) : CredentialToBeIssued()

    data class Iso(
        val issuerSigned: IssuerSignedList,
        val mso: MobileSecurityObject
    ) : CredentialToBeIssued()
}
