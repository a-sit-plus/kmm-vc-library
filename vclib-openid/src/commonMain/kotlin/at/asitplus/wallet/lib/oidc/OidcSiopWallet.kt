package at.asitplus.wallet.lib.oidc

import at.asitplus.KmmResult
import at.asitplus.KmmResult.Companion.wrap
import at.asitplus.crypto.datatypes.CryptoPublicKey
import at.asitplus.crypto.datatypes.jws.JsonWebKeySet
import at.asitplus.crypto.datatypes.jws.JwsSigned
import at.asitplus.crypto.datatypes.jws.toJsonWebKey
import at.asitplus.crypto.datatypes.pki.X509Certificate
import at.asitplus.crypto.datatypes.pki.leaf
import at.asitplus.wallet.lib.agent.CryptoService
import at.asitplus.wallet.lib.agent.DefaultCryptoService
import at.asitplus.wallet.lib.agent.Holder
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.PathAuthorizationValidator
import at.asitplus.wallet.lib.data.dif.ClaimFormatEnum
import at.asitplus.wallet.lib.data.dif.PresentationDefinition
import at.asitplus.wallet.lib.data.dif.PresentationPreparationHelper
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.jws.JwsService
import at.asitplus.wallet.lib.oidc.OpenIdConstants.Errors
import at.asitplus.wallet.lib.oidc.OpenIdConstants.ID_TOKEN
import at.asitplus.wallet.lib.oidc.OpenIdConstants.PREFIX_DID_KEY
import at.asitplus.wallet.lib.oidc.OpenIdConstants.ResponseMode.DIRECT_POST
import at.asitplus.wallet.lib.oidc.OpenIdConstants.ResponseMode.DIRECT_POST_JWT
import at.asitplus.wallet.lib.oidc.OpenIdConstants.SCOPE_OPENID
import at.asitplus.wallet.lib.oidc.OpenIdConstants.URN_TYPE_JWK_THUMBPRINT
import at.asitplus.wallet.lib.oidc.OpenIdConstants.VP_TOKEN
import at.asitplus.wallet.lib.oidvci.IssuerMetadata
import at.asitplus.wallet.lib.oidvci.OAuth2Exception
import at.asitplus.wallet.lib.oidvci.decodeFromUrlQuery
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.oidvci.formUrlEncode
import io.github.aakira.napier.Napier
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.util.flattenEntries
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.time.Duration.Companion.seconds


/**
 * Combines Verifiable Presentations with OpenId Connect.
 * Implements [OIDC for VP](https://openid.net/specs/openid-connect-4-verifiable-presentations-1_0.html) (2023-04-21)
 * as well as [SIOP V2](https://openid.net/specs/openid-connect-self-issued-v2-1_0.html) (2023-01-01).
 *
 * The [holder] creates the Authentication Response, see [OidcSiopVerifier] for the verifier.
 */
