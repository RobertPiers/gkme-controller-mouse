package com.zyz4.gkme.model

/**
 * Mouse触控板手势动作。
 *
 * 点击类手势（Single tap / Two-finger tap / Three-finger tap）只使用 [NONE] + 三种Mouse点击；
 * 滑动类手势（Double-tap and drag / 单指 / 双指 / Three-finger swipe）额外SupportedMove cursor、拖拽与Scroll。
 */
enum class MouseGestureAction(val displayName: String) {
    NONE("None"),
    MOVE_CURSOR("Move cursor"),
    LEFT_CLICK("Left click"),
    RIGHT_CLICK("Right click"),
    MIDDLE_CLICK("Middle click"),
    LEFT_DRAG("Left-button drag"),
    RIGHT_DRAG("Right-button drag"),
    MIDDLE_DRAG("Middle-button drag"),
    SCROLL("Scroll");

    companion object {
        /** 点击手势可选动作（4 项）。 */
        val TAP_ACTIONS: List<MouseGestureAction> = listOf(
            NONE, LEFT_CLICK, RIGHT_CLICK, MIDDLE_CLICK,
        )

        /** 滑动手势可选动作（6 项）。 */
        val SWIPE_ACTIONS: List<MouseGestureAction> = listOf(
            NONE, MOVE_CURSOR, LEFT_DRAG, RIGHT_DRAG, MIDDLE_DRAG, SCROLL,
        )

        /** 解析持久化的动作名，未知值回退到 [NONE]。 */
        fun fromName(name: String?): MouseGestureAction =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
    }
}
