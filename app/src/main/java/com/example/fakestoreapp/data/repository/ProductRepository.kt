package com.example.fakestoreapp.data.repository

import com.example.fakestoreapp.data.api.ApiService
import com.example.fakestoreapp.data.api.RetrofitClient
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.model.ProductRequest
import com.example.fakestoreapp.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Repositorio de productos. Cubre US03 (catálogo general), US04 (categorías y filtro),
 * US05 (detalle), US06 (crear), US07 (editar) y US08 (eliminar).
 *
 * Consulta la API para obtener el listado completo de productos
 */
class ProductRepository(private val api: ApiService = RetrofitClient.apiService) {

    suspend fun getAllProducts(): Resource<List<Product>> = withContext(Dispatchers.IO) {
        safeCall { api.getAllProducts() }
    }

    suspend fun getCategories(): Resource<List<String>> = withContext(Dispatchers.IO) {
        safeCall { api.getCategories() }
    }

    suspend fun getProductsByCategory(category: String): Resource<List<Product>> =
        withContext(Dispatchers.IO) {
            safeCall { api.getProductsByCategory(category) }
        }

    suspend fun getProductById(id: Int): Resource<Product> = withContext(Dispatchers.IO) {
        safeCall { api.getProductById(id) }
    }

    // ------------------------------------------------------------------
    // US06 - Crear producto (POST /products)
    // ------------------------------------------------------------------

    /**
     * Envía el nuevo producto a la API y devuelve el ID que ésta genera.
     * Recordatorio: Fake Store API solo SIMULA la creación; el producto no se guarda.
     */
    suspend fun createProduct(request: ProductRequest): Resource<Int> =
        withContext(Dispatchers.IO) {
            when (val result = safeMutation { api.createProduct(request) }) {
                is Resource.Success -> {
                    val newId = result.data.body()?.id
                    if (newId != null) Resource.Success(newId) else Resource.Error(ERROR_GENERIC)
                }
                is Resource.Error -> result
                else -> Resource.Error(ERROR_GENERIC)
            }
        }

    // ------------------------------------------------------------------
    // US07 - Editar producto (PUT /products/{id})
    // ------------------------------------------------------------------

    /** La API responde 200 con el objeto "actualizado", pero no modifica su base de datos. */
    suspend fun updateProduct(id: Int, request: ProductRequest): Resource<Unit> =
        withContext(Dispatchers.IO) {
            when (val result = safeMutation { api.updateProduct(id, request) }) {
                is Resource.Success -> {
                    result.data.body()?.close() // liberamos el cuerpo: no lo necesitamos
                    Resource.Success(Unit)
                }
                is Resource.Error -> result
                else -> Resource.Error(ERROR_GENERIC)
            }
        }

    // ------------------------------------------------------------------
    // US08 - Eliminar producto (DELETE /products/{id})
    // ------------------------------------------------------------------

    /**
     * La API devuelve el objeto "eliminado", pero en futuros GET seguirá apareciendo.
     * Basta con que el código HTTP sea exitoso para considerar el flujo correcto.
     */
    suspend fun deleteProduct(id: Int): Resource<Unit> =
        withContext(Dispatchers.IO) {
            when (val result = safeMutation { api.deleteProduct(id) }) {
                is Resource.Success -> {
                    result.data.body()?.close()
                    Resource.Success(Unit)
                }
                is Resource.Error -> result
                else -> Resource.Error(ERROR_GENERIC)
            }
        }

    // ------------------------------------------------------------------
    // Utilidades internas
    // ------------------------------------------------------------------

    private suspend inline fun <T> safeCall(call: () -> retrofit2.Response<T>): Resource<T> {
        return try {
            val response = call()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                Resource.Success(body)
            } else {
                Resource.Error("NOT_FOUND")
            }
        } catch (io: IOException) {
            Resource.Error("NETWORK_ERROR")
        } catch (e: Exception) {
            Resource.Error("GENERIC_ERROR")
        }
    }

    /**
     * Variante para operaciones que modifican datos (POST/PUT/DELETE). Devuelve la
     * respuesta completa si el HTTP fue 2xx, o el error clasificado:
     *  - 403 -> bloqueada por el AdminGuardInterceptor (sin permisos)
     *  - IOException -> sin red / timeout
     *  - otro -> error genérico
     */
    private suspend inline fun <T> safeMutation(
        call: () -> retrofit2.Response<T>
    ): Resource<retrofit2.Response<T>> {
        return try {
            val response = call()
            when {
                response.code() == 403 -> Resource.Error(ERROR_FORBIDDEN)
                response.isSuccessful -> Resource.Success(response)
                else -> Resource.Error(ERROR_GENERIC)
            }
        } catch (io: IOException) {
            Resource.Error(ERROR_NETWORK)
        } catch (e: Exception) {
            Resource.Error(ERROR_GENERIC)
        }
    }

    companion object {
        const val ERROR_NETWORK = "NETWORK_ERROR"
        const val ERROR_GENERIC = "GENERIC_ERROR"
        const val ERROR_FORBIDDEN = "FORBIDDEN"
    }
}
