package io.github.d4vram369.mirlox.hook

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Entrada del módulo (META-INF/xposed/java_init.list), con la API moderna de Xposed (libxposed
 * 101+). LSPatch 1.2 ya no carga módulos con la API clásica 82: los reconoce pero arranca la app
 * con "Module snapshot holds 0 module(s)".
 *
 * Solo se engancha a API de Android, nunca a clases de X: X ofusca su código y cambia los nombres
 * en cada versión, Android no.
 */
class MirloModule : XposedModule() {
    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != X_PACKAGE || !param.isFirstPackage) return
        xposed = this
        log("cargado en ${param.packageName}")
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
