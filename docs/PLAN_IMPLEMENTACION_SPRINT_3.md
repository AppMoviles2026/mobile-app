# Sprint 3 · Plan de implementación de CollabPro

24 de noviembre de 2026 · Alcance propuesto para la app Android en Kotlin y Jetpack Compose, sobre el backend existente.

El objetivo es cerrar el recorrido de ambas partes desde la evaluación de una postulación hasta la entrega revisada, la resolución de incidencias, la compensación, los resultados y el historial. Los nueve commits propuestos son del repositorio Android. El backend local ya ofrece los contratos necesarios para comenzar. Si el recorrido descubre un defecto del servidor, aislarlo en `platform` con su reproducción y corrección, sin mezclarlo con un commit de Android.

## Selección del sprint

Se cruzaron el Product Backlog (§2.4.3, líneas 4120–4160), las tablas completas de Sprint Backlog 1 (línea 6514) y Sprint Backlog 2 (línea 7500, después de una sección de Sprint 2 que solo contiene encabezados), las historias de §2.4.1 y los controladores REST actuales. US-09 aparece en ambos sprints y corresponde al registro ya conectado. Los rótulos `Done` del reporte y el inventario del código local se registran por separado.

| Historia nueva del Sprint 3 | Contexto | Puntos del reporte | Entrega esperada |
| --- | --- | ---: | --- |
| US-24 · Validación de entregables | Collaboration | 5 | Aprobar, rechazar con observaciones y permitir que el creador envíe una nueva versión conforme al plazo. |
| US-25 · Gestión de incidencias | Collaboration | 5 | Abrir, consultar y resolver incidencias conservando las decisiones y el historial. |
| US-26 · Vinculación de medio de pago | Billing | 3 | Vinculación Sandbox de los dos roles, error de validación y bloqueo de operaciones sin medio válido. |
| US-27 · Suscripción de la empresa | Billing | 5 | Consultar catálogo real y confirmar el estado de la suscripción según el cobro. |
| US-28 · Consulta del estado de la compensación | Billing | 3 | Consultar pendiente, pagada y afectada por incidencia. Conectar el inicio y confirmación empresariales para poder recorrer el escenario de pago. |
| US-29 · Consulta y atribución de resultados | Performance | 3 | Mostrar métricas con fuente/periodo, ausencia, evidencia del creador, atribución y advertencia de estimación. |
| US-30 · Historial de colaboraciones | Collaboration | 3 | Listado, detalle y cronología para ambas partes, incluido historial vacío. |

**Total nuevo: 7 US, 27 puntos del Product Backlog.** Los puntos no estiman por sí solos la carga de integración. TS-06 y TS-07 son soporte técnico y SS-01/SS-03 son verificaciones de proveedor ya referenciadas por el reporte. TS-05 ya figura en Sprint 2 y sus servicios habilitan US-24/25/28. US-01–US-08 corresponden a la landing. El backlog del reporte termina en US-30.

**Arrastre móvil de Sprint 2:** US-12, US-20, US-21, US-22 y US-23. La tabla de Sprint 2 marca sus integraciones como `Done`, pero las rutas y adaptadores del proyecto local muestran el trabajo detallado abajo. Se planifican como dependencias de Android sin volver a contarlas como US nuevas. US-09 requiere regresión del registro y sesión de empresa. El reporte se conserva como está.

## Punto de partida comprobado antes de los bloques

