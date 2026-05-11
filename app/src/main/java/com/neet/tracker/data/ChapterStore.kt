package com.neet.tracker.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ChapterStore {
    private const val PREF_NAME = "neet_chapter_library"
    private const val KEY_JSON  = "chapters_json"

    fun saveJson(context: Context, json: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_JSON, json).apply()
    }

    fun loadJson(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JSON, "") ?: ""

    fun getChapters(context: Context): List<String> {
        val json = loadJson(context).trim()
        if (json.isBlank()) return emptyList()
        val gson = Gson()
        return try {
            if (json.startsWith("{")) {
                val mapType = object : TypeToken<Map<String, List<String>>>() {}.type
                val map: Map<String, List<String>> = gson.fromJson(json, mapType)
                map.values.flatten().distinct().sorted()
            } else {
                val listType = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(json, listType).distinct().sorted()
            }
        } catch (e: Exception) { emptyList() }
    }

    fun clearChapters(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
