package at.asitplus.wallet.lib.agent

import at.asitplus.crypto.datatypes.cose.CoseAlgorithm
import at.asitplus.crypto.datatypes.cose.CoseHeader
import at.asitplus.crypto.datatypes.cose.CoseKey
import at.asitplus.crypto.datatypes.jws.JwsContentTypeConstants
import at.asitplus.wallet.lib.DataSourceProblem
import at.asitplus.wallet.lib.DefaultZlibService
import at.asitplus.wallet.lib.KmmBitSet
import at.asitplus.wallet.lib.ZlibService
import at.asitplus.wallet.lib.cbor.CoseService
import at.asitplus.wallet.lib.cbor.DefaultCoseService
import at.asitplus.wallet.lib.data.Base64Strict
import at.asitplus.wallet.lib.data.CredentialStatus
import at.asitplus.wallet.lib.data.RevocationListSubject
import at.asitplus.wallet.lib.data.VcDataModelConstants.REVOCATION_LIST_MIN_SIZE
import at.asitplus.wallet.lib.data.VerifiableCredential
import at.asitplus.wallet.lib.iso.*
import at.asitplus.wallet.lib.iso.IsoDataModelConstants.DIGEST_SHA_256
import at.asitplus.wallet.lib.iso.IsoDataModelConstants.VERSION_1_0
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.jws.JwsService
import com.benasher44.uuid.uuid4
import io.github.aakira.napier.Napier
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * An agent that only implements [Issuer], i.e. it issues credentials for other agents.
 */
