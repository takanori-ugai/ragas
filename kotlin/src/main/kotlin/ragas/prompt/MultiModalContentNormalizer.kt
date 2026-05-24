package ragas.prompt

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.io.path.extension

/**
 * Policy used to normalize untrusted multimodal prompt items.
 */
data class MultiModalInputPolicy(
    val allowedUrlSchemes: Set<String> = setOf("http", "https"),
    val maxDownloadSizeBytes: Long = 10L * 1024L * 1024L,
    val requestTimeoutMillis: Int = 10_000,
    val allowLocalFileAccess: Boolean = false,
    val allowInternalTargets: Boolean = false,
    val allowedImageBaseDir: Path? = null,
    val maxLocalFileSizeBytes: Long = 10L * 1024L * 1024L,
)

/**
 * Converts untrusted string items into safe prompt content parts.
 *
 * Behavior mirrors Python's `ImageTextPromptValue` normalization strategy:
 * - valid image data URI -> `ImageDataUri`
 * - safe URL image -> downloaded and converted to `ImageDataUri`
 * - optional local file path -> validated and converted to `ImageDataUri`
 * - otherwise -> `Text`
 */
object MultiModalContentNormalizer {
    private val dataUriRegex =
        Regex(
            "^data:(image/(?:png|jpeg|jpg|gif|webp|bmp));base64,([a-zA-Z0-9+/=]+)$",
            RegexOption.IGNORE_CASE,
        )

    private val imageExtensions =
        setOf(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "webp",
            "bmp",
        )

    fun normalizeItem(
        item: String,
        policy: MultiModalInputPolicy = MultiModalInputPolicy(),
    ): PromptContentPart {
        tryProcessDataUri(item)?.let { return it }
        tryProcessAllowedUrl(item, policy)?.let { return it }
        if (policy.allowLocalFileAccess && looksLikeImagePath(item)) {
            tryProcessLocalFile(item, policy)?.let { return it }
        }
        return PromptContentPart.Text(item)
    }

    fun normalizeItems(
        items: List<String>,
        policy: MultiModalInputPolicy = MultiModalInputPolicy(),
    ): List<PromptContentPart> = items.map { item -> normalizeItem(item, policy) }

    private fun tryProcessDataUri(item: String): PromptContentPart.ImageDataUri? {
        val match = dataUriRegex.matchEntire(item.trim()) ?: return null
        val mimeType = match.groupValues[1].lowercase()
        val encodedData = match.groupValues[2]
        val decoded =
            runCatching { Base64.getDecoder().decode(encodedData) }
                .getOrNull()
                ?: return null
        val normalizedMimeType = detectMimeType(decoded) ?: mimeType
        return PromptContentPart.ImageDataUri("data:$normalizedMimeType;base64,$encodedData")
    }

    private fun tryProcessAllowedUrl(
        item: String,
        policy: MultiModalInputPolicy,
    ): PromptContentPart.ImageDataUri? {
        val parsed = runCatching { URI(item.trim()) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        if (scheme !in policy.allowedUrlSchemes) {
            return null
        }
        val host = parsed.host ?: return null
        if (!isSafeUrlTarget(host, policy.allowInternalTargets)) {
            return null
        }
        return downloadValidateAndEncode(item.trim(), policy)
    }

    private fun downloadValidateAndEncode(
        url: String,
        policy: MultiModalInputPolicy,
    ): PromptContentPart.ImageDataUri? {
        val connection =
            runCatching { URI(url).toURL().openConnection() as HttpURLConnection }
                .getOrNull()
                ?: return null
        connection.requestMethod = "GET"
        connection.connectTimeout = policy.requestTimeoutMillis
        connection.readTimeout = policy.requestTimeoutMillis
        connection.instanceFollowRedirects = true

        return runCatching {
            connection.inputStream.use { input ->
                val contentLength = connection.contentLengthLong
                if (contentLength > policy.maxDownloadSizeBytes && contentLength >= 0L) {
                    return null
                }
                val bytes = readUpTo(input, policy.maxDownloadSizeBytes) ?: return null
                val mimeType = detectMimeType(bytes) ?: return null
                val encoded = Base64.getEncoder().encodeToString(bytes)
                PromptContentPart.ImageDataUri("data:$mimeType;base64,$encoded")
            }
        }.getOrNull()
    }

    private fun tryProcessLocalFile(
        item: String,
        policy: MultiModalInputPolicy,
    ): PromptContentPart.ImageDataUri? {
        val baseDir = policy.allowedImageBaseDir ?: return null
        if (!Files.isDirectory(baseDir)) {
            return null
        }
        val relativePath = runCatching { Path.of(item) }.getOrNull() ?: return null
        if (relativePath.isAbsolute) {
            return null
        }
        val candidate = baseDir.resolve(relativePath).normalize()
        val normalizedBase = baseDir.normalize()
        if (!candidate.startsWith(normalizedBase)) {
            return null
        }
        if (!Files.isRegularFile(candidate)) {
            return null
        }
        val fileSize = runCatching { Files.size(candidate) }.getOrNull() ?: return null
        if (fileSize > policy.maxLocalFileSizeBytes) {
            return null
        }
        val bytes = runCatching { Files.readAllBytes(candidate) }.getOrNull() ?: return null
        val mimeType = detectMimeType(bytes) ?: return null
        val encoded = Base64.getEncoder().encodeToString(bytes)
        return PromptContentPart.ImageDataUri("data:$mimeType;base64,$encoded")
    }

    private fun looksLikeImagePath(item: String): Boolean {
        val parsed = runCatching { URI(item.trim()) }.getOrNull()
        val pathValue = parsed?.path ?: item.trim()
        val extension = runCatching { Path.of(pathValue).extension.lowercase() }.getOrNull() ?: return false
        return extension in imageExtensions
    }

    private fun isSafeUrlTarget(
        hostname: String,
        allowInternalTargets: Boolean,
    ): Boolean {
        if (allowInternalTargets) {
            return true
        }
        val addresses = runCatching { InetAddress.getAllByName(hostname) }.getOrNull() ?: return false
        if (addresses.isEmpty()) {
            return false
        }
        return addresses.all { address ->
            !address.isAnyLocalAddress &&
                !address.isLoopbackAddress &&
                !address.isLinkLocalAddress &&
                !address.isSiteLocalAddress &&
                !address.isMulticastAddress
        }
    }

    private fun detectMimeType(bytes: ByteArray): String? {
        if (bytes.isEmpty()) {
            return null
        }
        // First validate the payload is parseable as an image.
        if (runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() == null) {
            return null
        }

        detectMimeTypeByMagic(bytes)?.let { return it }
        val guessed =
            runCatching { URLConnection.guessContentTypeFromStream(ByteArrayInputStream(bytes)) }
                .getOrNull()
                ?.lowercase()
        if (!guessed.isNullOrBlank() && guessed.startsWith("image/")) {
            return guessed
        }
        return "image/jpeg"
    }

    private fun detectMimeTypeByMagic(bytes: ByteArray): String? {
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 6) {
            val header = bytes.copyOfRange(0, 6).decodeToString()
            if (header == "GIF87a" || header == "GIF89a") {
                return "image/gif"
            }
        }
        if (bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
        ) {
            return "image/webp"
        }
        if (bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) {
            return "image/bmp"
        }
        return null
    }

    private fun readUpTo(
        input: java.io.InputStream,
        maxSizeBytes: Long,
    ): ByteArray? {
        val buffer = ByteArray(8 * 1024)
        val output = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            total += read
            if (total > maxSizeBytes) {
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
