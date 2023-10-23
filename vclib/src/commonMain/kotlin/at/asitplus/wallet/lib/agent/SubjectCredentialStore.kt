package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.SelectiveDisclosureItem
import at.asitplus.wallet.lib.data.VerifiableCredential
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.iso.IssuerSigned

/**
 * Stores all credentials that a subject has received
 */
interface SubjectCredentialStore {

    /**
     * Implementations should store the passed credential in a secure way.
     * Passed credentials have been validated before.
     *
     * @param vc Instance of [VerifiableCredentialJws]
     * @param vcSerialized Serialized form of [VerifiableCredential]
     */
    suspend fun storeCredential(
        vc: VerifiableCredentialJws,
        vcSerialized: String,
        scheme: ConstantIndex.CredentialScheme,
    )

    /**
     * Implementations should store the passed credential in a secure way.
     * Passed credentials have been validated before.
     *
     * @param vc Instance of [VerifiableCredentialSdJwt]
     * @param vcSerialized Serialized form of [VerifiableCredential]
     */
    suspend fun storeCredential(
        vc: VerifiableCredentialSdJwt,
        vcSerialized: String,
        disclosures: Map<String, SelectiveDisclosureItem?>,
        scheme: ConstantIndex.CredentialScheme,
    )

    /**
     * Implementations should store the passed credential in a secure way.
     * Passed credentials have been validated before.
     *
     * @param issuerSigned Instance of [IssuerSigned] (an ISO credential)
     */
    suspend fun storeCredential(
        issuerSigned: IssuerSigned,
        scheme: ConstantIndex.CredentialScheme,
    )

    /**
     * Implementation should store the attachment in a secure way.
     * Note that the data has not been validated since it may not be signed.
     *
     * @param name Name of the Attachment
     * @param data Data of the Attachment (a binary blob)
     * @param vcId ID of the VC to this Attachment (see [VerifiableCredential.id])
     */
    suspend fun storeAttachment(name: String, data: ByteArray, vcId: String)

    /**
     * Return all stored credentials.
     * Selective Disclosure: Specify list of attribute types in [requiredAttributeTypes].
     */
    suspend fun getCredentials(requiredAttributeTypes: Collection<String>? = null): KmmResult<List<StoreEntry>>

    /**
     * Return attachments filtered by [name]
     */
    suspend fun getAttachment(name: String): KmmResult<ByteArray>

    /**
     * Return attachments filtered by [name] and [vcId]
     */
    suspend fun getAttachment(name: String, vcId: String): KmmResult<ByteArray>

    sealed class StoreEntry {
        data class Vc(
            val vcSerialized: String,
            val vc: VerifiableCredentialJws,
            val scheme: ConstantIndex.CredentialScheme
        ) : StoreEntry()

        data class SdJwt(
            val vcSerialized: String,
            val sdJwt: VerifiableCredentialSdJwt,
            /**
             * Map of original serialized disclosure item to parsed item
             */
            val disclosures: Map<String, SelectiveDisclosureItem?>,
            val scheme: ConstantIndex.CredentialScheme
        ) : StoreEntry()

        data class Iso(
            val issuerSigned: IssuerSigned,
            val scheme: ConstantIndex.CredentialScheme
        ) : StoreEntry()
    }

    data class AttachmentEntry(val name: String, val data: ByteArray, val vcId: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as AttachmentEntry

            if (name != other.name) return false
            if (!data.contentEquals(other.data)) return false
            if (vcId != other.vcId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + vcId.hashCode()
            return result
        }
    }

}
