package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.cbor.CoseAlgorithm
import at.asitplus.wallet.lib.cbor.CoseKey
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JweAlgorithm
import at.asitplus.wallet.lib.jws.JweEncryption
import at.asitplus.wallet.lib.jws.JwsAlgorithm

interface CryptoService {

    suspend fun sign(input: ByteArray): KmmResult<ByteArray>

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

    fun generateEphemeralKeyPair(ecCurve: EcCurve): KmmResult<EphemeralKeyHolder>

    fun performKeyAgreement(
        ephemeralKey: EphemeralKeyHolder,
        recipientKey: JsonWebKey,
        algorithm: JweAlgorithm
    ): KmmResult<ByteArray>

    fun performKeyAgreement(ephemeralKey: JsonWebKey, algorithm: JweAlgorithm): KmmResult<ByteArray>

    fun messageDigest(input: ByteArray, digest: Digest): KmmResult<ByteArray>

    val identifier: String
        get() = toJsonWebKey().identifier

    val jwsAlgorithm: JwsAlgorithm

    val coseAlgorithm: CoseAlgorithm

    /**
     * May be used in [at.asitplus.wallet.lib.cbor.CoseService] to transport the signing key for a COSE structure
     */
    val certificate: ByteArray

    fun toJsonWebKey(): JsonWebKey

    fun toCoseKey(): CoseKey

}

interface VerifierCryptoService {

    fun verify(
        input: ByteArray,
        signature: ByteArray,
        algorithm: JwsAlgorithm,
        publicKey: JsonWebKey
    ): KmmResult<Boolean>

    fun verify(
        input: ByteArray,
        signature: ByteArray,
        algorithm: CoseAlgorithm,
        publicKey: CoseKey
    ): KmmResult<Boolean>

}

expect object CryptoUtils {
    fun extractPublicKeyFromX509Cert(it: ByteArray): JsonWebKey?
    fun extractCoseKeyFromX509Cert(it: ByteArray): CoseKey?
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
    fun toPublicJsonWebKey(): JsonWebKey
}

expect class DefaultCryptoService() : CryptoService

expect class DefaultVerifierCryptoService() : VerifierCryptoService
