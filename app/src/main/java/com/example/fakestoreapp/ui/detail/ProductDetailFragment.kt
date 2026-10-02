package com.example.fakestoreapp.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.fakestoreapp.R
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.repository.ProductRepository
import com.example.fakestoreapp.data.session.SessionManager
import com.example.fakestoreapp.databinding.FragmentProductDetailBinding
import com.example.fakestoreapp.databinding.LayoutAdminControlsBinding
import com.example.fakestoreapp.ui.form.ProductFormFragment
import com.example.fakestoreapp.util.Resource
import com.example.fakestoreapp.util.ViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import java.util.Locale

/**
 * Vista de detalle con interfaz dinámica según el rol de sesión (US05):
 *  - Cliente / Auditor: solo información de consulta (Escenario 1).
 *  - Administrador: además ve los botones "Editar" y "Eliminar", inflados
 *    dinámicamente vía ViewStub (Escenario 2), nunca instanciados ocultos.
 *  - Cualquier error de la API (o producto inexistente) muestra una alerta y
 *    regresa automáticamente al catálogo (Escenario 3).
 *
 * Además orquesta:
 *  - US07: "Editar" abre el formulario precargado; al volver, el detalle muestra los
 *    cambios (la API no los persiste, por eso viajan localmente).
 *  - US08: "Eliminar" pide confirmación con un AlertDialog; solo si se confirma se
 *    dispara el DELETE, y al terminar se muestra un Snackbar y se regresa al catálogo.
 */
