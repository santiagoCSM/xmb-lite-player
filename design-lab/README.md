# XMB Lite · Laboratorio visual

Un espacio de **revisión y comparación visual** para XMB Lite Player. La interfaz del laboratorio es sobria y se mantiene separada de la UI de Android.

## Abrir

Desde la raíz del repositorio:

```sh
python3 -m http.server 8000
```

Abre http://localhost:8000/design-lab/ o el despliegue de GitHub Pages: https://santiagocsm.github.io/xmb-lite-player/

## Uso

- Elige una pantalla en la navegación lateral.
- A la izquierda verás la **captura original** tomada del Redmi 9T y guardada en `docs/capturas-ui/`.
- A la derecha verás una **aproximación web** del mismo diseño.
- Puedes alternar únicamente el fondo azul/violeta y mostrar/ocultar la tarjeta de reproducción para revisar estados. **No son configuraciones de diseño**.
- No existen controles de posición, escala, opacidad, importación/exportación ni modificaciones desde el navegador.

## Flujo de trabajo con un LLM

1. Explica al LLM qué bloque quieres mover o qué detalle quieres corregir y, de ser posible, señala la pantalla y su captura de referencia.
2. El LLM edita la composición de la vista web en `design-lab/editor.js` (datos `SCREENS` y `DEFAULTS`) o sus estilos en `design-lab/styles.css`.
3. Revisa el resultado publicado y solicita ajustes adicionales. Cada cambio queda registrado en Git.
4. Cuando la propuesta esté aprobada, el LLM traslada los cambios pertinentes a `XmbMenuView.kt` considerando unidades dp, dimensiones, recortes e interacción real.
5. Verifica la aplicación en el Redmi 9T, ejecuta pruebas, toma nuevas capturas y compáralas.

**La web no es la app Android**: no ejecuta Media3 ni los fondos Canvas en tiempo real. Tampoco convierte automáticamente CSS en Kotlin. La referencia funcional sigue siendo la aplicación nativa.

## Recursos y licencias

El laboratorio lee los VectorDrawable de `app/src/main/res/drawable/ic_xmb_*.xml` para mantener los iconos originales del proyecto. No redistribuye gráficos propietarios de Sony. Consulta [ICONOGRAPHY.md](../docs/ICONOGRAPHY.md).

Las capturas de listas pueden ocultar nombres privados. Los ejemplos web usan títulos de prueba y no reconstruyen datos ocultos.

## Despliegue

El workflow [`Deploy XMB Design Lab`](../.github/workflows/design-lab-pages.yml) publica solo el laboratorio y las dependencias visuales que necesita: capturas, vectores y documentación. No publica APKs, Kotlin ni el resto del proyecto Android. Se activa al actualizar el laboratorio o esos recursos, y puede ejecutarse manualmente desde GitHub Actions.
