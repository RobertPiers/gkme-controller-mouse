package com.zyz4.gkme.controlled

import android.content.Context
import android.util.Log
import com.zyz4.gkme.haptic.HapticArbiter
import com.zyz4.gkme.haptic.HapticSource
import com.zyz4.gkme.haptic.RichTapEngine
import com.zyz4.gkme.haptic.RichTapFrequency
import com.zyz4.gkme.haptic.RichTapHe
import com.zyz4.gkme.haptic.RichTapPrebaked

/**
 * App 侧对 HD Vibration（RichTap 隐藏 API）的封装。
 *
 * 直接在 app 进程内实例化 [RemoteHapticService] Driver RichTap: 调用方是前台 app 的 uid，
 * 不会被系统的“后台Vibration”策略丢弃，也不再依赖 Shizuku 授权。只有LocalSupported RichTap 隐藏
 * API 时 [isHapticReady] 才为 true；否则调用方应回退到普通 Vibrator 通路。
 */
object HapticInjector {

    private const val TAG = "GKME_HapticInjector"

    /**
     * 连续效果的单次Duration。
     *
     * 实测Local DynamicEffect 单次DurationUp限约 5000ms；另外厂商 HAL 的循环重播（loop=-1）
     * 在每圈衔接处有约 200ms 断点，而连续重投递新的 effect 可None缝衔接。因此这里用一段
     * 较短效果 + PhoneHdHaptics 定时重投递的方式实现持续Vibration。
     *
     * 取 1000ms: 单段更短，来源Stop后残留尾音更短；重投递由 PhoneHdHaptics.REFRESH_NS
     * 联动保持在其内。
     */
    internal const val CONTINUOUS_DURATION_MS = 1_000

    @Volatile
    var service: RemoteHapticService? = null
        private set

    /** Local是否Supported RichTap 隐藏 API。 */
    @Volatile
    var available: Boolean = false
        private set

    /** RichTap core 版本号。 */
    @Volatile
    var version: String = ""
        private set

    /** 当前 backend 类型（对齐 SDK `PlayerType`）: 0 None、1 TencentPerformer、2 RichTapPerformer。 */
    @Volatile
    var playerType: Int = 0
        private set

    /** 是否Supported type 2 的实时调参（`createHapticParameter`）。 */
    @Volatile
    var realtimeAdjust: Boolean = false
        private set

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        RichTapPrebaked.load(context)

