package com.example.fakestoreapp.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.fakestoreapp.R
import com.example.fakestoreapp.data.api.RetrofitClient
import com.example.fakestoreapp.data.session.CartManager
import com.example.fakestoreapp.data.session.SessionManager
import com.example.fakestoreapp.databinding.ActivityMainBinding
import com.example.fakestoreapp.ui.login.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Actividad única que aloja el grafo de navegación (catálogo -> detalle) y la gestión
 * del perfil de usuario y cierre de sesión (US02).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager.getInstance(applicationContext)

        // Verificar si existe sesión activa
        if (!sessionManager.isLoggedIn()) {
            goToLogin()
            return
        }

        // US06/US07/US08: el guardia de red (AdminGuardInterceptor) consulta el rol local en
        // cada petición para bloquear POST/PUT/DELETE de productos a quien no sea Administrador.
        RetrofitClient.roleProvider = { sessionManager.getRole() }

        // Obtener la referencia al NavController desde el FragmentContainerView
        val navHostFragment = supportFragmentManager
            .findFragmentById(binding.navHostFragment.id) as NavHostFragment
        navController = navHostFragment.navController

        // Escuchador del botón de perfil personalizado en la barra superior
        binding.btnProfile.setOnClickListener {
            showProfileDialog()
        }
    }

    /**
     * Muestra el diálogo personalizado de perfil utilizando dialog_profile.xml
     */
    private fun showProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile, null)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Fondo transparente para conservar esquinas redondeadas del CardView
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Referencias a las vistas exactas dentro de dialog_profile.xml
        val tvProfileUsername = dialogView.findViewById<TextView>(R.id.tvProfileUsername)
        val tvProfileRole = dialogView.findViewById<TextView>(R.id.tvProfileRole)
        val tvProfileId = dialogView.findViewById<TextView>(R.id.tvProfileId)
        val tvProfileEmail = dialogView.findViewById<TextView>(R.id.tvProfileEmail)
        val tvProfilePhone = dialogView.findViewById<TextView>(R.id.tvProfilePhone)
        val tvProfileAddress = dialogView.findViewById<TextView>(R.id.tvProfileAddress)

        val btnBackToCatalogHeader = dialogView.findViewById<MaterialCardView>(R.id.btnBackToCatalogHeader)
        val btnLogout = dialogView.findViewById<MaterialButton>(R.id.btnLogout)

        // Cargar los datos guardados en SessionManager
        tvProfileUsername.text = sessionManager.getUsername() ?: "Usuario"
        tvProfileRole.text = "Rol: ${sessionManager.getRole()}"
        tvProfileId.text = "#${sessionManager.getUserId()}"

        val email = sessionManager.getEmail()
        val phone = sessionManager.getPhone()
        val address = sessionManager.getAddress()

        tvProfileEmail.text = if (!email.isNullOrBlank()) email else "No disponible"
        tvProfilePhone.text = if (!phone.isNullOrBlank()) phone else "No disponible"
        tvProfileAddress.text = if (!address.isNullOrBlank()) address else "No disponible"

        // Acciones de los botones del diálogo
        btnBackToCatalogHeader?.setOnClickListener {
            dialog.dismiss()
        }

        btnLogout.setOnClickListener {
            dialog.dismiss()
            confirmLogout()
        }

        dialog.show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> performLogout() }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    /**
     * US02 completo:
     *  - Borra token, rol, id y datos del almacenamiento local.
     *  - Resetea el carrito.
     *  - Usa FLAG_ACTIVITY_CLEAR_TASK + NEW_TASK para limpiar la pila de actividades.
     */
    private fun performLogout() {
        sessionManager.clear()
        CartManager.clear()
        goToLogin()
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}