# Postulaciones propias — Campaign / US-19

Implementación del 8 de octubre de 2026, fase 7 del plan. Dentro de las primeras 18 posiciones ordenadas del Product Backlog; no implementa evaluación empresarial (US-20), selección, acuerdos ni colaboraciones. No requiere cambios de backend.

## Operaciones conectadas

| Operación | Pantalla / comportamiento |
|---|---|
| `POST /campaigns/{id}/applications` | Formulario con mensaje y `confirmedRequirementIds`; `Idempotency-Key` estable por intención |
| `GET /applications/mine?page=&size=` | Mis postulaciones, paginación real y precarga por campaña |
| `GET /applications/{id}` | Detalle propio, versión actual y reconciliación después de resultados inciertos |
| `PUT /applications/{id}` | Editar solo mensaje con `expectedVersion` de la propuesta consultada |
| `POST /applications/{id}/cancellation` | Confirmación de cancelación con `expectedVersion` del detalle mostrado |

Todas usan la sesión JWT real y el guard `ExpectedAccount`. La propiedad corresponde al `profileId` del creador, no al UUID de su cuenta. Expiración, logout y cambio de cuenta eliminan el estado privado y descartan respuestas tardías.

## Recorrido

1. Inicio → explorar campañas → detalle → preparar/consultar mi postulación. También Inicio → Mis postulaciones → detalle → editar.
2. Antes de habilitar un envío, consultar condiciones y buscar la postulación existente en **todas** las páginas propias, de 100 elementos. El contrato no tiene consulta por campaña. Una página fallida, incompleta o inconsistente no confirma ausencia. Si existe, consultar su detalle y precargar su mensaje, confirmaciones y versión; nunca fabricar una propuesta nueva.
3. El creador marca únicamente requisitos `MANUAL_CONFIRMATION` de esa campaña por UUID. Los obligatorios bloquean el envío si falta confirmar; los opcionales también pueden confirmarse. Las reglas de nicho, ubicación y red autorizada las evalúa el backend con perfil y redes actuales. Se muestran errores `requirements.<UUID>` junto al requisito correspondiente.
4. Un envío exitoso abre el detalle real y actualiza Mis postulaciones. Además consulta GET: una repetición idempotente del POST puede devolver el snapshot original, aunque su estado actual sea cancelado.
5. Solo `PENDING` permite editar o cancelar. La edición conserva las confirmaciones y la fecha originales; cancelar conserva el registro. Una campaña cerrada o vencida no impide gestionar una postulación pendiente existente, aunque sí bloquea un envío nuevo.

## Concurrencia, errores y conservación

- El mensaje admite hasta 4000 caracteres. Se conserva ante validación, requisitos incumplidos, fallo de red, duplicado y conflicto.
- El envío incierto congela cuerpo y clave. Reintentar reutiliza exactamente ambos, sin crear otra intención. Un duplicado busca y abre el registro real; si no logra localizarlo, bloquea un nuevo envío.
- Edición y cancelación envían la versión consultada; nunca la incrementan en Android. Ante `CONCURRENT_UPDATE`, consultar y mostrar ambas versiones sin sobrescribir la propuesta local ni reintentar automáticamente con la versión nueva.
- El formulario ofrece dos decisiones confirmadas: cargar la propuesta del servidor o conservar el texto local usando la versión actual como base. Esta última **no escribe**: exige revisar y pulsar Guardar después.
- Una confirmación abierta se cierra si cambia la versión/estado del detalle o la versión comparada del conflicto. No se conserva un diálogo antiguo para autorizar una operación sobre una propuesta recién recargada.
- Una respuesta perdida tras edición/cancelación se reconcilia mediante GET. Si confirma el resultado, no repetir la escritura. Si conserva la versión original, permitir reintento con esa misma versión. Si cambió, comparar versiones; si GET también falla, bloquear escritura hasta verificar.
- El adapter normaliza `submittedAt` a microsegundos, precisión de `DATETIME(6)` en MySQL V1: POST/cache puede devolver nanosegundos y GET no conservarlos. Esa diferencia no representa una modificación ni un conflicto de versión.
- Mis postulaciones distingue vacío confirmado de error. 404, propiedad incorrecta o respuesta inválida no se sustituyen por un elemento de ejemplo.
- `CANCELLED`, `REJECTED` y `SELECTED` son de solo lectura. Android no permite seleccionar/rechazar; solo muestra estados recibidos. No permite repostular tras cancelar, respetando la unicidad actual del backend.
- Las propuestas locales y claves pendientes se conservan **en memoria del ViewModel**, por campaña, durante navegación y rotación de la sesión actual. No sobreviven muerte del proceso, reinicio ni logout. Las propuestas ya registradas se recuperan del servidor. No se promete conservación cifrada persistente como la del wizard de campañas.

