package com.zyz4.gkme

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.zyz4.gkme.model.ButtonPosition
import com.zyz4.gkme.model.CenterShape
import com.zyz4.gkme.model.ConnectionMode
import com.zyz4.gkme.model.DisplayMode
import com.zyz4.gkme.model.GamepadState
import com.zyz4.gkme.model.MouseGestureAction
import com.zyz4.gkme.model.TouchPoint
import com.zyz4.gkme.view.CustomKeypadView
import com.zyz4.gkme.view.DpadPadView
import com.zyz4.gkme.view.GamepadLayout
import com.zyz4.gkme.view.ButtonEventHandler
import com.zyz4.gkme.view.ButtonTracker

private val _mainHandler = Handler(Looper.getMainLooper())

// ── HID keyboard scan codes ──
internal object Kb {
    // Modifiers (mapped to modifier bits 0-7 in report byte 0)
    const val LCtrl = 0xE0
    const val LShift = 0xE1
    const val LAlt = 0xE2
    const val LGui = 0xE3
    const val RCtrl = 0xE4
    const val RShift = 0xE5
    const val RAlt = 0xE6
    const val RGui = 0xE7
    // Function keys
    const val F1 = 0x3A
    const val F2 = 0x3B
    const val F3 = 0x3C
    const val F4 = 0x3D
    const val F5 = 0x3E
    const val F6 = 0x3F
    const val F7 = 0x40
    const val F8 = 0x41
    const val F9 = 0x42
    const val F10 = 0x43
    const val F11 = 0x44
    const val F12 = 0x45
    // Letters (a-z) in alphabetical order
    const val KeyA = 0x04
    const val KeyB = 0x05
    const val KeyC = 0x06
    const val KeyD = 0x07
    const val KeyE = 0x08
    const val KeyF = 0x09
    const val KeyG = 0x0A
    const val KeyH = 0x0B
    const val KeyI = 0x0C
    const val KeyJ = 0x0D
    const val KeyK = 0x0E
    const val KeyL = 0x0F
    const val KeyM = 0x10
    const val KeyN = 0x11
    const val KeyO = 0x12
    const val KeyP = 0x13
    const val KeyQ = 0x14
    const val KeyR = 0x15
    const val KeyS = 0x16
    const val KeyT = 0x17
    const val KeyU = 0x18
    const val KeyV = 0x19
    const val KeyW = 0x1A
    const val KeyX = 0x1B
    const val KeyY = 0x1C
    const val KeyZ = 0x1D
    // Numbers (1-0)
    const val Key1 = 0x1E
    const val Key2 = 0x1F
    const val Key3 = 0x20
    const val Key4 = 0x21
    const val Key5 = 0x22
    const val Key6 = 0x23
    const val Key7 = 0x24
    const val Key8 = 0x25
    const val Key9 = 0x26
    const val Key0 = 0x27
    // Row below numbers
    const val Enter = 0x28
    const val Esc = 0x29
    const val Backspace = 0x2A
    const val Tab = 0x2B
    const val Space = 0x2C
    const val Minus = 0x2D
    const val Equal = 0x2E
    const val LBracket = 0x2F
    const val RBracket = 0x30
    const val Backslash = 0x31
    const val Semicolon = 0x33
    const val Apostrophe = 0x34
    const val Grave = 0x35
    const val Comma = 0x36
    const val Dot = 0x37
    const val Slash = 0x38
    const val CapsLock = 0x39
    // Navigation
    const val Delete = 0x4C
    const val Insert = 0x49
    const val Home = 0x4A
    const val End = 0x4D
    const val PageUp = 0x4B
    const val PageDown = 0x4E
    // Arrows
    const val ArrowRight = 0x4F
    const val ArrowLeft = 0x50
    const val ArrowDown = 0x51
    const val ArrowUp = 0x52
    // Menu
    const val Menu = 0x65
}

internal object BitNameMapper {
    fun getBitName(bit: Int): String {
        if (bit < 0) {
            val kbCode = -bit
            return when (kbCode) {
                Kb.LCtrl -> "Keyboard: LCtrl"
                Kb.LShift -> "Keyboard: LShift"
                Kb.LAlt -> "Keyboard: LAlt"
                Kb.LGui -> "Keyboard: Win"
                Kb.RCtrl -> "Keyboard: RCtrl"
                Kb.RShift -> "Keyboard: RShift"
                Kb.RAlt -> "Keyboard: AltGr"
                Kb.RGui -> "Keyboard: RWin"
                Kb.KeyQ -> "Keyboard: Q"
                Kb.KeyW -> "Keyboard: W"
                Kb.KeyE -> "Keyboard: E"
                Kb.KeyR -> "Keyboard: R"
                Kb.KeyT -> "Keyboard: T"
                Kb.KeyY -> "Keyboard: Y"
                Kb.KeyU -> "Keyboard: U"
                Kb.KeyI -> "Keyboard: I"
                Kb.KeyO -> "Keyboard: O"
                Kb.KeyP -> "Keyboard: P"
                Kb.KeyA -> "Keyboard: A"
                Kb.KeyS -> "Keyboard: S"
                Kb.KeyD -> "Keyboard: D"
                Kb.KeyF -> "Keyboard: F"
                Kb.KeyG -> "Keyboard: G"
                Kb.KeyH -> "Keyboard: H"
                Kb.KeyJ -> "Keyboard: J"
                Kb.KeyK -> "Keyboard: K"
                Kb.KeyL -> "Keyboard: L"
                Kb.KeyZ -> "Keyboard: Z"
                Kb.KeyX -> "Keyboard: X"
                Kb.KeyC -> "Keyboard: C"
                Kb.KeyV -> "Keyboard: V"
                Kb.KeyB -> "Keyboard: B"
                Kb.KeyN -> "Keyboard: N"
                Kb.KeyM -> "Keyboard: M"
                Kb.Key1 -> "Keyboard: 1"
                Kb.Key2 -> "Keyboard: 2"
                Kb.Key3 -> "Keyboard: 3"
                Kb.Key4 -> "Keyboard: 4"
                Kb.Key5 -> "Keyboard: 5"
                Kb.Key6 -> "Keyboard: 6"
                Kb.Key7 -> "Keyboard: 7"
                Kb.Key8 -> "Keyboard: 8"
                Kb.Key9 -> "Keyboard: 9"
                Kb.Key0 -> "Keyboard: 0"
                Kb.Space -> "Keyboard: Space"
                Kb.Enter -> "Keyboard: Enter"
                Kb.Backspace -> "Keyboard: Bksp"
                Kb.Tab -> "Keyboard: Tab"
                Kb.CapsLock -> "Keyboard: Caps"
                Kb.Esc -> "Keyboard: Esc"
                Kb.Delete -> "Keyboard: Del"
                Kb.Menu -> "Keyboard: Menu"
                Kb.Minus -> "Keyboard: -"
                Kb.Equal -> "Keyboard: ="
                Kb.LBracket -> "Keyboard: ["
                Kb.RBracket -> "Keyboard: ]"
                Kb.Backslash -> "Keyboard: \\"
                Kb.Semicolon -> "Keyboard: ;"
                Kb.Apostrophe -> "Keyboard: '"
                Kb.Comma -> "Keyboard: ,"
                Kb.Dot -> "Keyboard: ."
                Kb.Slash -> "Keyboard: /"
                Kb.Grave -> "Keyboard: `"
                Kb.F1 -> "Keyboard: F1"
                Kb.F2 -> "Keyboard: F2"
                Kb.F3 -> "Keyboard: F3"
                Kb.F4 -> "Keyboard: F4"
                Kb.F5 -> "Keyboard: F5"
                Kb.F6 -> "Keyboard: F6"
                Kb.F7 -> "Keyboard: F7"
                Kb.F8 -> "Keyboard: F8"
                Kb.F9 -> "Keyboard: F9"
                Kb.F10 -> "Keyboard: F10"
                Kb.F11 -> "Keyboard: F11"
                Kb.F12 -> "Keyboard: F12"
                Kb.ArrowUp -> "Keyboard: ↑"
                Kb.ArrowDown -> "Keyboard: ↓"
                Kb.ArrowLeft -> "Keyboard: ←"
                Kb.ArrowRight -> "Keyboard: →"
                else -> "Keyboard: Key$kbCode"
            }
        }
        return when (bit) {
            GamepadState.A -> "Controller: A"
            GamepadState.B -> "Controller: B"
            GamepadState.X -> "Controller: X"
            GamepadState.Y -> "Controller: Y"
            GamepadState.LB -> "Controller: LB"
            GamepadState.RB -> "Controller: RB"
            GamepadState.LT -> "Controller: LT"
            GamepadState.RT -> "Controller: RT"
            GamepadState.SELECT -> "Controller: Select"
            GamepadState.START -> "Controller: Menu"
            GamepadState.L3 -> "Controller: Left stick click"
            GamepadState.R3 -> "Controller: Right stick click"
            GamepadState.HOME -> "Controller: Home"
            GamepadState.TOUCHPAD_CLICK -> "Controller: Share"
            GamepadState.DPAD_BIT_UP -> "Up direction"
            GamepadState.DPAD_BIT_DOWN -> "Down direction"
            GamepadState.DPAD_BIT_LEFT -> "Left direction"
            GamepadState.DPAD_BIT_RIGHT -> "Right direction"
            GamepadState.MIC_MUTE -> "Mute microphone"
            GamepadState.MOUSE_LMB -> "Mouse: LMB"
            GamepadState.MOUSE_RMB -> "Mouse: RMB"
            GamepadState.MOUSE_MMB -> "Mouse: MMB"
            GamepadState.MOUSE_BACK -> "Mouse: Back"
            GamepadState.MOUSE_FORWARD -> "Mouse: Forward"
            else -> " bits$bit"
        }
    }

