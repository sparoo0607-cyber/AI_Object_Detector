package com.accessibility.detector.core

/**
 * SAHAY CONTEXT ENGINE — remembers recent events so the Decision
 * Engine can suppress duplicate announcements (the same signboard
 * text, the same repeated horn) instead of re-announcing every frame.
 * Kotlin port of core/context.js.
 */
object ContextEngine {
    private data class Seen(val key: String, val ts: Long)
    private val recent = ArrayDeque<Seen>()
    private const val MAX_HISTORY = 40

    private fun keyFor(type: String, content: String?): String =
        if (!content.isNullOrBlank()) "$type::${content.take(60)}" else type

    fun remember(type: String, content: String?, ts: Long = System.currentTimeMillis()) {
        recent.addFirst(Seen(keyFor(type, content), ts))
        while (recent.size > MAX_HISTORY) recent.removeLast()
    }

    /** True if this (type, content) pair was announced within cooldownMs. */
    fun isDuplicate(type: String, content: String?, cooldownMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        val key = keyFor(type, content)
        val last = recent.firstOrNull { it.key == key } ?: return false
        return (now - last.ts) < cooldownMs
    }

    fun clear() = recent.clear()
}
