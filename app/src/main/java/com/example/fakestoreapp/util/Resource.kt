package com.example.fakestoreapp.util

/**
 * Envoltura genérica para representar el estado de una operación asíncrona
 * (carga / éxito / error) y así pilotar los indicadores visuales exigidos
 * en US03, US04 y US05 (spinners, mensajes de error, reintentos).
 */
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}

/**
 * Transforma el dato de un Success conservando Error/Loading tal cual.
 * Útil para convertir la respuesta del repositorio (p. ej. un ID) en el modelo
 * que necesita la pantalla (un Product completo).
 */
inline fun <T, R> Resource<T>.map(transform: (T) -> R): Resource<R> = when (this) {
    is Resource.Success -> Resource.Success(transform(data))
    is Resource.Error -> this
    is Resource.Loading -> this
}
