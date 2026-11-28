package com.example.collabpro.core.infrastructure.network

import com.example.collabpro.core.application.security.*
import com.example.collabpro.core.domain.*
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Clock
import kotlin.coroutines.coroutineContext

internal data class ApiErrorDto(val code: String?, val message: String?, val fieldErrors: Map<String, String>?)
class InvalidApiResponse(message: String) : IllegalArgumentException(message)

class ApiExecutor(
    private val gson: Gson,
    private val sessions: SessionAccess,
    private val clock: Clock,
    private val configuration: ApiConfiguration
) {
    suspend fun <D, T> publicCall(call: suspend () -> Response<D>, map: (D) -> T): ApiResult<T> =
        execute(null, call) { response -> map(response.body() ?: throw InvalidApiResponse("Missing response body")) }

    suspend fun <D, T> protectedCall(call: suspend (SessionCredentials) -> Response<D>, map: (D) -> T): ApiResult<T> = withContext(Dispatchers.IO) {
        val credentials = activeCredentials() ?: return@withContext unauthorized()
        if (!expectedActor(credentials)) return@withContext failure(FailureKind.SESSION_CHANGED, "La cuenta que inició la operación cambió; no se envió la solicitud.")
        execute(credentials, { call(credentials) }) { response ->
            map(response.body() ?: throw InvalidApiResponse("Missing response body"))
        }
    }

    suspend fun publicUnit(call: suspend () -> Response<Unit>): ApiResult<Unit> = execute(null, call) { Unit }
    suspend fun protectedUnit(call: suspend (SessionCredentials) -> Response<Unit>): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val credentials = activeCredentials() ?: return@withContext unauthorized()
        if (!expectedActor(credentials)) return@withContext failure(FailureKind.SESSION_CHANGED, "La cuenta que inició la operación cambió; no se envió la solicitud.")
        execute(credentials, { call(credentials) }) { Unit }
    }

    private fun activeCredentials(): SessionCredentials? = sessions.credentials()?.takeIf {
        if (it.expiresAt.isAfter(clock.instant())) true else {
            sessions.invalidateIfCurrent(it)
            false
        }
    }
    private suspend fun expectedActor(credentials: SessionCredentials): Boolean {
        val expected = coroutineContext[ExpectedAccount] ?: return true
        return expected.accountId == credentials.accountId && expected.expiresAt == credentials.expiresAt
    }

    private fun unauthorized() = ApiResult.Failure(ApiFailure(FailureKind.UNAUTHORIZED, message = "Inicia sesión para continuar."))

    private suspend fun <D, T> execute(
        credentials: SessionCredentials?, call: suspend () -> Response<D>, map: (Response<D>) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (!configuration.isConfigured) return@withContext failure(FailureKind.CONFIGURATION, "El servicio no está disponible en este momento.")
        try {
            val response = call()
            if (credentials != null && !sessions.isCurrent(credentials)) {
                response.errorBody()?.close()
                (response.body() as? java.io.Closeable)?.close()
                return@withContext failure(FailureKind.SESSION_CHANGED, "La sesión cambió; se descartó la respuesta anterior.")
            }
            if (response.isSuccessful) ApiResult.Success(map(response)) else {
                if (response.code() == 401 && credentials != null) sessions.invalidateIfCurrent(credentials)
                ApiResult.Failure(httpFailure(response))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SessionChangedException) {
            failure(FailureKind.SESSION_CHANGED, "La sesión cambió antes de completar la solicitud.")
        } catch (_: UntrustedRequestException) {
            failure(FailureKind.CONFIGURATION, "No se pudo conectar con CollabPro.")
        } catch (_: SocketTimeoutException) {
            failure(FailureKind.TIMEOUT, "La conexión tardó demasiado. Comprueba si se completó antes de volver a intentarlo.")
        } catch (_: IOException) {
            failure(FailureKind.NETWORK, "No se pudo conectar con CollabPro. Revisa tu conexión.")
        } catch (_: JsonParseException) {
            failure(FailureKind.MALFORMED_RESPONSE, "No pudimos leer la información. Inténtalo de nuevo.")
        } catch (_: IllegalArgumentException) {
            failure(FailureKind.MALFORMED_RESPONSE, "No pudimos leer la información. Inténtalo de nuevo.")
        } catch (_: NullPointerException) {
            // A null element in a Gson collection must not become an empty list or crash a screen.
            failure(FailureKind.MALFORMED_RESPONSE, "No pudimos leer la información. Inténtalo de nuevo.")
        }
    }

    private fun httpFailure(response: Response<*>): ApiFailure {
        val body = response.errorBody()?.use { error ->
            try { gson.fromJson(error.string(), ApiErrorDto::class.java) } catch (_: Exception) { null }
        }
        val kind = when (response.code()) {
            400 -> FailureKind.VALIDATION
            401 -> FailureKind.UNAUTHORIZED
            403 -> FailureKind.FORBIDDEN
            404 -> FailureKind.NOT_FOUND
            409 -> FailureKind.CONFLICT
            422 -> FailureKind.REQUIREMENTS_NOT_MET
            502, 503 -> FailureKind.PROVIDER_UNAVAILABLE
            else -> FailureKind.SERVER
        }
        val message = when {
            body?.code == "PROVIDER_NOT_CONFIGURED" -> "Esta opción no está disponible en este momento."
            kind == FailureKind.PROVIDER_UNAVAILABLE -> "Esta opción no está disponible por ahora. Inténtalo más tarde."
            kind == FailureKind.SERVER && response.code() >= 500 -> "No pudimos completar la acción. Inténtalo más tarde."
            else -> body?.message ?: "No pudimos completar la acción."
        }
        return ApiFailure(kind, body?.code, message,
            body?.fieldErrors.orEmpty(), response.code())
    }

    private fun failure(kind: FailureKind, message: String) = ApiResult.Failure(ApiFailure(kind, message = message))
}