## Capas DDD

- **Domain:** `Application`, `ApplicationStatus`, `ApplicationRepository`, contratos existentes.
- **Application:** `ApplicationUseCases`; `applications/ApplicationProposal.kt` valida intención y confirma resultados; `FindOwnApplication.kt` localiza el registro sin confundir lectura parcial con ausencia.
- **Infrastructure:** `RemoteApplicationRepository`, `ApplicationApi`, DTOs/mapeadores y Hilt existentes; cinco operaciones HTTP sin lógica de pantalla.
- **Presentation:** `presentation/applications/OwnApplicationsViewModel.kt`, UiState y Screens puros con callbacks. Navegación por UUID, estados reales, confirmaciones y resolución explícita de conflicto.

Se retiraron `ApplicationFormScreen` y `MyApplicationsScreen` simulados. Las rutas conectadas no incluyen botones para fabricar duplicados, incumplimientos, selección ni cancelación. `navigation/PreviewScreens.kt` conserva vistas de formulario/listado y añade detalle, cancelada, conflicto y error mediante fixtures exclusivos de preview. El listado/detalle de postulantes empresariales permanece como prototipo futuro independiente.

## Verificación y uso

Pruebas de reglas, paginación exhaustiva, estados, concurrencia, sesión, respuestas tardías y contratos HTTP; integración real de los cinco endpoints con JWT/MySQL en base temporal y cuentas sintéticas. La integración comprueba requisitos manuales/automáticos, idempotencia, propiedad, edición tras cierre, versiones antiguas, cancelación y conservación del historial.

- **264 pruebas JVM exitosas, sin omisiones:** 259 unitarias/contratos y 5 integraciones reales (autenticación/SMTP, perfil/OAuth, preparación de campañas, exploración/detalle y postulaciones).
- Esta fase añade 10 pruebas de reglas/búsqueda propia, 30 del ViewModel, 6 de contratos HTTP, 1 de integración real y 1 de navegación protegida.
- Compilaciones Debug, Release sin firma y APK de pruebas Android verificadas; lint sin errores, con 34 advertencias y 1 sugerencia ya presentes. Reportes en `app/build/reports/tests/testDebugUnitTest/index.html` y `app/build/reports/lint-results-debug.html`.
- Cinco pruebas Compose nuevas preparadas para error/vacío, estado terminal, conflicto, escritura incierta y confirmación obsoleta. **Compiladas, no ejecutadas:** no había dispositivo conectado según `adb devices -l`.

Se utilizaron backend en loopback `18081`, MySQL temporal `33079` y Mailpit temporal `8029/10279`, sin tocar la base de desarrollo ni los servicios SmartQuote. Los servicios y datos sintéticos temporales se retiran después de verificar; son recreables, no una copia de datos de usuario.

Para repetir las integraciones, preparar un backend **aislado**, solo en loopback, con MySQL y SMTP Mailpit; nunca usar datos reales. Variables opt-in: `COLLABPRO_CAMPAIGN_TEST_API`, `COLLABPRO_IDENTITY_TEST_API`, `COLLABPRO_AUTH_TEST_API` (URL terminada en `/api/v1/`) y `COLLABPRO_AUTH_TEST_MAILPIT` (URL de Mailpit). Sin ellas las integraciones se omiten explícitamente; las pruebas unitarias/contratos siguen ejecutándose.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --console=plain --no-configuration-cache
```

Debug usa `http://10.0.2.2:8081/api/v1/` en el emulador. Para celular físico, configurar la IP local del equipo con `-Pcollabpro.debugBaseUrl=http://IP_DEL_EQUIPO:8081/api/v1/`. El backend debe estar accesible y el teléfono en la misma red. APK: `app/build/outputs/apk/debug/app-debug.apk`.

La comprobación visual y ejecución de pruebas Compose en teléfono/AVD queda pendiente cuando no hay dispositivo conectado. La fase 8 de limpieza general y aceptación completa no se declara realizada por esta entrega.
