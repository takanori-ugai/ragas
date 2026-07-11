import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm") version "2.4.0"
    application
    id("com.gradleup.shadow") version "9.4.3"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jetbrains.dokka-javadoc") version "2.2.0"
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt.yml"))
}

group = "io.ragas"
version = "0.0.1"

application {
    val requestedMain =
        if (project.hasProperty("mainClass")) {
            project.property("mainClass") as String
        } else {
            null
        }
    mainClass.set(requestedMain ?: "ragas.cli.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.33")
    implementation("com.knuddels:jtokkit:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")
    implementation("com.github.haifengl:smile-core:4.4.2")
    implementation("gg.jte:jte-kotlin:3.2.4")

    // LangChain4j dependencies
    implementation("dev.langchain4j:langchain4j:1.17.1")
    implementation("dev.langchain4j:langchain4j-open-ai:1.17.1")
    implementation("dev.langchain4j:langchain4j-azure-open-ai:1.17.1")
    implementation("dev.langchain4j:langchain4j-ollama:1.17.1")
    implementation("dev.langchain4j:langchain4j-google-genai:1.17.2-beta27")
    implementation("dev.langchain4j:langchain4j-google-ai-gemini:1.17.1")
    implementation("dev.langchain4j:langchain4j-community-neo4j:1.15.0-beta25")
    implementation("com.langchain.smith:langsmith-java:0.1.0-beta.11")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
}

val docsSnippetSourceDir = layout.buildDirectory.dir("generated/docs-snippets/src/main/kotlin")
val docsSnippetRoots =
    listOf(
        "docs/howtos/cli",
        "docs/howtos/applications",
        "docs/howtos/integrations",
    )

val extractDocsKotlinSnippets =
    tasks.register("extractDocsKotlinSnippets") {
        group = "verification"
        description = "Extracts Kotlin fenced code blocks from docs into generated sources."
        inputs.files(
            docsSnippetRoots.map { root ->
                fileTree(root) {
                    include("**/*.md")
                }
            },
        )
        outputs.dir(docsSnippetSourceDir)
        doLast {
            val outDir = docsSnippetSourceDir.get().asFile
            if (outDir.exists()) {
                outDir.deleteRecursively()
            }
            outDir.mkdirs()

            val fenceRegex = Regex("(?s)```kotlin\\s*\\n(.*?)\\n```")
            var snippetIndex = 0
            docsSnippetRoots.forEach { root ->
                val rootDir = file(root)
                if (!rootDir.exists()) {
                    return@forEach
                }
                rootDir
                    .walkTopDown()
                    .filter { entry -> entry.isFile && entry.extension == "md" }
                    .sortedBy { entry -> entry.relativeTo(rootDir).invariantSeparatorsPath }
                    .forEach { markdown ->
                        val text = markdown.readText()
                        fenceRegex.findAll(text).forEach { match ->
                            val snippet = match.groupValues[1].trim()
                            if (snippet.isBlank()) {
                                return@forEach
                            }
                            if (!snippet
                                    .lineSequence()
                                    .first()
                                    .trim()
                                    .startsWith("// @compile")
                            ) {
                                return@forEach
                            }
                            val snippetWithoutMarker =
                                snippet
                                    .lineSequence()
                                    .drop(1)
                                    .joinToString("\n")
                                    .trim()
                            if (snippetWithoutMarker.isBlank()) {
                                return@forEach
                            }
                            val lines = snippetWithoutMarker.lines()
                            val imports = lines.filter { line -> line.trimStart().startsWith("import ") }
                            val bodyLines = lines.filterNot { line -> line.trimStart().startsWith("import ") }
                            snippetIndex += 1
                            val fileName = "DocSnippet${snippetIndex.toString().padStart(4, '0')}.kt"
                            val packageSuffix = snippetIndex.toString().padStart(4, '0')
                            val generated =
                                buildString {
                                    appendLine("@file:Suppress(")
                                    appendLine("    \"ktlint\",")
                                    appendLine("    \"UNUSED_IMPORT\",")
                                    appendLine("    \"UNUSED_VARIABLE\",")
                                    appendLine("    \"UNUSED_PARAMETER\",")
                                    appendLine("    \"UNUSED_EXPRESSION\",")
                                    appendLine("    \"RedundantVisibilityModifier\",")
                                    appendLine(")")
                                    appendLine()
                                    appendLine("package docs.snippets.generated.s$packageSuffix")
                                    appendLine()
                                    appendLine("// Source: ${markdown.relativeTo(projectDir).path.replace('\\', '/')}")
                                    appendLine()
                                    if (imports.isNotEmpty()) {
                                        imports.forEach { importLine -> appendLine(importLine) }
                                        appendLine()
                                    }
                                    appendLine("private suspend fun snippetCompileCheck$packageSuffix() {")
                                    bodyLines.forEach { bodyLine -> appendLine("    $bodyLine") }
                                    appendLine("}")
                                }
                            outDir.resolve(fileName).writeText(generated)
                        }
                    }
            }
            logger.lifecycle("Extracted $snippetIndex Kotlin doc snippets to ${outDir.path}")
        }
    }

val docsSnippets =
    sourceSets.create("docsSnippets") {
        java.srcDir(docsSnippetSourceDir)
        compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
        runtimeClasspath += output + compileClasspath
    }

configurations[docsSnippets.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[docsSnippets.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())

tasks.named("compileDocsSnippetsKotlin") {
    dependsOn(extractDocsKotlinSnippets)
}

tasks.named("runKtlintCheckOverDocsSnippetsSourceSet") {
    dependsOn(extractDocsKotlinSnippets)
}

tasks.named("runKtlintFormatOverDocsSnippetsSourceSet") {
    dependsOn(extractDocsKotlinSnippets)
}

tasks {
    withType<Test> {
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        testLogging {
            events("passed", "skipped", "failed", "standardOut", "standardError")
            showStandardStreams = true
        }
    }

    // Separate task for scriptable/CLI runs; keeps `run` intact for IDE defaults.
    val execute =
        register<JavaExec>("execute") {
            group = "application"
            mainClass.set(application.mainClass)
            classpath = sourceSets.main.get().runtimeClasspath
        }

    shadowJar {
        isZip64 = true
    }

    named("check") {
        dependsOn("compileDocsSnippetsKotlin")
    }
}

ktlint {
    version.set("1.8.0")
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.JSON)
        reporter(ReporterType.HTML)
    }
    filter {
        exclude("**/style-violations.kt")
    }
}
