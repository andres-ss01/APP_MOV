package com.example.fakestoreapp.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.fakestoreapp.data.model.Product
import com.example.fakestoreapp.databinding.ItemProductBinding
import java.util.Locale
/**
 * Adaptador basado en ListAdapter + DiffUtil para aprovechar el reciclaje de vistas
 * de RecyclerView (requisito de rendimiento de US03) y animar únicamente los cambios
 * reales cuando el catálogo se filtra (US04).
 * Toma la lista de productos (List<Product>) y dibuja cada tarjeta de producto (con imagen, título y precio) en el RecyclerView.
 */
class ProductAdapter(
    private val onProductClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback) {

    //Infla o dibuja el diseño individual de cada tarjeta de producto (item_product.xml).
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }
    //Toma el producto de la posición actual y le asigna a la tarjeta la imagen, el título y el precio correspondientes.
    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.tvTitle.text = product.title
            binding.tvPrice.text = String.format(Locale.US, "$%.2f", product.price)

            // Carga asíncrona en segundo plano para no congelar la interfaz (US03)
            Glide.with(binding.ivProduct.context)
                .load(product.image)
                .centerInside()
                .into(binding.ivProduct)

            binding.root.setOnClickListener { onProductClick(product) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
            return oldItem == newItem
        }
    }
}