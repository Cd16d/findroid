package dev.jdtech.jellyfin.core.presentation.components

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

const val MAX_PARTICLES = 1000

class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vecX: Float = 0f,
    var vecY: Float = 0f,
    var velocity: Float = 0f,
    var radiusDp: Float = 0.8f,
    var lifeTime: Float = 1500f,
    var currentTime: Float = 0f,
    var alphaTier: Float = 0.6f,
)

fun respawnParticle(
    p: Particle,
    width: Float,
    height: Float,
    random: Random,
    density: Float,
) {
    p.x = random.nextFloat() * width
    p.y = random.nextFloat() * height
    val angle = random.nextFloat() * (2f * Math.PI.toFloat())
    p.vecX = cos(angle)
    p.vecY = sin(angle)
    p.velocity = (8f + random.nextFloat() * 12f) * density
    p.radiusDp = 0.65f + random.nextFloat() * 0.35f
    p.lifeTime = 1000f + random.nextFloat() * 2000f
    p.currentTime = random.nextFloat() * p.lifeTime
    p.alphaTier =
        when (random.nextInt(3)) {
            0 -> 0.35f
            1 -> 0.65f
            else -> 1.0f
        }
}

fun particleCount(areaDp: Float): Int {
    return when {
        areaDp < 500f -> 10
        areaDp < 1_000f -> 25
        areaDp < 2_500f -> 50
        areaDp < 5_000f -> 85
        areaDp < 8_000f -> 125
        areaDp < 11_000f -> 165
        areaDp < 14_500f -> 210
        areaDp < 19_000f -> 270
        areaDp < 24_000f -> 340
        areaDp < 30_000f -> 430
        areaDp < 37_000f -> 530
        areaDp < 45_000f -> 640
        areaDp < 54_000f -> 770
        areaDp < 64_000f -> 910
        areaDp < 75_000f -> 1060
        areaDp < 88_000f -> 1230
        areaDp < 102_000f -> 1410
        areaDp < 118_000f -> 1600
        areaDp < 136_000f -> 1800
        else -> MAX_PARTICLES
    }
}
