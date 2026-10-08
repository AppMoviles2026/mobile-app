# Base móvil DDD V1 — fase 2

Implementada el 8 de octubre de 2026 en CollabPro. Sigue el orden del `PLAN_IMPLEMENTACION_FINAL_V1.md` y la separación de capas de EasyVet. **La infraestructura está preparada; las pantallas existentes todavía son un prototipo y no invocan estos casos de uso.** Esta entrega no implementa funciones de las historias posteriores a las primeras 18 posiciones del backlog.

## Capas y límites

```text
features/identity
  domain/model                  Account, Session, CreatorProfile, redes e intentos
  domain/repositories           IdentityRepository, SessionStore
  application/usecases          registro, autenticación, recuperación, perfil y redes
  infrastructure/remote         IdentityApi, DTOs y mapeadores
  infrastructure/session        almacenamiento cifrado y Android Keystore
  infrastructure/di             proveedores Hilt
  presentation                  pantallas existentes; conexión en fases 3 y 4
features/campaign
  domain/model                  campañas, condiciones, compensaciones y postulaciones
  domain/repositories           CampaignRepository, ApplicationRepository
  application/usecases          operaciones actuales del backend
  infrastructure/remote         CampaignApi, ApplicationApi, DTOs y mapeadores
  infrastructure/di             proveedores Hilt
  presentation                  pantallas existentes; conexión en fases 5–7
core
  domain                        ApiResult, ApiFailure, Page, PageRequest
  application/security          puerto SessionAccess y snapshots de credenciales
  infrastructure/network        Retrofit, executor, JSON y BearerInterceptor
  infrastructure/di             red, reloj y Gson
  presentation                  AsyncUiState
navigation/BackendDestination   argumentos UUID para futuros recursos reales
```

Domain y Application no importan Android, Retrofit, Gson, Hilt ni las capas externas. Las interfaces retornan `ApiResult` y modelos propios; los adapters traducen los contratos HTTP. Se incluyen casos de uso invocables para cada operación; Hilt proporciona los grupos `IdentityUseCases`, `CampaignUseCases` y `ApplicationUseCases` para los futuros ViewModels.

Los modelos antiguos de los archivos `domain/Profile.kt` y `domain/Campaign.kt`, sus repositorios Preview y `AppState` siguen alimentando exclusivamente la maqueta. Los modelos nuevos viven en `domain/model` y los puertos reales en `domain/repositories`, evitando mezclar IDs enteros de demostración con UUID del servidor. **No se debe conectar una pantalla real a esos repositorios de muestra ni usarlos como fallback.** `Collaboration`, `Billing` y `Performance` conservan sus cuatro capas existentes sin nuevos clientes remotos. No se agrega un bounded context Matching independiente.

El recorrido operativo de las fases siguientes será `Screen → ViewModel → UseCase → Repository → adapter REST`. Las Screens recibirán estado y callbacks para conservar previews independientes; la navegación real deberá usar UUID y verificar la sesión, no el selector local de rol. Se mantienen todas las previews actuales sin llamadas de red ni dependencia de Hilt.

## Contratos preparados

Las rutas siguientes son relativas a `/api/v1/`. Los requests contienen únicamente campos aceptados por el servidor: no incluyen owner IDs, rol editable ni IDs de los requisitos/entregables nuevos.

| Área | Operaciones iniciadas por Android |
|---|---|
| Autenticación, 5 | `POST auth/brands`, `POST auth/creators`, `POST auth/sessions`, `POST auth/recovery-requests`, `POST auth/password-resets` |
| Cuenta/perfil, 3 | `GET accounts/me`, `GET profiles/me/creator`, `PUT profiles/me/creator` |
| Redes, 3 | `POST social-accounts/{platform}/authorizations?client=ANDROID`, `GET social-accounts/me`, `GET social-accounts/authorizations/{id}` |
| Campañas, 9 | `POST campaigns`, `PUT campaigns/{id}/conditions`, `POST campaigns/{id}/publication`, `GET campaigns/mine`, `GET campaigns/published`, `GET campaigns` con filtros, `GET campaigns/{id}`, `DELETE campaigns/{id}`, `POST campaigns/{id}/closure` |
| Postulaciones propias, 5 | `POST campaigns/{id}/applications`, `GET applications/mine`, `GET applications/{id}`, `PUT applications/{id}`, `POST applications/{id}/cancellation` |

**25 operaciones móviles + 1 callback del proveedor = 26 endpoints del backend.** `GET social-accounts/{platform}/callback` pertenece al flujo del navegador/proveedor hacia el backend; no se llama desde Retrofit ni se le envía JWT. En la fase 4, Android abrirá la URL HTTPS recibida, recibirá `collabpro://social-authorization-completed?authorizationId=...` y consultará el intento. Volver del navegador no confirma una vinculación. Esta fase no incorpora intent filters sin sus handlers ni simula autorizaciones.

UUID se representa con `java.util.UUID`; fechas-hora con `Instant` y compensaciones con `BigDecimal`, sin conversiones a Double. Desugaring conserva compatibilidad con minSdk 24. Detalles de campaña se reciben como JSON plano y se agrupan en `summary` solo en Domain. Los borradores admiten compensación y plazo nulos. Los enums desconocidos, campos obligatorios ausentes y respuestas ilegibles son errores, no valores inventados.

La paginación es base cero con tamaño 1–100, `items`, `total`, `page` y `size`. `acceptsApplications` proviene del backend. Las postulaciones conservan las confirmaciones por UUID y `version`; actualizar mensaje/cancelar exige pasar la versión obtenida. La clave `IdempotencyKey` se genera una sola vez por intención de creación/postulación y se reutiliza en reintentos; el adapter nunca la regenera. No se agregan edición de metadatos publicados, reapertura, selección/rechazo ni pagos.

