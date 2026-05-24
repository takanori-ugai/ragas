package ragas.prompt

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiModalContentNormalizerTest {
    @Test
    fun fromUntrustedItemParsesValidDataUri() {
        val part = PromptContentPart.fromUntrustedItem(PNG_DATA_URI)
        assertTrue(part is PromptContentPart.ImageDataUri)
        assertTrue(part.dataUri.startsWith("data:image/png;base64,"))
    }

    @Test
    fun fromUntrustedItemFallsBackToTextForUnsafeUrlTarget() {
        val part =
            PromptContentPart.fromUntrustedItem(
                "http://127.0.0.1/image.png",
            )
        assertEquals(PromptContentPart.Text("http://127.0.0.1/image.png"), part)
    }

    @Test
    fun fromUntrustedItemFallsBackToTextWhenLocalFileDisabled() {
        val dir = createTempDirectory("ragas-multimodal-local-disabled")
        val imagePath = dir.resolve("sample.png")
        Files.write(imagePath, PNG_BYTES)

        val part = PromptContentPart.fromUntrustedItem("sample.png")
        assertEquals(PromptContentPart.Text("sample.png"), part)
    }

    @Test
    fun fromUntrustedItemLoadsLocalImageWithinAllowedBaseDir() {
        val dir = createTempDirectory("ragas-multimodal-local-enabled")
        val imagePath = dir.resolve("sample.png")
        Files.write(imagePath, PNG_BYTES)

        val part =
            PromptContentPart.fromUntrustedItem(
                item = "sample.png",
                policy =
                    MultiModalInputPolicy(
                        allowLocalFileAccess = true,
                        allowedImageBaseDir = dir,
                    ),
            )

        assertTrue(part is PromptContentPart.ImageDataUri)
        assertTrue(part.dataUri.startsWith("data:image/png;base64,"))
    }

    @Test
    fun fromUntrustedItemRejectsLocalPathTraversal() {
        val dir = createTempDirectory("ragas-multimodal-traversal")
        val parentImagePath = dir.parent.resolve("outside.png")
        Files.write(parentImagePath, PNG_BYTES)

        val part =
            PromptContentPart.fromUntrustedItem(
                item = "../outside.png",
                policy =
                    MultiModalInputPolicy(
                        allowLocalFileAccess = true,
                        allowedImageBaseDir = dir,
                    ),
            )
        assertEquals(PromptContentPart.Text("../outside.png"), part)
    }

    @Test
    fun fromUntrustedItemsKeepsOrderAcrossTextAndImages() {
        val parts =
            PromptContentPart.fromUntrustedItems(
                listOf(
                    "Instruction",
                    PNG_DATA_URI,
                    "Tail text",
                ),
            )

        assertEquals(3, parts.size)
        assertEquals(PromptContentPart.Text("Instruction"), parts[0])
        assertTrue(parts[1] is PromptContentPart.ImageDataUri)
        assertEquals(PromptContentPart.Text("Tail text"), parts[2])
    }

    private companion object {
        const val PNG_DATA_URI =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
        val PNG_BYTES =
            java.util.Base64
                .getDecoder()
                .decode(PNG_DATA_URI.substringAfter(","))
    }
}