        // 在 app 进程内直接Driver RichTap: 调用方是前台 app 的 uid，不会被系统的
        //“后台Vibration”策略（ignored_background）丢弃。需要 manifest 声明 richtap-api 共享库。
        val local = try {
            RemoteHapticService(context.applicationContext)
        } catch (t: Throwable) {
            Log.w(TAG, "In-process RichTap initialization error", t)
            null
        }
        if (local != null && local.available) {
            service = local
            refreshCapabilities(local)
            Log.i(TAG, "Using RichTap in app process: playerType=$playerType version=$version")
        }
        initialized = true
    }

    private fun refreshCapabilities(svc: RemoteHapticService) {
        available = try {
            svc.available
        } catch (t: Throwable) {
            Log.w(TAG, "Query for available failed", t)
            false
        }
        version = try {
            svc.version ?: ""
        } catch (_: Throwable) {
            ""
        }
        // 这些能力查询在不同机型/核心版本Up可能不可用，逐个兜底。
        playerType = try {
            svc.playerType
        } catch (_: Throwable) {
            0
        }
        realtimeAdjust = try {
            svc.supportsRealtimeAdjustment()
        } catch (_: Throwable) {
            false
        }
    }

    /** 是否可Down发 HD 效果。 */
    fun isHapticReady(): Boolean = service != null && available

    // ── 效果所有权 / 优先级仲裁 ──
    // 四条通路（Adaptive triggers > Audio到Vibration > Game vibration > Button vibration）共用同一个 HapticPlayer。
    // 由本对象集Center仲裁: 同一时刻只有一个来源Driver；高优先级可抢占低优先级，低优先级在更高
    // 来源播放期间不能 start（调用方据此静默而不回退系统Vibration）。这同时保证一方 stop() 不会
    // 误杀另一方正在播放的效果。

    private val arbiter = HapticArbiter()

    /** 当前是否允许 [source] Driver HD: 空闲，或优先级不低于当前来源（同级可Refresh）。 */
    fun canPlay(source: HapticSource): Boolean = arbiter.canPlay(source)

    /** 抢占当前来源: 空闲或优先级不低于当前时成功，并成为当前来源。 */
    fun acquire(source: HapticSource): Boolean = arbiter.acquire(source)

    /** 当前来源；空闲为 null。 */
    fun owner(): HapticSource? = arbiter.current()

    /** 仅当 [source] 为当前来源时清除所有权（不Stop正在播放的效果）。 */
    fun release(source: HapticSource) = arbiter.release(source)

    /** Down发一段 HE 1.0 效果。 */
    fun startPattern(json: String, loop: Int, interval: Int, amplitude: Int, freq: Int, source: HapticSource): Boolean {
        val svc = service ?: return false
        if (!available) return false
        if (!acquire(source)) return false
        val ok = try {
            svc.startPattern(json, loop, interval, amplitude, freq)
        } catch (t: Throwable) {
            Log.w(TAG, "startPattern failed", t)
            false
        }
        if (!ok) release(source)
        return ok
    }

    /**
     * Down发一段**使用自身参数**的 HE 1.0 效果（多事件分块，每个事件自带 Frequency 与
     * 4 点 Curve）。用None参 `start()`，不传全局 amplitude/freq，避免覆盖事件参数。
     *
     * 兼容: 若Local RichTap 实现没有 `startEffect` 通路，回退到 [startPattern]，至少能出声。
     */
    fun startEffect(json: String, source: HapticSource): Boolean {
        val svc = service ?: return false
        if (!available) return false
        if (!acquire(source)) return false
        val ok = try {
            svc.startEffect(json)
        } catch (t: Throwable) {
            Log.w(TAG, "startEffect failed; falling back to startPattern", t)
            false
        }
        val started = if (ok) {
            true
        } else {
            try {
                svc.startPattern(json, 1, 0, 255, RichTapFrequency.HE_AT_RESONANCE)
            } catch (t: Throwable) {
                Log.w(TAG, "startPattern fallback failed", t)
                false
            }
        }
        if (!started) release(source)
        return started
    }

    /**
     * type 2 专用: 实时调整当前效果的全局强度/频率（`createHapticParameter`），None需 stop+start。
     * [amplitude] 0-255 会换算成引擎的 0-100 强度。仅当 [realtimeAdjust] 为 true 时有效。
     */
    fun updateParameter(amplitude: Int, frequency: Int): Boolean {
        val svc = service ?: return false
        if (!available || !realtimeAdjust) return false
        val intensity = (amplitude.coerceIn(0, 255) * 100 / 255)
        return try {
            svc.updateParameter(intensity, frequency.coerceIn(0, 100))
        } catch (t: Throwable) {
            Log.w(TAG, "updateParameter failed", t)
            false
        }
    }

    /** 播放持续Vibration（游戏 rumble / Adaptive triggers / 音圈）。[amplitude] 0-255，[frequency] 0-100。
     *  [compensate] 为 true 时对幅度做幅度-频率补偿；游戏Size马达启发式通路传 false，直接透传强度。
     *  单次只投递一段效果，持续由 PhoneHdHaptics 定时重投递实现（见 continuous duration 注释）。 */
    fun startContinuous(
        amplitude: Int,
        frequency: Int,
        source: HapticSource,
        compensate: Boolean = true,
    ): Boolean {
        val amp = if (compensate) {
            RichTapEngine.compensate255(amplitude, frequency)
        } else {
            amplitude.coerceIn(0, 255)
        }
        return startPattern(
            RichTapHe.continuous(frequency, CONTINUOUS_DURATION_MS),
            // 单次播放；不要用 loop=-1（HAL 循环衔接有 ~200ms 断点）。
            1, 0, amp, frequency.coerceIn(0, 100), source,
        )
    }

    /** 播放短促点击（按键反馈）。[strength] 按频率做幅度-频率补偿后写入事件强度。 */
    fun playClick(strength: Int, frequency: Int, source: HapticSource): Boolean = startPattern(
        RichTapHe.click(RichTapEngine.compensate255(strength, frequency), frequency),
        1, 0, 255, frequency.coerceIn(0, 100), source,
    )

    /** None条件Stop当前效果（用户Close HD / 硬Stop）。 */
    fun stop() {
        try {
            service?.stop()
        } catch (_: Throwable) {
        }
        arbiter.clear()
    }

    /**
     * 仅当当前来源为 [source] 时Stop；否则不动，避免误杀别的来源正在播放的效果。
     * @return 是否实际Stop。
     */
    fun stopOwnedBy(source: HapticSource): Boolean {
        if (arbiter.current() !== source) return false
        stop()
        return true
    }

    fun statusText(): String {
        if (!available) return "This device does not support HD rumble"
        val type = when (playerType) {
            2 -> "type2 RichTap"
            1 -> "type1 Tencent"
            else -> "type?"
        }
        return "HD ready ($type)"
    }

    fun destroy() {
        service = null
        available = false
        playerType = 0
        realtimeAdjust = false
        initialized = false
    }
}
