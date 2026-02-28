package kz.mybrain.superkassa.core.data.adapter

import kz.mybrain.superkassa.core.domain.port.PinHasherPort
import java.security.MessageDigest

/**
 * Реализация PinHasherPort с использованием SHA-256.
 */
class Sha256PinHasherPort : PinHasherPort {
    override fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
