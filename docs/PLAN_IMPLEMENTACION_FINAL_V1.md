# Plan final de implementación V1: backend y Android

Fecha: 6 de octubre de 2026. Este documento sustituye las decisiones de implementación del `PLAN_INTEGRACION_BACKEND_V1.md`; aquel conserva el inventario de las 23 operaciones REST que existen actualmente.

## 1. Regla de alcance

La entrega comprende las **primeras 18 posiciones ordenadas** de la tabla Product Backlog de `C:/Users/fabio/Documents/report/README.md:3708`. Las posiciones 1–8 corresponden al recorrido de landing; se conservan las pantallas informativas existentes sin desarrollar una landing ni sus servicios en esta entrega.

| Posición | Historia | Capacidad de backend/móvil de esta entrega |
|---|---|---|
| 9 | US-17 | Búsqueda de campañas |
| 10 | US-18 | Detalle de condiciones y aviso cuando no admite postulaciones |
| 11 | US-19 | Crear, consultar, editar y cancelar una postulación propia |
| 12 | US-10 | Registro de creador |
| 13 | US-11 | Login, acceso por tipo de cuenta y recuperación |
| 14 | US-13 | Leer y guardar perfil de creador |
| 15 | US-14 | Vinculación social autorizada, rechazada y duplicada |
| 16 | US-15 | Preparar/publicar campaña e impedir publicación incompleta |
| 17 | US-16 | Requisitos, entregables, fechas y compensación |
| 18 | US-09 | Registro de empresa |

### Capacidades que deben permanecer pendientes

| Posición posterior | Historia | Qué no se implementa en esta entrega |
|---|---|---|
| 19 | US-12 | Registro/actualización de información empresarial |
| 20 | US-20 | Listado de postulantes para la empresa, selección y rechazo |
| 21–25 | US-21–US-25 | Acuerdos, colaboraciones, entrega de contenido, validación e incidencias |
| 26–30 | US-26–US-30 | Medios de pago, suscripciones, compensación ejecutada, métricas e historial de colaboraciones |
| 31 | SS-02 | Entregables de investigación de OAuth, especialmente consulta de métricas |
| 32–34 | TS-01, TS-03, TS-02 | Completar la totalidad de sus contratos, aliases y capacidades adicionales |
| 35–40 | TS-04, TS-05, SS-01, TS-06, SS-03, TS-07 | Servicios y spikes de las fases posteriores |

Una Technical Story posterior puede repetir infraestructura necesaria para una User Story actual. Implementar autenticación para US-11, OAuth para US-14 o edición de postulación para US-19 no anticipa por sí mismo toda TS-01/TS-02/TS-03. En cambio, no se usará la existencia de esas TS para introducir funciones adicionales expresamente reservadas para después.

El reporte representa la arquitectura del producto completo: que una clase futura aparezca en sus diagramas no convierte sus operaciones en alcance de esta entrega.

## 2. Respuesta y decisión sobre JWT

### Qué existe actualmente

El backend **sí tiene Login**: `POST /api/v1/auth/sessions` valida email/contraseña, comprueba cuenta activa y devuelve `account`, `accessToken`, `tokenType="Bearer"` y `expiresAt`.

Al elaborar el análisis, el token era opaco. Por indicación posterior del usuario, el backend ahora emite **JWT firmado HS256** con duración configurable (una hora predeterminada), valida firma/claims y conserva un registro de hashes en `identity_access_session` para revocación y comprobación de cuenta activa. Cambiar contraseña elimina las sesiones previas. Los tokens opacos anteriores requieren un nuevo login.

