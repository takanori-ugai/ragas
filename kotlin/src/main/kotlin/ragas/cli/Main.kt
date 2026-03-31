package ragas.cli

import ragas.backends.BACKEND_REGISTRY

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "help", "--help", "-h" -> {
            printHelp()
        }

        "status" -> {
            printStatus()
        }

        "backends" -> {
            printBackends()
        }

        else -> {
            println("Unknown command: ${args.first()}")
            printHelp()
        }
    }
}

private fun printStatus() {
    println("ragas-kotlin status")
    println("- core evaluation: available")
    println("- default metrics: available")
    println("- testset scaffold: available")
    println("- integrations: scaffold only")
}

private fun printBackends() {
    println("available backends:")
    BACKEND_REGISTRY.availableNames().forEach { name ->
        println("- $name")
    }
}

private fun printHelp() {
    println("ragas-kotlin CLI")
    println("usage: ragas <command>")
    println("commands:")
    println("  status    Show conversion/runtime status")
    println("  backends  List registered backends")
}
