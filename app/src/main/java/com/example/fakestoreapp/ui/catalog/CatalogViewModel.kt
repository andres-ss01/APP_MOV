package com.example.fakestoreapp.ui.catalog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.repository.ProductRepository
import com.example.fakestoreapp.util.Resource
import kotlinx.coroutines.launch

const val ALL_CATEGORIES = "__ALL__"

/**
 * ViewModel del catálogo. Cubre US03 (listado general + loading + error) y US04
 * (obtención de categorías, aplicación y remoción de filtro).
 *
 * Pide la lista de productos al repositorio y notifica a la pantalla cuando los datos están listos.
 */
class CatalogViewModel(
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    private val _products = MutableLiveData<Resource<List<Product>>>()
    val products: LiveData<Resource<List<Product>>> = _products

    private val _categories = MutableLiveData<List<String>>()
    val categories: LiveData<List<String>> = _categories

    private val _selectedCategory = MutableLiveData(ALL_CATEGORIES)
    val selectedCategory: LiveData<String> = _selectedCategory

    fun loadInitialData() {
        loadCategories()
        loadProducts(ALL_CATEGORIES)
    }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val result = repository.getCategories()) {
                is Resource.Success -> _categories.value = result.data
                else -> _categories.value = emptyList() // el filtro es opcional; no bloquea el catálogo
            }
        }
    }

    fun onCategorySelected(category: String) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
        loadProducts(category)
    }

    private fun loadProducts(category: String) {
        // US04 - "Gestión de memoria": se limpia la lista anterior mostrando el
        // estado de carga antes de pedir los nuevos datos, para no mezclar resultados.
        _products.value = Resource.Loading

        viewModelScope.launch {
            val result = if (category == ALL_CATEGORIES) {
                repository.getAllProducts()
            } else {
                repository.getProductsByCategory(category)
            }
            _products.value = result
        }
    }

    fun retry() {
        loadProducts(_selectedCategory.value ?: ALL_CATEGORIES)
    }
}
