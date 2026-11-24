package io.github.d4vram369.mirlox.hook

import android.content.Intent
import android.content.IntentSender
import android.content.pm.LabeledIntent
import android.os.Parcelable
import io.github.d4vram369.mirlox.PostId

/**
 * Cuando X abre el selector de Android ("Compartir vía…") con el enlace de un post, pone
 * "Enviar a Mirlo" el primero de la lista. Sin el módulo también aparece, porque SendActivity
 * acepta cualquier SEND de texto, pero en el orden que decida Android.
 *
 * createChooser(Intent, CharSequence) llama por dentro a la versión de tres argumentos.
 */
internal object Chooser {
    fun hook() {
        hook(
            Intent::class.java, "createChooser", Intent::class.java, CharSequence::class.java, IntentSender::class.java,
            h = after { p ->
                val chooser = p.result as? Intent ?: return@after
                val target = p.args[0] as? Intent ?: return@after
                if (target.action != Intent.ACTION_SEND) return@after
                val id = PostId.fromText(target.getStringExtra(Intent.EXTRA_TEXT)) ?: return@after
                val ours = LabeledIntent(Sender.intent(id), MODULE_PACKAGE, sendLabel(), 0)
                @Suppress("DEPRECATION")
                val previous = chooser.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS) ?: emptyArray()
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf<Parcelable>(ours, *previous))
            },
        )
    }
}
