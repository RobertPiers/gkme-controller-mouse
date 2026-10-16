package com.zyz4.gkme.controlled

import android.content.Context
import android.util.Log
import com.zyz4.gkme.input.SdlNative
import com.zyz4.gkme.input.VirtualGamepad
import com.zyz4.gkme.model.GamepadState
import com.zyz4.gkme.proto.GamepadInput

/**
 * App 侧对 Shizuku UserService 的封装: 负责把虚拟ControllerStatus转发到用户服务进程。
 *
 * 授权/绑定的通用逻辑在 [ShizukuServiceBinding] Center（单独文件）；本文件只保留Controller相关Status。
 * 只有在 Shizuku 运行且Authorized、并且用户服务成功打开 /dev/uinput 后，[ensureReady] 才Back true。
 */
object GamepadInjector {

    private const val TAG = "GKME_GamepadInjector"

    const val SHIZUKU_PACKAGE = ShizukuServiceBinding.SHIZUKU_PACKAGE
    const val SHIZUKU_DOWNLOAD_URL = ShizukuServiceBinding.SHIZUKU_DOWNLOAD_URL

    @Volatile
    var service: IGamepadService? = null
        private set

    @Volatile
    private var created = false

    @Volatile
    private var lastError: String? = null

    /** 当前虚拟Controller的配置，用于判断是否需要重建。 */
    @Volatile
    private var rumbleEnabled = true

    @Volatile
    private var mouseEnabled = true

    /** 当前虚拟Controller后端（0 = uinput，1 = uhid）与 uhid 身份（1 = DS4，2 = DualSense，3 = Switch Pro）。 */
    @Volatile
    private var backend = 0

    @Volatile
    private var profile = 1

    /** Virtual key盘/Mouse的CreateStatus；虚拟ControllerCreate成功后立即Create。 */
    @Volatile
    private var keyboardCreated = false

    @Volatile
    private var keyboardFailed = false

    @Volatile
    private var mouseCreated = false

    @Volatile
    private var mouseFailed = false

    private var binding: ShizukuServiceBinding? = null

    /** 根据当前环境推断用户Down一步需要执行的操作。 */
    enum class Action { DOWNLOAD, OPEN, REQUEST_PERMISSION, NONE }

    val binderAlive: Boolean get() = binding?.binderAlive == true
    val permissionGranted: Boolean get() = binding?.permissionGranted == true
    val permissionDenied: Boolean get() = binding?.permissionDenied == true

    /** 用户服务长时间None法Connect（疑似 Shizuku 服务Status异常，需重启手机）。 */
    val bindFailed: Boolean get() = binding?.bindFailed == true

    @Synchronized
    fun init(context: Context) {
        if (binding != null) return
        val b = ShizukuServiceBinding(
            tag = TAG,
            processNameSuffix = "gkme_remote_input",
            serviceClass = RemoteGamepadService::class.java,
            onConnected = { binder ->
                service = IGamepadService.Stub.asInterface(binder)
                setCreated(false)
                lastError = null
            },
            onDisconnected = {
                service = null
                setCreated(false)
                resetKeyboardMouse()
            },
        )
        binding = b
        b.init(context)
    }

    fun requestPermission(force: Boolean = false) {
        binding?.requestPermission(force)
    }

    /**
     * 同步当前SettingsCenter的虚拟Controller类型（由 ConnectionManager 在Settings变化时调用）。
     *
     * 若虚拟Controller当前已Create，会立即销毁并按新配置重建，保证切换设备类型后及时生效，
     * None需等到Down次 [ensureReady]。
     */
    @Synchronized
    fun configure(backendId: Int, profileId: Int) {
        if (backend == backendId && profile == profileId) return
        backend = backendId
        profile = profileId
        val svc = service
        if (!created || svc == null) return
        release()
        try {
            val r = svc.create(backend, profile, rumbleEnabled)
            if (r == 0) {
                setCreated(true)
                createKeyboardMouse(svc)
                lastError = null
            } else {
                lastError = "Failed to create virtual controller (code=$r); you may need to start Shizuku as root"
            }
        } catch (t: Throwable) {
            setCreated(false)
            lastError = "Error creating virtual controller: ${t.message}"
        }
    }

