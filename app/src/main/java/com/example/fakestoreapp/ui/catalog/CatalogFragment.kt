package com.example.fakestoreapp.ui.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.fakestoreapp.R
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.model.UserRole
import com.example.fakestoreapp.data.session.SessionManager
import com.example.fakestoreapp.databinding.FragmentCatalogBinding
import com.example.fakestoreapp.util.NetworkUtils
import com.example.fakestoreapp.util.Resource
import com.example.fakestoreapp.util.ViewModelFactory
import com.google.android.material.chip.Chip

/**
 * Pantalla principal tras el login (US03) con filtro por categoría (US04).
 *
 * Muestra la pantalla principal con la lista/cuadrícula de productos.
 *
 * US06: incluye un botón flotante (abajo a la derecha) para agregar productos,
 * visible únicamente para el rol Administrador.
 */
class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CatalogViewModel by viewModels {
        ViewModelFactory { CatalogViewModel() }
    }

    private lateinit var adapter: ProductAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSwipeRefresh()
        setupRetryButton()
        setupAddProductFab()
        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.loadInitialData()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter { product ->
            val action = CatalogFragmentDirections.actionCatalogToDetail(product.id)
            findNavController().navigate(action)
        }
        // RecyclerView con GridLayoutManager: vistas reciclables, no ScrollView (US03).
        binding.rvProducts.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = this@CatalogFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.retry()
        }
    }

    /**
     * US06: botón flotante "+" para agregar un producto.
     * El rol se consulta localmente (SessionManager). Si no es Administrador el botón
     * permanece oculto; y aunque alguien llegara a la pantalla de creación por otro
     * medio, el formulario lo redirigiría de vuelta aquí (Escenario 3).
     */
    private fun setupAddProductFab() {
        val isAdmin = SessionManager.getInstance(requireContext().applicationContext)
            .getRole() == UserRole.ADMINISTRADOR

        binding.fabAddProduct.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.fabAddProduct.setOnClickListener {
            // productJson = null -> el formulario se abre en modo "crear".
            findNavController().navigate(CatalogFragmentDirections.actionCatalogToForm(null))
        }
    }

    private fun setupRetryButton() {
        binding.btnRetry.setOnClickListener { viewModel.retry() }
    }

    private fun observeViewModel() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            buildCategoryChips(categories)
        }

        viewModel.selectedCategory.observe(viewLifecycleOwner) { selected ->
            syncChipSelection(selected)
        }

        viewModel.products.observe(viewLifecycleOwner) { state ->
            binding.swipeRefresh.isRefreshing = false
            when (state) {
                is Resource.Loading -> showLoading()
                is Resource.Success -> showProducts(state.data)
                is Resource.Error -> showError()
            }
        }
    }

    private fun buildCategoryChips(categories: List<String>) {
        binding.chipGroupCategories.removeAllViews()

        // Chip "Ver todos" siempre presente para remover el filtro (US04 - Escenario 3).----------------------------------------------------------------------------------------------
        addChip(getString(R.string.catalog_all_categories), ALL_CATEGORIES)
        categories.forEach { category ->
            addChip(category.replaceFirstChar { it.uppercase() }, category)
        }
        syncChipSelection(viewModel.selectedCategory.value ?: ALL_CATEGORIES)
    }

    private fun addChip(label: String, value: String) {
        val chip = Chip(requireContext()).apply {
            text = label
            isCheckable = true
            tag = value
            setOnClickListener { viewModel.onCategorySelected(value) }
        }
        binding.chipGroupCategories.addView(chip)
    }

    private fun syncChipSelection(selectedValue: String) {
        for (i in 0 until binding.chipGroupCategories.childCount) {
            val chip = binding.chipGroupCategories.getChildAt(i) as? Chip ?: continue
            chip.isChecked = chip.tag == selectedValue
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
        binding.rvProducts.visibility = View.INVISIBLE
    }

    private fun showProducts(products: List<Product>) {
        binding.progressBar.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
        binding.rvProducts.visibility = View.VISIBLE
        adapter.submitList(products)
    }

    private fun showError() {
        binding.progressBar.visibility = View.GONE
        binding.rvProducts.visibility = View.INVISIBLE
        binding.errorContainer.visibility = View.VISIBLE

        val message = if (!NetworkUtils.isConnected(requireContext())) {
            getString(R.string.catalog_error_connection)
        } else {
            getString(R.string.catalog_error_connection)
        }
        binding.tvErrorMessage.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
