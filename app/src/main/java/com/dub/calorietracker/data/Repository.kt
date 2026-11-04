package com.dub.calorietracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/** Stores the whole app state as one JSON file in private app storage. */
class Repository(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; allowStructuredMapKeys = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun load(): AppState = try {
        if (file.exists()) json.decodeFromString(AppState.serializer(), file.readText()) else AppState()
    } catch (e: Exception) {
        // Keep a copy of an unreadable file instead of silently losing data.
        file.copyTo(File(file.parentFile, "state.corrupt.json"), overwrite = true)
        AppState()
    }

    @Synchronized
    fun update(transform: (AppState) -> AppState) {
        val next = transform(_state.value)
        _state.value = next
        val tmp = File(file.parentFile, "state.json.tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), next))
        tmp.renameTo(file)
    }
}
