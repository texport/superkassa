package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.application.error.ValidationException
import kz.mybrain.superkassa.core.application.error.ErrorMessages
import kz.mybrain.superkassa.core.domain.port.OfdConfigPort
import kz.mybrain.superkassa.core.data.ofd.OfdRegistry

/**
 * Адаптер OfdConfigPort — делегирует в OfdRegistry (data-слой).
 */
class OfdConfigPortAdapter : OfdConfigPort {
    override fun validateAndFormatTag(providerId: String, environmentId: String): String {
        val provider = OfdRegistry.normalizeProviderId(providerId)
        val environment = OfdRegistry.normalizeEnvironmentId(environmentId)
        val providerEntry = OfdRegistry.findProvider(provider)
            ?: throw ValidationException(
                ErrorMessages.ofdProviderUnknown(provider),
                "OFD_PROVIDER_UNKNOWN"
            )
        val environmentEntry = OfdRegistry.findEnvironment(environment)
            ?: throw ValidationException(
                ErrorMessages.ofdEnvironmentUnknown(environment),
                "OFD_ENVIRONMENT_UNKNOWN"
            )
        OfdRegistry.findEndpoint(providerEntry, environmentEntry)
            ?: throw ValidationException(
                ErrorMessages.ofdEnvironmentUnknown(environment),
                "OFD_ENVIRONMENT_UNKNOWN"
            )
        return OfdRegistry.formatTag(provider, environment)
    }

    override fun parseTag(tag: String): Pair<String, String> {
        val parts = tag.split(":")
        if (parts.size != 2) {
            throw ValidationException(
                ErrorMessages.ofdProviderTagInvalid(tag),
                "OFD_PROVIDER_TAG_INVALID"
            )
        }
        val provider = OfdRegistry.normalizeProviderId(parts[0])
        val environment = OfdRegistry.normalizeEnvironmentId(parts[1])
        return provider to environment
    }

    override fun hasEndpoint(providerId: String, environmentId: String): Boolean {
        val provider = OfdRegistry.findProvider(OfdRegistry.normalizeProviderId(providerId)) ?: return false
        val environment = OfdRegistry.findEnvironment(OfdRegistry.normalizeEnvironmentId(environmentId)) ?: return false
        return OfdRegistry.findEndpoint(provider, environment) != null
    }
}
