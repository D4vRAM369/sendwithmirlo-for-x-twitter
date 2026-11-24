package io.github.d4vram369.sendwithmirlo

import android.content.Context
import android.net.Uri

/** La carpeta (de Syncthing) que eligió la persona y sus ajustes de recordatorio. */
object Prefs {
    private const val FILE = "mirlo"
    private const val TREE = "inbox_tree"
    private const val ONBOARDED = "onboarded"
    const val REMIND_DAYS = "remind_days" // 0 = nunca
    const val REMIND_TEXT = "remind_text"
    const val THRESHOLD = "threshold"
    const val CHECK_HOURS = "check_hours" // 0 = nunca
    val DEFAULTS = mapOf(REMIND_DAYS to 3, THRESHOLD to 15, CHECK_HOURS to 24)

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun tree(context: Context): Uri? = sp(context).getString(TREE, null)?.let(Uri::parse)

    fun setTree(context: Context, uri: Uri) {
        sp(context).edit().putString(TREE, uri.toString()).apply()
    }

    fun onboarded(context: Context) = sp(context).getBoolean(ONBOARDED, false)

    fun setOnboarded(context: Context) {
        sp(context).edit().putBoolean(ONBOARDED, true).apply()
    }

    fun int(context: Context, key: String): Int = sp(context).getInt(key, DEFAULTS.getValue(key))

    fun remindText(context: Context): String =
        sp(context).getString(REMIND_TEXT, null) ?: context.getString(R.string.remind_default)

    fun save(context: Context, ints: Map<String, Int>, text: String) {
        val e = sp(context).edit()
        ints.forEach { (k, v) -> e.putInt(k, v) }
        // el texto por defecto no se guarda: así sigue el idioma del móvil si este cambia
        val custom = text.trim().takeUnless { it.isEmpty() || it == context.getString(R.string.remind_default) }
        e.putString(REMIND_TEXT, custom).apply()
    }
}
