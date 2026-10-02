package com.example.fakestoreapp.data.model

/**
 * Modelo que empata exactamente con la estructura JSON de /products (US03, regla de
 * "Mapeo del JSON").
 */
data class Product(
    val id: Int,
    val title: String,
    val price: Double,
    val description: String,
    val category: String,
    val image: String,
    val rating: Rating? = null
)

data class Rating(
    val rate: Double? = null,
    val count: Int? = null
)

/**
 * Cuerpo JSON que se envía a la API en US06 (POST /products) y US07 (PUT /products/{id}).
 * No lleva "id": ese lo asigna el servidor (POST) o va en la URL (PUT).
 */
data class ProductRequest(
    val title: String,
    val price: Double,
    val description: String,
    val image: String,
    val category: String
) {
    /**
     * Construye el [Product] local a partir de lo que el usuario capturó.
     * Fake Store API NO persiste nada, así que la app arma el objeto con los datos
     * del formulario + el id devuelto/existente, en lugar de depender del cuerpo
     * de la respuesta (que puede venir incompleto, p. ej. solo { "id": 21 }).
     */
    fun toProduct(id: Int, rating: Rating? = null) = Product(
        id = id,
        title = title,
        price = price,
        description = description,
        category = category,
        image = image,
        rating = rating
    )
}

/**
 * Respuesta mínima de POST /products: solo nos interesa el nuevo ID generado (US06).
 * Gson ignora el resto de campos que pudiera devolver la API.
 */
data class ProductResponse(
    val id: Int? = null
)