class OidcSiopWallet(
    private val holder: Holder,
    private val agentPublicKey: CryptoPublicKey,
    private val jwsService: JwsService,
    private val clock: Clock = Clock.System,
    private val clientId: String = "https://wallet.a-sit.at/",
    /**
     * Need to implement if resources are defined by reference, i.e. the URL for a [JsonWebKeySet],
     * or the authentication request itself as `request_uri`, or `presentation_definition_uri`.
     * Implementations need to fetch the url passed in, and return either the body, if there is one,
     * or the HTTP header `Location`, i.e. if the server sends the request object as a redirect.
     */
    private val remoteResourceRetriever: RemoteResourceRetrieverFunction? = null,
    /**
     * Need to verify the request object serialized as a JWS,
     * which may be signed with a pre-registered key (see [OpenIdConstants.ClientIdScheme.PRE_REGISTERED]).
     */
    private val requestObjectJwsVerifier: RequestObjectJwsVerifier? = null,
    /**
     * Need to implement if the presentation definition needs to be derived from a scope value.
     * See [ScopePresentationDefinitionRetriever] for implementation instructions.
     */
    private val scopePresentationDefinitionRetriever: ScopePresentationDefinitionRetriever? = null,
    /**
     * Need to implement in order to enforce authorization rules on
     * credential attributes that are to be disclosed.
     */
    private val pathAuthorizationValidator: PathAuthorizationValidator? = null,
) {
    companion object {
        fun newDefaultInstance(
            cryptoService: CryptoService = DefaultCryptoService(),
            holder: Holder = HolderAgent.newDefaultInstance(cryptoService),
            jwsService: JwsService = DefaultJwsService(cryptoService),
            clock: Clock = Clock.System,
            clientId: String = "https://wallet.a-sit.at/",
            remoteResourceRetriever: RemoteResourceRetrieverFunction = { null },
            requestObjectJwsVerifier: RequestObjectJwsVerifier = RequestObjectJwsVerifier { jws, authnRequest -> true },
            scopePresentationDefinitionRetriever: ScopePresentationDefinitionRetriever? = { null },
        ) = OidcSiopWallet(
            holder = holder,
            agentPublicKey = cryptoService.publicKey,
            jwsService = jwsService,
            clock = clock,
            clientId = clientId,
            remoteResourceRetriever = remoteResourceRetriever,
            requestObjectJwsVerifier = requestObjectJwsVerifier,
            scopePresentationDefinitionRetriever = scopePresentationDefinitionRetriever,
        )
    }

    val metadata: IssuerMetadata by lazy {
        IssuerMetadata(
            issuer = clientId,
            authorizationEndpointUrl = clientId,
            responseTypesSupported = setOf(ID_TOKEN),
            scopesSupported = setOf(SCOPE_OPENID),
            subjectTypesSupported = setOf("pairwise", "public"),
            idTokenSigningAlgorithmsSupported = setOf(jwsService.algorithm.identifier),
            requestObjectSigningAlgorithmsSupported = setOf(jwsService.algorithm.identifier),
            subjectSyntaxTypesSupported = setOf(URN_TYPE_JWK_THUMBPRINT, PREFIX_DID_KEY),
            idTokenTypesSupported = setOf(IdTokenType.SUBJECT_SIGNED),
            presentationDefinitionUriSupported = false,
        )
    }

    /**
     * Pass in the URL sent by the Verifier (containing the [AuthenticationRequestParameters] as query parameters),
     * to create [AuthenticationResponseParameters] that can be sent back to the Verifier, see
     * [AuthenticationResponseResult].
     */
    suspend fun parseAuthenticationRequestParameters(input: String): KmmResult<AuthenticationRequestParametersFrom<*>> {
        val parsedParams = kotlin.run { // maybe it is a request JWS
            parseRequestObjectJws(input)
        } ?: kotlin.runCatching { // maybe it's in the URL parameters
            Url(input).let {
                AuthenticationRequestParametersFrom.Uri(
                    it,
                    it.parameters.flattenEntries().toMap()
                        .decodeFromUrlQuery<AuthenticationRequestParameters>()
                )
            }
        }.onFailure { it.printStackTrace() }.getOrNull()
        ?: kotlin.runCatching {  // maybe it is already a JSON string
            AuthenticationRequestParametersFrom.Json(
                input, AuthenticationRequestParameters.deserialize(input).getOrThrow()
            )
        }.getOrNull() ?: return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
            Napier.w("Could not parse authentication request: $input")
        })

        val extractedParams = parsedParams.let { extractRequestObject(it.parameters) ?: it }
        if (parsedParams.parameters.clientId != null && extractedParams.parameters.clientId != parsedParams.parameters.clientId) {
            return KmmResult.failure<AuthenticationRequestParametersFrom<*>>(OAuth2Exception(Errors.INVALID_REQUEST))
                .also { Napier.w("ClientIds changed: ${parsedParams.parameters.clientId} to ${extractedParams.parameters.clientId}") }
        }
        return KmmResult.success(extractedParams)
    }

    private suspend fun extractRequestObject(params: AuthenticationRequestParameters): AuthenticationRequestParametersFrom<*>? =
        params.request?.let { requestObject ->
            parseRequestObjectJws(requestObject)
        } ?: params.requestUri?.let { uri ->
            remoteResourceRetriever?.invoke(uri)
                ?.let { parseAuthenticationRequestParameters(it).getOrNull() }
        }

    private fun parseRequestObjectJws(requestObject: String): AuthenticationRequestParametersFrom.JwsSigned? {
        return JwsSigned.parse(requestObject).getOrNull()?.let { jws ->
            val params = AuthenticationRequestParameters.deserialize(jws.payload.decodeToString())
                .getOrElse {
                    Napier.w("parseRequestObjectJws: Deserialization failed", it)
                    return null
                }
            if (requestObjectJwsVerifier?.invoke(jws, params) != false) {
                AuthenticationRequestParametersFrom.JwsSigned(
                    jwsSigned = jws, parameters = params
                )
            } else null.also { Napier.w("parseRequestObjectJws: Signature not verified for $jws") }
        }
    }

    /**
     * Pass in the URL sent by the Verifier (containing the [AuthenticationRequestParameters] as query parameters),
     * to create [AuthenticationResponsePreparationHelper] for preparing a response.
     */
    suspend fun startAuthenticationResponsePreparation(
        input: String,
    ): KmmResult<AuthenticationResponsePreparationHelper> = startAuthenticationResponsePreparation(
        request = parseAuthenticationRequestParameters(input).getOrElse {
            return KmmResult.failure<AuthenticationResponsePreparationHelper>(it)
                .also { Napier.w("Could not parse authentication request: $input") }
        },
    )

    suspend fun startAuthenticationResponsePreparation(
        request: AuthenticationRequestParametersFrom<*>,
    ): KmmResult<AuthenticationResponsePreparationHelper> {
        val nonce = request.parameters.nonce ?: run {
            return KmmResult.failure<AuthenticationResponsePreparationHelper>(OAuth2Exception(Errors.INVALID_REQUEST))
                .also { Napier.w("nonce is null") }
        }

        val responseType = request.parameters.responseType ?: run {
            return KmmResult.failure<AuthenticationResponsePreparationHelper>(OAuth2Exception(Errors.INVALID_REQUEST))
                .also { Napier.w("response_type is not specified") }
        }

        if (!responseType.contains(ID_TOKEN) && !responseType.contains(VP_TOKEN)) {
            return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                Napier.w("response_type is not supported")
            })
        }

        val responseModeParameters: ResponseModeParameters =
            ResponseModeParametersFactory(request.parameters).createResponseModeParameters()
                .getOrElse {
                    return KmmResult.failure(it)
                }

        val clientIdScheme = request.parameters.clientIdScheme
        if (clientIdScheme == OpenIdConstants.ClientIdScheme.REDIRECT_URI) {
            if (request.parameters.clientMetadata == null && request.parameters.clientMetadataUri == null) {
                return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                    Napier.w("client_id_scheme is redirect_uri, but metadata is not set")
                })
            }
        }

        val clientIdSchemeParameters =
            ClientIdSchemeParametersFactory(request).createClientIdSchemeParameters().getOrElse {
                return KmmResult.failure(it)
            }

        // TODO Check removed for EUDI interop
