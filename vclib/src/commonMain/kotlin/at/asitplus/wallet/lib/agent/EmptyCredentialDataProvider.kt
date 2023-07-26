package at.asitplus.wallet.lib.agent

import at.asitplus.KmmResult

object EmptyCredentialDataProvider : IssuerCredentialDataProvider {

    override fun getCredentialWithType(
        subjectId: String,
        attributeTypes: Collection<String>
    ): KmmResult<List<CredentialToBeIssued>> =
        KmmResult.failure(NotImplementedError())

}