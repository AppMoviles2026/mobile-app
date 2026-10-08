# Creación y condiciones de campañas — fase 5

Implementación del 8 de octubre de 2026, dentro de **Campaign**, para US-15 y US-16 (posiciones 16 y 17 del backlog ordenado). No se implementan selección de postulantes, acuerdos, entregas, pagos ni métricas. Actualización posterior: la exploración del creador y sus postulaciones están implementadas en las fases 6 y 7, con guías independientes.

## Recorrido real

Desde Inicio de empresa → Mis campañas → Crear o continuar campaña:

1. **Información:** título, objetivo, descripción opcional, categoría libre, público objetivo y ubicación opcional. «Continuar» valida y cambia de paso; no crea una campaña remota. «Guardar borrador» permite crear solo esta información, sin condiciones completas ni publicación.
2. **Condiciones:** requisitos repetibles con obligatoriedad y regla, entregables con formato/descripción/cantidad/fecha-hora, cierre de postulaciones y compensación tipada. «Guardar condiciones» conserva la campaña en DRAFT. «Publicar» pide confirmación y solo anuncia éxito después de OPEN confirmado.
3. **Mis campañas:** consulta paginada del servidor, total real, actualización y acceso por UUID. «Retomar condiciones» abre un borrador ya creado. El detalle muestra todos los datos reales y no enlaza un UUID real a postulantes/colaboraciones ficticios.

Las operaciones conectadas son `POST /campaigns` (con Idempotency-Key), `PUT /campaigns/{id}/conditions`, `POST /campaigns/{id}/publication`, `GET /campaigns/mine`, `GET /campaigns/{id}`, `DELETE /campaigns/{id}` y `POST /campaigns/{id}/closure`. La API existente fue suficiente: no se modificaron el backend ni sus migraciones.

Los metadatos quedan en solo lectura cuando existe el UUID o hay una creación sin confirmar. Las condiciones solo se editan con estado DRAFT comprobado. No hay PATCH de metadatos, reapertura, compensación híbrida ni eliminación de campañas publicadas. El descarte de un DRAFT y cierre de postulaciones de una OPEN corresponden a la gestión limitada prevista en el plan; requieren confirmación y respuesta del servidor. El cierre conserva la campaña y sus postulaciones; el descarte confirmado elimina el borrador y no es recuperable.

## Borradores y éxitos parciales

La edición local se guarda automáticamente con un debounce de 300 ms: se muestra «Guardando edición local cifrada…» hasta completar la escritura, y los errores de almacenamiento son visibles. No debe considerarse durable el último texto mientras ese indicador esté activo. Antes de cada escritura HTTP se realiza además un checkpoint local confirmado; si falla, no se inicia la siguiente operación remota.

Se guarda una preparación local por cuenta: formularios, zona horaria, UUID confirmado, etapa pendiente y snapshot exacto de la creación con su clave de reintento. Se utiliza AES-256-GCM, IV nuevo en cada escritura y clave de Android Keystore, dentro del almacenamiento privado y excluido de backup/transferencia. No se guardan JWT ni credenciales dentro del borrador. Cerrar sesión elimina el estado en memoria y cancela trabajo; la preparación cifrada puede restaurarse al volver a verificar **la misma cuenta**, sin mostrarse a otras cuentas. Los borradores remotos pueden recuperarse desde Mis campañas aunque se pierda el almacenamiento local.

«Comenzar otra campaña» pide confirmación y reemplaza únicamente la preparación local; no borra una campaña remota. Una creación sin confirmar no se puede reemplazar silenciosamente. Si se daña el almacenamiento o se pierde la clave, se informa el error y se ofrece restablecer la preparación local, conservando las campañas existentes en el servidor.

| Resultado | Comportamiento |
|---|---|
| Se pierde la respuesta de creación | Conserva el mismo body y la misma Idempotency-Key. Reintentar obtiene el UUID sin crear un duplicado dentro de la garantía del servidor |
| Creación confirmada, condiciones fallan | Conserva UUID y formularios; se corrigen o reintentan las condiciones sin volver a crear |
| PUT confirmado en servidor, respuesta perdida | GET comprueba el contenido; no repite PUT si las condiciones ya coinciden |
| Condiciones guardadas, publicación falla | Conserva el borrador; no lo etiqueta como publicado |
| Publicación confirmada en servidor, respuesta perdida | GET reconoce OPEN; no crea otra campaña ni vuelve a publicar |
| Éxito remoto seguido de error local | Muestra el estado remoto confirmado junto al error de almacenamiento; no revierte un éxito del servidor |

La confirmación compara requisitos/entregables por contenido y multiplicidad, **sin depender del orden ni de los UUID nuevos**: el backend los devuelve ordenados por sus identificadores. Un replay de creación puede contener una respuesta DRAFT antigua, por lo que se consulta GET antes de seguir escribiendo.

La protección de creación del backend dura 24 horas. Una creación todavía sin UUID que supere ese plazo **no se reenvía automáticamente**: se indica revisar Mis campañas y seleccionar el recurso correspondiente antes de generar otro intento. No se promete deduplicación permanente con una clave vencida.

