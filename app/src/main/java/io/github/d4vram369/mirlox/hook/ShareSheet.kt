package io.github.d4vram369.mirlox.hook

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Añade "Enviar a Mirlo" al menú Compartir de X, debajo de "Copiar enlace".
 *
 * Funciona si ese menú está hecho con vistas clásicas (TextView dentro de un LinearLayout
 * vertical, o de un RecyclerView dentro de uno). Si X lo pinta con Compose o con otra
 * estructura no se toca nada y se vuelca el árbol de vistas al log (etiqueta MirloX) para
 * adaptar la hook: en ese caso sigue funcionando Compartir → Enviar a Mirlo (Chooser.kt).
 */
internal object ShareSheet {
    private const val MARK = "mirlo-send"
    private val COPY_LABELS = listOf("copy link", "copiar enlace", "copiar link", "copiar vínculo")
    private val RETRY_AT_MS = longArrayOf(0, 150, 400, 900, 1600) // el menú puede rellenarse tarde
    private val main = Handler(Looper.getMainLooper())

    private sealed interface Outcome {
        data object Injected : Outcome
        data object NoCopyRow : Outcome
        data class Cannot(val why: String) : Outcome
    }

    fun hook() {
        hook(Dialog::class.java, "show", h = after { p ->
            (p.thisObject as Dialog).window?.decorView?.let(::watch)
        })
        val popup = after { p -> (p.thisObject as PopupWindow).contentView?.rootView?.let(::watch) }
        hookAll(PopupWindow::class.java, "showAtLocation", popup)
        hookAll(PopupWindow::class.java, "showAsDropDown", popup)
    }

    private fun watch(root: View) {
        fun attempt(n: Int) {
            when (val outcome = runCatching { tryInject(root) }.getOrElse { Outcome.Cannot("excepción: $it") }) {
                Outcome.Injected -> Unit
                is Outcome.Cannot -> Diagnose.dump(root, outcome.why)
                Outcome.NoCopyRow ->
                    if (n + 1 < RETRY_AT_MS.size) main.postDelayed({ attempt(n + 1) }, RETRY_AT_MS[n + 1] - RETRY_AT_MS[n])
                    else if (Diagnose.looksLikeShare(root)) Diagnose.dump(root, "no encuentro «Copiar enlace» como TextView")
            }
        }
        main.post { attempt(0) }
    }

    private fun tryInject(root: View): Outcome {
        if (root.findViewWithTag<View>(MARK) != null) return Outcome.Injected
        val label = findCopyLabel(root) ?: return Outcome.NoCopyRow

        // lo que X escucha al pulsar: el primer ancestro pulsable (puede ser el propio texto)
        val click = ancestors(label, root).firstOrNull { it.isClickable }
            ?: return Outcome.Cannot("«Copiar enlace» sin nada pulsable encima")
        // la fila: el hijo directo de la lista vertical, buscado desde lo pulsable y no desde el
        // texto, para no acabar dentro de una fila con título y subtítulo apilados
        val row = ancestors(click, root).firstOrNull { (it.parent as? ViewGroup)?.let(::isList) == true }
            ?: return Outcome.Cannot("no hay lista vertical encima de «Copiar enlace»")
        if (row.height == 0) return Outcome.NoCopyRow // aún sin medir: se reintenta

        val list = row.parent as ViewGroup
        val (container, index) = if (list is LinearLayout) {
            list to list.indexOfChild(row) + 1
        } else {
            // en un RecyclerView no se puede meter una vista a mano: va justo debajo de la lista
            val outer = list.parent as? LinearLayout
            if (outer == null || outer.orientation != LinearLayout.VERTICAL) {
                return Outcome.Cannot("lista reciclable dentro de ${list.parent?.javaClass?.name}")
            }
            outer to outer.indexOfChild(list) + 1
        }
        container.addView(buildItem(row, label, click), index,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        log("botón añadido (lista: ${list.javaClass.name})")
        return Outcome.Injected
    }

    /** Copia el aspecto de la fila de "Copiar enlace": altura, fondo y tipografía, con el texto alineado. */
    private fun buildItem(row: View, label: TextView, click: View): View {
        val context = row.context
        return TextView(context).apply {
            tag = MARK
            text = sendLabel()
            setTextColor(label.textColors)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, label.textSize)
            typeface = label.typeface
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            minHeight = row.height
            setPaddingRelative(leftWithin(label, row), 0, row.paddingEnd, 0)
            background = row.background?.constantState?.newDrawable(resources)?.mutate() ?: ripple(this)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Clipboard.arm(context)
                if (!click.performClick()) {
                    Clipboard.disarm()
                    return@setOnClickListener log("«Copiar enlace» no respondió a performClick")
                }
                main.postDelayed({
                    if (Clipboard.disarm()) {
                        log("X no copió ningún enlace tras pulsar «Copiar enlace»")
                        toast(context, "Mirlo: X no ha dado el enlace de este post", "Mirlo: X did not give the link to this post")
                    }
                }, 2_600)
            }
        }
    }

    private fun findCopyLabel(view: View): TextView? {
        if (view is TextView && view.isShown) {
            val text = view.text?.toString()?.trim()?.lowercase().orEmpty()
            if (COPY_LABELS.any { text.startsWith(it) }) return view
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) findCopyLabel(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun isList(group: ViewGroup) =
        (group is LinearLayout && group.orientation == LinearLayout.VERTICAL) || isRecycler(group)

    /** Sin depender de AndroidX de X (puede ir renombrado): basta con el nombre en la jerarquía. */
    private fun isRecycler(group: ViewGroup): Boolean {
        if (group is AdapterView<*>) return true
        var c: Class<*>? = group.javaClass
        while (c != null) {
            if (c.name.endsWith("RecyclerView")) return true
            c = c.superclass
        }
        return false
    }

    /** De la vista hacia arriba, sin salir de root. */
    private fun ancestors(from: View, root: View): Sequence<View> =
        generateSequence(from) { v -> if (v === root) null else v.parent as? View }

    private fun leftWithin(child: View, ancestor: View): Int {
        var x = 0
        var v: View = child
        while (v !== ancestor) {
            x += v.left
            v = v.parent as? View ?: break
        }
        return x
    }

    private fun ripple(view: View) = runCatching {
        val out = TypedValue()
        view.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        view.context.getDrawable(out.resourceId)
    }.getOrNull()
}