| Contexto | Backend local | Android local |
| --- | --- | --- |
| Identity | Registro, JWT, recuperación, perfiles de ambos roles y OAuth. `GET/PUT /profiles/me/brand` con versión; estado de credenciales sociales disponible. | `IdentityApi` conecta registro/login/recuperación y perfil del creador. `MainActivity` recibe recuperación y retorno social. La ruta empresarial usa `BrandAccountScreen` con datos básicos de cuenta; falta edición y consulta de perfil completas. |
| Campaign | Campañas y condiciones versionadas, postulaciones propias y API de evaluación empresarial. | `CampaignApi` conecta creación, condiciones, publicación, búsqueda, detalle, cierre/descarte y postulaciones propias. La edición de campaña/condiciones carece de `version`/`expectedVersion` en los DTO actuales. `ApplicantsScreen` y `ApplicantDetailScreen` usan muestras y selección local. |
| Collaboration | Acuerdos bilaterales, activación, estado y obligaciones, entregas/versiones, archivos privados, incidencias y cronología. | `CollabApp` direcciona a pantallas de `CollaborationScreens.kt` con estados locales y datos de `PreviewCollaborations`. Sin API/repository remoto del contexto ni navegación parametrizada por los UUID de esos recursos. |
| Billing | Vinculación, suscripciones, compensaciones, callbacks y reconciliación PayPal Sandbox. | `BillingScreens.kt` cambia estados de muestra y el prototipo de vinculación incluye campos de tarjeta. Los tres retornos financieros aún no están en el manifiesto ni en el manejador de intents. |
| Performance | Consultas de métricas y capturas, evidencia manual y su revisión, atribución LINK/CODE con registro e interpretación. | `PerformanceScreens.kt` tiene resultados e historial de muestra. No hay API remota para Performance. El historial funcional corresponde a Collaboration. |

Referencias de código: `app/src/main/java/com/example/collabpro/navigation/{CollabApp,AppState}.kt`, `features/*/infrastructure`, `MainActivity.kt`, `app/src/main/AndroidManifest.xml` y los controladores de `C:/Users/fabio/Documents/platform/src/main/java/com/collabtech/platform`. Los contratos del servidor están en `platform/docs/*-contracts.md`; Flyway llega a V17. La validación anterior registrada en `platform/docs/backend-validation-and-mobile-contracts.md` es evidencia histórica del backend, con proveedor controlado en los recorridos automatizados.

**Línea base Android:** Gradle completó `:app:testDebugUnitTest :app:assembleDebug` con JDK 21. De 286 casos declarados, 281 pasaron y cinco suites de integración con API externa quedaron omitidas sin sus variables de entorno. La primera ejecución encontró un `JAVA_HOME` que apuntaba a un JBR inexistente; se usó el JDK instalado solo para esa ejecución. Esto comprueba compilación y suite local; emulador, VM y proveedores se validan en los recorridos del sprint.

## Criterios comunes a los commits

- Conservar la estructura DDD por contexto. Modelos y contratos en `domain`, casos de uso en `application`, Retrofit/DTO/mapeo/Hilt en `infrastructure` y ViewModels/UiState/Compose en `presentation`. Los cruces de contexto usan UUID, rutas y contratos publicados. Reutilizar `ApiExecutor`, sesión cifrada y el sistema visual.
- **El JWT de Android depende de una lista de rutas.** `ApiConfiguration.isProtected` no reconoce el perfil empresarial ni las familias nuevas de Collaboration/Billing/Performance. Ampliarla en cada bloque que añade rutas y comprobar el envío del bearer para la cuenta activa. Mantener el control de origen y las exclusiones de callbacks y enlaces públicos; las descargas privadas también deben autenticar.
- Pasar IDs reales, incluidos los distintos IDs de cuenta, perfil, acuerdo, colaboración, pieza y versión de entrega. Conservar el ID seleccionado al volver y validar cada consulta con la sesión activa. Limpiar estado privado al cambiar de cuenta. Retirar el estado de muestra de cada ruta al conectarla, y conservar las previews de Compose con fixtures independientes.
- Renderizar carga, contenido, vacío, error y reintento. En un conflicto de versión, volver a consultar y dejar revisar los cambios locales antes de confirmar. Ante timeout, conservar el identificador de operación y verificar el resultado antes de repetir. Reutilizar `requestId`, `eventId` o `Idempotency-Key` según el contrato, y `termsVersion` o `expectedVersion` solo donde correspondan. Las acciones y estados de negocio se derivan del servidor.
- Los contratos de los bloques se citan sin el prefijo `/api/v1`. Los comandos Git son sugerencias para ejecutar manualmente **desde el repositorio Android y después de preparar solo los archivos del bloque**. No se ejecutaron al elaborar este plan.

