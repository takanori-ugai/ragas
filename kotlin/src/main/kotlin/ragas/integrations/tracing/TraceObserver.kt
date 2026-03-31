package ragas.integrations.tracing

fun interface TraceObserver {
    fun onEvent(event: TraceEvent)
}

class InMemoryTraceObserver : TraceObserver {
    private val _events = mutableListOf<TraceEvent>()

    val events: List<TraceEvent>
        get() = _events.toList()

    override fun onEvent(event: TraceEvent) {
        _events += event
    }
}
