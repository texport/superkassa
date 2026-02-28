package kz.mybrain.superkassa.core.application.service

import kz.mybrain.superkassa.core.application.model.CoreSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Файловое хранилище конфигурации ядра.
 */
class FileCoreSettingsRepository(
    private val path: Path,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }
) : CoreSettingsRepository {
    override fun load(): CoreSettings? {
        if (!Files.exists(path)) return null
        val text = Files.readString(path)
        return json.decodeFromString(CoreSettings.serializer(), text)
    }

    override fun save(settings: CoreSettings): Boolean {
        val text = json.encodeToString(settings)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return true
    }

    override fun loadOrCreate(defaults: CoreSettings): CoreSettings {
        return load() ?: defaults.also { save(it) }
    }
}
