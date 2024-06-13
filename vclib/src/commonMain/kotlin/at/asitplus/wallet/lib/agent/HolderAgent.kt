package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult
import at.asitplus.KmmResult.Companion.wrap
import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.crypto.datatypes.cose.CoseKey
import at.asitplus.crypto.datatypes.cose.toCoseKey
import at.asitplus.crypto.datatypes.pki.X509Certificate
import at.asitplus.jsonpath.core.NodeList
import at.asitplus.jsonpath.core.NormalizedJsonPath
import at.asitplus.wallet.lib.cbor.CoseService
import at.asitplus.wallet.lib.cbor.DefaultCoseService
import at.asitplus.wallet.lib.data.CredentialToJsonConverter
import at.asitplus.wallet.lib.data.VerifiableCredentialJws
import at.asitplus.wallet.lib.data.VerifiableCredentialSdJwt
import at.asitplus.wallet.lib.data.dif.ClaimFormatEnum
import at.asitplus.wallet.lib.data.dif.ConstraintField
import at.asitplus.wallet.lib.data.dif.FormatHolder
import at.asitplus.wallet.lib.data.dif.InputDescriptor
import at.asitplus.wallet.lib.data.dif.InputEvaluator
import at.asitplus.wallet.lib.data.dif.PresentationDefinition
import at.asitplus.wallet.lib.data.dif.PresentationSubmission
import at.asitplus.wallet.lib.data.dif.PresentationSubmissionDescriptor
import at.asitplus.wallet.lib.data.dif.PresentationSubmissionValidator
import at.asitplus.wallet.lib.iso.IssuerSigned
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.jws.JwsService
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier


/**
 * An agent that only implements [Holder], i.e. it can receive credentials form other agents
 * and present credentials to other agents.
 */
