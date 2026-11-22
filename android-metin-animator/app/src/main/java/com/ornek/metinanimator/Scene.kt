package com.ornek.metinanimator

/**
 * Bir metinden üretilen sahnenin tarifi.
 * AnimationView bu tarife bakarak çizim yapar.
 */
data class Scene(
    val background: Background,
    val ground: Ground,
    val elements: List<ElementType>,
    val caption: String
)

/** Gökyüzü / genel atmosfer. */
enum class Background { DAY, NIGHT, SUNSET }

/** Zemin türü. */
enum class Ground { GRASS, WATER, SAND, NONE }

/** Sahneye yerleştirilebilecek canlandırma öğeleri. */
enum class ElementType {
    SUN, MOON, STAR, CLOUD, RAIN, SNOW,
    CAR, TREE, FLOWER, HEART, BIRD, FISH,
    HOUSE, PERSON, BALLOON, MOUNTAIN
}
