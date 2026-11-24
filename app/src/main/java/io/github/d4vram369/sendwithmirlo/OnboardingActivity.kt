package io.github.d4vram369.sendwithmirlo

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Bienvenida en tres páginas, solo la primera vez: qué es Mirlo-desktop (y dónde bajarlo), qué fricción quita
 * este módulo y qué hace falta. Vistas a mano, sin AndroidX, como el resto de la app.
 */
class OnboardingActivity : Activity() {

    private class Page(val title: Int, val body: Int, val link: Int? = null, val url: String? = null)

    private val pages = listOf(
        Page(R.string.onb1_title, R.string.onb1_body, R.string.onb1_link, MIRLO_DESKTOP),
        Page(R.string.onb2_title, R.string.onb2_body),
        Page(R.string.onb3_title, R.string.onb3_body, R.string.onb3_link, SYNCTHING_FORK),
    )
    private var index = 0
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var link: Button
    private lateinit var dots: TextView
    private lateinit var next: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = savedInstanceState?.getInt("page") ?: 0
        val pad = dp(28)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, dp(48), pad, pad)
        }
        column.addView(ImageView(this).apply { setImageResource(R.drawable.ic_mirlo) }, LinearLayout.LayoutParams(dp(96), dp(96)))
        title = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(16))
        }
        body = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setLineSpacing(0f, 1.25f)
        }
        link = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            setTextColor(if (night) 0xFF9DB0FF.toInt() else BLUE) // contraste suficiente sobre fondo oscuro
            setOnClickListener { pages[index].url?.let { open(it) } }
        }
        dots = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, dp(24), 0, dp(8))
        }
        next = Button(this).apply {
            backgroundTintList = ColorStateList.valueOf(BLUE)
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { if (index < pages.lastIndex) show(index + 1) else done() }
        }
        listOf(title, body, link, dots, next).forEach { column.addView(it, wide()) }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })
        show(index)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", index)
    }

    @Deprecated("Activity sin AndroidX")
    @Suppress("DEPRECATION")
    override fun onBackPressed() = if (index > 0) show(index - 1) else super.onBackPressed()

    private fun show(i: Int) {
        index = i
        val p = pages[i]
        title.setText(p.title)
        body.setText(p.body)
        link.visibility = if (p.link == null) Button.GONE else Button.VISIBLE
        p.link?.let(link::setText)
        dots.text = pages.indices.joinToString("  ") { if (it == i) "●" else "○" }
        next.setText(if (i < pages.lastIndex) R.string.onb_next else R.string.onb_start)
    }

    private fun done() {
        Prefs.setOnboarded(this)
        finish() // debajo está SetupActivity
    }

    private fun open(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val BLUE = 0xFF2446C7.toInt() // el azul del mirlo
        private const val MIRLO_DESKTOP = "https://github.com/D4vRAM369/Mirlo-desktop/releases/latest"
        private const val SYNCTHING_FORK = "https://github.com/researchxxl/syncthing-android"
    }
}
