package io.github.d4vram369.mirlox.hook

import android.app.AndroidAppHelper
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.d4vram369.mirlox.PostId
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
    runCatching { XposedBridge.log("$TAG: $message") }
}

internal fun isSpanish() = Locale.getDefault().language == "es"
internal fun sendLabel() = if (isSpanish()) "Enviar a Mirlo" else "Send to Mirlo"

internal fun toast(context: Context, es: String, en: String) =
    runCatching { Toast.makeText(context, if (isSpanish()) es else en, Toast.LENGTH_SHORT).show() }

internal inline fun after(crossinline body: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun afterHookedMethod(param: MethodHookParam) {
        try { body(param) } catch (t: Throwable) { log("fallo en hook: ${Log.getStackTraceString(t)}") }
    }
}

internal inline fun before(crossinline body: (XC_MethodHook.MethodHookParam) -> Unit) = object : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        try { body(param) } catch (t: Throwable) { log("fallo en hook: ${Log.getStackTraceString(t)}") }
    }
}

/** El Application de X, para abrir la actividad del módulo desde cualquier sitio. */
internal object HostApp {
    @Volatile
    private var app: Application? = null

    val context: Context?
        get() = app ?: runCatching { AndroidAppHelper.currentApplication() }.getOrNull()

    fun hook() {
        XposedHelpers.findAndHookMethod(
            Instrumentation::class.java, "callApplicationOnCreate", Application::class.java,
            before { app = it.args[0] as Application },
        )
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
