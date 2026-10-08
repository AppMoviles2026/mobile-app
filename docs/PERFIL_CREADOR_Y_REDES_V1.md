# Perfil del creador y redes sociales — fase 4

Implementado el 8 de octubre de 2026 dentro de **Identity**, para US-13 y US-14 (posiciones 14 y 15 del backlog ordenado). No incluye gestión empresarial US-12, métricas, publicación de contenido ni endpoints nuevos.

## Funcionalidad conectada

| Operación | Uso móvil |
|---|---|
| `GET /profiles/me/creator` | Precarga del perfil de la sesión verificada |
| `PUT /profiles/me/creator` | Guardado de nombre público, biografía, nicho, audiencia y ubicación |
| `GET /accounts/me` | Actualiza el nombre de la sesión después del guardado, sin cambiar rol, JWT o vencimiento |
| `POST /social-accounts/{platform}/authorizations?client=ANDROID` | Inicia el permiso para Instagram o TikTok |
| `GET /social-accounts/authorizations/{authorizationId}` | Comprueba el resultado, propietario y plataforma del intento |
| `GET /social-accounts/me` | Lista real de todas las cuentas, incluyendo múltiples cuentas por red y permisos revocados |
| `GET /social-accounts/{platform}/callback` | Lo llama el navegador/proveedor al backend, **no Retrofit ni la app** |

El perfil empresarial y los demás contextos mantienen sus pantallas de prototipo. Se quitaron los datos ficticios del formulario creador, la audiencia no editable y los botones de autorización simulada. Las previews ahora utilizan los mismos composables puros que el recorrido real, con fixtures únicamente en `PreviewScreens.kt`.

## Separación DDD

- **Domain:** `CreatorProfile`, `CreatorProfileUpdate`, `SocialAccount`, `SocialAuthorization`, `AuthorizationAttempt` y el puerto `IdentityRepository`.
- **Application:** casos de uso existentes, `CreatorProfileValidation`, `SocialAuthorizationReturn` y actualización de metadatos en `AuthenticationSession`.
- **Infrastructure:** Retrofit, mapeadores, JWT restringido al backend y almacenamiento de sesión cifrado existentes. No hay clientes secretos de Instagram/TikTok en Android.
- **Presentation:** `CreatorIdentityViewModel`, estados independientes de perfil/lista/intento y composables `CreatorProfileScreen` / `LinkedSocialAccountsScreen` con callbacks. La Activity únicamente recibe enlaces y abre el navegador.

El coordinador de autenticación es compartido por Hilt: login, recuperación y perfil observan la misma sesión. Después de un PUT confirmado, se consulta la cuenta y se actualiza el nombre persistido y la cabecera. Si esa segunda operación falla, se informa que el perfil sí se guardó y se ofrece reintentar la actualización del nombre, sin repetir el guardado automáticamente.

## Perfil y errores

Nombre obligatorio y máximo 150 caracteres; nicho/ubicación máximo 150; biografía/audiencia máximo 2000. Los campos opcionales vacíos se envían como `null`. Se muestran validaciones locales y errores por campo del backend.

Carga, guardado y reintentos tienen estados visibles y evitan solicitudes duplicadas. Un fallo de carga no rellena muestras; un fallo al guardar conserva la edición. El éxito usa el perfil devuelto por el servidor. Un timeout no afirma que el PUT se guardó: se permite recargar el estado autoritativo, con confirmación si hay cambios sin guardar. Descartar cambios restaura la última respuesta correcta. El borrador se conserva en memoria al navegar, no se persiste en `SavedStateHandle` ni en disco.

## Autorización y retorno

1. Una sesión **CREATOR verificada** solicita una autorización al backend.
2. Se abre la URL HTTPS de la red oficial correspondiente en el navegador externo. No se envía el JWT de CollabPro al navegador/proveedor.
3. Instagram/TikTok redirige al **callback HTTPS del backend**, que valida el estado y registra el resultado.
4. Para `client=ANDROID`, el backend devuelve HTTP 303 a `collabpro://social-authorization-completed?authorizationId=UUID`.
5. Android valida el esquema, host y único parámetro UUID; no acepta `state`, `code`, tokens, parámetros duplicados, rutas ajenas ni fragmentos. Consulta el intento con el JWT vigente. **Un enlace o un regreso del navegador jamás equivale a éxito.**
6. Solo `SUCCEEDED` confirmado muestra el resultado exitoso; los estados terminales actualizan la lista real de cuentas. La lista puede fallar por separado: no se inventa una cuenta ni se convierte el fallo en lista vacía.

Estados: `PENDING`, `SUCCEEDED`, `FAILED` y `EXPIRED`. Se explican rechazo de permisos, duplicado, proveedor no configurado/fallido, intento desconocido/de otra cuenta, error de red y navegador no disponible. El backend define el vencimiento del intento (10 minutos); la app consulta su resultado, sin inferir permisos localmente.

Hay hasta tres consultas separadas por un segundo cuando el intento sigue pendiente. Después se permite comprobar manualmente. Cerrar o volver del navegador no se etiqueta como rechazo: podría seguir pendiente. Mientras hay un intento pendiente se deshabilita iniciar otro. «Dejar de seguir este intento» requiere confirmación y solo olvida el seguimiento local: **no cancela en el backend ni desvincula una cuenta**; esos endpoints no existen en esta versión.

El retorno funciona con la app abierta y en arranque frío. Si no hay sesión, se solicita login y después se verifica la propiedad en el servidor. Un login de empresa no abre el flujo del creador. Se conserva en `SavedStateHandle` únicamente el identificador opaco, propietario y plataforma, nunca la URL OAuth, `state`, códigos, JWT ni credenciales del proveedor. La URL para reabrir el navegador solo existe en memoria.

