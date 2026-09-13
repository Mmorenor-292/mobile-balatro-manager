# Mobile Balatro Manager

- La interfaz fuente vive en `ui/src`; `app/src/main/assets/web` se regenera con Vite antes de compilar Android.
- Antes de modificar este checkout, ejecutar `git fetch origin` y comprobar `git rev-list --left-right --count HEAD...origin/main`. Un Pull de FleetSync sin salida no demuestra que este checkout de staging esté actualizado. Preservar cambios y resolver las diferencias sin reset ni rebase forzado.
- Usar el estado de actualización y las versiones publicados por el bridge nativo. No sustituir su política por comparaciones locales cuando existe `updateAvailable`.
- Para comparar una APK con otra firma, compilar debug con `'-PqaApplicationIdSuffix=.preview'`; conservar la instalación previa.
- Los datos controlados usados en capturas no deben aparecer como inventario real al iniciar Android.
