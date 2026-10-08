# Plan de integración de CollabPro Android con Platform V1

> Las decisiones de implementación de este análisis inicial fueron sustituidas por [el plan final contrastado con el Product Backlog](PLAN_IMPLEMENTACION_FINAL_V1.md). El inventario E01–E23 de este documento describe los endpoints actualmente existentes; los cambios propuestos y el alcance definitivo están en el plan final.

Fecha del análisis: 6 de octubre de 2026.

## 1. Objetivo y alcance comprobado

Integrar todas las operaciones REST implementadas en `C:/Users/fabio/Documents/platform` desde los recorridos correspondientes de `C:/Users/fabio/AndroidStudioProjects/CollabPro`, conservando los bounded contexts y las capas DDD del proyecto y la organización de `C:/Users/fabio/Documents/easyvet-mobile`.

El resultado debe ser una app con registro, sesión, recuperación completa, perfil de creador, vinculación social, preparación/publicación de campañas, exploración y gestión de postulaciones conectados al servidor. Las demás capacidades conservan sus pantallas de demostración, claramente identificadas y separadas de los recursos reales.

Este documento es un plan: el análisis se realizó leyendo controladores, contratos, servicios, reglas de dominio, seguridad, adaptadores de persistencia, configuración, migraciones y pruebas existentes; también se revisaron las pantallas, navegación, modelos y repositorios móviles. No se ejecutó el backend ni su suite de pruebas, ni se comprobó un despliegue o una cuenta real de los proveedores OAuth. No se modificó el código funcional de ninguno de los dos proyectos.

| Bounded context | Backend actual | Decisión para Android |
|---|---|---|
| Identity & Profile Management (`identity`) | 11 operaciones REST implementadas | Integración completa de las operaciones disponibles |
| Campaign Management (`campaign`) | 12 operaciones REST implementadas, incluidas postulaciones | Integración completa de las operaciones disponibles |
| Collaboration Management (`collaboration`) | Estructura de paquetes; ningún controlador REST | Mantener prototipo |
| Billing & Compensation Management (`billing`) | Estructura de paquetes; ningún controlador REST | Mantener prototipo |
| Performance & Attribution Management (`performance`) | Estructura de paquetes; ningún controlador REST | Mantener prototipo |

El backlog actualizado ubica entre sus primeras 18 posiciones las historias US-09, US-10, US-11, US-13, US-14, US-15, US-16, US-17, US-18 y US-19, además de las historias informativas de la landing. Las 18 posiciones no equivalen a US-01–US-18. US-12 (edición del perfil empresarial) y US-20 (evaluación por la empresa) están fuera de los endpoints actuales.

## 2. Hallazgos que determinan la implementación

1. **El registro no entrega sesión.** Devuelve una cuenta activa y dos UUID, sin token. Después de crear la cuenta se abrirá Login con confirmación de registro y correo precargado. El acceso al panel ocurrirá después de autenticar.
2. **El rol lo establece el servidor.** Login devuelve `account.accountType` (`BRAND` o `CREATOR`). Se elimina el selector de rol de Login; la elección empresa/creador se conserva al registrarse.
3. **El token es opaco, no JWT.** La implementación actual lo emite por 3600 segundos y devuelve `expiresAt`. No existen endpoints de refresh ni de logout. La app usará la fecha devuelta, sin intentar decodificar el token. Cerrar sesión será una limpieza local; no revocará la sesión remota.
4. **Account y Profile son identificadores distintos.** `brandId` y `creatorId` corresponden a perfiles, no a `accountId`. La app no enviará IDs de propietario: el backend obtiene al actor del Bearer.
5. **El perfil empresarial solo tiene lectura parcial.** `GET /accounts/me` ofrece nombre, tipo, estado e IDs. No hay lectura de rubro/ubicación/descripción empresarial ni escritura de ese perfil. La pantalla de empresa mostrará la información real disponible; los campos y acciones sin soporte quedarán fuera del formulario conectado.
6. **La campaña se crea en tres operaciones.** Crear metadatos genera un `DRAFT`; guardar condiciones es otra operación; publicar requiere una tercera. Es necesario conservar el UUID del borrador y manejar éxito parcial. No hay endpoint para editar sus metadatos, eliminarlo, cerrarlo o cancelarlo.
7. **Las condiciones son estructuradas.** Requisitos y entregables son listas, la compensación tiene tipo y campos propios, y las fechas son instantes. El formulario actual de cuatro textos no representa el contrato.
8. **Estado abierto no basta para postular.** Los listados buscan `OPEN` sin excluir campañas con plazo vencido. El detalle devuelve `acceptsApplications`; ese indicador y la respuesta del servidor controlarán la acción.
9. **Los requisitos tienen reglas explícitas.** No se interpreta la descripción para inferir reglas. Nicho, ubicación y red autorizada se comprueban en backend; los requisitos manuales se confirman mediante sus UUID.
10. **Una postulación cancelada sigue impidiendo otra postulación a la misma campaña.** La unicidad es por campaña y creador, independientemente del estado. No se ofrecerá “volver a postular” después de cancelar.
11. **Solo una postulación `PENDING` se puede editar o cancelar.** El backend permite esas operaciones según el estado de la postulación; la app no agregará una prohibición por vencimiento de campaña que el contrato no establece.
12. **No existe evaluación de postulantes por empresa.** Las operaciones de postulaciones actuales son del creador y exigen ese rol. Las pantallas `ApplicantsScreen` y `ApplicantDetailScreen` seguirán como prototipo; `applications/mine` no se usará para llenarlas.
13. **Hay concurrencia en las postulaciones.** Las respuestas entregan `version`; aunque `expectedVersion` es opcional en backend, Android lo enviará siempre al editar/cancelar.
14. **OAuth tiene condiciones externas.** El backend conserva los secretos y exige un redirect HTTPS configurado para cada proveedor. Su callback devuelve JSON, no redirige a la app. El estado OAuth vence a los 600 segundos y se consume una sola vez.
15. **La recuperación tiene dos pasos.** Pedir el correo es insuficiente: también debe consumirse `password-resets`. El enlace configurado por defecto es `collabpro://password-reset?token=...`; el token vence a los 1800 segundos y cambiar la contraseña invalida las sesiones previas.
16. **El servidor rechaza propiedades desconocidas.** Los requests deben contener exclusivamente los campos del contrato. No se envían rol, propietario, status, IDs ni datos de demostración adicionales.
17. **No hay eventos para Android en tiempo real.** Los eventos de dominio internos no son endpoints, SSE ni WebSockets. La actualización móvil será por respuestas REST y recarga de las consultas afectadas.

