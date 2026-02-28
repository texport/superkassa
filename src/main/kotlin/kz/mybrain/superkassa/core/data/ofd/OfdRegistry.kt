package kz.mybrain.superkassa.core.data.ofd

data class OfdEndpoint(val host: String, val port: Int)

enum class OfdEnvironment(val id: String) {
    DEV("DEV"),
    TEST("TEST"),
    PROD("PROD")
}

enum class OfdProvider(val id: String, val endpoints: Map<OfdEnvironment, OfdEndpoint>) {
    KAZAKHTELECOM(
        id = "KAZAKHTELECOM",
        endpoints = mapOf(
            OfdEnvironment.TEST to OfdEndpoint(host = "37.150.215.187", port = 7777)
        )
    )
}

object OfdRegistry {
    fun findProvider(providerId: String): OfdProvider? =
        OfdProvider.values().firstOrNull { it.id.equals(providerId, ignoreCase = true) }

    fun findEnvironment(environmentId: String): OfdEnvironment? =
        OfdEnvironment.values().firstOrNull { it.id.equals(environmentId, ignoreCase = true) }

    fun findEndpoint(provider: OfdProvider, environment: OfdEnvironment): OfdEndpoint? =
        provider.endpoints[environment]

    fun defaultProviderId(): String = OfdProvider.KAZAKHTELECOM.id

    fun defaultEnvironmentId(): String = OfdEnvironment.TEST.id

    fun normalizeProviderId(providerId: String): String = providerId.trim().uppercase()

    fun normalizeEnvironmentId(environmentId: String): String = environmentId.trim().uppercase()

    fun formatTag(providerId: String, environmentId: String): String =
        "${normalizeProviderId(providerId)}:${normalizeEnvironmentId(environmentId)}"
}
