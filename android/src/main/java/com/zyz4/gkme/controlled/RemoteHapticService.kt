package com.zyz4.gkme.controlled

import android.content.Context
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.zyz4.gkme.haptic.HeJson
import com.zyz4.gkme.haptic.RichTapRawCodec
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 高清Vibration（RichTap Dynamic效果）Driver实现。
 *
 * 普通 Kotlin 类（非 Binder/Shizuku 用户服务）: 由 [HapticInjector] 在 app 进程内直接实例化，
 * 以**前台 app 的 uid** 反射调用框架隐藏类（manifest 声明 `richtap-api` 共享库后可用），
 * 从而不被系统“后台Vibration”策略丢弃。
 * 按优先级选 backend（对齐 RichTap ASDK `RichTapUtils.init`）:
 * - **type 2 `RichTapPerformer`**: `richtap.os.PhonyVibrationEffect` / `android.os.RichTapVibrationEffect`
 *   的 `createPatternHeWithParam(int[])`，Supported实时调参（`createHapticParameter`）。
 * - **type 1 `TencentPerformer`**: `android.os.DynamicEffect` + `android.os.HapticPlayer`。
 * - 两者都不可用时 `available=false`，由调用方回退普通 `Vibrator`。
 *
 * type 2 的 raw int[] 编码见 [RichTapRawCodec]（逆向自 SDK `base.b.a`）。
 */