class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    // Binding de los botones de administrador (null si el rol no es Administrador).
    private var adminBinding: LayoutAdminControlsBinding? = null

    private val args: ProductDetailFragmentArgs by navArgs()

    private val viewModel: ProductDetailViewModel by viewModels {
        ViewModelFactory {
            ProductDetailViewModel(
                productId = args.productId,
                sessionManager = SessionManager.getInstance(requireContext().applicationContext)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        // US07: el formulario de edición nos devuelve el producto modificado. El listener se
        // registra en onCreate (no en onViewCreated) para no perder el resultado cuando la
        // vista del detalle se destruye/recrea al ir y volver del formulario.
        setFragmentResultListener(ProductFormFragment.REQUEST_KEY_PRODUCT_UPDATED) { _, bundle ->
            val json = bundle.getString(ProductFormFragment.RESULT_PRODUCT_JSON)
            if (json != null) {
                viewModel.onProductUpdated(Gson().fromJson(json, Product::class.java))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Habilitar flecha de regreso en la ActionBar de la Activity superior
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        observeViewModel()
        // loadProduct() ignora la llamada si el producto ya está cargado (p. ej. al volver
        // del formulario de edición), así no se pisan los cambios simulados de US07.
        if (savedInstanceState == null) {
            viewModel.loadProduct()
        }
    }

    private fun observeViewModel() {
        viewModel.product.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.contentContainer.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    bindProduct(state.data)
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    handleError()
                }
            }
        }

        // US08: mientras se elimina, se bloquean los botones para evitar toques repetidos.
        viewModel.isDeleting.observe(viewLifecycleOwner) { deleting ->
            setAdminControlsBusy(deleting)
        }

        // US08: resultado de la eliminación.
        viewModel.deleteResult.observe(viewLifecycleOwner) { event ->
            val result = event.getContentIfNotHandled() ?: return@observe
            when (result) {
                is Resource.Success -> onProductDeleted()
                is Resource.Error -> handleDeleteError(result.message)
                is Resource.Loading -> Unit
            }
        }
    }

    private fun bindProduct(product: Product) {
        binding.contentContainer.visibility = View.VISIBLE
        (activity as? AppCompatActivity)?.supportActionBar?.title = product.title

        Glide.with(binding.ivDetailImage.context)
            .load(product.image)
            .centerInside()
            .into(binding.ivDetailImage)

        binding.tvDetailCategory.text = product.category.replaceFirstChar { it.uppercase() }
        binding.tvDetailTitle.text = product.title
        binding.tvDetailPrice.text = String.format(Locale.US, getString(R.string.detail_price_format), product.price)
        binding.tvDetailDescription.text = product.description

        // Escenario 2 (US05): el ViewStub SOLO se infla si el rol es Administrador.
        // Para Cliente/Auditor, btnEdit y btnDelete nunca llegan a existir en el árbol
        // de vistas (no se ocultan con visibility=GONE, se excluyen por completo).
        // US08 - Escenario 3: por eso la interfaz "nunca renderiza el botón de eliminación"
        // para Cliente/Auditor.
        if (viewModel.isAdmin && binding.stubAdminControls.parent != null) {
            val inflatedView = binding.stubAdminControls.inflate()
            adminBinding = LayoutAdminControlsBinding.bind(inflatedView).also { setupAdminControls(it) }
            setAdminControlsBusy(viewModel.isDeleting.value == true)
        }
    }

    private fun setupAdminControls(admin: LayoutAdminControlsBinding) {
        // US07: abrir el formulario de edición con el producto ACTUAL (puede ya estar editado).
        admin.btnEdit.setOnClickListener {
            val product = viewModel.currentProduct ?: return@setOnClickListener
            val action = ProductDetailFragmentDirections.actionDetailToForm(Gson().toJson(product))
            findNavController().navigate(action)
        }

        // US08: nunca se dispara el DELETE con un solo toque; primero se confirma.
        admin.btnDelete.setOnClickListener { showDeleteConfirmation() }
    }

    /**
     * US08 - Escenarios 1 y 2: AlertDialog nativo de confirmación.
     *  - "Sí"       -> se ejecuta el DELETE.
     *  - "Cancelar" -> el diálogo se cierra, NO se hace ninguna petición y la vista no cambia.
     */
    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.detail_delete_confirm)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> viewModel.deleteProduct() }
            .setNegativeButton(R.string.dialog_no, null) // null = solo cierra el diálogo
            .show()
    }

    /** Activa/desactiva los botones de administrador y cambia el texto de "Eliminar". */
    private fun setAdminControlsBusy(busy: Boolean) {
        adminBinding?.apply {
            btnEdit.isEnabled = !busy
            btnDelete.isEnabled = !busy
            btnDelete.setText(if (busy) R.string.detail_deleting else R.string.detail_delete)
        }
    }

    /**
     * US08 - Escenario 1: Snackbar de éxito + regreso al catálogo general.
     * El Snackbar se ancla a la vista raíz de la Activity para que siga visible
     * aunque este fragmento se destruya al volver al catálogo.
     */
    private fun onProductDeleted() {
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            R.string.detail_delete_toast,
            Snackbar.LENGTH_LONG
        ).show()
        findNavController().popBackStack(R.id.catalogFragment, false)
    }

    private fun handleDeleteError(message: String) {
        when (message) {
            ProductRepository.ERROR_FORBIDDEN -> {
                Toast.makeText(requireContext(), R.string.error_forbidden, Toast.LENGTH_LONG).show()
                findNavController().popBackStack(R.id.catalogFragment, false)
            }
            ProductRepository.ERROR_NETWORK ->
                Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_LONG).show()
            else ->
                Toast.makeText(requireContext(), R.string.detail_delete_error, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Manejar el toque en la flecha de la ActionBar para volver al catálogo
     */
    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                findNavController().popBackStack()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * US05 - Escenario 3: la solicitud a la API falla o el producto no existe ->
     * alerta "Producto no disponible" + regreso automático al catálogo.
     */
    private fun handleError() {
        Toast.makeText(requireContext(), R.string.detail_error_not_available, Toast.LENGTH_LONG).show()
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ocultar la flecha de navegación al salir del detalle para no dejarla activa en el catálogo
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
        }
        adminBinding = null
        _binding = null
    }
}
