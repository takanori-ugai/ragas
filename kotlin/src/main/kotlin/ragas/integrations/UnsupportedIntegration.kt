package ragas.integrations

fun unsupportedIntegration(name: String): Nothing {
    throw UnsupportedOperationException(
        "Integration '$name' is not yet implemented in ragas-kotlin. Track parity in Plan.md.",
    )
}
