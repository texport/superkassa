package kz.mybrain.superkassa.core

import kz.mybrain.superkassa.core.application.policy.DefaultCounterUpdater
import kz.mybrain.superkassa.core.application.policy.CounterScopes
import kz.mybrain.superkassa.core.domain.model.CounterSnapshot
import kz.mybrain.superkassa.core.domain.model.FiscalDocumentSnapshot
import kz.mybrain.superkassa.core.domain.model.KkmInfo
import kz.mybrain.superkassa.core.domain.model.KkmUser
import kz.mybrain.superkassa.core.domain.model.Money
import kz.mybrain.superkassa.core.domain.model.PaymentType
import kz.mybrain.superkassa.core.domain.model.ReceiptItem
import kz.mybrain.superkassa.core.domain.model.ReceiptOperationType
import kz.mybrain.superkassa.core.domain.model.ReceiptPayment
import kz.mybrain.superkassa.core.domain.model.ReceiptRequest
import kz.mybrain.superkassa.core.domain.model.ShiftInfo
import kz.mybrain.superkassa.core.domain.model.ShiftStatus
import kz.mybrain.superkassa.core.domain.model.UserRole
import kz.mybrain.superkassa.core.domain.port.StoragePort
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterUpdaterTest {
    @Test
    fun shouldUpdateShiftAndGlobalCounters() {
        val storage = InMemoryStoragePort()
        val updater = DefaultCounterUpdater(storage)
        val request = ReceiptRequest(
            kkmId = "kkm-1",
            pin = "1111",
            operation = ReceiptOperationType.SELL,
            items = listOf(ReceiptItem("Item", "1", 1, Money(1000, 0), Money(1000, 0))),
            payments = listOf(ReceiptPayment(PaymentType.CASH, Money(1000, 0))),
            total = Money(1000, 0),
            idempotencyKey = "key-1"
        )

        updater.updateForReceipt("kkm-1", "shift-1", request, isOffline = false)

        val shift = storage.loadCounters("kkm-1", CounterScopes.SHIFT, "shift-1")
        val global = storage.loadCounters("kkm-1", CounterScopes.GLOBAL, null)

        assertEquals(1L, shift["operation.OPERATION_SELL.count"])
        assertEquals(1000L, shift["operation.OPERATION_SELL.sum"])
        assertEquals(1L, global["operation.OPERATION_SELL.count"])
        assertEquals(1000L, global["operation.OPERATION_SELL.sum"])
    }
}

private class InMemoryStoragePort : StoragePort {
    private val counters = mutableMapOf<String, MutableMap<String, Long>>()
    private val kkms = mutableMapOf<String, KkmInfo>()
    private val users = mutableMapOf<String, MutableList<KkmUser>>()

    override fun <T> inTransaction(block: () -> T): T = block()

    override fun createKkm(info: KkmInfo): Boolean {
        kkms[info.id] = info
        return true
    }

    override fun updateKkm(info: KkmInfo): Boolean {
        kkms[info.id] = info
        return true
    }

    override fun findKkm(id: String): KkmInfo? = kkms[id]

    override fun findKkmByRegistrationNumber(registrationNumber: String): KkmInfo? = null

    override fun findKkmBySystemId(systemId: String): KkmInfo? = null

    override fun listKkms(
        limit: Int,
        offset: Int,
        state: String?,
        search: String?,
        sortBy: String,
        sortOrder: String
    ): List<KkmInfo> {
        var filtered = kkms.values.asSequence()
        if (state != null) filtered = filtered.filter { it.state == state }
        if (search != null) {
            filtered = filtered.filter {
                it.registrationNumber?.contains(search, ignoreCase = true) == true ||
                it.id.contains(search, ignoreCase = true)
            }
        }
        return filtered.drop(offset).take(limit).toList()
    }

    override fun countKkms(state: String?, search: String?): Int {
        var filtered = kkms.values.asSequence()
        if (state != null) filtered = filtered.filter { it.state == state }
        if (search != null) {
            filtered = filtered.filter {
                it.registrationNumber?.contains(search, ignoreCase = true) == true ||
                it.id.contains(search, ignoreCase = true)
            }
        }
        return filtered.count()
    }

    override fun deleteKkm(id: String): Boolean = kkms.remove(id) != null

    override fun hasOfflineQueue(kkmId: String): Boolean = false

    override fun deleteKkmCompletely(kkmId: String): Boolean {
        kkms.remove(kkmId)
        users.remove(kkmId)
        return true
    }

    override fun updateKkmToken(id: String, tokenEncryptedBase64: String, updatedAt: Long): Boolean {
        val current = kkms[id] ?: return false
        kkms[id] = current.copy(
            tokenEncryptedBase64 = tokenEncryptedBase64,
            tokenUpdatedAt = updatedAt,
            updatedAt = updatedAt
        )
        return true
    }

