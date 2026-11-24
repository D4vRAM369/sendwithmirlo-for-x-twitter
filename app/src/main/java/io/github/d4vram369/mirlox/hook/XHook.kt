package io.github.d4vram369.mirlox.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Entrada del módulo (assets/xposed_init). Solo se engancha a API de Android, nunca a clases de
 * X: X ofusca su código y cambia los nombres en cada versión, Android no.
 */
class XHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != X_PACKAGE || !lpparam.isFirstApplication) return
        log("cargado en ${lpparam.processName}")
        for ((name, install) in listOf(
            "HostApp" to HostApp::hook,
            "Clipboard" to Clipboard::hook,
            "ShareSheet" to ShareSheet::hook,
            "Chooser" to Chooser::hook,
        )) {
            // si una falla (p. ej. un método que cambió en otra versión de Android) las demás siguen
            runCatching(install).onFailure { log("no se pudo instalar $name: $it") }
        }
    }
}
