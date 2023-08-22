package at.asitplus.wallet.lib.cbor

import at.asitplus.wallet.lib.jws.EcCurve
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CoseEllipticCurveSerializer::class)
enum class CoseEllipticCurve(val value: Int) {

    P256(1),
    P384(2),
    P521(3);

    fun toJwkCurve() = when (this) {
        P256 -> EcCurve.SECP_256_R_1
        P384 -> EcCurve.SECP_384_R_1
        P521 -> EcCurve.SECP_521_R_1
    }
    //X25519(4),
    //X448(5),
    //Ed25519(6),
    //Ed448(7);

    val keyLengthBits
        get() = when (this) {
            P256 -> 256
            P384 -> 384
            P521 -> 521
        }

    val coordinateLengthBytes
        get() = when (this) {
            P256 -> 256 / 8
            P384 -> 384 / 8
            P521 -> 521 / 8
        }

    val signatureLengthBytes
        get() = when (this) {
            P256 -> 256 / 8
            P384 -> 384 / 8
            P521 -> 521 / 8
        }

}

object CoseEllipticCurveSerializer : KSerializer<CoseEllipticCurve?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CoseEllipticCurveSerializer", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: CoseEllipticCurve?) {
        value?.let { encoder.encodeInt(it.value) }
    }

    override fun deserialize(decoder: Decoder): CoseEllipticCurve? {
        val decoded = decoder.decodeInt()
        return CoseEllipticCurve.values().firstOrNull { it.value == decoded }
    }
}