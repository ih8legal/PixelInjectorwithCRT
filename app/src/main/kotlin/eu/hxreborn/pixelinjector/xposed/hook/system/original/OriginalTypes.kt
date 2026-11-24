// Ported from "CRT Screen Off Animation" by Typongtv, included with the owner's permission.
package eu.hxreborn.pixelinjector.xposed.hook.system.original

import android.graphics.Canvas

enum class CrtStyle {
    CLASSIC_HORIZONTAL, // Vertical collapse to line -> Horizontal snap to dot
    CLASSIC_VERTICAL    // Horizontal collapse to line -> Vertical snap to dot
}

enum class DeflectionDynamics(
    val label: String,
    val exponent: Float,
    val timeMultiplier: Float,
    val phaseSplit: Float,
    val subtitle: String
) {
    STANDARD(
        label = "Classic Standard",
        exponent = 2.0f,
        timeMultiplier = 0.70f,
        phaseSplit = 0.72f,
        subtitle = "Linear-quadratic collapse (2.0x) with immediate drop & snappy beam zip"
    ),
    STYLIZED_CREEP(
        label = "Stylized Creep",
        exponent = 4.5f,
        timeMultiplier = 1.0f,
        phaseSplit = 0.78f,
        subtitle = "Viscous inductive drag (4.5x) with sustained creep & sudden razor plunge"
    )
}

enum class CoilResistance(
    val label: String,
    val exponent: Float,
    val timeMultiplier: Float,
    val initialCreep: Float,
    val subtitle: String
) {
    CLASSIC("Classic (2.0x)", 2.0f, 1.0f, 0.05f, "Familiar Gingerbread / retro quadratic feel"),
    HEAVY("Heavy Inductance (4.0x)", 4.6f, 1.50f, 0.20f, "Heavy magnetic drag, viscous crawl & explosive drop"),
    EXTREME("Extreme Tension (6.0x)", 6.2f, 2.00f, 0.32f, "Sustained magnetic struggle, deep slow creep & guillotine plunge")
}

interface CrtRenderer {
    fun draw(canvas: Canvas, width: Float, height: Float, progress: Float)
    fun release() {}
}
