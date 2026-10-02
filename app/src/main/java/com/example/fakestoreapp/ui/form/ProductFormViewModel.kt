package com.example.fakestoreapp.ui.form

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.model.ProductRequest
import com.example.fakestoreapp.data.model.UserRole
import com.example.fakestoreapp.data.repository.ProductRepository
import com.example.fakestoreapp.data.session.SessionManager
import com.example.fakestoreapp.util.Event
import com.example.fakestoreapp.util.FormField
import com.example.fakestoreapp.util.ProductFormValidator
import com.example.fakestoreapp.util.Resource
import com.example.fakestoreapp.util.map
import kotlinx.coroutines.launch

/**
 * ViewModel del formulario de producto. Sirve para dos historias:
 *  - US06 (crear):  [editingProduct] == null  -> POST /products
 *  - US07 (editar): [editingProduct] != null  -> PUT  /products/{id}
 *
 * Orden de las comprobaciones al guardar (todas ANTES de tocar la red):
 *   1. Evitar doble envío mientras hay una petición en curso.
 *   2. Permisos: solo el Administrador puede continuar (el rol se lee localmente).
 *   3. Validación local de campos (vacíos, precio numérico, URL válida).
 */
class ProductFormViewModel(
    private val editingProduct: Product?,
    private val sessionManager: SessionManager,
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    /** true cuando se está editando un artículo existente (US07). */
    val isEditMode: Boolean get() = editingProduct != null

    /** El rol siempre se consulta de forma local, nunca a la API. */
    val isAdmin: Boolean get() = sessionManager.getRole() == UserRole.ADMINISTRADOR

    // Categorías para el menú desplegable (con respaldo por si la API falla).
    private val _categories = MutableLiveData(DEFAULT_CATEGORIES)
    val categories: LiveData<List<String>> = _categories

    // true mientras la petición HTTP está en curso (deshabilita el botón y muestra el indicador).
    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    // Errores de validación por campo (evento de una sola vez).
    private val _validationErrors = MutableLiveData<Event<Map<FormField, Int>>>()
    val validationErrors: LiveData<Event<Map<FormField, Int>>> = _validationErrors

    // Resultado final del guardado: el Product resultante o un error (evento de una sola vez).
    private val _saveResult = MutableLiveData<Event<Resource<Product>>>()
    val saveResult: LiveData<Event<Resource<Product>>> = _saveResult

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val result = repository.getCategories()
            if (result is Resource.Success && result.data.isNotEmpty()) {
                _categories.value = result.data
            }
        }
    }

    /**
     * Punto de entrada del botón "Guardar".
     */
    fun submit(
        title: String,
        priceText: String,
        description: String,
        imageUrl: String,
        category: String
    ) {
        // 1. Bloqueo de envíos duplicados mientras hay una petición activa.
        if (_isSaving.value == true) return

        // 2. Permisos: un Cliente/Auditor jamás debe llegar a la red.
        if (!isAdmin) {
            _saveResult.value = Event(Resource.Error(ProductRepository.ERROR_FORBIDDEN))
            return
        }

        // 3. Validación local: si falla, NO se ejecuta ninguna petición HTTP.
        val errors = ProductFormValidator.validate(title, priceText, description, imageUrl, category)
        if (errors.isNotEmpty()) {
            _validationErrors.value = Event(errors)
            return
        }

        // Los datos ya son válidos: armamos el cuerpo de la petición con valores limpios.
        val request = ProductRequest(
            title = title.trim(),
            price = ProductFormValidator.parsePrice(priceText)!!, // validado arriba
            description = description.trim(),
            image = imageUrl.trim(),
            category = category.trim()
        )

        _isSaving.value = true
        viewModelScope.launch {
            val product = editingProduct
            val result: Resource<Product> = if (product == null) {
                // US06: la API devuelve el nuevo ID; armamos el Product con lo capturado.
                repository.createProduct(request).map { newId -> request.toProduct(newId) }
            } else {
                // US07: conservamos id y rating originales; el resto viene del formulario.
                repository.updateProduct(product.id, request)
                    .map { request.toProduct(product.id, product.rating) }
            }

            _isSaving.value = false
            _saveResult.value = Event(result)
        }
    }

    companion object {
        // Categorías oficiales de Fake Store API, usadas solo si GET /products/categories falla.
        private val DEFAULT_CATEGORIES = listOf(
            "electronics",
            "jewelery",
            "men's clothing",
            "women's clothing"
        )
    }
}