## Sesión y errores

- El JWT se trata como credencial opaca: Android no lo firma ni obtiene el rol decodificando claims. El tipo de cuenta proviene de `Account` y sus permisos finales del servidor.
- El almacenamiento privado guarda solamente la sesión y su cuenta, cifradas con AES-256-GCM y un IV nuevo por escritura; la clave se genera en Android Keystore. Nunca persiste contraseñas ni credenciales OAuth del proveedor.
- Se deshabilita backup y se excluye expresamente `collabpro_session.xml` tanto de backup como de transferencia. Las representaciones de sesiones y requests sensibles ocultan credenciales; no hay interceptores de logging HTTP.
- `ReadStoredSession` devuelve un candidato. En fase 3 se validará con `GetCurrentAccount` antes de abrir rutas privadas. `SignIn` no persiste por sí solo: su consumidor debe esperar éxito y confirmar `StoreSession`.
- Cada llamada protegida captura la sesión original y verifica que siga vigente antes del envío y al recibir respuesta. Un 401 protegido invalida solamente esa sesión; un 401 de login no elimina otra sesión. Cambiar de cuenta descarta respuestas anteriores. La UI deberá también cancelar consultas y limpiar el back stack al cambiar de sesión.
- Solo se añade Bearer a los endpoints protegidos de la misma URL base (esquema, host, puerto y prefijo). Requests públicos no reciben token; no se siguen redirects y se deshabilitan reintentos automáticos de transporte.
- El executor diferencia red, timeout, autorización, permisos, inexistencia, validación, conflicto, requisitos incumplidos, proveedor no disponible, servidor, contrato inválido y configuración. Conserva `code`, `message`, `fieldErrors` y código HTTP. Nunca devuelve listas vacías para esconder un fallo. La cancelación de coroutines se propaga.
- 204 se maneja como `Unit` solamente en operaciones sin contenido. Timeout de una escritura no equivale a rechazo ni éxito: el ViewModel futuro deberá reconciliar/reintentar con la misma clave. Una falla de red no borra la sesión vigente.

## URL por entorno

Debug usa por defecto `http://10.0.2.2:8081/api/v1/`, dirección del host desde el emulador. El backend debe estar efectivamente escuchando en 8081; Docker no lo inicia por abrir la aplicación. Se escogió 8081 para no interferir con los servicios locales que ya ocupaban 8080.

Para un teléfono físico usa la IP local del equipo, la misma red Wi-Fi y el puerto real del backend. Sobrescribe la URL sin modificar Kotlin:

```powershell
.\gradlew.bat :app:assembleDebug '-Pcollabpro.debugBaseUrl=http://192.168.1.20:8081/api/v1/'
```

También puedes configurar esa propiedad en tu archivo personal `%USERPROFILE%/.gradle/gradle.properties` para los builds de Android Studio. La IP es un ejemplo, no la dirección detectada de este equipo. HTTP se permite solamente en debug; el manifiesto release lo rechaza.

Release requiere URL HTTPS con prefijo `/api/v1/`:

```powershell
.\gradlew.bat :app:assembleRelease '-Pcollabpro.releaseBaseUrl=https://api.tu-dominio.com/api/v1/'
```

Mientras no exista un entorno desplegado, el default release es `https://unconfigured.invalid/api/v1/`: permite verificar compilación, pero las operaciones retornan `CONFIGURATION` y no envían solicitudes. No es un servidor real. No copies `JWT_SECRET`, credenciales de base de datos ni secretos OAuth al APK.

## Verificación y siguiente fase

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --console=plain
```

Las pruebas HTTP usan MockWebServer y cubren las 25 operaciones, campos de request, headers, idempotencia, 204, nulos, decimales, fechas y estados. Las pruebas de seguridad cubren aislamiento de sesiones, Bearer, redirects y errores; las de persistencia verifican el cifrado GCM, IV nuevo, corrupción, pérdida de clave y escrituras fallidas. Los tests de arquitectura guardan los límites de capas y evitan bindings de Preview.

Resultado de esta entrega: **42 tests aprobados** (41 de la base y 1 previo), `assembleDebug` y `assembleRelease` exitosos, `lintDebug` sin errores. Lint mantiene 31 advertencias de versiones/estilo/recursos y 1 sugerencia; no se cambia toda la cadena de herramientas ni se reescribe la UI para resolver avisos ajenos a esta fase. APK debug: `app/build/outputs/apk/debug/app-debug.apk`. Release es un artefacto de compilación sin firma de distribución ni backend productivo configurado.

El cifrado se prueba con una clave AES JVM y un storage en memoria; Android Keystore, instalación, ejecución y renderizado de Preview deben validarse en dispositivo/Android Studio. No hay dispositivo/emulador conectado para esas comprobaciones durante esta entrega. Los servicios del backend y los proveedores OAuth reales no se ejecutan en estas pruebas móviles.

Sigue **fase 3: conectar registro, login, restauración verificada de sesión y recuperación**. Allí se incorporarán ViewModels concretos, Screens con UiState/callbacks, navegación autenticada y limpieza de estados simulados de esas pantallas. Después: perfil/redes (4), creación de campañas (5), exploración (6), postulaciones (7) y limpieza/aceptación integral (8).

Referencias de configuración: [Hilt Gradle](https://dagger.dev/hilt/gradle-setup), [Kotlin integrado en AGP](https://developer.android.com/build/migrate-to-built-in-kotlin). Se utiliza Kotlin integrado en AGP 9: no se añade el plugin Kotlin Android antiguo.
