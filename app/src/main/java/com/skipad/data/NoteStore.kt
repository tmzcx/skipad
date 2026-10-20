package com.skipad.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条笔记 */
data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val color: Int,
    val textColor: Int = 0xFF3B2E0A.toInt(),
    val updatedAt: Long,
    /** 笔记里粘贴的图片，存的是应用私有目录的绝对路径 */
    val images: List<String> = emptyList(),
)

/**
 * 笔记存储：直接用 SharedPreferences 存 JSON 数组，够用且不用建数据库。
 * 约定：列表按 updatedAt 倒序，第 0 条就是"最新笔记"（悬浮窗默认显示它）。
 */
object NoteStore {

    private const val SP = "skipad_notes"
    private const val KEY = "notes"

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun all(context: Context): List<Note> {
        val raw = sp(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Note(
                    id = o.optLong("id"),
                    title = o.optString("title"),
                    content = o.optString("content"),
                    color = o.optInt("color", 0xFFFFF8E1.toInt()),
                    textColor = o.optInt("textColor", 0xFF3B2E0A.toInt()),
                    updatedAt = o.optLong("updatedAt"),
                    images = runCatching {
                        val a = o.optJSONArray("images") ?: return@runCatching emptyList<String>()
                        (0 until a.length()).map { a.getString(it) }
                    }.getOrDefault(emptyList()),
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun latest(context: Context): Note? = all(context).firstOrNull()

    fun save(context: Context, note: Note) {
        val list = all(context).filterNot { it.id == note.id } + note
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, all(context).filterNot { it.id == id })
    }

    private fun write(context: Context, notes: List<Note>) {
        val arr = JSONArray()
        notes.sortedByDescending { it.updatedAt }.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("content", n.content)
                    put("color", n.color)
                    put("textColor", n.textColor)
                    put("updatedAt", n.updatedAt)
                    put("images", JSONArray(n.images))
                }
            )
        }
        sp(context).edit().putString(KEY, arr.toString()).apply()
    }
}