//        if (clientMetadata.subjectSyntaxTypesSupported == null || URN_TYPE_JWK_THUMBPRINT !in clientMetadata.subjectSyntaxTypesSupported) {
//            return KmmResult.failure<AuthenticationResponseBuilder>(OAuth2Exception(Errors.SUBJECT_SYNTAX_TYPES_NOT_SUPPORTED))
//                .also { Napier.w("Incompatible subject syntax types algorithms") }
//        }

        val clientMetadata = retrieveClientMetadata(request.parameters) ?: run {
            return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                Napier.w("client metadata is not specified")
            })
        }

        val audience = retrieveAudience(clientMetadata) ?: clientIdSchemeParameters?.let {
            if (it is ClientIdSchemeParameters.X509ClientIdSchemeParameters) {
                request.parameters.clientId
            } else null
        } ?: return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
            Napier.w("Could not parse audience")
        })


        val presentationDefinition = retrievePresentationDefinition(request.parameters)
        if (!request.parameters.responseType.contains(VP_TOKEN) && presentationDefinition == null) {
            return KmmResult.failure<AuthenticationResponsePreparationHelper>(
                OAuth2Exception(
                    Errors.INVALID_REQUEST
                )
            ).also { Napier.w("vp_token not requested") }
        }

        return KmmResult.success(
            AuthenticationResponsePreparationHelper(
                parameters = request.parameters,
                responseType = responseType,
                responseModeParameters = responseModeParameters,
                clientIdSchemeParameters = clientIdSchemeParameters,
                clientMetadata = clientMetadata,
                audience = audience,
                nonce = nonce,
                presentationPreparationHelper = presentationDefinition?.let {
                    PresentationPreparationHelper(
                        presentationDefinition = presentationDefinition,
                        fallbackFormatHolder = clientMetadata.vpFormats
                    ).also {
                        try {
                            refreshPresentationPreparationHelper(it)
                        } catch (e: Throwable) {
                            return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                                e.message?.let { Napier.w(it) }
                            })
                        }
                    }
                },
            )
        )
    }

    /**
     * Users of the library need to call this method in case the stored credentials change.
     */
    suspend fun refreshPresentationPreparationHelper(presentationPreparationHelper: PresentationPreparationHelper) {
        presentationPreparationHelper.refreshInputDescriptorMatches(
            holder = holder,
            pathAuthorizationValidator = pathAuthorizationValidator,
        )
    }

    suspend fun finalizeAuthenticationResponseResult(
        authenticationResponsePreparationHelper: AuthenticationResponsePreparationHelper,
    ): KmmResult<AuthenticationResponseResult> {
        val responseParams = finalizeAuthenticationResponseParameters(
            authenticationResponsePreparationHelper,
        ).getOrElse {
            return KmmResult.failure(it)
        }

        return AuthenticationResponseResultFactory(
            responseParameters = responseParams,
            responseModeParameters = authenticationResponsePreparationHelper.responseModeParameters
        ).createAuthenticationResponseResult()
    }

    inner class AuthenticationResponseResultFactory(
        val responseModeParameters: ResponseModeParameters,
        val responseParameters: AuthenticationResponseParameters,
    ) {
        suspend fun createAuthenticationResponseResult(): KmmResult<AuthenticationResponseResult> {
            return when (responseModeParameters) {
                is ResponseModeParameters.DirectPost -> KmmResult.success(
                    AuthenticationResponseResult.Post(
                        url = responseModeParameters.responseUrl,
                        params = responseParameters.encodeToParameters(),
                    )
                )

                is ResponseModeParameters.DirectPostJwt -> KmmResult.runCatching {
                    authnResponseDirectPostJwt(
                        responseUrl = responseModeParameters.responseUrl,
                        responseParams = responseParameters,
                    )
                }.wrap()

                is ResponseModeParameters.Query -> KmmResult.runCatching {
                    authnResponseQuery(
                        redirectUrl = responseModeParameters.redirectUrl,
                        responseParams = responseParameters,
                    )
                }.wrap()

                is ResponseModeParameters.Fragment -> KmmResult.runCatching {
                    authnResponseFragment(
                        redirectUrl = responseModeParameters.redirectUrl,
                        responseParams = responseParameters,
                    )
                }.wrap()
            }
        }

        private suspend fun authnResponseDirectPostJwt(
            responseUrl: String,
            responseParams: AuthenticationResponseParameters,
        ): AuthenticationResponseResult.Post {
            val responseParamsJws = jwsService.createSignedJwsAddingParams(
                payload = responseParams.serialize().encodeToByteArray(),
                addX5c = false,
            ).getOrElse {
                Napier.w("authnResponseDirectPostJwt error", it)
                throw OAuth2Exception(Errors.INVALID_REQUEST)
            }
            val jarm = AuthenticationResponseParameters(response = responseParamsJws.serialize())

            return AuthenticationResponseResult.Post(
                url = responseUrl,
                params = jarm.encodeToParameters(),
            )
        }

        private fun authnResponseQuery(
            redirectUrl: String,
            responseParams: AuthenticationResponseParameters,
        ): AuthenticationResponseResult.Redirect {
            val url = URLBuilder(redirectUrl).apply {
                responseParams.encodeToParameters().forEach {
                    this.parameters.append(it.key, it.value)
                }
            }.buildString()

            return AuthenticationResponseResult.Redirect(
                url = url,
                params = responseParams,
            )
        }

        /**
         * That's the default for `id_token` and `vp_token`
         */
        private fun authnResponseFragment(
            redirectUrl: String, responseParams: AuthenticationResponseParameters
        ): AuthenticationResponseResult.Redirect {
            val url = URLBuilder(redirectUrl).apply {
                encodedFragment = responseParams.encodeToParameters().formUrlEncode()
            }.buildString()
            return AuthenticationResponseResult.Redirect(url, responseParams)
        }
    }

    internal suspend fun finalizeAuthenticationResponseParameters(
        authenticationResponsePreparationHelper: AuthenticationResponsePreparationHelper,
    ): KmmResult<AuthenticationResponseParameters> {
        val signedIdToken =
            if (!authenticationResponsePreparationHelper.responseType.contains(ID_TOKEN)) {
                null
            } else {
                createIdToken(
                    nonce = authenticationResponsePreparationHelper.nonce,
                    audience = authenticationResponsePreparationHelper.parameters.redirectUrl
                        ?: authenticationResponsePreparationHelper.parameters.clientId,
                ).getOrElse {
                    Napier.w("Could not sign id_token", it)
                    return KmmResult.failure(OAuth2Exception(Errors.USER_CANCELLED))
                }
            }

        val presentationResultContainer: Holder.PresentationResponseParameters? =
            authenticationResponsePreparationHelper.presentationPreparationHelper?.let { it ->
                val credentialSubmissions = it.inputDescriptorMatches.mapValues {
                    // TODO: allow for manual credential selection by the user
                    it.value.firstOrNull()
                        ?: return KmmResult.failure(OAuth2Exception(Errors.USER_CANCELLED).also {
                            Napier.w("submission requirements are not satisfied")
                        })
                }.mapKeys {
                    it.key.id
                }

                if (!it.isSubmissionRequirementsSatisfied(credentialSubmissions.keys)) {
                    Napier.w("submission requirements are not satisfied")
                    return KmmResult.failure(OAuth2Exception(Errors.USER_CANCELLED))
                }
                holder.createPresentation(
                    challenge = authenticationResponsePreparationHelper.nonce,
                    audienceId = authenticationResponsePreparationHelper.audience,
                    presentationDefinitionId = it.presentationDefinitionId,
                    presentationSubmissionSelection = credentialSubmissions,
                ).getOrElse { exception ->
                    Napier.w("Could not create presentation: ${exception.message}")
                    return KmmResult.failure(OAuth2Exception(Errors.USER_CANCELLED))
                }
            }
        presentationResultContainer?.let {
            authenticationResponsePreparationHelper.clientMetadata.vpFormats?.let { supportedFormats ->
                presentationResultContainer.presentationSubmission.descriptorMap?.mapIndexed { index, descriptor ->
                    val isMissingFormatSupport = when (descriptor.format) {
                        ClaimFormatEnum.JWT_VP -> supportedFormats.jwtVp?.algorithms?.contains(
                            jwsService.algorithm.identifier
                        ) != true

                        ClaimFormatEnum.JWT_SD -> supportedFormats.jwtSd?.algorithms?.contains(
                            jwsService.algorithm.identifier
                        ) != true

                        ClaimFormatEnum.MSO_MDOC -> supportedFormats.msoMdoc?.algorithms?.contains(
                            jwsService.algorithm.identifier
                        ) != true

                        else -> true
                    }

                    if (isMissingFormatSupport) {
                        return KmmResult.failure(OAuth2Exception(Errors.REGISTRATION_VALUE_NOT_SUPPORTED).also {
                            Napier.w(
                                "Incompatible JWT algorithms for claim format ${descriptor.format}: $supportedFormats"
                            )
                        })
                    }
                }
            }
        }

        return KmmResult.success(
            AuthenticationResponseParameters(
                idToken = signedIdToken?.serialize(),
                state = authenticationResponsePreparationHelper.parameters.state,
                vpToken = presentationResultContainer?.presentationResults?.map {
                    when (it) {
                        is Holder.CreatePresentationResult.Signed -> {
                            // must be a string
                            // source: https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#appendix-A.1.1.5-1
                            JsonPrimitive(it.jws)
                        }

                        is Holder.CreatePresentationResult.SdJwt -> {
                            // must be a string
                            // source: https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#appendix-A.3.5-1
                            JsonPrimitive(it.sdJwt)
                        }

                        is Holder.CreatePresentationResult.Document -> {
                            // must be a string
                            // source: https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#appendix-A.2.5-1
                            JsonPrimitive(
                                it.document.serialize().encodeToString(Base16(strict = true))
                            )
                        }
                    }
                }?.let {
                    if (it.size == 1) it[0]
                    else buildJsonArray {
                        for (value in it) {
                            add(value)
                        }
                    }
                },
                presentationSubmission = presentationResultContainer?.presentationSubmission,
            ),
        )
    }

    private suspend fun createIdToken(audience: String?, nonce: String): KmmResult<JwsSigned> {
        val now = clock.now()
        // we'll assume jwk-thumbprint
        val agentJsonWebKey = agentPublicKey.toJsonWebKey()
        val idToken = IdToken(
            issuer = agentJsonWebKey.jwkThumbprint,
            subject = agentJsonWebKey.jwkThumbprint,
            subjectJwk = agentJsonWebKey,
            audience = audience ?: agentJsonWebKey.jwkThumbprint,
            issuedAt = now,
            expiration = now + 60.seconds,
            nonce = nonce,
        )
        val jwsPayload = idToken.serialize().encodeToByteArray()
        return jwsService.createSignedJwsAddingParams(payload = jwsPayload)
    }

    class ClientIdSchemeParametersFactory(val request: AuthenticationRequestParametersFrom<*>) {
        fun createClientIdSchemeParameters(): KmmResult<ClientIdSchemeParameters?> {
            return when (request.parameters.clientIdScheme) {
                OpenIdConstants.ClientIdScheme.X509_SAN_DNS -> createX509SanDnsClientIdSchemeParameters()
                OpenIdConstants.ClientIdScheme.X509_SAN_URI -> createX509SanUriClientIdSchemeParameters()

                else -> createOtherClientIdSchemeParameters(request.parameters.clientIdScheme)
            }.getOrElse {
                return KmmResult.failure(it)
            }.let {
                KmmResult.success(it)
            }
        }

        private fun createOtherClientIdSchemeParameters(clientIdScheme: OpenIdConstants.ClientIdScheme?): KmmResult<ClientIdSchemeParameters.OtherClientIdSchemeParameters?> {
            if (request.parameters.redirectUrl != null) {
                if (request.parameters.clientId != request.parameters.redirectUrl) {
                    return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                        Napier.w("client_id does not match redirect_uri")
                    })
                }
            }
            return KmmResult.success(clientIdScheme?.let {
                ClientIdSchemeParameters.OtherClientIdSchemeParameters(clientIdScheme)
            })
        }

        private fun createX509SanDnsClientIdSchemeParameters(): KmmResult<ClientIdSchemeParameters.X509ClientIdSchemeParameters.X509SanUriClientIdSchemeParameters> {
            val result = validateAndRetrieveX509ClientIdSchemeParameters().getOrElse {
                return KmmResult.failure(it)
            }
            return KmmResult.success(
                ClientIdSchemeParameters.X509ClientIdSchemeParameters.X509SanUriClientIdSchemeParameters(
                    leaf = result
                )
            )
        }

        private fun createX509SanUriClientIdSchemeParameters(): KmmResult<ClientIdSchemeParameters.X509ClientIdSchemeParameters.X509SanDnsClientIdSchemeParameters> {
            val result = validateAndRetrieveX509ClientIdSchemeParameters().getOrElse {
                return KmmResult.failure(it)
            }
            return KmmResult.success(
                ClientIdSchemeParameters.X509ClientIdSchemeParameters.X509SanDnsClientIdSchemeParameters(
                    leaf = result
                )
            )
        }

        private fun validateAndRetrieveX509ClientIdSchemeParameters(): KmmResult<X509Certificate> {
            if (request.parameters.clientMetadata == null || request !is AuthenticationRequestParametersFrom.JwsSigned || request.source.header.certificateChain == null || request.source.header.certificateChain?.isEmpty() == true) {
                return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                    Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but metadata is not set and no x5c certificate chain is present in the original authn request")
                })
            }

            val leaf = request.source.header.certificateChain!!.leaf
            if (leaf.tbsCertificate.extensions == null || leaf.tbsCertificate.extensions?.isEmpty() == true ) {
                return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                    Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but no extensions were found in the leaf certificate")
                })
            }
            if (request.parameters.clientIdScheme == OpenIdConstants.ClientIdScheme.X509_SAN_DNS) {
                val dnsNames = leaf.tbsCertificate.subjectAlternativeNames?.dnsNames ?: run {
                    return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                        Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but no dnsNames were found in the leaf certificate")
                    })
                }

                if (!dnsNames.contains(request.parameters.clientId)) {
                    return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                        Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but client_id does not match any dnsName in the leaf certificate")
                    })
                }

                if (request.parameters.responseMode != DIRECT_POST && request.parameters.responseMode != DIRECT_POST_JWT) {
                    val parsedUrl = request.parameters.redirectUrl?.let { Url(it) } ?: run {
                        return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                            Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but no redirect_url was provided")
                        })
                    }

                    //TODO  If the Wallet can establish trust in the Client Identifier authenticated through the certificate it may allow the client to freely choose the redirect_uri value
                    if (parsedUrl.host != request.parameters.clientId) {
                        return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                            Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but no redirect_url was provided")
                        })
                    }
                }
            } else {
                val uris = leaf.tbsCertificate.subjectAlternativeNames?.uris ?: run {
                    return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                        Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but no URIs were found in the leaf certificate")
                    })
                }
                if (!uris.contains(request.parameters.clientId)) {
                    return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                        Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but client_id does not match any URIs in the leaf certificate")
                    })
                }

                if (request.parameters.clientId != request.parameters.redirectUrl) {
                    return KmmResult.failure(OAuth2Exception(Errors.INVALID_REQUEST).also {
                        Napier.w("client_id_scheme is ${request.parameters.clientIdScheme}, but client_id does not match redirect_uri")
                    })
                }
            }
            return KmmResult.success(leaf)
        }
    }


    private suspend fun retrieveClientMetadata(params: AuthenticationRequestParameters): RelyingPartyMetadata? {
        return params.clientMetadata ?: params.clientMetadataUri?.let { uri ->
            remoteResourceRetriever?.invoke(uri)
                ?.let { RelyingPartyMetadata.deserialize(it).getOrNull() }
        }
    }

    private suspend fun retrieveAudience(
        clientMetadata: RelyingPartyMetadata,
    ): String? {
        return clientMetadata.jsonWebKeySet?.keys?.firstOrNull()?.identifier
            ?: clientMetadata.jsonWebKeySetUrl?.let {
                remoteResourceRetriever?.invoke(it)?.let {
                    JsonWebKeySet.deserialize(it).getOrNull()
                }?.keys?.firstOrNull()?.identifier
            }
    }

    private suspend fun retrievePresentationDefinition(params: AuthenticationRequestParameters): PresentationDefinition? {
        return params.presentationDefinition ?: params.presentationDefinitionUrl?.let {
            remoteResourceRetriever?.invoke(it)
        }?.let {
            PresentationDefinition.deserialize(it).getOrNull()
        } ?: params.scope?.split(" ")?.firstNotNullOfOrNull {
            scopePresentationDefinitionRetriever?.invoke(it)
        }
    }
}

/**
 * Implementations need to fetch the url passed in, and return either the body, if there is one,
 * or the HTTP header `Location`, i.e. if the server sends the request object as a redirect.
 */
typealias RemoteResourceRetrieverFunction = suspend (String) -> String?

/**
 * Implementations need to match a scope value to a [PresentationDefinition] if a related
 * presentation definition is known.
 */
typealias ScopePresentationDefinitionRetriever = suspend (String) -> PresentationDefinition?

/**
 * Implementations need to verify the passed [JwsSigned] and return its result
 */
fun interface RequestObjectJwsVerifier {
    operator fun invoke(jws: JwsSigned, authnRequest: AuthenticationRequestParameters): Boolean
}
