package ragas.prompt

import java.net.URI

sealed interface PromptContentPart {
    fun toPromptText(): String

    data class Text(
        val text: String,
    ) : PromptContentPart {
        override fun toPromptText(): String = text
    }

    data class ImageDataUri(
        val dataUri: String,
    ) : PromptContentPart {
        init {
            require(DATA_URI_REGEX.matches(dataUri)) {
                "ImageDataUri must be a base64 data URI with an allowed image MIME type."
            }
        }

        override fun toPromptText(): String = "[image:data-uri]"
    }

    data class ImageUrl(
        val url: String,
    ) : PromptContentPart {
        init {
            val parsed = URI(url)
            require(parsed.scheme.equals("https", ignoreCase = true)) {
                "ImageUrl must use HTTPS."
            }
            require(!parsed.host.isNullOrBlank()) { "ImageUrl must include a host." }
        }

        override fun toPromptText(): String = "[image:url:$url]"
    }

    companion object {
        private val DATA_URI_REGEX =
            Regex(
                "^data:image/(?:png|jpeg|jpg|gif|webp);base64,[a-zA-Z0-9+/=]+$",
                RegexOption.IGNORE_CASE,
            )
    }
}
