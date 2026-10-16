package com.zyz4.gkme.haptic

/**
 * HE（RichTap Dynamic效果）的发送来源。
 *
 * 手机线性马达只有一个 `HapticPlayer`，但Adaptive triggers、Audio到Vibration、Game vibration、Button vibration四条
 * 通路都会向它投递效果。若没有仲裁，它们会交替 `start()`，马达收到互相交错的幅度/频率，
 * 表现为“Vibration混乱”。
 *
 * 这里给每条通路一个优先级: 同一时刻只允许一个来源Driver；高优先级可抢占低优先级，低优先级
 * 在更高来源播放期间被**静默**（不发效果，也不回退系统Vibration）。优先级按需求排列:
 * Adaptive triggers > Audio到Vibration > Game vibration > Button vibration。
 */
enum class HapticSource(val priority: Int) {
    /** 按键CustomVibration / 系统触感替换。 */
    BUTTON(0),

    /** PC 游戏 rumble（强/弱马达）。 */
    GAME_RUMBLE(1),

    /** 语音线圈 PCM（Audio到Vibration）。 */
    AUDIO(2),

    /** Adaptive triggers转Vibration。 */
    ADAPTIVE_TRIGGER(3),
}
