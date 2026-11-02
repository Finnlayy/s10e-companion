package dev.s10e.companion.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

class PhoneControlService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only use the service for active requests (gestures, view walking), no passive listening needed.
    }

    override fun onInterrupt() {
        // No-op
    }
    
    suspend fun tap(x: Float, y: Float): String {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
            
        val deferred = CompletableDeferred<String>()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete("Tapped at $x, $y")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete("Tap cancelled")
            }
        }, null)
        
        return deferred.await()
    }
    
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): String {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
            
        val deferred = CompletableDeferred<String>()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                deferred.complete("Swiped")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                deferred.complete("Swipe cancelled")
            }
        }, null)
        
        return deferred.await()
    }
    
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "Kein aktives Fenster lesbar"
        val sb = java.lang.StringBuilder()
        
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()?.takeUnless { node.isPassword }
            val desc = node.contentDescription?.toString()
            
            if (!text.isNullOrBlank()) {
                sb.append("Text: $text\n")
            } else if (!desc.isNullOrBlank()) {
                sb.append("Element: $desc\n")
            }
            
            for (i in 0 until node.childCount) {
                walk(node.getChild(i))
            }
            node.recycle()
        }
        
        walk(root)
        val res = sb.toString()
        return if (res.isBlank()) "Bildschirm ist leer" else res
    }
    
    fun typeText(text: String): String {
        // Without root, standard accessibility services cannot type arbitrary text outside of 
        // nodes that explicitly support ACTION_SET_TEXT. 
        // A full keyboard replacement is out of scope. We try to find a focused editable node.
        val root = rootInActiveWindow ?: return "Kein aktives Fenster"
        var found = false
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || found) return
            if (node.isFocused && node.isEditable) {
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                found = true
            }
            if (!found) {
                for (i in 0 until node.childCount) {
                    walk(node.getChild(i))
                }
            }
            node.recycle()
        }
        walk(root)
        return if (found) "Text eingegeben" else "Kein fokussiertes Eingabefeld gefunden"
    }

    companion object {
        var instance: PhoneControlService? = null
            private set
    }
}
