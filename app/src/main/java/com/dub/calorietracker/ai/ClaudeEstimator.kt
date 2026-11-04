package com.dub.calorietracker.ai

import android.util.Base64
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.dub.calorietracker.data.SavedFood

/** Anthropic Claude via the official Java SDK. Billed per use on platform.claude.com. */
class ClaudeEstimator(private val apiKey: String, private val model: String) : FoodEstimator {

    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .maxRetries(1)
            .build()
    }

    override fun estimate(text: String?, images: List<ByteArray>, known: List<SavedFood>): Estimate {
        val blocks = buildList {
            images.forEach { jpeg -> add(
                ContentBlockParam.ofImage(
                    ImageBlockParam.builder()
                        .source(
                            Base64ImageSource.builder()
                                .data(Base64.encodeToString(jpeg, Base64.NO_WRAP))
                                .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                .build()
                        )
                        .build()
                )
            ) }
            add(ContentBlockParam.ofText(TextBlockParam.builder().text(Prompt.user(text, images.size, known)).build()))
        }

        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(Prompt.system)
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .addUserMessageOfContentBlockParams(blocks)
            // If a request is ever declined by a safety classifier, let the API retry it on a fallback model.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

        val response = try {
            client.messages().create(params)
        } catch (e: RateLimitException) {
            val retry = parseDurationSeconds(e.headers().values("retry-after").firstOrNull()) ?: 60
            throw AiRateLimitException(retry, "Claude rate limit reached.")
        } catch (e: UnauthorizedException) {
            throw AiException("Claude rejected the API key. Check it in Settings.")
        } catch (e: AnthropicServiceException) {
            throw AiException("Claude error ${e.statusCode()}: ${e.message}")
        } catch (e: Exception) {
            throw AiException("Couldn't reach Claude: ${e.message}")
        }

        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw AiException("Claude declined this request. Try a different photo or description.")
        }
        val reply = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        return Prompt.parse(reply)
    }
}
