package com.dub.calorietracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dub.calorietracker.ai.AiException
import com.dub.calorietracker.ai.AiRateLimitException
import com.dub.calorietracker.ai.FoodEstimator
import com.dub.calorietracker.ai.ImageUtils
import com.dub.calorietracker.calc.WeekPlanner
import com.dub.calorietracker.data.AiProvider
import com.dub.calorietracker.data.AppState
import com.dub.calorietracker.data.EntrySource
import com.dub.calorietracker.data.FoodEntry
import com.dub.calorietracker.data.FoodItem
import com.dub.calorietracker.data.FoodMemory
import com.dub.calorietracker.data.MacroSplit
import com.dub.calorietracker.data.Plan
import com.dub.calorietracker.data.Repository
import com.dub.calorietracker.data.SavedFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val state: StateFlow<AppState> = repo.state

    val selectedDate = MutableStateFlow(LocalDate.now())
    val pending = MutableStateFlow<Pending?>(null)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private fun newId() = UUID.randomUUID().toString()

    init { clearCapturedPhotos() } // leftovers from a previous session

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

    fun startManual() {
        pending.value = Pending(name = "", calories = 0.0, protein = 0.0, carbs = 0.0, fat = 0.0, source = EntrySource.MANUAL)
    }

    private fun runAi(text: String?, images: List<Uri>, source: EntrySource) {
        val s = state.value
        val now = System.currentTimeMillis()
        if (s.ai.cooldownUntil > now) {
            error.value = "${s.ai.cooldownMessage} AI is available again in ${formatWait(s.ai.cooldownUntil - now)}. " +
                "You can still log saved foods or enter food manually."
            return
        }
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                val est = withContext(Dispatchers.IO) {
                    val jpegs = images.map { ImageUtils.loadForUpload(getApplication(), it) }
                    val known = s.savedFoods.sortedByDescending { it.timesUsed }
                    FoodEstimator.create(s.ai).estimate(text, jpegs, known)
                }
                pending.value = Pending(
                    name = est.name, calories = est.calories, protein = est.protein, carbs = est.carbs, fat = est.fat,
                    items = est.items, notes = est.notes, confidence = est.confidence, source = source, sourceText = text,
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

    fun cancelPending() { pending.value = null }
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
                    description = p.items.joinToString(", ") { listOf(it.quantity, it.name).filter(String::isNotBlank).joinToString(" ") },
                    calories = p.calories, protein = p.protein, carbs = p.carbs, fat = p.fat,
                    items = p.items, newId = ::newId,
                )
            }
            val added = s.copy(entries = s.entries + entry, savedFoods = foods)
            WeekPlanner.autoShift(added, date, LocalDate.now())
        }
        pending.value = null
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
        WeekPlanner.swapDays(s, date, WeekPlanner.dayIndex(date), later)
    }

    fun dismissNotice(date: LocalDate) = repo.update { it.copy(shiftNotices = it.shiftNotices - date.toString()) }

    // ---------- Settings ----------

    fun setProvider(p: AiProvider) = repo.update { it.copy(ai = it.ai.copy(provider = p, cooldownUntil = 0, cooldownMessage = "")) }
    fun setApiKey(p: AiProvider, key: String) = repo.update { it.copy(ai = it.ai.copy(apiKeys = it.ai.apiKeys + (p to key.trim()))) }
    fun setModel(p: AiProvider, model: String) = repo.update { it.copy(ai = it.ai.copy(models = it.ai.models + (p to model.trim()))) }
    fun clearCooldown() = repo.update { it.copy(ai = it.ai.copy(cooldownUntil = 0, cooldownMessage = "")) }
    fun setMacros(m: MacroSplit) = repo.update { it.copy(macros = m) }
    fun setAutoDetect(on: Boolean) = repo.update { it.copy(autoDetectHighDays = on) }

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
