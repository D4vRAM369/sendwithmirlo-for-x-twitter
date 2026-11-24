package io.github.d4vram369.mirlox

/**
 * El ID de un post de X dentro de un texto ("Mira esto https://x.com/u/status/123?s=19").
 * Mismos hosts y rutas que acepta Mirlo-desktop (x_url.rs): /usuario/status/ID, /i/status/ID,
 * /i/web/status/ID y "statuses". Si no está claro devuelve null: mejor no enviar que enviar otro post.
 */
object PostId {
    private val LINK = Regex(
        """(?<![\w.-])(?:https?://)?(?:(?:www|mobile)\.)?(?:x|twitter)\.com/""" +
            """(?:[A-Za-z0-9_]{1,15}|i(?:/web)?)/status(?:es)?/(\d{1,20})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    fun fromText(text: CharSequence?): String? = text?.let { LINK.find(it)?.groupValues?.get(1) }

    fun url(id: String) = "https://x.com/i/status/$id"
}
