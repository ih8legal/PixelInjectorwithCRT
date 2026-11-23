package eu.hxreborn.pixelinjector.xposed.hook.system

import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Value
import kotlin.math.pow

private const val DEFAULT_PHASE_END = 0.5f
private const val CLASSIC_PHASE_END = 0.66f
private const val STUDIO_STANDARD_PHASE_END = 0.72f
private const val STUDIO_CREEP_PHASE_END = 0.78f
private const val PHOSPHOR_PHASE_END = 0.74f
private const val DEFAULT_OVERSCAN = 0.03f
private const val CLASSIC_OVERSCAN = 0.25f
private const val STUDIO_ZIP_EXPONENT = 2.2f
private const val PERCENT = 100f

/**
 * Look presets, ported from the standalone CRT Screen-Off app. The app draws a screenshot bitmap
 * on a canvas, while this hook only has SurfaceControl colour layers, so each preset reproduces
 * the timeline, easing and flash of the original but cannot scale, ghost or shade the screen.
 */
internal enum class CrtMode(
    val key: String,
) {
    /** The module's original collapse. */
    DEFAULT(Prefs.CRT_MODE_DEFAULT),

    /** Mode 1, Classic Cathode: 66/33 split, overscan, white flash crescendo, terminal fade. */
    CLASSIC(Prefs.CRT_MODE_CLASSIC),

    /** Mode 2, Studio Deflection: bezel-locked sweep, additive wash, tapered cyan beam zip. */
    STUDIO(Prefs.CRT_MODE_STUDIO),

    /** Mode 3, Phosphor Overdrive: late phosphor surge and a lingering afterglow dot. */
    PHOSPHOR(Prefs.CRT_MODE_PHOSPHOR),
    ;

    companion object {
        fun of(key: String): CrtMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** First-phase easing. STANDARD is u^2; the rest are the app's "creep then plunge" curves. */
internal enum class CrtCurve(
    val key: String,
    private val creepWeight: Float,
    private val creepExponent: Float,
    private val exponent: Float,
) {
    STANDARD(Prefs.CRT_CURVE_STANDARD, 0f, 1f, 2f),
    CREEP(Prefs.CRT_CURVE_CREEP, 0.14f, 1.2f, 4.5f),
    HEAVY(Prefs.CRT_CURVE_HEAVY, 0.20f, 1.25f, 4.6f),
    EXTREME(Prefs.CRT_CURVE_EXTREME, 0.32f, 1.25f, 6.2f),
    ;

    fun ease(u: Float): Float =
        (creepWeight * u.pow(creepExponent) + (1f - creepWeight) * u.pow(exponent)).coerceIn(0f, 1f)

    companion object {
        fun of(key: String): CrtCurve = entries.firstOrNull { it.key == key } ?: STANDARD
    }
}

/** Immutable settings snapshot, taken per surface so one animation never mixes values. */
internal class CrtParams(
    val mode: CrtMode,
    val vertical: Boolean,
    val curve: CrtCurve,
    val collapseMs: Long,
    afterglowMs: Long,
    val flash: Float,
    val fringe: Boolean,
    cyan: Boolean,
    val capture: Boolean,
) {
    /** Only Phosphor Overdrive lingers after the collapse, as in the app. */
    val afterglowMs: Long = if (mode == CrtMode.PHOSPHOR) afterglowMs else 0L
    val totalMs: Long get() = collapseMs + afterglowMs
    val collapseFraction: Float get() = collapseMs.toFloat() / totalMs

    /** Studio always zips with a cyan halo. */
    val cyan: Boolean = cyan || mode == CrtMode.STUDIO

    /** Where the first (collapse to a line) phase ends, as a fraction of the collapse. */
    val phaseEnd: Float
        get() =
            when (mode) {
                CrtMode.DEFAULT -> DEFAULT_PHASE_END
                CrtMode.CLASSIC -> CLASSIC_PHASE_END
                CrtMode.STUDIO ->
                    if (curve == CrtCurve.STANDARD) {
                        STUDIO_STANDARD_PHASE_END
                    } else {
                        STUDIO_CREEP_PHASE_END
                    }
                CrtMode.PHOSPHOR -> PHOSPHOR_PHASE_END
            }

    /** How far past the bezel the second phase starts, as a fraction of the width. */
    val overscan: Float
        get() =
            when (mode) {
                CrtMode.CLASSIC -> CLASSIC_OVERSCAN
                CrtMode.STUDIO -> 0f
                else -> DEFAULT_OVERSCAN
            }

    /** Remaining beam length in the second phase, 1 at the start and 0 at the end. */
    fun zipRemaining(v: Float): Float =
        if (mode == CrtMode.STUDIO) (1f - v).pow(STUDIO_ZIP_EXPONENT) else 1f - v * v
}

internal object CrtSettings {
    val mode = Value(Prefs.CRT_MODE) { CrtMode.of(it) }
    val style = Value(Prefs.CRT_STYLE) { it == Prefs.CRT_STYLE_VERTICAL }
    val curve = Value(Prefs.CRT_CURVE) { CrtCurve.of(it) }
    val duration =
        Value(Prefs.CRT_DURATION) { it.coerceIn(Prefs.CRT_DURATION_MIN, Prefs.CRT_DURATION_MAX) }
    val flash =
        Value(Prefs.CRT_FLASH) { it.coerceIn(Prefs.CRT_FLASH_MIN, Prefs.CRT_FLASH_MAX) / PERCENT }
    val afterglow =
        Value(Prefs.CRT_AFTERGLOW) {
            it.coerceIn(Prefs.CRT_AFTERGLOW_MIN, Prefs.CRT_AFTERGLOW_MAX)
        }
    val fringe = Switch(Prefs.CRT_FRINGE)
    val cyan = Switch(Prefs.CRT_CYAN)
    val capture = Switch(Prefs.CRT_CAPTURE)

    val values: List<Value<*, *>> =
        listOf(mode, style, curve, duration, flash, afterglow, fringe, cyan, capture)

    fun snapshot(): CrtParams =
        CrtParams(
            mode = mode.value,
            vertical = style.value,
            curve = curve.value,
            collapseMs = duration.value.toLong(),
            afterglowMs = afterglow.value.toLong(),
            flash = flash.value,
            fringe = fringe.enabled,
            cyan = cyan.enabled,
            capture = capture.enabled,
        )
}

/** Total animation length including any afterglow, for the SystemUI side's watchdog. */
internal fun crtAnimationDurationMs(): Long = CrtSettings.snapshot().totalMs
