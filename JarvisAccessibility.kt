package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibility : AccessibilityService() {

    companion object {
        var active: JarvisAccessibility? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        // No automatic actions.
        // Actions run only after a user command.
    }

    override fun onInterrupt() {
        // Service interrupted.
    }

    override fun onDestroy() {
        if (active === this) {
            active = null
        }
        super.onDestroy()
    }

    // Find a visible button by its text.
    fun tapLabel(label: String): Boolean {

        if (label.isBlank()) return false

        val root = rootInActiveWindow ?: return false

        fun visit(node: AccessibilityNodeInfo): Boolean {

            val textMatch =
                node.text?.toString()
                    ?.contains(label, ignoreCase = true) == true

            val descriptionMatch =
                node.contentDescription?.toString()
                    ?.contains(label, ignoreCase = true) == true

            if (textMatch || descriptionMatch) {

                var target: AccessibilityNodeInfo? = node

                while (target != null) {

                    if (target.isClickable) {
                        return target.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )
                    }

                    target = target.parent
                }

                val rect = Rect()
                node.getBoundsInScreen(rect)

                if (!rect.isEmpty) {
                    return tap(
                        rect.centerX().toFloat(),
                        rect.centerY().toFloat()
                    )
                }
            }

            for (i in 0 until node.childCount) {

                val child = node.getChild(i)

                if (child != null && visit(child)) {
                    return true
                }
            }

            return false
        }

        return visit(root)
    }

    // Perform a screen tap.
    fun tap(x: Float, y: Float): Boolean {

        val path = Path().apply {
            moveTo(x, y)
        }

        val stroke =
            GestureDescription.StrokeDescription(
                path,
                0,
                80
            )

        val gesture =
            GestureDescription.Builder()
                .addStroke(stroke)
                .build()

        return dispatchGesture(
            gesture,
            null,
            null
        )
    }

    // Scroll screen.
    // up = true means scroll upwards.
    fun scroll(up: Boolean): Boolean {

        val display = resources.displayMetrics

        val x = display.widthPixels / 2f

        val startY = display.heightPixels *
            if (up) 0.35f else 0.75f

        val endY = display.heightPixels *
            if (up) 0.75f else 0.35f

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val stroke =
            GestureDescription.StrokeDescription(
                path,
                0,
                450
            )

        val gesture =
            GestureDescription.Builder()
                .addStroke(stroke)
                .build()

        return dispatchGesture(
            gesture,
            null,
            null
        )
    }
}
