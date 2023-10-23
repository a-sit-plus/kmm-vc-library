package at.asitplus.wallet.lib.aries

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.CryptoPublicKey
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.AtomicAttribute2023
import at.asitplus.wallet.lib.data.ConstantIndex
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

class DummyCredentialDataProvider(
    private val clock: Clock = Clock.System,
) : IssuerCredentialDataProvider {

    private val defaultLifetime = 1.minutes

    override fun getCredentialWithType(
        subjectId: String,
        subjectPublicKey: CryptoPublicKey?,
        attributeTypes: Collection<String>
    ): KmmResult<List<CredentialToBeIssued>> {
        val attributeType = ConstantIndex.AtomicAttribute2023.vcType
        if (!attributeTypes.contains(attributeType)) {
            return KmmResult.failure(UnsupportedOperationException("no data"))
        }
        val expiration = clock.now() + defaultLifetime
        return KmmResult.success(
            listOf(
                CredentialToBeIssued.Vc(
                    subject = AtomicAttribute2023(subjectId, "given-name", "Susanne"),
                    expiration = expiration,
                    scheme = ConstantIndex.AtomicAttribute2023,
                ),
                CredentialToBeIssued.Vc(
                    subject = AtomicAttribute2023(subjectId, "family-name", "Meier"),
                    expiration = expiration,
                    scheme = ConstantIndex.AtomicAttribute2023,
                ),
                CredentialToBeIssued.Vc(
                    subject = AtomicAttribute2023(subjectId, "date-of-birth", "1990-01-01"),
                    expiration = expiration,
                    scheme = ConstantIndex.AtomicAttribute2023,
                ),
                CredentialToBeIssued.Vc(
                    subject = AtomicAttribute2023(subjectId, "identifier", randomValue()),
                    expiration = expiration,
                    scheme = ConstantIndex.AtomicAttribute2023,
                ),
                CredentialToBeIssued.Vc(
                    subject = AtomicAttribute2023(subjectId, "picture", randomValue()),
                    expiration = expiration,
                    scheme = ConstantIndex.AtomicAttribute2023,
                    attachments = listOf(Issuer.Attachment("picture", "image/webp", byteArrayOf(32)))
                )
            )
        )
    }

    private fun randomValue() = Random.nextBytes(32).encodeToString(Base16(strict = true))

}