    fun ensureBound() {
        binding?.ensureBound()
    }

    /**
     * 确保Virtual controller ready。若 Shizuku is not running/未授权/未绑定，则Back false 并触发相应流程，
     * 调用方可稍后重试。
     *
     * @param rumble 是否把虚拟Controller的Vibration数据转发回控制端/手机。FF 能力始终暴露（系统
     *   与游戏仍视其为带Vibration的设备）；Local模式传 false 以忽略Vibration数据，避免Phone motor
     *   与虚拟Controller之间形成死循环。
     * @param mouse 是否Create虚拟Mouse。Local模式默认启用，便于物理Controller的Mouse模式；调用方
     *   可以在确实需要避免触摸冲突时Close它。
     */
    @Synchronized
    fun ensureReady(
        rumble: Boolean = true,
        mouse: Boolean = true,
        backendId: Int = backend,
        profileId: Int = profile,
    ): Boolean {
        val b = binding
        if (b == null || !b.binderAlive) {
            lastError = "Shizuku is not running"
            return false
        }
        if (!b.permissionGranted) {
            if (b.permissionDenied) {
                lastError = b.lastError ?: "Shizuku permission denied"
            }
            b.requestPermission()
            return false
        }
        val svc = service
        if (svc == null) {
            lastError = "Starting Shizuku user service…"
            b.ensureBound()
            return false
        }
        if (created) {
            if (rumbleEnabled == rumble && mouseEnabled == mouse &&
                backend == backendId && profile == profileId
            ) {
                return true
            }
            // 配置发生变化（例如切换后端/身份、从Host切到Local模式）: 销毁后按新配置重建。
            release()
        }
        rumbleEnabled = rumble
        mouseEnabled = mouse
        backend = backendId
        profile = profileId
        return try {
            val r = svc.create(backendId, profileId, rumble)
            if (r == 0) {
                setCreated(true)
                createKeyboardMouse(svc)
                lastError = null
                true
            } else {
                lastError = "Failed to create virtual controller (code=$r); you may need to start Shizuku as root"
                false
            }
        } catch (t: Throwable) {
            lastError = "Error creating virtual controller: ${t.message}"
            false
        }
    }

    fun isReady(): Boolean = binderAlive && permissionGranted && service != null && created

    fun release() {
        val svc = service
        if (svc != null) {
            if (keyboardCreated || mouseCreated) {
                try {
                    svc.releaseKeyboardMouse()
                } catch (_: Throwable) {
                }
            }
            if (created) {
                try {
                    svc.release()
                } catch (_: Throwable) {
                }
            }
        }
        setCreated(false)
        resetKeyboardMouse()
    }

    /**
     * 更新虚拟Controller的CreateStatus，并同步“隐藏 GKME 自身虚拟Controller”的开关。
     *
     * SDL 会改写设备名，且 uhid Controller复用真实Controller的 vendor/product，所以排除只能按
     * vendor/product，且仅在虚拟Controller确实运行时启用，避免隐藏同型号的真实Controller。
     */
    private fun setCreated(value: Boolean) {
        created = value
        try {
            VirtualGamepad.virtualGamepadActive = value
            SdlNative.nativeSetVirtualGamepadExclusion(value)
        } catch (_: Throwable) {
        }
    }

    /** ResetVirtual key盘/Mouse的懒CreateStatus（服务Disconnect或释放后调用）。 */
    private fun resetKeyboardMouse() {
        keyboardCreated = false
        keyboardFailed = false
        mouseCreated = false
        mouseFailed = false
    }

