package at.asitplus.wallet.lib.oidc

import at.asitplus.wallet.lib.agent.Holder
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.KeyPairAdapter
import at.asitplus.wallet.lib.agent.RandomKeyPairAdapter
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.agent.VerifierAgent
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.dif.FormatHolder
import at.asitplus.wallet.mdl.MobileDrivingLicenceScheme
import com.benasher44.uuid.uuid4
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

class OidcSiopCombinedProtocolTwoStepTest : FreeSpec({

    lateinit var relyingPartyUrl: String

    lateinit var holderKeyPair: KeyPairAdapter
    lateinit var verifierKeyPair: KeyPairAdapter

    lateinit var holderAgent: Holder
    lateinit var verifierAgent: Verifier

    lateinit var holderSiop: OidcSiopWallet
    lateinit var verifierSiop: OidcSiopVerifier

    beforeEach {
        holderKeyPair = RandomKeyPairAdapter()
        verifierKeyPair = RandomKeyPairAdapter()
        relyingPartyUrl = "https://example.com/rp/${uuid4()}"
        holderAgent = HolderAgent(holderKeyPair)
        verifierAgent = VerifierAgent(verifierKeyPair)

        holderSiop = OidcSiopWallet.newDefaultInstance(
            keyPairAdapter = holderKeyPair,
            holder = holderAgent,
        )
        verifierSiop = OidcSiopVerifier.newInstance(
            verifier = verifierAgent,
            relyingPartyUrl = relyingPartyUrl,
        )
    }

    "test credential matching" - {
        "only credentials of the correct format are matched" {
            runBlocking {
                holderAgent.storeIsoCredential(holderKeyPair, ConstantIndex.AtomicAttribute2023)
                holderAgent.storeIsoCredential(holderKeyPair, ConstantIndex.AtomicAttribute2023)
                holderAgent.storeSdJwtCredential(holderKeyPair, ConstantIndex.AtomicAttribute2023)
            }

            verifierSiop = OidcSiopVerifier.newInstance(
                verifier = verifierAgent,
                relyingPartyUrl = relyingPartyUrl,
            )

            val authnRequest = verifierSiop.createAuthnRequest(
                requestOptions = OidcSiopVerifier.RequestOptions(
                    credentialScheme = ConstantIndex.AtomicAttribute2023,
                    representation = ConstantIndex.CredentialRepresentation.ISO_MDOC,
                )
            ).let { request ->
                request.copy(
                    presentationDefinition = request.presentationDefinition?.let { presentationDefinition ->
                        presentationDefinition.copy(
                            // only support msoMdoc here
                            formats = FormatHolder(
                                msoMdoc = presentationDefinition.formats?.msoMdoc
                            ),
                            inputDescriptors = presentationDefinition.inputDescriptors.map { inputDescriptor ->
                                inputDescriptor.copy(
                                    format = null
                                )
                            }
                        )
                    },
                )
            }
            val preparationState = holderSiop.startAuthorizationResponsePreparation(
                holderSiop.parseAuthenticationRequestParameters(authnRequest.serialize())
                    .getOrThrow()
            ).getOrThrow()

            val presentationDefinition = preparationState.presentationDefinition
            presentationDefinition.shouldNotBeNull()

            val inputDescriptorId = presentationDefinition.inputDescriptors.first().id

            val matches = holderAgent.matchInputDescriptorsAgainstCredentialStore(
                presentationDefinition.inputDescriptors,
                presentationDefinition.formats,
            ).getOrThrow()
            val inputDescriptorMatches = matches[inputDescriptorId]!!
            inputDescriptorMatches shouldHaveSize 2
            inputDescriptorMatches.keys.forEach {
                it.shouldBeInstanceOf<SubjectCredentialStore.StoreEntry.Iso>()
            }
        }
    }

    "test credential submission" - {
        "submission requirements need to macth" - {
            "invalid type should not match" {
                runBlocking {
                    holderAgent.storeIsoCredential(
                        holderKeyPair,
                        ConstantIndex.AtomicAttribute2023
                    )
                    holderAgent.storeIsoCredential(
                        holderKeyPair,
                        ConstantIndex.AtomicAttribute2023
                    )
                    holderAgent.storeSdJwtCredential(
                        holderKeyPair,
                        ConstantIndex.AtomicAttribute2023
                    )
                }

                verifierSiop = OidcSiopVerifier.newInstance(
                    verifier = verifierAgent,
                    relyingPartyUrl = relyingPartyUrl,
                )

                val authnRequest = verifierSiop.createAuthnRequest(
                    requestOptions = OidcSiopVerifier.RequestOptions(
                        credentialScheme = ConstantIndex.AtomicAttribute2023,
                        representation = ConstantIndex.CredentialRepresentation.ISO_MDOC,
                    )
                ).let { request ->
                    request.copy(
                        presentationDefinition = request.presentationDefinition?.let { presentationDefinition ->
                            presentationDefinition.copy(
                                // only support msoMdoc here
                                formats = FormatHolder(
                                    msoMdoc = presentationDefinition.formats?.msoMdoc
                                ),
                                inputDescriptors = presentationDefinition.inputDescriptors.map { inputDescriptor ->
                                    inputDescriptor.copy(
                                        format = null
                                    )
                                }
                            )
                        },
                    )
                }
                val preparationState = holderSiop.startAuthorizationResponsePreparation(
                    holderSiop.parseAuthenticationRequestParameters(authnRequest.serialize())
                        .getOrThrow()
                ).getOrThrow()

                val presentationDefinition = preparationState.presentationDefinition
                presentationDefinition.shouldNotBeNull()

                val inputDescriptorId = presentationDefinition.inputDescriptors.first().id

                val matches = holderAgent.matchInputDescriptorsAgainstCredentialStore(
                    presentationDefinition.inputDescriptors,
                    presentationDefinition.formats,
                ).getOrThrow()
                val inputDescriptorMatches = matches[inputDescriptorId]!!
                inputDescriptorMatches shouldHaveSize 2
                inputDescriptorMatches.keys.forEach {
                    it.shouldBeInstanceOf<SubjectCredentialStore.StoreEntry.Iso>()
                }
            }
        }
    }
})

