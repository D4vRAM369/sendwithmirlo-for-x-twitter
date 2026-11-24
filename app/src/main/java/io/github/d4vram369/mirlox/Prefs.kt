package io.github.d4vram369.mirlox

import android.content.Context
import android.net.Uri

/** Solo se guarda una cosa: qué carpeta (de Syncthing) eligió la persona. */
object Prefs {
    private const val FILE = "mirlo"
    private const val TREE = "inbox_tree"

    fun tree(context: Context): Uri? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(TREE, null)?.let(Uri::parse)

    fun setTree(context: Context, uri: Uri) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(TREE, uri.toString()).apply()
    }
}
