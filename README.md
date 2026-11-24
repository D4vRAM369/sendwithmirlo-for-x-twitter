# Mirlo para X (módulo LSPatch)

Añade **Enviar a Mirlo** a la app de X en Android. El post no se descarga en el móvil: se deja
su ID en una carpeta que Syncthing lleva a `~/Mirlo/.inbox/` del ordenador, y Mirlo-desktop lo
guarda como si lo hubieras pegado (necesita el parche de bandeja en Mirlo-desktop).

```
X (parcheado con LSPatch)                    Mirlo para X                 PC
 Compartir ▸ Enviar a Mirlo ──ID──▶ SendActivity ──▶ <ID>.mirlo ══Syncthing══▶ ~/Mirlo/.inbox ▶ Mirlo
 Compartir vía… ▸ Enviar a Mirlo ──────┘ (funciona también sin módulo)
```

## Cómo saca el ID sin depender de X

X ofusca su código y cambia los nombres de sus clases en cada versión, así que el módulo solo se
engancha a API de Android:

| Hook | Qué hace |
|---|---|
| `Dialog.show` / `PopupWindow.show*` | Busca la fila «Copiar enlace» del menú y añade debajo «Enviar a Mirlo» con su mismo aspecto. |
| `ClipboardManager.setPrimaryClip` | Al pulsar el botón de Mirlo se pulsa por debajo «Copiar enlace»; aquí se coge el enlace que X intenta copiar, **sin tocar tu portapapeles**. Si no se ha pulsado el botón, no hace nada. |
| `Intent.createChooser` | Si X abre el selector de Android con un post, pone «Enviar a Mirlo» el primero. |

Si el menú de tu versión de X está hecho con Compose u otra estructura, no se toca nada y se
vuelca el árbol de vistas al log para adaptar la hook:

```
adb logcat -s MirloX
```

(Los textos van recortados a 32 caracteres para que no acabe un post entero en el log.)

## Compilar

Abre la carpeta en Android Studio (genera el wrapper de Gradle) o, con Gradle instalado:

```
gradle wrapper --gradle-version 8.11.1
./gradlew assembleRelease
```

Fija AGP 8.7.3 y Kotlin 2.1.0. Si tu Android Studio pide versiones más nuevas, súbelas en
`build.gradle.kts`. R8 está desactivado a propósito: renombraría `hook.XHook`, que es lo que
LSPatch busca en `assets/xposed_init`.

## Instalar

1. **Syncthing**: comparte una carpeta entre el móvil (p. ej. `Sync/MirloInbox`) y
   `~/Mirlo/.inbox` del PC. No hace falta que se llamen igual.
2. Instala el APK de **Mirlo para X**, ábrelo y elige esa carpeta.
3. En **LSPatch**, parchea X en modo *local* y activa este módulo para `com.twitter.android`.
   El APK de Mirlo para X tiene que quedarse instalado: es quien escribe en la carpeta.
4. En X: Compartir ▸ **Enviar a Mirlo**.

Cada envío deja un archivo `<ID>.mirlo`. Mirlo-desktop mira la bandeja cada 5 s mientras está
abierto (también en la bandeja del sistema), mete los IDs en la misma cola que `mirlo://save/` y
borra los archivos. Syncthing propaga ese borrado al móvil, así que la cola se vacía en los dos lados.

## Privacidad

- El módulo no hace peticiones de red ni lee cookies de X. Solo ve el enlace que X ya iba a copiar.
- La app no pide permisos: escribe en la carpeta que eliges con el selector de Android.
- Lo que llega al PC es un número. Mirlo-desktop lo valida igual que un enlace pegado.

## Licencia

GPL-3.0-or-later, como Mirlo-desktop.
