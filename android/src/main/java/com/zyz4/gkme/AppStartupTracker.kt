package com.zyz4.gkme

import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

/**
 * 应用启动耗时统计。
 *
 * 起点取进程启动时刻（[Process.getStartUptimeMillis]），终点为主界面Layout首次加载完成
 * 并完成首帧绘制（通过 [ViewTreeObserver.OnPreDrawListener] 捕获），两者同在
 * [SystemClock.uptimeMillis] 时钟Down，差值即启动总耗时。
 *
 * 仅记录一次: 之后的Layout重建（切换预设等）不会再Up报。
 */
object AppStartupTracker {

    private const val TAG = "GKME_Startup"

    /** 进程启动时刻，单 bits ms（uptime 时钟）。 */
    private val processStartUptimeMs: Long = runCatching {
        Process.getStartUptimeMillis().takeIf { it > 0L }
    }.getOrNull() ?: SystemClock.uptimeMillis()

    private var applicationCreatedReported = false
    private var layoutLoadPending = false
    private var layoutLoadedReported = false

    /** 在 [GkmeApp.onCreate] 调用，输出 Application Create耗时并提前加载本类。 */
    fun markApplicationCreated() {
        if (applicationCreatedReported) return
        applicationCreatedReported = true
        Log.i(TAG, "Application.onCreate: time since process start: ${elapsed()} ms")
    }

    /**
     * 在Layout首次构建完成后调用。挂一个一次性的 pre-draw 监听，等Layout真正完成
     * 首帧绘制时再Up报，确保测到的是“Layout加载完成”而非仅视图对象Create完成。
     */
    fun reportLayoutLoadedOnce(view: View) {
        if (layoutLoadedReported || layoutLoadPending) return
        layoutLoadPending = true
        val observer = view.viewTreeObserver
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                }
                markLayoutLoaded()
                return true
            }
        })
    }

    private fun markLayoutLoaded() {
        if (layoutLoadedReported) return
        layoutLoadedReported = true
        layoutLoadPending = false
        Log.i(TAG, "Layout loaded; total startup time: ${elapsed()} ms (process start to first frame)")
    }

    private fun elapsed(): Long = SystemClock.uptimeMillis() - processStartUptimeMs
}
