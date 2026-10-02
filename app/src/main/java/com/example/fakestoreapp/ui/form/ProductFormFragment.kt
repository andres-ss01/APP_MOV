package com.example.fakestoreapp.ui.form

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.fakestoreapp.R
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.data.model.UserRole
import com.example.fakestoreapp.data.repository.ProductRepository
import com.example.fakestoreapp.data.session.SessionManager
import com.example.fakestoreapp.databinding.FragmentProductFormBinding
import com.example.fakestoreapp.util.FormField
import com.example.fakestoreapp.util.Resource
import com.example.fakestoreapp.util.ViewModelFactory
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import java.util.Locale

/**
 * Formulario de producto compartido por dos historias de usuario:
 *
 *  US06 - AGREGAR (se llega desde el botón flotante del catálogo, productJson == null):
 *    - Éxito: alerta con el ID generado + limpieza del formulario.
 *    - Campos vacíos / precio con letras / URL mala: se detiene la petición y se
 *      resaltan en rojo los campos a corregir.
 *    - Rol Cliente/Auditor: se bloquea el acceso y se redirige al catálogo.
 *
 *  US07 - EDITAR (se llega desde el botón "Editar" del detalle, productJson != null):
 *    - Los campos aparecen precargados con la información actual del artículo.
 *    - Éxito: Toast "Producto actualizado (Simulación)", se cierra el formulario y el
 *      detalle refleja los cambios.
 *    - El botón Guardar se deshabilita y muestra un indicador mientras dura la petición.
 */
class ProductFormFragment : Fragment() {

    private var _binding: FragmentProductFormBinding? = null
    private val binding get() = _binding!!

    private val args: ProductFormFragmentArgs by navArgs()

    // Gestor de sesión: se usa para el guardia de permisos (el rol se lee SIEMPRE de forma local).
    private val sessionManager by lazy {
        SessionManager.getInstance(requireContext().applicationContext)
    }

    // Producto a editar (null => modo "crear"). Llega serializado como JSON en los argumentos.
    private val editingProduct: Product? by lazy {
        args.productJson?.let { Gson().fromJson(it, Product::class.java) }
    }

