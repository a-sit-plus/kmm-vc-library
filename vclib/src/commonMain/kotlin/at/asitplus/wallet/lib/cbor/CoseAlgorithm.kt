package at.asitplus.wallet.lib.cbor

import at.asitplus.wallet.lib.jws.JwsAlgorithm
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CoseAlgorithmSerializer::class)
enum class CoseAlgorithm(val value: Int) {

    ES256(-7),
    ES384(-35),
    ES512(-36),
    HMAC256_256(5);

    fun toJwsAlgorithm() = when(this) {
        ES256 -> JwsAlgorithm.ES256
        ES384 -> JwsAlgorithm.ES384
        ES512 -> JwsAlgorithm.ES512
        HMAC256_256 -> JwsAlgorithm.HMAC256
    }

    val signatureValueLength
        get() = when (this) {
            ES256 -> 256 / 8
            ES384 -> 384 / 8
            ES512 -> 512 / 8
            else -> throw IllegalArgumentException(this.toString())
        }
}


object CoseAlgorithmSerializer : KSerializer<CoseAlgorithm> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CoseAlgorithmSerializer", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: CoseAlgorithm) {
        value.let { encoder.encodeInt(it.value) }
    }

    override fun deserialize(decoder: Decoder): CoseAlgorithm {
        val decoded = decoder.decodeInt()
        return CoseAlgorithm.values().first { it.value == decoded }
    }

}