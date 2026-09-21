package com.ramybaheeg.yetanotherwidget.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import com.ramybaheeg.yetanotherwidget.utils.isDarkTheme
import kotlin.math.min
import kotlin.math.roundToInt

object ImageHelper {
    fun ImageView.applyShadow(originalView: ImageView, factor: Float = 1f) {
        clearColorFilter()
        val cElevation = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            when (
                if (context.isDarkTheme()) {
                    com.ramybaheeg.yetanotherwidget.global.Preferences.textShadowDark
                } else {
                    com.ramybaheeg.yetanotherwidget.global.Preferences.textShadow
                }
            ) {
                0 -> 0f * factor
                1 -> 8f * factor
                2 -> 16f * factor
                else -> 0f * factor
            },
            resources.displayMetrics
        )

        if (originalView.drawable != null &&
            originalView.drawable.intrinsicWidth > 0 &&
            originalView.drawable.intrinsicHeight > 0
        ) {
            val source = originalView.drawable.toBitmap().copy(Bitmap.Config.ARGB_8888, false)
            val combined = Bitmap.createBitmap(source)
            val shadowBitmap = generateShadowBitmap(context, cElevation, source, factor)

            shadowBitmap?.let {
                val canvas = Canvas(combined)
                val rect = Rect()
                canvas.getClipBounds(rect)
                val padding = 2 * getBlurRadius(context, cElevation).toInt()
                rect.inset(-padding, -padding)
                canvas.save()
                canvas.clipRect(rect)
                canvas.drawBitmap(it, 0f, 2f, null)
                canvas.restore()
                setImageBitmap(combined)
            }
        }
    }

    /**
     * Build the icon shadow with the platform bitmap/canvas APIs instead of RenderScript.
     * BlurMaskFilter operates on the drawable alpha mask and is available throughout the
     * app's supported API range, so release builds no longer need RenderScript support JNI.
     */
    private fun generateShadowBitmap(
        context: Context,
        cElevation: Float,
        bitmap: Bitmap?,
        factor: Float
    ): Bitmap? {
        bitmap ?: return null

        val opacity = when (
            if (context.isDarkTheme()) {
                com.ramybaheeg.yetanotherwidget.global.Preferences.textShadowDark
            } else {
                com.ramybaheeg.yetanotherwidget.global.Preferences.textShadow
            }
        ) {
            0 -> 0f
            1 -> 0.8f * factor
            2 -> 1f * factor
            else -> 0f
        }.coerceIn(0f, 1f)

        val shadow = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        if (opacity <= 0f) return shadow

        val blurRadius = getBlurRadius(context, cElevation)
        val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (blurRadius > 0f) {
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }
        val offset = IntArray(2)
        val alphaMask = bitmap.extractAlpha(blurPaint, offset)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = (255f * opacity).roundToInt().coerceIn(0, 255)
        }

        Canvas(shadow).drawBitmap(
            alphaMask,
            offset[0].toFloat(),
            offset[1].toFloat(),
            shadowPaint
        )
        alphaMask.recycle()
        return shadow
    }

    private fun getBlurRadius(context: Context, customElevation: Float): Float {
        val maxElevation = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            24f,
            context.resources.displayMetrics
        )
        return min(25f * (customElevation / maxElevation), 25f)
    }
}