    private val viewModel: ProductFormViewModel by viewModels {
        ViewModelFactory { ProductFormViewModel(editingProduct, sessionManager) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---- US06/US07 Escenario 3: guardia de permisos ----
        // Un Cliente o Auditor que llegue aquí (enlace profundo, alteración de estado...)
        // es expulsado de inmediato al catálogo y la pantalla ni siquiera se configura.
        if (sessionManager.getRole() != UserRole.ADMINISTRADOR) {
            // post{} para no navegar en medio del ciclo de creación de la vista.
            view.post { if (isAdded) redirectToCatalog() }
            return
        }

        setupTexts()
        setupCategoryDropdown()
        setupErrorClearing()
        setupListeners()
        observeViewModel()

        // US07 Escenario 2: precargar los campos con la información actual del artículo.
        // Solo en la primera creación; al rotar, Android restaura lo que el usuario escribió.
        if (savedInstanceState == null) {
            editingProduct?.let { prefillForm(it) }
        }
    }

    // ------------------------------------------------------------------
    // Configuración de la UI
    // ------------------------------------------------------------------

    /** Ajusta títulos/etiquetas según el modo (crear o editar). */
    private fun setupTexts() {
        binding.tvFormTitle.setText(
            if (viewModel.isEditMode) R.string.form_title_edit else R.string.form_title_create
        )
        binding.btnSave.setText(saveButtonLabel())
    }

    private fun saveButtonLabel(): Int =
        if (viewModel.isEditMode) R.string.form_save_changes else R.string.form_save

    /** Menú desplegable de categorías (se actualiza cuando llegan las de la API). */
    private fun setupCategoryDropdown() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            binding.etCategory.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
            )
        }
    }

    /** Cuando el usuario empieza a corregir un campo, se le quita el resaltado rojo. */
    private fun setupErrorClearing() {
        clearErrorOnEdit(binding.tilTitle, binding.etTitle)
        clearErrorOnEdit(binding.tilPrice, binding.etPrice)
        clearErrorOnEdit(binding.tilDescription, binding.etDescription)
        clearErrorOnEdit(binding.tilImage, binding.etImage)
        clearErrorOnEdit(binding.tilCategory, binding.etCategory)
    }

    private fun clearErrorOnEdit(layout: TextInputLayout, editText: EditText) {
        editText.doAfterTextChanged { layout.error = null }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            // Toda la lógica (permisos, validación local, petición HTTP) vive en el ViewModel.
            viewModel.submit(
                title = binding.etTitle.text?.toString().orEmpty(),
                priceText = binding.etPrice.text?.toString().orEmpty(),
                description = binding.etDescription.text?.toString().orEmpty(),
                imageUrl = binding.etImage.text?.toString().orEmpty(),
                category = binding.etCategory.text?.toString().orEmpty()
            )
        }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun observeViewModel() {
        // Estado de "guardando": deshabilita botón/campos y muestra el indicador de carga.
        viewModel.isSaving.observe(viewLifecycleOwner) { saving -> setSaving(saving) }

        // Errores de validación local -> resaltar campos en rojo (la petición NO se envió).
        viewModel.validationErrors.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { showValidationErrors(it) }
        }

        // Resultado del guardado (éxito o error de red/permiso).
        viewModel.saveResult.observe(viewLifecycleOwner) { event ->
            val result = event.getContentIfNotHandled() ?: return@observe
            when (result) {
                is Resource.Success -> {
                    if (viewModel.isEditMode) onProductUpdated(result.data)
                    else onProductCreated(result.data)
                }
                is Resource.Error -> handleSaveError(result.message)
                is Resource.Loading -> Unit
            }
        }
    }

    // ------------------------------------------------------------------
    // Estados y resultados
    // ------------------------------------------------------------------

    /**
     * US07 (regla de negocio): mientras la solicitud HTTP está en curso, el botón de
     * guardado se deshabilita y se muestra un indicador de carga (evita envíos duplicados).
     */
    private fun setSaving(saving: Boolean) {
        binding.progressSaving.visibility = if (saving) View.VISIBLE else View.INVISIBLE
        binding.btnSave.isEnabled = !saving
        binding.btnCancel.isEnabled = !saving
        binding.btnSave.setText(if (saving) R.string.form_saving else saveButtonLabel())

        listOf(
            binding.tilTitle, binding.tilPrice, binding.tilDescription,
            binding.tilImage, binding.tilCategory
        ).forEach { it.isEnabled = !saving }
    }

    /** Pone el mensaje de error (rojo) en cada campo inválido y enfoca el primero. */
    private fun showValidationErrors(errors: Map<FormField, Int>) {
        fun errorFor(field: FormField): String? = errors[field]?.let { getString(it) }

        binding.tilTitle.error = errorFor(FormField.TITLE)
        binding.tilPrice.error = errorFor(FormField.PRICE)
        binding.tilDescription.error = errorFor(FormField.DESCRIPTION)
        binding.tilImage.error = errorFor(FormField.IMAGE)
        binding.tilCategory.error = errorFor(FormField.CATEGORY)

        // Llevar el foco al primer campo con error, en el orden visual del formulario.
        val firstInvalid = listOf(
            FormField.TITLE to binding.etTitle,
            FormField.PRICE to binding.etPrice,
            FormField.DESCRIPTION to binding.etDescription,
            FormField.IMAGE to binding.etImage,
            FormField.CATEGORY to binding.etCategory
        ).firstOrNull { errors.containsKey(it.first) }
        firstInvalid?.second?.requestFocus()
    }

    /**
     * US06 Escenario 1: alerta de confirmación con el nuevo ID generado y limpieza del formulario.
     */
    private fun onProductCreated(product: Product) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.form_created_title)
            .setMessage(getString(R.string.form_created_message, product.id))
            .setPositiveButton(R.string.dialog_ok, null)
            .show()

        clearForm()
    }

    /**
     * US07 Escenario 1: mensaje de simulación, cierre del formulario y envío del producto
     * actualizado al detalle (la API no persiste cambios, así que se pasa localmente).
     */
    private fun onProductUpdated(product: Product) {
        Toast.makeText(requireContext(), R.string.form_updated_toast, Toast.LENGTH_SHORT).show()

        // El detalle escucha esta clave y refresca su pantalla con el producto editado.
        setFragmentResult(
            REQUEST_KEY_PRODUCT_UPDATED,
            bundleOf(RESULT_PRODUCT_JSON to Gson().toJson(product))
        )
        findNavController().popBackStack()
    }

    private fun handleSaveError(message: String) {
        when (message) {
            // El AdminGuardInterceptor o el ViewModel bloquearon la petición por falta de permisos.
            ProductRepository.ERROR_FORBIDDEN -> redirectToCatalog()
            ProductRepository.ERROR_NETWORK ->
                Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_LONG).show()
            else ->
                Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Rellena los campos con los datos actuales del artículo (US07 - Escenario 2). */
    private fun prefillForm(product: Product) {
        binding.etTitle.setText(product.title)
        binding.etPrice.setText(String.format(Locale.US, "%.2f", product.price))
        binding.etDescription.setText(product.description)
        binding.etImage.setText(product.image)
        // filter = false: evita que el menú filtre las opciones con el texto precargado.
        binding.etCategory.setText(product.category, false)
    }

    /** Deja el formulario vacío y sin errores (US06 - Escenario 1). */
    private fun clearForm() {
        binding.etTitle.text?.clear()
        binding.etPrice.text?.clear()
        binding.etDescription.text?.clear()
        binding.etImage.text?.clear()
        binding.etCategory.setText("", false)

        listOf(
            binding.tilTitle, binding.tilPrice, binding.tilDescription,
            binding.tilImage, binding.tilCategory
        ).forEach { it.error = null }

        binding.etTitle.requestFocus()
    }

    /** US06/US07 - Escenario 3: expulsa al usuario sin permisos a la pantalla del catálogo. */
    private fun redirectToCatalog() {
        Toast.makeText(requireContext(), R.string.error_forbidden, Toast.LENGTH_LONG).show()
        val navController = findNavController()
        if (!navController.popBackStack(R.id.catalogFragment, false)) {
            navController.navigate(R.id.catalogFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Clave del Fragment Result API con el que el formulario avisa al detalle (US07). */
        const val REQUEST_KEY_PRODUCT_UPDATED = "product_updated"
        const val RESULT_PRODUCT_JSON = "product_json"
    }
}
