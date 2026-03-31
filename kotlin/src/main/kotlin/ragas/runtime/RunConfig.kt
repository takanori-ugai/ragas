package ragas.runtime

import kotlin.random.Random
import kotlin.reflect.KClass

data class RunConfig(
    val timeoutSeconds: Long = 180,
    val maxRetries: Int = 10,
    val maxWaitSeconds: Long = 60,
    val maxWorkers: Int = 16,
    val retryOn: Set<KClass<out Throwable>> = setOf(Exception::class),
    val logRetries: Boolean = false,
    val seed: Int = 42,
) {
    val random: Random = Random(seed)

    fun shouldRetry(error: Throwable): Boolean = retryOn.any { type -> type.isInstance(error) }
}
