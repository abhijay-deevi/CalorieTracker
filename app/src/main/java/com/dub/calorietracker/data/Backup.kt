package com.dub.calorietracker.data

import kotlinx.serialization.json.Json

/**
 * The backup file: everything in the app (plan, every logged food, check-ins, saved foods, week swaps, settings)
 * as JSON, kept in shared storage (Documents/CalorieTracker or a file the user picks) so it survives deleting the app.
 * API keys are left out — the file sits in shared storage, and keys are easy to paste again.
 */
object Backup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true; prettyPrint = true }

    fun toJson(s: AppState): String =
        json.encodeToString(AppState.serializer(), s.copy(ai = s.ai.copy(apiKeys = emptyMap(), cooldownUntil = 0, cooldownMessage = "")))

    /** Null if the file isn't a backup from this app. */
    fun fromJson(text: String): AppState? = try {
        json.decodeFromString(AppState.serializer(), text)
            .takeIf { it.plan != null || it.entries.isNotEmpty() || it.savedFoods.isNotEmpty() || it.dayLogs.isNotEmpty() }
    } catch (e: Exception) { null }

    /**
     * Adds the backup to what's in the app — nothing is deleted. Foods logged in both count once; for a day
     * checked in on both, and for the plan, the newer one wins. API keys typed into this install are kept.
     */
    fun restore(current: AppState, backup: AppState): AppState {
        val entryIds = backup.entries.map { it.id }.toSet()
        val savedIds = backup.savedFoods.map { it.id }.toSet()
        val plan = listOfNotNull(backup.plan, current.plan).maxByOrNull { it.createdAt }
        return backup.copy(
            plan = plan,
            entries = (backup.entries + current.entries.filterNot { it.id in entryIds }).sortedBy { it.time },
            savedFoods = backup.savedFoods + current.savedFoods.filterNot { it.id in savedIds },
            dayLogs = backup.dayLogs + current.dayLogs,
            weekOverrides = if (plan === backup.plan) backup.weekOverrides else current.weekOverrides,
            ai = backup.ai.copy(apiKeys = current.ai.apiKeys.ifEmpty { backup.ai.apiKeys }),
        )
    }
}
