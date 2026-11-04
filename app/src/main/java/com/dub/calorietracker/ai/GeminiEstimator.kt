package com.dub.calorietracker.ai

import android.util.Base64
import com.dub.calorietracker.data.SavedFood
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
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

/**
 * Google Gemini via the REST API, on the free tier.
 *
 * With [lookups] on, Gemini gets a free tool to search Open Food Facts for branded packaged foods
 * (Barebells, Fairlife, Orgain…) and uses the label values. (Google's own Search tool isn't offered on
 * the free tier for current models, so this is the no-cost way to get real brand numbers.)
 *
 * When Gemini Flash is overloaded (HTTP 503 "high demand"), the request is retried on [LITE_MODEL].
 */
class GeminiEstimator(
    private val apiKey: String,
    private val model: String,
    private val lookups: Boolean = false,
) : FoodEstimator {

    companion object {
        /** Tried in order when the chosen model is overloaded: an older full Flash first, Flash-Lite last. */
        val FALLBACKS = listOf("gemini-3.6-flash", "gemini-flash-lite-latest")
        private const val MAX_TOOL_ROUNDS = 3
    }

    private class Overloaded : Exception()
    private class Missing : Exception()

    override fun estimate(text: String?, images: List<ByteArray>, known: List<SavedFood>): Estimate {
        val chain = (listOf(model) + FALLBACKS).distinct()
        for ((i, m) in chain.withIndex()) {
            try {
                val est = run(m, text, images, known)
                return when {
                    i == 0 -> est
                    m.contains("lite") -> est.copy(notes = (est.notes + " (Gemini Flash was busy, so the lighter Flash-Lite " +
                        "model estimated this — double-check the numbers.)").trim())
                    else -> est.copy(notes = (est.notes + " (Newest Gemini Flash was busy; used $m.)").trim())
                }
            } catch (e: Overloaded) {
                // try the next model
            } catch (e: Missing) {
                if (i == 0) throw AiException("Gemini model \"$m\" not found. Change the model name in Settings.")
                // a fallback model that no longer exists — skip it
            }
        }
        throw AiException("Gemini is overloaded right now (Google says demand is high). Try again in a minute.")
    }

    private fun run(model: String, text: String?, images: List<ByteArray>, known: List<SavedFood>): Estimate {
        val contents = mutableListOf<JsonObject>(
            buildJsonObject {
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
        )
        val looked = mutableListOf<String>()
        var toolRounds = 0
        var askedForMissing = false

        while (true) {
            val offerTool = lookups && toolRounds < MAX_TOOL_ROUNDS
            val body = buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", if (lookups) Prompt.system + "\n\n" + Prompt.lookupHint else Prompt.system) } }
                }
                put("contents", buildJsonArray { contents.forEach { add(it) } })
                if (offerTool) {
                    putJsonArray("tools") {
                        addJsonObject {
                            putJsonArray("functionDeclarations") {
                                addJsonObject {
                                    put("name", "lookup_packaged_food")
                                    put("description", "Search the Open Food Facts database for a BRANDED packaged product " +
                                        "and get its nutrition label (per serving and per 100 g). Use for brand names like " +
                                        "Barebells, Fairlife, Orgain, Tostitos. Not for generic or home-made food.")
                                    putJsonObject("parameters") {
                                        put("type", "object")
                                        putJsonObject("properties") {
                                            putJsonObject("query") {
                                                put("type", "string")
                                                put("description", "Brand + product, e.g. \"Barebells peanut butter protein bar\"")
                                            }
                                        }
                                        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("query")) }
                                    }
                                }
                            }
                        }
                    }
                } else if (!lookups) {
                    putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
                }
            }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val (code, headers, resp) = postJson(url, mapOf("x-goog-api-key" to apiKey), body.toString())
            when {
                code == 429 -> throw rateLimit(resp, headers)
                code == 503 || code == 500 -> throw Overloaded()
                code == 402 -> throw AiException(
                    "Google says this API key's project has no credits left (error 402). That key is on a prepaid " +
                        "billing project — prepaid can't charge your card, it just stops. Fix: in AI Studio (aistudio.google.com " +
                        "→ API keys) create a key in a project WITHOUT billing so it uses the free tier, and paste it in Settings."
                )
                code == 400 || code == 403 -> throw AiException("Gemini rejected the request: ${errorMessage(resp)}")
                code == 404 -> throw Missing()
                code !in 200..299 -> throw AiException("Gemini error $code: ${errorMessage(resp)}")
            }

            val root = Json.parseToJsonElement(resp).jsonObject
            val content = root.arr("candidates").firstOrNull()?.get("content") as? JsonObject
                ?: throw AiException("Gemini returned no answer (it may have blocked the image). Try again.")
            val parts = content.arr("parts")
            val calls = parts.mapNotNull { it["functionCall"] as? JsonObject }

            if (calls.isEmpty()) {
                val reply = parts.filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
                    .joinToString("") { it["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                if (reply.isBlank()) throw AiException("Gemini returned no answer (it may have blocked the image). Try again.")
                val est = Prompt.parse(reply)

                // Dropped foods? Its own list is longer than its items — send it back once to fill them in.
                if (!askedForMissing && est.mentioned.size > est.items.size) {
                    askedForMissing = true
                    contents += content
                    contents += buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject {
                                put("text", "Your foods_mentioned lists ${est.mentioned.size} foods (${est.mentioned.joinToString(", ")}) " +
                                    "but items only has ${est.items.size}. Some foods are missing. Reply with the complete JSON again, " +
                                    "with an item for EVERY food mentioned.")
                            }
                        }
                    }
                    continue
                }
                return if (looked.isEmpty()) est
                else est.copy(notes = (est.notes + " Checked Open Food Facts for: ${looked.distinct().joinToString(", ")}.").trim())
            }

            // Gemini wants product lookups: keep its turn exactly as sent (thought signatures included),
            // run each lookup, and send the results back.
            toolRounds++
            if (toolRounds > MAX_TOOL_ROUNDS + 1) throw AiException("Gemini kept looking things up without answering. Try rephrasing.")
            contents += content
            contents += buildJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    calls.forEach { call ->
                        val name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val query = (call["args"] as? JsonObject)?.get("query")?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (query.isNotBlank()) looked += query
                        addJsonObject {
                            putJsonObject("functionResponse") {
                                put("name", name)
                                call["id"]?.let { put("id", it) }
                                put("response", OpenFoodFacts.searchForAi(query))
                            }
                        }
                    }
                }
            }
        }
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