Si Android restaura el estado de la Activity tras muerte del proceso, se puede consultar el intento guardado. Si no restaura ese estado, el enlace de retorno aporta el UUID y el backend comprueba su propietario. Sin estado restaurado **y sin enlace**, no se puede descubrir un intento anterior porque no existe un endpoint para listar intentos. Cambiar/cerrar sesión elimina formularios, seguimiento local y respuestas tardías; los eventos pendientes no abren el navegador para otra cuenta.

Los enlaces de recuperación y OAuth tienen filtros separados y siguen compartiendo `MainActivity`, sin confundir sus parámetros. Los enlaces personalizados no acreditan por sí mismos al remitente: la autorización se verifica siempre en el backend.

## Configuración necesaria para usar cuentas reales

La app debug sigue utilizando `http://10.0.2.2:8081/api/v1/` en el emulador. Consulta `AUTENTICACION_MOVIL_V1.md` para arrancar el backend o configurar un teléfono físico. El APK generado usa esa dirección; el servidor de pruebas aislado en 18081 no es el servidor de desarrollo de la app.

El backend admite estas variables; deben configurarse **solo allí**, sin incluir valores secretos en Android o Git:

| Proveedor | Variables del backend |
|---|---|
| Instagram | `INSTAGRAM_CLIENT_ID`, `INSTAGRAM_CLIENT_SECRET`, `INSTAGRAM_REDIRECT_URI` |
| TikTok | `TIKTOK_CLIENT_KEY`, `TIKTOK_CLIENT_SECRET`, `TIKTOK_REDIRECT_URI` |

Registrar en cada consola la URL HTTPS pública exacta que llega al callback del backend: `/api/v1/social-accounts/instagram/callback` o `/api/v1/social-accounts/tiktok/callback`. **No registrar `collabpro://…` como redirect URI del proveedor**: ese enlace se utiliza después del callback del servidor. El proveedor necesita alcanzar el backend; `10.0.2.2`, localhost y una dirección LAN privada no sustituyen una URL pública de callback. La cuenta debe ser compatible con los permisos del adaptador actual. Si faltan credenciales, la app muestra el error real `PROVIDER_NOT_CONFIGURED`.

## Verificación

Resultado: **123 tests JVM aprobados, cero fallos y cero omisiones**, con ambos tests de integración activados; debug, release y APK de pruebas instrumentadas compilados. Lint se verifica sin errores. Las pruebas instrumentadas están preparadas, pero **no ejecutadas**: no hay dispositivo/emulador conectado. Tampoco se confirmó visualmente el retorno navegador→Android ni Android Keystore en un teléfono durante esta entrega.

Pruebas unitarias: límites y errores por campo, guardado/lectura, actualización de sesión, aislamiento de cuentas, respuestas tardías, enlaces inválidos, URL oficial, rechazo, duplicado, expiración, pendiente, fallo de consulta/lista, restauración del intento y eventos de navegador. Las pruebas HTTP existentes cubren los contratos Retrofit.

La integración ejecutada utiliza el backend existente con **MySQL 8.4 y Mailpit aislados**. Verifica perfil persistido y nombre de cuenta actualizado, denegación de perfiles a empresas, retorno 303 para Instagram/TikTok, estado `FAILED/AUTHORIZATION_DENIED`, callback de un solo uso y propiedad del intento. También se repite la regresión de registro/login/recuperación real. Para generar una URL inicial se usaron credenciales sintéticas de prueba; el callback se completa con `error=access_denied`, **sin llamadas ni autorizaciones exitosas reales a Instagram/TikTok**. Éxito/duplicado del flujo móvil se prueban con fixtures del puerto, no se presentan como evidencia de permiso concedido por una cuenta real.

Repetir contra un backend **aislado** en loopback, con MySQL/Mailpit y credenciales de prueba para generar la URL (no una base con datos de usuarios reales):

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:COLLABPRO_IDENTITY_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_MAILPIT='http://127.0.0.1:8029'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --console=plain --no-configuration-cache
```

Sin esas variables se omiten únicamente las dos integraciones opcionales; no se crean éxitos ficticios. Los contenedores/datos y el proceso de backend temporales de esta entrega se retiran al terminar, sin modificar SmartQuote ni la base de desarrollo.

En un teléfono/emulador con la app instalada y una sesión apropiada, probar un **UUID real del intento**:

```powershell
& 'C:/Users/fabio/AppData/Local/Android/Sdk/platform-tools/adb.exe' shell am start -W -a android.intent.action.VIEW -d 'collabpro://social-authorization-completed?authorizationId=UUID_DEL_INTENTO' com.example.collabpro
```

Un UUID inventado debe mostrar error, nunca vinculación. Verificar manualmente guardar y recargar perfil, iniciar permiso en ambas redes, conceder/rechazar permisos, duplicado, volver sin terminar, cerrar sesión en medio del flujo, rotación/arranque frío y recuperación de contraseña para descartar regresiones.

Referencias Android: [deep links y pruebas con adb](https://developer.android.com/training/app-links/create-deeplinks), [límites de SavedStateHandle](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate), [coroutines y lifecycle](https://developer.android.com/topic/libraries/architecture/coroutines).

Las fases 5–7 de campañas y postulaciones propias ya están implementadas en entregas posteriores; consulta `CAMPANAS_Y_CONDICIONES_V1.md`, `EXPLORACION_Y_DETALLE_V1.md` y `POSTULACIONES_PROPIAS_V1.md`. Sigue limpieza general y aceptación completa (fase 8).
