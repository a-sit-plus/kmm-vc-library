package at.asitplus.wallet.lib.aries

import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.Holder
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.data.AtomicAttributeCredential
import at.asitplus.wallet.lib.data.ConstantIndex
import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class PresentProofMessengerTest : FreeSpec() {

    private lateinit var holderCryptoService: CryptoService
    private lateinit var verifierCryptoService: CryptoService
    private lateinit var issuerCryptoService: CryptoService
    private lateinit var holder: Holder
    private lateinit var verifier: Verifier
    private lateinit var issuer: Issuer
    private lateinit var verifierChallenge: String
    private lateinit var holderServiceEndpoint: String
    private var attributeLifetime: Duration = 5.toDuration(DurationUnit.SECONDS)

    init {

        beforeEach {
            holderCryptoService = DefaultCryptoService()
            verifierCryptoService = DefaultCryptoService()
            issuerCryptoService = DefaultCryptoService()
            holder = HolderAgent.newDefaultInstance(holderCryptoService)
            verifier = VerifierAgent.newDefaultInstance(verifierCryptoService.identifier)
            issuer = IssuerAgent.newDefaultInstance(issuerCryptoService)
            verifierChallenge = uuid4().toString()
            holderServiceEndpoint = "https://example.com/present-proof?${uuid4()}"
        }

        "presentProof" {
            val credentialSubject = randomCredential(holderCryptoService.identifier)
            holder.storeCredentials(issuer.issueCredential(credentialSubject).toStoreCredentialInput())
            val holderMessenger = PresentProofMessenger.newHolderInstance(
                holder = holder,
                messageWrapper = MessageWrapper(holderCryptoService),
                serviceEndpoint = holderServiceEndpoint
            )
            val verifierMessenger = PresentProofMessenger.newVerifierInstance(
                verifier = verifier,
                messageWrapper = MessageWrapper(verifierCryptoService)
            )

            val oobInvitation = holderMessenger.startCreatingInvitation()
            oobInvitation.shouldBeInstanceOf<NextMessage.Send>()
            val invitationMessage = oobInvitation.message

            val parsedInvitation = verifierMessenger.parseMessage(invitationMessage)
            parsedInvitation.shouldBeInstanceOf<NextMessage.Send>()
            parsedInvitation.endpoint shouldBe holderServiceEndpoint
            val requestPresentation = parsedInvitation.message

            val parsedRequestPresentation = holderMessenger.parseMessage(requestPresentation)
            parsedRequestPresentation.shouldBeInstanceOf<NextMessage.Send>()
            val presentation = parsedRequestPresentation.message

            val parsePresentation = verifierMessenger.parseMessage(presentation)
            parsePresentation.shouldBeInstanceOf<NextMessage.Result<PresentProofProtocolResult>>()
            val vpResult = parsePresentation.result

            vpResult.shouldBeInstanceOf<Verifier.VerifyPresentationResult.Success>()
            vpResult.vp.verifiableCredentials.shouldNotBeEmpty()
        }

        "selectiveDisclosure" {
            val expectedSubject = randomCredential(holder.identifier)
            val attributeName = (expectedSubject.subject as AtomicAttributeCredential).name
            val attributeValue = (expectedSubject.subject as AtomicAttributeCredential).value
            val expectedVc = issuer.issueCredential(expectedSubject)
            holder.storeCredentials(expectedVc.toStoreCredentialInput())

            val holderMessenger = PresentProofMessenger.newHolderInstance(
                holder = holder,
                messageWrapper = MessageWrapper(holderCryptoService),
                serviceEndpoint = "https://example.com"
            )
            val verifierMessenger = PresentProofMessenger.newVerifierInstance(
                verifier = verifier,
                messageWrapper = MessageWrapper(verifierCryptoService),
                challengeForPresentation = verifierChallenge,
                requestedAttributeTypes = listOf(attributeName)
            )

            val oobInvitation = holderMessenger.startCreatingInvitation()
            oobInvitation.shouldBeInstanceOf<NextMessage.Send>()
            val invitationMessage = oobInvitation.message

            val parsedInvitation = verifierMessenger.parseMessage(invitationMessage)
            parsedInvitation.shouldBeInstanceOf<NextMessage.Send>()
            val requestPresentation = parsedInvitation.message

            val parsedRequestPresentation = holderMessenger.parseMessage(requestPresentation)
            parsedRequestPresentation.shouldBeInstanceOf<NextMessage.Send>()
            val presentation = parsedRequestPresentation.message

            val parsePresentation = verifierMessenger.parseMessage(presentation)
            parsePresentation.shouldBeInstanceOf<NextMessage.Result<PresentProofProtocolResult>>()
            val receivedPresentation = parsePresentation.result

            assertPresentation(receivedPresentation, attributeName, attributeValue)
        }

        "selectiveDisclosure_notFulfilled" {
            val expectedSubject = randomCredential(holder.identifier)
            val attributeName = (expectedSubject.subject as AtomicAttributeCredential).name
            val attributeValue = (expectedSubject.subject as AtomicAttributeCredential).value
            val expectedVc = issuer.issueCredential(expectedSubject).toStoreCredentialInput()
            holder.storeCredentials(expectedVc)

            val holderMessenger = PresentProofMessenger.newHolderInstance(
                holder = holder,
                messageWrapper = MessageWrapper(holderCryptoService),
                serviceEndpoint = "https://example.com/",
            )
            var verifierMessenger = PresentProofMessenger.newVerifierInstance(
                verifier = verifier,
                messageWrapper = MessageWrapper(verifierCryptoService),
                challengeForPresentation = verifierChallenge,
                // subject is not expected to provide an attribute with this type
                requestedAttributeTypes = listOf(uuid4().toString()),
                credentialScheme = object : ConstantIndex.CredentialScheme {
                    override val goalCodeIssue: String
                        get() = "issue-vc-random"
                    override val goalCodeRequestProof: String
                        get() = "request-proof-random"
                    override val credentialDefinitionName: String
                        get() = "random"
                    override val schemaUri: String
                        get() = "https://example.com/random"
                    override val vcType: String
                        get() = "random"
                }
            )

            val oobInvitation = holderMessenger.startCreatingInvitation()
            oobInvitation.shouldBeInstanceOf<NextMessage.Send>()
            val invitationMessage = oobInvitation.message

            val parsedInvitation = verifierMessenger.parseMessage(invitationMessage)
            parsedInvitation.shouldBeInstanceOf<NextMessage.Send>()
            val requestPresentation = parsedInvitation.message

            val parsedRequestPresentation = holderMessenger.parseMessage(requestPresentation)
            parsedRequestPresentation.shouldBeInstanceOf<NextMessage.SendProblemReport>()
            val problemReport = parsedRequestPresentation.message

            val parseProblemReport = verifierMessenger.parseMessage(problemReport)
            parseProblemReport.shouldBeInstanceOf<NextMessage.ReceivedProblemReport>()
            val receivedProblemReport = parseProblemReport.message
            receivedProblemReport.body.code shouldNotBe null

            // sender may try to resend the last message
            // for testing purposes, we'll need to create the messenger again, with the correct requested attribute names
            // note that the subject messenger is not recreated, i.e. it expects another "requestPresentation" message
            verifierMessenger = PresentProofMessenger.newVerifierInstance(
                verifier = verifier,
                messageWrapper = MessageWrapper(verifierCryptoService),
                challengeForPresentation = verifierChallenge,
                requestedAttributeTypes = listOf(attributeName)
            )
            val secondParsedInvitation = verifierMessenger.parseMessage(invitationMessage)
            secondParsedInvitation.shouldBeInstanceOf<NextMessage.Send>()
            val secondRequestPresentation = secondParsedInvitation.message

            val parsedSecondRequestPresentation =
                holderMessenger.parseMessage(secondRequestPresentation)
            parsedSecondRequestPresentation.shouldBeInstanceOf<NextMessage.Send>()
            val presentation = parsedSecondRequestPresentation.message

            val parseSecondPresentation = verifierMessenger.parseMessage(presentation)
            parseSecondPresentation.shouldBeInstanceOf<NextMessage.Result<PresentProofProtocolResult>>()
            val receivedPresentation = parseSecondPresentation.result

            assertPresentation(receivedPresentation, attributeName, attributeValue)
        }
    }

    private fun assertPresentation(
        vpResult: PresentProofProtocolResult,
        attributeName: String,
        attributeValue: String
    ) {
        vpResult.shouldBeInstanceOf<Verifier.VerifyPresentationResult.Success>()
        val vp = vpResult.vp
        vp.verifiableCredentials shouldHaveSize 1
        vp.verifiableCredentials.forEach {
            it.vc.credentialSubject.shouldBeInstanceOf<AtomicAttributeCredential>()
            (it.vc.credentialSubject as AtomicAttributeCredential).name shouldBe attributeName
            (it.vc.credentialSubject as AtomicAttributeCredential).value shouldBe attributeValue
        }
    }

    private fun randomCredential(subjectId: String) =
        IssuerCredentialDataProvider.CredentialToBeIssued(
            AtomicAttributeCredential(
                subjectId,
                uuid4().toString(),
                uuid4().toString(),
                "application/text"
            ),
            Clock.System.now() + attributeLifetime,
            ConstantIndex.Generic.vcType
        )

}