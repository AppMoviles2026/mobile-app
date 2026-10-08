# Exploración y detalle de campañas V1

Implementación del 8 de octubre de 2026 para US-17 y US-18, dentro de las primeras 18 posiciones del Product Backlog. Pertenece a **Campaign**, sin introducir Matching, recomendaciones, evaluación de postulantes ni historias futuras. La postulación propia (US-19) continúa pendiente de la fase 7.

## Capas DDD y contratos

| Capa | Responsabilidad |
|---|---|
| Domain | `CampaignSummary`, `CampaignDetails` y `CampaignAvailability`; la disponibilidad exige permiso del servidor, estado OPEN y plazo vigente |
| Application | `DiscoveryFilters` normaliza/valida texto y construye criterios; los casos `GetPublishedCampaigns`, `SearchCampaigns` y `GetCampaignDetails` utilizan el puerto `CampaignRepository` |
| Infrastructure | Se reutilizan Retrofit, DTOs, mapeadores y `RemoteCampaignRepository`; sesión JWT y `ExpectedAccount` aíslan cada consulta por cuenta y vencimiento de sesión |
| Presentation | `campaign/presentation/discovery`: ViewModel, UiState, exploración, detalle y oportunidades del inicio; pantallas puras y previews sin red/Hilt |

| Endpoint | Uso conectado |
|---|---|
| `GET /campaigns/published?page=&size=` | Inicio del creador (hasta tres oportunidades) y exploración sin filtros (20 por página) |
| `GET /campaigns?q=&category=&location=&compensationType=&page=&size=` | Exploración con filtros combinados y paginación en el servidor |
| `GET /campaigns/{uuid}` | Consulta de condiciones y actualización de disponibilidad de la campaña seleccionada |

Se conserva la URL debug `http://10.0.2.2:8081/api/v1/`. Para un teléfono físico, usar la IP LAN del equipo mediante la propiedad Gradle documentada en `AUTENTICACION_MOVIL_V1.md`. La sesión real de creador es obligatoria; una empresa usa sus recorridos de gestión, no el catálogo del creador.

## Recorrido y búsqueda

1. Ingresar como creador: Inicio muestra oportunidades del servidor, sin campañas de muestra ni contadores inventados.
2. Explorar: escribir marca/título/objetivo, categoría y ubicación, y seleccionar cualquier tipo real de compensación (dinero, producto, servicio, crédito o canje).
3. Pulsar **Buscar campañas** aplica los filtros normalizados y vuelve a la página inicial. Categoría usa coincidencia exacta sin distinguir mayúsculas; ubicación y texto son búsquedas parciales conforme al backend. No se inventa un catálogo fijo de categorías.
4. Anterior/Siguiente utiliza `page`, `size` y `total` del servidor; no filtra solamente la página cargada. Las páginas comienzan en cero en HTTP y en uno para el usuario. No permite avanzar más allá del total.
5. Limpiar filtros elimina los cuatro criterios y vuelve al catálogo publicado, página inicial. Actualizar conserva los filtros **aplicados**; Reintentar conserva la página solicitada que falló.
6. Cada tarjeta abre su UUID y muestra empresa, objetivo, descripción, audiencia, categoría, ubicación, fechas, compensación, requisitos con regla/valor/obligatoriedad y entregables con cantidad/plazo.

Los campos editados se distinguen de los criterios aplicados. Si se modifican durante una consulta, se cancela esa consulta y se descarta cualquier respuesta tardía; los resultados anteriores, si existen, se identifican como correspondientes a la consulta previa. La paginación queda deshabilitada hasta aplicar o limpiar los cambios. Los límites son los del backend: texto 200, categoría 100, ubicación 150 caracteres tras recortar extremos.

## Disponibilidad y errores

- Ambos listados REST devuelven campañas OPEN con plazo vigente y `acceptsApplications=true`. Los totales son del servidor, no se recalculan con filtros locales.
- El detalle de una campaña publicada sigue siendo consultable si cerró o venció entre listado y consulta. Las condiciones se conservan visibles con el aviso de que no admite nuevas postulaciones.
- `acceptsApplications=false` nunca se transforma en disponibilidad positiva. Un reloj local adelantado puede desactivar disponibilidad al alcanzar el plazo; no puede activar una campaña que el servidor haya bloqueado. Se respeta el límite exclusivo: exactamente en la fecha límite ya venció.
- Mientras la pantalla está visible y la app en primer plano, un temporizador local actualiza la indicación al vencer el plazo (sin sondeo periódico de red). Al volver a primer plano se vuelve a consultar únicamente la pantalla activa. El servidor conserva la última palabra al implementar posteriormente el envío de postulación.
- Estar abierta **no significa** que el creador haya aprobado reglas de nicho, ubicación o autorización social. Esas reglas se muestran, no se inventa una evaluación favorable del perfil.
- Vacío confirmado, carga y error son estados distintos. Ante fallo de una actualización se retiran los datos no verificados de esa consulta; hay reintento, sin reemplazo por fixtures.
- Un UUID inexistente muestra 404; 403, 401, timeouts, red y respuestas inválidas tienen estados de error. El 401 utiliza la invalidación global de sesión ya existente.
- Cambiar de cuenta, cerrar sesión o volver a entrar con otra vigencia elimina filtros, resultados, detalle y navegación anteriores. Las respuestas tardías de lista y detalle no pueden sobreescribir una consulta nueva.

Se retiraron las dos pantallas antiguas de búsqueda/detalle simulados y el salto de una campaña real al formulario ficticio. El detalle no ofrece un envío simulado ni navegación a selección/acuerdos/colaboraciones. Las pantallas de postulaciones todavía son prototipos explícitos y se mantienen independientes hasta la fase 7.

## Verificación

**Resultado final: 216 pruebas JVM aprobadas, cero fallos, cero errores y cero omisiones** (212 unitarias/contratos y cuatro integraciones activadas). `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest` y `lintDebug` finalizaron correctamente. Lint conserva 34 advertencias y una sugerencia, sin errores. No hay teléfono/AVD conectado: las pruebas instrumentadas y el renderizado visual quedan pendientes, no se presentan como ejecutados.

Pruebas nuevas cubren reglas de disponibilidad, filtros, ViewModel, contratos HTTP y una integración opt-in con MySQL. Esta última usa cuentas/campañas sintéticas en loopback: compara totales y páginas, combina todos los filtros, verifica caracteres reservados, comprueba condiciones reales, borradores ocultos, permisos, 404, cierre y vencimiento del plazo. También se repiten las integraciones previas de autenticación, OAuth rechazado y preparación de campañas.

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:COLLABPRO_CAMPAIGN_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_IDENTITY_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_MAILPIT='http://127.0.0.1:8029'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --console=plain --no-configuration-cache
```

Usar una instancia aislada de MySQL/Mailpit y el backend con la configuración sintética de pruebas descrita en las guías anteriores, **no una base con usuarios reales**. Sin las variables opt-in, se omiten las cuatro integraciones. Las pruebas de interfaz se compilan, pero requieren teléfono/AVD para ejecutarse. Las previews incluyen listado, vacío, error, detalle disponible y detalle cerrado.

La verificación utilizó el backend temporal en 18081 con MySQL 8.4 y Mailpit aislados; no modificó el código del backend ni el reporte. Ese servidor no es la dirección permanente del APK. Los procesos, contenedores y datos sintéticos temporales se retiraron al concluir, conservando SmartQuote y la base de desarrollo. Los escenarios se pueden recrear ejecutando los tests contra otra instancia aislada.

Siguiente fase: **Postulaciones propias**, sin adelantar selección de creadores ni las historias posteriores del backlog.
