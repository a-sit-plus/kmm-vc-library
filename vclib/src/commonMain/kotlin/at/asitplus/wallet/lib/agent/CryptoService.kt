@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult
import at.asitplus.crypto.datatypes.*
import at.asitplus.crypto.datatypes.cose.CoseKey
import at.asitplus.crypto.datatypes.jws.JsonWebKey
import at.asitplus.crypto.datatypes.jws.JweAlgorithm
import at.asitplus.crypto.datatypes.jws.JweEncryption
import at.asitplus.crypto.datatypes.pki.X509Certificate
import at.asitplus.crypto.datatypes.pki.X509CertificateExtension

interface CryptoService {

    suspend fun sign(input: ByteArray): KmmResult<CryptoSignature.RawByteEncodable> =
        doSign(input).map {
            when (it) {
                is CryptoSignature.RawByteEncodable -> it
                is CryptoSignature.NotRawByteEncodable -> when (it) {
                    is CryptoSignature.EC.IndefiniteLength -> it.withCurve((publicKey as CryptoPublicKey.EC).curve)
                }
            }
    }

    suspend fun doSign(input: ByteArray): KmmResult<CryptoSignature>

    fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        algorithm: JweEncryption
    ): KmmResult<AuthenticatedCiphertext>

    suspend fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        authTag: ByteArray,
        algorithm: JweEncryption
    ): KmmResult<ByteArray>

    fun generateEphemeralKeyPair(ecCurve: ECCurve): KmmResult<EphemeralKeyHolder>

    fun performKeyAgreement(
        ephemeralKey: EphemeralKeyHolder,
        recipientKey: JsonWebKey,
        algorithm: JweAlgorithm
    ): KmmResult<ByteArray>

    fun performKeyAgreement(ephemeralKey: JsonWebKey, algorithm: JweAlgorithm): KmmResult<ByteArray>

    fun messageDigest(input: ByteArray, digest: Digest): KmmResult<ByteArray>

    val algorithm: CryptoAlgorithm

    val publicKey: CryptoPublicKey

    val jsonWebKey: JsonWebKey

    val coseKey: CoseKey

    /**
     * May be used in [at.asitplus.wallet.lib.cbor.CoseService] to transport the signing key for a COSE structure.
     * a `null` value signifies that raw public keys are used and no certificate is present
     */
    val certificate: X509Certificate?

}

interface VerifierCryptoService {

    /**
     * List of algorithms, for which signatures can be verified in [verify].
     */
    val supportedAlgorithms: List<CryptoAlgorithm>

    fun verify(
        input: ByteArray,
        signature: CryptoSignature,
        algorithm: CryptoAlgorithm,
        publicKey: CryptoPublicKey,
    ): KmmResult<Boolean>

}


data class AuthenticatedCiphertext(val ciphertext: ByteArray, val authtag: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AuthenticatedCiphertext

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!authtag.contentEquals(other.authtag)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + authtag.contentHashCode()
        return result
    }
}

interface EphemeralKeyHolder {
    val publicJsonWebKey: JsonWebKey?
}

expect class DefaultCryptoService() : CryptoService {
    override suspend fun doSign(input: ByteArray): KmmResult<CryptoSignature>
    override fun encrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        algorithm: JweEncryption
    ): KmmResult<AuthenticatedCiphertext>

    override suspend fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        aad: ByteArray,
        input: ByteArray,
        authTag: ByteArray,
        algorithm: JweEncryption
    ): KmmResult<ByteArray>

    override fun generateEphemeralKeyPair(ecCurve: ECCurve): KmmResult<EphemeralKeyHolder>
    override fun performKeyAgreement(
        ephemeralKey: EphemeralKeyHolder,
        recipientKey: JsonWebKey,
        algorithm: JweAlgorithm
    ): KmmResult<ByteArray>

    override fun performKeyAgreement(
        ephemeralKey: JsonWebKey,
        algorithm: JweAlgorithm
    ): KmmResult<ByteArray>

    override fun messageDigest(
        input: ByteArray,
        digest: Digest
    ): KmmResult<ByteArray>

    override val algorithm: CryptoAlgorithm
    override val publicKey: CryptoPublicKey
    override val jsonWebKey: JsonWebKey
    override val coseKey: CoseKey
    override val certificate: X509Certificate?

    companion object {
        fun withSelfSignedCert(
            extensions: List<X509CertificateExtension> = listOf()
        ): CryptoService
    }
}

expect class DefaultVerifierCryptoService() : VerifierCryptoService {
    override val supportedAlgorithms: List<CryptoAlgorithm>
    override fun verify(
        input: ByteArray,
        signature: CryptoSignature,
        algorithm: CryptoAlgorithm,
        publicKey: CryptoPublicKey
    ): KmmResult<Boolean>
}