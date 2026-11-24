package io.github.d4vram369.mirlox.hook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import io.github.d4vram369.mirlox.PostId

/**
 * Cómo se saca el ID sin conocer las clases de X: el botón de Mirlo pulsa por debajo la opción
 * "Copiar enlace" de X, y aquí se intercepta lo que X intenta copiar. Mientras está "armado",
 * la copia no llega al portapapeles: lo que la persona tuviera copiado sigue ahí.
 * Sin armar, este hook no hace nada.
 */
internal object Clipboard {
    private const val WINDOW_MS = 2_500L

    @Volatile private var armedUntil = 0L
    @Volatile private var armedContext: Context? = null

    fun arm(context: Context) {
        armedContext = context.applicationContext
        armedUntil = SystemClock.uptimeMillis() + WINDOW_MS
    }

    /** true si seguía armado, o sea, si X no llegó a copiar nada. */
    fun disarm(): Boolean {
        val was = SystemClock.uptimeMillis() <= armedUntil
        armedUntil = 0
        return was
    }

    fun hook() {
        hook(ClipboardManager::class.java, "setPrimaryClip", ClipData::class.java, h = before { p ->
            if (SystemClock.uptimeMillis() > armedUntil) return@before
            val clip = p.args[0] as? ClipData ?: return@before
            val text = (0 until clip.itemCount).joinToString(" ") { clip.getItemAt(it).text?.toString().orEmpty() }
            val id = PostId.fromText(text) ?: return@before log("X copió algo que no es un post")
            armedUntil = 0
            p.skip() // se salta el setPrimaryClip original
            val context = armedContext ?: HostApp.context ?: return@before log("sin contexto para enviar $id")
            Sender.send(context, id)
        })
    }
}
