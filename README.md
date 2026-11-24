# Mirlo para X (módulo LSPatch)

Añade **Enviar a Mirlo** a la app de X en Android. El post no se descarga en el móvil: se deja
su ID en una carpeta que Syncthing lleva a `~/Mirlo/.inbox/` del ordenador, y Mirlo-desktop lo
guarda como si lo hubieras pegado (necesita el parche de bandeja en Mirlo-desktop).

<p align="center">
  <img src="screenshots/hoja-compartir.png" width="540" alt="Hoja Compartir de X en modo oscuro: encima de la fila Compartir por…, Copiar enlace, WhatsApp, Historias y Messages aparece un círculo azul con un mirlo blanco y la etiqueta Mirlo.">
</p>

```
X (LSPatch / LSPosed)                        Mirlo para X                 PC
 Compartir ▸ Mirlo ───────────ID──▶ SendActivity ──▶ <ID>.mirlo ══Syncthing══▶ ~/Mirlo/.inbox ▶ Mirlo
 Compartir por… ▸ Enviar a Mirlo ──────┘ (funciona también sin módulo)
```

Sin PC encendido o sin Syncthing no se pierde nada: los `.mirlo` esperan en la carpeta del móvil
y se suben en cuanto Syncthing vuelve a ver el PC.

## Cómo saca el ID sin depender de X

X ofusca su código y cambia los nombres de sus clases en cada versión, así que el módulo solo se
engancha a API de Android:

| Hook | Qué hace |
|---|---|
| `WindowManagerGlobal.addView` | Cada ventana nueva de X. La hoja Compartir está hecha con Jetpack Compose (no tiene vistas clásicas), así que se lee su árbol de accesibilidad —el mismo que usa TalkBack— hasta encontrar «Copiar enlace», y encima de la fila se superpone el botón **Mirlo** con las mismas proporciones. Sigue a la hoja si se anima o se arrastra. |
| `ClipboardManager.setPrimaryClip` | Al pulsar **Mirlo** se pulsa por debajo «Copiar enlace»; aquí se coge el enlace que X intenta copiar, **sin tocar tu portapapeles**. Si no se ha pulsado el botón, no hace nada. |
| `Intent.createChooser` | Si X abre el selector de Android con un post, pone «Enviar a Mirlo» el primero. |
| `Instrumentation.callApplicationOnCreate` | Guarda el `Application` de X para poder abrir `SendActivity`. |

Para ver qué hace en tu versión de X:

```
adb logcat -s MirloX
```

Esperado: `cargado en com.twitter.android` al abrir X y `botón añadido a la hoja Compartir` al abrir
Compartir. Si una versión de X cambia el texto «Copiar enlace», sale `ventana sin «Copiar enlace»`.

## Compilar

Abre la carpeta en Android Studio (genera el wrapper de Gradle) o, con Gradle instalado:

```
gradle wrapper --gradle-version 8.11.1
./gradlew assembleRelease
```

Fija AGP 8.7.3 y Kotlin 2.1.0. Si tu Android Studio pide versiones más nuevas, súbelas en
`build.gradle.kts`.

Usa la **API moderna de Xposed** ([libxposed](https://github.com/libxposed/api) 102, desde Maven
Central, solo para compilar): LSPatch 1.2 ya no carga módulos con la API clásica 82. La entrada es
`hook.MirloModule`, declarada en `app/src/main/resources/META-INF/xposed/java_init.list`. R8 está
desactivado a propósito: renombraría esa clase.

## Instalar

1. **Syncthing**: comparte una carpeta entre el móvil (p. ej. `Sync/MirloInbox`) y
   `~/Mirlo/.inbox` del PC. No hace falta que se llamen igual.
2. Instala el APK de **Mirlo para X**, ábrelo y elige esa carpeta.
3. **Sin root (LSPatch):** parchea X en modo *local*. Luego **Gestionar ▸ Aplicaciones ▸ X ▸ Desde
   los módulos instalados**, marca *Mirlo para X*, **Añadir** y, en la barra de abajo, **Aplicar**
   (sin este último paso no se guarda). Fuerza la detención de X y ábrelo.
   **Con root (LSPosed):** activa el módulo con ámbito `com.twitter.android`. Hace falta un LSPosed
   que cargue módulos de la API moderna (100+); todavía sin probar.
   El APK de Mirlo para X tiene que quedarse instalado: es quien escribe en la carpeta.
4. En X: Compartir ▸ **Mirlo**.

Cada envío deja un archivo `<ID>.mirlo`. Mirlo-desktop mira la bandeja cada segundo mientras está
abierto (también en la bandeja del sistema), mete los IDs en la misma cola que `mirlo://save/` y
borra los archivos. Syncthing propaga ese borrado al móvil, así que la cola se vacía en los dos lados.

## Privacidad

- El módulo no hace peticiones de red ni lee cookies de X. Solo ve el enlace que X ya iba a copiar.
- La app no pide permisos: escribe en la carpeta que eliges con el selector de Android.
- Lo que llega al PC es un número. Mirlo-desktop lo valida igual que un enlace pegado.

## Licencia

GPL-3.0-or-later, como Mirlo-desktop.
