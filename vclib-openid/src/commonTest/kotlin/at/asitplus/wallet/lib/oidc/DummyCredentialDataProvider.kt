package at.asitplus.wallet.lib.oidc

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.CryptoPublicKey
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.AtomicAttribute2023
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.iso.DrivingPrivilege
import at.asitplus.wallet.lib.iso.ElementValue
import at.asitplus.wallet.lib.iso.IsoDataModelConstants.DataElements
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
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
        val expiration = clock.now() + defaultLifetime
        val listOfAttributes = mutableListOf<CredentialToBeIssued>()
        if (attributeTypes.contains(attributeType)) {
            listOfAttributes.addAll(
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
        if (attributeTypes.contains(ConstantIndex.MobileDrivingLicence2023.vcType) && subjectPublicKey != null) {
            val drivingPrivilege = DrivingPrivilege(
                vehicleCategoryCode = "B",
                issueDate = LocalDate.parse("2023-01-01"),
                expiryDate = LocalDate.parse("2033-01-31"),
                //codes = arrayOf(DrivingPrivilegeCode(code = "B"))
            )
            val issuerSignedItems = listOf(
                buildIssuerSignedItem(DataElements.FAMILY_NAME, "Mustermann", 0U),
                buildIssuerSignedItem(DataElements.GIVEN_NAME, "Max", 1U),
                buildIssuerSignedItem(DataElements.DOCUMENT_NUMBER, "123456789", 2U),
                buildIssuerSignedItem(DataElements.ISSUE_DATE, "2023-01-01", 3U),
                buildIssuerSignedItem(DataElements.EXPIRY_DATE, "2033-01-31", 4U),
                //buildIssuerSignedItem(DataElements.DRIVING_PRIVILEGES, drivingPrivilege, 5U),
            )

            listOfAttributes.add(
                CredentialToBeIssued.Iso(
                    issuerSignedItems = issuerSignedItems,
                    subjectPublicKey = subjectPublicKey.toCoseKey(),
                    expiration = expiration,
                    scheme = ConstantIndex.MobileDrivingLicence2023,
                )
            )
        }
        return KmmResult.success(listOfAttributes)
    }

    private fun randomValue() = Random.nextBytes(32).encodeToString(Base16(strict = true))

    fun buildIssuerSignedItem(elementIdentifier: String, elementValue: String, digestId: UInt) = IssuerSignedItem(
        digestId = digestId,
        random = Random.nextBytes(16),
        elementIdentifier = elementIdentifier,
        elementValue = ElementValue(string = elementValue)
    )

    fun buildIssuerSignedItem(elementIdentifier: String, elementValue: DrivingPrivilege, digestId: UInt) =
        IssuerSignedItem(
            digestId = digestId,
            random = Random.nextBytes(16),
            elementIdentifier = elementIdentifier,
            elementValue = ElementValue(drivingPrivilege = arrayOf(elementValue))
        )

}
