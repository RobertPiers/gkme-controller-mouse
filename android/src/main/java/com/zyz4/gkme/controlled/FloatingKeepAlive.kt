package com.zyz4.gkme.controlled

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Keep-alive overlay: Host运行时在屏幕Up挂一个 1×1 像素、完全透明且不可触摸的悬浮窗。
 *
 * 只要本进程持有一个系统级悬浮窗，系统就会把它视为“有可见窗口”的进程，
 * 从而在后台/熄屏时更不容易被内存或省电策略回收（国产 ROM 尤为如此）。
 *
 * 该方法不强制，由Settings里的「Keep-alive overlay”开关控制；需要 [Settings.canDrawOverlays] 权限，
 * 未授权时 [start] 会直接跳过，由界面引导用户授权。
 */
object FloatingKeepAlive {

    private const val TAG = "GKME_FloatingKeepAlive"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var view: View? = null

    /** 是否已获得“Display在其他应用Up层”权限。 */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context.applicationContext)

    fun isActive(): Boolean = view != null

    /** 开启Keep-alive overlay；需 [canDrawOverlays] 为 true，否则记录日志并Discard。 */
    fun start(context: Context) {
        val app = context.applicationContext
        if (!canDrawOverlays(app)) {
            Log.w(TAG, "Missing overlay permission; skipping keep-alive overlay")
            return
        }
        mainHandler.post {
            if (view != null) return@post
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val v = View(app)
            val params = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            try {
                wm.addView(v, params)
                view = v
                Log.i(TAG, "Keep-alive overlay active")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to add keep-alive overlay: ${t.message}")
            }
        }
    }

    /** CloseKeep-alive overlay并移除窗口。 */
    fun stop() {
        mainHandler.post {
            val v = view ?: return@post
            view = null
            val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            try {
                wm?.removeView(v)
                Log.i(TAG, "Keep-alive overlay stopped")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to remove keep-alive overlay: ${t.message}")
            }
        }
    }
}
