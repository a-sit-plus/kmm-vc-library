package at.asitplus.wallet.lib.agent

import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.jws.DefaultVerifierJwsService
import at.asitplus.wallet.lib.jws.JsonWebKey
import at.asitplus.wallet.lib.jws.JweAlgorithm
import at.asitplus.wallet.lib.jws.JweEncrypted
import at.asitplus.wallet.lib.jws.JweEncryption
import at.asitplus.wallet.lib.jws.JwsContentType
import at.asitplus.wallet.lib.jws.JwsService
import at.asitplus.wallet.lib.jws.JwsSigned
import at.asitplus.wallet.lib.jws.VerifierJwsService
import at.asitplus.wallet.lib.msg.JsonWebMessage
import io.github.aakira.napier.Napier


class MessageWrapper(
    private val cryptoService: CryptoService,
    private val jwsService: JwsService = DefaultJwsService(cryptoService),
    private val verifierJwsService: VerifierJwsService = DefaultVerifierJwsService(),
) {

    suspend fun parseMessage(it: String): ReceivedMessage {
        val jwsSigned = JwsSigned.parse(it)
        if (jwsSigned != null) {
            return parseJwsMessage(jwsSigned, it)
        }
        val jweEncrypted = JweEncrypted.parse(it)
        if (jweEncrypted != null)
            return parseJweMessage(jweEncrypted, it)
        return ReceivedMessage.Error
            .also { Napier.w("Could not parse message: $it") }
    }

    private suspend fun parseJweMessage(
        jweObject: JweEncrypted,
        serialized: String
    ): ReceivedMessage {
        Napier.d("Parsing JWE ${jweObject.serialize()}")
        val joseObject = jwsService.decryptJweObject(jweObject, serialized)
            ?: return ReceivedMessage.Error
                .also { Napier.w("Could not parse JWE") }
        val payloadString = joseObject.payload.decodeToString()
        if (joseObject.header.contentType == JwsContentType.DIDCOMM_SIGNED_JSON) {
            val parsed = JwsSigned.parse(payloadString)
                ?: return ReceivedMessage.Error
                    .also { Napier.w("Could not parse inner JWS") }
            return parseJwsMessage(parsed, payloadString)
        }
        if (joseObject.header.contentType == JwsContentType.DIDCOMM_PLAIN_JSON) {
            val message = JsonWebMessage.deserialize(payloadString)
                ?: return ReceivedMessage.Error
                    .also { Napier.w("Could not parse plain message") }
            return ReceivedMessage.Success(message, joseObject.header.keyId)
        }
        return ReceivedMessage.Error
            .also { Napier.w("ContentType not matching") }
    }

    private fun parseJwsMessage(joseObject: JwsSigned, serialized: String): ReceivedMessage {
        Napier.d("Parsing JWS ${joseObject.serialize()}")
        if (!verifierJwsService.verifyJwsObject(joseObject, serialized))
            return ReceivedMessage.Error
                .also { Napier.w("Signature invalid") }
        if (joseObject.header.contentType == JwsContentType.DIDCOMM_PLAIN_JSON) {
            val payloadString = joseObject.payload.decodeToString()
            val message = JsonWebMessage.deserialize(payloadString)
                ?: return ReceivedMessage.Error
                    .also { Napier.w("Could not parse plain message") }
            return ReceivedMessage.Success(message, joseObject.header.keyId)
        }
        return ReceivedMessage.Error
            .also { Napier.w("ContentType not matching") }
    }

    fun createEncryptedJwe(jwm: JsonWebMessage, recipientKeyId: String): String? {
        val jwePayload = jwm.serialize().encodeToByteArray()
        val recipientKey = JsonWebKey.fromKeyId(recipientKeyId)
            ?: return null
                .also { Napier.w("Can not calc JWK from recipientKeyId: $recipientKeyId") }
        return jwsService.encryptJweObject(
            JwsContentType.DIDCOMM_ENCRYPTED_JSON,
            jwePayload,
            recipientKey,
            JwsContentType.DIDCOMM_PLAIN_JSON,
            JweAlgorithm.ECDH_ES,
            JweEncryption.A256GCM,
        )
    }

    suspend fun createSignedAndEncryptedJwe(jwm: JsonWebMessage, recipientKeyId: String): String? {
        val jwePayload = createSignedJwt(jwm)?.encodeToByteArray()
            ?: return null
                .also { Napier.w("Can not create signed JWT for encryption") }
        val recipientKey = JsonWebKey.fromKeyId(recipientKeyId)
            ?: return null
                .also { Napier.w("Can not calc JWK from recipientKeyId: $recipientKeyId") }
        return jwsService.encryptJweObject(
            JwsContentType.DIDCOMM_ENCRYPTED_JSON,
            jwePayload,
            recipientKey,
            JwsContentType.DIDCOMM_SIGNED_JSON,
            JweAlgorithm.ECDH_ES,
            JweEncryption.A256GCM,
        )
    }

    suspend fun createSignedJwt(jwm: JsonWebMessage): String? {
        return jwsService.createSignedJwt(
            JwsContentType.DIDCOMM_SIGNED_JSON,
            jwm.serialize().encodeToByteArray(),
            JwsContentType.DIDCOMM_PLAIN_JSON
        )
    }

}
