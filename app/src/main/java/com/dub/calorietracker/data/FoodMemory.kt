package com.dub.calorietracker.data

/**
 * Remembers foods the user has logged so "i ate peanut butter jelly" can be logged instantly
 * (no AI call) after they've described it once.
 */
object FoodMemory {
    private val leadingPhrases = listOf(
        "i just ate", "i just had", "just ate", "just had", "i ate", "i had", "i have eaten", "i've eaten",
        "ate", "had", "for breakfast", "for lunch", "for dinner", "for a snack", "eating", "i'm eating",
    )
    private val articles = listOf("a ", "an ", "some ", "the ", "my ", "one ", "1 ")
    /** Words that start the "here's what's in it" part of a description. */
    private val detailSeparators = listOf(" which was ", " which is ", " which had ", " that was ", " made with ", " with ", " - ", ":", "(", ",")

    /** Lowercase, strip punctuation and filler like "i ate a". */
    fun normalize(raw: String): String {
        var s = raw.lowercase().replace('&', ' ').replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        var changed = true
        while (changed) {
            changed = false
            for (p in leadingPhrases) if (s.startsWith("$p ")) { s = s.removePrefix("$p ").trim(); changed = true }
            for (a in articles) if (s.startsWith(a)) { s = s.removePrefix(a).trim(); changed = true }
        }
        return s.removeSuffix(" today").removeSuffix(" earlier").trim()
    }

    /** The short "name" part of a description: "i ate pb&j which was 2 bread…" → "pb j". */
    fun headName(raw: String): String {
        val lower = raw.lowercase()
        val cut = detailSeparators.map { lower.indexOf(it) }.filter { it > 0 }.minOrNull() ?: lower.length
        return normalize(raw.substring(0, cut))
    }

    private fun keys(f: SavedFood) = (listOf(f.name) + f.aliases).map { normalize(it) }.filter { it.isNotEmpty() }

    /** Exact match on the whole text (after normalizing), e.g. "i ate peanut butter jelly". */
    fun exactMatch(text: String, foods: List<SavedFood>): SavedFood? {
        val n = normalize(text)
        if (n.isEmpty()) return null
        return foods.firstOrNull { n in keys(it) }
    }

    /** Suggestions while typing. */
    fun suggestions(text: String, foods: List<SavedFood>, limit: Int = 5): List<SavedFood> {
        val n = normalize(text)
        if (n.length < 2) return emptyList()
        return foods.filter { f -> keys(f).any { it.contains(n) || n.contains(it) } }
            .sortedByDescending { it.timesUsed }
            .take(limit)
    }

    /** Add or update a remembered food. Matching names merge; the newest values win. */
    fun remember(
        foods: List<SavedFood>,
        name: String,
        aliasFromText: String?,
        description: String,
        calories: Double, protein: Double, carbs: Double, fat: Double,
        items: List<FoodItem>,
        newId: () -> String,
    ): List<SavedFood> {
        val cleanName = normalize(name).ifEmpty { return foods }
        val alias = aliasFromText?.let { headName(it) }?.takeIf { it.isNotEmpty() && it != cleanName }
        val existing = foods.firstOrNull { f -> cleanName in keys(f) || (alias != null && alias in keys(f)) }
        return if (existing != null) {
            val updated = existing.copy(
                aliases = (existing.aliases + listOfNotNull(alias, cleanName.takeIf { it != normalize(existing.name) })).distinct(),
                description = description.ifBlank { existing.description },
                calories = calories, protein = protein, carbs = carbs, fat = fat,
                items = items.ifEmpty { existing.items },
                timesUsed = existing.timesUsed + 1,
                lastUsed = System.currentTimeMillis(),
            )
            foods.map { if (it.id == existing.id) updated else it }
        } else {
            foods + SavedFood(
                id = newId(), name = cleanName, aliases = listOfNotNull(alias),
                description = description, calories = calories, protein = protein, carbs = carbs, fat = fat,
                items = items,
            )
        }
    }

    fun markUsed(foods: List<SavedFood>, id: String) = foods.map {
        if (it.id == id) it.copy(timesUsed = it.timesUsed + 1, lastUsed = System.currentTimeMillis()) else it
    }
}
