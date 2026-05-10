package com.neet.tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AnnotationTool { PEN, HIGHLIGHTER, ERASER }

data class AnnotationStroke(
    val points: List<Pair<Float, Float>>,
    val colorArgb: Int,
    val widthDp: Float,
    val tool: String
)

object AnnotationManager {

    private fun fileFor(context: Context, pdfKey: String): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) dir.mkdirs()
        // Use a stable numeric hash so even very long paths map to unique short names
        val h = pdfKey.hashCode().toLong() and 0xFFFFFFFFL
        return File(dir, "annot_$h.json")
    }

    suspend fun load(context: Context, pdfKey: String): Map<Int, List<AnnotationStroke>> =
        withContext(Dispatchers.IO) {
            val f = fileFor(context, pdfKey)
            if (!f.exists()) return@withContext emptyMap()
            val result = mutableMapOf<Int, List<AnnotationStroke>>()
            try {
                val root = JSONObject(f.readText())
                for (key in root.keys()) {
                    val pageIdx = key.toIntOrNull() ?: continue
                    val arr  = root.getJSONArray(key)
                    val list = mutableListOf<AnnotationStroke>()
                    for (i in 0 until arr.length()) {
                        val s   = arr.getJSONObject(i)
                        val pts = s.getJSONArray("pts")
                        val points = (0 until pts.length() step 2).map { j ->
                            Pair(pts.getDouble(j).toFloat(), pts.getDouble(j + 1).toFloat())
                        }
                        list += AnnotationStroke(
                            points    = points,
                            colorArgb = s.getInt("c"),
                            widthDp   = s.getDouble("w").toFloat(),
                            tool      = s.getString("t")
                        )
                    }
                    result[pageIdx] = list
                }
            } catch (_: Exception) {}
            result
        }

    suspend fun save(context: Context, pdfKey: String, data: Map<Int, List<AnnotationStroke>>) =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject()
                for ((pageIdx, strokes) in data) {
                    if (strokes.isEmpty()) continue
                    val arr = JSONArray()
                    for (stroke in strokes) {
                        val s   = JSONObject()
                        val pts = JSONArray()
                        for ((x, y) in stroke.points) { pts.put(x.toDouble()); pts.put(y.toDouble()) }
                        s.put("pts", pts)
                        s.put("c", stroke.colorArgb)
                        s.put("w", stroke.widthDp.toDouble())
                        s.put("t", stroke.tool)
                        arr.put(s)
                    }
                    root.put(pageIdx.toString(), arr)
                }
                fileFor(context, pdfKey).writeText(root.toString())
            } catch (_: Exception) {}
        }

    fun clearAll(context: Context, pdfKey: String) {
        runCatching { fileFor(context, pdfKey).delete() }
    }
}
