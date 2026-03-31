package ragas.runtime

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

suspend fun <T> retryAsync(
    runConfig: RunConfig,
    block: suspend () -> T,
): T {
    var attempt = 0
    var lastError: Throwable? = null

    while (attempt < runConfig.maxRetries) {
        try {
            return block()
        } catch (error: Throwable) {
            attempt += 1
            lastError = error
            if (!runConfig.shouldRetry(error) || attempt >= runConfig.maxRetries) {
                throw error
            }

            val exponential = 2.0.pow((attempt - 1).toDouble()).toLong()
            val waitSeconds = min(exponential, runConfig.maxWaitSeconds)
            val jitterMillis = runConfig.random.nextLong(0, 1_000)
            delay(waitSeconds * 1_000 + jitterMillis)
        }
    }

    throw lastError ?: IllegalStateException("Retry loop exited without error or result.")
}
