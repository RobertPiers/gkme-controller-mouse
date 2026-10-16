package com.zyz4.gkme.controlled;

/**
 * native uinput 虚拟Controller的 Java 封装。只应在 Shizuku UserService 进程Center使用，
 * 该进程拥有 shell/root 身份，可以打开 /dev/uinput。
 */
public final class RemoteGamepadDevice {

    private static final boolean LOADED;
    private static final String LOAD_ERROR;

    static {
        boolean loaded;
        String error = null;
        try {
            System.loadLibrary("gkme_uinput");
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        LOADED = loaded;
        LOAD_ERROR = error;
    }

    private RemoteGamepadDevice() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }

    public static String loadError() {
        return LOAD_ERROR;
    }

    public static native int nativeCreate(int rumbleEnabled);

    public static native void nativeWrite(int fd, int buttons, int leftTrigger, int rightTrigger,
                                          int leftX, int leftY, int rightX, int rightY);

    public static native long nativeRumble(int fd);

    public static native void nativeDestroy(int fd);

    /** CreateVirtual key盘，Back uinput fd（负数表示 -errno）。 */
    public static native int nativeCreateKeyboard();

    /**
     * 写入一次全量KeyboardStatus。
     *
     * @param modifiers HID 修饰键 bits掩码（bit0=LCtrl … bit7=RGui）
     * @param usages    HID Keyboard/Keypad 键 bits用法（0x04..0xFF，修饰键除外）
     */
    public static native void nativeWriteKeyboard(int fd, int modifiers, int[] usages);

    /** Create虚拟Mouse，Back uinput fd（负数表示 -errno）。 */
    public static native int nativeCreateMouse();

    /**
     * 写入一次Mouse帧。Button掩码: bit0 Left键、bit1 Right键、bit2 Center键、bit3 后退、bit4 前进。
     *
     * @param dx/dy  相对 bits移
     * @param wheel  垂直滚轮（正为Up）
     * @param pan    横向滚轮（正为Right）
     */
    public static native void nativeWriteMouse(int fd, int dx, int dy, int wheel, int pan,
                                               int buttons);

    /** 销毁Keyboard/Mouse uinput 设备（与Controller的 nativeDestroy 区分）。 */
    public static native void nativeDestroyInput(int fd);

    // ── uhid 后端（真实 HID 身份，经 /dev/uhid）──────────────────────────

    /**
     * 通过 /dev/uhid Create真实 HID Controller，Back fd（负数表示 -errno）。
     *
     * @param profile 1 = DualShock 4，2 = DualSense，3 = Switch Pro
     */
    public static native int nativeCreateUhid(int profile, int rumbleEnabled);

    /**
     * 写入一次 uhid ControllerStatus。
     *
     * @param touches Touchpad数据，最多 2 个触点，每点 4 个 int: [id, x, y, active]，
     *                x/y 为 0..1919/0..942；仅 DS4/DualSense 使用，可传 null。
     */
    public static native void nativeWriteUhid(int fd, int buttons, int leftTrigger,
                                              int rightTrigger, int leftX, int leftY,
                                              int rightX, int rightY,
                                              float gyroX, float gyroY, float gyroZ,
                                              float accelX, float accelY, float accelZ,
                                              int[] touches);

    public static native long nativeRumbleUhid(int fd);

    public static native void nativeDestroyUhid(int fd);

    public static native int nativeCreateKeyboardUhid();

    public static native void nativeWriteKeyboardUhid(int fd, int modifiers, int[] usages);

    public static native int nativeCreateMouseUhid();

    public static native void nativeWriteMouseUhid(int fd, int dx, int dy, int wheel, int pan,
                                                   int buttons);

    public static native void nativeDestroyUhidInput(int fd);
}
