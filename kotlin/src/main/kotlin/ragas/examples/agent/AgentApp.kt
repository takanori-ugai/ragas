package ragas.examples.agent

import java.io.File
import kotlin.math.abs

data class AgentResult(
    val result: Double,
    val logFile: String,
)

/**
 * Minimal agent app for tutorial usage.
 */
fun main() {
    val expression = "(2 + 3) * (4 - 1)"
    val mathAgent = MathToolsAgent(logDir = "logs")
    val solved = mathAgent.solve(expression)
    println("Expression: $expression")
    println("Result: ${solved.result}")
    println("Log file: ${solved.logFile}")
}

internal class MathToolsAgent(
    private val logDir: String = "logs",
) {
    init {
        File(logDir).mkdirs()
    }

    fun solve(expression: String): AgentResult {
        val steps = mutableListOf<String>()
        val parser = ExpressionParser(expression, this, steps)
        val result = parser.parse()
        val logFile = File(logDir, "agent-${System.currentTimeMillis()}.log")
        logFile.writeText(steps.joinToString("\n"))
        return AgentResult(result = result, logFile = logFile.absolutePath)
    }

    fun add(
        left: Double,
        right: Double,
        steps: MutableList<String>,
    ): Double {
        val value = left + right
        steps += "add($left, $right) -> $value"
        return value
    }

    fun sub(
        left: Double,
        right: Double,
        steps: MutableList<String>,
    ): Double {
        val value = left - right
        steps += "sub($left, $right) -> $value"
        return value
    }

    fun mul(
        left: Double,
        right: Double,
        steps: MutableList<String>,
    ): Double {
        val value = left * right
        steps += "mul($left, $right) -> $value"
        return value
    }

    fun div(
        left: Double,
        right: Double,
        steps: MutableList<String>,
    ): Double {
        val value = left / right
        steps += "div($left, $right) -> $value"
        return value
    }
}

private class ExpressionParser(
    expression: String,
    private val tools: MathToolsAgent,
    private val steps: MutableList<String>,
) {
    private val tokens =
        expression
            .replace(" ", "")
            .toCharArray()
    private var index = 0

    fun parse(): Double {
        val value = parseExpression()
        require(index == tokens.size) { "Unexpected trailing input at position $index" }
        return value
    }

    private fun parseExpression(): Double {
        var value = parseTerm()
        while (peek() == '+' || peek() == '-') {
            val op = next()
            val right = parseTerm()
            value =
                if (op == '+') {
                    tools.add(value, right, steps)
                } else {
                    tools.sub(value, right, steps)
                }
        }
        return value
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (peek() == '*' || peek() == '/') {
            val op = next()
            val right = parseFactor()
            value =
                if (op == '*') {
                    tools.mul(value, right, steps)
                } else {
                    tools.div(value, right, steps)
                }
        }
        return value
    }

    private fun parseFactor(): Double =
        when {
            peek() == '(' -> {
                next()
                val value = parseExpression()
                expect(')')
                value
            }

            peek() == '-' -> {
                next()
                -parseFactor()
            }

            else -> {
                parseNumber()
            }
        }

    private fun parseNumber(): Double {
        val start = index
        while (peek()?.isDigit() == true || peek() == '.') {
            next()
        }
        require(index > start) { "Expected number at position $index" }
        return tokens.concatToString(start, index).toDouble()
    }

    private fun expect(char: Char) {
        require(next() == char) { "Expected '$char' at position $index" }
    }

    private fun next(): Char {
        require(index < tokens.size) { "Unexpected end of input" }
        return tokens[index++]
    }

    private fun peek(): Char? = tokens.getOrNull(index)
}

internal fun isCorrect(
    predicted: Double,
    expected: Double,
): Boolean = abs(predicted - expected) < 1e-5
