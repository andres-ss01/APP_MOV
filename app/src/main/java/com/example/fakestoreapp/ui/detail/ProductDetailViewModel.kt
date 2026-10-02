package com.example.fakestoreapp.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.model.UserRole
import com.example.fakestoreapp.data.repository.ProductRepository
import com.example.fakestoreapp.data.session.SessionManager
import com.example.fakestoreapp.util.Event
import com.example.fakestoreapp.util.Resource
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de producto (US05) que además orquesta la eliminación (US08)
 * y recibe las ediciones hechas desde el formulario (US07).
 *
 * El rol se lee siempre de forma local desde SessionManager (nunca se delega esa
 * validación a la Fake Store API, tal como exige la regla de negocio de US05).
 */
class ProductDetailViewModel(
    private val productId: Int,
    private val repository: ProductRepository = ProductRepository(),
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _product = MutableLiveData<Resource<Product>>()
    val product: LiveData<Resource<Product>> = _product

    // US08: true mientras la petición DELETE está en curso (evita doble toque).
    private val _isDeleting = MutableLiveData(false)
    val isDeleting: LiveData<Boolean> = _isDeleting

    // US08: resultado de la eliminación (evento de una sola vez).
    private val _deleteResult = MutableLiveData<Event<Resource<Unit>>>()
    val deleteResult: LiveData<Event<Resource<Unit>>> = _deleteResult

    val currentRole: UserRole
        get() = sessionManager.getRole()

    val isAdmin: Boolean
        get() = currentRole == UserRole.ADMINISTRADOR

    /** Producto actualmente mostrado (null mientras carga o si hubo error). */
    val currentProduct: Product?
        get() = (_product.value as? Resource.Success)?.data

    fun loadProduct() {
        // Si ya tenemos el producto (p. ej. al volver del formulario de edición) NO se
        // vuelve a consultar: Fake Store API devolvería los datos viejos y se perderían
        // los cambios simulados de US07.
        if (_product.value is Resource.Success) return

        _product.value = Resource.Loading
        viewModelScope.launch {
            _product.value = repository.getProductById(productId)
        }
    }

    /**
     * US07: la API no persiste la edición, así que el producto editado se guarda
     * localmente aquí para que la pantalla de detalle refleje los cambios.
     */
    fun onProductUpdated(updated: Product) {
        _product.value = Resource.Success(updated)
    }

    /**
     * US08: se invoca SOLO después de que el Administrador confirmó el diálogo.
     */
    fun deleteProduct() {
        if (_isDeleting.value == true) return

        // Permisos: si por alguna razón no es Administrador, ni se intenta la petición.
        if (!isAdmin) {
            _deleteResult.value = Event(Resource.Error(ProductRepository.ERROR_FORBIDDEN))
            return
        }

        _isDeleting.value = true
        viewModelScope.launch {
            val result = repository.deleteProduct(productId)
            _isDeleting.value = false
            _deleteResult.value = Event(result)
        }
    }
}
