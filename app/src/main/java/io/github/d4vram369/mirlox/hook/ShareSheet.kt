package io.github.d4vram369.mirlox.hook

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Añade «Mirlo» a la hoja Compartir de X, con el aspecto de los destinos de su fila
 * (Copiar enlace, WhatsApp…).
 *
 * X pinta esa hoja con Jetpack Compose, en una ventana propia: no hay TextView ni listas donde
 * meter una vista. Lo que sí hay es el árbol de accesibilidad de Compose (el que leen TalkBack
 * y uiautomator), que da el texto, la posición y la acción "pulsar" de cada elemento. Con él:
 *  1. se localiza «Copiar enlace» y su fila;
 *  2. se pone encima de la ventana una vista propia, alineada con la primera columna de la fila;
 *  3. al pulsarla se pulsa «Copiar enlace» por accesibilidad y Clipboard.kt se queda el enlace.
 */
internal object ShareSheet {
    private const val MARK = "mirlo-send"
    private const val MIRLO_BLUE = 0xFF2446C7.toInt()
    private val COPY_LABELS = listOf("copiar enlace", "copy link", "copiar link", "copiar vínculo")
    private val RETRY_AT_MS = longArrayOf(150, 400, 900, 1600) // la hoja entra animada y se rellena tarde
    private const val MAX_NODES = 600 // tope por si el árbol es enorme (una ventana que no es la hoja)
    private val main = Handler(Looper.getMainLooper())

    /** API oculta de AccessibilityNodeInfo: id del hijo i (id virtual en los 32 bits altos). */
    private val getChildId by lazy {
        AccessibilityNodeInfo::class.java.getDeclaredMethod("getChildId", Int::class.javaPrimitiveType)
    }

    fun hook() {
        // Toda ventana nueva del proceso pasa por aquí: diálogos, popups y las ventanas de Compose.
        val n = hookAll(Class.forName("android.view.WindowManagerGlobal"), "addView", after { p ->
            (p.args[0] as? ViewGroup)?.let(::watch)
        })
        if (n == 0) log("WindowManagerGlobal.addView no encontrado: sin botón en la hoja")
    }

    private fun watch(root: ViewGroup) {
        fun attempt(i: Int) {
            if (!root.isAttachedToWindow || root.findViewWithTag<View>(MARK) != null) return
            val found = runCatching { find(root) }.onFailure { log("buscando la hoja: $it") }.getOrNull()
            when {
                found != null -> inject(root, found)
                i + 1 < RETRY_AT_MS.size -> main.postDelayed({ attempt(i + 1) }, RETRY_AT_MS[i + 1] - RETRY_AT_MS[i])
                else -> log("ventana sin «Copiar enlace»: ${root.javaClass.name}")
            }
        }
        main.postDelayed({ attempt(0) }, RETRY_AT_MS[0])
    }

    /** Lo que hace falta de la hoja: quién la pinta y los ids de «Copiar enlace» y de su fila. */
    private class Sheet(
        val provider: AccessibilityNodeProvider,
        val copyId: Int,     // el elemento pulsable «Copiar enlace»
        val labelId: Int,    // su texto (para medir la tipografía)
        val firstId: Int,    // el primer elemento de la fila (para alinear la columna)
    )

    private fun find(root: View): Sheet? {
        for (host in composeHosts(root)) {
            val provider = host.accessibilityNodeProvider ?: continue
            // recorrido en anchura del árbol virtual, apuntando el padre de cada nodo
            val parent = HashMap<Int, Int>()
            val children = HashMap<Int, List<Int>>()
            val queue = ArrayDeque(listOf(AccessibilityNodeProvider.HOST_VIEW_ID))
            var visited = 0
            while (queue.isNotEmpty() && visited++ < MAX_NODES) {
                val id = queue.removeFirst()
                val info = provider.createAccessibilityNodeInfo(id) ?: continue
                val text = (info.text ?: info.contentDescription)?.toString()?.trim()?.lowercase()
                if (text != null && COPY_LABELS.any { text.startsWith(it) }) {
                    // lo que X escucha al pulsar: el propio texto o el primer antecesor pulsable
                    val copy = generateSequence(id) { parent[it] }
                        .firstOrNull { provider.createAccessibilityNodeInfo(it)?.isClickable == true } ?: return null
                    val siblings = parent[copy]?.let { children[it] }.orEmpty()
                    val first = siblings.minByOrNull { bounds(provider, it)?.left ?: Int.MAX_VALUE } ?: copy
                    return Sheet(provider, copy, id, first)
                }
                val kids = (0 until info.childCount).map { ((getChildId.invoke(info, it) as Long) ushr 32).toInt() }
                    .filter { it != AccessibilityNodeProvider.HOST_VIEW_ID && it !in parent }
                children[id] = kids
                kids.forEach { parent[it] = id; queue += it }
            }
        }
        return null
    }

