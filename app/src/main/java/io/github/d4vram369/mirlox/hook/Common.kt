package io.github.d4vram369.mirlox.hook

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import io.github.d4vram369.mirlox.PostId
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.Locale

// Todo lo de este paquete se ejecuta DENTRO del proceso de X. Aquí no existen los recursos del
// módulo (R.string…): los textos van en código y cualquier fallo se registra y se traga, porque
// una excepción que se escape aquí tumba la app de X.

internal const val X_PACKAGE = "com.twitter.android"
internal const val MODULE_PACKAGE = "io.github.d4vram369.mirlox"
private const val SEND_ACTIVITY = "$MODULE_PACKAGE.SendActivity"
private const val TAG = "MirloX"

internal fun log(message: String) {
    Log.i(TAG, message)
}

internal fun isSpanish() = Locale.getDefault().language == "es"
internal fun sendLabel() = if (isSpanish()) "Enviar a Mirlo" else "Send to Mirlo"

internal fun toast(context: Context, es: String, en: String) =
    runCatching { Toast.makeText(context, if (isSpanish()) es else en, Toast.LENGTH_SHORT).show() }

/** El módulo ya cargado (lo fija MirloModule). A través de él se instalan las hooks. */
internal lateinit var xposed: XposedInterface

/** Lo que ve una hook, al estilo de la API clásica: argumentos, objeto y resultado. */
internal class Param(private val chain: XposedInterface.Chain) {
    val args: List<Any?> get() = chain.args
    val thisObject: Any? get() = chain.thisObject
    var result: Any? = null
    var skipped = false
        private set

    /** Desde un `before`: no se llama al método original y devuelve [result]. */
    fun skip(result: Any? = null) {
        skipped = true
        this.result = result
    }
}

internal class Hook(val before: ((Param) -> Unit)? = null, val after: ((Param) -> Unit)? = null)

internal fun before(body: (Param) -> Unit) = Hook(before = body)
internal fun after(body: (Param) -> Unit) = Hook(after = body)

/**
 * Equivalente de findAndHookMethod. Un fallo DENTRO de la hook se registra y se traga (X sigue);
 * una excepción del método original sí se propaga, como si no hubiera hook.
 */
internal fun hook(method: Executable, h: Hook) {
    xposed.hook(method).intercept { chain ->
        val p = Param(chain)
        h.before?.let { guarded { it(p) } }
        if (p.skipped) return@intercept p.result
        p.result = chain.proceed()
        h.after?.let { guarded { it(p) } }
        p.result
    }
}

internal fun hook(cls: Class<*>, name: String, vararg types: Class<*>, h: Hook) =
    hook(cls.getDeclaredMethod(name, *types), h)

/** Todas las sobrecargas de [name] (equivale a hookAllMethods). Devuelve cuántas enganchó. */
internal fun hookAll(cls: Class<*>, name: String, h: Hook): Int =
    cls.declaredMethods.filter { it.name == name }.onEach { hook(it, h) }.size

private inline fun guarded(body: () -> Unit) {
    try { body() } catch (t: Throwable) { log("fallo en hook: ${Log.getStackTraceString(t)}") }
}

/** El Application de X, para abrir la actividad del módulo desde cualquier sitio. */
internal object HostApp {
    @Volatile
    private var app: Application? = null

    val context: Context?
        get() = app ?: runCatching {
            // API oculta de Android: devuelve el Application del proceso actual
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application?
        }.getOrNull()

    fun hook() {
        hook(Instrumentation::class.java, "callApplicationOnCreate", Application::class.java,
            h = before { app = it.args[0] as Application })
    }
}

/** Le pasa el ID a SendActivity (la parte normal de la app), que es quien escribe el archivo. */
internal object Sender {
    fun intent(id: String): Intent = Intent(Intent.ACTION_SEND)
        .setClassName(MODULE_PACKAGE, SEND_ACTIVITY)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, PostId.url(id))

    fun send(context: Context, id: String) {
        try {
            context.startActivity(intent(id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log("enviado $id")
        } catch (t: Throwable) {
            log("no se pudo abrir SendActivity: $t")
            toast(context, "Mirlo: falta la app «Mirlo para X» instalada", "Mirlo: the \"Mirlo for X\" app is not installed")
        }
    }
}