    fun getKeyboardPreviewText(kbKeyCode: Int, shift: Boolean = false): String {
        if (shift) {
            when (kbKeyCode) {
                Kb.Key1 -> return "!"
                Kb.Key2 -> return "@"
                Kb.Key3 -> return "#"
                Kb.Key4 -> return "$"
                Kb.Key5 -> return "%"
                Kb.Key6 -> return "^"
                Kb.Key7 -> return "&"
                Kb.Key8 -> return "*"
                Kb.Key9 -> return "("
                Kb.Key0 -> return ")"
                Kb.Minus -> return "_"
                Kb.Equal -> return "+"
                Kb.LBracket -> return "{"
                Kb.RBracket -> return "}"
                Kb.Backslash -> return "|"
                Kb.Semicolon -> return ":"
                Kb.Apostrophe -> return "\""
                Kb.Comma -> return "<"
                Kb.Dot -> return ">"
                Kb.Slash -> return "?"
                Kb.Grave -> return "~"
            }
        }
        return when (kbKeyCode) {
            Kb.LCtrl -> "LCtrl"; Kb.LShift -> "LShift"; Kb.LAlt -> "LAlt"; Kb.LGui -> "Win"
            Kb.RCtrl -> "RCtrl"; Kb.RShift -> "RShift"; Kb.RAlt -> "AltGr"; Kb.RGui -> "RWin"
            Kb.KeyQ -> "Q"; Kb.KeyW -> "W"; Kb.KeyE -> "E"; Kb.KeyR -> "R"; Kb.KeyT -> "T"
            Kb.KeyY -> "Y"; Kb.KeyU -> "U"; Kb.KeyI -> "I"; Kb.KeyO -> "O"; Kb.KeyP -> "P"
            Kb.KeyA -> "A"; Kb.KeyS -> "S"; Kb.KeyD -> "D"; Kb.KeyF -> "F"; Kb.KeyG -> "G"
            Kb.KeyH -> "H"; Kb.KeyJ -> "J"; Kb.KeyK -> "K"; Kb.KeyL -> "L"
            Kb.KeyZ -> "Z"; Kb.KeyX -> "X"; Kb.KeyC -> "C"; Kb.KeyV -> "V"; Kb.KeyB -> "B"
            Kb.KeyN -> "N"; Kb.KeyM -> "M"
            Kb.Key1 -> "1"; Kb.Key2 -> "2"; Kb.Key3 -> "3"; Kb.Key4 -> "4"; Kb.Key5 -> "5"
            Kb.Key6 -> "6"; Kb.Key7 -> "7"; Kb.Key8 -> "8"; Kb.Key9 -> "9"; Kb.Key0 -> "0"
            Kb.Space -> "Space"; Kb.Enter -> "Enter"; Kb.Backspace -> "Bksp"; Kb.Tab -> "Tab"
            Kb.CapsLock -> "Caps"; Kb.Esc -> "Esc"; Kb.Delete -> "Del"; Kb.Menu -> "Menu"
            Kb.Minus -> "-"; Kb.Equal -> "="; Kb.LBracket -> "["; Kb.RBracket -> "]"
            Kb.Backslash -> "\\"; Kb.Semicolon -> ";"; Kb.Apostrophe -> "'"; Kb.Comma -> ","
            Kb.Dot -> "."; Kb.Slash -> "/"; Kb.Grave -> "`"
            Kb.F1 -> "F1"; Kb.F2 -> "F2"; Kb.F3 -> "F3"; Kb.F4 -> "F4"; Kb.F5 -> "F5"
            Kb.F6 -> "F6"; Kb.F7 -> "F7"; Kb.F8 -> "F8"; Kb.F9 -> "F9"; Kb.F10 -> "F10"
            Kb.F11 -> "F11"; Kb.F12 -> "F12"
            Kb.ArrowUp -> "↑"; Kb.ArrowDown -> "↓"; Kb.ArrowLeft -> "←"; Kb.ArrowRight -> "→"
            else -> "Key"
        }
    }
}

internal data class CtrlEntry(
    val baseId: String, val name: String, val icon: Int,
    val bgRes: Int = R.drawable.button_circle,
    val isJoystick: Boolean = false,
    val isTouchpad: Boolean = false,
    val isMousepad: Boolean = false,
    val isDpad: Boolean = false,
    val isTrigger: Boolean = false,
    val isDpadPad: Boolean = false,
    val isKeypad: Boolean = false,
    val isKeyboard: Boolean = false,
    val keyboardKeyCode: Int = 0,
    val useImageButton: Boolean = false,
    val isCustom: Boolean = false,
    val bit: Int = 0,
    val w: Int = 10, val h: Int = 10,
    val lockAspect: Boolean = false,
)

internal val ctrlEntryBitMap: Map<String, Int> = listOf(
    "btnA" to GamepadState.A, "btnB" to GamepadState.B,
    "btnX" to GamepadState.X, "btnY" to GamepadState.Y,
    "btnLB" to GamepadState.LB, "btnRB" to GamepadState.RB,
    "btnLT" to GamepadState.LT, "btnRT" to GamepadState.RT,
    "btnLS" to GamepadState.L3, "btnRS" to GamepadState.R3,
    "btnSelect" to GamepadState.SELECT, "btnHome" to GamepadState.HOME,
    "btnMenu" to GamepadState.START, "btnShare" to GamepadState.TOUCHPAD_CLICK,
    "btnMic" to GamepadState.MIC_MUTE,
    "btnMouseLMB" to GamepadState.MOUSE_LMB,
    "btnMouseRMB" to GamepadState.MOUSE_RMB,
    "btnMouseMMB" to GamepadState.MOUSE_MMB,
    "btnMouseBack" to GamepadState.MOUSE_BACK,
    "btnMouseForward" to GamepadState.MOUSE_FORWARD,
    "btnDpadUp" to GamepadState.DPAD_BIT_UP, "btnDpadDown" to GamepadState.DPAD_BIT_DOWN,
    "btnDpadLeft" to GamepadState.DPAD_BIT_LEFT, "btnDpadRight" to GamepadState.DPAD_BIT_RIGHT,
).toMap()