    fun update(input: GamepadInput) {
        val svc = service ?: return
        if (!created) return
        // 数字Trigger只在 buttons 里置 bits（bit6/7）而不发Trigger数值，这里按电脑端
        // GamepadSession 的逻辑回退: 数值为 0 但对应 bitsPress时按 255 处理。
        val buttons = input.buttons.toInt()
        val leftTrigger = if (input.leftTrigger > 0) {
            input.leftTrigger
        } else if (buttons and GamepadState.LT != 0) {
            255
        } else {
            0
        }
        val rightTrigger = if (input.rightTrigger > 0) {
            input.rightTrigger
        } else if (buttons and GamepadState.RT != 0) {
            255
        } else {
            0
        }
        try {
            svc.update(
                toXInputButtons(input),
                leftTrigger,
                rightTrigger,
                input.leftStickX,
                input.leftStickY,
                input.rightStickX,
                input.rightStickY,
                input.gyroX,
                input.gyroY,
                input.gyroZ,
                input.accelX,
                input.accelY,
                input.accelZ,
                buildTouches(input),
            )
        } catch (_: Throwable) {
        }
        updateKeyboard(svc, input)
        if (mouseEnabled) updateMouse(svc, input)
    }

    /**
     * 虚拟ControllerCreate成功后立即CreateVirtual key盘与（可选）虚拟Mouse，避免首次输入到达时
     * 才Create造成的延迟。Create失败则本次会话不再重试（与 GKME-Windows 的
     * [MarkKeyboardMouseFailed] 行为一致）。
     */
    private fun createKeyboardMouse(svc: IGamepadService) {
        if (!keyboardCreated && !keyboardFailed) {
            val r = try {
                svc.createKeyboard()
            } catch (_: Throwable) {
                -1
            }
            if (r == 0) {
                keyboardCreated = true
            } else {
                keyboardFailed = true
            }
        }
        if (mouseEnabled && !mouseCreated && !mouseFailed) {
            val r = try {
                svc.createMouse()
            } catch (_: Throwable) {
                -1
            }
            if (r == 0) {
                mouseCreated = true
            } else {
                mouseFailed = true
            }
        }
    }

    /** 转发一帧Keyboard全量Status。 */
    private fun updateKeyboard(svc: IGamepadService, input: GamepadInput) {
        if (!keyboardCreated) return
        try {
            svc.updateKeyboard(input.keyboardModifiers, input.pressedScanCodesList.toIntArray())
        } catch (_: Throwable) {
        }
    }

    /** 转发一帧MouseStatus（相对 bits移 + 滚轮 + 按键）。 */
    private fun updateMouse(svc: IGamepadService, input: GamepadInput) {
        if (!mouseCreated) return
        try {
            svc.updateMouse(
                input.mouseDx,
                input.mouseDy,
                input.mouseWheel,
                input.mousePan,
                input.mouseButtons,
            )
        } catch (_: Throwable) {
        }
    }

    /** Back高 16  bitsLeft马达、低 16  bitsRight马达（0..65535），NoneVibration为 0。 */
    fun pumpRumble(): Long {
        val svc = service ?: return 0L
        if (!created) return 0L
        return try {
            svc.pumpRumble()
        } catch (_: Throwable) {
            0L
        }
    }

    fun statusText(): String {
        val b = binding ?: return "Shizuku is not running"
        return when {
            !b.binderAlive -> "Shizuku is not running"
            !b.permissionGranted ->
                if (b.permissionDenied) (b.lastError ?: "Shizuku permission denied") else "Shizuku not authorized"
            service == null -> if (b.bindFailed) {
                ShizukuServiceBinding.BIND_FAILED_MESSAGE
            } else {
                "Starting Shizuku user service…"
            }
            lastError != null -> lastError!!
            created -> "Virtual controller ready"
            else -> "Creating virtual controller…"
        }
    }

    fun lastErrorMessage(): String? = lastError