## 3. Inventario completo: 23 operaciones y su recorrido

Los paths de esta tabla parten de `/api/v1`. `Público` significa que no requiere Bearer; las demás operaciones requieren una sesión. La autorización de negocio se verifica adicionalmente en los servicios del contexto.

### Identity: 11 operaciones

| ID | Método y path | Actor | Pantalla/recorrido | Request y resultado relevante |
|---|---|---|---|---|
| E01 | `POST /auth/brands` | Público | Registro de empresa | `businessName, email, password` → 201 `Account` |
| E02 | `POST /auth/creators` | Público | Registro de creador | `displayName, email, password` → 201 `Account` |
| E03 | `POST /auth/sessions` | Público | Login | `email, password` → 200 `account, accessToken, tokenType, expiresAt` |
| E04 | `POST /auth/recovery-requests` | Público | Recuperar acceso: solicitar correo | `email` → 202 `{message}` genérico |
| E05 | `POST /auth/password-resets` | Público | Recuperar acceso: formulario de nueva contraseña desde enlace | `token, newPassword` → 204 sin cuerpo |
| E06 | `GET /accounts/me` | Empresa/creador | Restauración de sesión, nombre del panel y resumen de cuenta empresarial | 200 `accountId, profileId, name, accountType, status` |
| E07 | `GET /profiles/me/creator` | Creador | Perfil de creador | 200 `profileId, displayName, biography, niche, audienceDescription, location` |
| E08 | `PUT /profiles/me/creator` | Creador | Guardar perfil | `displayName, biography, niche, audienceDescription, location` → perfil actualizado |
| E09 | `POST /social-accounts/{platform}/authorizations` | Creador | Redes sociales: iniciar vinculación | Sin cuerpo → `{authorizationUrl}` |
| E10 | `GET /social-accounts/{platform}/callback` | Público; propietario resuelto por `state` | Regreso del proveedor durante vinculación | Query `state` y `code` o `error` → cuenta social o error |
| E11 | `GET /social-accounts/me` | Creador | Redes sociales: carga inicial y recarga al volver | Array de `id, platform, username, status` |

### Campaign: 12 operaciones

| ID | Método y path | Actor | Pantalla/recorrido | Request y resultado relevante |
|---|---|---|---|---|
| E12 | `POST /campaigns` | Empresa | Nueva campaña: continuar a condiciones | `title, objective, description, category, targetAudience, location` → 201 detalle `DRAFT` |
| E13 | `PUT /campaigns/{id}/conditions` | Empresa propietaria | Condiciones: guardar borrador o publicar | `requirements[], deliverables[], applicationDeadline, compensation` → detalle actualizado |
| E14 | `POST /campaigns/{id}/publication` | Empresa propietaria | Condiciones/detalle de borrador: publicar | Sin cuerpo → detalle `OPEN` |
| E15 | `GET /campaigns/mine` | Empresa | Mis campañas y resumen del panel empresarial | `page, size` → página de resúmenes propios |
| E16 | `GET /campaigns/published` | Creador | Oportunidades recientes del panel de creador | `page, size` → página de campañas `OPEN` |
| E17 | `GET /campaigns` | Creador | Explorar campañas | `q, category, location, compensationType, page, size` → página de resultados |
| E18 | `GET /campaigns/{id}` | Empresa propietaria/creador | Detalle real, continuar borrador y condiciones al postular | Detalle completo con `requirements, deliverables, acceptsApplications` |
| E19 | `POST /campaigns/{id}/applications` | Creador | Enviar postulación | `message, confirmedRequirementIds[]` → 201 postulación `PENDING` |
| E20 | `GET /applications/mine` | Creador | Mis postulaciones y resumen del panel de creador | `page, size` → página de postulaciones propias |
| E21 | `GET /applications/{id}` | Creador propietario | Consultar o abrir edición de una postulación existente | Postulación con mensaje, estado y `version` actuales |
| E22 | `PUT /applications/{id}` | Creador propietario | Guardar edición | `message, expectedVersion` → postulación actualizada |
| E23 | `POST /applications/{id}/cancellation` | Creador propietario | Cancelar postulación después de confirmación | `{expectedVersion}` → postulación `CANCELLED` |

El consumo de E10 forma parte del recorrido OAuth: puede ejecutarlo la app al recibir un App Link antes del backend, o el navegador al seguir el redirect del proveedor. No se debe ejecutarlo dos veces para intentar “confirmar” la autorización.

## 4. Contratos que deben modelarse antes de conectar pantallas

### Identity

- `Account`: UUID de cuenta y perfil, nombre, `AccountType`, `AccountStatus`. No incluye email ni datos empresariales completos.
- `Session`: cuenta, token, tipo y vencimiento. El token es un dato de credencial, separado del estado visible y de los argumentos de navegación.
- `CreatorProfile`: los seis campos de E07; los datos opcionales pueden llegar nulos en un perfil recién registrado.
- `SocialAccount`: UUID, plataforma en minúsculas, username y estado (`ACTIVE`, `REVOKED`). El backend puede devolver varias cuentas de una plataforma; no reducirlas a un único booleano.
- Registro: nombre obligatorio de hasta 150 caracteres, email hasta 254, contraseña entre 8 y 128. La validación actual de 6 caracteres debe corregirse. No recortar ni cambiar la contraseña introducida.
- Perfil: `displayName` obligatorio hasta 150; biografía y audiencia hasta 2000; nicho y ubicación hasta 150. El PUT representa todos los campos editables: precargarlos y preservarlos al guardar, para no borrar campos omitidos accidentalmente.