    override fun listUsers(kkmId: String): List<KkmUser> {
        return users[kkmId]?.toList() ?: emptyList()
    }

    override fun createUser(
        kkmId: String,
        userId: String,
        name: String,
        role: UserRole,
        pin: String,
        pinHash: String,
        createdAt: Long
    ): Boolean {
        val list = users.getOrPut(kkmId) { mutableListOf() }
        list.add(KkmUser(userId, name, role, pin, createdAt))
        return true
    }

    override fun updateUser(
        kkmId: String,
        userId: String,
        name: String?,
        role: UserRole?,
        pin: String?,
        pinHash: String?
    ): Boolean {
        val list = users[kkmId] ?: return false
        val index = list.indexOfFirst { it.id == userId }
        if (index == -1) return false
        val current = list[index]
        list[index] = current.copy(
            name = name ?: current.name,
            role = role ?: current.role,
            pin = pin ?: current.pin
        )
        return true
    }

    override fun deleteUser(kkmId: String, userId: String): Boolean {
        val list = users[kkmId] ?: return false
        return list.removeIf { it.id == userId }
    }

    override fun findUserById(kkmId: String, userId: String): KkmUser? {
        return users[kkmId]?.firstOrNull { it.id == userId }
    }

    override fun findUserByPin(kkmId: String, pinHash: String): KkmUser? = null

    override fun findOpenShift(kkmId: String): ShiftInfo? = null

    override fun findShiftById(shiftId: String): ShiftInfo? = null

    override fun listShifts(kkmId: String, limit: Int, offset: Int): List<ShiftInfo> = emptyList()

    override fun createShift(shift: ShiftInfo): Boolean = true

    override fun closeShift(shiftId: String, status: ShiftStatus, closedAt: Long, closeDocumentId: String?): Boolean = true

    override fun saveReceipt(request: ReceiptRequest, documentId: String, shiftId: String, createdAt: Long): Boolean = true

    override fun saveCashOperation(
        kkmId: String,
        type: String,
        amount: Money,
        documentId: String,
        shiftId: String,
        createdAt: Long
    ): Boolean = true

    override fun updateReceiptStatus(
        documentId: String,
        fiscalSign: String?,
        autonomousSign: String?,
        ofdStatus: String,
        deliveredAt: Long?,
        isAutonomous: Boolean?
    ): Boolean = true

    override fun loadCounters(kkmId: String, scope: String, shiftId: String?): Map<String, Long> {
        val key = "$kkmId:$scope:${shiftId ?: "-"}"
        return counters[key] ?: emptyMap()
    }

    override fun listCounters(kkmId: String): List<CounterSnapshot> {
        return counters.flatMap { (mapKey, values) ->
            val parts = mapKey.split(":", limit = 3)
            if (parts.size != 3 || parts[0] != kkmId) {
                emptyList()
            } else {
                val scope = parts[1]
                val shiftId = parts[2].takeIf { it != "-" }
                values.map { (key, value) ->
                    CounterSnapshot(
                        scope = scope,
                        shiftId = shiftId,
                        key = key,
                        value = value,
                        updatedAt = 0L
                    )
                }
            }
        }
    }

    override fun upsertCounter(kkmId: String, scope: String, shiftId: String?, key: String, value: Long): Boolean {
        val mapKey = "$kkmId:$scope:${shiftId ?: "-"}"
        val map = counters.getOrPut(mapKey) { mutableMapOf() }
        map[key] = value
        return true
    }

    override fun insertIdempotency(kkmId: String, idempotencyKey: String, operation: String): Boolean = true

    override fun findIdempotencyResponse(kkmId: String, idempotencyKey: String): String? = null

    override fun updateIdempotencyResponse(kkmId: String, idempotencyKey: String, responseRef: String?): Boolean = true

    override fun findFiscalDocumentById(id: String): FiscalDocumentSnapshot? = null

    override fun findFiscalDocumentWithReceiptPayload(documentId: String): Pair<FiscalDocumentSnapshot, ReceiptRequest>? = null

    override fun listFiscalDocumentsByShift(
        kkmId: String,
        shiftId: String,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentSnapshot> = emptyList()

    override fun listFiscalDocumentsByPeriod(
        kkmId: String,
        fromInclusive: Long,
        toExclusive: Long,
        limit: Int,
        offset: Int
    ): List<FiscalDocumentSnapshot> = emptyList()

    override fun countFiscalDocuments(docType: String?): Long = 0L

    override fun countClosedShifts(): Long = 0L

    override fun countOfflineQueue(): Long = 0L
}
