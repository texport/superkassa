package kz.mybrain.superkassa.core.application.common

import java.net.InetAddress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kz.mybrain.superkassa.core.application.model.CoreSettings
import kz.mybrain.superkassa.core.domain.model.KkmState
import kz.mybrain.superkassa.core.domain.port.StoragePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

@Component
class ConsoleLoader(
    private val env: Environment,
    @Autowired(required = false) private val storage: StoragePort? = null,
    @Autowired(required = false) private val coreSettings: CoreSettings? = null
) : ApplicationListener<ApplicationReadyEvent> {

    private val log = LoggerFactory.getLogger(ConsoleLoader::class.java)

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val serverPort = env.getProperty("server.port")
        val contextPath = env.getProperty("server.servlet.context-path") ?: "/"
        val protocol = "http"
        val hostAddress =
                try {
                    InetAddress.getLocalHost().hostAddress
                } catch (e: Exception) {
                    "localhost"
                }

        val banner =
                """
   _____                       __ __                          
  / ___/__  ______  ___  _____/ //_/__  ______________ _    
  \__ \/ / / / __ \/ _ \/ ___/ ,< / _ \/ ___/ ___/ __ `/    
 ___/ / /_/ / /_/ /  __/ /  / /| /  __(__  |__  ) /_/ /     
/____/\__,_/ .___/\___/_/  /_/ |_\___/____/____/\__,_/      
          /_/                                               
        """

        println(ANSI_CYAN + banner + ANSI_RESET)

        val stats = gatherStartupStats()
        val startedAt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        val javaVersion = System.getProperty("java.version") ?: "—"
        val timezone = ZoneId.systemDefault().id
        val profiles = env.getProperty("spring.profiles.active")?.takeIf { it.isNotBlank() } ?: "default"
        val storageEngine = coreSettings?.storage?.engine ?: "—"
        val ofdVersion = coreSettings?.ofdProtocolVersion ?: "—"
        val deliverySummary = formatDeliverySummary()
        val (loggingLevel, loggingImpl) = getLoggingInfo()

        val lines = mutableListOf<String>()
        lines.add(" Приложение успешно запущено! ")
        lines.add(" Статус:      " + ANSI_BOLD + "Работает" + ANSI_RESET)
        lines.add(" ----------------------------------------")
        lines.add(" Время запуска: $startedAt")
        lines.add(" Java: $javaVersion | TZ: $timezone")
        lines.add(" Профили: $profiles")
        lines.add(" Логирование: $loggingLevel | фасад: $loggingImpl")
        lines.add(" ----------------------------------------")
        lines.add(" База данных: ${stats.dbStatus}")
        lines.add(" Движок БД: $storageEngine")
        lines.add(" ----------------------------------------")
        lines.add(" ККМ всего: ${stats.kkmTotal}")
        lines.add(" ККМ по статусам: ${stats.kkmByStatus}")
        lines.add(" Открытых смен: ${stats.openShiftsStr}")
        lines.add(" Всего чеков: ${stats.receiptsTotal}")
        lines.add(" Z-отчётов (закрытых смен): ${stats.closedShiftsTotal}")
        lines.add(" В автономной очереди: ${stats.offlineQueueTotal} транзакций")
        lines.add(" ----------------------------------------")
        lines.add(" ОФД протокол: $ofdVersion")
        lines.add(deliverySummary)
        if (coreSettings != null) {
            lines.add(" Режим: ${coreSettings.mode.name} | узел: ${coreSettings.nodeId}")
        }
        lines.add(" ----------------------------------------")
        lines.add(" Локально:    $protocol://localhost:$serverPort$contextPath")
        lines.add(" В сети:      $protocol://$hostAddress:$serverPort$contextPath")
        lines.add(" Swagger UI:  $protocol://localhost:$serverPort/swagger")
        lines.add(" API Docs:    $protocol://localhost:$serverPort/v3/api-docs")

        val plainLengths = lines.map { visibleLength(it) }
        val innerWidth = maxOf(MIN_BOX_WIDTH, plainLengths.maxOrNull() ?: MIN_BOX_WIDTH)

        fun border(char: Char) = ANSI_GREEN + char + "═".repeat(innerWidth) + char + ANSI_RESET
        fun row(content: String, plainLen: Int) =
                ANSI_GREEN + "║" + ANSI_RESET +
                        content + " ".repeat((innerWidth - plainLen).coerceAtLeast(0)) +
                        ANSI_GREEN + "║" + ANSI_RESET

        println(border('╔'))
        println(row(lines[0], plainLengths[0]))
        println(border('╠'))
        (1 until lines.size).forEach { i -> println(row(lines[i], plainLengths[i])) }
        println(border('╚'))
    }

    private fun formatDeliverySummary(): String {
        val d = coreSettings?.delivery ?: return " Доставка чеков: не настроена"
        val printOn = d.print?.enabled == true
        val channelCount = d.channels.count { it.enabled }
        val parts = mutableListOf<String>()
        if (printOn) parts.add("печать")
        if (channelCount > 0) parts.add("каналов: $channelCount")
        return " Доставка чеков: " + if (parts.isEmpty()) "выкл" else parts.joinToString(", ")
    }

    private fun gatherStartupStats(): StartupStats {
        if (storage == null) {
            return StartupStats(
                dbStatus = "не проверялась (storage не подключён)",
                kkmTotal = "—",
                kkmByStatus = "—",
                openShiftsStr = "—",
                receiptsTotal = "—",
                closedShiftsTotal = "—",
                offlineQueueTotal = "—"
            )
        }
        return try {
            val total = storage.countKkms(state = null, search = null)
            val byStatus = KkmState.entries.joinToString(", ") { state ->
                "${state.name}: ${storage.countKkms(state = state.name, search = null)}"
            }
            val openShiftsCount = countOpenShifts(storage, total)
            val openShiftsStr = if (openShiftsCount >= 0) openShiftsCount.toString() else "—"
            val receipts = storage.countFiscalDocuments("CHECK")
            val closedShifts = storage.countClosedShifts()
            val offlineQueue = storage.countOfflineQueue()
            StartupStats(
                dbStatus = "подключена",
                kkmTotal = total.toString(),
                kkmByStatus = byStatus,
                openShiftsStr = openShiftsStr,
                receiptsTotal = receipts.toString(),
                closedShiftsTotal = closedShifts.toString(),
                offlineQueueTotal = offlineQueue.toString()
            )
        } catch (e: Exception) {
            log.warn("Startup stats failed", e)
            StartupStats(
                dbStatus = "ошибка при проверке",
                kkmTotal = "—",
                kkmByStatus = "—",
                openShiftsStr = "—",
                receiptsTotal = "—",
                closedShiftsTotal = "—",
                offlineQueueTotal = "—"
            )
        }
    }

    private fun getLoggingInfo(): Pair<String, String> {
        val level = env.getProperty("logging.level.root")
            ?: env.getProperty("logging.level.kz.mybrain")
            ?: "—"
        val impl = try {
            LoggerFactory.getILoggerFactory().javaClass.simpleName
        } catch (_: Exception) {
            "—"
        }
        return level to impl
    }

    private fun countOpenShifts(storage: StoragePort, kkmLimit: Int): Int {
        if (kkmLimit <= 0) return 0
        return try {
            val kkms = storage.listKkms(limit = minOf(kkmLimit, 500), offset = 0, state = null, search = null)
            kkms.count { storage.findOpenShift(it.id) != null }
        } catch (e: Exception) {
            log.debug("Open shifts count failed", e)
            -1
        }
    }

    /** Длина строки без ANSI-кодов (для выравнивания рамки). */
    private fun visibleLength(s: String): Int = stripAnsi(s).length

    /** Удаляет все ANSI CSI-последовательности (например \u001B[1m, \u001B[0;32m). */
    private fun stripAnsi(s: String): String = s.replace(Regex("\u001B\\[[0-9;?]*[a-zA-Z]"), "")

    private data class StartupStats(
        val dbStatus: String,
        val kkmTotal: String,
        val kkmByStatus: String,
        val openShiftsStr: String,
        val receiptsTotal: String,
        val closedShiftsTotal: String,
        val offlineQueueTotal: String
    )

    companion object {
        private const val MIN_BOX_WIDTH = 72

        const val ANSI_RESET = "\u001B[0m"
        const val ANSI_GREEN = "\u001B[32m"
        const val ANSI_CYAN = "\u001B[36m"
        const val ANSI_BOLD = "\u001B[1m"
    }
}