internal val allControls = listOf(
    CtrlEntry("btnDpadUp", "Up direction", R.drawable.ic_arrow_up, isDpad = true, useImageButton = true, bit = GamepadState.DPAD_UP, lockAspect = true),
    CtrlEntry("btnDpadDown", "Down direction", R.drawable.ic_arrow_down, isDpad = true, useImageButton = true, bit = GamepadState.DPAD_DOWN, lockAspect = true),
    CtrlEntry("btnDpadLeft", "Left direction", R.drawable.ic_arrow_left, isDpad = true, useImageButton = true, bit = GamepadState.DPAD_LEFT, lockAspect = true),
    CtrlEntry("btnDpadRight", "Right direction", R.drawable.ic_arrow_right, isDpad = true, useImageButton = true, bit = GamepadState.DPAD_RIGHT, lockAspect = true),
    CtrlEntry("dpadPad", "D-pad", R.drawable.ic_dpad_pad, isDpadPad = true, w = 17, h = 17, lockAspect = true),
    CtrlEntry("btnA", "Controller: A", R.drawable.btn_ps_cross, bit = GamepadState.A, lockAspect = true),
    CtrlEntry("btnB", "Controller: B", R.drawable.btn_ps_circle, bit = GamepadState.B, lockAspect = true),
    CtrlEntry("btnX", "Controller: X", R.drawable.btn_ps_square, bit = GamepadState.X, lockAspect = true),
    CtrlEntry("btnY", "Controller: Y", R.drawable.btn_ps_triangle, bit = GamepadState.Y, lockAspect = true),
    CtrlEntry("btnLB", "Controller: LB", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, bit = GamepadState.LB, w = 14, h = 8, lockAspect = false),
    CtrlEntry("btnRB", "Controller: RB", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, bit = GamepadState.RB, w = 14, h = 8, lockAspect = false),
    CtrlEntry("btnLT", "Controller: LT", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, isTrigger = true, bit = GamepadState.LT, w = 14, h = 8, lockAspect = false),
    CtrlEntry("btnRT", "Controller: RT", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, isTrigger = true, bit = GamepadState.RT, w = 14, h = 8, lockAspect = false),
    CtrlEntry("leftJoystick", "Controller: Left stick", R.drawable.joystick_outer, isJoystick = true, w = 17, h = 17, lockAspect = true),
    CtrlEntry("rightJoystick", "Controller: Right stick", R.drawable.joystick_outer, isJoystick = true, w = 17, h = 17, lockAspect = true),
    CtrlEntry("touchpad", "Touchpad (controller)", R.drawable.center_rect, isTouchpad = true, w = 34, h = 22, lockAspect = false),
    CtrlEntry("mousepad", "Touchpad (mouse)", R.drawable.center_rect, isMousepad = true, w = 34, h = 22, lockAspect = false),
    CtrlEntry("btnShare", "Controller: Share", R.drawable.btn_touchpad, R.drawable.btn_touchpad, bit = GamepadState.TOUCHPAD_CLICK, w = 9, h = 9, lockAspect = true),
    CtrlEntry("btnLS", "Controller: Left stick click", R.drawable.btn_ls, R.drawable.btn_ls, bit = GamepadState.L3, w = 9, h = 9, lockAspect = true),
    CtrlEntry("btnRS", "Controller: Right stick click", R.drawable.btn_rs, R.drawable.btn_rs, bit = GamepadState.R3, w = 9, h = 9, lockAspect = true),
    CtrlEntry("btnSelect", "Controller: Select", R.drawable.btn_select_xbox, bit = GamepadState.SELECT, w = 9, h = 9, lockAspect = true),
    CtrlEntry("btnHome", "Controller: Home", R.drawable.ic_home, useImageButton = true, bit = GamepadState.HOME, w = 9, h = 9, lockAspect = true),
    CtrlEntry("btnMenu", "Controller: Menu", R.drawable.btn_menu_xbox, bit = GamepadState.START, w = 9, h = 9, lockAspect = true),
    CtrlEntry("btnCustomCircle", "Custom (circle)", R.drawable.button_circle, isCustom = true, lockAspect = true),
    CtrlEntry("btnCustomRect", "Custom (square)", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, w = 14, h = 8, lockAspect = false, isCustom = true),
    CtrlEntry("customKeypad", "Custom keypad", R.drawable.ic_custom_keypad, isKeypad = true, w = 17, h = 17, lockAspect = true),
    CtrlEntry("btnMic", "Mute microphone", R.drawable.ic_mic, useImageButton = true, bit = GamepadState.MIC_MUTE, w = 9, h = 9, lockAspect = true),

    // ── Mouse keys ──
    CtrlEntry("btnMouseLMB", "Mouse: LMB", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, bit = GamepadState.MOUSE_LMB, w = 9, h = 9, lockAspect = false),
    CtrlEntry("btnMouseRMB", "Mouse: RMB", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, bit = GamepadState.MOUSE_RMB, w = 9, h = 9, lockAspect = false),
    CtrlEntry("btnMouseMMB", "Mouse: MMB", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, bit = GamepadState.MOUSE_MMB, w = 9, h = 9, lockAspect = false),
    CtrlEntry("btnMouseBack", "Mouse: Back", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, bit = GamepadState.MOUSE_BACK, w = 9, h = 9, lockAspect = false),
    CtrlEntry("btnMouseForward", "Mouse: Forward", R.drawable.button_rounded_rect, R.drawable.button_rounded_rect, bit = GamepadState.MOUSE_FORWARD, w = 9, h = 9, lockAspect = false),

    // ── Keyboard keys ──
    // Modifiers
    CtrlEntry("kbLCtrl", "Keyboard: LCtrl", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.LCtrl),
    CtrlEntry("kbLShift", "Keyboard: LShift", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.LShift),
    CtrlEntry("kbLAlt", "Keyboard: LAlt", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.LAlt),
    CtrlEntry("kbLWin", "Keyboard: Win", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.LGui),
    CtrlEntry("kbRCtrl", "Keyboard: RCtrl", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.RCtrl),
    CtrlEntry("kbRShift", "Keyboard: RShift", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.RShift),
    CtrlEntry("kbRAlt", "Keyboard: AltGr", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.RAlt),
    CtrlEntry("kbRGui", "Keyboard: RWin", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.RGui),
    // Letters row 1
    CtrlEntry("kbQ", "Keyboard: Q", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyQ),
    CtrlEntry("kbW", "Keyboard: W", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyW),
    CtrlEntry("kbE", "Keyboard: E", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyE),
    CtrlEntry("kbR", "Keyboard: R", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyR),
    CtrlEntry("kbT", "Keyboard: T", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyT),
    CtrlEntry("kbY", "Keyboard: Y", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyY),
    CtrlEntry("kbU", "Keyboard: U", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyU),
    CtrlEntry("kbI", "Keyboard: I", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyI),
    CtrlEntry("kbO", "Keyboard: O", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyO),
    CtrlEntry("kbP", "Keyboard: P", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyP),
    // Letters row 2
    CtrlEntry("kbA", "Keyboard: A", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyA),
    CtrlEntry("kbS", "Keyboard: S", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyS),
    CtrlEntry("kbD", "Keyboard: D", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyD),
    CtrlEntry("kbF", "Keyboard: F", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyF),
    CtrlEntry("kbG", "Keyboard: G", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyG),
    CtrlEntry("kbH", "Keyboard: H", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyH),
    CtrlEntry("kbJ", "Keyboard: J", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyJ),
    CtrlEntry("kbK", "Keyboard: K", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyK),
    CtrlEntry("kbL", "Keyboard: L", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyL),
    // Letters row 3
    CtrlEntry("kbZ", "Keyboard: Z", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyZ),
    CtrlEntry("kbX", "Keyboard: X", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyX),
    CtrlEntry("kbC", "Keyboard: C", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyC),
    CtrlEntry("kbV", "Keyboard: V", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyV),
    CtrlEntry("kbB", "Keyboard: B", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyB),
    CtrlEntry("kbN", "Keyboard: N", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyN),
    CtrlEntry("kbM", "Keyboard: M", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.KeyM),
    // Numbers
    CtrlEntry("kb1", "Keyboard: 1", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key1),
    CtrlEntry("kb2", "Keyboard: 2", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key2),
    CtrlEntry("kb3", "Keyboard: 3", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key3),
    CtrlEntry("kb4", "Keyboard: 4", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key4),
    CtrlEntry("kb5", "Keyboard: 5", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key5),
    CtrlEntry("kb6", "Keyboard: 6", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key6),
    CtrlEntry("kb7", "Keyboard: 7", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key7),
    CtrlEntry("kb8", "Keyboard: 8", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key8),
    CtrlEntry("kb9", "Keyboard: 9", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key9),
    CtrlEntry("kb0", "Keyboard: 0", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Key0),
    // Navigation
    CtrlEntry("kbSpace", "Keyboard: Space", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Space, w = 14, h = 8),
    CtrlEntry("kbEnter", "Keyboard: Enter", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Enter, w = 9, h = 8),
    CtrlEntry("kbBackspace", "Keyboard: Bksp", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Backspace, w = 9, h = 8),
    CtrlEntry("kbTab", "Keyboard: Tab", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Tab, w = 8, h = 8),
    CtrlEntry("kbCaps", "Keyboard: Caps", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.CapsLock, w = 9, h = 8),
    CtrlEntry("kbEsc", "Keyboard: Esc", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Esc),
    CtrlEntry("kbDelete", "Keyboard: Del", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Delete),
    CtrlEntry("kbMenu", "Keyboard: Menu", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Menu),
    // Symbols
    CtrlEntry("kbMinus", "Keyboard: -", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Minus),
    CtrlEntry("kbEqual", "Keyboard: =", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Equal),
    CtrlEntry("kbLBracket", "Keyboard: [", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.LBracket),
    CtrlEntry("kbRBracket", "Keyboard: ]", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.RBracket),
    CtrlEntry("kbBackslash", "Keyboard: \\", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Backslash),
    CtrlEntry("kbSemicolon", "Keyboard: ;", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Semicolon),
    CtrlEntry("kbApostrophe", "Keyboard: '", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Apostrophe),
    CtrlEntry("kbComma", "Keyboard: ,", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Comma),
    CtrlEntry("kbDot", "Keyboard: .", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Dot),
    CtrlEntry("kbSlash", "Keyboard: /", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Slash),
    CtrlEntry("kbGrave", "Keyboard: `", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.Grave),
    // Function keys
    CtrlEntry("kbF1", "Keyboard: F1", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F1),
    CtrlEntry("kbF2", "Keyboard: F2", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F2),
    CtrlEntry("kbF3", "Keyboard: F3", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F3),
    CtrlEntry("kbF4", "Keyboard: F4", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F4),
    CtrlEntry("kbF5", "Keyboard: F5", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F5),
    CtrlEntry("kbF6", "Keyboard: F6", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F6),
    CtrlEntry("kbF7", "Keyboard: F7", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F7),
    CtrlEntry("kbF8", "Keyboard: F8", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F8),
    CtrlEntry("kbF9", "Keyboard: F9", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F9),
    CtrlEntry("kbF10", "Keyboard: F10", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F10),
    CtrlEntry("kbF11", "Keyboard: F11", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F11),
    CtrlEntry("kbF12", "Keyboard: F12", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.F12),
    // Arrow keys
    CtrlEntry("kbArrowUp", "Keyboard: ↑", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.ArrowUp),
    CtrlEntry("kbArrowDown", "Keyboard: ↓", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.ArrowDown),
    CtrlEntry("kbArrowLeft", "Keyboard: ←", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.ArrowLeft),
    CtrlEntry("kbArrowRight", "Keyboard: →", R.drawable.button_rounded_rect, isKeyboard = true, keyboardKeyCode = Kb.ArrowRight),
)

// Keep custom button text vertically centered even when the button is shrunk:
// drop the default Button theme padding/font padding and let the text auto-shrink to fit.
internal fun TextView.enableAutoFitButtonText(maxSizeSp: Float, minSizeSp: Float = maxSizeSp * 0.25f) {
    includeFontPadding = false
    setPadding(0, 0, 0, 0)
    minWidth = 0
    minHeight = 0
    maxLines = 1
    val scaled = resources.displayMetrics.density
    val maxPx = (maxSizeSp * scaled).toInt().coerceAtLeast(2)
    val minPx = (minSizeSp * scaled).toInt().coerceAtLeast(1)
    setAutoSizeTextTypeUniformWithConfiguration(minPx, maxPx, 1, TypedValue.COMPLEX_UNIT_PX)
}