## Orden de implementación y commits

| Orden | Contexto | Alcance | Dependencia principal |
| ---: | --- | --- | --- |
| 1 | Identity | US-12 integrada. Regresión de identidad y autenticación de las rutas de perfil empresarial. | Sesión existente |
| 2 | Campaign | US-20 integrada y edición de borradores/condiciones con versión | Campaign existente |
| 3 | Collaboration | US-21 y US-22 pendientes | 2 |
| 4 | Collaboration | US-23 pendiente + US-24 nueva | 3 |
| 5 | Collaboration | US-25 y US-30 nuevas | 3 y 4 |
| 6 | Billing | US-26 nueva y retorno financiero reutilizable | Sesión existente |
| 7 | Billing | US-27 nueva | 6 |
| 8 | Billing | US-28 nueva | 4, 5 y 6. La suscripción no es un requisito del pago. |
| 9 | Performance | US-29 nueva | 3 y 4. Enlace histórico del bloque 5. |

Los bloques 6–7 pueden avanzar en paralelo con Collaboration. Ejecutar los recorridos integrados al cerrar los bloques que los completan, sin reservar un commit para tareas de verificación aisladas.

### 1. Identity · Perfil empresarial

**Estado (24 de noviembre de 2026):** integrado en la app. Inicio → Perfil empresarial y la pestaña Perfil abren el formulario autenticado. Si la versión cambió durante la edición, el formulario conserva el borrador y compara con la versión actual antes de permitir guardar. Tras guardar, el nombre del panel se actualiza desde `accounts/me`.

**Verificación:** Gradle compiló ambos APK de debug y pasó 292 pruebas, incluyendo registro, perfil empresarial, sesión, recuperación con Mailpit y retorno OAuth por cancelación con el backend local y MySQL aislados. En el emulador se comprobó inicio de sesión, perfil precargado, edición, guardado, nombre nuevo en Inicio y persistencia de la versión. Las pruebas instrumentadas compilaron, pero Espresso 3.5.1 falló al inicializar la inyección de eventos en la imagen Android 17 disponible.

- **US-12:** conectar `GET/PUT /profiles/me/brand` desde el menú de perfil empresarial. Precargar nombre, descripción, categoría y ubicación. Usar el perfil creado al registrarse, enviar todo el formulario editable con `expectedVersion` y actualizar la cuenta mostrada en el panel cuando cambie el nombre.
- **Regresión US-09/11/14:** reutilizar sesión y rol del servidor. Verificar que registro de empresa, login, recuperación y retorno OAuth del creador conservan su recorrido. Añadir autenticación para la ruta empresarial.
- **Aceptación:** una empresa con campos opcionales vacíos puede completarlos y editarlos. Un 409 conserva el formulario para revisión. El creador no puede consultar ni modificar ese recurso.
- Contrato `platform/docs/identity-contracts.md`.

```bash
git commit -m "feat(identity): connect versioned brand profile on Android"
```

### 2. Campaign · Evaluación empresarial

**Estado (24 de noviembre de 2026):** integrado. Desde Mis campañas → detalle → Ver postulaciones, la empresa consulta, filtra, pagina, selecciona o rechaza con motivo. Mis postulaciones muestra al creador el resultado del servidor. El detalle conserva el UUID de la postulación para la propuesta de acuerdo del bloque 3.

La edición guarda la versión del borrador junto con sus checkpoints locales. Al actualizar metadatos usa PATCH, y las condiciones usan la versión que dejó la última escritura confirmada. Un conflicto conserva la edición local y permite consultar o cargar el borrador vigente de forma expresa. Los borradores locales antiguos sin versión se deben cotejar con el servidor antes de aplicar cambios.

**Verificación:** la integración con MySQL aislado cubrió PATCH y control de versión, listado/detalle de postulantes, prohibición para el creador en las rutas de evaluación, selección y rechazo posterior al cierre de postulaciones, y motivo visible en la consulta propia del creador.

