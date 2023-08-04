@file:UseSerializers(ByteArrayBase64Serializer::class)

package at.asitplus.wallet.lib.jws

import at.asitplus.wallet.lib.agent.CryptoUtils
import at.asitplus.wallet.lib.data.InstantLongSerializer
import at.asitplus.wallet.lib.data.jsonSerializer
import io.github.aakira.napier.Napier
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Header of a [JwsSigned].
 */
@Serializable
data class JwsHeader(
    @SerialName("alg")
    val algorithm: JwsAlgorithm,
    @SerialName("kid")
    val keyId: String? = null,
    @SerialName("typ")
    val type: String? = null,
    @SerialName("cty")
    val contentType: String? = null,
    @SerialName("x5c")
    val certificateChain: Array<ByteArray>? = null,
    @SerialName("nbf")
    @Serializable(with = InstantLongSerializer::class)
    val notBefore: Instant? = null,
    @SerialName("iat")
    @Serializable(with = InstantLongSerializer::class)
    val issuedAt: Instant? = null,
    @SerialName("exp")
    @Serializable(with = InstantLongSerializer::class)
    val expiration: Instant? = null,
    @SerialName("jwk")
    val jsonWebKey: JsonWebKey? = null
) {

    fun serialize() = jsonSerializer.encodeToString(this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as JwsHeader

        if (algorithm != other.algorithm) return false
        if (keyId != other.keyId) return false
        if (type != other.type) return false
        if (contentType != other.contentType) return false
        if (certificateChain != null) {
            if (other.certificateChain == null) return false
            if (!certificateChain.contentDeepEquals(other.certificateChain)) return false
        } else if (other.certificateChain != null) return false
        if (notBefore != other.notBefore) return false
        if (issuedAt != other.issuedAt) return false
        if (expiration != other.expiration) return false
        return jsonWebKey == other.jsonWebKey
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + (keyId?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (certificateChain?.contentDeepHashCode() ?: 0)
        result = 31 * result + (notBefore?.hashCode() ?: 0)
        result = 31 * result + (issuedAt?.hashCode() ?: 0)
        result = 31 * result + (expiration?.hashCode() ?: 0)
        result = 31 * result + (jsonWebKey?.hashCode() ?: 0)
        return result
    }

    val publicKey: JsonWebKey? by lazy {
        jsonWebKey
            ?: keyId?.let { JsonWebKey.fromKeyId(it) }
            ?: certificateChain?.firstOrNull()?.let { CryptoUtils.extractPublicKeyFromX509Cert(it)?.toJsonWebKey() }
    }

    companion object {
        fun deserialize(it: String) = kotlin.runCatching {
            jsonSerializer.decodeFromString<JwsHeader>(it)
        }.getOrElse {
            Napier.w("deserialize failed", it)
            null
        }


    }
}