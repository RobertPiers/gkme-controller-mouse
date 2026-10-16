package com.zyz4.gkme.model

data class ButtonPosition(
    val id: String,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 10,
    val height: Int = 10,
    val visible: Boolean = true,
    val lockAspect: Boolean = false,
    val swipeTrigger: Boolean = false,
    val rotation: Int = 0,
    val isCustom: Boolean = false,
    val customText: String? = null,
    val customBits: List<Int>? = null,
    val roundShape: Boolean = true,
    val doubleClickEnable: Boolean = true,
    val isKeypad: Boolean = false,
    val keypadTexts: List<String>? = null,
    val keypadBits: List<List<Int>>? = null,
    // true = 8 Direction mode（含 4 个斜向），false = 4 Direction mode
    val keypadEightWay: Boolean = false,
    // Center shape: SQUARE = SquareCenter心，CIRCLE = CircleCenter心（周围区域变径向扇环）
    val centerShape: CenterShape = CenterShape.SQUARE,
    val sensitivityCurve: List<Float>? = null,
    // StickSensitivity百分比 (10-300)，100 = 手指 bits移与输出距离 1:1；在死区之前应用
    val joystickSensitivity: Int = 100,
    // Touchpad mode: 不以手指绝对 bits置映射Stick，而以手指速度映射；手指停Down即回Center。
    // Sensitivity/死区/反死区/曲线仍照常生效（作用于速度换算出的归一化偏移）。
    val touchpadMode: Boolean = false,
    // 预测: 用输入（手指/滑杆）速度外推Down一帧 bits置，加快响应。适用于Stick、Touchpad、Mouse板、Linear trigger。
    val prediction: Boolean = false,
    val deadZone: Int = 0,
    val reverseDeadZone: Int = 0,
    // 不透明度百分比 (0-100)，100 = 完全不透明
    val idleOpacity: Int = 100,
    val activeOpacity: Int = 100,
    val followAreaOpacity: Int = 100,
    val followAreaEnabled: Boolean = false,
    val followAreaX: Int = 0,
    val followAreaY: Int = 0,
    val followAreaW: Int = 0,
    val followAreaH: Int = 0,
    val overlapTrigger: Boolean = true,
    val followAreaOverlapTrigger: Boolean = false,
    val mouseSensitivity: Float = 1f,
    val mouseAcceleration: List<Float>? = null,
    val scrollSensitivity: Float = 0.1f,
    val mouseMoveSlop: Int = 4,
    val invertScrollV: Boolean = false,
    val invertScrollH: Boolean = false,
    // ── Mouse触控板手势动作 ──
    val singleTapAction: MouseGestureAction = MouseGestureAction.LEFT_CLICK,
    val twoFingerTapAction: MouseGestureAction = MouseGestureAction.RIGHT_CLICK,
    val threeFingerTapAction: MouseGestureAction = MouseGestureAction.MIDDLE_CLICK,
    val doubleTapDragAction: MouseGestureAction = MouseGestureAction.LEFT_DRAG,
    val oneFingerSwipeAction: MouseGestureAction = MouseGestureAction.MOVE_CURSOR,
    val twoFingerSwipeAction: MouseGestureAction = MouseGestureAction.SCROLL,
    val threeFingerSwipeAction: MouseGestureAction = MouseGestureAction.SCROLL,
    val linearTriggerEnabled: Boolean = false,
    val slideDirection: SlideDirection = SlideDirection.DOWN,
    val travelDistance: Int = 10,
    // ── Per-control gyro activation ──
    val gyroActivate: Boolean = false,
    // ── Auto hold: one tap holds, second tap releases ──
    val autoHold: Boolean = false,
    // ── Keyboard control ──
    val isKeyboard: Boolean = false,
) {
    companion object {
        // 预测外推时间（约一帧，60fps）: 预测 bits置 = 当前 bits置 + 速度 × 该时间。
        const val PREDICTION_FRAME_MS = 16f
        const val KEYPAD_BASE_ID = "customKeypad"
        // 基础区域: 0=Up, 1=Down, 2=Left, 3=Right, 4=Center心
        const val KEYPAD_COUNT = 5
        const val KEYPAD_CENTER_INDEX = 4
        // 8 方向区域: 5=Up-left, 6=Up-right, 7=Down-left, 8=Down-right
        const val KEYPAD_EIGHT_WAY_COUNT = 9

        val KEYPAD_DEFAULT_TEXTS: List<String> =
            listOf("Up", "Down", "Left", "Right", "Center", "Up-left", "Up-right", "Down-left", "Down-right")
        val KEYPAD_DEFAULT_BITS: List<List<Int>> = List(KEYPAD_EIGHT_WAY_COUNT) { emptyList() }

        fun isKeypad(id: String): Boolean =
            id.substringBefore("_") == KEYPAD_BASE_ID

        /** 按区域索引顺序Back所有可绑定方向（不含Center心）。8 Direction mode才包含斜向。 */
        fun keypadDirectionIndices(eightWay: Boolean): List<Int> =
            if (eightWay) listOf(0, 1, 2, 3, 5, 6, 7, 8) else listOf(0, 1, 2, 3)

        fun keypadTextsOf(p: ButtonPosition): List<String> {
            val t = p.keypadTexts ?: return KEYPAD_DEFAULT_TEXTS
            if (t.size < KEYPAD_COUNT) return KEYPAD_DEFAULT_TEXTS
            if (t.size >= KEYPAD_EIGHT_WAY_COUNT) return t
            // 旧版只有 5 项: 补齐斜向默认文本，保证 8 Direction mode可安全索引
            return t + KEYPAD_DEFAULT_TEXTS.subList(t.size, KEYPAD_EIGHT_WAY_COUNT)
        }

        fun keypadBitsOf(p: ButtonPosition): List<List<Int>> {
            val b = p.keypadBits ?: return KEYPAD_DEFAULT_BITS
            if (b.size < KEYPAD_COUNT) return KEYPAD_DEFAULT_BITS
            if (b.size >= KEYPAD_EIGHT_WAY_COUNT) return b
            return b + KEYPAD_DEFAULT_BITS.subList(b.size, KEYPAD_EIGHT_WAY_COUNT)
        }
    }

}