- **US-20:** enlazar desde Mis campañas y su detalle, con UUID de campaña. Consumir listado y detalle `GET /campaigns/{id}/applications[/{applicationId}]` y `POST .../{applicationId}/decision`. Mostrar perfil, propuesta, requisitos y estado. Seleccionar o rechazar una postulación pendiente, con motivo al rechazar y versión actual. Refrescar listado y detalle. Mostrar resultado y motivo cuando estén disponibles en Mis postulaciones del creador.
- **Compatibilidad US-15/16/19:** transportar `version` de campaña y `expectedVersion` al guardar condiciones. Usar `PATCH /campaigns/{id}` para editar metadatos si un borrador ya existe, conservando la preparación y el manejo de éxitos parciales. Mantener la edición/cancelación propia concurrente con la decisión empresarial.
- **Aceptación:** la marca ve las postulaciones de su campaña, decide una pendiente y el creador ve el resultado. Una decisión obsoleta presenta conflicto y actualiza los datos. La selección entrega el UUID al paso de propuesta de acuerdo. La colaboración se activa con las firmas del bloque 3.
- Contrato `platform/docs/campaign-contracts.md`.

```bash
git commit -m "feat(campaign): integrate applicant evaluation and campaign versions"
```

### 3. Collaboration · Acuerdo y seguimiento

- **US-21:** crear o recuperar propuesta con `POST /agreements` para la postulación seleccionada, mostrar acuerdos propios y detalle. Mostrar snapshot, vigencia y firma de cada lado. Enviar `ACCEPT` o `REJECT` para el `termsVersion` leído. Abrir el `collaborationId` devuelto tras la segunda firma y recuperar su detalle mediante el contrato existente.
- **US-22:** consultar `/collaborations/mine` y `/collaborations/{id}`; representar estado, obligaciones, cantidades, avance, fechas, vencimientos y acciones pendientes. Añadir navegación desde acuerdo y lista a un detalle parametrizado, con actualización al volver de una acción.
- **Aceptación:** aprobación bilateral desde dos cuentas produce una sola colaboración, con las condiciones firmadas. Rechazo o vencimiento impiden activación. Ambas partes ven estado activo o vencido según el servidor y sus tareas correspondientes.
- Contrato `platform/docs/collaboration-contracts.md`.

```bash
git commit -m "feat(collaboration): connect bilateral agreements and lifecycle screens"
```

### 4. Collaboration · Entregas y revisión

- **US-23:** conectar piezas por obligación, listado `/collaborations/{id}/deliverables`, detalle y subida de archivo a `/collaborations/{id}/assets`. Usar selector de documentos y metadatos/descarga autenticados de `/collaboration-assets/{id}`. Enviar evidencia y enlace o archivo según contrato. Conservar formulario y assetId si la carga funciona y el registro falla. Mostrar tardanza del servidor y cantidad de piezas pendientes.
- **US-24:** empresa aprueba o rechaza la versión pendiente en `/deliverables/{id}/validation` o `/rejection`. Enviar `submissionId`, versión de pieza y clave. Rechazo requiere observaciones y nuevo plazo futuro. Creador consulta la revisión y corrige con `POST /deliverables/{id}/versions`; mostrar versiones y decisiones anteriores. Actualizar avance de la colaboración tras la revisión.
- **Aceptación:** entrega puntual o tardía queda pendiente; la marca descarga su evidencia y aprueba o registra un rechazo; la corrección dentro de plazo genera otra versión pendiente y conserva la anterior. Revisiones obsoletas y archivos ajenos no se aplican.
- Contrato `platform/docs/delivery-contracts.md`.

```bash
git commit -m "feat(collaboration): integrate deliverables reviews and corrections"
```

### 5. Collaboration · Incidencias e historial

