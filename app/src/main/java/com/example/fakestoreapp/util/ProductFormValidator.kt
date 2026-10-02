package com.example.fakestoreapp.util

import android.util.Patterns
import com.example.fakestoreapp.R

/** Campos del formulario de producto (US06 / US07). */
enum class FormField { TITLE, PRICE, DESCRIPTION, IMAGE, CATEGORY }

/**
 * Validación 100% LOCAL del formulario (regla de negocio de US06 y US07): se ejecuta
 * SIEMPRE antes de lanzar cualquier petición HTTP, para no gastar red con datos inválidos.
 */
object ProductFormValidator {

    // Solo dígitos con hasta 2 decimales, ej. "19", "19.9", "109.95". Rechaza letras, signos y notación científica.
    private val PRICE_REGEX = Regex("^\\d{1,7}(\\.\\d{1,2})?$")

    /**
     * Convierte el texto del precio a Double o devuelve null si no es numérico válido (> 0).
     * Se tolera la coma decimal ("12,5") por comodidad de los teclados en español.
     */
    fun parsePrice(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (!PRICE_REGEX.matches(normalized)) return null
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    /** La URL debe empezar con http:// o https:// y tener un formato web correcto. */
    fun isValidImageUrl(raw: String): Boolean {
        val url = raw.trim()
        val hasScheme = url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        return hasScheme && Patterns.WEB_URL.matcher(url).matches()
    }

    /**
     * Valida todos los campos y devuelve un mapa campo -> mensaje de error (recurso string).
     * Si el mapa está vacío, el formulario es válido.
     */
    fun validate(
        title: String,
        price: String,
        description: String,
        imageUrl: String,
        category: String
    ): Map<FormField, Int> {
        val errors = linkedMapOf<FormField, Int>()

        if (title.isBlank()) errors[FormField.TITLE] = R.string.form_error_title_required

        if (price.isBlank()) {
            errors[FormField.PRICE] = R.string.form_error_price_required
        } else if (parsePrice(price) == null) {
            errors[FormField.PRICE] = R.string.form_error_price_invalid
        }

        if (description.isBlank()) errors[FormField.DESCRIPTION] = R.string.form_error_description_required

        if (imageUrl.isBlank()) {
            errors[FormField.IMAGE] = R.string.form_error_image_required
        } else if (!isValidImageUrl(imageUrl)) {
            errors[FormField.IMAGE] = R.string.form_error_image_invalid
        }

        if (category.isBlank()) errors[FormField.CATEGORY] = R.string.form_error_category_required

        return errors
    }
}
