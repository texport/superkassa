package kz.mybrain.superkassa.storage.data.jdbc

import java.sql.PreparedStatement
import java.sql.Types

/**
 * Упрощает биндинг nullable-параметров.
 */
internal fun PreparedStatement.bindBytes(index: Int, value: ByteArray?) {
    if (value == null) {
        setNull(index, Types.BLOB)
    } else {
        setBytes(index, value)
    }
}

internal fun PreparedStatement.bindLong(index: Int, value: Long?) {
    if (value == null) {
        setNull(index, Types.BIGINT)
    } else {
        setLong(index, value)
    }
}

internal fun PreparedStatement.bindInt(index: Int, value: Int?) {
    if (value == null) {
        setNull(index, Types.INTEGER)
    } else {
        setInt(index, value)
    }
}

internal fun PreparedStatement.bindString(index: Int, value: String?) {
    if (value == null) {
        setNull(index, Types.VARCHAR)
    } else {
        setString(index, value)
    }
}
