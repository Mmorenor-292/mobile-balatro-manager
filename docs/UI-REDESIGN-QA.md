# Verificación del rediseño visual — 2026-09-12

## Alcance y referencia

Implementación React/WebView del mockup aprobado Home, Mods y Apariencia. Descubrir mantiene el mismo lenguaje visual. Tipografías Jersey 10 y Barlow Condensed, distribuidas con sus licencias OFL en ui/public/fonts. Fondo generado específicamente para esta interfaz; no se usa una imagen de pantalla como interfaz. Los assets personalizados de Aseprite siguen pospuestos.

El diseño se integró con origin/main 7ec8437 (motor 2.0.4). La copia local inicialmente estaba ocho commits atrás; se preservó el rediseño en 22120dd y se fusionó el upstream sin rebase ni descarte de sus correcciones. Los bundles WebView se regeneraron desde ui/src después de resolver el merge.

## Evidencia

- ESLint: sin errores.
- Vitest: 5 pruebas aprobadas, incluidos filtros, toggle, persistencia CRT, inicio nativo sin datos demo y payload exacto de selección de versión anterior.
- JUnit Android: 28 pruebas aprobadas, 0 fallos, 0 errores.
- assembleDebug con sufijo .preview: correcto.
- Instalación en BMM_Public_GooglePlay_API36, Android 16, emulator-5570: correcta, junto al manager anterior.
- Verificación en WebView real: fuentes locales cargadas; ancho 411 dp sin desbordamiento; Home, Mods y panel de Apariencia comparados con el mockup.
- Filtro inactivo devuelve los 3 mods inactivos de una colección controlada de 12.
- CRT se desactiva y deshabilita su deslizador; fondo oscuro y preferencias persisten.
- Selector ACTION_OPEN_DOCUMENT abre Android DocumentsUI y FileReader carga correctamente una imagen elegida de 1,71 MB (data URL de 2.281.698 caracteres).
- Atrás de Android 16 cierra el panel y vuelve de Mods a Home. Corregido usando OnBackInvokedDispatcher desde API 33.

Las capturas Home/Mods/Apariencia usan datos controlados inyectados únicamente durante QA para comparar con el mockup (12/9/3). La APK nativa no contiene una colección simulada visible: inicia con el estado real del bridge.

## Entregable y límites

APK de vista previa debug: cl.mauricio.balatromods.preview. Versión del motor: 2.0.4.
SHA256: 498EBD062E6953F3A52E4EA1AB281BB828EC8C746BC9B1F5E8AF1B49A9255DFA.

Entregables: G:\Mi unidad\Codex App\outputs\mobile-balatro-manager\Implementación V1 (2026-09-12).

No se probó en el Xiaomi 14T ni se certificó que cada mod del catálogo funcione en Balatro móvil. No se hizo una reinstalación real de mods de usuario. El cambio de versiones usa las publicaciones que ofrece el motor; no inventa versiones beta ni soporta automáticamente cualquier historial Git. Se conserva el motor existente, no es una reescritura del instalador.

## Repetición mínima

Construir Vite antes de Android. Ejecutar npm --prefix ui run lint, npm --prefix ui test y Gradle :app:testDebugUnitTest :app:assembleDebug con '-PqaApplicationIdSuffix=.preview'. Probar la APK aislada en Android 16 y verificar una sola vez las pantallas, fondo personalizado y Atrás. Para probar updates reales, usar primero una colección prescindible.
