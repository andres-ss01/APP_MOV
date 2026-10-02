package com.example.fakestoreapp.data.api

import com.example.fakestoreapp.data.model.UserRole
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Punto único de construcción del cliente Retrofit apuntando a la Fake Store API.
 * Se expone como singleton (object) para reutilizar la conexión en toda la app.
 */
object RetrofitClient {

    private const val BASE_URL = "https://fakestoreapi.com/"

    /**
     * Proveedor del rol actual para el [AdminGuardInterceptor]. Lo asigna MainActivity
     * apuntando al SessionManager. Por seguridad, el valor por defecto es el rol sin
     * privilegios: si nadie lo configura, ninguna mutación de productos sale a la red.
     */
    @Volatile
    var roleProvider: () -> UserRole = { UserRole.CLIENTE }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // Guardia de permisos a nivel de red (se lee el rol en cada petición).
        .addInterceptor(AdminGuardInterceptor { roleProvider() })
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
