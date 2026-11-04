package com.dub.calorietracker.ai

import android.util.Base64
import com.dub.calorietracker.data.SavedFood
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** OpenAI Chat Completions API. Billed per use on platform.openai.com (not part of ChatGPT Plus). */
class OpenAiEstimator(private val apiKey: String, private val model: String) : FoodEstimator {

    override fun estimate(text: String?, images: List<ByteArray>, known: List<SavedFood>): Estimate {
        val body = buildJsonObject {
            put("model", model)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", Prompt.system)
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", Prompt.user(text, images.size, known))
                        }
                        images.forEach { img ->
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:image/jpeg;base64," + Base64.encodeToString(img, Base64.NO_WRAP))
                                }
                            }
                        }
                    }
                }
            }
        }
        val (code, headers, resp) = postJson(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $apiKey"),
            body.toString(),
        )
        if (code == 429) {
            val code429 = try {
                Json.parseToJsonElement(resp).jsonObject["error"]?.jsonObject?.get("code")?.jsonPrimitive?.contentOrNull
            } catch (_: Exception) { null }
            if (code429 == "insufficient_quota") {
                throw AiException("Your OpenAI API account has no credit. API billing is separate from ChatGPT Plus — add credit at platform.openai.com or switch to Gemini (free) in Settings.")
            }
            val retry = parseDurationSeconds(headers["retry-after"])
                ?: parseDurationSeconds(headers["x-ratelimit-reset-requests"])
                ?: parseDurationSeconds(headers["x-ratelimit-reset-tokens"])
                ?: 60
            throw AiRateLimitException(retry, "OpenAI rate limit reached.")
        }
        if (code == 401) throw AiException("OpenAI rejected the API key. Check it in Settings.")
        if (code !in 200..299) throw AiException("OpenAI error $code: ${errorMessage(resp)}")

        val reply = Json.parseToJsonElement(resp).jsonObject.arr("choices").firstOrNull()
            ?.get("message")?.let { it as? JsonObject }
            ?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        return Prompt.parse(reply)
    }
}
