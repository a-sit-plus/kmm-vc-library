package at.asitplus.wallet.lib.oidc

import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.Holder
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.oidc.OidcSiopVerifier.RequestOptions
import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

class OidcSiopSdJwtProtocolTest : FreeSpec({

    lateinit var relyingPartyUrl: String
    lateinit var walletUrl: String

    lateinit var holderCryptoService: CryptoService
    lateinit var verifierCryptoService: CryptoService

    lateinit var holderAgent: Holder
    lateinit var verifierAgent: Verifier

    lateinit var holderSiop: OidcSiopWallet
    lateinit var verifierSiop: OidcSiopVerifier

    beforeEach {
        holderCryptoService = DefaultCryptoService()
        verifierCryptoService = DefaultCryptoService()
        relyingPartyUrl = "https://example.com/rp/${uuid4()}"
        walletUrl = "https://example.com/wallet/${uuid4()}"
        holderAgent = HolderAgent.newDefaultInstance(holderCryptoService)
        verifierAgent = VerifierAgent.newDefaultInstance(verifierCryptoService.publicKey.didEncoded)
        runBlocking {
            holderAgent.storeCredentials(
                IssuerAgent.newDefaultInstance(
                    DefaultCryptoService(),
                    dataProvider = DummyCredentialDataProvider(),
                ).issueCredential(
                    subjectPublicKey = holderCryptoService.publicKey,
                    attributeTypes = listOf(ConstantIndex.AtomicAttribute2023.vcType),
                    representation = ConstantIndex.CredentialRepresentation.SD_JWT,
                ).toStoreCredentialInput()
            )
        }

        holderSiop = OidcSiopWallet.newInstance(
            holder = holderAgent,
            cryptoService = holderCryptoService
        )
        verifierSiop = OidcSiopVerifier.newInstance(
            verifier = verifierAgent,
            cryptoService = verifierCryptoService,
            relyingPartyUrl = relyingPartyUrl,
        )
    }

    "test with Fragment" {
        val authnRequest = verifierSiop.createAuthnRequestUrl(
            walletUrl = walletUrl,
            requestOptions = RequestOptions(representation = ConstantIndex.CredentialRepresentation.SD_JWT)
        ).also { println(it) }
        authnRequest shouldContain "jwt_sd"

        val authnResponse = holderSiop.createAuthnResponse(authnRequest).getOrThrow()
        authnResponse.shouldBeInstanceOf<OidcSiopWallet.AuthenticationResponseResult.Redirect>().also { println(it) }

        val validationResults = verifierSiop.validateAuthnResponse(authnResponse.url)
        validationResults.shouldBeInstanceOf<OidcSiopVerifier.AuthnResponseResult.VerifiablePresentationValidationResults>()
        val result = validationResults.validationResults.first()
        result.shouldBeInstanceOf<OidcSiopVerifier.AuthnResponseResult.SuccessSdJwt>()
        result.disclosures.shouldNotBeEmpty()

        assertSecondRun(verifierSiop, holderSiop, walletUrl)
    }

    "Selective Disclosure with custom credential" {
        val requestedClaim = "given-name"
        verifierSiop = OidcSiopVerifier.newInstance(
            verifier = verifierAgent,
            cryptoService = verifierCryptoService,
            relyingPartyUrl = relyingPartyUrl,
        )
        val authnRequest = verifierSiop.createAuthnRequestUrl(
            walletUrl = walletUrl,
            requestOptions = RequestOptions(
                representation = ConstantIndex.CredentialRepresentation.SD_JWT,
                credentialScheme = ConstantIndex.AtomicAttribute2023,
                requestedAttributes = listOf(requestedClaim)
            )
        ).also { println(it) }
        authnRequest shouldContain "jwt_sd"
        authnRequest shouldContain requestedClaim

        val authnResponse = holderSiop.createAuthnResponse(authnRequest).getOrThrow()
        authnResponse.shouldBeInstanceOf<OidcSiopWallet.AuthenticationResponseResult.Redirect>().also { println(it) }

        val validationResult = verifierSiop.validateAuthnResponse(authnResponse.url)
        validationResult.shouldBeInstanceOf<OidcSiopVerifier.AuthnResponseResult.VerifiablePresentationValidationResults>()
        val result = validationResult.validationResults.first()
        result.shouldBeInstanceOf<OidcSiopVerifier.AuthnResponseResult.SuccessSdJwt>()
        val sdJwt = result.sdJwt.also { println(it) }

        result.disclosures.shouldNotBeEmpty()
        result.disclosures.shouldBeSingleton()
        result.disclosures.shouldHaveSingleElement { it.claimName == requestedClaim }
        sdJwt.shouldNotBeNull()
    }

})

private suspend fun assertSecondRun(
    verifierSiop: OidcSiopVerifier,
    holderSiop: OidcSiopWallet,
    walletUrl: String
) {
    val authnRequestUrl = verifierSiop.createAuthnRequestUrl(
        walletUrl = walletUrl,
        requestOptions = RequestOptions(representation = ConstantIndex.CredentialRepresentation.SD_JWT)
    )
    val authnResponse = holderSiop.createAuthnResponse(authnRequestUrl)
    val url = (authnResponse.getOrThrow() as OidcSiopWallet.AuthenticationResponseResult.Redirect).url
    val validationResults = verifierSiop.validateAuthnResponse(url)
    validationResults.shouldBeInstanceOf<OidcSiopVerifier.AuthnResponseResult.VerifiablePresentationValidationResults>()
    val validation = validationResults.validationResults.first()
    validation.shouldBeInstanceOf<OidcSiopVerifier.AuthnResponseResult.SuccessSdJwt>()
}