class HolderAgent(
    private val validator: Validator = Validator.newDefaultInstance(),
    private val subjectCredentialStore: SubjectCredentialStore = InMemorySubjectCredentialStore(),
    private val jwsService: JwsService,
    private val coseService: CoseService,
    override val publicKey: CryptoPublicKey,
    private val verifiablePresentationFactory: VerifiablePresentationFactory = VerifiablePresentationFactory(
        jwsService = jwsService,
        coseService = coseService,
        identifier = publicKey.didEncoded,
    ),
    private val difInputEvaluator: InputEvaluator = InputEvaluator(),
) : Holder {

    constructor(
        cryptoService: CryptoService,
        subjectCredentialStore: SubjectCredentialStore = InMemorySubjectCredentialStore()
    ) : this(
        validator = Validator.newDefaultInstance(DefaultVerifierCryptoService(), Parser()),
        subjectCredentialStore = subjectCredentialStore,
        jwsService = DefaultJwsService(cryptoService),
        coseService = DefaultCoseService(cryptoService),
        publicKey = cryptoService.publicKey
    )

    /**
     * Sets the revocation list ot use for further processing of Verifiable Credentials
     *
     * @return `true` if the revocation list has been validated and set, `false` otherwise
     */
    override fun setRevocationList(it: String): Boolean {
        return validator.setRevocationList(it)
    }

    /**
     * Stores all verifiable credentials from [credentialList] that parse and validate,
     * and returns them for future reference.
     *
     * Note: Revocation credentials should not be stored, but set with [setRevocationList].
     */
    override suspend fun storeCredentials(credentialList: List<Holder.StoreCredentialInput>): Holder.StoredCredentialsResult {
        val acceptedVcJwt = mutableListOf<VerifiableCredentialJws>()
        val acceptedSdJwt = mutableListOf<VerifiableCredentialSdJwt>()
        val acceptedIso = mutableListOf<IssuerSigned>()
        val rejected = mutableListOf<String>()
        val attachments = mutableListOf<Holder.StoredAttachmentResult>()
        credentialList.filterIsInstance<Holder.StoreCredentialInput.Vc>().forEach { cred ->
            when (val vc = validator.verifyVcJws(cred.vcJws, publicKey)) {
                is Verifier.VerifyCredentialResult.InvalidStructure -> rejected += vc.input
                is Verifier.VerifyCredentialResult.Revoked -> rejected += vc.input
                is Verifier.VerifyCredentialResult.SuccessJwt -> acceptedVcJwt += vc.jws.also {
                    subjectCredentialStore.storeCredential(
                        it, cred.vcJws, cred.scheme
                    )
                }.also {
                    cred.attachments?.forEach { attachment ->
                        subjectCredentialStore.storeAttachment(
                            attachment.name,
                            attachment.data,
                            it.vc.id,
                        ).also {
                            attachments += Holder.StoredAttachmentResult(
                                attachment.name,
                                attachment.data,
                            )
                        }
                    }
                }

                else -> {}
            }
        }
        credentialList.filterIsInstance<Holder.StoreCredentialInput.SdJwt>().forEach { cred ->
            when (val vc = validator.verifySdJwt(cred.vcSdJwt, publicKey)) {
                is Verifier.VerifyCredentialResult.InvalidStructure -> rejected += vc.input
                is Verifier.VerifyCredentialResult.Revoked -> rejected += vc.input
                is Verifier.VerifyCredentialResult.SuccessSdJwt -> acceptedSdJwt += vc.sdJwt.also {
                    subjectCredentialStore.storeCredential(
                        it, cred.vcSdJwt,
                        vc.disclosures,
                        cred.scheme,
                    )
                }

                else -> {}
            }
        }
        credentialList.filterIsInstance<Holder.StoreCredentialInput.Iso>().forEach { cred ->
            val issuerKey: CoseKey? =
                cred.issuerSigned.issuerAuth.unprotectedHeader?.certificateChain?.let {
                    runCatching { X509Certificate.decodeFromDer(it) }.getOrNull()?.publicKey?.toCoseKey()
                        ?.getOrNull()
                }

            when (val result = validator.verifyIsoCred(cred.issuerSigned, issuerKey)) {
                is Verifier.VerifyCredentialResult.InvalidStructure -> rejected += result.input
                is Verifier.VerifyCredentialResult.Revoked -> rejected += result.input
                is Verifier.VerifyCredentialResult.SuccessIso -> acceptedIso += result.issuerSigned.also {
                    subjectCredentialStore.storeCredential(
                        result.issuerSigned, cred.scheme
                    )
                }

                else -> {}
            }
        }
        return Holder.StoredCredentialsResult(
            acceptedVcJwt = acceptedVcJwt,
            acceptedSdJwt = acceptedSdJwt,
            acceptedIso = acceptedIso,
            rejected = rejected,
            attachments = attachments
        )
    }


    /**
     * Gets a list of all stored credentials, with a revocation status.
     *
     * Note that the revocation status may be [Validator.RevocationStatus.UNKNOWN] if no revocation list
     * has been set with [setRevocationList]
     */
    override suspend fun getCredentials(): Collection<Holder.StoredCredential>? {
        val credentials = subjectCredentialStore.getCredentials().getOrNull() ?: run {
            Napier.w("Got no credentials from subjectCredentialStore")
            return null
        }
        return credentials.map {
            when (it) {
                is SubjectCredentialStore.StoreEntry.Iso -> Holder.StoredCredential.Iso(
                    storeEntry = it,
                    status = Validator.RevocationStatus.UNKNOWN,
                )

                is SubjectCredentialStore.StoreEntry.Vc -> Holder.StoredCredential.Vc(
                    storeEntry = it,
                    status = validator.checkRevocationStatus(it.vc),
                )

                is SubjectCredentialStore.StoreEntry.SdJwt -> Holder.StoredCredential.SdJwt(
                    storeEntry = it,
                    status = validator.checkRevocationStatus(it.sdJwt),
                )
            }
        }
    }

    /**
     * Gets a list of all valid stored credentials sorted by preference
     */
    private suspend fun getValidCredentialsByPriority() = getCredentials()?.filter {
        it.status != Validator.RevocationStatus.REVOKED
    }?.map { it.storeEntry }?.sortedBy {
        // prefer iso credentials and sd jwt credentials over plain vc credentials
        // -> they support selective disclosure!
        when (it) {
            is SubjectCredentialStore.StoreEntry.Vc -> 2
            is SubjectCredentialStore.StoreEntry.SdJwt -> 1
            is SubjectCredentialStore.StoreEntry.Iso -> 1
        }
    }


    override suspend fun createPresentation(
        challenge: String,
        audienceId: String,
        presentationDefinition: PresentationDefinition,
        fallbackFormatHolder: FormatHolder?,
        pathAuthorizationValidator: PathAuthorizationValidator?,
    ): KmmResult<Holder.PresentationResponseParameters> = runCatching {
        val submittedCredentials = matchInputDescriptorsAgainstCredentialStore(
            inputDescriptors = presentationDefinition.inputDescriptors,
            fallbackFormatHolder = fallbackFormatHolder,
            pathAuthorizationValidator = pathAuthorizationValidator,
        ).getOrThrow().toDefaultSubmission()

        val validator = PresentationSubmissionValidator.createInstance(
            submissionRequirements = presentationDefinition.submissionRequirements,
            inputDescriptors = presentationDefinition.inputDescriptors,
        ).getOrThrow()

        if (!validator.isValidSubmission(submittedCredentials.keys)) {
            return KmmResult.failure(
                SubmissionRequirementsUnsatisfiedException(
                    missingInputDescriptors = presentationDefinition.inputDescriptors.map {
                        it.id
                    }.toSet() - submittedCredentials.keys
                )
            )
        }

        return createPresentation(
            challenge = challenge,
            audienceId = audienceId,
            presentationDefinitionId = presentationDefinition.id,
            presentationSubmissionSelection = submittedCredentials,
        ).getOrThrow()
    }.wrap()

    override suspend fun createPresentation(
        challenge: String,
        audienceId: String,
        presentationDefinitionId: String?,
        presentationSubmissionSelection: Map<String, InputDescriptorCredentialSubmission>,
    ): KmmResult<Holder.PresentationResponseParameters> = runCatching {
        val submissionList = presentationSubmissionSelection.toList()
        val presentationSubmission = PresentationSubmission.fromMatches(
            presentationId = presentationDefinitionId,
            matches = submissionList,
        )

        val verifiablePresentations = submissionList.map { match ->
            val credential = match.second.credential
            val disclosedAttributes = match.second.disclosedAttributes
            verifiablePresentationFactory.createVerifiablePresentation(
                challenge = challenge,
                audienceId = audienceId,
                credential = credential,
                disclosedAttributes = disclosedAttributes,
            ) ?: throw CredentialPresentationException(
                credential = credential,
                inputDescriptorId = match.first,
                disclosedAttributes = disclosedAttributes,
            )
        }

        Holder.PresentationResponseParameters(
            presentationSubmission = presentationSubmission,
            presentationResults = verifiablePresentations,
        )
    }.wrap()

    suspend fun createVcPresentation(
        validCredentials: List<String>,
        challenge: String,
        audienceId: String,
    ) = verifiablePresentationFactory.createVcPresentation(
        validCredentials = validCredentials,
        challenge = challenge,
        audienceId = audienceId,
    )


    override suspend fun matchInputDescriptorsAgainstCredentialStore(
        inputDescriptors: Collection<InputDescriptor>,
        fallbackFormatHolder: FormatHolder?,
        pathAuthorizationValidator: PathAuthorizationValidator?,
    ) = runCatching {
        findInputDescriptorMatches(
            inputDescriptors = inputDescriptors,
            credentials = getValidCredentialsByPriority() ?: throw CredentialRetrievalException(),
            fallbackFormatHolder = fallbackFormatHolder,
            pathAuthorizationValidator = pathAuthorizationValidator,
        )
    }.wrap()

    private fun findInputDescriptorMatches(
        inputDescriptors: Collection<InputDescriptor>,
        credentials: Collection<SubjectCredentialStore.StoreEntry>,
        fallbackFormatHolder: FormatHolder?,
        pathAuthorizationValidator: PathAuthorizationValidator?,
    ) = inputDescriptors.associateWith { inputDescriptor ->
        credentials.mapNotNull { credential ->
            evaluateInputDescriptorAgainstCredential(
                inputDescriptor = inputDescriptor,
                credential = credential,
                presentationDefinitionFormatHolder = fallbackFormatHolder,
                pathAuthorizationValidator = {
                    pathAuthorizationValidator?.invoke(credential, it) ?: true
                },
            )?.let {
                credential to it
            }
        }.toMap()
    }

    private fun evaluateInputDescriptorAgainstCredential(
        inputDescriptor: InputDescriptor,
        credential: SubjectCredentialStore.StoreEntry,
        presentationDefinitionFormatHolder: FormatHolder?,
        pathAuthorizationValidator: (NormalizedJsonPath) -> Boolean,
    ) = listOf(credential).filter {
        it.isFormatSupported(inputDescriptor.format ?: presentationDefinitionFormatHolder)
    }.filter {
        // iso credentials now have their doctype encoded into the id
        when (it) {
            is SubjectCredentialStore.StoreEntry.Iso -> it.scheme.isoDocType == inputDescriptor.id
            else -> true
        }
    }.firstNotNullOfOrNull {
        difInputEvaluator.evaluateConstraintFieldMatches(
            inputDescriptor = inputDescriptor,
            credential = CredentialToJsonConverter.toJsonElement(it),
            pathAuthorizationValidator = pathAuthorizationValidator,
        ).getOrNull()
    }

    /** assume credential format to be supported by the verifier if no format holder is specified */
    private fun SubjectCredentialStore.StoreEntry.isFormatSupported(supportedFormats: FormatHolder?): Boolean =
        supportedFormats?.let { formatHolder ->
            when (this) {
                is SubjectCredentialStore.StoreEntry.Vc -> formatHolder.jwtVp != null
                is SubjectCredentialStore.StoreEntry.SdJwt -> formatHolder.jwtSd != null
                is SubjectCredentialStore.StoreEntry.Iso -> formatHolder.msoMdoc != null
            }
        } ?: true

    private fun PresentationSubmission.Companion.fromMatches(
        presentationId: String?,
        matches: List<Pair<String, InputDescriptorCredentialSubmission>>,
    ) = PresentationSubmission(
        id = uuid4().toString(),
        definitionId = presentationId,
        descriptorMap = matches.mapIndexed { index, match ->
            PresentationSubmissionDescriptor.fromMatch(
                inputDescriptorId = match.first,
                credential = match.second.credential,
                index = if (matches.size == 1) null else index,
            )
        },
    )

    private fun PresentationSubmissionDescriptor.Companion.fromMatch(
        credential: SubjectCredentialStore.StoreEntry,
        inputDescriptorId: String,
        index: Int?,
    ) = PresentationSubmissionDescriptor(
        id = inputDescriptorId,
        format = when (credential) {
            is SubjectCredentialStore.StoreEntry.Vc -> ClaimFormatEnum.JWT_VP
            is SubjectCredentialStore.StoreEntry.SdJwt -> ClaimFormatEnum.JWT_SD
            is SubjectCredentialStore.StoreEntry.Iso -> ClaimFormatEnum.MSO_MDOC
        },
        // from https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-6.1-2.4
        // These objects contain a field called path, which, for this specification,
        // MUST have the value $ (top level root path) when only one Verifiable Presentation is contained in the VP Token,
        // and MUST have the value $[n] (indexed path from root) when there are multiple Verifiable Presentations,
        // where n is the index to select.
        path = index?.let { "\$[$it]" } ?: "\$",
    )
}

open class PresentationException(message: String) : Exception(message)

class CredentialRetrievalException : PresentationException(
    "Credentials could not be retrieved from the store"
)

class SubmissionRequirementsUnsatisfiedException(
    missingInputDescriptors: Set<String>
) : PresentationException(
    "Submission requirements are unsatisfied: No credentials were submitted for input descriptors: $missingInputDescriptors"
)

class AttributeNotAvailableException(
    val credential: SubjectCredentialStore.StoreEntry.Iso,
    val namespace: String,
    val attributeName: String,
) : PresentationException("Attribute not available in credential: $['$namespace']['$attributeName']: $credential")

class CredentialPresentationException(
    val inputDescriptorId: String,
    val credential: SubjectCredentialStore.StoreEntry,
    val disclosedAttributes: Collection<NormalizedJsonPath>,
) : PresentationException(
    "Presentation for input descriptor with id '$inputDescriptorId' failed with credential $credential and attributes: <${
        disclosedAttributes.joinToString(", ")
    }>"
)
