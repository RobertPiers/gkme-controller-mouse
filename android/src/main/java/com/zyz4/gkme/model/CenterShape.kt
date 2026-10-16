package com.zyz4.gkme.model

/** D-pad / Custom keypad的Center shape。
 *  SQUARE = SquareCenter心（沿用 3×3 网格 / 楔形划分）；
 *  CIRCLE = CircleCenter心，周围区域改为径向扇环（扇环的判定范围与绘制一致）。 */
enum class CenterShape {
    SQUARE,
    CIRCLE;
}
