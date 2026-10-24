package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibility : AccessibilityService() {

    companion object {
        var active: JarvisAccessibility? = null
            private set

        val instance: JarvisAccessibility?
            get() = active
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        active = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // स्क्रीन की जानकारी सेव नहीं की जाती।
    }

    override fun onInterrupt() {
        // No action required
    }

    override fun onDestroy() {
        if (active === this) active = null
        super.onDestroy()
    }

    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun openRecentApps(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun scroll(up: Boolean): Boolean {
        val action = if (up) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        val root = rootInActiveWindow ?: return false
        return scrollNode(root, action)
    }

    fun scrollDown(): Boolean = scroll(false)

    fun scrollUp(): Boolean = scroll(true)

    private fun scrollNode(
        node: AccessibilityNodeInfo,
        action: Int
    ): Boolean {
        if (node.isScrollable && node.performAction(action)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scrollNode(child, action)) return true
        }

        return false
    }

    fun tapLabel(label: String): Boolean {
        if (label.isBlank()) return false

        val root = rootInActiveWindow ?: return false
        return findAndClick(root, label)
    }

    private fun findAndClick(
        node: AccessibilityNodeInfo,
        label: String
    ): Boolean {

        val text = node.text?.toString().orEmpty()
        val description =
            node.contentDescription?.toString().orEmpty()

        if (
            text.contains(label, ignoreCase = true) ||
            description.contains(label, ignoreCase = true)
        ) {
            var target: AccessibilityNodeInfo? = node

            while (target != null) {
                if (
                    target.isClickable &&
                    target.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                ) {
                    return true
                }
                target = target.parent
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue

            if (findAndClick(child, label)) {
                return true
            }
        }

        return false
    }
}
