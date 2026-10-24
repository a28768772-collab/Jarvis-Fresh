package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibility : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibility? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // स्क्रीन की जानकारी रिकॉर्ड या सेव नहीं की जाती।
    }

    override fun onInterrupt() {
        // सर्विस रुकने पर कोई अतिरिक्त काम नहीं।
    }

    override fun onDestroy() {
        instance = null
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

    fun scrollDown(): Boolean {
        return scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollUp(): Boolean {
        return scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun scroll(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return scrollNode(root, action)
    }

    private fun scrollNode(
        node: AccessibilityNodeInfo,
        action: Int
    ): Boolean {
        if (node.isScrollable && node.performAction(action)) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue

            if (scrollNode(child, action)) {
                return true
            }
        }

        return false
    }
}
