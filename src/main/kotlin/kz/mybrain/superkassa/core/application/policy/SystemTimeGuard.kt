package kz.mybrain.superkassa.core.application.policy

import kz.mybrain.superkassa.core.domain.port.ClockPort
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Проверка системного времени по принципу браузеров:
 * 1) диапазон разумного времени;
 * 2) контроль "скачков" по монотонным часам;
 * 3) сверка с внешним эталоном (HTTP Date).
 */
object SystemTimeGuard {
    private val logger = LoggerFactory.getLogger(SystemTimeGuard::class.java)
    private val minAllowedMs = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        .toInstant().toEpochMilli()
    private val maxAllowedMs = ZonedDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        .toInstant().toEpochMilli()
    private val maxMonotonicSkewMs = 2 * 60 * 1000L
    private val maxReferenceSkewMs = 5 * 60 * 1000L
    private val referenceTtlMs = 10 * 60 * 1000L
    private val referenceUrls = listOf(
        "https://www.cloudflare.com",
        "https://www.google.com",
        "https://www.microsoft.com"
    )

    private val lock = Any()
    private var lastWallMs: Long? = null
    private var lastMonoNs: Long? = null
    private var referenceMs: Long? = null
    private var referenceFetchedAtMs: Long? = null

    data class TimeCheckResult(val ok: Boolean, val reason: String?)

    fun validate(clock: ClockPort): TimeCheckResult {
        val now = clock.now()
        if (now < minAllowedMs || now > maxAllowedMs) {
            return TimeCheckResult(false, "RANGE")
        }
        synchronized(lock) {
            if (lastWallMs != null && lastMonoNs != null) {
                val deltaMonoMs = (System.nanoTime() - lastMonoNs!!) / 1_000_000
                val expected = lastWallMs!! + deltaMonoMs
                val skew = abs(now - expected)
                if (skew > maxMonotonicSkewMs) {
                    return TimeCheckResult(false, "MONOTONIC_SKEW")
                }
            }
            lastWallMs = now
            lastMonoNs = System.nanoTime()
        }
        val reference = ensureReference(now)
        if (reference != null) {
            val skew = abs(now - reference)
            if (skew > maxReferenceSkewMs) {
                return TimeCheckResult(false, "REFERENCE_SKEW")
            }
        }
        return TimeCheckResult(true, null)
    }

    fun logStatus(clock: ClockPort) {
        val result = validate(clock)
        if (result.ok) {
            logger.info("Системное время проверено: ok")
        } else {
            logger.error("Системное время некорректно: причина={}", result.reason ?: "UNKNOWN")
        }
    }

    private fun ensureReference(now: Long): Long? {
        synchronized(lock) {
            val cached = referenceMs
            val fetchedAt = referenceFetchedAtMs
            if (cached != null && fetchedAt != null && now - fetchedAt <= referenceTtlMs) {
                return cached
            }
            val fetched = fetchReferenceTime()
            if (fetched != null) {
                referenceMs = fetched
                referenceFetchedAtMs = now
            }
            return fetched ?: cached
        }
    }

    private fun fetchReferenceTime(): Long? {
        for (url in referenceUrls) {
            runCatching {
                val connection = java.net.URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 1500
                connection.readTimeout = 1500
                connection.connect()
                val dateHeader = connection.getHeaderField("Date")
                connection.disconnect()
                if (!dateHeader.isNullOrBlank()) {
                    val parsed = ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME)
                    return parsed.toInstant().toEpochMilli()
                }
            }.onFailure {
                logger.debug("Не удалось получить эталонное время с {}", url)
            }
        }
        return null
    }
}
