package com.dub.calorietracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dub.calorietracker.ai.AiException
import com.dub.calorietracker.ai.AiRateLimitException
import com.dub.calorietracker.ai.FoodEstimator
import com.dub.calorietracker.ai.ImageUtils
import com.dub.calorietracker.ai.OpenFoodFacts
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.AiProvider
import com.dub.calorietracker.data.AppState
import com.dub.calorietracker.data.Backup
import com.dub.calorietracker.data.DayLog
import kotlinx.coroutines.flow.collectLatest
import com.dub.calorietracker.data.EntrySource
import com.dub.calorietracker.data.FoodEntry
import com.dub.calorietracker.data.FoodItem
import com.dub.calorietracker.data.FoodMemory
import com.dub.calorietracker.data.MacroSplit
import com.dub.calorietracker.data.PasteParser
import com.dub.calorietracker.data.Plan
import com.dub.calorietracker.data.Repository
import com.dub.calorietracker.data.SavedFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.time.LocalDate
import java.util.UUID

/** A food estimate waiting for the user to review/edit before it's logged. */
data class Pending(
    val name: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val items: List<FoodItem> = emptyList(),
    val notes: String = "",
    val confidence: String = "",
    val source: EntrySource,
    val sourceText: String? = null,
    /** Set when this came from a remembered food (no AI used). */
    val savedFoodId: String? = null,
    /** What "1 serving" means, e.g. "1 bar (40 g)" — shown next to the servings box. */
    val servingLabel: String? = null,
    /** "Food [step] of [of]" when several pasted foods are reviewed one after another (0 = single food). */
    val step: Int = 0,
    val of: Int = 0,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val state: StateFlow<AppState> = repo.state

    val selectedDate = MutableStateFlow(LocalDate.now())
    val pending = MutableStateFlow<Pending?>(null)
    val busy = MutableStateFlow(false)
    /** What's happening while [busy], e.g. "Looking up barcode…". */
    val busyMessage = MutableStateFlow("")
    val error = MutableStateFlow<String?>(null)

    private fun newId() = UUID.randomUUID().toString()

    // ---------- Backup file (survives deleting the app) ----------

    private val prefs = app.getSharedPreferences("backup", android.content.Context.MODE_PRIVATE)
    /** The file the app keeps saving everything to (made automatically in Documents, or chosen by the user). */
    val backupUri = MutableStateFlow(prefs.getString("uri", null))
    val lastBackupAt = MutableStateFlow(prefs.getLong("at", 0L))
    val backupProblem = MutableStateFlow<String?>(null)
    /** A backup that was opened and is waiting for "Replace" to be confirmed. */
    val restoreCandidate = MutableStateFlow<AppState?>(null)

    private val resolver get() = getApplication<Application>().contentResolver

    init {
        clearCapturedPhotos() // leftovers from a previous session
        // Keep the backup file up to date: a couple of seconds after any change, rewrite it.
        // On first launch with a plan (and no file yet) this also creates the automatic backup file.
        viewModelScope.launch {
            var first = true
            state.collectLatest { s ->
                if (first) { first = false; if (backupUri.value != null) return@collectLatest }
                kotlinx.coroutines.delay(2000)
                writeBackup(s)
            }
        }
    }

    /**
     * Documents/CalorieTracker/calorie-tracker-backup.json, created without asking (Android 10+).
     * Files in Documents stay when the app is deleted. Only made once there's a plan, so a fresh reinstall
     * doesn't create an empty backup before you've had a chance to restore the old one.
     */
    private fun createAutoBackupFile(): Uri? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "calorie-tracker-backup.json")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOCUMENTS + "/CalorieTracker")
        }
        return try {
            resolver.insert(android.provider.MediaStore.Files.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (e: Exception) { null }
    }

    private suspend fun writeBackup(s: AppState): Boolean = withContext(Dispatchers.IO) {
        if (backupUri.value == null && s.plan != null) {
            createAutoBackupFile()?.let { u ->
                prefs.edit().putString("uri", u.toString()).putBoolean("auto", true).apply()
                backupUri.value = u.toString()
            }
        }
        val uri = backupUri.value?.let(Uri::parse) ?: return@withContext false
        try {
            resolver.openOutputStream(uri, "wt")!!.use { it.write(Backup.toJson(s).toByteArray()) }
            val now = System.currentTimeMillis()
            prefs.edit().putLong("at", now).apply()
            lastBackupAt.value = now; backupProblem.value = null
            true
        } catch (e: Exception) {
            backupProblem.value = "Couldn't write the backup file (was it moved or deleted?). Choose it again."
            false
        }
    }

    private fun keepAccess(uri: Uri) {
        val rw = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { resolver.takePersistableUriPermission(uri, rw) } catch (_: Exception) {}
        prefs.edit().putString("uri", uri.toString()).apply()
        backupUri.value = uri.toString()
    }

    /** A new backup file was created → save everything into it now and after every change. */
    fun setBackupFile(uri: Uri) {
        keepAccess(uri)
        viewModelScope.launch { writeBackup(state.value) }
    }

    /** Backup now (button). */
    fun backupNow() { viewModelScope.launch { writeBackup(state.value) } }

    /** An existing backup file was picked → read it and ask before replacing what's in the app. */
    fun openBackup(uri: Uri) {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                try { resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }?.let(Backup::fromJson) }
                catch (e: Exception) { null }
            }
            if (restored == null) { error.value = "That file isn't a Calorie Tracker backup."; return@launch }
            pendingRestoreUri = uri
            restoreCandidate.value = restored
        }
    }
    private var pendingRestoreUri: Uri? = null

    fun confirmRestore() {
        val backup = restoreCandidate.value ?: return
        repo.update { Backup.restore(it, backup) }
        // Keep saving to the same file from now on.
        pendingRestoreUri?.let(::keepAccess)
        restoreCandidate.value = null
    }

    fun cancelRestore() { restoreCandidate.value = null }

    /**
     * Camera photos live only in the app's private cache (never the gallery / MediaStore).
     * They're deleted once the meal is logged so nothing piles up.
     */
    fun clearCapturedPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            java.io.File(getApplication<Application>().cacheDir, "photos").listFiles()?.forEach { it.delete() }
        }
    }

    // ---------- Adding food ----------

    fun estimateFromText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // Remembered food? Log it without calling the AI.
        FoodMemory.exactMatch(trimmed, state.value.savedFoods)?.let { useSaved(it, trimmed); return }
        runAi(text = trimmed, images = emptyList(), source = EntrySource.TEXT)
    }

    fun estimateFromPhoto(uri: Uri, note: String?) = estimateFromPhotos(listOf(uri), note)

    /** Several photos of the same meal (different angles) sent together in one AI request. */
    fun estimateFromPhotos(uris: List<Uri>, note: String?) {
        if (uris.isEmpty()) return
        runAi(text = note?.trim()?.ifEmpty { null }, images = uris.take(MAX_PHOTOS), source = EntrySource.PHOTO)
    }

    fun useSaved(food: SavedFood, sourceText: String? = null) {
        pending.value = Pending(
            name = food.name, calories = food.calories, protein = food.protein, carbs = food.carbs, fat = food.fat,
            items = food.items, notes = "From your saved foods — no AI needed.", source = EntrySource.SAVED,
            sourceText = sourceText, savedFoodId = food.id,
        )
    }

    /** Foods still to review after the current one (several LOG lines pasted at once). */
    private val queued = ArrayDeque<Pending>()

    /**
     * Numbers pasted from ChatGPT → review dialog with them filled in.
     * Several LOG lines become separate foods, reviewed and logged one after another.
     */
    fun startFromPaste(p: PasteParser.Parsed) {
        val foods = p.parts.ifEmpty { listOf(p) }
        val list = foods.mapIndexed { i, f ->
            Pending(
                name = f.name, calories = f.calories, protein = f.protein, carbs = f.carbs, fat = f.fat,
                notes = "Pasted from ChatGPT.", source = EntrySource.PASTE,
                step = if (foods.size > 1) i + 1 else 0, of = if (foods.size > 1) foods.size else 0,
            )
        }
        queued.clear(); queued.addAll(list.drop(1))
        pending.value = list.first()
    }

    fun startManual() {
        pending.value = Pending(name = "", calories = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0, source = EntrySource.MANUAL)
    }

    /**
     * Scanned barcode → exact label values from Open Food Facts (free, no AI, works during AI cooldowns).
     * Products scanned before are remembered, so re-scanning works offline and instantly.
     */
    fun lookupBarcode(barcode: String) {
        val code = barcode.filter(Char::isDigit)
        if (code.isEmpty()) { error.value = "That doesn't look like a food barcode."; return }
        val alias = "barcode $code"
        FoodMemory.exactMatch(alias, state.value.savedFoods)?.let { f ->
            pending.value = Pending(
                name = f.name, calories = f.calories, protein = f.protein, carbs = f.carbs, fat = f.fat,
                items = f.items, notes = "Scanned before — from your saved foods.", source = EntrySource.BARCODE,
                sourceText = alias, savedFoodId = f.id, servingLabel = f.description.takeIf { it.isNotBlank() },
            )
            return
        }
        busy.value = true
        busyMessage.value = "Looking up barcode…"
        error.value = null
        viewModelScope.launch {
            try {
                val p = withContext(Dispatchers.IO) { OpenFoodFacts.lookup(code) }
                if (p == null) {
                    error.value = "That product isn't in the Open Food Facts database yet. " +
                        "Take a photo of the nutrition label (or the food) instead and the AI will read it."
                } else {
                    val serving = if (p.perServing) p.servingSize?.let { "1 serving = $it" } ?: "1 serving" else "1 serving = 100 g"
                    pending.value = Pending(
                        name = p.name, calories = p.calories, protein = p.protein, carbs = p.carbs, fat = p.fat,
                        items = listOf(FoodItem(name = p.name, quantity = serving.removePrefix("1 serving = "),
                            calories = p.calories, protein = p.protein, carbs = p.carbs, fat = p.fat)),
                        notes = "From the product's nutrition label (Open Food Facts)." +
                            if (!p.perServing) " Values are per 100 g — set servings to grams eaten ÷ 100." else "",
                        source = EntrySource.BARCODE, sourceText = alias, servingLabel = serving,
                    )
                }
            } catch (e: AiException) {
                error.value = e.message
            } catch (e: Exception) {
                error.value = "Couldn't look up that barcode: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    // What the current estimate was made from, so a correction can resend it.
    private var lastText: String? = null
    private var lastImages: List<Uri> = emptyList()

    /**
     * "I meant 2 of the sandwiches" — re-asks the AI with the original message/photos, its previous answer
     * and the user's correction, like replying in a chat.
     */
    fun refine(p: Pending, correction: String) {
        val fix = correction.trim()
        if (fix.isEmpty()) return
        val prompt = buildString {
            lastText?.let { appendLine("Original message: $it"); appendLine() }
            appendLine("Your previous estimate was:")
            p.items.forEach {
                appendLine("- ${listOf(it.quantity, it.name).filter(String::isNotBlank).joinToString(" ")}: " +
                    "${it.calories.roundToInt()} cal, ${it.protein.roundToInt()} g protein, ${it.carbs.roundToInt()} g carbs, ${it.fat.roundToInt()} g fat")
            }
            appendLine()
            appendLine("The user's correction: $fix")
            append("Apply the correction and return the FULL corrected meal (all items, not just the changed ones).")
        }
        runAi(prompt, lastImages, p.source, sourceText = lastText ?: p.sourceText)
    }

    private fun runAi(text: String?, images: List<Uri>, source: EntrySource, sourceText: String? = text) {
        val s = state.value
        val now = System.currentTimeMillis()
        if (s.ai.cooldownUntil > now) {
            error.value = "${s.ai.cooldownMessage} AI is available again in ${formatWait(s.ai.cooldownUntil - now)}. " +
                "You can still log saved foods or enter food manually."
            return
        }
        busy.value = true
        busyMessage.value = "Estimating calories…"
        error.value = null
        viewModelScope.launch {
            try {
                val est = withContext(Dispatchers.IO) {
                    val jpegs = images.map { ImageUtils.loadForUpload(getApplication(), it) }
                    val known = s.savedFoods.sortedByDescending { it.timesUsed }
                    FoodEstimator.create(s.ai).estimate(text, jpegs, known)
                }
                lastText = sourceText
                lastImages = images
                pending.value = Pending(
                    name = est.name, calories = est.calories, protein = est.protein, carbs = est.carbs, fat = est.fat,
                    items = est.items, notes = est.notes, confidence = est.confidence, source = source, sourceText = sourceText,
                )
            } catch (e: AiRateLimitException) {
                val until = System.currentTimeMillis() + e.retryAfterSeconds * 1000
                repo.update { it.copy(ai = it.ai.copy(cooldownUntil = until, cooldownMessage = e.message.orEmpty())) }
                error.value = "${e.message} AI is available again in ${formatWait(e.retryAfterSeconds * 1000)}. " +
                    "You can still log saved foods or enter food manually."
            } catch (e: AiException) {
                error.value = e.message
            } catch (e: Exception) {
                error.value = "Something went wrong: ${e.message}"
            } finally {
                busy.value = false
            }
        }
    }

    /** Cancel (or "Skip" while pasted foods are queued — moves on to the next one). */
    fun cancelPending() { pending.value = queued.removeFirstOrNull() }
    fun clearError() { error.value = null }

    /** Logs the reviewed food for the selected date and remembers it for next time. */
    fun confirmPending(p: Pending, servings: Double, remember: Boolean) {
        val date = selectedDate.value
        val entry = FoodEntry(
            id = newId(), date = date.toString(), time = System.currentTimeMillis(),
            name = p.name.ifBlank { "Food" },
            calories = p.calories * servings, protein = p.protein * servings,
            carbs = p.carbs * servings, fat = p.fat * servings,
            servings = servings, items = p.items, source = p.source,
        )
        repo.update { s ->
            var foods = s.savedFoods
            if (p.savedFoodId != null) foods = FoodMemory.markUsed(foods, p.savedFoodId)
            else if (remember && p.name.isNotBlank()) {
                foods = FoodMemory.remember(
                    foods, p.name, p.sourceText,
                    description = p.servingLabel
                        ?: p.items.joinToString(", ") { listOf(it.quantity, it.name).filter(String::isNotBlank).joinToString(" ") },
                    calories = p.calories, protein = p.protein, carbs = p.carbs, fat = p.fat,
                    items = p.items, newId = ::newId,
                )
            }
            val added = s.copy(entries = s.entries + entry, savedFoods = foods)
            WeekPlanner.autoShift(added, date, LocalDate.now())
        }
        pending.value = queued.removeFirstOrNull() // next pasted food, if any
        clearCapturedPhotos()
    }

    fun deleteEntry(id: String) = repo.update { s -> s.copy(entries = s.entries.filterNot { it.id == id }) }

    fun updateEntry(e: FoodEntry) = repo.update { s ->
        WeekPlanner.autoShift(s.copy(entries = s.entries.map { if (it.id == e.id) e else it }), LocalDate.parse(e.date), LocalDate.now())
    }

    // ---------- Saved foods ----------

    fun updateSavedFood(f: SavedFood) = repo.update { s -> s.copy(savedFoods = s.savedFoods.map { if (it.id == f.id) f else it }) }
    fun deleteSavedFood(id: String) = repo.update { s -> s.copy(savedFoods = s.savedFoods.filterNot { it.id == id }) }

    // ---------- Plan & week ----------

    /** Change the check-in for [date]; an emptied check-in is removed. */
    fun updateDayLog(date: LocalDate, change: (DayLog) -> DayLog) = repo.update { s ->
        val k = date.toString()
        val next = change(s.dayLogs[k] ?: DayLog())
        s.copy(dayLogs = if (next.isEmpty()) s.dayLogs - k else s.dayLogs + (k to next))
    }

    fun savePlan(plan: Plan) = repo.update { it.copy(plan = plan, weekOverrides = emptyMap(), shiftNotices = emptyMap()) }

    fun swapDays(date: LocalDate, a: Int, b: Int) = repo.update { WeekPlanner.swapDays(it, date, a, b) }

    fun resetWeek(date: LocalDate) = repo.update { s ->
        val key = WeekPlanner.weekStart(date).toString()
        s.copy(weekOverrides = s.weekOverrides - key,
            shiftNotices = s.shiftNotices.filterKeys { LocalDate.parse(it).let { d -> WeekPlanner.weekStart(d).toString() != key } })
    }

    /** Manually make [date] a high-calorie day by swapping with the best later high day this week. */
    fun useHighDay(date: LocalDate) = repo.update { s ->
        val later = WeekPlanner.bestLaterHighDay(s, date) ?: return@update s
        WeekPlanner.swapDays(s, date, WeekPlanner.dayIndex(date), WeekPlanner.dayIndex(later))
    }

    fun dismissNotice(date: LocalDate) = repo.update { it.copy(shiftNotices = it.shiftNotices - date.toString()) }

    // ---------- Settings ----------

    fun setProvider(p: AiProvider) = repo.update { it.copy(ai = it.ai.copy(provider = p, cooldownUntil = 0, cooldownMessage = "")) }
    fun setApiKey(p: AiProvider, key: String) = repo.update { it.copy(ai = it.ai.copy(apiKeys = it.ai.apiKeys + (p to key.trim()))) }
    fun setModel(p: AiProvider, model: String) = repo.update { it.copy(ai = it.ai.copy(models = it.ai.models + (p to model.trim()))) }
    fun clearCooldown() = repo.update { it.copy(ai = it.ai.copy(cooldownUntil = 0, cooldownMessage = "")) }
    fun setMacros(m: MacroSplit) = repo.update { it.copy(macros = m) }
    fun setAutoDetect(on: Boolean) = repo.update { it.copy(autoDetectHighDays = on) }
    fun setThemeMode(m: com.dub.calorietracker.data.ThemeMode) = repo.update { it.copy(theme = it.theme.copy(mode = m)) }
    fun setAccent(a: com.dub.calorietracker.data.Accent) = repo.update { it.copy(theme = it.theme.copy(accent = a)) }
    fun setWebSearch(on: Boolean) = repo.update { it.copy(ai = it.ai.copy(webSearch = on)) }

    companion object {
        /** Photos per meal — enough angles to judge portions without making requests huge. */
        const val MAX_PHOTOS = 3

        fun formatWait(ms: Long): String {
            val totalMin = (ms / 60000.0).let { kotlin.math.ceil(it).toLong() }.coerceAtLeast(1)
            val h = totalMin / 60
            val m = totalMin % 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m} min"
            }
        }
    }
}