    /** 推断用户Down一步操作: Down载 / Open Shizuku / Request permission / None需操作。 */
    fun requiredAction(context: Context): Action = when (binding?.requiredAction(context)) {
        ShizukuServiceBinding.Action.DOWNLOAD -> Action.DOWNLOAD
        ShizukuServiceBinding.Action.OPEN -> Action.OPEN
        ShizukuServiceBinding.Action.REQUEST_PERMISSION -> Action.REQUEST_PERMISSION
        else -> Action.NONE
    }

    fun isShizukuInstalled(context: Context): Boolean =
        ShizukuServiceBinding.isShizukuInstalled(context)

    /** Open Shizuku；未安装时回退到Down载页。 */
    fun openShizuku(context: Context) = ShizukuServiceBinding.openShizuku(context)

    fun openDownloadPage(context: Context) = ShizukuServiceBinding.openDownloadPage(context)

    fun destroy() {
        binding?.detach()
        binding = null
        service = null
        setCreated(false)
        rumbleEnabled = true
        mouseEnabled = true
        resetKeyboardMouse()
    }

    /**
     * 把 [GamepadInput] 的Touchpad数据编码为 native 需要的格式: 最多 2 个触点，
     * 每点 4 个 int [id, x, y, active]（x/y 为 0..1919/0..942）。仅 DS4/DualSense
     * 使用该数据。
     */
    private fun buildTouches(input: GamepadInput): IntArray {
        val list = input.touchesList
        val arr = IntArray(8)
        for (i in 0 until minOf(2, list.size)) {
            val tp = list[i]
            arr[i * 4] = tp.id
            arr[i * 4 + 1] = tp.x
            arr[i * 4 + 2] = tp.y
            arr[i * 4 + 3] = if (tp.active) 1 else 0
        }
        return arr
    }

    /** 把 GKME 的按键 bitsLayout翻译成 XInput wButtons 掩码（含十字键低 4  bits）。 */
    private fun toXInputButtons(input: GamepadInput): Int {
        val b = input.buttons.toInt()
        var x = 0
        if (b and GamepadState.A != 0) x = x or 0x1000
        if (b and GamepadState.B != 0) x = x or 0x2000
        if (b and GamepadState.X != 0) x = x or 0x4000
        if (b and GamepadState.Y != 0) x = x or 0x8000
        if (b and GamepadState.LB != 0) x = x or 0x0100
        if (b and GamepadState.RB != 0) x = x or 0x0200
        if (b and GamepadState.SELECT != 0) x = x or 0x0020
        if (b and GamepadState.START != 0) x = x or 0x0010
        if (b and GamepadState.L3 != 0) x = x or 0x0040
        if (b and GamepadState.R3 != 0) x = x or 0x0080
        if (b and GamepadState.HOME != 0) x = x or 0x0400
        // Touchpad点击不属于 XInput，用保留 bits bit17（0x20000）透传给 native 打包。
        if (b and GamepadState.TOUCHPAD_CLICK != 0) x = x or 0x20000
        // 兼容把十字键编码进 buttons bit12..15 的输入源。
        if (b and GamepadState.DPAD_BIT_UP != 0) x = x or 0x1
        if (b and GamepadState.DPAD_BIT_DOWN != 0) x = x or 0x2
        if (b and GamepadState.DPAD_BIT_LEFT != 0) x = x or 0x4
        if (b and GamepadState.DPAD_BIT_RIGHT != 0) x = x or 0x8
        when (input.dpad) {
            GamepadState.DPAD_UP -> x = x or 0x1
            GamepadState.DPAD_DOWN -> x = x or 0x2
            GamepadState.DPAD_LEFT -> x = x or 0x4
            GamepadState.DPAD_RIGHT -> x = x or 0x8
            GamepadState.DPAD_UP_LEFT -> x = x or 0x5
            GamepadState.DPAD_UP_RIGHT -> x = x or 0x9
            GamepadState.DPAD_DOWN_LEFT -> x = x or 0x6
            GamepadState.DPAD_DOWN_RIGHT -> x = x or 0xA
        }
        return x
    }
}
