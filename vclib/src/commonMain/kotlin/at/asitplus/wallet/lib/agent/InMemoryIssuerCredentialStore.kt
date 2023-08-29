package at.asitplus.wallet.lib.agent

import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.iso.IssuerSignedItem
import at.asitplus.wallet.lib.iso.sha256
import io.matthewnelson.component.encoding.base16.encodeBase16
import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlinx.datetime.Instant


class InMemoryIssuerCredentialStore : IssuerCredentialStore {

    data class Credential(
        val vcId: String,
        val statusListIndex: Long,
        var revoked: Boolean,
        val expirationDate: Instant
    )

    private val map = mutableMapOf<Int, MutableList<Credential>>()

    override fun storeGetNextIndex(
        vcId: String,
        credentialSubject: CredentialSubject,
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int
    ): Long {
        val list = map.getOrPut(timePeriod) { mutableListOf() }
        val newIndex = (list.maxOfOrNull { it.statusListIndex } ?: 0) + 1
        list += Credential(
            vcId = vcId,
            statusListIndex = newIndex,
            revoked = false,
            expirationDate = expirationDate
        )
        return newIndex
    }

    override fun storeGetNextIndex(
        issuerSignedItemList: List<IssuerSignedItem>,
        issuanceDate: Instant,
        expirationDate: Instant,
        timePeriod: Int
    ): Long {
        val list = map.getOrPut(timePeriod) { mutableListOf() }
        val newIndex = (list.maxOfOrNull { it.statusListIndex } ?: 0) + 1
        list += Credential(
            vcId = issuerSignedItemList.toString().encodeToByteArray().sha256().encodeToString(Base16()),
            statusListIndex = newIndex,
            revoked = false,
            expirationDate = expirationDate
        )
        return newIndex
    }

    override fun getRevokedStatusListIndexList(timePeriod: Int): Collection<Long> {
        return  map.getOrPut(timePeriod) { mutableListOf() }.filter { it.revoked }.map { it.statusListIndex }
    }

    override fun revoke(vcId: String, timePeriod: Int): Boolean {
        val entry =  map.getOrPut(timePeriod) { mutableListOf() }.find { it.vcId == vcId } ?: return false
        entry.revoked = true
        return true
    }

}