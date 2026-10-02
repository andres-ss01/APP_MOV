package com.example.fakestoreapp.data.api

import com.example.fakestoreapp.data.model.LoginRequest
import com.example.fakestoreapp.data.model.LoginResponse
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.model.ProductRequest
import com.example.fakestoreapp.data.model.ProductResponse
import com.example.fakestoreapp.data.model.User
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Contrato de la Fake Store API (https://fakestoreapi.com/).
 * Cubre los endpoints requeridos por US01, US03, US04, US05, US06, US07 y US08.
 */
interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("users")
    suspend fun getUsers(): Response<List<User>>

    @GET("users/{id}")
    suspend fun getUserById(@Path("id") id: Int): Response<User>

    @GET("products")
    suspend fun getAllProducts(): Response<List<Product>>

    @GET("products/categories")
    suspend fun getCategories(): Response<List<String>>

    @GET("products/category/{category}")
    suspend fun getProductsByCategory(@Path("category") category: String): Response<List<Product>>

    @GET("products/{id}")
    suspend fun getProductById(@Path("id") id: Int): Response<Product>

    // ---------------- US06: crear producto ----------------
    /** POST /products -> la API responde con el objeto creado y un nuevo ID (simulado). */
    @POST("products")
    suspend fun createProduct(@Body body: ProductRequest): Response<ProductResponse>

    // ---------------- US07: editar producto ----------------
    /**
     * PUT /products/{id} -> la API responde 200 con el objeto "actualizado" (simulado).
     * Se recibe como ResponseBody crudo porque la app no depende del contenido de la respuesta.
     */
    @PUT("products/{id}")
    suspend fun updateProduct(
        @Path("id") id: Int,
        @Body body: ProductRequest
    ): Response<ResponseBody>

    // ---------------- US08: eliminar producto ----------------
    /**
     * DELETE /products/{id} -> la API devuelve el objeto "eliminado" (o incluso null).
     * Con ResponseBody evitamos fallos de parseo: solo importa que el código HTTP sea 2xx.
     */
    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: Int): Response<ResponseBody>
}
