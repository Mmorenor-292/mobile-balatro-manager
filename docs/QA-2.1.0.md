# QA de Balatro Manager 2.1.0 — 2026-09-14

Entrega Preview aprobada por Mauricio. Conserva Home/Mods/Apariencia y añade catálogo filtrable, ficha, instalación y selección de versiones publicadas.

## Verificación
- UI: 8 pruebas y lint correctos. Android: 30 pruebas unitarias correctas. Build debug con sufijo `.preview` instalado en Android 16, emulator-5570, sin cuentas personales.
- Carpeta aislada: `/sdcard/Documents/MBM-QA-20260914/Mods`.
- Catálogo real: 319 entradas de origen; 310 después de deduplicar repositorios identificables.
- Instalación real de Steamodded (preparación) y Handy 2.0.6 (APK nueva).
- Desactivación de Handy; downgrade real 2.0.6 → 2.0.5; actualización 2.0.5 → 2.0.6 pulsada desde la interfaz. Ambas conservaron `.lovelyignore`, estado inactivo y una sola carpeta Handy.
- Correcciones: preservar desactivación al reemplazar; rechazar instalaciones duplicadas y nombres alternativos del proveedor; ignorar alias vacíos al comparar identidad; mantener preferencia por entradas instaladas en el catálogo.
- Reinstalación final y revisión visual de Descubrir/ficha; margen inferior aumentado para separar acciones de la navegación Android.

## Entrega
APK: `G:\Mi unidad\Codex App\outputs\mobile-balatro-manager\Entrega V2 (2026-09-14)\Balatro Manager 2.1.0 Preview.apk`
SHA256: `FA00069F115E6C7F70797F806C17171ED4134518DE114526FAFB532C5A4740CC`
Paquete: `cl.mauricio.balatromods.preview`, versionCode 25. Convive con la app anterior; actualiza una Preview instalada con la misma firma.

## Límites
Pendiente Xiaomi 14T y carga efectiva de mods dentro del juego. No se comprobó rollback ante un fallo del proveedor durante la restauración. El selector muestra publicaciones que ofrece el motor existente; no añade un canal beta universal. Algunas descripciones externas conservan Markdown sin renderizar. La prueba confirma operaciones del manager, no compatibilidad móvil de todo el catálogo.