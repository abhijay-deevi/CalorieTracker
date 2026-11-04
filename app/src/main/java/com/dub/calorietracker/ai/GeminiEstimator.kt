package com.dub.calorietracker.ai

import android.util.Base64
import com.dub.calorietracker.data.SavedFood
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Google Gemini via the REST API. Works on the free tier (rate-limited). */
class GeminiEstimator(private val apiKey: String, private val model: String) : FoodEstimator {

    override fun estimate(text: String?, images: List<ByteArray>, known: List<SavedFood>): Estimate {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", Prompt.system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        images.forEach { img ->
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", Base64.encodeToString(img, Base64.NO_WRAP))
                                }
                            }
                        }
                        addJsonObject { put("text", Prompt.user(text, images.size, known)) }
                    }
                }
            }
            putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val (code, headers, resp) = postJson(url, mapOf("x-goog-api-key" to apiKey), body.toString())

        when {
            code == 429 -> throw rateLimit(resp, headers)
            code == 400 || code == 403 -> throw AiException("Gemini rejected the request: ${errorMessage(resp)}")
            code == 404 -> throw AiException("Gemini model \"$model\" not found. Change the model name in Settings.")
            code !in 200..299 -> throw AiException("Gemini error $code: ${errorMessage(resp)}")
        }
        val root = Json.parseToJsonElement(resp).jsonObject
        val parts = root.arr("candidates").firstOrNull()
            ?.get("content")?.let { it as? JsonObject }?.arr("parts").orEmpty()
        val reply = parts.filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        if (reply.isBlank()) throw AiException("Gemini returned no answer (it may have blocked the image). Try again.")
        return Prompt.parse(reply)
    }

    private fun rateLimit(resp: String, headers: Map<String, String>): AiRateLimitException {
        var retry: Long? = parseDurationSeconds(headers["retry-after"])
        var daily = false
        try {
            val err = Json.parseToJsonElement(resp).jsonObject["error"]?.jsonObject
            err?.arr("details")?.forEach { d ->
                val type = d["@type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (type.endsWith("RetryInfo")) {
                    retry = parseDurationSeconds(d["retryDelay"]?.jsonPrimitive?.contentOrNull) ?: retry
                }
                if (type.endsWith("QuotaFailure")) {
                    d.arr("violations").forEach { v ->
                        val id = v["quotaId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (id.contains("PerDay", ignoreCase = true)) daily = true
                    }
                }
            }
        } catch (_: Exception) { }

        return if (daily) {
            // Free-tier daily quotas reset at midnight Pacific time.
            val pacific = ZoneId.of("America/Los_Angeles")
            val now = ZonedDateTime.now(pacific)
            val reset = LocalDate.now(pacific).plusDays(1).atStartOfDay(pacific)
            AiRateLimitException(Duration.between(now, reset).seconds,
                "You've used today's free Gemini requests. They reset at midnight Pacific time.")
        } else {
            AiRateLimitException(retry ?: 60, "Gemini's free tier is busy for a moment (too many requests).")
        }
    }
}
