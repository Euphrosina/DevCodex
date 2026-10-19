package ai.devcodex.agent.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** 无障碍服务：屏幕内容快照 + 手势自动化（点击/滑动/返回/主页） */
class DcAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: DcAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null

        /** 屏幕文本快照（前 200 个可交互/文本节点） */
        fun snapshot(maxNodes: Int = 200): String {
            val svc = instance ?: return "ERROR: 无障碍服务未启用"
            val root = svc.rootInActiveWindow ?: return "(当前窗口无可读取内容)"
            val sb = StringBuilder()
            var count = 0
            fun walk(node: AccessibilityNodeInfo?, depth: Int) {
                if (node == null || count >= maxNodes) return
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val cls = node.className?.toString()?.substringAfterLast('.') ?: ""
                if (text?.isNotEmpty() == true || desc?.isNotEmpty() == true) {
                    val r = Rect().also { node.getBoundsInScreen(it) }
                    val label = when {
                        text?.isNotEmpty() == true -> text
                        else -> desc ?: ""
                    }
                    sb.appendLine("${" ".repeat(depth.coerceAtMost(4))}[$cls] \"$label\" @(${r.left},${r.top},${r.right},${r.bottom})")
                    count++
                }
                for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
            }
            walk(root, 0)
            return sb.toString().ifBlank { "(无文本节点)" }
        }

        fun tap(x: Float, y: Float): Boolean {
            val svc = instance ?: return false
            val path = Path().apply { moveTo(x, y); lineTo(x + 1f, y) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
                .build()
            return svc.dispatchGesture(gesture, null, Handler(Looper.getMainLooper()))
        }

        fun scroll(down: Boolean): Boolean {
            val svc = instance ?: return false
            val metrics = svc.resources.displayMetrics
            val cx = metrics.widthPixels / 2f
            val cy = metrics.heightPixels / 2f
            val path = Path().apply {
                moveTo(cx, if (down) cy + 300 else cy - 300)
                lineTo(cx, if (down) cy - 300 else cy + 300)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
                .build()
            return svc.dispatchGesture(gesture, null, Handler(Looper.getMainLooper()))
        }

        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        fun home(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        fun recents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
