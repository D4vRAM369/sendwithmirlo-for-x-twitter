package io.github.d4vram369.mirlox.hook

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap

/**
 * Cuando no se puede inyectar el botón, vuelca el árbol de vistas al log (adb logcat -s MirloX)
 * para ver cómo está hecho el menú de esa versión de X. Solo menús que parecen de Compartir,
 * una vez por menú, y los textos recortados: no debe acabar un post entero en el log.
 */
internal object Diagnose {
    private const val MAX_LINES = 250
    private const val MAX_TEXT = 32
    private val dumped: MutableSet<View> = Collections.newSetFromMap(WeakHashMap())
    private val HINTS = listOf("compartir", "share", "enlace", "link")

    fun looksLikeShare(root: View): Boolean {
        var found = false
        walk(root, 0) { v, _ ->
            val text = (v as? TextView)?.text?.toString()?.lowercase()
            if (text != null && HINTS.any { it in text }) found = true
            if (isCompose(v)) found = true
        }
        return found
    }

    fun dump(root: View, why: String) {
        if (!dumped.add(root)) return
        val lines = mutableListOf("── no se inyectó: $why")
        var compose = false
        walk(root, 0) { v, depth ->
            compose = compose || isCompose(v)
            if (lines.size < MAX_LINES) lines += "  ".repeat(depth) + describe(v)
        }
        if (compose) lines.add(1, "── hay AndroidComposeView: el menú es Compose, no se pueden añadir vistas")
        lines.chunked(40).forEach { log(it.joinToString("\n")) } // logcat corta las líneas largas
    }

    private fun describe(v: View): String = buildString {
        append(v.javaClass.name)
        if (v.id != View.NO_ID) runCatching { append(" #").append(v.resources.getResourceEntryName(v.id)) }
        if (v.isClickable) append(" [pulsable]")
        if (v.visibility != View.VISIBLE) append(" [oculta]")
        (v as? ViewGroup)?.let { append(" (").append(it.childCount).append(')') }
        (v as? TextView)?.text?.takeIf { it.isNotBlank() }?.let {
            append(" «").append(it.toString().take(MAX_TEXT).replace('\n', ' ')).append('»')
        }
    }

    private fun isCompose(v: View) = v.javaClass.name.endsWith("AndroidComposeView")

    private fun walk(v: View, depth: Int, visit: (View, Int) -> Unit) {
        visit(v, depth)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1, visit)
    }
}
