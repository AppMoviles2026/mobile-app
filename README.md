# CollabPro Android — prototipo y base DDD

Aplicación Android en Kotlin y Jetpack Compose basada en las historias de usuario US-01 a US-30 de `C:\Users\fabio\Documents\report\README.md`. Contiene rutas navegables para empresas y creadores, formularios visuales y estados de ejemplo. No realiza autenticación, cobros, cargas de archivos, vinculación OAuth ni llamadas a una API. Los botones que representan esos procesos muestran estados locales de demostración.

La **fase 2 de integración** está implementada: contratos REST, modelos reales, repositorios, casos de uso, Hilt, errores tipados y sesión cifrada. Las pantallas aún no utilizan esa infraestructura; no confundir el login de demostración con autenticación real. La estructura, contratos y configuración por entorno están en [Base móvil DDD V1](docs/BASE_MOVIL_DDD_V1.md). La siguiente fase conecta registro, login y recuperación, conforme al [plan final](docs/PLAN_IMPLEMENTACION_FINAL_V1.md).

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

## Estructura

Los paquetes `features/identity`, `features/campaign`, `features/collaboration`, `features/billing` y `features/performance` corresponden a los cinco bounded contexts del informe:

- `domain`: modelos y contratos del contexto; los reales están en `model` y `repositories`.
- `application`: casos de uso reales en `usecases`, separados de consultas del prototipo.
- `infrastructure`: adapters REST, DTOs, mapeadores, DI y persistencia segura; repositorios Preview conservados exclusivamente para la maqueta.
- `presentation`: pantallas Compose y estados locales de demostración.

`navigation/AppState.kt` mantiene el rol, la ruta, la campaña elegida y el historial de navegación durante la sesión. `navigation/CollabApp.kt` conecta todas las pantallas. `core/designsystem` concentra componentes visuales reutilizables.

## Ejecutar

Abrir la carpeta en Android Studio y ejecutar `app`, o compilar desde la raíz con:

```powershell
.\gradlew.bat :app:assembleDebug
```

En el inicio se puede escoger **Empresa** o **Creador**. El formulario de inicio de sesión acepta cualquier correo y contraseña no vacíos para recorrer la maqueta. Los datos y cambios de pantalla no se conservan al reiniciar la aplicación.

## Vistas previas y emulador

El proyecto incluye funciones `@Preview` en `navigation/PreviewScreens.kt` para revisar las pantallas de bienvenida, ambos paneles, campañas, colaboración, compensación y resultados. En Android Studio abre ese archivo y pulsa **Split** o **Design**; si el panel no aparece, usa **View > Tool Windows > Preview** y luego **Build & Refresh**.

Para simular un teléfono, abre **Tools > Device Manager > Create Device**, elige un modelo (por ejemplo, Pixel 7a) e instala una imagen de sistema recomendada, como API 35 o 36. Después inicia el AVD con el botón ▶ y ejecuta la configuración `app`. El SDK local tiene el emulador, pero al revisar este proyecto no había AVD ni imagen de sistema instalados.
