# Autenticación móvil real — fase 3

Implementada el 8 de octubre de 2026. Alcance: US-09, US-10 y US-11, incluidas en las primeras 18 posiciones ordenadas del Product Backlog. No se modifica el backend ni el reporte; se consumen los contratos actuales. No se añade refresh token, logout remoto, verificación de email ni funcionalidades de historias posteriores.

## Qué está conectado

| Pantalla/flujo | Contrato | Resultado |
|---|---|---|
| Registro empresa | `POST /api/v1/auth/brands` | Cuenta creada → login con correo precargado; sin sesión automática |
| Registro creador | `POST /api/v1/auth/creators` | Cuenta creada → login; sin sesión automática |
| Login | `POST /api/v1/auth/sessions` | JWT/expiración y rol del servidor; acceso solo tras confirmar el guardado cifrado |
| Arranque con sesión guardada | `GET /api/v1/accounts/me` | Verifica identidad, estado activo y tipo de cuenta antes del panel |
| Recuperar acceso | `POST /api/v1/auth/recovery-requests` | Muestra únicamente la confirmación recibida; no revela si el correo existe |
| Nueva contraseña | `POST /api/v1/auth/password-resets` | 204 → contraseña actualizada; borra sesión local y exige otro login |

Contraseñas nuevas: 8–128 caracteres, protegidas visualmente y confirmación en el cambio de contraseña. Login respeta el contrato actual: contraseña no vacía, máximo 128, sin imponer una longitud mínima diferente al backend. Se recortan nombre/correo al enviar, **no se recorta ni transforma la contraseña**. Se muestran errores HTTP/por campo del backend; 409 de registro no se inventa ni se oculta. Los formularios se bloquean mientras envían y una pulsación doble no duplica llamadas.

Se eliminaron los botones para simular credenciales inválidas/correo registrado, el selector Empresa/Creador del login y la navegación al panel sin respuesta del servidor. Empresa/Creador se elige únicamente al registrarse. El panel conectado muestra nombre y rol reales, sin contadores de campañas/postulaciones inventados. Las demás funcionalidades son vistas previas claramente identificadas.

## DDD, sesión y aislamiento

- **Domain:** `IdentityRepository`, `SessionStore`, Account/Session. El store expone un contador observable de cambios; la UI no observa JWT ni recibe tokens como argumentos de rutas.
- **Application:** `AuthenticationSession` coordina login, guardado, restauración verificada, logout local, expiración e invalidaciones. `AuthInputValidation` y `PasswordResetLink` son independientes de Android. Los casos de uso existentes gestionan registros y recuperación.
- **Infrastructure:** Retrofit/DTOs/mapeadores y AES-256-GCM/Android Keystore de fase 2. Hilt construye el coordinador para el ViewModel; las credenciales siguen excluidas de backups y logs.
- **Presentation:** `AuthenticationViewModel`, estados sin persistencia de campos sensibles y Screens con callbacks. Hilt y red no se ejecutan en previews.

Se conservan las previews de registro/login/recuperación y se agregan nueva contraseña, reset confirmado y verificación de sesión. Las rutas privadas solo se construyen con una cuenta verificada. Cambiar de cuenta o cerrar sesión elimina su árbol de pantallas y back stack; un rol/ruta guardados localmente no autorizan acceso. El rol del servidor no puede modificarse con `AppState.selectRole` o `restore`.

El login fallido mantiene el formulario para corregirlo. Salir de un formulario cancela su solicitud y limpia contraseñas; respuestas tardías no navegan ni guardan una cuenta anterior. El coordinador también comprueba generaciones de sesión antes de aceptar cambios, evitando que un login/restauración tardíos reviertan logout o un login nuevo.

La sesión se verifica al arrancar, sin confiar en el perfil/rol serializado. Una falla de red conserva la credencial pero muestra una puerta de verificación con Reintentar/Cerrar sesión, sin abrir el panel. Un 401 protegido invalida la sesión original y la UI vuelve al login. La expiración se controla con temporizador y comprobación al volver al primer plano. No hay renovación automática ni permisos derivados de claims decodificados localmente.

Logout elimina únicamente la credencial **local**, porque no existe endpoint de logout remoto. Un fallo de persistencia se muestra; no se afirma que un guardado/borrado fallido haya sido exitoso. Cambiar contraseña sí revoca sesiones en el backend. La prueba real confirma que el JWT anterior ya no accede, aunque su vencimiento nominal sea futuro.

## Recuperación y enlaces

El backend manda un enlace `collabpro://password-reset?token=...`, válido 30 minutos y de uso único. Android registra solamente ese esquema/host en MainActivity y maneja tanto arranque frío como `onNewIntent`. El parser rechaza URL externa, host incorrecto, token duplicado/ausente, fragmentos y contenido inválido. No se intenta validar localmente si el token venció o ya fue usado: lo determina el backend.