«Comprobar estado» conserva la edición local; «Recargar condiciones» pide confirmación y reemplaza esa edición por la respuesta del servidor. Un fallo de lista no se convierte en «sin campañas», y un 404 no selecciona una campaña de muestra.

## Validación y seguridad

- Metadatos: título 200, objetivo/audiencia 2000, descripción 5000, categoría 100 y ubicación 150 caracteres, con obligatoriedad conforme al contrato.
- Entre 1 y 50 requisitos y entregables; cantidad de 1 a 1000. Reglas explícitas: confirmación manual, nicho, ubicación o Instagram/TikTok autorizados. La regla manual no envía valor automático.
- Fechas con zona horaria indicada, formato `aaaa-mm-dd hh:mm` (también segundos cuando existen en el recurso). El cierre debe ser futuro y cada entrega estrictamente posterior; se rechazan fechas imposibles y horas ambiguas/inexistentes por cambio horario. El backend vuelve a validar al guardar y publicar.
- CASH requiere monto positivo (máximo 10 enteros/2 decimales) y moneda ISO reconocida. PRODUCT, SERVICE, CREDIT y BARTER envían descripción, sin monto ni moneda. Esta oferta no inicia cobros.
- Errores por campo del cliente/servidor, validaciones 422, conflictos, permisos, red, timeout y almacenamiento conservan la entrada y no fabrican éxitos.
- La empresa debe tener una sesión verificada. Cada trabajo de Campaign queda ligado al AccountId y vencimiento que lo iniciaron mediante un contexto `ExpectedAccount`, sin JWT en UI. El transporte rechaza adquirir credenciales de otra sesión al ejecutar una operación encolada; el interceptor existente sigue controlando cambios posteriores. Estado de pantalla filtrado por propietario, UUID/BrandId validados y respuestas tardías descartadas.

## Capas DDD y previews

Application contiene el modelo de preparación local, puerto `CampaignDraftStore`, validación y coordinador `CampaignPreparation` (crear → condiciones → publicar con checkpoints). Domain conserva los modelos de campaña y puerto REST independiente del transporte. Infrastructure implementa el almacenamiento cifrado y reutiliza Retrofit/DTOs/mapeadores existentes. Presentation contiene `BrandCampaignViewModel`, UiState y composables puros. No hay dependencias de Android/Retrofit en Domain/Application.

Se actualizaron las previews de información, condiciones y Mis campañas, y se añadieron error de lista y publicación incierta. Los fixtures son exclusivos de previews/tests. Se retiraron los formularios antiguos de publicación simulada del recorrido conectado.

## Ejecutar y verificar

Resultado: **176 tests JVM aprobados, cero fallos y cero omisiones** (173 unitarios/contratos y tres integraciones activadas). Compilación debug/release y APK instrumentado correctos; `lintDebug` sin errores. Las pruebas instrumentadas no se ejecutaron por ausencia de dispositivo.

La app debug conserva `http://10.0.2.2:8081/api/v1/` para el emulador. La configuración de backend/teléfono se encuentra en `AUTENTICACION_MOVIL_V1.md`. El servidor temporal en 18081 es exclusivamente de pruebas, no la dirección permanente del APK.

Pruebas de esta fase cubren validación, almacenamiento cifrado y aislamiento, creación idempotente, respuesta perdida de cada etapa, checkpoint fallido, orden de hijos, recuperación tras vencimiento del plazo de postulación, bloqueo de metadatos, lectura/paginación, descarte/cierre y cambio de cuenta. La integración contra el backend real y MySQL inyecta pérdida de respuesta **después de confirmar las operaciones HTTP reales**, comprueba que no se duplica el recurso/PUT/publicación, visibilidad para el creador, restricciones de rol/propietario, borrador incompleto bloqueado, fechas incompatibles y cierre repetible. También se repiten las regresiones de autenticación y OAuth rechazado con Mailpit.

Para repetir las tres integraciones, usar un backend y MySQL/Mailpit **aislados en loopback**, no una base de usuarios reales. La integración OAuth previa requiere las credenciales sintéticas indicadas en su guía, sin contacto con los proveedores reales:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:COLLABPRO_CAMPAIGN_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_IDENTITY_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_API='http://127.0.0.1:18081/api/v1/'
$env:COLLABPRO_AUTH_TEST_MAILPIT='http://127.0.0.1:8029'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug --console=plain --no-configuration-cache
```

Sin variables de integración se omiten únicamente los tests opt-in correspondientes. Los procesos/contenedores y datos temporales se retiran al finalizar; SmartQuote y la base de desarrollo permanecen intactos. La instalación, interfaz y Android Keystore todavía deben comprobarse en teléfono/AVD: no hay un dispositivo conectado, y las pruebas instrumentadas se compilan pero no se ejecutan en esta entrega.

Las fases 6 y 7 de exploración/detalle y postulaciones propias ya están implementadas; consulta `EXPLORACION_Y_DETALLE_V1.md` y `POSTULACIONES_PROPIAS_V1.md`. Siguiente fase: limpieza general y aceptación completa, sin implementar historias posteriores del backlog.
