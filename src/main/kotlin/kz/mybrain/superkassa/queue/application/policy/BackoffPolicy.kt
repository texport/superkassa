package kz.mybrain.superkassa.queue.application.policy

/**
 * Политика задержек между повторными попытками.
 */
interface BackoffPolicy {
    fun nextAttemptAt(now: Long, attempt: Int): Long
}
