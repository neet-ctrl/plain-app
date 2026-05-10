package com.neet.tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AnnotationTool { PEN, HIGHLIGHTER, ERASER, ARROW, TEXT }

data class AnnotationStroke(
    val points: List<Pair<Float, Float>>,
    val colorArgb: Int,
    val widthDp: Float,
    val tool: String
)

data class AnnotationTextBox(
    val id: String = UUID.randomUUID().toString(),
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val text: String = "",
    val colorArgb: Int = android.graphics.Color.WHITE,
    val fontSizeSp: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val bgArgb: Int = 0,
    val hasBorder: Boolean = true
)

object AnnotationManager {

    private fun fileFor(context: Context, pdfKey: String): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) dir.mkdirs()
        val h = pdfKey.hashCode().toLong() and 0xFFFFFFFFL
        return File(dir, "annot_$h.json")
    }

    private fun textsFileFor(context: Context, pdfKey: String): File {
        val dir = File(context.filesDir, "annotations")
        if (!dir.exists()) dir.mkdirs()
        val h = pdfKey.hashCode().toLong() and 0xFFFFFFFFL
        return File(dir, "annot_texts_$h.json")
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

    suspend fun loadTextBoxes(context: Context, pdfKey: String): Map<Int, List<AnnotationTextBox>> =
        withContext(Dispatchers.IO) {
            val f = textsFileFor(context, pdfKey)
            if (!f.exists()) return@withContext emptyMap()
            val result = mutableMapOf<Int, List<AnnotationTextBox>>()
            try {
                val root = JSONObject(f.readText())
                for (key in root.keys()) {
                    val pageIdx = key.toIntOrNull() ?: continue
                    val arr  = root.getJSONArray(key)
                    val list = mutableListOf<AnnotationTextBox>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list += AnnotationTextBox(
                            id        = o.optString("id", UUID.randomUUID().toString()),
                            xNorm     = o.getDouble("x").toFloat(),
                            yNorm     = o.getDouble("y").toFloat(),
                            text      = o.getString("text"),
                            colorArgb = o.getInt("c"),
                            fontSizeSp= o.getDouble("fs").toFloat(),
                            isBold    = o.getBoolean("bold"),
                            isItalic  = o.getBoolean("italic"),
                            bgArgb    = o.getInt("bg"),
                            hasBorder = o.getBoolean("border")
                        )
                    }
                    result[pageIdx] = list
                }
            } catch (_: Exception) {}
            result
        }

    suspend fun saveTextBoxes(context: Context, pdfKey: String, data: Map<Int, List<AnnotationTextBox>>) =
        withContext(Dispatchers.IO) {
            try {
                val root = JSONObject()
                for ((pageIdx, boxes) in data) {
                    if (boxes.isEmpty()) continue
                    val arr = JSONArray()
                    for (tb in boxes) {
                        val o = JSONObject()
                        o.put("id",     tb.id)
                        o.put("x",      tb.xNorm.toDouble())
                        o.put("y",      tb.yNorm.toDouble())
                        o.put("text",   tb.text)
                        o.put("c",      tb.colorArgb)
                        o.put("fs",     tb.fontSizeSp.toDouble())
                        o.put("bold",   tb.isBold)
                        o.put("italic", tb.isItalic)
                        o.put("bg",     tb.bgArgb)
                        o.put("border", tb.hasBorder)
                        arr.put(o)
                    }
                    root.put(pageIdx.toString(), arr)
                }
                textsFileFor(context, pdfKey).writeText(root.toString())
            } catch (_: Exception) {}
        }

    fun clearAll(context: Context, pdfKey: String) {
        runCatching { fileFor(context, pdfKey).delete() }
        runCatching { textsFileFor(context, pdfKey).delete() }
    }
}
