package at.asitplus.wallet.lib.msg

import at.asitplus.KmmResult.Companion.wrap
import at.asitplus.signum.indispensable.io.Base64Strict
import at.asitplus.wallet.lib.aries.jsonSerializer
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * From [DIDComm Messaging](https://identity.foundation/didcomm-messaging/spec/)
 */
@Serializable
data class JwmAttachment(
    @SerialName("id")
    val id: String,
    @SerialName("media_type")
    val mediaType: String? = null,
    @SerialName("data")
    val data: JwmAttachmentData,
    @SerialName("filename")
    val filename: String? = null,
    @SerialName("parent")
    val parent: String? = null,
) {
    fun serialize() = jsonSerializer.encodeToString(this)

    fun decodeString(): String? {
        if (data.base64 != null)
            return data.base64.decodeToByteArrayOrNull(Base64Strict)?.decodeToString()
        if (data.jws != null)
            return data.jws
        return null
            .also { Napier.w("Could not decode JWM attachment") }
    }

    fun decodeBinary(): ByteArray? {
        if (data.base64 != null)
            return data.base64.decodeToByteArrayOrNull(Base64Strict)
        return null
            .also { Napier.w("Could not binary decode JWM attachment") }
    }

    companion object {

        fun deserialize(it: String) = kotlin.runCatching {
            jsonSerializer.decodeFromString<JwmAttachment>(it)
        }.wrap()

        fun encodeBase64(data: String) = JwmAttachment(
            id = uuid4().toString(),
            mediaType = "application/base64",
            data = JwmAttachmentData(
                base64 = data.encodeToByteArray().encodeToString(Base64Strict)
            )
        )

        fun encodeBase64(data: ByteArray) = JwmAttachment(
            id = uuid4().toString(),
            mediaType = "application/base64",
            data = JwmAttachmentData(
                base64 = data.encodeToString(Base64Strict)
            )
        )

        fun encode(data: ByteArray, filename: String, mediaType: String, parent: String) = JwmAttachment(
            id = uuid4().toString(),
            mediaType = mediaType,
            filename = filename,
            parent = parent,
            data = JwmAttachmentData(
                base64 = data.encodeToString(Base64Strict)
            )
        )

        fun encodeJws(data: String) = JwmAttachment(
            id = uuid4().toString(),
            mediaType = "application/jws",
            data = JwmAttachmentData(
                jws = data
            )
        )
    }
}