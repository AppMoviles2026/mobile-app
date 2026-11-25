package com.example.collabpro.features.billing.presentation.sandbox

import com.example.collabpro.features.billing.application.PayPalReturnKind
import java.time.Instant
import java.util.UUID

internal data class BillingOwner(val id: UUID, val expiry: Instant)
internal enum class BillingAction { LINK, SUBSCRIBE, FUND, CONFIRM }

internal data class PendingBillingCommand(
    val owner: BillingOwner, val action: BillingAction, val target: UUID,
    val request: UUID = UUID.randomUUID(), val method: UUID? = null, val plan: UUID? = null,
    val version: Long? = null, val resource: UUID? = null
) {
    fun encode() = arrayListOf(owner.id.toString(), owner.expiry.toString(), action.name, target.toString(),
        request.toString(), method?.toString().orEmpty(), plan?.toString().orEmpty(),
        version?.toString().orEmpty(), resource?.toString().orEmpty())

    companion object {
        fun decode(fields: List<String>?): PendingBillingCommand? = runCatching {
            require(fields?.size == 9)
            val data = fields!!
            PendingBillingCommand(BillingOwner(UUID.fromString(data[0]), Instant.parse(data[1])), BillingAction.valueOf(data[2]),
                UUID.fromString(data[3]), UUID.fromString(data[4]), data.uuidAt(5), data.uuidAt(6),
                data[7].takeIf { it.isNotEmpty() }?.toLong(), data.uuidAt(8))
        }.getOrNull()

        private fun List<String>.uuidAt(index: Int) = this[index].takeIf { it.isNotEmpty() }?.let(UUID::fromString)
    }
}

internal data class BrowserReturnTicket(val owner: BillingOwner, val kind: PayPalReturnKind, val id: UUID) {
    fun encode() = arrayListOf(owner.id.toString(), owner.expiry.toString(), kind.name, id.toString())

    companion object {
        fun decode(fields: List<String>?): BrowserReturnTicket? = runCatching {
            require(fields?.size == 4)
            val data = fields!!
            BrowserReturnTicket(BillingOwner(UUID.fromString(data[0]), Instant.parse(data[1])),
                PayPalReturnKind.valueOf(data[2]), UUID.fromString(data[3]))
        }.getOrNull()
    }
}