El token se conserva exclusivamente en una propiedad privada del ViewModel; no aparece en UiState, rutas, SavedStateHandle, backups ni mensajes de diagnóstico. Se elimina del Intent después de recibirlo y de memoria al abandonar/completar el formulario. Sobrevive a rotación mediante ViewModel, no a muerte de proceso: si Android termina la app antes de enviar, abre otra vez el enlace del correo. El UI permite solicitar otro enlace o ir al login; un timeout no se presenta como éxito.

Con SMTP local, **Mailpit captura el correo; no se envía a Gmail ni a destinatarios externos**. Usa el enlace del correo más reciente: pedir otro invalida el previo.

## Ejecutar en emulador o celular

1. Desde `C:/Users/fabio/Documents/platform`, iniciar las dependencias y la API. Mantener su base de desarrollo existente; no borrar volúmenes:

   ```powershell
   docker compose up -d
   .\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'
   ```

2. Abrir CollabPro en Android Studio, sincronizar y ejecutar `app`. Debug usa `http://10.0.2.2:8081/api/v1/`: la dirección del equipo vista desde el emulador. Docker abierto por sí solo no inicia esta API.
3. Para celular físico en Wi-Fi, compilar con la IP LAN real del equipo y permitir el puerto de desarrollo en el firewall:

   ```powershell
   .\gradlew.bat :app:assembleDebug '-Pcollabpro.debugBaseUrl=http://192.168.1.20:8081/api/v1/'
   ```

   Esa IP es un ejemplo. También puede configurarse `collabpro.debugBaseUrl` en el archivo personal `%USERPROFILE%/.gradle/gradle.properties` para los builds de Android Studio. Otra opción por USB es `adb reverse tcp:8081 tcp:8081` y configurar `http://127.0.0.1:8081/api/v1/`.
4. Registrarse como empresa/creador e iniciar sesión. Solicitar recuperación y revisar el correo en **Mailpit desde el equipo**, `http://localhost:8025`. Es loopback en la configuración Compose; no se supone accesible por Wi-Fi desde el celular. Con un dispositivo conectado, abrir el enlace copiado del correo así:

   ```powershell
   & 'C:/Users/fabio/AppData/Local/Android/Sdk/platform-tools/adb.exe' shell am start -W -a android.intent.action.VIEW -d 'collabpro://password-reset?token=TOKEN_DEL_CORREO' com.example.collabpro
   ```

   Sustituir el placeholder por el token del correo, sin publicarlo ni compartir capturas de él. Completar nueva contraseña, esperar confirmación y hacer login nuevamente.

APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Release sigue requiriendo URL HTTPS productiva y firma de distribución; no contiene secretos de firma JWT/SMTP/backend. No usar el servidor de prueba de la siguiente sección como URL permanente de la app.

## Pruebas y evidencia

Verificación de esta entrega: **82 pruebas JVM aprobadas**, incluyendo 1 integración real con MySQL 8.4.11 y SMTP/Mailpit. Se comprobaron ambos registros y roles, correo duplicado, login incorrecto, candidato de sesión verificado, confirmación genérica para correo inexistente, recepción del correo, parser de su enlace real, reset 204, uso único, JWT revocado, contraseña anterior rechazada y nueva contraseña aceptada. Pruebas adicionales cubren cifrado, endpoints, permisos, expiración, cancelación, cambios de cuenta, formularios y límites DDD.

Para pruebas sin servicios:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

La integración real es opt-in; sin sus variables queda omitida. Para repetirla, primero preparar un **backend/base y Mailpit aislados y descartables**, nunca datos de usuarios reales. En esta entrega se usaron puertos API 18081, MySQL 33079, SMTP 10279 y UI Mailpit 8029:

```powershell
$env:COLLABPRO_AUTH_TEST_API = 'http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_MAILPIT = 'http://127.0.0.1:8029'
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-configuration-cache
```

El test utiliza correos únicos `@example.test` y no muestra passwords/JWT/enlaces en consola. Gradle registra esas dos variables como inputs para no reutilizar un resultado omitido al activar la integración.

Las tres pruebas Compose instrumentadas de autenticación están preparadas en `AuthenticationScreensTest` y el APK de tests se compila. **Su ejecución/renderizado en Android, Android Keystore y el enlace desde un cliente de correo físico permanecen sin verificar: no había dispositivo ni AVD conectado.** En un dispositivo usar `:app:connectedDebugAndroidTest` y revisar las previews en Android Studio. Las pruebas JVM no sustituyen esa comprobación visual.

Los servicios de prueba se crean por separado de tus contenedores SmartQuote y se retiran al finalizar. No se borran bases/volúmenes del proyecto ni se modifican servicios del usuario.

Perfil/redes y preparación de campañas ya están implementados; consulta `PERFIL_CREADOR_Y_REDES_V1.md` y `CAMPANAS_Y_CONDICIONES_V1.md`. Los demás recorridos continúan como prototipo; sigue exploración y detalle para el creador.

Referencias de implementación: [Hilt ViewModels](https://dagger.dev/hilt/view-model.html) y [estado en Compose con ciclo de vida](https://developer.android.com/develop/ui/compose/state).
