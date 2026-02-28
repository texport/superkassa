package kz.mybrain.superkassa.core.application.http

import kz.mybrain.superkassa.core.application.model.KkmResponse
import kz.mybrain.superkassa.core.domain.model.KkmInfo

fun KkmInfo.toResponse(): KkmResponse {
    val (ofdId, ofdEnvironment) = splitOfdTag(ofdProvider)
    return KkmResponse(
        kkmId = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mode = mode,
        state = state,
        ofdId = ofdId,
        ofdEnvironment = ofdEnvironment,
        kkmKgdId = registrationNumber,
        factoryNumber = factoryNumber,
        manufactureYear = manufactureYear,
        ofdSystemId = systemId,
        ofdServiceInfo = ofdServiceInfo,
        tokenEncryptedBase64 = tokenEncryptedBase64,
        tokenUpdatedAt = tokenUpdatedAt,
        lastShiftNo = lastShiftNo,
        lastReceiptNo = lastReceiptNo,
        lastZReportNo = lastZReportNo,
        autonomousSince = autonomousSince,
        autoCloseShift = autoCloseShift,
        lastFiscalHashBase64 = lastFiscalHashBase64,
        taxRegime = taxRegime.name,
        defaultVatGroup = defaultVatGroup.name
    )
}

private fun splitOfdTag(tag: String?): Pair<String?, String?> {
    if (tag == null) return null to null
    val parts = tag.split(":")
    return if (parts.size == 2) parts[0] to parts[1] else tag to null
}