private suspend fun Holder.storeJwtCredentials(
    holderKeyPair: KeyPairAdapter,
    credentialScheme: ConstantIndex.CredentialScheme,
) {
    storeCredential(
        IssuerAgent(
            RandomKeyPairAdapter(),
            DummyCredentialDataProvider(),
        ).issueCredential(
            holderKeyPair.publicKey,
            credentialScheme,
            ConstantIndex.CredentialRepresentation.PLAIN_JWT,
        ).getOrThrow().toStoreCredentialInput()
    )
}

private suspend fun Holder.storeSdJwtCredential(
    holderKeyPair: KeyPairAdapter,
    credentialScheme: ConstantIndex.CredentialScheme,
) {
    storeCredential(
        IssuerAgent(
            RandomKeyPairAdapter(),
            DummyCredentialDataProvider(),
        ).issueCredential(
            holderKeyPair.publicKey,
            credentialScheme,
            ConstantIndex.CredentialRepresentation.SD_JWT,
        ).getOrThrow().toStoreCredentialInput()
    )
}

private suspend fun Holder.storeIsoCredential(
    holderKeyPair: KeyPairAdapter,
    credentialScheme: ConstantIndex.CredentialScheme,
) = storeCredential(
    IssuerAgent(
        RandomKeyPairAdapter(),
        DummyCredentialDataProvider(),
    ).issueCredential(
        holderKeyPair.publicKey,
        credentialScheme,
        ConstantIndex.CredentialRepresentation.ISO_MDOC,
    ).getOrThrow().toStoreCredentialInput()
)
