package kz.mybrain.superkassa.storage.application.connector

import kz.mybrain.superkassa.storage.domain.config.StorageEngine

/**
 * Реестр коннекторов, выдающий реализацию по движку.
 *
 * Зачем это нужно:
 * - подключение к SQLite/MySQL/PostgreSQL одной точкой,
 * - возможность подменить набор коннекторов (например, только SQLite).
 */
interface StorageConnectorRegistry {
    fun connectorFor(engine: StorageEngine): StorageConnector
}
