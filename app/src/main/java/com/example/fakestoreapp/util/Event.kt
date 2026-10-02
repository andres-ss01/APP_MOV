package com.example.fakestoreapp.util

/**
 * Envoltura para eventos de "una sola vez" expuestos por LiveData (mostrar una alerta,
 * un Toast, navegar...). Sin esto, al rotar la pantalla el LiveData volvería a entregar
 * el último valor y la alerta/navegación se repetiría.
 */
class Event<out T>(private val content: T) {

    private var handled = false

    /** Devuelve el contenido solo la primera vez; después devuelve null. */
    fun getContentIfNotHandled(): T? {
        return if (handled) {
            null
        } else {
            handled = true
            content
        }
    }
}
