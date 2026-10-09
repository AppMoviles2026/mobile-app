# Vinculación social, paneles y validación final — V1

Actualización del 8 de octubre de 2026. Alcance: primeras 18 posiciones del Product Backlog, sin implementar US-12, US-20 ni las colaboraciones, pagos o métricas posteriores. No se añadieron endpoints ni se cambió lógica de negocio del backend en esta etapa; allí solo se reforzaron pruebas.

## Vinculación social: Identity

El recorrido es **Android → autorización del backend → Custom Tab del proveedor → callback HTTPS del backend → retorno a Android → consulta de resultado → lista real**.

1. Una sesión verificada de creador llama `POST /social-accounts/{platform}/authorizations?client=ANDROID`.
2. `SocialCustomTabs` selecciona un navegador compatible y abre únicamente la URL HTTPS oficial devuelta. Usa AndroidX Browser 1.10.0 ([referencia oficial](https://developer.android.com/jetpack/androidx/releases/browser)). No abre WebView ni envía JWT/cabeceras del API al navegador. Si no hay navegador compatible, muestra error y permite reabrir el mismo intento; no crea otra autorización automáticamente.
3. El proveedor llama al callback del backend. Solo el backend intercambia `code`, valida/consume `state` y guarda vínculo/resultado. El estado es de un solo uso. Repetir el callback devuelve 400, sin repetir el intercambio ni crear otro vínculo.
4. El retorno fijo `collabpro://social-authorization-completed?authorizationId=UUID` solo transporta el identificador opaco. La app rechaza códigos, tokens, parámetros adicionales, hosts/rutas ajenos y enlaces inválidos.
5. El ViewModel consulta `GET /social-accounts/authorizations/{id}` autenticado y recarga `GET /social-accounts/me`. Android **no tiene una operación Retrofit de callback**.

Se descartan retornos duplicados durante la sesión (ventana de 32 identificadores), sin cancelar una consulta en curso ni reiniciar un resultado terminal. La Activity elimina el enlace de su Intent tras recibirlo. Tras muerte del proceso puede repetirse una lectura idempotente del resultado, nunca el intercambio OAuth. Un fallo de consulta permite reintento explícito; volver del navegador no equivale a éxito. Para `PENDING` se realizan hasta tres lecturas separadas por un segundo y después queda la comprobación manual.

Estados visibles: carga, lista vacía confirmada, vínculos activos/revocados, pendiente, éxito, rechazo, duplicado, expiración, proveedor no configurado/fallido, navegador no disponible y error de red. Un error de lista no se convierte en “sin cuentas”; conserva la última consulta correcta con aviso. Se rechazan UUID repetidos o nombres vacíos antes de renderizar.

Solicitudes de perfil/redes y eventos de navegador quedan vinculados a cuenta y vencimiento de sesión. `ExpectedAccount` evita que una solicitud encolada use el JWT de un login posterior; cancelación/revisiones y filtrado de UiState impiden mostrar respuestas de otra sesión. El estado guardado solo contiene identificador/propietario/plataforma; URL OAuth y secretos no se persisten. «Dejar de seguir» olvida el intento local, **no cancela ni desvincula en el servidor**.

## Paneles: datos de Identity y Campaign

`HomeScreen` recibe la cuenta verificada y callbacks, sin consultar repositorios Preview. `LoadOwnActivityTotal` pertenece a Application de Campaign y usa los puertos existentes; su ViewModel pertenece a Presentation y la DI a Infrastructure.

| Dato visible | Fuente y significado |
|---|---|
| Nombre, tipo y estado | Cuenta del servidor verificada por `GET /accounts/me` |
| Total de campañas (empresa) | `GET /campaigns/mine?page=0&size=1`, campo global `total`; incluye borradores/publicadas/cerradas, no se etiqueta como “activas” |
| Total de postulaciones (creador) | `GET /applications/mine?page=0&size=1`, campo global `total`; incluye canceladas, no se etiqueta como “pendientes” |
| Oportunidades (creador) | `GET /campaigns/published`, UUID y disponibilidad reales |
| Cuenta empresarial | Nombre/tipo/estado reales en lectura, sin campos inventados ni botón de guardado US-12 |

El resumen se actualiza al entrar a Inicio, volver a primer plano o pulsar Actualizar. Se verifica página y propiedad por **ProfileId**, no AccountId. Un fallo muestra reintento, no un cero ficticio. Cambiar/cerrar sesión limpia la consulta y descarta respuestas tardías. La carga de Mis campañas tiene un efecto separado ligado a su propio propietario/vigencia, para no depender del orden de inicialización del ViewModel de Identity.

Las rutas futuras mantienen el aviso «PROTOTIPO • datos de ejemplo, no provienen del servidor». No se conectan recursos reales a selección, acuerdos, entregas, pagos o métricas inexistentes. Sus repositorios de ejemplo se cargan solo si el prototipo los utiliza.

## Previews

Se conservaron las **57 funciones Preview anteriores**. Se añadieron diez: panel cargando/vacío/error, cuenta empresarial real y seis estados sociales (carga, error de red, proveedor sin configurar, expiración, duplicado y navegador no disponible). En total hay **67 previews**.

Abrir `app/src/main/java/com/example/collabpro/navigation/PreviewScreens.kt` en Android Studio, seleccionar Split/Design y Build & Refresh. Todas usan UiState de muestra y composables puros, sin Hilt/ViewModel/red. `BrandProfilePreview` conserva el formulario completo futuro, mientras la ruta operativa usa `BrandAccountScreen`.

## Cobertura de los contratos

Todas las rutas de la tabla tienen prefijo `/api/v1`. Las **23 operaciones originales** permanecen cubiertas, más tres complementarias incorporadas previamente: descarte de borrador, cierre y consulta OAuth. Son **26 contratos lógicos: 25 iniciados por Android y uno por el navegador/proveedor**; `instagram/tiktok` son variantes de la misma ruta.

Pruebas de recorridos reales: **A** = `RealAuthenticationIntegrationTest`; **I** = `RealCreatorIdentityIntegrationTest`; **C** = `RealCampaignPreparationIntegrationTest`; **D** = `RealCampaignDiscoveryIntegrationTest`; **P** = `RealOwnApplicationsIntegrationTest`. Se ejecutan con Retrofit/OkHttp del móvil, backend HTTP, MySQL y Mailpit aislados. No son pruebas de interacción visual del APK.

| ID | Operación | Consumidor / recorrido verificado |
|---|---|---|
| E01 | `POST /auth/brands` | Registro empresarial → Login; A |
| E02 | `POST /auth/creators` | Registro creador → Login; A |
| E03 | `POST /auth/sessions` | JWT, rol del servidor, credenciales inválidas; A |
| E04 | `POST /auth/recovery-requests` | Solicitud genérica → correo SMTP a Mailpit; A |
| E05 | `POST /auth/password-resets` | Nueva contraseña, 204, uso único y revocación; A |
| E06 | `GET /accounts/me` | Restauración, cabecera, cuenta empresarial; A/I |
| E07 | `GET /profiles/me/creator` | Precarga y lectura persistida; I |
| E08 | `PUT /profiles/me/creator` | Guardado, validaciones y nombre actualizado; I |
| E09 | `POST /social-accounts/{platform}/authorizations` | URL oficial/identificador/PENDING, ambas redes; I |
| E10 | `GET /social-accounts/{platform}/callback` | Navegador, rechazo → 303, repetición → 400; I. Éxito/duplicado con proveedor simulado: `IdentityLifecycleApiTests` |
| E11 | `GET /social-accounts/me` | Lista real y recarga después del retorno; I. Lista con vínculo exitoso: prueba backend con proveedor simulado |
| E12 | `POST /campaigns` | Crear DRAFT, reintentar con clave estable; C |
| E13 | `PUT /campaigns/{id}/conditions` | Requisitos, entregables, fechas y compensación; C |
| E14 | `POST /campaigns/{id}/publication` | Publicación y reconciliación de éxito parcial; C |
| E15 | `GET /campaigns/mine` | Mis campañas y total empresarial; C/P |
| E16 | `GET /campaigns/published` | Oportunidades disponibles; D |
| E17 | `GET /campaigns` | Filtros combinados, total y paginación; D |
| E18 | `GET /campaigns/{id}` | Detalles propios/visibles, cierre, expiración, permisos; C/D |
| E19 | `POST /campaigns/{id}/applications` | UUID manuales, reglas automáticas, duplicado e idempotencia; P |
| E20 | `GET /applications/mine` | Listado propio, páginas y total tras cancelación; P |
| E21 | `GET /applications/{id}` | Consulta/precarga, propiedad y versión vigente; P |
| E22 | `PUT /applications/{id}` | Editar mensaje, conservar confirmaciones y controlar versión; P |
| E23 | `POST /applications/{id}/cancellation` | Cancelar PENDING, conflicto y terminal; P |
| E24 | `DELETE /campaigns/{id}` | Descartar únicamente DRAFT; C |
| E25 | `POST /campaigns/{id}/closure` | Cerrar sin cambiar postulaciones existentes; C/P |
| E26 | `GET /social-accounts/authorizations/{id}` | Resultado autenticado, propietario y retorno; I |

Los tests unitarios/contratos complementan los recorridos con respuestas fuera de orden, sesión cambiada/vencida, errores HTTP, reintentos, links maliciosos y estados UI. Las pruebas backend también verifican migraciones V5→V7, límites DDD, JWT, concurrencia y adaptadores. El test OAuth backend comprueba que el intercambio se llama **una sola vez** aun después de repetir el callback, y que la lista no expone accessToken.

## Cómo repetir la validación

Resultado final del 8 de octubre de 2026:

| Comprobación | Resultado |
|---|---|
| Móvil: `testDebugUnitTest` | 286 pruebas, cero fallos/errores/omisiones; incluye cinco recorridos HTTP contra MySQL/Mailpit |
| Backend: `mvnw.cmd test` | 144 pruebas, cero fallos/errores/omisiones; incluye correo SMTP real |
| `assembleDebug`, `assembleRelease` | Ambos APK compilados; release sin firma/destino de producción |
| `assembleDebugAndroidTest` | APK instrumentado compilado; pruebas **no ejecutadas** por ausencia de dispositivo |
| `lintDebug` / lint vital release | Sin errores; debug conserva 34 advertencias y una nota (dependencias, estilo, recursos) |
| Previews | 67 funciones; test de regresión comprueba las 57 originales |
| `git diff --check` | Sin errores de whitespace en ambos proyectos |

La vinculación exitosa/duplicada se verifica con proveedor simulado en tests backend y estados de puerto en el móvil. Las denegaciones OAuth de ambas redes, retorno 303 y rechazo de callback repetido se verifican contra el backend/MySQL real, usando credenciales sintéticas sin acceder a terceros. **Esto no certifica permiso concedido por Instagram/TikTok ni interacción visual en Android.**

Usar exclusivamente un backend de prueba aislado en loopback, con MySQL/Mailpit y credenciales sintéticas para construir URLs de autorización. No apuntar estas pruebas a una base de usuarios. Para la denegación se usa `error=access_denied`, sin intercambiar códigos con terceros.

Desde CollabPro, con ese servidor ya iniciado:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:COLLABPRO_AUTH_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_MAILPIT='http://127.0.0.1:8029'
$env:COLLABPRO_IDENTITY_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_CAMPAIGN_TEST_API='http://127.0.0.1:18081/api/v1/'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --console=plain --no-configuration-cache
```

Sin estas variables se omiten cinco integraciones opcionales. Gradle registra las variables como entradas para no reutilizar un resultado previo omitido.

Desde platform, para incluir también su test SMTP:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:SMTP_HOST='127.0.0.1'
$env:SMTP_PORT='10279'
$env:COLLABPRO_TEST_MAILPIT='true'
$env:COLLABPRO_TEST_MAILPIT_URL='http://127.0.0.1:8029'
.\mvnw.cmd test
```

Mailpit de pruebas admite únicamente URL loopback; el test identifica sus mensajes por un correo único, sin borrar mensajes ajenos.

## Validaciones externas pendientes

- **Permiso exitoso real de Instagram/TikTok:** requiere credenciales del proveedor en backend y callback HTTPS público registrado; consultar `PERFIL_CREADOR_Y_REDES_V1.md`. No se configuran secretos en Android. Ni localhost ni `10.0.2.2` sirven como callback público. No registrar el esquema `collabpro://` ante el proveedor.
- **Teléfono/AVD:** validar Custom Tabs → backend → Android, permiso concedido/rechazado/duplicado, volver sin completar, rotación/arranque frío, logout durante la autorización y recorridos de las 23 operaciones. Las pruebas instrumentadas se compilan, pero solo se ejecutan con un dispositivo conectado.
- **API operativa:** el APK debug conserva `http://10.0.2.2:8081/api/v1/` para emulador; en un celular físico recompilar con `-Pcollabpro.debugBaseUrl=http://IP_LAN:8081/api/v1/`. Release requiere URL HTTPS real y firma; el APK release generado sin configurar destino/firma no es una entrega lista para producción.

Los recursos de prueba temporales de esta validación se retiran al finalizar. No se modificó el reporte ni se borraron datos de desarrollo.