// Re-cap the auto-fit text to at most maxSizePx (does not touch padding, which is
// managed separately so layout can re-apply it on size changes).
internal fun TextView.applyContentSizeCap(maxSizePx: Int, minSizeSp: Float = 4f) {
    includeFontPadding = false
    minWidth = 0
    minHeight = 0
    maxLines = 1
    val scaled = resources.displayMetrics.density
    val maxPx = maxSizePx.coerceAtLeast(3)
    val minPx = (minSizeSp * scaled).toInt().coerceIn(1, maxPx - 1)
    setAutoSizeTextTypeUniformWithConfiguration(minPx, maxPx, 1, TypedValue.COMPLEX_UNIT_PX)
}

// ── Gamepad Layout Listener ────────────────────────────────

internal fun MainActivity.setupGamepadLayoutListener() {
    val a = this
    gamepadLayout.listener = object : GamepadLayout.GamepadLayoutListener {
        override fun onButtonSelected(buttonId: String?) {
            a.viewModel.setSelectedButtonId(buttonId)
            if (a.floatingEditor.showingGlobalSettings) {
                a.floatingEditor.clearParameters()
            } else if (buttonId != null) {
                val pos = a.gamepadLayout.currentButtons.find { it.id == buttonId }
                if (pos != null) {
                    a.floatingEditor.showParametersAnimated(buttonId, pos)
                }
            } else {
                a.floatingEditor.clearParametersAnimated()
            }
        }

        override fun onEditModeChanged(isEditMode: Boolean) {
            if (isEditMode) {
                a.floatingEditor.showAnimated()
                a.floatingEditor.restoreFromSettings(a.viewModel.settings.value)
            } else {
                a.floatingEditor.hideAnimated()
            }
        }

        override fun onTouchpadEvent(
            x: Float, y: Float,
            touches: List<FloatArray>,
            touchpadTouch: Boolean, touchpadClick: Boolean
        ) {
            val touchPoints = touches.map { arr ->
                com.zyz4.gkme.model.TouchPoint(id = arr[0].toInt(),
                    x = (arr[1] * 1919).toInt().coerceIn(0, 1919),
                    y = (arr[2] * 942).toInt().coerceIn(0, 942),
                    active = arr.size > 3 && arr[3] > 0.5f)
            }
            a.physicalControllerHandler.setCapturedTouchpadState(x, y, touchPoints, touchpadTouch, touchpadClick)
            a.syncPhysicalControllerState()
        }
    }

    a.physicalControllerHandler.onPointerCaptureNeeded = { enabled ->
        val needCapture = enabled
        a.pointerCaptureNeeded = needCapture
        a.physicalControllerHandler.isPointerCaptureActive = enabled
        a.gamepadLayout.setTouchpadCaptureMode(needCapture)
    }

    // Set up mouse mode: when mousepad control exists in the layout, route captured pointer events
    // through MouseInputDispatcher and send reports via Bluetooth HID Report ID 2.
    a.gamepadLayout.onCapturedMouseReport = { dx, dy, wheelV, wheelH, buttonDown, buttonUp, _, _ ->
        val s = a.viewModel.settings.value
        if (s.connectionMode != com.zyz4.gkme.model.ConnectionMode.BLUETOOTH) {
            ByteArray(0)
        } else {
            val mp = a.gamepadLayout.currentButtons.find { it.id.startsWith("mousepad") }
            val v = if (mp?.invertScrollV == true) (-wheelV).toShort() else wheelV
            val h = if (mp?.invertScrollH == true) wheelH else (-wheelH).toShort()
            val btn = (buttonDown.toInt() and 0x03) or (buttonUp.toInt() and 0xFC)
            a.viewModel.connectionManager.sendMouseReport(
                button = btn.toByte(),
                dx = dx,
                dy = dy,
                wheel = v,
                hWheel = h,
            )
            ByteArray(4)
        }
    }
}

// ── Gamepad ──────────────────────────────────────────────

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.createSettingsButtonView(): View {
    val a = this
    val view = ImageButton(a).apply {
        id = View.generateViewId()
        tag = GamepadLayout.SETTINGS_BUTTON_ID
        setBackgroundResource(R.drawable.bg_small_btn)
        setImageResource(R.drawable.ic_settings)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(
            (8f * a.resources.displayMetrics.density).toInt(),
            (8f * a.resources.displayMetrics.density).toInt(),
            (8f * a.resources.displayMetrics.density).toInt(),
            (8f * a.resources.displayMetrics.density).toInt()
        )
        contentDescription = "Settings"
        setOnClickListener { a.showSettings() }
    }
    a.gamepadLayout.addView(view)
    return view
}



@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupTouchHandler(
    view: View,
    bit: Int,
    isDpad: Boolean,
    isTrigger: Boolean,
    isJoystick: Boolean,
) {
    val a = this
    if (view is com.zyz4.gkme.view.LinearTriggerView) return

    when {
        isDpad -> view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; v.performClick(); a.viewModel.triggerHapticPress(); a.viewModel.onDpad(bit, true); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; a.viewModel.triggerHapticRelease(); a.viewModel.onDpad(bit, false); true }
                else -> true
            }
        }
        isTrigger -> {
            val analogFn: (Int) -> Unit = if (bit == GamepadState.LT) a.viewModel::onLeftTrigger else a.viewModel::onRightTrigger
            view.setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { v.isPressed = true; v.performClick(); a.viewModel.onButtonDown(bit); a.viewModel.triggerHapticPress(); analogFn(255); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; a.viewModel.onButtonUp(bit); a.viewModel.triggerHapticRelease(); analogFn(0); true }
                    else -> true
                }
            }
        }
        !isJoystick -> {
            val id = view.tag as? String
            val curPos = id?.let { a.gamepadLayout.currentButtons.find { it.id == id } }
            val holdEnabled = curPos?.autoHold == true
            val gyroActivate = curPos?.gyroActivate == true
            val tracker = ButtonTracker()
            val handler = object : ButtonEventHandler {
                override fun onPress(viewId: String, bit: Int) {
                    a.viewModel.triggerHapticPress()
                    if (bit != 0) a.viewModel.onButtonDown(bit)
                }
                override fun onRelease(viewId: String, bit: Int) {
                    a.viewModel.triggerHapticRelease()
                    if (bit != 0) a.viewModel.onButtonUp(bit)
                }
                override fun onGyroActivateDown(viewId: String) {
                    if (gyroActivate) a.viewModel.onGyroActivateButtonDown()
                }
                override fun onGyroActivateUp(viewId: String) {
                    if (gyroActivate) a.viewModel.onGyroActivateButtonUp()
                }
            }
            view.setOnTouchListener { v, e ->
                val viewId = v.tag as? String ?: ""
                tracker.feed(e, v, viewId, bit, holdEnabled, gyroActivate, handler)
            }
        }
    }
}