class RemoteHapticService @JvmOverloads constructor(
    @Suppress("unused") private val context: Context? = null,
) {

    companion object {
        private const val TAG = "GKME_RemoteHaptic"
        private const val DEFAULT_PACKAGE = "com.android.shell"

        const val PLAYER_NONE = 0
        const val PLAYER_TENCENT = 1
        const val PLAYER_RICHTAP = 2

        /** type 2 实时调参（createHapticParameter）的发送者组 id。 */
        private const val SENDER_GID = 1

        // createHapticParameter 头部标签（SDK `d.a` 常量）: 256=发送者块，513=强度，514=频率。
        private const val PARAM_TAG_SENDER = 256
        private const val PARAM_TAG_INTENSITY = 513
        private const val PARAM_TAG_FREQUENCY = 514
    }

    private val lock = Any()

    @Volatile
    var available = false
        private set

    @Volatile
    var playerType = PLAYER_NONE
        private set

    @Volatile
    var version: String = ""
        private set

    @Volatile
    private var packageName: String = DEFAULT_PACKAGE

    // ── type 1 资源 ──
    @Volatile
    private var player: Any? = null
    private var dynamicEffectClass: Class<*>? = null
    private var startMethod: Method? = null
    private var startNoArgMethod: Method? = null
    private var stopMethod: Method? = null
    private var packageField: Field? = null

    // ── type 2 资源 ──
    private var createPatternHeWithParam: Method? = null
    private var createHapticParameter: Method? = null
    private var createPatternHeParameter: Method? = null
    private var vibrator: Vibrator? = null
    private var coreMajor = -1
    private var coreMinor = -1

    private val pid = Process.myPid()
    private var senderSeq = 0

    init {
        synchronized(lock) {
            packageName = context?.packageName ?: DEFAULT_PACKAGE
            vibrator = try {
                context?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } catch (_: Throwable) {
                null
            }
            if (!tryInitType2()) {
                tryInitType1()
            }
            Log.i(TAG, "init: playerType=$playerType available=$available version=$version pkg=$packageName")
        }
    }

    // ── 能力查询 ──

    /** 是否Supported type 2 实时调参（`createHapticParameter`）。 */
    fun supportsRealtimeAdjustment(): Boolean = playerType == PLAYER_RICHTAP && coreMajor >= 32

    // ── 效果Down发 ──

    fun startPattern(
        heJson: String,
        loop: Int,
        interval: Int,
        amplitude: Int,
        freq: Int,
    ): Boolean {
        if (!available) return false
        return if (playerType == PLAYER_RICHTAP) {
            startRichtap(heJson, loop, interval, amplitude, freq)
        } else {
            startTencent(heJson, loop, interval, amplitude, freq)
        }
    }

    fun startEffect(heJson: String): Boolean {
        if (!available) return false
        return if (playerType == PLAYER_RICHTAP) {
            // 事件自带参数: None全局覆盖（对齐 SDK start() 的 1,0,gain,0）。
            startRichtap(heJson, 1, 0, 255, 0)
        } else {
            startTencentEffect(heJson)
        }
    }

    fun updateParameter(intensity: Int, frequency: Int): Boolean {
        if (!available || playerType != PLAYER_RICHTAP) return false
        if (coreMajor < 32) return false
        val method = createHapticParameter ?: return false
        val vib = vibrator ?: return false
        val i = intensity.coerceIn(0, 100)
        val f = frequency.coerceIn(0, 100)
        synchronized(lock) {
            return try {
                val params = intArrayOf(
                    PARAM_TAG_SENDER, pid, nextSid(),
                    PARAM_TAG_INTENSITY, i,
                    PARAM_TAG_FREQUENCY, f,
                )
                val effect = method.invoke(null, params, params.size) as VibrationEffect
                vib.vibrate(effect)
                Log.i(TAG, "updateParameter intensity=$i freq=$f")
                true
            } catch (t: Throwable) {
                Log.w(TAG, "updateParameter failed", t)
                false
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            when (playerType) {
                PLAYER_RICHTAP -> stopRichtapLocked()
                PLAYER_TENCENT -> stopTencentLocked()
                else -> Unit
            }
        }
    }

    // ── type 2: RichTapVibrationEffect / PhonyVibrationEffect ──

    private fun tryInitType2(): Boolean {
        val cls = tryClass("richtap.os.PhonyVibrationEffect")
            ?: tryClass("android.os.RichTapVibrationEffect")
            ?: return false
        return try {
            val check = cls.getMethod("checkIfRichTapSupport").invoke(null) as? Int ?: return false
            // SDK 约定: Back 2 表示Unsupported；Back 1 表示框架接了 RichTap 但None版本号。
            if (check == 2) {
                Log.w(TAG, "RichTapVibrationEffect exists but checkIfRichTapSupport=2 (unsupported)")
                return false
            }
            if (check != 1) {
                coreMajor = (check shr 8) and 0xFF
                coreMinor = check and 0xFF
            }
            if (coreMajor <= 0) {
                Log.w(TAG, "RichTapVibrationEffect check=$check has no valid version; falling back to type 1")
                return false
            }
            val int = Int::class.javaPrimitiveType!!
            createPatternHeWithParam = cls.getMethod(
                "createPatternHeWithParam",
                IntArray::class.java, int, int, int, int,
            )
            createHapticParameter = cls.getMethod("createHapticParameter", IntArray::class.java, int)
            createPatternHeParameter = cls.getMethod("createPatternHeParameter", int, int, int)
            playerType = PLAYER_RICHTAP
            available = vibrator != null
            version = "RichTap core $coreMajor.$coreMinor"
            if (vibrator == null) Log.w(TAG, "type 2 available but no Vibrator instance")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to initialize type 2: ${t.message}")
            false
        }
    }

    private fun startRichtap(heJson: String, loop: Int, interval: Int, amplitude: Int, freq: Int): Boolean {
        val method = createPatternHeWithParam ?: return false
        val vib = vibrator ?: return false
        val events = HeJson.parseHe10(heJson)
        if (events.isNullOrEmpty()) {
            Log.w(TAG, "type 2 HE parsing failed")
            return false
        }
        // SDK: core ≤ 23 走 HE1.0，否则包成单个 pattern 走 HE2.0（带 senderId，便于实时调参）。
        val heVersion = if (coreMajor <= 23) 1 else 2
        val raw = RichTapRawCodec.encode(
            events = events,
            heVersion = heVersion,
            coreMajorRichTap = coreMajor,
            minorRichTap = coreMinor,
            pid = pid,
            sid = nextSid(),
            // LeftRight马达交换未接入 UI；保留 SDK 的默认（不交换）。
            swapVibrationIndex = false,
        ) ?: return false
        synchronized(lock) {
            return try {
                val effect = method.invoke(
                    null, raw, loop, interval, amplitude.coerceIn(0, 255), freq.coerceIn(0, 100),
                ) as VibrationEffect
                vib.vibrate(effect)
                Log.i(TAG, "startRichtap heV=$heVersion raw=${raw.size} loop=$loop interval=$interval amp=$amplitude freq=$freq")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "startRichtap failed", t)
                false
            }
        }
    }

    private fun stopRichtapLocked() {
        val method = createPatternHeParameter ?: return
        val vib = vibrator ?: return
        try {
            val effect = method.invoke(null, 0, 0, 0) as VibrationEffect
            vib.vibrate(effect)
        } catch (t: Throwable) {
            Log.w(TAG, "stopRichtap failed: ${t.message}")
        }
    }

    // ── type 1: DynamicEffect + HapticPlayer ──

    private fun tryInitType1() {
        try {
            val hp = Class.forName("android.os.HapticPlayer")
            val hpAvailable = try {
                hp.getMethod("isAvailable").invoke(null) as? Boolean ?: false
            } catch (t: Throwable) {
                Log.w(TAG, "isAvailable call failed", t)
                false
            }
            if (!hpAvailable) {
                available = false
                playerType = PLAYER_NONE
                return
            }
            dynamicEffectClass = Class.forName("android.os.DynamicEffect")
            startMethod = hp.getMethod(
                "start",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            stopMethod = hp.getMethod("stop")
            startNoArgMethod = hp.getMethod("start")
            // app_process Down该字段为 null 需手动补齐；app 进程内它是 blocked 字段（拿不到），
            // 但厂商构造函数会自动填好，因此这里允许为 null。
            packageField = try {
                hp.getDeclaredField("mPackageName").apply { isAccessible = true }
            } catch (t: Throwable) {
                Log.i(TAG, "mPackageName field inaccessible (using default in app process): ${t.message}")
                null
            }
            playerType = PLAYER_TENCENT
            available = true
            version = try {
                hp.getMethod("getVersion").invoke(null) as? String ?: ""
            } catch (_: Throwable) {
                ""
            }
        } catch (t: Throwable) {
            available = false
            playerType = PLAYER_NONE
            Log.w(TAG, "Hidden API unavailable: ${t.message}")
        }
    }

    private fun startTencent(heJson: String, loop: Int, interval: Int, amplitude: Int, freq: Int): Boolean {
        val de = dynamicEffectClass ?: return false
        val start = startMethod ?: return false
        synchronized(lock) {
            return try {
                // 必须先 stop 旧 effect: 实测“不 stop 直接 start”不会替换旧 track，
                // 幅度/频率更新会被忽略（Vibration卡在首帧）。stop+start 的重投递才是None缝的。
                stopTencentLocked()
                val effect = de.getMethod("create", String::class.java).invoke(null, heJson)
                val hp = Class.forName("android.os.HapticPlayer")
                val newPlayer = hp.getConstructor(de).newInstance(effect)
                // app_process Down mPackageName 为 null 需补齐；app 进程内它是 blocked 字段，
                // 拿不到时跳过（厂商构造函数会用当前包名填好）。
                packageField?.set(newPlayer, packageName)
                start.invoke(newPlayer, loop, interval, amplitude, freq)
                player = newPlayer
                Log.i(TAG, "startTencent loop=$loop interval=$interval amp=$amplitude freq=$freq")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "startTencent failed", t)
                false
            }
        }
    }

    private fun startTencentEffect(heJson: String): Boolean {
        val de = dynamicEffectClass ?: return false
        val start = startNoArgMethod ?: return false
        synchronized(lock) {
            return try {
                // 多事件分块效果: 事件自带 Parameters/Curve，用None参 start() 避免被全局
                // amplitude/freq 覆盖。
                //
                // **不要 stop 旧 player**: 真机实测（加速度计，见 docs/richtap-hd-vibration.md §13）
                // 新 player 的 start() 会直接接管当前 track；而 stop() 是**全局**Cancel（停旧会连新
                // 一起停，`chain_single_stop` 实测停旧后整段静音）。因此流式分块改为「None stop 链接”，
                // 只在整条流真正结束时由 [stop] 停一次。旧 player 对象随作用域被回收不影响播放
                //（与 `AccStream` 探针一致）。
                val effect = de.getMethod("create", String::class.java).invoke(null, heJson)
                val hp = Class.forName("android.os.HapticPlayer")
                val newPlayer = hp.getConstructor(de).newInstance(effect)
                packageField?.set(newPlayer, packageName)
                start.invoke(newPlayer)
                player = newPlayer
                Log.i(TAG, "startTencentEffect chars=${heJson.length}")
                true
            } catch (t: Throwable) {
                Log.e(TAG, "startTencentEffect failed", t)
                false
            }
        }
    }

    private fun stopTencentLocked() {
        val p = player ?: return
        try {
            stopMethod?.invoke(p)
        } catch (t: Throwable) {
            // 厂商实现里 stop() 可能会打印一个 NPE 栈，但不影响Stop。
            Log.w(TAG, "stopTencent exception: ${t.message}")
        } finally {
            player = null
        }
    }

    private fun nextSid(): Int {
        senderSeq = (senderSeq + 1) and 0xFFFF
        return (SENDER_GID shl 16) or senderSeq
    }

    private fun tryClass(name: String): Class<*>? = try {
        Class.forName(name)
    } catch (_: Throwable) {
        null
    }
}