### Campaign

- Separar `CampaignSummary` de `CampaignDetails`. El listado no contiene todos los datos del detalle.
- UUID en campaña, perfil empresarial, postulación, perfil creador, requisito y entregable. Sustituir el `Campaign.id: Int` y quitar el valor inicial `campaignId = 1`.
- Estados tipados de campaña: `DRAFT, OPEN, CLOSED, CANCELLED`; de postulación: `PENDING, SELECTED, REJECTED, CANCELLED`. Los estados terminales se renderizan si llegan del servidor; esta V1 no tiene comandos de selección/rechazo.
- `Page<T>`: `{items, total, page, size}`; página inicial 0, size entre 1 y 100, valor habitual 20. No usar el formato `content/totalElements` de Spring Data.
- `Requirement`: UUID, descripción, obligatoriedad, regla y valor esperado. `MANUAL_CONFIRMATION` no lleva valor esperado; `NICHE_EQUALS` y `LOCATION_EQUALS` sí; `AUTHORIZED_PLATFORM` acepta `instagram` o `tiktok`.
- `DeliverableSpecification`: UUID, tipo de contenido, descripción, cantidad y fecha/hora. El tipo de contenido es texto validado, no un enum backend cerrado. Cantidad entre 1 y 1000.
- `CompensationTerms`: tipos `CASH, PRODUCT, SERVICE, CREDIT, BARTER`. CASH requiere monto positivo de hasta 10 dígitos enteros y 2 decimales, moneda válida de tres letras y descripción; los otros tipos llevan descripción y no deben enviar monto/moneda. La muestra “Canje + S/ 180” no es un tipo híbrido soportado y no debe convertirse arbitrariamente en un payload.
- Mantener `BigDecimal` para el monto, nunca `Double`; formatear moneda solo en presentación.
- Las fechas REST son ISO-8601 con instante UTC. El usuario selecciona fecha y hora en su zona; Android convierte el valor al enviar y lo convierte a su zona al mostrar. Activar desugaring de `java.time` para minSdk 24.
- La fecha límite de postulación debe ser futura y cada fecha de entregable debe ser estrictamente posterior a ella. Debe existir entre 1 y 50 requisitos y entre 1 y 50 entregables. La publicación vuelve a verificar las fechas.
- Al guardar condiciones, el backend genera nuevos UUID para requisitos/entregables. Reemplazar la versión visible con la respuesta del PUT; no conservar IDs anteriores para confirmar requisitos.
- `Application`: `id, campaignId, creatorId, campaignTitle, brandName, message, status, submittedAt, confirmedRequirementIds, version`. No incluye seguidores ni perfil público del postulante. Mensaje obligatorio hasta 4000 caracteres.

### Errores y serialización

Deserializar `ApiError` como `{code, message, fieldErrors}`. Mapearlo a fallos propios sin propagar excepciones Retrofit/Gson/HTTP a `domain`. Las respuestas 204 se procesan como éxito sin intentar deserializar JSON. Admitir campos de respuesta adicionales, pero construir requests estrictos. Un fallo de contrato/deserialización debe mostrarse como error, sin datos de muestra de reemplazo.

## 5. Arquitectura móvil propuesta

Mantener los paquetes `features/identity`, `features/campaign`, `features/collaboration`, `features/billing` y `features/performance`. No crear un bounded context nuevo de Matching ni repartir postulaciones entre Campaign y Collaboration: el reporte actualizado y el backend las sitúan en Campaign.

Flujo de una operación: `Screen → ViewModel → UseCase → Repository (contrato) → RemoteRepository → ApiService → Backend`. La respuesta vuelve como modelos del contexto y estado de UI. La composición mediante DI vincula los contratos con las implementaciones.

```text
com.example.collabpro/
├── CollabProApplication.kt
├── core/
│   ├── di/                      NetworkModule y servicios técnicos compartidos
│   ├── network/                 ApiErrorDto, mapeador de fallos, Bearer interceptor
│   ├── designsystem/            Componentes existentes + loading/error/validación
│   └── presentation/            Recursos comunes de estado y formato
├── navigation/                  AppNavHost, destinos y coordinación de sesión
├── features/identity/
│   ├── domain/
│   │   ├── model/               Account, Session, CreatorProfile, SocialAccount, IDs
│   │   └── repositories/        AuthRepository, ProfileRepository, SocialAccountRepository
│   ├── application/
│   │   ├── usecases/            Registro, acceso, recuperación, perfil y redes
│   │   └── ports/               SessionStore y acceso a sesión requerido por casos de uso
│   ├── infrastructure/
│   │   ├── remote/              AuthApi, ProfileApi, SocialAccountsApi y DTOs
│   │   ├── mappers/             DTO ↔ modelos del contexto
│   │   ├── repositories/        Adaptadores REST de los tres repositorios
│   │   ├── local/               Almacenamiento de sesión cifrado
│   │   └── di/                  Bindings y módulos de Identity
│   └── presentation/
│       ├── auth/                Login, registro, recuperación; UiState y ViewModels
│       ├── profile/             Perfil y cuenta actual
│       ├── social/              Vinculación, callback y lista de cuentas
│       ├── home/                Composición de información real disponible
│       └── navigation/          Grafo de Identity
├── features/campaign/
│   ├── domain/
│   │   ├── model/               Summary, Details, Requirement, Compensation, Application
│   │   └── repositories/        CampaignRepository, ApplicationRepository
│   ├── application/usecases/    Preparación, consulta y gestión de postulaciones
│   ├── infrastructure/
│   │   ├── remote/              CampaignApi, ApplicationsApi y DTOs
│   │   ├── mappers/             Mapeadores de Campaign
│   │   ├── repositories/        RemoteCampaignRepository, RemoteApplicationRepository
│   │   └── di/                  Bindings y módulos de Campaign
│   └── presentation/            Lista, búsqueda, detalle, wizard, postulaciones y grafo
└── features/{collaboration,billing,performance}/  Pantallas de prototipo conservadas
```

### Reglas de dependencia

