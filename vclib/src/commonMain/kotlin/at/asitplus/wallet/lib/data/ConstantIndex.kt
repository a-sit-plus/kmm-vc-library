package at.asitplus.wallet.lib.data

object ConstantIndex {

    enum class CredentialRepresentation {
        PLAIN_JWT,
        SD_JWT,
        ISO_MDOC,
    }

    interface CredentialScheme {
        /**
         * Schema URL of the credential, used in protocols to map
         * from the requested schema to the internal attribute type used in [at.asitplus.wallet.lib.agent.Issuer]
         * when issuing credentials.
         */
        val schemaUri: String

        /**
         * Name of the subclass of [CredentialSubject] and thus the `type` of the credential,
         * when using [CredentialRepresentation.PLAIN_JWT]. Will not be used for other representations.
         */
        val vcType: String

        /**
         * Type used for `vct` when using [CredentialRepresentation.SD_JWT].
         */
        val sdJwtType: String?
            get() = null

        /**
         * Namespace to use for attributes of this credential type, when using [CredentialRepresentation.ISO_MDOC].
         *
         * From ISO/IEC 18013-5:
         * There is no requirement for the `NameSpace` format. An approach to avoid collisions is to use the
         * following general format: `[Reverse Domain].[Domain Specific Extension]`.
         */
        val isoNamespace: String?
            get() = null

        /**
         * ISO DocType to use for attributes of this credential type, when using [CredentialRepresentation.ISO_MDOC].
         *
         * From ISO/IEC 18013-5:
         * There is no requirement for the `DocType` format. An approach to avoid collisions is to use the
         * following general format: `[Reverse Domain].[Domain Specific Extension]`.
         */
        val isoDocType: String?
            get() = null

        /**
         * List of claims that may be issued separately when requested in format [CredentialRepresentation.SD_JWT]
         * or [CredentialRepresentation.ISO_MDOC].
         */
        val claimNames: Collection<String>
    }

    object AtomicAttribute2023 : CredentialScheme {
        override val schemaUri: String = "https://wallet.a-sit.at/schemas/1.0.0/AtomicAttribute2023.json"
        override val vcType: String = "AtomicAttribute2023"
        override val sdJwtType: String = "AtomicAttribute2023"
        override val isoNamespace: String = "at.a-sit.wallet.atomic-attribute-2023"
        override val isoDocType: String = "at.a-sit.wallet.atomic-attribute-2023.iso"
        override val claimNames: Collection<String> = listOf()
    }

}
