package eu.hxreborn.pixelinjector.xposed.hook.system

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.view.Surface
import android.view.SurfaceControl
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.xposed.hook.system.original.ClassicCathodeRenderer
import eu.hxreborn.pixelinjector.xposed.hook.system.original.CoilResistance
import eu.hxreborn.pixelinjector.xposed.hook.system.original.CrtRenderer
import eu.hxreborn.pixelinjector.xposed.hook.system.original.CrtStyle
import eu.hxreborn.pixelinjector.xposed.hook.system.original.DeflectionDynamics
import eu.hxreborn.pixelinjector.xposed.hook.system.original.PhosphorOverdriveRenderer
import eu.hxreborn.pixelinjector.xposed.hook.system.original.StudioDeflectionRenderer
import java.lang.reflect.Constructor
import java.lang.reflect.Method

private const val SHADER_LAYER_Z = 32
private const val FRINGE_SPLIT_BOOST = 2.5f

/** Hidden `BLASTBufferQueue` calls, the same ones the stock fade uses to get a drawable Surface. */
internal class CrtShaderBindings(
    private val queueConstructor: Constructor<*>,
    private val createSurface: Method,
    private val destroyQueue: Method,
    val setBlastLayer: Method,
) {
    fun newQueue(
        layer: SurfaceControl,
        width: Int,
        height: Int,
    ): Any {
        val ints = intArrayOf(width, height, PixelFormat.TRANSLUCENT)
        var nextInt = 0
        val args =
            queueConstructor.parameterTypes.map { type ->
                when (type) {
                    String::class.java -> "CrtShader"
                    SurfaceControl::class.java -> layer
                    Int::class.javaPrimitiveType -> ints[nextInt++]
                    else -> error("unsupported constructor parameter $type")
                }
            }
        return queueConstructor.newInstance(*args.toTypedArray())
    }

    fun surfaceOf(queue: Any): Surface = createSurface.invoke(queue) as Surface

    fun destroy(queue: Any) {
        destroyQueue.invoke(queue)
    }
}

/**
 * Draws the standalone app's own renderers (shaders and all) onto one layer, from the captured
 * screen, so the look matches the original instead of an approximation built from layers.
 */
internal class CrtShaderLayer private constructor(
    val layer: SurfaceControl,
    private val bindings: CrtShaderBindings,
    private val queue: Any,
    private val surface: Surface,
    private val bitmap: Bitmap,
    private val renderer: CrtRenderer,
    private val width: Float,
    private val height: Float,
) {
    var frames = 0
        private set

    fun draw(progress: Float): Boolean =
        try {
            val canvas = surface.lockHardwareCanvas()
            try {
                canvas.drawColor(Color.BLACK)
                renderer.draw(canvas, width, height, progress)
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            frames++
            true
        } catch (error: Exception) {
            crtTrace("shader frame failed reason=${error.reason()} detail=${error.message}")
            false
        }

    fun release() {
        runCatching { renderer.release() }
        runCatching { surface.release() }
        runCatching { layer.release() }
        runCatching { bindings.destroy(queue) }
        runCatching { bitmap.recycle() }
    }

    companion object {
        @Suppress("LongParameterList")
        fun create(
            bindings: CrtShaderBindings,
            container: SurfaceControl,
            buffer: HardwareBuffer,
            width: Int,
            height: Int,
            params: CrtParams,
            density: Float,
            transaction: SurfaceControl.Transaction,
        ): CrtShaderLayer? {
            var layer: SurfaceControl? = null
            var queue: Any? = null
            var surface: Surface? = null
            var bitmap: Bitmap? = null
            return try {
                val wrapped =
                    Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
                        ?: throw IllegalStateException("wrapHardwareBuffer returned null")
                bitmap = wrapped
                val renderer =
                    rendererFor(params, wrapped, density)
                        ?: throw IllegalStateException("no renderer for ${params.mode}")
                val created =
                    SurfaceControl
                        .Builder()
                        .setName("ColorFade CRT shader")
                        .also { bindings.setBlastLayer.invoke(it) }
                        .setParent(container)
                        .build()
                layer = created
                val newQueue = bindings.newQueue(created, width, height)
                queue = newQueue
                val newSurface = bindings.surfaceOf(newQueue)
                surface = newSurface
                transaction
                    .setLayer(created, SHADER_LAYER_Z)
                    .setAlpha(created, 0f)
                    .setVisibility(created, true)
                crtTrace("shader renderer ready mode=${params.mode}")
                CrtShaderLayer(
                    created,
                    bindings,
                    newQueue,
                    newSurface,
                    wrapped,
                    renderer,
                    width.toFloat(),
                    height.toFloat(),
                )
            } catch (error: Exception) {
                crtTrace(
                    "shader renderer unavailable reason=${error.reason()} detail=${error.message}",
                )
                runCatching { surface?.release() }
                runCatching { layer?.release() }
                queue?.let { runCatching { bindings.destroy(it) } }
                runCatching { bitmap?.recycle() }
                null
            }
        }

        private fun rendererFor(
            params: CrtParams,
            bitmap: Bitmap,
            density: Float,
        ): CrtRenderer? {
            val style =
                if (params.vertical) CrtStyle.CLASSIC_VERTICAL else CrtStyle.CLASSIC_HORIZONTAL
            return when (params.mode) {
                CrtMode.CLASSIC -> ClassicCathodeRenderer(bitmap, style, density)
                CrtMode.STUDIO ->
                    StudioDeflectionRenderer(
                        bitmap,
                        style,
                        density,
                        if (params.curve == CrtCurve.STANDARD) {
                            DeflectionDynamics.STANDARD
                        } else {
                            DeflectionDynamics.STYLIZED_CREEP
                        },
                    )
                CrtMode.PHOSPHOR ->
                    PhosphorOverdriveRenderer(
                        screenBitmap = bitmap,
                        style = style,
                        density = density,
                        effectiveDurationMs = params.collapseMs,
                        totalAnimDuration = params.totalMs,
                        flashIntensity = params.flash,
                        afterglowMs = params.afterglowMs,
                        coilResistance = coilFor(params.curve),
                        enableMagneticStretch = true,
                        enableAnamorphicFlare = true,
                        colorSplitBoost = if (params.fringe) FRINGE_SPLIT_BOOST else 1f,
                    )
                CrtMode.DEFAULT -> null
            }
        }
    }
}

internal fun coilFor(curve: CrtCurve): CoilResistance =
    when (curve) {
        CrtCurve.STANDARD, CrtCurve.CREEP -> CoilResistance.CLASSIC
        CrtCurve.HEAVY -> CoilResistance.HEAVY
        CrtCurve.EXTREME -> CoilResistance.EXTREME
    }
