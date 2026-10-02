package com.example.fakestoreapp.data.api

import com.example.fakestoreapp.data.model.UserRole
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Guardia de seguridad a NIVEL DE RED (US06/US07/US08 - Escenario 3).
 *
 * Intercepta toda petición saliente: si es una operación que MODIFICA productos
 * (POST / PUT / DELETE sobre /products...) y el rol local NO es Administrador, la
 * petición se bloquea y ni siquiera sale del dispositivo: se devuelve un 403 sintético.
 * Así, aunque alguien fuerce la navegación o un llamado desde el código, el
 * servidor nunca recibe la solicitud.
 *
 * Las peticiones GET y el login (/auth/login) pasan sin restricción.
 */
class AdminGuardInterceptor(
    private val roleProvider: () -> UserRole
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val isProductMutation = request.method != "GET" &&
            request.url.encodedPath.startsWith("/products")

        if (isProductMutation && roleProvider() != UserRole.ADMINISTRADOR) {
            // Respuesta falsa 403: la petición real nunca se ejecuta.
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden (bloqueado localmente)")
                .body("".toResponseBody(null))
                .build()
        }

        return chain.proceed(request)
    }
}
