package com.dub.calorietracker.data

/**
 * Reads the numbers ChatGPT gives back so they can be pasted straight into the app.
 *
 * Preferred format (what [CHATGPT_INSTRUCTIONS] asks for):
 *   LOG: banana bread & paneer sandwiches | 1660 cal | 200 carbs | 54 protein | 75 fat
 * Also understands labelled numbers in any order ("Calories: 1,660 … Protein 54g …", "P 54 C 200 F 75")
 * and, as a last resort, four plain numbers in the order calories, carbs, protein, fat.
 */
object PasteParser {

    /** [parts] holds each food when several LOG lines were pasted; the top-level numbers are their sum. */
    data class Parsed(
        val name: String, val calories: Double, val carbs: Double, val protein: Double, val fat: Double,
        val parts: List<Parsed> = emptyList(),
    )

    const val CHATGPT_INSTRUCTIONS =
        "For my calorie tracker app: whenever I tell you what I ate, after your normal answer add one LOG line " +
            "per food, at the very end, in exactly this format, covering only the food in my latest message " +
            "(no total line, no daily totals):\n" +
            "LOG: <short food name> | <calories> cal | <carbs> carbs | <protein> protein | <fat> fat\n" +
            "Use whole numbers (macros in grams). If I correct something, give the corrected LOG line for that food."

    private const val NUM = """(\d+(?:\.\d+)?)"""
    private val LOG_LINE = Regex("""^\W*LOG\s*:""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Parsed? {
        if (raw.isBlank()) return null
        val logLines = raw.lines().map { it.trim() }.filter { it.contains(LOG_LINE) }
        if (logLines.isEmpty()) return parseOne(raw, null)

        // One item per food. The same food again means a correction, so the later line wins.
        var foods = linkedMapOf<String, Parsed>()
        logLines.forEachIndexed { i, line ->
            val p = parseOne(line, line) ?: return@forEachIndexed
            val key = p.name.lowercase().takeIf { it != "from chatgpt" } ?: "#$i"
            foods.remove(key); foods[key] = p
        }
        // A "Total" line on top of the foods would count everything twice.
        if (foods.size > 1) foods = LinkedHashMap(foods.filterKeys { !it.startsWith("total") })
        val list = foods.values.toList()
        return when (list.size) {
            0 -> null
            1 -> list[0]
            else -> Parsed(
                name = list.joinToString(" + ") { it.name },
                calories = list.sumOf { it.calories }, carbs = list.sumOf { it.carbs },
                protein = list.sumOf { it.protein }, fat = list.sumOf { it.fat },
                parts = list,
            )
        }
    }

    private fun parseOne(source: String, logLine: String?): Parsed? {
        val text = source.replace(Regex("""(?<=\d),(?=\d{3}\b)"""), "") // 1,660 → 1660

        val name = logLine?.substringAfter(':')?.substringBefore('|')?.trim()
            ?.takeIf { it.isNotEmpty() && !it.first().isDigit() }
            ?: "From ChatGPT"

        val cal = labelled(text, "kcal|calories|calorie|cals|cal")
        val carbs = labelled(text, "carbohydrates|carbohydrate|carbs|carb|c")
        val protein = labelled(text, "protein|prot|p")
        val fat = labelled(text, "fats|fat|f")
        if (cal != null && listOf(carbs, protein, fat).count { it != null } >= 1) {
            return Parsed(name, cal, carbs ?: 0.0, protein ?: 0.0, fat ?: 0.0)
        }

        // Fallback: 4 bare numbers → calories, carbs, protein, fat.
        val nums = Regex(NUM).findAll(text).map { it.value.toDouble() }.toList()
        if (nums.size == 4) return Parsed(name, nums[0], nums[1], nums[2], nums[3])
        return null
    }

    /** "1660 cal", "200g carbs", "Calories: 1660", "P 54", "protein - 54 g". */
    private fun labelled(text: String, labels: String): Double? {
        val o = RegexOption.IGNORE_CASE
        // "Protein: 54g" — explicit label first, so "54g Carbs: 200g" isn't misread as 54 carbs.
        val colon = Regex("""\b(?:$labels)\b\s*[:=]\s*$NUM""", o)
        val after = Regex("""$NUM\s*(?:g|grams?)?\s*(?:$labels)\b""", o)
        val before = Regex("""\b(?:$labels)\b\s*-?\s*$NUM""", o)
        return listOf(colon, after, before).firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }?.toDoubleOrNull()
    }
}
