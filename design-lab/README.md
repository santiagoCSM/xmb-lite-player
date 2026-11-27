# XMB Lite · Design Lab

Editor de composición **experimental y sin dependencias** para ajustar la interfaz de XMB Lite Player antes de trasladar cambios a Android Canvas.

## Abrir

Desde la raíz del repositorio:

```sh
python3 -m http.server 8000
```

Visita **http://localhost:8000/design-lab/**. Requiere un servidor HTTP: abrir `index.html` con `file://` puede impedir cargar los VectorDrawable por restricciones del navegador. También funciona como sitio estático bajo GitHub Pages **si se habilita su publicación**; subir la carpeta no activa Pages automáticamente.

## Uso

1. Selecciona una pantalla; el panel izquierdo muestra la **captura real** del Redmi 9T de `docs/capturas-ui/`.
2. El panel derecho es una **aproximación HTML editable** con los mismos textos, iconos originales del repositorio, disposición y colores base.
3. Selecciona una capa y arrástrala, o cambia coordenadas, escala, distancia entre filas y opacidad del foco.
4. Alterna azul/violeta y tarjeta de reproducción para comparar estados (no todas las combinaciones disponen de captura original).
5. **Exportar JSON** guarda tanto valores en el espacio de 540 × 1170 px como posiciones normalizadas (0–1). **Importar JSON** recupera el diseño. Los ajustes también se conservan en el almacenamiento local del navegador.

No modifica archivos Kotlin, ni edita música, ni accede al teléfono. El botón **Restablecer** afecta solo a la copia de diseño local.

## Pasar ajustes a Kotlin

El archivo exportado **no es una configuración ejecutable por Android**. Antes de trasladar cambios:

- Convertir las posiciones normalizadas a las unidades y restricciones del Canvas (dimensiones reales, densidad, cortes de pantalla e insets).
- Comparar en el Redmi tanto la pantalla sin tarjeta como con tarjeta de reproducción.
- Mantener las rutas táctiles, el foco D-pad, la navegación Atrás, la accesibilidad y la lógica de Media3.
- Ejecutar `bash scripts/gradle.sh assembleDebug testDebugUnitTest lintDebug`, instalar el APK y tomar una nueva captura para comparación.

Las capturas de biblioteca incluyen datos ocultados para proteger nombres privados; el laboratorio usa etiquetas de prueba sintéticas, no intenta reconstruir contenido oculto.

## Recursos y licencia

Los vectores se leen **directamente de** `app/src/main/res/drawable/ic_xmb_*.xml` y se convierten a SVG en el navegador. No se copian iconos de PSP ni recursos propietarios. Véase [documentación y licencia de los iconos](../docs/ICONOGRAPHY.md).

## Limitaciones de esta primera versión

La forma y animación exacta de los fondos siguen siendo responsabilidad de `WaveBackgroundView`; aquí se representan con SVG y degradados estáticos de aproximación. Los textos también pueden variar entre las fuentes de Android y del navegador. Las capturas originales de la izquierda son la referencia visual; la aplicación Android sigue siendo la referencia funcional.

## Despliegue en GitHub Pages

El workflow [`Deploy XMB Design Lab`](../.github/workflows/design-lab-pages.yml) publica únicamente el editor y los recursos visuales que necesita (capturas, iconos XML y documentación de iconografía). **No publica el código fuente Kotlin ni APKs**, y no participa en la compilación Android.

Se ejecuta automáticamente cuando cambian el laboratorio, sus capturas, iconos o el propio workflow; también admite **Run workflow** desde GitHub Actions.

Para habilitarlo por primera vez: **Settings → Pages → Build and deployment → Source: GitHub Actions**. Después consulta la ejecución de `Deploy XMB Design Lab` en **Actions**. Cuando GitHub Pages termine de desplegar, la URL prevista es:

https://santiagocsm.github.io/xmb-lite-player/

La ruta `/design-lab/` también funciona directamente. El resultado público depende de que GitHub Pages esté habilitado y de que el workflow se complete correctamente.