class IssuerAgent(
    private val validator: Validator,
    private val issuerCredentialStore: IssuerCredentialStore = InMemoryIssuerCredentialStore(),
    private val revocationListBaseUrl: String = "https://wallet.a-sit.at/backend/credentials/status",
    private val dataProvider: IssuerCredentialDataProvider = EmptyCredentialDataProvider,
    private val zlibService: ZlibService = DefaultZlibService(),
    private val revocationListLifetime: Duration = 48.hours,
    private val jwsService: JwsService,
    private val coseService: CoseService,
    private val clock: Clock = Clock.System,
    override val identifier: String,
    private val timePeriodProvider: TimePeriodProvider = FixedTimePeriodProvider,
) : Issuer {

    companion object {
        fun newDefaultInstance(
            cryptoService: CryptoService = DefaultCryptoService(),
            verifierCryptoService: VerifierCryptoService = DefaultVerifierCryptoService(),
            issuerCredentialStore: IssuerCredentialStore = InMemoryIssuerCredentialStore(),
            clock: Clock = Clock.System,
            timePeriodProvider: TimePeriodProvider = FixedTimePeriodProvider,
            dataProvider: IssuerCredentialDataProvider = EmptyCredentialDataProvider,
        ): IssuerAgent = IssuerAgent(
            validator = Validator.newDefaultInstance(
                verifierCryptoService,
                Parser(clock.now().toEpochMilliseconds())
            ),
            issuerCredentialStore = issuerCredentialStore,
            jwsService = DefaultJwsService(cryptoService),
            coseService = DefaultCoseService(cryptoService),
            dataProvider = dataProvider,
            identifier = cryptoService.identifier,
            timePeriodProvider = timePeriodProvider,
            clock = clock,
        )
    }

    /**
     * Issues credentials for some [attributeTypes] (i.e. some of
     * [at.asitplus.wallet.lib.data.ConstantIndex.CredentialScheme.vcType]) to the subject specified with [subjectId]
     * (which should be a URL of the cryptographic key of the holder)
     */
    override suspend fun issueCredentialWithTypes(
        subjectId: String,
        subjectPublicKey: CoseKey?,
        attributeTypes: Collection<String>
    ): Issuer.IssuedCredentialResult {
        val result = dataProvider.getCredentialWithType(subjectId, subjectPublicKey, attributeTypes)
        result.exceptionOrNull()?.let { failure ->
            return Issuer.IssuedCredentialResult(failed = attributeTypes.map { Issuer.FailedAttribute(it, failure) })
        }
        val issuedCredentials = result.getOrThrow().map { issueCredential(it) }
        return Issuer.IssuedCredentialResult(
            successful = issuedCredentials.flatMap { it.successful },
            failed = issuedCredentials.flatMap { it.failed })
    }

    /**
     * Wraps [credential] into a single [VerifiableCredential],
     * returns a JWS representation of that VC.
     */
    override suspend fun issueCredential(
        credential: CredentialToBeIssued,
    ): Issuer.IssuedCredentialResult {
        val issuanceDate = clock.now()
        when (credential) {
            is CredentialToBeIssued.Iso -> {
                val expirationDate = credential.expiration
                //TODO: we are not going to store anything ATM since we will first need a clear stance on what to store
                /*val timePeriod = timePeriodProvider.getTimePeriodFor(issuanceDate)
                val statusListIndex = issuerCredentialStore.storeGetNextIndex(
                    credential.issuerSignedItems,
                    issuanceDate,
                    expirationDate,
                    timePeriod,
                ) ?: return Issuer.IssuedCredentialResult(
                    failed = listOf(
                        Issuer.FailedAttribute(credential.attributeType, DataSourceProblem("no statusListIndex"))
                    )
                ).also { Napier.w("Got no statusListIndex from issuerCredentialStore, can't issue credential") }*/
                val mso = MobileSecurityObject(
                    version = VERSION_1_0,
                    digestAlgorithm = DIGEST_SHA_256,
                    valueDigests = mapOf(
                        IsoDataModelConstants.NAMESPACE_MDL to ValueDigestList(credential.issuerSignedItems.map {
                            ValueDigest.fromIssuerSigned(it)
                        })
                    ),
                    deviceKeyInfo = DeviceKeyInfo(credential.subjectPublicKey),
                    docType = IsoDataModelConstants.DOC_TYPE_MDL,
                    validityInfo = ValidityInfo(
                        signed = issuanceDate,
                        validFrom = issuanceDate,
                        validUntil = expirationDate,
                    )
                )
                val issuerSigned = IssuerSigned(
                    namespaces = mapOf(
                        IsoDataModelConstants.NAMESPACE_MDL to IssuerSignedList.withItems(credential.issuerSignedItems)
                    ),
                    issuerAuth = coseService.createSignedCose(
                        protectedHeader = CoseHeader(algorithm = CoseAlgorithm.ES256),
                        unprotectedHeader = null,
                        payload = mso.serializeForIssuerAuth(),
                        addKeyId = false,
                        addCertificate = true,
                    ).getOrThrow()
                )
                return Issuer.IssuedCredentialResult(
                    successful = listOf(Issuer.IssuedCredential.Iso(issuerSigned))
                )
            }

            is CredentialToBeIssued.Vc -> {
                val vcId = "urn:uuid:${uuid4()}"
                val expirationDate = credential.expiration
                val timePeriod = timePeriodProvider.getTimePeriodFor(issuanceDate)
                val statusListIndex = issuerCredentialStore.storeGetNextIndex(
                    vcId,
                    credential.subject,
                    issuanceDate,
                    expirationDate,
                    timePeriod
                ) ?: return Issuer.IssuedCredentialResult(
                    failed = listOf(
                        Issuer.FailedAttribute(credential.attributeType, DataSourceProblem("vcId internal mismatch"))
                    )
                ).also { Napier.w("Got no statusListIndex from issuerCredentialStore, can't issue credential") }

                val credentialStatus = CredentialStatus(getRevocationListUrlFor(timePeriod), statusListIndex)
                val vc = VerifiableCredential(
                    id = vcId,
                    issuer = identifier,
                    issuanceDate = issuanceDate,
                    expirationDate = expirationDate,
                    credentialStatus = credentialStatus,
                    credentialSubject = credential.subject,
                    credentialType = credential.attributeType,
                )

                val vcInJws = wrapVcInJws(vc)
                    ?: return Issuer.IssuedCredentialResult(
                        failed = listOf(
                            Issuer.FailedAttribute(credential.attributeType, RuntimeException("signing failed"))
                        )
                    ).also { Napier.w("Could not wrap credential in JWS") }

                return Issuer.IssuedCredentialResult(
                    successful = listOf(Issuer.IssuedCredential.Vc(vcInJws, credential.attachments))
                )
            }
        }
    }

    /**
     * Wraps the revocation information from [issuerCredentialStore] into a VC,
     * returns a JWS representation of that.
     */
    override suspend fun issueRevocationListCredential(timePeriod: Int): String? {
        val revocationListUrl = getRevocationListUrlFor(timePeriod)
        val revocationList = buildRevocationList(timePeriod) ?: return null
        val subject = RevocationListSubject("$revocationListUrl#list", revocationList)
        val credential = VerifiableCredential(
            id = revocationListUrl,
            issuer = identifier,
            issuanceDate = clock.now(),
            lifetime = revocationListLifetime,
            credentialSubject = subject
        )
        return wrapVcInJws(credential)
    }

    /**
     * Returns a Base64-encoded, zlib-compressed bitstring of revoked credentials, where
     * the entry at "revocationListIndex" (of the credential) is true iff it is revoked
     */
    override fun buildRevocationList(timePeriod: Int): String? {
        val bitset = KmmBitSet(REVOCATION_LIST_MIN_SIZE)
        issuerCredentialStore.getRevokedStatusListIndexList(timePeriod)
            .forEach { bitset[it] = true }
        val input = bitset.toByteArray()
        return zlibService.compress(input)?.encodeToString(Base64Strict)
    }

    /**
     * Revokes all verifiable credentials from [credentialsToRevoke] list that parse and validate.
     * It returns true if all revocations were successful.
     */
    override fun revokeCredentials(credentialsToRevoke: List<String>): Boolean =
        credentialsToRevoke.map { validator.verifyVcJws(it, null) }
            .filterIsInstance<Verifier.VerifyCredentialResult.Success>()
            .all {
                issuerCredentialStore.revoke(
                    it.jws.vc.id,
                    timePeriodProvider.getTimePeriodFor(it.jws.vc.issuanceDate)
                )
            }

    override fun compileCurrentRevocationLists(): List<String> {
        val list = mutableListOf<String>()
        for (timePeriod in timePeriodProvider.getRelevantTimePeriods(clock)) {
            if (timePeriodProvider.getCurrentTimePeriod(clock) == timePeriod
                || issuerCredentialStore.getRevokedStatusListIndexList(timePeriod).isNotEmpty()
            ) {
                list.add(getRevocationListUrlFor(timePeriod))
            }
        }
        return list
    }

    private suspend fun wrapVcInJws(vc: VerifiableCredential): String? {
        val jwsPayload = vc.toJws().serialize().encodeToByteArray()
        return jwsService.createSignedJwt(JwsContentTypeConstants.JWT, jwsPayload)
    }

    private fun getRevocationListUrlFor(timePeriod: Int) =
        revocationListBaseUrl.let { it + (if (!it.endsWith('/')) "/" else "") + timePeriod }

}
