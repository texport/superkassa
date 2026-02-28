package kz.mybrain.superkassa.storage.application.session

import java.sql.Connection

/**
 * Фабрика сессий, отделяет способ подключения от доменных контрактов.
 *
 * Зачем это нужно:
 * - JDBC подходит для серверов/desktop,
 * - на мобильных (Android/Kotlin) обычно нет JDBC,
 * - интегратор может подменить реализацию хранения.
 *
 * Поэтому фабрика позволяет дать свой способ открытия StorageSession.
 */
interface StorageSessionFactory {
    fun open(connection: Connection): StorageSession
}