- **US-25:** incluir listado/detalle, formulario y evidencia privada con `/collaborations/{id}/incidents`, `/incidents/{id}` e `incident-assets`. Renderizar acciones permitidas por rol y estado. La ventana de apertura del servidor incluye siete días tras finalización/cancelación. Conectar propuestas y decisiones sobre la propuesta exacta, incluidas aceptación de contraparte, rechazo con motivo y retiro del proponente. Mostrar los resultados de conservar acuerdo, ampliar una corrección o cancelar cuando proceda. Mantener el bloqueo mientras corresponda, incluso con varias incidencias.
- **US-30:** mover el historial funcional desde el prototipo de Performance. Consumir `/collaborations/history`, su detalle y `/collaborations/{id}/history`. Presentar participaciones paginadas, vacío por cuenta, snapshot histórico y cronología. Mostrar accesos a recursos asociados según referencias autorizadas y disponibilidad de los bloques que los conecten.
- **Aceptación:** ambos roles pueden consultar su historial y abrir incidencias válidas. La contraparte confirma resolución y la cronología conserva el registro. Puede haber historial vacío y referencias ausentes legítimas.
- Contrato `platform/docs/incident-history-contracts.md`.

```bash
git commit -m "feat(collaboration): connect incidents and collaboration history"
```

### 6. Billing · Vinculación de medio financiero

- **US-26:** reemplazar la captura local de tarjeta por PayPal Sandbox. Cargar y validar métodos de `/user/{id}/billing` y `/validation` con ID de cuenta. Iniciar con `requestId` y `client=ANDROID`, abrir la aprobación del proveedor en Custom Tabs, recuperar/cancelar intentos en `/billing/authorizations/{id}`. Marca usa PAYMENT y creador PAYOUT.
- Registrar `collabpro://billing-return` en Android. Correlacionar el UUID de autorización con cuenta e intento, manejar recreación/retorno repetido y consultar el estado protegido. Reutilizar este patrón en los siguientes dos bloques. La consulta móvil se realiza sobre el backend; el callback lo procesa el servidor.
- **Aceptación:** aprobación, error, retorno vencido/cancelado y ausencia de método muestran estados distintos. Un medio ajeno, revocado o de propósito incorrecto no habilita la operación.
- Contrato `platform/docs/billing-linking-contracts.md`.

```bash
git commit -m "feat(billing): integrate verified PayPal Sandbox linking"
```

### 7. Billing · Suscripción de empresa

- **US-27:** consultar `/subscription-plans`, suscripción actual e historial de `/user/{id}`. Usar planes, precios, moneda y disponibilidad del servidor. Crear con método PAYMENT propio y clave persistida. Abrir el consentimiento y gestionar `collabpro://subscription-return`. Consultar detalle, actualizar, reanudar aprobación con versión y cancelar según el estado.
- **Aceptación:** un intento creado o aprobado en navegador se muestra pendiente hasta confirmar el cobro. Mostrar `benefitsActive` y `paidThrough` según API. Cobro fallido o medio ausente conserva beneficios inactivos. Reintentos no duplican la suscripción.
- Contrato `platform/docs/subscription-contracts.md`.

```bash
git commit -m "feat(billing): connect company subscriptions to the Sandbox API"
```

### 8. Billing · Compensación

- **US-28:** desde el detalle de colaboración y su historial consultar `GET /collaborations/{id}/compensation` (o por ID financiero autorizado), consultar auditoría y recuperar estado mediante `payment/refresh`. Mostrar condiciones firmadas, validaciones pendientes, bloqueo por incidencia, canje, moneda no admitida, ingreso recibido, envío pendiente/fallido, pago confirmado y reversión según respuesta.
- Habilitar para empresa las acciones permitidas por el servidor. Enviar inicio, abrir aprobación y manejar `collabpro://compensation-return`. Refrescar con JWT y pedir confirmación expresa a la empresa mediante `payment/confirmation`. Conservar la misma clave ante resultado incierto. Si se permite reintentar un envío terminal, conservar el ingreso ya capturado. El creador consulta el estado y puede acceder a incidencias según permisos.
- **Aceptación:** PEN sigue en el acuerdo sin conversión y queda bloqueado para pago. El canje jamás inicia un cargo. La demo USD distingue `fundedAt` de `paidAt`; solo el envío confirmado al creador se muestra pagado. Una incidencia abierta bloquea nuevos pasos cuando corresponda y una posterior no borra un pago confirmado.
- Contrato `platform/docs/compensation-contracts.md`.