@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.createDpadPadView(id: String): DpadPadView {
    val a = this
    return DpadPadView(a).apply {
        this.id = View.generateViewId(); tag = id
        a.setupDpadPadTouch(this)
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupDpadPadTouch(view: DpadPadView) {
    val a = this
    val id = view.tag as String
    view.centerShape = a.gamepadLayout.currentButtons.find { it.id == id }?.centerShape ?: CenterShape.SQUARE
    view.onDpadChange = { released, pressed ->
        a.viewModel.updateDpad(pressed, released)
    }
    view.onLift = {
        a.viewModel.updateDpadRelease()
    }
    view.onGyroActivateDown = {
        val curPos = a.gamepadLayout.currentButtons.find { it.id == id }
        if (curPos?.gyroActivate == true) {
            a.viewModel.onGyroActivateButtonDown()
        }
    }
    view.onGyroActivateUp = {
        val curPos = a.gamepadLayout.currentButtons.find { it.id == id }
        if (curPos?.gyroActivate == true) {
            a.viewModel.onGyroActivateButtonUp()
        }
    }
}
@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.createCustomKeypadView(id: String): CustomKeypadView {
    val a = this
    val pos = a.gamepadLayout.currentButtons.find { it.id == id }
    return CustomKeypadView(a).apply {
        this.id = View.generateViewId(); tag = id
        a.setupCustomKeypadTouch(this, pos)
    }
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupCustomKeypadTouch(view: CustomKeypadView, initialPos: ButtonPosition?) {
    val a = this
    fun findKeypadPos(): ButtonPosition? = a.gamepadLayout.currentButtons.find { it.id == view.tag }
    view.onRegionPress = { region ->
        val pos = findKeypadPos()
        if (pos != null) {
            val bits = ButtonPosition.keypadBitsOf(pos).getOrNull(region).orEmpty()
            a.viewModel.onCustomButtonDown(bits)
        }
    }
    view.onRegionRelease = { region ->
        val pos = findKeypadPos()
        if (pos != null) {
            val bits = ButtonPosition.keypadBitsOf(pos).getOrNull(region).orEmpty()
            a.viewModel.onCustomButtonUp(bits)
        }
    }
    view.eightWay = findKeypadPos()?.keypadEightWay == true
    view.centerShape = findKeypadPos()?.centerShape ?: CenterShape.SQUARE
    val kpBits = findKeypadPos()?.let { ButtonPosition.keypadBitsOf(it) } ?: listOf()
    view.validDirs = ButtonPosition.keypadDirectionIndices(view.eightWay)
        .filter { kpBits.getOrNull(it)?.isNotEmpty() == true }
        .toSet()
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupTouchpadView(tp: FrameLayout) {
    val a = this
    val handler = Handler(Looper.getMainLooper())
    var firstTapTime = 0L; var firstTapX = 0f; var firstTapY = 0f
    var isDoubleClick = false
    var wasDoubleClickUp = false
    val density = resources.displayMetrics.density
    val doubleTapTimeout = Runnable { firstTapTime = 0 }
    val slots = arrayOfNulls<TouchPoint>(10)

    fun mapPoint(px: Float, py: Float): Pair<Int, Int> {
        val btnId = tp.tag as? String ?: return 0 to 0
        val pos = a.gamepadLayout.currentButtons.find { it.id == btnId } ?: return 0 to 0
        val w = tp.measuredWidth.coerceAtLeast(1)
        val h = tp.measuredHeight.coerceAtLeast(1)
        val rotation = pos.rotation
        val nx = px / w
        val ny = py / h
        val (vx, vy) = when (rotation % 360) {
            90 -> Pair(ny, 1f - nx)
            180 -> Pair(1f - nx, 1f - ny)
            270 -> Pair(1f - ny, nx)
            else -> Pair(nx, ny)
        }
        if (pos.followAreaEnabled && pos.followAreaW > 0 && pos.followAreaH > 0) {
            // Extended touch range: the sent coordinate is normalized over the
            // rectangle area instead of the touchpad control itself.
            val cell = a.gamepadLayout.getCellSize()
            if (cell > 0f) {
                val areaL = pos.followAreaX * cell
                val areaT = pos.followAreaY * cell
                val areaW = pos.followAreaW * cell
                val areaH = pos.followAreaH * cell
                val absX = tp.left + vx * w
                val absY = tp.top + vy * h
                val nax = ((absX - areaL) / areaW).coerceIn(0f, 1f)
                val nay = ((absY - areaT) / areaH).coerceIn(0f, 1f)
                return (nax * 1919).toInt().coerceIn(0, 1919) to (nay * 942).toInt().coerceIn(0, 942)
            }
        }
        return (vx * 1919).toInt().coerceIn(0, 1919) to (vy * 942).toInt().coerceIn(0, 942)
    }

    fun emptySlot(): Int {
        for (i in slots.indices) if (slots[i] == null) return i
        return -1
    }

    fun nearestSlot(x: Int, y: Int): Int {
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in slots.indices) {
            val s = slots[i] ?: continue
            val dx = s.x - x; val dy = s.y - y
            val d = (dx * dx + dy * dy).toFloat()
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    fun send() {
        if (isDoubleClick && slots.all { it == null }) {
            tp.isPressed = false; a.viewModel.onButtonUp(GamepadState.TOUCHPAD_CLICK)
            isDoubleClick = false
        }
        a.viewModel.onTouchpadTouches(
            slots.take(2).mapIndexed { i, tp -> tp ?: TouchPoint(id = i, active = false) }
        )
    }

    fun touchpadAlpha(active: Boolean) {
        val id = tp.tag as? String ?: return
        val pos = a.gamepadLayout.currentButtons.find { it.id == id } ?: return
        tp.alpha = (if (active) pos.activeOpacity else pos.idleOpacity).coerceIn(0, 100) / 100f
    }

    var touchpadGyroHeld = false
    val touchpadListener = View.OnTouchListener touchpadListenerLabel@ { v, event ->
        val btnId = v.tag as? String
        val doubleClickEnable = btnId?.let { a.gamepadLayout.currentButtons.find { p -> p.id == it }?.doubleClickEnable } ?: true
        val curPos = btnId?.let { a.gamepadLayout.currentButtons.find { p -> p.id == it } }
        val touchpadGyroActivate = curPos?.gyroActivate == true
        val masked = event.action and MotionEvent.ACTION_MASK

        if (masked == MotionEvent.ACTION_UP) {
            val (sx, sy) = mapPoint(event.getX(0), event.getY(0))
            val slot = nearestSlot(sx, sy)
            if (slot >= 0) slots[slot] = null
            if (touchpadGyroActivate && touchpadGyroHeld) {
                touchpadGyroHeld = false
                a.viewModel.onGyroActivateButtonUp()
            }
            if (slots.all { it == null }) {
                if (isDoubleClick) {
                    v.isPressed = false
                    wasDoubleClickUp = true
                    a.viewModel.onButtonUp(GamepadState.TOUCHPAD_CLICK)
                    a.viewModel.triggerHapticRelease()
                }
                isDoubleClick = false
                touchpadAlpha(false)
            }
            send()
            return@touchpadListenerLabel true
        }

        if (masked == MotionEvent.ACTION_CANCEL) {
            slots.fill(null)
            if (touchpadGyroActivate && touchpadGyroHeld) {
                touchpadGyroHeld = false
                a.viewModel.onGyroActivateButtonUp()
            }
            if (isDoubleClick) {
                v.isPressed = false
                wasDoubleClickUp = true
                a.viewModel.onButtonUp(GamepadState.TOUCHPAD_CLICK)
                a.viewModel.triggerHapticRelease()
            }
            isDoubleClick = false
            touchpadAlpha(false)
            send()
            return@touchpadListenerLabel true
        }

        if (masked == MotionEvent.ACTION_DOWN || masked == MotionEvent.ACTION_POINTER_DOWN) {
            val idx = if (masked == MotionEvent.ACTION_DOWN) 0 else event.actionIndex
            val (sx, sy) = mapPoint(event.getX(idx), event.getY(idx))
            var slot = emptySlot()
            if (slot < 0) slot = nearestSlot(sx, sy)
            if (slot < 0) slot = if (masked == MotionEvent.ACTION_DOWN) 0 else 1
            if (touchpadGyroActivate && !touchpadGyroHeld) {
                touchpadGyroHeld = true
                a.viewModel.onGyroActivateButtonDown()
            }
            slots[slot] = TouchPoint(id = slot, x = sx, y = sy, active = true)
            touchpadAlpha(true)
            if (masked == MotionEvent.ACTION_DOWN) {
                v.performClick()
                if (wasDoubleClickUp) {
                    wasDoubleClickUp = false
                }
                if (isDoubleClick) {
                } else if (doubleClickEnable) {
                    val now = System.currentTimeMillis()
                    if (now - firstTapTime < 300 && firstTapTime > 0) {
                        val dx = (event.getX(0) - firstTapX) / density
                        val dy = (event.getY(0) - firstTapY) / density
                        val distDp = Math.sqrt((dx * dx + dy * dy).toDouble())
                        if (distDp < 32.0 && slots.count { it != null } < 2) {
                            handler.removeCallbacks(doubleTapTimeout)
                            v.isPressed = true; isDoubleClick = true; firstTapTime = 0
                            a.viewModel.onButtonDown(GamepadState.TOUCHPAD_CLICK)
                            a.viewModel.triggerHapticPress()
                        } else {
                            firstTapTime = now; firstTapX = event.getX(0); firstTapY = event.getY(0); isDoubleClick = false
                            handler.postDelayed(doubleTapTimeout, 300)
                        }
                    } else {
                        firstTapTime = now; firstTapX = event.getX(0); firstTapY = event.getY(0); isDoubleClick = false
                        handler.postDelayed(doubleTapTimeout, 300)
                    }
                }
            }
            send()
            return@touchpadListenerLabel true
        }

        if (masked == MotionEvent.ACTION_POINTER_UP) {
            val idx = event.actionIndex
            val (sx, sy) = mapPoint(event.getX(idx), event.getY(idx))
            val slot = nearestSlot(sx, sy)
            if (slot >= 0) slots[slot] = null
            if (slots.all { it == null }) touchpadAlpha(false)
            send()
            return@touchpadListenerLabel true
        }

        if (masked == MotionEvent.ACTION_MOVE) {
            for (i in 0 until event.pointerCount) {
                val (sx, sy) = mapPoint(event.getX(i), event.getY(i))
                val slot = nearestSlot(sx, sy)
                if (slot >= 0) slots[slot] = slots[slot]!!.copy(x = sx, y = sy)
                else { val e = emptySlot(); if (e >= 0) slots[e] = TouchPoint(id = e, x = sx, y = sy, active = true) }
            }
            send()
            return@touchpadListenerLabel true
        }

        true
    }
    // When connected over Bluetooth, a regular touchpad control simulates a mouse
    // (single-finger move, two-finger scroll, tap = click, etc.) using default
    // sensitivity / scroll-direction settings.
    val mouseListener = a.attachMousepadGestures(tp, useConfig = false)
    tp.setOnTouchListener { v, event ->
        if (a.viewModel.settings.value.connectionMode == com.zyz4.gkme.model.ConnectionMode.BLUETOOTH) {
            mouseListener.onTouch(v, event)
        } else {
            touchpadListener.onTouch(v, event)
        }
    }
}

/**
 * Set up a mousepad control.
 *
 * Uses GestureDetectorCompat on the mousepad View itself
 * (exactly like Mousedroid's GestureHandler on the touchpadSensor View).
 * Works regardless of pointer capture / connection mode.
 *
 * 手势动作由LayoutCenter的“Touchpad (mouse)”控件配置（[ButtonPosition] 的
 * singleTapAction / twoFingerTapAction / threeFingerTapAction /
 * doubleTapDragAction / oneFingerSwipeAction / twoFingerSwipeAction /
 * threeFingerSwipeAction），默认值为:
 *   Single tap             → Left click
 *   Two-finger tap         → Right click
 *   Three-finger tap         → Middle click
 *   Double-tap and drag       → Left-button drag
 *   One-finger swipe         → Move cursor
 *   Two-finger swipe         → Scroll
 *   Three-finger swipe         → Scroll
 */
@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupMousepadView(mp: FrameLayout) {
    mp.setOnTouchListener(attachMousepadGestures(mp, useConfig = true))
}

/**
 * Attach mouse-pad gesture handling to a [FrameLayout] and return the
 * [View.OnTouchListener] to install on it.
 *
 * @param useConfig when true, sensitivity / scroll direction are read from the
 *   layout [ButtonPosition] (dedicated "mousepad" control). When false, default
 *   settings are used — this is how a regular "touchpad" control simulates a
 *   mouse over a Bluetooth connection.
 */
@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.attachMousepadGestures(mp: FrameLayout, useConfig: Boolean): View.OnTouchListener {
    val a = this
    var activeDragBit = 0           // Mouse板自身拖拽动作Press的键 bits（0=None）
    var gestureMaxPointers = 0      // 本次手势Center同时Press的最大手指数
    var gestureMoved = false        // 本次手势是否明显移动（区分点击与滑动）
    var lastSingleTapUpTime = 0L    // Up一次单指轻点抬起时刻（双击判定）
    var doubleTapArmed = false      // 第二击已Press，滑动时执行“Double-tap and drag”动作
    var pendingClickRelease: Runnable? = null  // Single tap后延迟释放（可被第二击Cancel以衔接拖拽）
    var pendingClickBit = 0         // 待释放的Single tap键 bits（0=None）
    val HOLD_FOR_DOUBLE_MS = 200L   // 可衔接拖拽时Single tap的HoldDuration
    val SCROLL_SENSITIVITY = 0.3f  // Scroll sensitivity默认值（像素 -> 滚轮单 bits，越小越慢）
    var wheelAccumX = 0f           // 双指Scroll的水平/垂直累积量（传统Scroll，降Sensitivity用）
    var wheelAccumY = 0f
    var cursorAccumX = 0f          // Move cursor的累积量（Mouse sensitivity <1 时保留小数 bits移）
    var cursorAccumY = 0f
    var mouseSens = 1f             // Mouse sensitivity（来自Layout配置）
    var mouseAccel = 0f            // Mouse加速度（来自Layout配置，0=None加速度）
    var moveSlop = 4f              // Swipe threshold（来自Layout配置，累计 bits移超过它才判定为滑动）
    var gestureTravel = 0f         // 本次手势累计 bits移
    var scrollSens = SCROLL_SENSITIVITY // Scroll sensitivity（来自Layout配置）
    var invertScrollV = false      // Invert vertical scrolling方向（来自Layout配置）
    var invertScrollH = false      // Invert horizontal scrolling方向（来自Layout配置）
    // ── 手势动作（来自Layout配置，默认值见 MouseGestureAction）──
    var singleTapAction = MouseGestureAction.LEFT_CLICK
    var twoFingerTapAction = MouseGestureAction.RIGHT_CLICK
    var threeFingerTapAction = MouseGestureAction.MIDDLE_CLICK
    var doubleTapDragAction = MouseGestureAction.LEFT_DRAG
    var oneFingerSwipeAction = MouseGestureAction.MOVE_CURSOR
    var twoFingerSwipeAction = MouseGestureAction.SCROLL
    var threeFingerSwipeAction = MouseGestureAction.SCROLL
    val tapThreshold = 200L        // Single tap判定阈值: 手指Hold小于此值算Single tap
    val clickHoldTime = 40L        // 模拟点击的HoldDuration（Press→释放的间隔）

    // per-pointer down times for right-click detection
    val pointerDownTimes = mutableMapOf<Int, Long>()
    // per-pointer last-known position for delta calculation
    val prevX = mutableMapOf<Int, Float>()
    val prevY = mutableMapOf<Int, Float>()

    val DOUBLE_TAP_WINDOW = 200L   // 第一击Press后一定时间的再次轻点 -> 潜在双击/Hold拖动

    fun readConfig() {
        if (!useConfig) return
        val id = mp.tag as? String ?: return
        val pos = a.gamepadLayout.currentButtons.find { it.id == id } ?: return
        mouseSens = pos.mouseSensitivity
        mouseAccel = pos.mouseAcceleration?.let {
            if (it.isEmpty() || it.size < 2) 0f else it[1]
        } ?: 0f
        moveSlop = pos.mouseMoveSlop.toFloat()
        scrollSens = pos.scrollSensitivity
        invertScrollV = pos.invertScrollV
        invertScrollH = pos.invertScrollH
        singleTapAction = pos.singleTapAction
        twoFingerTapAction = pos.twoFingerTapAction
        threeFingerTapAction = pos.threeFingerTapAction
        doubleTapDragAction = pos.doubleTapDragAction
        oneFingerSwipeAction = pos.oneFingerSwipeAction
        twoFingerSwipeAction = pos.twoFingerSwipeAction
        threeFingerSwipeAction = pos.threeFingerSwipeAction
    }

    // Mouse板手势只写 ViewModel 的“手势键 bits”，与屏幕 LMB/RMB/MMB Button分开Save，
    // Up报时并集；这样Hold屏幕Button再点/滑Mouse板不会把它Release。
    fun press(bit: Int) {
        a.viewModel.onMouseGestureButton(bit, true)
    }
    fun release(bit: Int) {
        a.viewModel.onMouseGestureButton(bit, false)
    }
    fun releaseAll() {
        if (activeDragBit != 0) {
            release(activeDragBit)
            activeDragBit = 0
        }
    }
    /**
     * 发送一次点击（Press后延迟释放）。[trackForDouble] 为 true 时记录待释放
     * Status，第二击Press时可Cancel释放，使按键保持以None缝衔接“Double-tap and drag”。
     */
    fun click(bit: Int, holdMs: Long = clickHoldTime, trackForDouble: Boolean = false) {
        press(bit)
        val r = object : Runnable {
            override fun run() {
                release(bit)
                if (pendingClickRelease === this) {
                    pendingClickRelease = null
                    pendingClickBit = 0
                }
            }
        }
        if (trackForDouble) {
            pendingClickRelease = r
            pendingClickBit = bit
        }
        _mainHandler.postDelayed(r, holdMs)
    }
    fun moveRaw(rawDx: Float, rawDy: Float) {
        var dx = rawDx
        var dy = rawDy
        var accel = 0f
        if (useConfig) {
            val id = mp.tag as? String ?: return
            val pos = a.gamepadLayout.currentButtons.find { it.id == id }
            if (pos != null) {
                accel = pos.mouseAcceleration?.let {
                    if (it.isEmpty() || it.size < 2) 0f else it[1]
                } ?: 0f
                val rot = pos.rotation % 360
                if (rot == 90) {
                    val tmp = dy
                    dy = (-dx).toFloat()
                    dx = tmp.toFloat()
                } else if (rot == 180) {
                    dx = (-dx).toFloat()
                    dy = (-dy).toFloat()
                } else if (rot == 270) {
                    val tmp = dy
                    dy = dx.toFloat()
                    dx = (-tmp).toFloat()
                }
            }
        }
        val rawSpeed = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val finalSpeedMultiplier = if (rawSpeed > 0) 1f + accel * rawSpeed else 1f
        dx *= finalSpeedMultiplier
        dy *= finalSpeedMultiplier
        cursorAccumX += dx * mouseSens
        cursorAccumY += dy * mouseSens
        var ix = cursorAccumX.toInt()
        var iy = cursorAccumY.toInt()
        cursorAccumX -= ix
        cursorAccumY -= iy
        // Mouse报文已改为 16  bits有符号字段，None需再按 ±127 拆帧。
        val btnNow = a.viewModel.effectiveMouseButtons()
        if (ix != 0 || iy != 0) {
            a.sendMouseReportDirect(
                buttonDown = btnNow, buttonUp = 0,
                dx = ix.coerceIn(-32767, 32767).toShort(),
                dy = iy.coerceIn(-32767, 32767).toShort(),
            )
        }
    }
    /** 双指/Three-finger swipe的Scroll处理（含Layout旋转、反转与 WiFi 缩放）。 */
    fun doScroll(rawDx: Float, rawDy: Float) {
        var scrollDx = rawDx
        var scrollDy = rawDy
        if (useConfig) {
            val id = mp.tag as? String
            val pos = if (id != null) a.gamepadLayout.currentButtons.find { it.id == id } else null
            if (pos != null) {
                val rot = pos.rotation % 360
                if (rot == 90) {
                    val tmp = scrollDy
                    scrollDy = -scrollDx
                    scrollDx = tmp
                } else if (rot == 180) {
                    scrollDx = -scrollDx
                    scrollDy = -scrollDy
                } else if (rot == 270) {
                    val tmp = scrollDy
                    scrollDy = scrollDx
                    scrollDx = -tmp
                }
            }
        }
        val sDy = if (invertScrollV) -scrollDy else scrollDy
        val sDx = if (invertScrollH) scrollDx else -scrollDx
        val connMode = a.viewModel.settings.value.connectionMode
        // Bluetooth目标在 Windows Up不会做 Resolution Multiplier 初始化（实测主机只走Center断通道，
        // 从不发 Feature 报表），因此Bluetooth仍按传统“每单 bits = 1 格”Down发；WiFi/USB 经 GKMD
        // 走 120 计数的高精度单 bits。
        val scrollFactor = if (connMode == ConnectionMode.WIFI || connMode == ConnectionMode.USB) 33f else 1f
        wheelAccumY += sDy * scrollSens * scrollFactor
        wheelAccumX += sDx * scrollSens * scrollFactor
        val wY = wheelAccumY.toInt().coerceIn(-32767, 32767)
        val wX = wheelAccumX.toInt().coerceIn(-32767, 32767)
        wheelAccumY -= wY
        wheelAccumX -= wX
        if (wX != 0 || wY != 0) {
            a.sendMouseReportDirect(
                buttonDown = a.viewModel.effectiveMouseButtons(), buttonUp = 0, dx = 0, dy = 0,
                wheel = wY.toShort(), hWheel = wX.toShort()
            )
        }
    }

    /** 连续滑动手势: Move cursor / 拖拽 / Scroll。 */
    fun applySwipe(action: MouseGestureAction, dx: Float, dy: Float) {
        when (action) {
            MouseGestureAction.MOVE_CURSOR -> moveRaw(dx, dy)
            MouseGestureAction.SCROLL -> doScroll(dx, dy)
            MouseGestureAction.LEFT_DRAG -> {
                if (activeDragBit == 0) { activeDragBit = 1; press(1) }
                moveRaw(dx, dy)
            }
            MouseGestureAction.RIGHT_DRAG -> {
                if (activeDragBit == 0) { activeDragBit = 2; press(2) }
                moveRaw(dx, dy)
            }
            MouseGestureAction.MIDDLE_DRAG -> {
                if (activeDragBit == 0) { activeDragBit = 4; press(4) }
                moveRaw(dx, dy)
            }
            else -> {}
        }
    }

    /** 点击手势: Left键 / Right键 / Center键。 */
    fun applyTap(action: MouseGestureAction, holdMs: Long = clickHoldTime, trackForDouble: Boolean = false) {
        when (action) {
            MouseGestureAction.LEFT_CLICK -> click(1, holdMs, trackForDouble)
            MouseGestureAction.RIGHT_CLICK -> click(2, holdMs, trackForDouble)
            MouseGestureAction.MIDDLE_CLICK -> click(4, holdMs, trackForDouble)
            else -> {}
        }
    }

    fun swipeActionFor(count: Int): MouseGestureAction = when (count) {
        1 -> if (doubleTapArmed) doubleTapDragAction else oneFingerSwipeAction
        2 -> twoFingerSwipeAction
        3 -> threeFingerSwipeAction
        else -> MouseGestureAction.NONE
    }

    fun tapActionFor(count: Int): MouseGestureAction = when (count) {
        1 -> singleTapAction
        2 -> twoFingerTapAction
        3 -> threeFingerTapAction
        else -> MouseGestureAction.NONE
    }

    return View.OnTouchListener { _, event ->
        val masked = event.action and MotionEvent.ACTION_MASK
        val pointerCount = event.pointerCount

        when (masked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                pointerDownTimes[pid] = event.eventTime
                prevX[pid] = event.getX(idx)
                prevY[pid] = event.getY(idx)
                gestureMoved = false
                gestureMaxPointers = 1
                gestureTravel = 0f
                wheelAccumX = 0f
                wheelAccumY = 0f
                cursorAccumX = 0f
                cursorAccumY = 0f
                readConfig()

                // 距Up一次单指轻点足够近 -> 本次视为双击的第二击
                doubleTapArmed = doubleTapDragAction != MouseGestureAction.NONE &&
                        lastSingleTapUpTime > 0 &&
                        (event.eventTime - lastSingleTapUpTime) < DOUBLE_TAP_WINDOW
                lastSingleTapUpTime = 0L

                if (doubleTapArmed) {
                    // CancelUp一击的延迟释放，使按键保持，None缝衔接“Double-tap and drag”
                    pendingClickRelease?.let { _mainHandler.removeCallbacks(it) }
                    pendingClickRelease = null
                    val dragBit = when (doubleTapDragAction) {
                        MouseGestureAction.LEFT_DRAG -> 1
                        MouseGestureAction.RIGHT_DRAG -> 2
                        MouseGestureAction.MIDDLE_DRAG -> 4
                        else -> 0
                    }
                    // Up一击Hold的键 bits与拖拽键 bits不同则先释放
                    if (pendingClickBit != 0 && pendingClickBit != dragBit) {
                        release(pendingClickBit)
                    }
                    pendingClickBit = 0
                    if (dragBit != 0) {
                        activeDragBit = dragBit
                        press(dragBit)
                    }
                    mp.isPressed = true
                    mousepadHighlight(mp, true, a)
                    a.performHaptic(isPress = true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pid = event.getPointerId(idx)
                pointerDownTimes[pid] = event.eventTime
                prevX[pid] = event.getX(idx)
                prevY[pid] = event.getY(idx)
                if (pointerCount > gestureMaxPointers) gestureMaxPointers = pointerCount
                wheelAccumX = 0f
                wheelAccumY = 0f
                // 第二指落Down: Cancel“Double-tap and drag”保持，避免按键卡住
                if (doubleTapArmed) {
                    if (activeDragBit != 0) { release(activeDragBit); activeDragBit = 0 }
                    doubleTapArmed = false
                    mp.isPressed = false
                    mousepadHighlight(mp, false, a)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val liftedPid = event.getPointerId(idx)
                pointerDownTimes.remove(liftedPid)
                prevX.remove(liftedPid)
                prevY.remove(liftedPid)
            }
            MotionEvent.ACTION_MOVE -> {
                var totalDx = 0f
                var totalDy = 0f
                val count = pointerCount
                for (i in 0 until count) {
                    val pid = event.getPointerId(i)
                    val ex = event.getX(i)
                    val ey = event.getY(i)
                    val prevPx = prevX[pid] ?: ex
                    val prevPy = prevY[pid] ?: ey
                    totalDx += (ex - prevPx)
                    totalDy += (ey - prevPy)
                    prevX[pid] = ex
                    prevY[pid] = ey
                }
                if (count > 0) {
                    totalDx /= count
                    totalDy /= count
                }
                if (count > gestureMaxPointers) gestureMaxPointers = count

                // 累计 bits移超过阈值即判定为滑动，之后即使单次 bits移减小也保持滑动
                gestureTravel += Math.abs(totalDx) + Math.abs(totalDy)
                if (!gestureMoved && gestureTravel > moveSlop) {
                    gestureMoved = true
                }
                if (gestureMoved) {
                    applySwipe(swipeActionFor(count), totalDx, totalDy)
                }
            }
            MotionEvent.ACTION_UP -> {
                val pid = event.getPointerId(event.actionIndex)
                val downTime = pointerDownTimes[pid] ?: event.downTime
                val dur = event.eventTime - downTime
                pointerDownTimes.remove(pid)
                prevX.remove(pid)
                prevY.remove(pid)

                val wasTap = !gestureMoved && dur < tapThreshold

                // 先结束拖拽保持（点击会重新Press）
                if (activeDragBit != 0) {
                    release(activeDragBit)
                    activeDragBit = 0
                }
                if (doubleTapArmed) {
                    mp.isPressed = false
                    mousepadHighlight(mp, false, a)
                    a.performHaptic(isPress = false)
                }
                if (wasTap) {
                    if (gestureMaxPointers == 1) {
                        if (doubleTapArmed) {
                            // 第二击轻点: 立即点击形成双击，不再衔接拖拽
                            applyTap(singleTapAction)
                        } else {
                            lastSingleTapUpTime = event.eventTime
                            // “Double-tap and drag”非“None”时，Single tapHold 200ms 并准备衔接拖拽
                            val track = doubleTapDragAction != MouseGestureAction.NONE
                            applyTap(
                                singleTapAction,
                                if (track) HOLD_FOR_DOUBLE_MS else clickHoldTime,
                                track,
                            )
                        }
                    } else {
                        applyTap(tapActionFor(gestureMaxPointers))
                    }
                } else {
                    // 结束本次手势可能遗留的拖拽键 bits
                    releaseAll()
                }
                doubleTapArmed = false
                gestureMoved = false
                gestureMaxPointers = 0
                gestureTravel = 0f
                wheelAccumX = 0f
                wheelAccumY = 0f
                cursorAccumX = 0f
                cursorAccumY = 0f
            }
            MotionEvent.ACTION_CANCEL -> {
                if (doubleTapArmed) {
                    mp.isPressed = false
                    mousepadHighlight(mp, false, a)
                    a.performHaptic(isPress = false)
                }
                pendingClickRelease?.let { _mainHandler.removeCallbacks(it) }
                pendingClickRelease = null
                pendingClickBit = 0
                releaseAll()
                doubleTapArmed = false
                gestureMoved = false
                gestureMaxPointers = 0
                pointerDownTimes.clear()
                prevX.clear()
                prevY.clear()
                wheelAccumX = 0f
                wheelAccumY = 0f
                cursorAccumX = 0f
                cursorAccumY = 0f
            }
        }
        true
    }
}

/** Mousepad visual highlight (alpha) matching touchpad behavior. */
private fun mousepadHighlight(mp: FrameLayout, active: Boolean, a: MainActivity) {
    val id = mp.tag as? String ?: return
    val pos = a.gamepadLayout.currentButtons.find { it.id == id } ?: return
    mp.alpha = (if (active) pos.activeOpacity else pos.idleOpacity).coerceIn(0, 100) / 100f
}

/**
 * Send a mouse report with button state changes and delta movement.
 * Routes through [ConnectionManager.sendMouseReport] which handles both
 * WiFi (one-shot protobuf) and Bluetooth (HID Report 18). The report is
 * fire-and-forget so it is never re-sent by the periodic gamepad loop,
 * which previously caused cursor drift after lifting the finger.
 */
private fun MainActivity.sendMouseReportDirect(
    buttonDown: Int, buttonUp: Int,
    dx: Short, dy: Short, wheel: Short = 0, hWheel: Short = 0
) {
    val button = (buttonDown or buttonUp).toByte()
    this.viewModel.connectionManager.sendMouseReport(
        button = button,
        dx = dx,
        dy = dy,
        wheel = wheel,
        hWheel = hWheel
)
}

@SuppressLint("ClickableViewAccessibility")
internal fun MainActivity.setupCustomTouchHandler(view: View) {
    val a = this
    val id = view.tag as String
    val curPos = id.let { a.gamepadLayout.currentButtons.find { it.id == id } }
    val holdEnabled = curPos?.autoHold == true
    val gyroActivate = curPos?.gyroActivate == true
    val tracker = ButtonTracker()
    val customBits = curPos?.customBits.orEmpty()
    val handler = object : ButtonEventHandler {
        override fun onPress(viewId: String, bit: Int) {
            val bits = a.gamepadLayout.currentButtons.find { it.id == viewId }?.customBits.orEmpty()
            a.viewModel.onCustomButtonDown(bits)
        }
        override fun onRelease(viewId: String, bit: Int) {
            val bits = a.gamepadLayout.currentButtons.find { it.id == viewId }?.customBits.orEmpty()
            a.viewModel.onCustomButtonUp(bits)
        }
        override fun onGyroActivateDown(viewId: String) {
            if (gyroActivate) a.viewModel.onGyroActivateButtonDown()
        }
        override fun onGyroActivateUp(viewId: String) {
            if (gyroActivate) a.viewModel.onGyroActivateButtonUp()
        }
    }
    view.setOnTouchListener { v, e ->
        val viewId = v.tag as? String ?: ""
        tracker.feed(e, v, viewId, 0, holdEnabled, gyroActivate, handler)
    }
}

@SuppressLint("SetTextI18n")
internal fun MainActivity.updateButtonLabels(mode: DisplayMode) {
    val a = this
    val abxyList = listOf("btnA", "btnB", "btnX", "btnY")
    val bumperList = listOf("btnLB", "btnRB", "btnLT", "btnRT")

    for (i in 0 until a.gamepadLayout.childCount) {
        val child = a.gamepadLayout.getChildAt(i)
        val tag = child.tag as? String ?: continue
        val baseId = tag.substringBefore("_") ?: continue

        if (baseId.startsWith("btnCustom")) {
            val pos = a.gamepadLayout.currentButtons.find { it.id == tag }
            (child as? TextView)?.apply {
                text = (pos?.customText ?: "Custom").ifEmpty { "Custom" }
                setAllCaps(false)
                textSize = 20f
            }
            continue
        }

        when {
            baseId in abxyList -> {
                val idx = abxyList.indexOf(baseId)
                (child as? Button)?.apply {
                    when (mode) {
                        DisplayMode.XBOX -> {
                            text = listOf("A", "B", "X", "Y")[idx]; textSize = 20f
                            setBackgroundResource(R.drawable.button_circle)
                            foreground = null
                        }
                        DisplayMode.PLAYSTATION -> {
                            text = ""; textSize = 12f
                            setBackgroundResource(R.drawable.button_circle)
                            foreground = context.getDrawable(intArrayOf(
                                R.drawable.ic_ps_cross, R.drawable.ic_ps_circle,
                                R.drawable.ic_ps_square, R.drawable.ic_ps_triangle
                            )[idx])?.mutate()
                            foregroundGravity = android.view.Gravity.CENTER
                        }
                        DisplayMode.SWITCH -> {
                            text = listOf("B", "A", "Y", "X")[idx]; textSize = 20f
                            setBackgroundResource(R.drawable.button_circle)
                            foreground = null
                        }
                    }
                }
            }
            baseId in bumperList -> {
                val idx = bumperList.indexOf(baseId)
                when (mode) {
                    DisplayMode.XBOX -> {
                        val labels = listOf("LB", "RB", "LT", "RT")
                        for (i in 0 until a.gamepadLayout.childCount) {
                            val child = a.gamepadLayout.getChildAt(i)
                            if (child.tag.toString().startsWith(baseId)) {
                                if (child is Button) child.text = labels[idx]
                                break
                            }
                        }
                    }
                    DisplayMode.PLAYSTATION -> {
                        val labels = listOf("L1", "R1", "L2", "R2")
                        for (i in 0 until a.gamepadLayout.childCount) {
                            val child = a.gamepadLayout.getChildAt(i)
                            if (child.tag.toString().startsWith(baseId)) {
                                if (child is Button) child.text = labels[idx]
                                break
                            }
                        }
                    }
                    DisplayMode.SWITCH -> {
                        val labels = listOf("L", "R", "ZL", "ZR")
                        for (i in 0 until a.gamepadLayout.childCount) {
                            val child = a.gamepadLayout.getChildAt(i)
                            if (child.tag.toString().startsWith(baseId)) {
                                if (child is Button) child.text = labels[idx]
                                break
                            }
                        }
                    }
                }
            }
            baseId == "btnSelect" -> {
                (child as? Button)?.apply {
                    // XBOX/SWITCH: the icon is drawn as foreground content (adaptive), so the
                    // background is the neutral circle. PS: text.
                    when (mode) {
                        DisplayMode.XBOX -> { text = ""; setBackgroundResource(R.drawable.button_circle) }
                        DisplayMode.PLAYSTATION -> {
                            text = "SHARE"
                            setBackgroundResource(R.drawable.button_circle)
                            foreground = null
                        }
                        DisplayMode.SWITCH -> { text = ""; setBackgroundResource(R.drawable.button_circle) }
                    }
                }
            }
            baseId == "btnHome" -> {
                (child as? ImageButton)?.apply {
                    setBackgroundResource(R.drawable.button_circle)
                    // The adaptive cap is applied via the foreground (see AppearanceApplier), so
                    // clear it here: the freshly set image is then taken as the new icon.
                    foreground = null
                    when (mode) {
                        DisplayMode.XBOX -> setImageResource(R.drawable.ic_home_xbox)
                        DisplayMode.PLAYSTATION -> setImageResource(R.drawable.ic_home_playstation)
                        DisplayMode.SWITCH -> setImageResource(R.drawable.ic_home)
                    }
                }
            }
            baseId == "btnMenu" -> {
                (child as? Button)?.apply {
                    when (mode) {
                        DisplayMode.XBOX -> { text = ""; setBackgroundResource(R.drawable.button_circle) }
                        DisplayMode.PLAYSTATION -> {
                            text = "OPTION"
                            setBackgroundResource(R.drawable.button_circle)
                            foreground = null
                        }
                        DisplayMode.SWITCH -> { text = ""; setBackgroundResource(R.drawable.button_circle) }
                    }
                }
            }
            baseId == "btnShare" -> {
                (child as? Button)?.apply {
                    text = ""
                    setBackgroundResource(R.drawable.btn_touchpad)
                    foreground = null
                }
            }
            baseId == "btnLS" -> {
                (child as? Button)?.apply {
                    // Triangle + letter are integrated into a single foreground drawable
                    // (see AppearanceApplier.letterIconDrawable), so no text is needed here.
                    text = ""
                    setBackgroundResource(R.drawable.button_circle)
                    foreground = null
                }
            }
            baseId == "btnRS" -> {
                (child as? Button)?.apply {
                    text = ""
                    setBackgroundResource(R.drawable.button_circle)
                    foreground = null
                }
            }
            baseId in listOf("btnMouseLMB", "btnMouseRMB", "btnMouseMMB", "btnMouseBack", "btnMouseForward") -> {
                (child as? Button)?.apply {
                    text = when (baseId) {
                        "btnMouseLMB" -> "LMB"
                        "btnMouseRMB" -> "RMB"
                        "btnMouseMMB" -> "MMB"
                        "btnMouseBack" -> "BACK"
                        "btnMouseForward" -> "FWD"
                        else -> "MOUSE"
                    }
                    textSize = 12f
                }
            }
        }
    }

    // Re-apply custom appearance after label updates
    a.gamepadLayout.applyAppearance(a.viewModel.settings.value)
    if (a.inSettings && a.currentSettingsCategory == 2) {
        a.findViewById<View>(R.id.previewContainer)?.post {
            a.updateAppearancePreview()
        }
    }
}

// Shift Press时，把数字/符号键的标签切换为Up档字符
internal fun MainActivity.updateKeyboardLabels(shiftActive: Boolean) {
    val a = this
    for (i in 0 until a.gamepadLayout.childCount) {
        val child = a.gamepadLayout.getChildAt(i)
        val tag = child.tag as? String ?: continue
        val entry = allControls.find { it.baseId == tag.substringBefore("_") } ?: continue
        if (!entry.isKeyboard) continue
        (child as? TextView)?.text =
            BitNameMapper.getKeyboardPreviewText(entry.keyboardKeyCode, shiftActive)
    }
}
