# CollabPro Android — DDD e integración real

Aplicación Android en Kotlin y Jetpack Compose basada en el reporte. **Registro, login, recuperación, perfil del creador, redes, creación/condiciones/publicación, exploración/detalle de campañas, postulaciones propias y paneles ya consumen el backend real.** El rol viene del servidor y la sesión JWT se guarda cifrada. Instagram/TikTok requieren configurar sus proveedores en el backend; se abren en Custom Tabs y Android consulta el resultado sin repetir el callback. Mis campañas y ambos detalles usan UUID reales; la preparación se conserva cifrada y los éxitos parciales se pueden retomar. El creador consulta oportunidades, filtros combinados, paginación y disponibilidad reales. Sus postulaciones permiten enviar, consultar, editar el mensaje y cancelar con control de versión; la propuesta local se conserva en memoria durante la sesión. Los paneles muestran nombres y totales del servidor. La cuenta empresarial permite consultar nombre/tipo/estado; su formulario completo US-12 se conserva únicamente como preview. Evaluación de postulantes y demás pantallas posteriores siguen como prototipo identificado.

Las fases 2–8 están implementadas en código, sin ampliar las primeras 18 posiciones del backlog. Consulta [Vinculación social y validación final](docs/VINCULACION_SOCIAL_Y_VALIDACION_V1.md), [Postulaciones propias](docs/POSTULACIONES_PROPIAS_V1.md), [Exploración y detalle](docs/EXPLORACION_Y_DETALLE_V1.md), [Campañas, borradores y éxitos parciales](docs/CAMPANAS_Y_CONDICIONES_V1.md), [Perfil y OAuth](docs/PERFIL_CREADOR_Y_REDES_V1.md), [Autenticación real](docs/AUTENTICACION_MOVIL_V1.md), [Base móvil DDD](docs/BASE_MOVIL_DDD_V1.md) y el [plan final](docs/PLAN_IMPLEMENTACION_FINAL_V1.md). La aceptación visual en dispositivo y el permiso exitoso con cuentas reales de los proveedores requieren validación externa; no se certifican a partir de mocks.

## Recorridos y cobertura

| Historias | Pantallas |
|---|---|
| US-01–US-08 | Bienvenida, propuesta para empresas y creadores, «cómo funciona», contacto, idioma ES/EN en la presentación y elección de registro |
| US-09–US-11 | Registro de empresa, registro de creador, inicio de sesión y recuperación |
| US-12–US-14 | Perfiles de empresa y creador; cuentas sociales y estados de autorización |
| US-15–US-16 | Creación de campaña y definición de requisitos, entregables, fecha y compensación |
| US-17–US-19 | Búsqueda con filtros, resultados vacíos, detalle abierto/cerrado, postulación, edición, duplicado, requisito incumplido y cancelación |
| US-20–US-21 | Lista y detalle de postulantes; selección/rechazo; aceptación bilateral o rechazo del acuerdo |
| US-22–US-25 | Lista y estado de colaboraciones, entrega normal/tardía, validación/corrección de entregables e incidencias |
| US-26–US-28 | Medio de pago, planes, suscripción, compensación pendiente/pagada/afectada |
| US-29–US-30 | Resultados con fuente y periodo, ausencia de métricas, evidencia manual, atribución e historial lleno/vacío |

US-01 a US-08 describen originalmente una **Landing Page web**. La app incluye su recorrido informativo en formato móvil. El escenario de escritorio de US-07 corresponde a la implementación web y no se puede verificar desde un APK Android.

La tabla incluye también los escenarios del producto futuro, no solo los conectados: US-12 está únicamente en preview; US-20–US-30 permanecen como prototipos. Se respeta el orden de las primeras 18 posiciones, no las primeras 18 numeraciones de US.

## Estructura

Los paquetes `features/identity`, `features/campaign`, `features/collaboration`, `features/billing` y `features/performance` corresponden a los cinco bounded contexts del informe:

- `domain`: modelos y contratos del contexto; los reales están en `model` y `repositories`.
- `application`: casos de uso reales en `usecases`, separados de consultas del prototipo.
- `infrastructure`: adapters REST, DTOs, mapeadores, DI y persistencia segura; repositorios Preview conservados exclusivamente para la maqueta.
- `presentation`: ViewModel/UiState/Screens reales en `identity/presentation/auth`, `identity/presentation/profile`, `campaign/presentation/manage`, `campaign/presentation/discovery`, `campaign/presentation/applications` y `campaign/presentation/dashboard`; los otros recorridos permanecen de demostración.

`navigation/CollabApp.kt` separa rutas públicas, verificación de sesión, nueva contraseña y recorrido privado. El `AppState` privado se crea con la cuenta verificada; no restaura un rol o ruta privados desde preferencias de navegación. `core/designsystem` concentra componentes visuales reutilizables.

## Ejecutar

Abrir la carpeta en Android Studio y ejecutar `app`, o compilar desde la raíz con:

```powershell
.\gradlew.bat :app:assembleDebug
```

Primero inicia el backend en 8081 (8080 estaba ocupado por otros servicios): desde `C:/Users/fabio/Documents/platform`, ejecuta `docker compose up -d` y `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--server.port=8081'`. Debug usa `http://10.0.2.2:8081/api/v1/` para el emulador. Para un celular físico configura la IP del equipo como se explica en la guía de autenticación.

El tipo **Empresa/Creador** solo se elige para registrarse. El registro exitoso lleva al login; no crea una sesión automáticamente. El login exige credenciales reales, sin selector de rol ni estados simulados. Al reiniciar se verifica la sesión cifrada con `GET accounts/me`. Cerrar sesión elimina la credencial local y el historial privado. En desarrollo, los correos se capturan en Mailpit (`http://localhost:8025`), no llegan a Gmail u otros buzones externos.

## Vistas previas y emulador

El proyecto incluye funciones `@Preview` en `navigation/PreviewScreens.kt` para revisar las pantallas de bienvenida, ambos paneles, campañas, colaboración, compensación y resultados. En Android Studio abre ese archivo y pulsa **Split** o **Design**; si el panel no aparece, usa **View > Tool Windows > Preview** y luego **Build & Refresh**.

Se conservan las 57 previews anteriores y hay diez estados adicionales: 67 en total, sin backend ni ViewModels. La guía de validación final detalla la cobertura y las comprobaciones que aún necesitan dispositivo/proveedores reales.

Para simular un teléfono, abre **Tools > Device Manager > Create Device**, elige un modelo (por ejemplo, Pixel 7a) e instala una imagen de sistema recomendada, como API 35 o 36. Después inicia el AVD con el botón ▶ y ejecuta la configuración `app`. El SDK local tiene el emulador, pero al revisar este proyecto no había AVD ni imagen de sistema instalados.
