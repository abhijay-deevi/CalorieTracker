package com.dub.calorietracker.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Barcode lookups in Open Food Facts — a free, open food database (no key, no cost).
 * Values come straight from the product's nutrition label.
 */
object OpenFoodFacts {

    data class Product(
        val barcode: String,
        val name: String,
        /** e.g. "1 bar (40 g)"; null when the label only gives per-100 g values. */
        val servingSize: String?,
        /** True when the numbers are per serving; false = per 100 g. */
        val perServing: Boolean,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
    )

    /** Returns null when the barcode isn't in the database. Blocking — run off the main thread. */
    fun lookup(barcode: String): Product? {
        val code = barcode.filter(Char::isDigit)
        if (code.isEmpty()) return null
        val url = "https://world.openfoodfacts.org/api/v2/product/$code.json" +
            "?fields=product_name,brands,serving_size,nutriments"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            // Open Food Facts asks apps to identify themselves.
            setRequestProperty("User-Agent", "CalorieTracker-Android/1.0")
        }
        val (status, body) = try {
            val s = conn.responseCode
            s to (if (s in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        } catch (e: IOException) {
            throw AiException("Couldn't reach Open Food Facts: ${e.message ?: "check your connection"}")
        } finally {
            conn.disconnect()
        }
        if (status == 404) return null
        if (status !in 200..299) throw AiException("Open Food Facts error $status. Try again in a moment.")
        return parse(code, body)
    }

    /**
     * Text search for the AI's lookup tool. Returns a small JSON object of the top matches
     * (or an "error" field) — never throws, so a failed lookup just means the AI estimates itself.
     */
    fun searchForAi(query: String): JsonObject {
        if (query.isBlank()) return buildJsonObject { put("error", "empty query") }
        val url = "https://search.openfoodfacts.org/search?q=" + URLEncoder.encode(query, "UTF-8") +
            "&page_size=5&fields=product_name,brands,serving_size,nutriments"
        val body = try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "CalorieTracker-Android/1.0")
            }
            try {
                if (conn.responseCode !in 200..299) return buildJsonObject { put("error", "lookup unavailable (${conn.responseCode})") }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally { conn.disconnect() }
        } catch (e: IOException) {
            return buildJsonObject { put("error", "lookup unavailable (network)") }
        }
        return searchResultsForAi(body)
    }

    internal fun searchResultsForAi(body: String): JsonObject {
        val root = try { Json.parseToJsonElement(body).jsonObject } catch (e: Exception) {
            return buildJsonObject { put("error", "lookup unavailable") }
        }
        val hits = (root["hits"] as? JsonArray ?: root["products"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun round(v: Double?) = v?.let { kotlin.math.round(it * 10) / 10 }
        return buildJsonObject {
            putJsonArray("matches") {
                hits.take(5).forEach { p ->
                    val n = p["nutriments"] as? JsonObject ?: return@forEach
                    fun d(k: String) = n[k]?.jsonPrimitive?.doubleOrNull
                    val brands = when (val b = p["brands"]) {
                        is JsonArray -> b.mapNotNull { it.jsonPrimitive.contentOrNull }.joinToString(", ")
                        else -> b?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
                    val kcal100 = d("energy-kcal_100g") ?: d("energy_100g")?.div(4.184)
                    if (kcal100 == null && d("energy-kcal_serving") == null) return@forEach
                    addJsonObject {
                        put("brand", brands)
                        put("product", p["product_name"]?.jsonPrimitive?.contentOrNull.orEmpty())
                        p["serving_size"]?.jsonPrimitive?.contentOrNull?.let { put("serving_size", it) }
                        if (d("energy-kcal_serving") != null) putJsonObject("per_serving") {
                            put("kcal", round(d("energy-kcal_serving"))); put("protein_g", round(d("proteins_serving")))
                            put("carbs_g", round(d("carbohydrates_serving"))); put("fat_g", round(d("fat_serving")))
                        }
                        if (kcal100 != null) putJsonObject("per_100g") {
                            put("kcal", round(kcal100)); put("protein_g", round(d("proteins_100g")))
                            put("carbs_g", round(d("carbohydrates_100g"))); put("fat_g", round(d("fat_100g")))
                        }
                    }
                }
            }
        }
    }

    internal fun parse(code: String, body: String): Product? {
        val root = try { Json.parseToJsonElement(body).jsonObject } catch (e: Exception) { return null }
        if (root["status"]?.jsonPrimitive?.intOrNull != 1) return null
        val p = root["product"] as? JsonObject ?: return null
        val n = p["nutriments"] as? JsonObject ?: return null
        fun s(k: String) = p[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        fun d(k: String) = n[k]?.jsonPrimitive?.doubleOrNull ?: n[k]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

        // kcal, falling back to kJ ÷ 4.184 when only energy in kJ is listed.
        fun kcal(suffix: String) = d("energy-kcal_$suffix") ?: d("energy-kj_$suffix")?.div(4.184) ?: d("energy_$suffix")?.div(4.184)

        val servingKcal = kcal("serving")
        val perServing = servingKcal != null
        val sfx = if (perServing) "serving" else "100g"
        val calories = (if (perServing) servingKcal else kcal("100g")) ?: return null

        val brand = s("brands").split(',').firstOrNull()?.trim().orEmpty()
        val productName = s("product_name")
        val name = listOf(brand, productName).filter { it.isNotEmpty() }
            .let { parts -> if (parts.size == 2 && productName.contains(brand, ignoreCase = true)) listOf(productName) else parts }
            .joinToString(" ").ifEmpty { "Product $code" }

        return Product(
            barcode = code,
            name = name,
            servingSize = s("serving_size").takeIf { perServing && it.isNotEmpty() },
            perServing = perServing,
            calories = calories,
            protein = d("proteins_$sfx") ?: 0.0,
            carbs = d("carbohydrates_$sfx") ?: 0.0,
            fat = d("fat_$sfx") ?: 0.0,
        )
    }
}