```bash
git commit -m "feat(billing): connect compensation funding and payout status"
```

### 9. Performance · Resultados, evidencia y atribución

- **US-29, escenarios 1–2:** datos del reporte bajo `/collaborations/{id}/metrics`, actualización autorizada e historial de capturas. Mostrar fuente, periodo, fecha, permisos y estados vacío, no disponible y desactualizado. Obtener `submissionId` de la versión de entrega y los demás identificadores desde datos reales de cuentas/publicaciones. El creador puede elegir redes vinculadas propias. Para la marca, usar solo los datos autorizados disponibles por postulación asociada u otra representación publicada para participantes. Si falta un identificador legítimo para refrescar, mantener la lectura y el flujo manual, y verificar la brecha de contrato. TikTok necesita `video.list` y video autorizado. Instagram queda en evidencia manual.
- **US-29, escenario 3:** conectar creación, listado, detalle, archivos privados y revisión empresarial de evidencia en las rutas de Performance. Distinguir evidencia declarada de capturas automáticas, con periodo, observaciones y estado.
- **US-29, escenarios 4–5:** consumir destinos aprobados y mecanismos LINK/CODE, compartir el enlace real, consultar interacciones y registrar declaraciones de empresa con evidencia. Mantener `eventId` del mismo hecho. Mostrar periodo y fuente, separar muestras demo y mostrar la advertencia de estimación. Abrir el enlace público sin JWT.
- **Aceptación:** ambos roles autorizados acceden a su vista correspondiente. Verificar ausencia de métricas sin contadores inventados, evidencia revisada, clic/uso de código registrado y resultado estimado con aviso. El historial abre resultados y compensación solo según referencias/accesos del servidor.
- Contratos `platform/docs/performance-contracts.md` y `platform/docs/attribution-contracts.md`.

```bash
git commit -m "feat(performance): integrate metrics evidence and attribution"
```

## Verificación para cerrar el sprint

1. **Por bloque** ejecutar `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` en Android con el JDK y SDK del proyecto. Cubrir regresiones concretas de serialización, autorización de las nuevas rutas, versiones, retorno duplicado, idempotencia y cambio de sesión. Verificar los nuevos flujos de UI con las pruebas y previews existentes o ampliar los casos que no tengan cobertura.
2. **Recorrido con dos cuentas** usar backend local con MySQL y seed o datos dedicados. Desde ambas sesiones: crear campaña/postulación → decisión → firmas → entrega → rechazo/corrección/aprobación → incidencia y resolución → historial y resultados. Verificar también cuenta sin datos, sesión vencida, 403, 409 y pérdida de red.
3. **Billing Sandbox** probar vinculación con cuentas de prueba, retorno móvil, suscripción y compensación USD por separado. Usar el seed «Demo PayPal Sandbox USD» y probar también PEN y canje. Distinguir pruebas con proveedor controlado, reconciliación por consulta y pruebas reales del Sandbox. Un webhook público firmado requiere URL HTTPS e ID configurado. Con ID vacío se puede verificar la consulta/polling y dejar anotada la omisión de entrega de webhook.
4. **Dispositivo/VM** contrastar versión de los endpoints en Swagger con el contrato local. Debug usa `collabpro.debugBaseUrl` (por defecto emulador `10.0.2.2:8081`); release exige HTTPS mediante `collabpro.releaseBaseUrl`. Verificar carga, archivos, vuelta desde Custom Tabs y consulta de estados en el dispositivo. La validación de la VM y el consentimiento externo se registran según su resultado efectivo.
5. **Cierre del alcance** comprobar que las siete US del Sprint 3 y los cinco arrastres tienen evidencia del flujo que les corresponde. Actualizar el avance del plan y registrar fallos de despliegue o permisos externos por separado. La inclusión de un endpoint en Swagger no acredita que Android lo use.