- `domain`: modelos, valores, contratos y fallos propios; sin Compose, Retrofit, DTOs, Context ni importaciones de implementaciones.
- `application`: casos de uso que dependen de contratos. No crea clientes HTTP, no navega y no muestra Snackbars.
- `infrastructure`: servicios REST, DTOs, mapeadores, storage y DI. Traduce el contrato remoto hacia el lenguaje local del contexto.
- `presentation`: pantalla, ViewModel, estado y eventos de interacción. Usa casos de uso; nunca construye repositorios de muestra para la ejecución real.
- La composición de los paneles puede coordinar casos de uso de Identity y Campaign en presentación; ningún modelo interno de un contexto se convierte en el modelo compartido del otro.
- El interceptor recibe un proveedor técnico de credenciales mediante un contrato, evitando que `core/network` conozca `LoginViewModel` o que acceda directamente a `AppState`.

### Herramientas y patrón de clase

Usar Retrofit con OkHttp y Gson, Hilt con KSP, ViewModel, coroutines/StateFlow, colección de estado respetando el lifecycle y Navigation Compose con destinos que llevan IDs, igual que el patrón leído en EasyVet. La serialización de rutas puede usar Kotlin Serialization aunque los DTOs REST usen Gson.

Antes de fijar versiones, verificar la compatibilidad con el AGP 9.3.3/Kotlin 2.2.10 actuales; EasyVet usa otras versiones y no se debe copiar su catálogo completo. Ajustar Java 17 para la configuración de Hilt y mantener una compilación pequeña funcionando desde esta primera fase. Las responsabilidades propuestas siguen el [flujo de datos y separación de estado de Android](https://developer.android.com/topic/architecture) y la [integración oficial de Hilt](https://developer.android.com/training/dependency-injection/hilt-android).

No introducir Room, sincronización offline ni cola de escrituras en esta V1: el alcance exige datos reales y errores explícitos. EasyVet oculta ciertos errores y consulta un cache local; para CollabPro una consulta fallida no se traducirá a una lista vacía ni a un éxito inventado. Coroutines canceladas deben propagar `CancellationException`.

## 6. Limpieza concreta de las pantallas y de la navegación

| Archivo/pantalla actual | Problema observado | Cambio durante la integración |
|---|---|---|
| `navigation/AppState.kt` | Construye `PreviewProfiles`, `PreviewCampaigns` y todos los datos de demo; rol e ID inicial ficticios | Sacar datos de Identity/Campaign de AppState; separar sesión de navegación y mantener muestras fuera de sus recorridos reales |
| `navigation/CollabApp.kt` | Autoriza visualmente según `route.ordinal`; guarda solo enum, no parámetros del recurso | Grafo público/autenticado, guardas de sesión/rol y destinos con `campaignId/applicationId` |
| `RegisterScreen` | Contraseña mínima 6; entrar directamente al panel; toggle de duplicado | E01/E02, validación de 8–128, éxito a Login y error real `EMAIL_ALREADY_REGISTERED` |
| `LoginScreen` | Cualquier credencial no vacía; botón de error; selector empresa/creador | E03, token/rol reales; quitar “Ver credenciales inválidas”, selector y texto de acceso de prototipo |
| `RecoverScreen` | `sent = email.contains('@')`; solo simula el envío | E04 y segundo modo del formulario para E05, abierto desde enlace con token |
| `ProfileScreen(brand=false)` | Datos de Camila, ubicación de empresa, audiencia mediante `LocalEntry` sin estado del formulario | E07/E08; todos los campos controlados, incluida audiencia; precargar desde servidor |
| `ProfileScreen(brand=true)` | Formulario completo y guardado ficticio sin endpoint equivalente | Leer E06; mostrar datos disponibles de cuenta; no ofrecer guardar datos empresariales como si persistieran |
| `SocialAccountsScreen` | “Simular autorización”, “Simular autorización rechazada”, “Ver cuenta ya vinculada” | E09/E10/E11; reemplazar simulaciones por lista real, autorización externa y estados reales |
| `HomeScreen` | Nombres, contadores 2/1, acuerdos y tareas inventados; logout solo navega | Nombre de cuenta real; resumen medible mediante E15/E20; feed E16; logout limpia sesión y back stack |
| `BrandCampaignsScreen` | Todas las campañas de muestra, incluidas ajenas | E15 paginado; mostrar propios OPEN/DRAFT; continuar condiciones de borrador por su UUID |
| `CampaignFormScreen` | No guarda datos; navegar a condiciones pierde metadatos | E12 antes de continuar; conservar UUID retornado; añadir descripción/ubicación al formulario |
| `CampaignTermsScreen` | Cuatro textos; validación solo de `/`; publicar cambia un booleano | Formulario de listas/fechas/compensación; E13 y luego E14; quitar “Ver condiciones incompatibles” |
| `CampaignSearchScreen` | Filtro local sobre tres muestras | E17 con q/categoría/ubicación/compensación y paginación; estados vacíos/error reales |
| `CampaignDetailScreen` | `firstOrNull ?: first()` sustituye IDs inválidos por otra campaña; condiciones genéricas | E18 por UUID obligatorio; 404 real; renderizar campos completos y `acceptsApplications` |
| `CampaignDetailScreen` para empresa | Accesos directos a postulantes y colaboración ficticia | Quitar esos accesos del detalle conectado; ofrecer preparación/publicación únicamente en DRAFT |
| `ApplicationFormScreen` | No registra; propuestas no precargadas; requisitos/duplicados a demanda | E19 al crear; E21/E22 al editar; checks por UUID para manuales; errores reales |
| `MyApplicationsScreen` | Una tarjeta fija y estados elegidos con botones | E20 paginado; seleccionar UUID real; E21 para consultar; E23 para cancelar; quitar selección/rechazo manuales |
| `ApplicantsScreen`, `ApplicantDetailScreen` | Parecen relacionados con postulaciones pero no tienen API de empresa | Mantener como prototipo fuera del recorrido de una campaña real |
| `core/designsystem/Components.kt` | Inputs sin detalle de error, contraseña visible, `LocalEntry` autónomo | Ampliar Entry para password/email/decimal/errores de campo; loading y botones deshabilitados; campos conectados controlados |
| `navigation/PreviewScreens.kt` | Cada preview construye AppState con datos de demo | Mantener todas las previews; renderizar Screens con UiState de ejemplo y callbacks sin red/DI |

Los botones normales de navegación se conservan: “Crear cuenta”, “Olvidé mi contraseña”, “Explorar”, “Mis postulaciones”, “Volver” y la barra inferior siguen siendo necesarios. Se reemplazan los botones que inventan respuestas, alteran estados de negocio o saltan un paso obligatorio de backend. Navegar a condiciones requiere un borrador creado; entrar al panel requiere una sesión; confirmar publicación requiere la respuesta de E14.

En el panel conectado se quitarán tarjetas que afirman actividad inexistente (“Tienes creadores interesados”, “Acuerdo por confirmar”, entregas pendientes, etc.). Se podrán conservar enlaces genéricos hacia las pantallas sin API dentro de un área identificada como prototipo, sin trasladarles el UUID de una campaña real ni mostrar sus datos de muestra como resultados de esa campaña.

## 7. Plan ordenado de implementación

### Fase 1. Contratos, red y composición DDD

1. Crear modelos tipados, repositorios suspend y fallos de Identity/Campaign; dejar muestras únicamente para previews/tests y contextos no integrados.
2. Implementar los cinco servicios REST, DTOs request/response y mapeadores. Cada operación E01–E23 debe tener un consumidor o recorrido identificado.
3. Configurar Retrofit/OkHttp/Gson, Hilt/KSP, ViewModel/lifecycle y Navigation Compose. Añadir `INTERNET`, `BuildConfig.API_BASE_URL` y `java.time` desugaring.
4. Diferenciar cliente público (auth/callback) y autenticado: solo requests protegidos al host del backend llevan Bearer. No enviar un token antiguo a Login/recuperación/callback, porque el filtro puede rechazarlo antes de ejecutar incluso un endpoint público.
5. Centralizar `ApiError`, errores de transporte, timeout y deserialización; no instalar reintentos automáticos de comandos.

**Entrega:** infraestructura compila, contratos y rutas HTTP comprobados con fixtures del backend; ninguna pantalla conectada depende de DTOs o de un repositorio de demo.

### Fase 2. Sesión y navegación autenticada

1. Crear `SessionStore` con token cifrado mediante Android Keystore y almacenamiento privado persistente; guardar account/expiry sin passwords. Excluir credenciales de backups y logs.
2. Añadir `RegisterBrand`, `RegisterCreator`, `SignIn`, `GetCurrentAccount`, `RestoreSession` y `SignOutLocal`.
3. Al arrancar: leer sesión; si falta o venció, mostrar recorrido público; si sigue vigente, verificar E06 antes de abrir un panel protegido. Un error de red no borra una sesión vigente ni se presenta como credenciales inválidas: mostrar reintento.
4. Configurar grafos por sesión y rol. Los IDs van en argumentos recuperables por `SavedStateHandle`; retirar `route.ordinal`, `variant = "edit"` y el ID inicial arbitrario de las pantallas conectadas.
5. Al confirmar login/registro/logout, actualizar una sola vez el estado de navegación; login y logout limpian la pila anterior para que Back no reabra pantallas de la sesión previa.
6. 401 en endpoints protegidos invalida la sesión local y pide Login; 401 `INVALID_CREDENTIALS` del propio Login se queda en el formulario. 403 muestra restricción o cuenta inactiva, no un refresh ficticio.
7. Cancelar cargas y vaciar estado por usuario al cerrar sesión/cambiar cuenta; impedir que una respuesta de la cuenta anterior rellene la siguiente.

**Entrega:** dos roles reales, registro a Login, sesión restaurada/expirada, logout local y rutas protegidas correctas. E01, E02, E03 y E06 completos.

### Fase 3. Recuperación y perfil

1. Conectar solicitud E04; mostrar siempre el mensaje genérico recibido, sin revelar si existe la cuenta.
2. Ampliar `RecoverScreen` con modo de nueva contraseña y confirmación. Registrar el deep link `collabpro://password-reset`, recibirlo tanto en arranque frío como con la app abierta y validar esquema/host/parámetros.
3. El token no va a logs ni al historial persistido de navegación; mantenerlo solo durante el recorrido de reset y tratarlo como sensible.
4. E05: esperar 204, limpiar sesión si procede y abrir Login. Distinguir token inválido/vencido y ofrecer pedir otro enlace.
5. Implementar E07/E08 con un estado de formulario completo; al guardar, reemplazar datos por la respuesta y actualizar el nombre visible de la cuenta/panel mediante E06 o invalidación de la consulta.
6. Adaptar el perfil empresarial al resumen E06. No mapear datos que el backend no entrega.

**Entrega:** recuperación de extremo a extremo y perfil de creador persistido; E04, E05, E07 y E08 completos. El email de prueba se obtiene por Mailpit en entorno local, sin leer tokens de la base desde la app.

### Fase 4. Preparación/publicación de campañas empresariales

1. Sustituir el catálogo local empresarial por E15 y página real. Gestionar carga inicial, vacío, error, actualizar y cargar más.
2. Wizard paso 1: formulario de metadatos; crear mediante E12 y navegar con el UUID retornado. Deshabilitar doble envío. Una vez creado, usar ese UUID para continuar sin generar otro borrador al volver del paso 2.
3. Cargar E18 si se retoma un borrador desde Mis campañas. Metadatos guardados son lectura, porque no existe PUT de metadatos.
4. Wizard paso 2: editor de requisitos y entregables (añadir/quitar), obligatoriedad/regla/valor, cantidad, fechas/horas y compensación con campos condicionales. Mostrar condiciones precargadas de un borrador existente.
5. “Guardar borrador” consume E13. “Publicar” primero guarda condiciones y después consume E14. Si se guardó pero no se pudo publicar, conservar borrador y respuesta; reintentar únicamente la publicación mientras esas condiciones no hayan cambiado.
6. Ante pérdida de conexión después de publicar, consultar E18 antes de repetir: si ya está OPEN, reflejar éxito; si sigue DRAFT, permitir reintentar. Ante timeout de creación sin UUID, recargar Mis campañas y permitir reconocer el borrador; la API no ofrece una clave de idempotencia, por lo que no debe repetirse automáticamente ese POST.
7. Al guardar/publicar, invalidar detalle/lista/resumen; refrescar E15 y presentar estado real. OPEN/CLOSED/CANCELLED se muestran en lectura sin editar/publicar.

**Entrega:** borrador recuperable, condiciones completas y publicación real sin duplicados automáticos; E12, E13, E14, E15 y E18 completos para empresa.

### Fase 5. Exploración y detalle para creadores

1. Usar E16 para oportunidades recientes del panel: así este endpoint tiene una función distinta de la búsqueda.
2. Conectar E17 en Explorar con texto, categoría, ubicación y tipo de compensación. Normalizar “Todas” a query ausente, no enviar ese literal como categoría.
3. Consultar al buscar/cambiar filtros; debounce breve y cancelación de la consulta anterior. Resetear a página 0 al cambiar cualquier filtro y evitar que llegue un resultado anterior a sobrescribir el nuevo.
4. Leer y acumular páginas con `total/page/size`, eliminando duplicados por UUID; una primera página vacía representa “sin coincidencias”, un error representa un error.
5. Abrir E18 por UUID y mostrar objetivo, descripción, audiencia, requisitos, entregables, todas las fechas y compensación estructurada. Eliminar la selección alternativa de `first()`.
6. Mostrar postulación solo para creador y `acceptsApplications=true`; antes de enviar, el backend vuelve a decidir. Mostrar campaña vencida/cerrada si no admite postulaciones aunque su status aún sea OPEN.

**Entrega:** filtros y paginación reales, detalle completo y estados no disponibles; E16, E17 y E18 completos para creador.

### Fase 6. Postulaciones propias

1. Crear `SubmitApplication`, `GetMyApplications`, `GetApplication`, `UpdateApplication` y `CancelApplication` dentro de Campaign.
2. Postular: cargar campaña y condiciones actuales, recoger mensaje y UUID de confirmaciones manuales. No enviar confirmaciones de reglas automáticas ni IDs de otras campañas.
3. E19: mostrar éxito solo tras 201; guardar respuesta e invalidar E20/resumen del panel. Evitar doble envío. Ante timeout, consultar Mis postulaciones para reconciliar antes de ofrecer otro envío.
4. E20: lista paginada con título, empresa, fecha, mensaje y estado reales. Abrir una entrada por `applicationId`; E21 precarga el detalle y la versión actuales.
5. Reutilizar el formulario en modo consulta/edición: si PENDING, permitir edición; en estados terminales mostrar lectura. E22 modifica solo mensaje y envía `expectedVersion`; las confirmaciones originales no se editan por ese endpoint.
6. E23: pedir confirmación, enviar versión y reemplazar la tarjeta por la respuesta. Nunca cambiar estado local antes del éxito confirmado.
7. Manejar `CONCURRENT_UPDATE`: conservar el texto del usuario, recargar E21 y mostrar la diferencia/conflicto; no sobrescribir ni cancelar automáticamente. `APPLICATION_NOT_PENDING` actualiza el detalle y bloquea la operación.
8. Ante duplicado, ofrecer abrir la postulación existente si se encuentra en E20. Si está cancelada, informar la restricción de repostulación de la V1.

**Entrega:** creación/lectura/edición/cancelación real y consistencia con versiones; E19–E23 completos.

### Fase 7. Vinculación social y callbacks

1. Implementar E11 al entrar a Redes sociales; lista vacía = sin vínculos, con posibilidad de iniciar autorización. No existe desvinculación ni consulta de métricas en esta API.
2. E09: usar `instagram`/`tiktok`, recibir `authorizationUrl` y abrirla en Custom Tabs. No construir URLs ni almacenar secretos de proveedor en Android.
3. Configurar los proveedores en el entorno backend y registrar exactamente sus redirect URI HTTPS. Es una dependencia de despliegue, no un endpoint móvil adicional.
4. Para retorno integrado en Android: usar App Links verificados para las mismas URLs HTTPS de callback. Publicar `/.well-known/assetlinks.json` con package/fingerprint en el dominio mediante el servidor estático/proxy del despliegue, y comprobar su acceso público; la configuración Spring actual no publica ese archivo.
5. Cuando Android intercepte el redirect antes del backend, validar host/path/plataforma y el estado de la operación pendiente; enviar `state, code/error` a E10 una sola vez mediante el cliente público. Mostrar su resultado y recargar E11. El código se intercambia exclusivamente en backend.
6. Si el navegador ya ejecutó E10 (por falta de asociación App Link), no reenviar callback. Al regresar a la app, recargar E11 y permitir “Actualizar estado”. La respuesta JSON del callback actual no garantiza un regreso automático ni permite a Android recuperar por sí solo el error ocurrido en el navegador.
7. Persistir únicamente el estado mínimo de la autorización pendiente durante sus diez minutos, ligado al usuario/plataforma; borrarlo al completar, rechazar, vencer o cerrar sesión. Nunca reutilizar code/state ni asumir vinculación por haber vuelto del navegador.
8. Renderizar `PROVIDER_NOT_CONFIGURED`, `PROVIDER_FAILED`, `AUTHORIZATION_DENIED`, `INVALID_OAUTH_STATE`, `SOCIAL_ACCOUNT_ALREADY_LINKED` y `CONCURRENT_UPDATE`. No reemplazar esos casos por simulaciones.

**Entrega:** E09–E11 integrados, autorización real y retorno verificable. Se puede implementar todo el cliente con respuestas controladas durante desarrollo; la aceptación real de esta fase requiere credenciales, redirect HTTPS y proveedor operativo. No marcarla completa solamente porque funcionen mocks.

El recorrido usa [Custom Tabs](https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs), [deep links](https://developer.android.com/training/app-links/create-deeplinks) y [verificación de App Links](https://developer.android.com/training/app-links/verify-applinks). El esquema de recuperación `collabpro://` y el redirect social HTTPS son mecanismos distintos.

### Fase 8. Paneles, previews y validación completa

1. Panel empresarial: nombre real de E06 y total de campañas de E15, etiquetado como “Total de campañas”, no “activas” si incluye borradores. Panel creador: nombre real, total de postulaciones E20 y oportunidades E16.
2. No calcular totales de estados sobre una sola página como si fueran el total global. No hay endpoint de dashboard; omitir contadores de colaboraciones/entregas y el contador de pendientes si no se consultan todas las páginas necesarias.
3. Revisar todas las entradas a pantallas reales para exigir sesión, rol y UUID válido. Los accesos a postulantes/acuerdos/entregas/pagos/resultados quedan en recorridos de prototipo con identificación visible.
4. Separar `XxxRoute` (obtiene ViewModel) de `XxxScreen(uiState, callbacks)` (renderiza). Previews con fixtures de loading, vacío, éxito y error no llaman al backend ni necesitan una sesión real. Mantener cobertura de todas las pantallas existentes.
5. Actualizar README con configuración local, qué funciona realmente y qué continúa como maqueta; quitar instrucciones de “cualquier correo/contraseña”.
6. Ejecutar los criterios de aceptación y cobertura E01–E23; registrar resultados y dependencias pendientes.

**Entrega:** todos los endpoints tienen recorrido verificable, todas las pantallas integradas dependen del backend y las previews siguen funcionando.

## 8. Estados de UI y respuesta a fallos

Cada pantalla conectada necesita carga, datos, vacío cuando aplique, error con reintento y estado de operación (`submitting/saving/publishing/cancelling`). Un éxito de escritura se basa en la respuesta confirmada. Un error de red mantiene el formulario; no navega ni declara éxito. Evitar que una recomposición repita comandos o eventos de navegación.

| Respuesta/código | Comportamiento móvil |
|---|---|
| 400 `VALIDATION_ERROR` | Mostrar `fieldErrors` en los inputs correspondientes, incluso `requirements[i]`/`deliverables[i]` |
| 400 `INVALID_REQUEST` / `INVALID_CONFIRMATION` | Mensaje real; conservar formulario y volver a cargar condiciones si hay IDs obsoletos |
| 401 `UNAUTHORIZED` en protegido | Limpiar sesión, datos del usuario y grafo; pedir Login |
| 401 `INVALID_CREDENTIALS` en Login | Permanecer en Login y mostrar error de acceso |
| 403 `BRAND_REQUIRED`, `CREATOR_REQUIRED`, `FORBIDDEN`, `ACCOUNT_NOT_ACTIVE` | Explicar restricción y bloquear operación; no resolver cambiando el rol local |
| 404 `CAMPAIGN_NOT_FOUND` / `APPLICATION_NOT_FOUND` | Estado no disponible; volver al listado; no mostrar otro recurso |
| 409 `EMAIL_ALREADY_REGISTERED` | Error del email y opciones de Login/recuperación |
| 409 `CAMPAIGN_NOT_DRAFT` | Recargar E18 y mostrar estado actual; no seguir editando/publicando |
| 409 `CAMPAIGN_NOT_ACCEPTING_APPLICATIONS` | Recargar detalle y deshabilitar envío |
| 409 `APPLICATION_ALREADY_EXISTS` | Informar y localizar postulación propia, sin crear otra |
| 409 `APPLICATION_NOT_PENDING` | Recargar E21 y poner formulario en lectura |
| 409 `CONCURRENT_UPDATE` | Recargar dato y conservar borrador local para revisión explícita |
| 422 `INVALID_CONDITIONS` / `INCOMPLETE_CAMPAIGN` | Mantener borrador y corregir listas, fechas/compensación antes de publicar |
| 422 `REQUIREMENTS_NOT_MET` | Asociar `fieldErrors[requirements.<uuid>]` al requisito e indicar qué falta; ofrecer perfil/redes si corresponde |
| Errores de OAuth / reset | Mostrar rechazo, vencimiento o configuración faltante según el código; no declarar éxito |
| Timeout, sin internet, 5xx o cuerpo no JSON | Mensaje recuperable; distinguir consultas reintentables de escrituras cuyo resultado es incierto |

## 9. Configuración local y dependencias de ejecución

- Backend Spring Boot/Java 17 con MySQL y Flyway. Su perfil por defecto es `local`; `skeleton` desactiva los controladores y no sirve para integrar. No se encontró un `server.port` explícito; verificar el puerto de ejecución (8080 por defecto de Spring Boot) antes de fijar la URL.
- Emulador Android estándar: usar `http://10.0.2.2:8080/api/v1/` si el backend corre en ese puerto del PC. Teléfono por USB: `adb reverse tcp:8080 tcp:8080` y URL `http://127.0.0.1:8080/api/v1/`. Otra opción es IP LAN accesible del PC. Son valores configurables de entorno, no URLs incrustadas en Screens.
- Permitir tráfico HTTP local únicamente en configuración debug; release usa HTTPS. La [configuración de seguridad de red de Android](https://developer.android.com/privacy-and-security/security-config) permite definir estas políticas por entorno/dominio.
- MySQL local usa puerto 3307 por defecto y Mailpit SMTP 1025/UI 8025. Android consume el backend, no MySQL ni el SMTP directamente.
- Para reset local, abrir el enlace del correo de Mailpit en el dispositivo/emulador o mediante un intent de prueba. No hay endpoint público que entregue el token de recuperación.
- Las propiedades de OAuth en la configuración usan credenciales/redirect de entorno; no se comprobó que estén configuradas al ejecutar. La app debe consumir el endpoint aun si devuelve 503 y mostrar esa limitación real.
- No hay archivos OpenAPI/Swagger ni dependencia de springdoc en el código revisado. Los contratos inventariados se obtuvieron directamente de los cinco controladores y sus resources.

## 10. Criterios de aceptación y verificación

### Pruebas de contrato móvil

Usar un servidor HTTP controlado para verificar request/response, nombres de campos, enums, UUIDs, nulos en borradores, BigDecimal, instantes, paginación, 204, fieldErrors y Bearer solo en protegido. Estos mocks pertenecen a tests/previews, no son fallback de ejecución.

### Recorridos de aceptación con backend real

1. Registrar empresa y creador; probar campos inválidos y email duplicado; confirmar que el registro no entra al panel sin sesión.
2. Iniciar sesión con ambos roles; credenciales incorrectas, sesión restaurada, vencida, cuenta distinta y logout sin reabrir pantallas por Back.
3. Pedir recuperación para email existente/no existente; recibir mensaje genérico; cambiar contraseña desde deep link; rechazar token vencido/reutilizado; verificar que la sesión anterior recibe 401.
4. Cargar/guardar perfil creador, reiniciar app y comprobar persistencia real; guardar audiencia; comprobar que el perfil empresarial no promete escritura sin soporte.
5. Crear borrador, ver E15, retomarlo por UUID, guardar/reemplazar condiciones, validar fechas y los cinco tipos de compensación, publicar y verificar visibilidad desde otra cuenta creadora.
6. Impedir que otra empresa lea/modifique la campaña; impedir crear/publicar con cuenta creadora; comprobar que un borrador no es accesible para un creador.
7. Buscar con cada filtro y combinaciones; comprobar vacío, varias páginas, cambio de filtro durante carga, detalle completo y campaña OPEN con plazo vencido.
8. Postular con manuales confirmados y con reglas automáticas satisfechas; mostrar requisitos incumplidos por UUID, duplicado y campaña que dejó de aceptar entre consulta y envío.
9. Consultar postulación, editar mensaje con versión, conservar cambios tras reiniciar y cancelar; impedir repostulación de la cancelada. Verificar 409 por versión vieja y que terminales no ofrecen editar/cancelar.
10. Vincular Instagram/TikTok con configuración real; negar permisos, volver sin completar, estado vencido, repetición y cuenta duplicada. Comprobar lista real sin tokens del proveedor y que no se dispara callback dos veces.
11. Probar timeout y falta de internet en lecturas/escrituras; el formulario conserva datos, ninguna pantalla muestra éxito inventado y un comando incierto se reconcilia antes de repetir.
12. Renderizar todas las previews y ejecutar el APK en emulador/teléfono; ningún dato de muestra de Identity/Campaign entra en un recorrido real.

Las pruebas backend existentes `RegistrationApiTests`, `IdentityLifecycleApiTests`, `CampaignPreparationApiTests` y `CampaignDiscoveryApplicationApiTests` ya describen muchos de estos contratos, incluidos concurrencia, ownership, expiración y duplicados. Sirven como fixtures y referencia; su existencia no reemplaza verificar la integración Android. La aceptación final requiere ejecutar la suite pertinente, pruebas móviles y recorridos reales en un entorno de prueba.

### Definición de terminado

- [ ] E01–E23 tienen request y respuesta verificados y un recorrido de usuario asociado.
- [ ] Los contextos integrados conservan `domain/application/infrastructure/presentation` y sus dependencias correctas.
- [ ] Login/registro/perfil/campañas/postulaciones/redes no importan repositorios Preview para ejecución real.
- [ ] Cada comando modifica la UI después de confirmar respuesta; todos los fallos relevantes son visibles.
- [ ] IDs, rol y estado vienen del backend; navegar no fabrica autorizaciones ni recursos.
- [ ] Recuperación consume ambos endpoints y OAuth incluye el callback, con sus dependencias externas comprobadas.
- [ ] No quedan botones de simulación de errores/estados en las pantallas conectadas.
- [ ] Las pantallas sin backend conservadas no se presentan como datos/acciones de una campaña real.
- [ ] Build debug, verificaciones móviles y pruebas pertinentes pasan; las previews conservan su cobertura.

## 11. Evidencia local principal

- Backend REST: `src/main/java/com/collabtech/platform/identity/interfaces/rest/{AuthController,UserProfileController,SocialMediaController}.java` y `campaign/interfaces/rest/{CampaignController,ApplicationController}.java`.
- Payloads: `identity/interfaces/rest/resources/{RegisterBrandResource,RegisterCreatorResource,AccountResource,IdentityRequests,IdentityResources}.java`, `campaign/interfaces/rest/resources/{CampaignRequests,CampaignResources}.java`, `campaign/application/projections/CampaignViews.java`.
- Seguridad y callbacks: `identity/infrastructure/configuration/IdentitySecurityConfiguration.java`, `identity/infrastructure/security/{BearerSessionFilter,DatabaseAccessTokenProvider,DatabaseRecoveryService,RecoveryMailDispatcher}.java` y `identity/infrastructure/oauth/{DatabaseAuthorizationStateStore,HttpSocialOAuthClient}.java`.
- Reglas: `campaign/domain/model/aggregates/{Campaign,Application}.java`, `campaign/domain/model/valueobjects/{RequirementRule,CompensationTerms}.java`, `campaign/domain/services/ExplicitApplicationEligibilityService.java` y handlers de postulaciones.
- Android: `navigation/{AppState,CollabApp,PreviewScreens}.kt`, `features/identity/presentation/{PublicAndIdentityScreens,HomeScreen}.kt`, `features/campaign/presentation/CampaignScreens.kt`, modelos/repositorios y `core/designsystem/Components.kt`.
- Referencia EasyVet: `core/di/NetworkModule.kt`, `features/home/domain/ProductRepository.kt`, `application/GetProductsUseCase.kt`, `infrastructure/repositories/ProductRepositoryImpl.kt`, `presentation/home/HomeViewModel.kt` y `presentation/navigation/HomeNavGraph.kt`.

Todos los paths de evidencia backend son relativos a `C:/Users/fabio/Documents/platform`; los Android a `C:/Users/fabio/AndroidStudioProjects/CollabPro`; los de EasyVet a su raíz indicada al inicio. La matriz E01–E23 es la lista de control para implementar esta versión, sin inventar endpoints para historias futuras.