JWT es un formato para transportar claims. Bearer describe cómo se presenta una credencial; no determina su formato. Android y un cliente web pueden usar JWT o un token opaco, enviando el valor emitido por el servidor. El cliente no debe generar su propio token ni decidir permisos por decodificarlo. Referencias: [JWT, RFC 7519](https://www.rfc-editor.org/rfc/rfc7519) y [Bearer, RFC 6750](https://www.rfc-editor.org/rfc/rfc6750).

### Qué dice el reporte

No se encontró una obligación de usar JWT, ni una prohibición, en el texto del README revisado. US-11 pide credenciales válidas y acceso según el tipo de cuenta; TS-01 pide credenciales para servicios protegidos; la sección 2.6.1.4 define `AccessTokenProvider` sin fijar formato. Ambas alternativas son compatibles con esas indicaciones.

### Decisión final

**Usar JWT en esta V1, por solicitud expresa posterior del usuario.** Es compatible con US-11 y mantiene el contrato `Bearer`/`expiresAt`, incluyendo la revocación al recuperar la cuenta. El detalle de configuración y los contratos ya implementados están en `C:/Users/fabio/Documents/platform/README.md`.

La emisión/validación se mantiene detrás de `AccessTokenProvider` y `AccessTokenVerifier` del contexto Identity. `BearerSessionFilter` depende del puerto de validación, no de la implementación concreta. Spring Security/Nimbus y el registro revocable pertenecen a Infrastructure; JWT no se introduce en Domain ni en Screens.

No añadir ahora un sistema de refresh, un emisor OAuth propio o aliases `auth/login`/`auth/register` únicamente para anticipar TS-01. El móvil manejará el contrato existente, el vencimiento y el logout local, dejando clara la ausencia de revocación remota por logout.

## 3. Decisiones sobre los 17 puntos del análisis anterior

| Punto | Decisión final | Justificación y trabajo necesario |
|---|---|---|
| 1. Registro sin sesión | Mantener backend; corregir Android | US-09/US-10 crean cuentas, no prometen login automático. Registro confirmado → Login |
| 2. Rol del servidor | Mantener backend; corregir Android | US-11 exige acceso correspondiente a la cuenta. Quitar selector de rol de Login |
| 3. Autenticación JWT | Implementada por solicitud posterior; proveedor encapsulado | Android consume Bearer y expiresAt; no recibe la clave de firma ni decide permisos a partir de claims locales |
| 4. AccountId/ProfileId | Mantener; tipar y mapear Android | Son entidades distintas en el reporte. Propiedad siempre derivada de sesión en backend |
| 5. Perfil empresarial parcial | No ampliar su gestión | US-12 es posición 19. E06 basta para nombre/tipo/estado de cuenta y panel; formulario empresarial completo sigue pendiente |
| 6. Tres operaciones de campaña | Mantener separación; mejorar wizard | US-15 y US-16 respaldan preparación, condiciones y publicación. Edición genérica de campaña aparece en TS-03, posición 33 |
| 7. Condiciones estructuradas | Refactorizar formulario móvil; conservar modelo backend | Listas/fechas/compensación son necesarias para US-16. No basta un texto libre para cada lista |
| 8. OPEN vencida en listados | Corregir consultas y resúmenes backend; conectar Android | Unificar criterio de disponibilidad con acceptsApplications. US-18 ya exige aviso si no admite postulaciones |
| 9. Requisitos explícitos | Conservar reglas; hacerlas utilizables en Android | US-19 exige impedir incumplimiento e identificarlo. No inferir seguidores o nicho a partir de texto |
| 10. Duplicado incluso tras cancelar | Conservar por ahora; documentar ambigüedad | US-19 dice impedir duplicado a quien ya postuló, mientras Domain menciona duplicados activos. No introducir repostulación sin escenario explícito |
| 11. Solo PENDING editable/cancelable | Mantener; quitar simulaciones | Es parte expresa de US-19. Un cierre de campaña no cambia automáticamente una postulación pendiente |
| 12. Evaluación por empresa inexistente | Mantener pendiente | US-20 es posición 20. No crear listado/selección/rechazo empresarial |
| 13. Versionado de postulaciones | Mantener y consumir; mejorar recuperación de conflictos | Evita sobrescribir cambios y cancelar versiones antiguas sin añadir una capacidad futura |
| 14. OAuth con callback JSON | Mejorar backend para retorno móvil y resultado consultable | Vinculación/rechazo/duplicado ya están en US-14; su feedback debe llegar a Android |
| 15. Recuperación de dos pasos | Completar Android con endpoints actuales | Es necesaria para terminar US-11; no anticipa otro escenario de negocio |
| 16. Requests estrictos | Mantener; DTOs correctos y documentación | Evita asignación de rol/propiedad desde cliente; no enviar campos de mock |
| 17. Sin eventos push | Mantener REST y recarga de consultas | Ninguna historia actual exige WebSockets/SSE/notificaciones |

### Perfil empresarial: qué se verá realmente

No hace falta implementar US-12 para que la empresa se registre, se autentique o publique una campaña. La creación de campaña acepta ubicación/categoría propias, sin necesitar que hayan sido guardadas previamente en el perfil de empresa.

En el espacio de cuenta empresarial se mostrarán nombre, tipo y estado reales de `GET /accounts/me`. No se fabricarán rubro/ubicación/biografía ni se expondrá “Guardar perfil” como una operación real. La pantalla completa existente de gestión empresarial puede conservarse como preview/prototipo para US-12. No se añadirá un GET/PUT empresarial solo para rellenar campos todavía sin datos: el perfil inicial se crea con nombre y los otros campos no fueron capturados en el registro.

### Metadatos, eliminación, cierre y cancelación de campaña

Se revisaron tanto User Stories posteriores como Technical Stories y el modelo DDD.

| Operación | Decisión | Límite que protege el alcance |
|---|---|---|
| Corregir título/objetivo/etc. antes de enviar el wizard | Sí, localmente | Son datos de un formulario de creación; todavía no hay campaña persistida |
| Editar metadatos de una campaña ya persistida | Pendiente | TS-03 escenario 2 define `PATCH /campaigns/{id}` en posición 33. No agregar ese contrato ahora |
| Reemplazar condiciones mientras es DRAFT | Sí, ya existe | E13 implementa US-16; mantener la congelación de condiciones publicadas |
| Descartar/eliminar borrador DRAFT | Añadir como apoyo del formulario | No se encontró una historia posterior dedicada a esta operación; solo borradores sin postulaciones |
| Cerrar recepción de postulaciones de OPEN | Añadir cierre acotado | US-18 incluye campaña que no admite postulaciones; EventStorming contempla cierre y el Aggregate del reporte tiene `close()` |
| Borrar campañas publicadas | No | Rompería referencias y trazabilidad que requieren US-20/US-21 y las colaboraciones futuras |
| Cancelar campaña publicada y afectar postulaciones | No en esta V1 | El enum CANCELLED no define qué debe ocurrir con solicitudes/acuerdos. No inventar rechazo o cancelación en cascada |

Cerrar no edita metadatos: cambia únicamente la admisión de nuevas postulaciones. No selecciona/rechaza postulantes, no crea colaboraciones y no altera estados de postulaciones ya registradas.

## 4. Refactorización backend aprobada para el plan

### B01. Disponibilidad consistente de campañas

- Reutilizar la regla `OPEN && applicationDeadline > now` en búsqueda y listado publicado, con el mismo Clock y el mismo instante por consulta.
- La exploración predeterminada devuelve oportunidades que admiten postulaciones; el detalle de una publicada conserva acceso aunque ya no las admita, para cumplir US-18.
- Añadir `acceptsApplications` a Summary de forma aditiva, alineándolo con Details. Mantener el total paginado con el mismo predicado que los items.
- No actualizar el estado persistido durante un GET. Una OPEN vencida puede mantener OPEN y devolver `acceptsApplications=false`; Android muestra “Plazo de postulación vencido”.
- Preservar seguridad: la empresa accede a propias campañas, el creador no accede a borradores, y no hay endpoints públicos de campañas por confundirse con la landing.

### B02. Descartar borradores y cerrar recepción

Agregar dos comandos explícitos en Campaign, independientes del futuro PATCH de metadatos:

| Nueva operación | Condición | Resultado |
|---|---|---|
| `DELETE /api/v1/campaigns/{id}` | Empresa propietaria; DRAFT sin postulaciones | 204; borrar condiciones del borrador dentro de la misma transacción |
| `POST /api/v1/campaigns/{id}/closure` | Empresa propietaria; OPEN | 200 Details con CLOSED y acceptsApplications=false |

- Para borrar, verificar ausencia de dependencias aun cuando el flujo normal impide postulaciones a DRAFT. No eliminar ninguna campaña de usuario durante la implementación/pruebas fuera de una base de prueba.
- Para cerrar, usar bloqueo/concurrencia consistente con publicación/postulación. Si la campaña ya es CLOSED, devolver el detalle actual sin repetir efectos; DRAFT/CANCELLED no admiten este comando.
- No reabrir; no mover postulaciones PENDING a REJECTED/CANCELLED. Mantener las condiciones y las referencias de las postulaciones.
- Eliminar un borrador obsoleto devuelve 404; Android reconcilia con Mis campañas y no fabrica otro UUID.
- Ubicar reglas en Campaign aggregate, coordinación en Application, persistencia en Infrastructure y contrato HTTP en Interface. Las nuevas operaciones deben tener errores propios mapeados al ApiError existente.

### B03. Finalización OAuth usable desde Android

Conservar el callback HTTPS registrado ante Instagram/TikTok. El proveedor vuelve primero al backend, que consume el state y ejecuta el intercambio una única vez; después Android consulta el resultado autenticado. Esta opción sustituye la dependencia de interceptar el redirect del proveedor antes que el backend planteada en el plan anterior.

1. Extender E09 de forma compatible: canal opcional `client=ANDROID`, por defecto comportamiento actual, y respuesta aditiva `authorizationId` junto a `authorizationUrl`.
2. Persistir un intento de autorización con propietario, plataforma, canal, vencimiento y resultado (`PENDING/SUCCEEDED/FAILED/EXPIRED`), asociado al state. State/code nunca se devuelven como resultado ni como credenciales para Android.
3. E10 sigue siendo el callback público; conserva consumo único de state. Registrar éxito solo después de confirmar cuenta social y credenciales en la transacción. Registrar fallo sanitizado por rechazo, duplicado o proveedor fuera de una transacción que se haya revertido. Un callback repetido no sobrescribe un intento terminado.
4. Para un intento Android reconocido, retornar a un URI fijo de configuración como `collabpro://social-authorization-completed?authorizationId=<uuid>`. No aceptar un return URL libre desde el usuario y no incluir code, state, token del proveedor o token de sesión.
5. Añadir `GET /api/v1/social-accounts/authorizations/{authorizationId}` protegido: solo propietario creador; devolver plataforma, status y errorCode sanitizado cuando aplique. Otro propietario obtiene 404; no hay lectura pública del intento.
6. Android guarda temporalmente el authorizationId del intento, abre Custom Tabs, procesa el enlace de regreso y consulta el resultado. Recarga E11 únicamente para mostrar asociaciones confirmadas; volver del navegador no equivale a éxito.
7. Si no llega el deep link, al reanudar consultar el intento pendiente: PENDING significa aún incompleto; FAILED/EXPIRED informa el resultado. No se necesita polling permanente.
8. El callback de clientes existentes sin canal Android conserva su respuesta JSON. State desconocido o no resoluble sigue entregando error sin redirección arbitraria.

El URI de regreso solo contiene una referencia; esa referencia no concede acceso y su consulta exige Bearer. La configuración fija del dominio HTTPS y las credenciales del proveedor siguen siendo dependencias externas de US-14. No se extraen métricas ni se realizan entregables de investigación de SS-02/US-29.

### B04. Robustez de operaciones existentes

- Mantener DTOs estrictos, ApiError y versionado de postulaciones. No hacer refactor del modelo de compensación: su enum ya coincide con el reporte (`CASH/PRODUCT/SERVICE/CREDIT/BARTER`).
- Para creación de campaña y envío de postulación, añadir soporte de `Idempotency-Key` dentro de las mismas operaciones actuales. Android enviará esa key; para conservar compatibilidad, los clientes existentes que no la envíen mantienen el contrato actual. Mismo actor/operación/key/payload reejecutado devuelve resultado original; key reutilizada con payload distinto da conflicto. Una solicitud nueva con otra key sigue sujeta a la regla de duplicados de US-19.
- Persistir la resolución de idempotencia junto con el comando dentro de una transacción y aplicar una política de expiración documentada. Android mantiene la key de una operación hasta conocer su resultado; no cambia la key solo porque ocurrió un timeout.
- Cuando ya no sea posible resolver una key por su expiración, reconciliar mediante consultas antes de ofrecer otra creación. Nunca interpretar un timeout como prueba de que no se guardó.
- Probar cambios con migraciones Flyway adicionales sobre una base existente; no reescribir V1–V5 aplicadas ni resetear datos. Las nuevas migraciones de OAuth/idempotencia usarán el siguiente número libre global entre ambos directorios.

### Ambigüedad sobre repostulación después de cancelar

El backend tiene unicidad permanente `(campaign_id, creator_id)`, y la prueba `duplicateRemainsRejectedAfterCancellation` confirma su intención actual. US-19 impide duplicados cuando el creador ya postuló; el texto de dominio en README:4188 añade el calificativo “activas”. Ninguno ofrece un escenario explícito de volver a postular después de cancelar.

La decisión conservadora es mantener el comportamiento V1 y su prueba: se puede cancelar una pendiente, pero no volver a enviar otra a la misma campaña. No introducir nuevas reglas para postulaciones REJECTED/SELECTED, que son parte de US-20. Registrar esta diferencia de precisión para aclararla al evolucionar el dominio; no declarar que el reporte exige repostulación ni borrar la anterior para permitirla.

## 5. Contratos finales y separación DDD

Actualmente hay 23 operaciones. Con B02/B03 habrá **26 operaciones**: las 23 originales, descarte de borrador, cierre de recepción y consulta de intento OAuth. Idempotencia cambia el comportamiento de dos operaciones existentes, no agrega endpoints.

La matriz E01–E23 del plan anterior permanece aplicable. Agregar los siguientes consumidores:

| ID final | Operación nueva | Pantalla/acción |
|---|---|---|
| E24 | `DELETE /campaigns/{id}` | Mis campañas/detalle DRAFT → Descartar borrador → confirmación |
| E25 | `POST /campaigns/{id}/closure` | Detalle propio OPEN → Cerrar postulaciones → confirmación |
| E26 | `GET /social-accounts/authorizations/{authorizationId}` | Redes sociales → regreso/reanudación de autorización |

Preservar estos límites en ambos proyectos:

- Identity es dueño de Account, Profile, sesión y vinculación social. Sus puertos aíslan validación de acceso, recuperación, OAuth y persistencia.
- Campaign es dueño de requisitos, especificaciones esperadas, compensación ofrecida, campañas y postulaciones; no crea entregables reales, pagos o acuerdos.
- Collaboration, Billing y Performance permanecen en sus contextos para implementación posterior. La compensación indicada en una campaña no es un pago ejecutado.
- Domain no depende de frameworks/DTOs. Application coordina casos de uso contra contratos. Infrastructure implementa REST/storage/persistencia. Interface backend y Presentation móvil exponen los casos de uso.
- Android sigue el patrón de EasyVet: repositorios en Domain, casos de uso en Application, Retrofit/DTOs/mapeadores/DI en Infrastructure y ViewModel/UiState/Compose en Presentation. Mantener nombres de contexto del reporte, sin añadir Matching como contexto nuevo.
- `Screen → ViewModel → UseCase → Repository → Adapter REST` es el flujo; navegación maneja IDs y sesión, nunca datos de muestra como autoridad de negocio.

## 6. Secuencia final de implementación

### Fase 1. Estabilizar contrato backend dentro del alcance

JWT y B01–B04 están implementados en backend por solicitud posterior. Validación de acceso encapsulada y documentación de contratos presentes/futuros en su README. La integración móvil sigue pendiente; las rutas generales del README del reporte son una especificación posterior y no deben asumirse operativas.

**Validación:** tests existentes y nuevos de filtros/plazos, ownership, descarte DRAFT, cierre sin efectos en postulaciones, OAuth/resultado, idempotencia y migraciones desde el esquema V5. No marcar como terminada la fase con proveedores reales sin verificar las credenciales y redirect.

### Fase 2. Infraestructura móvil y modelos

**Implementada el 8 de octubre de 2026.** Detalle y evidencia en `BASE_MOVIL_DDD_V1.md`: 25 operaciones iniciadas por Android y el callback de proveedor documentado por separado, modelos/puertos/casos de uso DDD, Hilt, configuración por entorno y sesión cifrada preparada. La fase 3 ya conecta las pantallas de autenticación; las demás continúan como prototipo.

Configurar Retrofit/OkHttp/Gson, Hilt/KSP, lifecycle/ViewModel, navegación y URL por entorno, siguiendo EasyVet sin copiar sus versiones indiscriminadamente. `INTERNET`, HTTP local solo debug, HTTPS release y desugaring para fechas en minSdk 24.

Modelar Account/Session/CreatorProfile/SocialAccount/AuthorizationAttempt y CampaignSummary/Details/Requirement/DeliverableSpecification/CompensationTerms/Application/Page; UUID tipados, BigDecimal e instantes. Crear requests estrictos, mapeadores, repositorios suspend y fallos de aplicación. No convertir un error HTTP en una lista vacía ni fallback Preview.

**Validación:** contracts HTTP, Bearer solo en host/endpoints protegidos, 204, nulos, decimales, fechas y ApiError. Previews usan UiState de muestra sin Hilt/red.

### Fase 3. Registro, sesión y recuperación

**Implementada el 8 de octubre de 2026.** `AUTENTICACION_MOVIL_V1.md` documenta las seis operaciones conectadas, el coordinador Application, ViewModel/Screens, sesión verificada, aislamiento de navegación, expiración y deep link de nueva contraseña. Se eliminaron login por cualquier password, selector de rol y errores simulados. Las pruebas incluyen MySQL y entrega SMTP a Mailpit, reset de un solo uso y revocación del JWT anterior. La comprobación visual/interacción en teléfono o AVD continúa pendiente por ausencia de dispositivo conectado. La fase 4 también está implementada; las fases 5–8 siguen pendientes.

Conectar E01–E06: registro de ambos tipos → Login; rol del servidor; restauración verificada; expiración; logout local; deep link de nueva contraseña y 204. Quitar acceso por cualquier password y mensajes simulados. Contraseña mínima 8, máxima 128, input protegido.

Separar almacenamiento privado cifrado de sesión de navegación/formularios, sin passwords en disco ni credenciales en logs/backups. Un 401 protegido limpia sesión; Login inválido mantiene el formulario; una falla de red no borra una sesión vigente. Limpiar back stack/consultas del usuario al cambiar sesión y bloquear respuestas tardías de la cuenta anterior.

**Validación:** acceso por rol, credenciales inválidas, arranque frío, vencimiento, recuperación Mailpit, reset de uso único y revocación de sesión anterior.

### Fase 4. Perfil creador y OAuth

**Implementada el 8 de octubre de 2026.** Guía: `PERFIL_CREADOR_Y_REDES_V1.md`. Los cinco campos se precargan/guardan en el backend, con validación y errores sin perder la edición. Redes utiliza navegador oficial, retorno Android validado, consulta de intento y lista real. Se distinguen rechazo, duplicado, expiración, pendiente y fallos de proveedor/red. Sesión compartida, metadatos de cuenta actualizados tras PUT, seguimiento sin secretos en SavedStateHandle y respuestas tardías descartadas. 123 pruebas JVM pasan con MySQL/Mailpit y retorno 303 rechazado real; la autorización exitosa en proveedores y la comprobación en teléfono/AVD siguen pendientes de credenciales/dispositivo.

Conectar E07/E08 con los cinco campos editables, incluida audiencia, y precarga real. Conectar E09–E11/E26 con navegador, retorno fijo, consulta de intento y lista social real. El perfil empresarial usa únicamente E06 en el recorrido operativo; la pantalla US-12 completa continúa como prototipo.

**Validación:** guardado real tras reinicio, errores por campo, duplicado social, rechazo, intento vencido, cambio de cuenta, callback repetido y retorno sin completar. Nunca simular vínculo ante PROVIDER_NOT_CONFIGURED.

### Fase 5. Creación, condiciones y gestión limitada de campañas

Wizard paso 1 y paso 2 mantienen un formulario controlado local hasta “Guardar borrador”/“Publicar”. Así se pueden corregir metadatos antes de crear, sin implementar el PATCH futuro. “Continuar a condiciones” es navegación del formulario, no confirma una creación remota.

Al guardar/publicar, si no hay UUID se llama E12 y se conserva el retornado; después E13; para publicar también E14. Si ocurre un éxito parcial, mostrar el borrador persistido y retomar por E18/E15. Una vez persistida, sus metadatos son lectura; condiciones siguen editables solo en DRAFT. Para corregir metadatos se puede descartar un DRAFT y comenzar una nueva creación con confirmación, sin editar una campaña publicada.

El formulario de condiciones necesita requisitos repetibles (obligatoriedad/regla/valor), entregables repetibles (formato/descripción/cantidad/fecha-hora), plazo de postulación y compensación tipada. Mantener las restricciones de fechas y tipos del backend; no añadir compensación híbrida por una muestra visual.

Conectar Mis campañas a E15. En borrador ofrecer E24; en OPEN ofrecer E25. Esperar respuesta para actualizar lista/detalle. Quitar enlaces del recurso real a evaluación/acuerdos/colaboraciones todavía ficticios.

**Validación:** creación completa visible al creador (US-15), publicación incompleta bloqueada, condiciones incompatibles, éxito parcial, timeout, descarte y cierre con postulaciones conservadas.

### Fase 6. Exploración y detalle

E16 alimenta oportunidades del panel creador; E17 búsqueda paginada con texto/categoría/ubicación/compensación; E18 detalle por UUID. Cancelar consultas antiguas al cambiar filtros, resetear página, mostrar vacío distinto de error. No usar `first()` como reemplazo de un UUID inexistente.

Mostrar datos completos y `acceptsApplications`. Un recurso que expiró/cerró entre lista y detalle sigue mostrando condiciones y el aviso US-18; enviar una propuesta vuelve a depender de la validación del servidor.

**Validación:** ambos listados usan misma disponibilidad, total correcto, filtros combinados, páginas, detalle cerrado/vencido, 404 y permisos.

### Fase 7. Postulaciones propias

E19 crea con mensaje y UUIDs de confirmaciones manuales; el backend verifica reglas automáticas con perfil/redes actuales. E20 lista propias, E21 precarga detalle, E22 edita solo mensaje/version y E23 cancela una PENDING/version. Mantener confirmaciones originales al editar: ese endpoint no las modifica.

Conservar mensaje ante error/conflicto, identificar requisitos por UUID, refrescar estado después de cada respuesta y bloquear escritura de versiones antiguas. Duplicado abre la postulación existente si se encuentra; una cancelada permanece visible como cancelada sin volver a postular.

**Validación:** todos los escenarios de US-19, incluido requisito incumplido, duplicado, edición y cancelación, más concurrencia y aislamiento de cuentas.

### Fase 8. Limpieza de UI y aceptación completa

- Eliminar botones “Ver credenciales inválidas”, “Simular autorización”, “Ver requisito incumplido”, “Ver duplicado”, “Ver seleccionada/rechazada” y otros toggles de negocio de pantallas conectadas.
- Conservar navegación legítima por tabs, Volver y acciones de formulario. Se navega tras éxito cuando ese éxito es condición de la siguiente pantalla.
- Home muestra nombres/datos disponibles; no anuncia postulantes, acuerdos, entregas o contadores ficticios. `total` de todas las campañas no se etiqueta “activas”; pendientes no se calculan de una sola página como total global.
- Mantener las pantallas posteriores existentes en recorridos separados de prototipo/previews, sin asociarlas a UUIDs reales ni presentar sus datos como respuesta del backend.
- Separar composables Route (ViewModel) de Screen (UiState/callbacks), conservar todas las previews y añadir estados de carga/vacío/error/success relevantes.
- Actualizar README operativo y verificar cada operación E01–E26, build debug y ejecución en teléfono/emulador, con las pruebas pertinentes y cuentas de prueba.

## 7. Restricciones que la implementación no debe romper

1. Un tipo de cuenta no puede cambiarse con un selector ni en un request de perfil.
2. El usuario no decide brandId/creatorId/accountId del actor; el servidor deriva propiedad de la sesión.
3. Un borrador no es visible para creadores. Una publicada tiene condiciones completas, y esas condiciones no se reescriben en esta V1.
4. Cerrar recepción no equivale a rechazar postulantes, cancelar colaboraciones o ejecutar compensaciones.
5. Selección/rechazo, snapshots del acuerdo y colaboración bilateral siguen siendo US-20/US-21 posteriores.
6. Mantener modelos y enum futuros no significa exponer comandos de esas capacidades.
7. No añadir perfiles públicos detallados, cifras de seguidores o métricas para “enriquecer” postulaciones; esa información no está en el contrato actual.
8. Tokens de proveedores y de sesión jamás se usan como argumentos de Screens ni en URI de retorno. La red social se confirma por backend.
9. Operaciones de resultado incierto se reconcilian/reintentan con idempotencia; nunca se inventa un éxito ni se repite ciegamente una creación.
10. No cambiar URLs existentes solo para que coincidan con TS posteriores. Registrar las diferencias para esa implementación futura; conservar versionado API y compatibilidad.

## 8. Evidencia y estado del trabajo

Referencias principales del reporte:

- README:2085–2130: US-11; README:2138–2180: US-12; README:2187–2280: US-13/US-14.
- README:2290–2535: US-15–US-19; README:2548–3100: historias funcionales posteriores.
- README:3118–3285: TS-01–TS-03, con rutas/operaciones previstas; README:3708–3751: orden completo del backlog.
- README:3773 y README:4176: cierre de campaña en EventStorming y `Campaign.close()`; README:4188: duplicados activos.
- README:4141: AccessTokenProvider sin formato JWT; README:3931 y README:4248: condiciones aceptadas independientes de modificaciones posteriores.

Evidencia backend: `AuthController`, `DatabaseAccessTokenProvider`, `BearerSessionFilter`, `UserProfileController`, `CampaignController`, `ApplicationController`, `CampaignSearchAdapter`, `CampaignPersistenceAdapter`, `SubmitApplicationCommandHandler`, `DatabaseAuthorizationStateStore`, `HttpSocialOAuthClient` y la migración V5 de postulaciones.

Esta revisión contrastó las primeras 18 posiciones, las User Stories posteriores, las Technical/Spike Stories y las reglas DDD relevantes. No se modificó el README del reporte ni código funcional de backend/Android en esta solicitud: se preparó el plan final. Los tests existentes se inspeccionaron como evidencia; no se ejecutó el backend ni se verificaron proveedores externos.

La implementación debe empezar por la fase 1 de este documento y avanzar con sus criterios de aceptación, sin ampliar la entrega a funcionalidades de las posiciones posteriores.