    /** Las vistas con árbol de accesibilidad virtual (AndroidComposeView, aunque X la ofusque). */
    private fun composeHosts(v: View): Sequence<View> = sequence {
        if (v.accessibilityNodeProvider != null) yield(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) yieldAll(composeHosts(v.getChildAt(i)))
    }

    private fun bounds(provider: AccessibilityNodeProvider, id: Int): Rect? =
        provider.createAccessibilityNodeInfo(id)?.let { info -> Rect().also(info::getBoundsInScreen) }
            ?.takeIf { !it.isEmpty }

    private fun inject(root: ViewGroup, sheet: Sheet) {
        val copy = bounds(sheet.provider, sheet.copyId) ?: return
        val label = bounds(sheet.provider, sheet.labelId) ?: copy
        val item = buildItem(root, sheet, copy, label)
        root.addView(item, FrameLayout.LayoutParams(copy.width(), copy.height(), Gravity.TOP or Gravity.START))

        // La hoja entra animada, se puede arrastrar y la fila se desplaza: en cada fotograma se
        // recoloca según dónde esté ahora el primer elemento de la fila. Si ya no está, se oculta.
        val gap = dp(root, 12f)
        val origin = IntArray(2)
        val follow = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val first = runCatching { bounds(sheet.provider, sheet.firstId) }.getOrNull()
                val now = runCatching { bounds(sheet.provider, sheet.copyId) }.getOrNull()
                if (first == null || now == null) {
                    item.visibility = View.INVISIBLE
                    return true
                }
                root.getLocationOnScreen(origin)
                item.visibility = View.VISIBLE
                item.translationX = (first.left - origin[0]).toFloat()
                item.translationY = (now.top - origin[1] - now.height() - gap).toFloat()
                return true
            }
        }
        root.viewTreeObserver.addOnPreDrawListener(follow)
        item.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = root.viewTreeObserver.removeOnPreDrawListener(follow)
        })
        follow.onPreDraw()
        log("botón añadido a la hoja Compartir")
    }

    /** Círculo con el mirlo y la etiqueta debajo, con las proporciones de «Copiar enlace». */
    private fun buildItem(root: ViewGroup, sheet: Sheet, copy: Rect, label: Rect): View {
        val context = root.context
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES // ponytail: tema del sistema; si X va en otro tema, el texto desentona
        val top = label.top - copy.top                      // círculo + márgenes, encima del texto
        val margin = (top * 0.13f).toInt()
        val diameter = top - 2 * margin

        val circle = ImageView(context).apply {
            val oval = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(MIRLO_BLUE) }
            background = RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), oval, null)
            setImageDrawable(birdIcon(context)?.mutate()?.apply { setTint(Color.WHITE) })
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = (diameter * 0.24f).toInt()
            setPadding(pad, pad, pad, pad)
            isDuplicateParentStateEnabled = true // el ripple del círculo responde al pulsar todo el elemento
        }
        val text = TextView(context).apply {
            text = "Mirlo"
            setTextColor(if (dark) 0xFFE7E9EA.toInt() else 0xFF0F1419.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, label.height() / 1.17f) // alto de línea → tamaño de letra
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        return LinearLayout(context).apply {
            tag = MARK
            contentDescription = sendLabel()
            isClickable = true
            setOnClickListener { send(context, sheet) }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(circle, LinearLayout.LayoutParams(diameter, diameter).apply { topMargin = margin; bottomMargin = margin })
            addView(text, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, label.height()))
        }
    }

    private fun send(context: android.content.Context, sheet: Sheet) {
        Clipboard.arm(context)
        if (!sheet.provider.performAction(sheet.copyId, AccessibilityNodeInfo.ACTION_CLICK, null)) {
            Clipboard.disarm()
            log("«Copiar enlace» no respondió a la pulsación por accesibilidad")
            toast(context, "Mirlo: no he podido pulsar «Copiar enlace»", "Mirlo: could not press \"Copy link\"")
            return
        }
        main.postDelayed({
            if (Clipboard.disarm()) {
                log("X no copió ningún enlace tras pulsar «Copiar enlace»")
                toast(context, "Mirlo: X no ha dado el enlace de este post", "Mirlo: X did not give the link to this post")
            }
        }, 2_600)
    }

    /**
     * El icono vive en los recursos del módulo, no en los de X. getResourcesForApplication con el
     * ApplicationInfo que da LSPatch los abre sin necesitar que X "vea" el paquete del módulo.
     */
    private var bird: Drawable? = null
    private fun birdIcon(context: android.content.Context): Drawable? = bird ?: runCatching {
        val res = context.packageManager.getResourcesForApplication(xposed.moduleApplicationInfo)
        res.getDrawable(res.getIdentifier("ic_mirlo", "drawable", MODULE_PACKAGE), null)
    }.onFailure { log("sin icono del módulo: $it") }.getOrNull()?.also { bird = it }

    private fun dp(v: View, value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, v.resources.displayMetrics).toInt()
}
