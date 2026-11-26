# XMB Lite Player

Reproductor multimedia nativo ultraligero para Android, con un diseño original inspirado en la navegación horizontal y vertical XMB.

**Estado: prototipo 0.1.0.** El menú Música / Video / Ajustes funciona con gestos y controles direccionales, pero **todavía no reproduce archivos multimedia**.

## Fondos animados

Elige el fondo en **Ajustes → Apariencia → Fondo animado**. Capturas tomadas en un Redmi 9T.

| Ondas azules | Onda violeta |
| --- | --- |
| <img src="docs/capturas/ondas-azules.png" alt="Menú XMB con el fondo Ondas azules" width="240"> | <img src="docs/capturas/onda-violeta.png" alt="Menú XMB con el fondo Onda violeta" width="240"> |

## Plataforma objetivo

- Android 16, API 36; pruebas previstas en Redmi 9T ARM64 con crDroid.
- Kotlin + Android View y Canvas: sin Compose, AppCompat ni motor gráfico externo.
- Sin anuncios, telemetría, red ni permisos de almacenamiento en el prototipo.
- Objetivos pendientes de medir: animaciones a 60 FPS, bajo consumo y APK menor a 5 MB.

## Compilar en Linux Mint sin Android Studio

Requisitos: **JDK 17**, Android SDK con plataforma API 36, build-tools 36.0.0, curl, unzip y sha256sum.

```bash
git clone https://github.com/santiagoCSM/xmb-lite-player.git
cd xmb-lite-player
export ANDROID_HOME="$HOME/Android/Sdk"
bash scripts/gradle.sh assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El script descarga **Gradle 8.13** en `~/.gradle/xmb-lite-bootstrap` si todavía no está presente, verifica SHA-256 y lo ejecuta; no es el Gradle Wrapper oficial porque no incluye el JAR binario `gradle-wrapper.jar`. Si ya dispones de Gradle 8.13, puedes usar `gradle assembleDebug` directamente.

El plugin Android es AGP 8.13.2, con Kotlin 2.2.20. La primera compilación necesita Internet para descargar Gradle y dependencias.

## Controles

- Deslizar **izquierda/derecha**: cambiar categoría.
- Deslizar **arriba/abajo**: recorrer las opciones.
- Compatible también con las teclas D-pad.
- Tocar **Apariencia** o pulsar **Enter** con esa opción seleccionada abre el selector de fondos.

## Estructura

- `app/src/main/java/com/santiagocsm/xmblite/MainActivity.kt`: menú XMB en Canvas.
- `app/src/main/AndroidManifest.xml`: configuración de Android.
- `app/build.gradle.kts`: versión de SDK y compilación.
- `scripts/gradle.sh`: arranque de Gradle desde terminal.
- `.github/workflows/android.yml`: compilación de APK de depuración y artefacto en Actions.

## Siguientes hitos

1. Verificar el primer APK en Android 16; medir memoria, arranque, FPS y tamaño real.
2. Descubrir música local con MediaStore y permisos apropiados.
3. Incorporar reproducción en segundo plano, controles multimedia y Media3.
4. Agregar reproducción de video y optimizaciones basadas en mediciones.

Este prototipo no utiliza iconos, sonidos ni otros recursos propietarios de Sony.
