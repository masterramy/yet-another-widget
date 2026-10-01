package com.ramybaheeg.yetanotherwidget

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Q1-only launcher harness. It injects one coherent touchscreen pointer stream globally. */
@RunWith(AndroidJUnit4::class)
class LauncherWidgetDragTest {
    @Test
    fun injectWidgetDrag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()

        fun coordinate(name: String): Float = requireNotNull(args.getString(name)) {
            "Missing instrumentation argument: $name"
        }.toFloat()

        val sourceX = coordinate("sourceX")
        val sourceY = coordinate("sourceY")
        val edgeY = coordinate("edgeY")
        val targetX = coordinate("targetX")
        val targetY = coordinate("targetY")
        val ui = instrumentation.uiAutomation
        val downTime = SystemClock.uptimeMillis()

        fun inject(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                x,
                y,
                0
            )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue("UiAutomation rejected MotionEvent action=$action x=$x y=$y", ui.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }

        fun movePath(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int, delayMs: Long) {
            for (step in 1..steps) {
                val fraction = step.toFloat() / steps.toFloat()
                inject(
                    MotionEvent.ACTION_MOVE,
                    fromX + (toX - fromX) * fraction,
                    fromY + (toY - fromY) * fraction
                )
                SystemClock.sleep(delayMs)
            }
        }

        inject(MotionEvent.ACTION_DOWN, sourceX, sourceY)
        SystemClock.sleep(1600)
        movePath(sourceX, sourceY, targetX, edgeY, steps = 24, delayMs = 30)
        SystemClock.sleep(800)
        movePath(targetX, edgeY, targetX, targetY, steps = 12, delayMs = 35)
        SystemClock.sleep(300)
        inject(MotionEvent.ACTION_UP, targetX, targetY)
        SystemClock.sleep(1000)
    }
}
