package com.zyz4.gkme.controlled

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku 用户服务的“授权 + 绑定”封装（与具体业务解耦，单独成文件）。
 *
 * 负责监听 binder 存活/Disconnect、申请权限、绑定/解绑用户服务，并把Connect事件回调给调用方。
 * 调用方只需在 [onConnected] 里把 binder 转成具体接口，在 [onDisconnected] 里Clear引用。
 */
class ShizukuServiceBinding(
    private val tag: String,
    private val processNameSuffix: String,
    private val serviceClass: Class<*>,
    /**
     * 是否以 daemon 模式运行用户服务。
     *
     * `false`（默认）时，App 进程死亡后服务端会一并结束用户服务；`true` 时用户服务
     * 会一直存活到显式 [removeService]，因此可在 App 被杀后自行把 App 拉回来（保活用）。
     */
    private val daemon: Boolean = false,
    private val onConnected: (IBinder) -> Unit,
    private val onDisconnected: () -> Unit,
) {

    /** 用户Down一步需要执行的操作。 */
    enum class Action { DOWNLOAD, OPEN, REQUEST_PERMISSION, NONE }

    @Volatile
    var binderAlive: Boolean = false
        private set

    /**
     * 是否已获得 Shizuku 授权。
     *
     * Shizuku 授权是**应用级**的，因此这里读取 [ShizukuPermission] 的共享Status，而不是每个
     * binding 各存一份——否则Controller/Vibration/保活三个服务会出现“一个说Authorized、另一个说未授权”
     * 的不一致。读取时按需复核（内部 1s 节流），保证界面Display的始终是服务端真实Status。
     */
    val permissionGranted: Boolean get() = ShizukuPermission.refresh()

    /** 授权申请已被用户Deny，调用方据此判定“授权失败”。 */
    val permissionDenied: Boolean get() = ShizukuPermission.denied

    /** 用户服务是否已真正Connect（由 `onServiceConnected` 置 bits，而非绑定请求发出即为 true）。 */
    @Volatile
    var bound: Boolean = false
        private set

    /** 绑定请求是否在途，用于超时后重发，避免用户服务启动失败时永久卡住。 */
    @Volatile
    private var bindInFlight = false

    @Volatile
    private var bindRequestedAt = 0L

    /** 连续绑定尝试次数（未连Up前累加，连Up后清零），用于触发强制重建。 */
    @Volatile
    private var bindAttempts = 0

    /**
     * 用户服务长时间None法Connect（疑似 Shizuku 服务Status异常，例如存在多个 `shizuku_server`
     * 实例导致 token 不匹配）。置 bits后界面会提示用户重启手机。Connect成功后自动清除。
     */
    @Volatile
    var bindFailed: Boolean = false
        private set

    @Volatile
    var lastError: String? = null
        private set

    private var handler: Handler? = null
    private var args: Shizuku.UserServiceArgs? = null
    private var initialized = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) return
            bound = true
            bindInFlight = false
            bindAttempts = 0
            bindFailed = false
            lastError = null
            Log.i(tag, "Shizuku user service connected (${serviceClass.simpleName})")
            onConnected(binder)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            bindInFlight = false
            bindAttempts = 0
            bindFailed = false
            Log.w(tag, "Shizuku user service disconnected (${serviceClass.simpleName})")
            onDisconnected()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        binderAlive = true
        ShizukuPermission.refresh(force = true)
        if (permissionGranted) ensureBound()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        binderAlive = false
        bound = false
        bindInFlight = false
        bindAttempts = 0
        bindFailed = false
        lastError = "Shizuku stopped"
        ShizukuPermission.onBinderDead()
        onDisconnected()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { code, grantResult ->
            if (code != REQUEST_CODE) return@OnRequestPermissionResultListener
            ShizukuPermission.onRequestResult(grantResult == PackageManager.PERMISSION_GRANTED)
            if (permissionGranted) {
                lastError = null
                ensureBound()
            } else {
                lastError = "Shizuku permission denied"
            }
        }

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        handler = Handler(Looper.getMainLooper())
        try {
            args = Shizuku.UserServiceArgs(
                ComponentName(app.packageName, serviceClass.name)
            )
                .daemon(daemon)
                .processNameSuffix(processNameSuffix)
                .debuggable(false)
                .version(2)
                .tag(tag)

            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (t: Throwable) {
            lastError = "Shizuku initialization failed: ${t.message}"
            Log.e(tag, "init failed", t)
        }
    }

    /**
     * 申请 Shizuku 权限。
     *
     * 自动流程（[ensureBound] 等由轮询反复调用）只会真正发起一次申请，被Deny后不再重复弹窗；
     * 用户手动点击「Request permission”时传 [force] = true 可再次申请。
     */
    fun requestPermission(force: Boolean = false) {
        handler?.post {
            try {
                if (Shizuku.isPreV11()) {
                    lastError = "Shizuku version is too old. Please update Shizuku."
                    return@post
                }
                // 先按服务端真实StatusRefresh: 例如用户已在 Shizuku Center手动授权。
                if (ShizukuPermission.refresh(force = true)) {
                    lastError = null
                    ensureBound()
                    return@post
                }
                if (ShizukuPermission.requestInFlight) return@post
                if (ShizukuPermission.autoPrompted && !force) return@post
                if (force) ShizukuPermission.clearDenied()
                ShizukuPermission.markAutoPrompted()
                ShizukuPermission.markRequestInFlight()
                Log.i(tag, "Requesting Shizuku permission requestCode=$REQUEST_CODE force=$force")
                Shizuku.requestPermission(REQUEST_CODE)
            } catch (t: Throwable) {
                ShizukuPermission.clearRequestInFlight()
                lastError = "Failed to request Shizuku permission: ${t.message}"
            }
        }
    }

    fun ensureBound() {
        handler?.post {
            val a = args ?: return@post
            if (bound) return@post
            if (!binderAlive) {
                Log.d(tag, "ensureBound skipped: Shizuku is not running (${serviceClass.simpleName})")
                return@post
            }
            // 始终按服务端真实Status复核（内部节流），避免「缓存为 true 但实际已失效”
            // 或「缓存为 false 但用户已在 Shizuku Center授权”导致界面与操作不一致。
            if (!ShizukuPermission.refresh()) {
                requestPermission()
                return@post
            }
            val now = android.os.SystemClock.uptimeMillis()
            // 绑定请求在途时等待 onServiceConnected；超过重试间隔仍未连Up则重发。
            // Shizuku 服务端启动用户服务失败/超时（默认 30s）时不会回调 onServiceDisconnected，
            // 若不重试会永久卡在 bound=false、service=null。
            if (bindInFlight && now - bindRequestedAt < BIND_RETRY_INTERVAL_MS) return@post
            // 已重发过一次仍未连Up: 记录可能卡在服务端 "starting"（30s 超时才移除），
            // 主动移除强制重建，避免「Authorized但用户服务一直起不来”长时间None响应。
            if (bindAttempts >= 2) {
                // 已是第 3 次尝试仍连不Up: 判为 Shizuku 服务Status异常并提示用户。
                bindFailed = true
                Log.w(tag, "User service failed to connect repeatedly; removing and rebuilding (${serviceClass.simpleName})")
                try {
                    Shizuku.unbindUserService(a, connection, true)
                } catch (_: Throwable) {
                }
            }
            bindInFlight = true
            bindRequestedAt = now
            bindAttempts++
            Log.i(
                tag,
                "bindUserService attempt #$bindAttempts (${serviceClass.simpleName}, daemon=$daemon)",
            )
            try {
                Shizuku.bindUserService(a, connection)
                lastError = null
            } catch (t: Throwable) {
                bindInFlight = false
                lastError = "Failed to bind Shizuku user service: ${t.message}"
                Log.w(tag, "bindUserService failed (${serviceClass.simpleName})", t)
                // 权限/客户端Status异常: 作废缓存，Down一次 ensureBound 会重新检查并申请。
                if (t is SecurityException || t is IllegalStateException) {
                    ShizukuPermission.invalidate()
                }
            }
        }
    }

    /** 是否已运行、Authorized且已绑定用户服务（不代表具体能力就绪）。 */
    fun isBound(): Boolean = binderAlive && permissionGranted && bound

    fun unbind(remove: Boolean = false) {
        val a = args
        if (a != null) {
            // 即便尚未 onServiceConnected，也可能已在服务端登记了回调，一律注销一次。
            try {
                Shizuku.unbindUserService(a, connection, remove)
            } catch (_: Throwable) {
            }
        }
        bound = false
        bindInFlight = false
        bindAttempts = 0
        bindFailed = false
    }

    /**
     * 彻底移除用户服务: 即使当前未处于 bound Status（例如 App 进程曾被重建、daemon 用户
     * 服务仍在运行），也会请求服务端结束该服务并触发其 `exitService`。用于保活退出时
     * Close daemon 进程，避免其继续把已被用户停用的 App 拉回来。
     */
    fun removeService() {
        val a = args ?: return
        try {
            Shizuku.unbindUserService(a, connection, true)
        } catch (_: Throwable) {
        }
        bound = false
        bindInFlight = false
        bindAttempts = 0
        bindFailed = false
    }

    fun detach() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
        }
        unbind()
        bindInFlight = false
        bindAttempts = 0
        bindFailed = false
        initialized = false
        // 不Clear共享权限Status: 其它仍在运行的 binding 仍在读取它。
    }

    /** 推断用户Down一步操作: Down载 / Open Shizuku / Request permission / None需操作。 */
    fun requiredAction(context: Context): Action {
        if (!isShizukuInstalled(context)) return Action.DOWNLOAD
        if (!binderAlive) return Action.OPEN
        // Refresh真实授权Status，避免ButtonDisplay与 Shizuku 内部不一致。
        if (!ShizukuPermission.refresh()) return Action.REQUEST_PERMISSION
        return Action.NONE
    }

    fun statusText(): String = when {
        !binderAlive -> "Shizuku is not running"
        !permissionGranted -> "Shizuku not authorized"
        !bound -> if (bindFailed) BIND_FAILED_MESSAGE else "Starting Shizuku user service…"
        lastError != null -> lastError!!
        else -> "Connected"
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

        /** 用户服务长时间None法Connect时的用户可见提示（疑似 Shizuku 存在重复实例）。 */
        const val BIND_FAILED_MESSAGE = "Shizuku service error. Restart your phone and try again."

        /**
         * Shizuku 权限是**应用级一次授权**，所有用户服务（Controller/Vibration/保活）共用同一个
         * requestCode 与申请Status，避免各功能各自 `requestPermission` 造成重复弹窗。
         */
        const val REQUEST_CODE = 0x5A17

        /**
         * 绑定请求的重试间隔: 发出绑定后若在该时间内未收到 `onServiceConnected`，则重发
         * `bindUserService`。用于应对 Shizuku 用户服务进程启动失败/服务端启动超时（其
         * 超时移除不会回调 `onServiceDisconnected`）。
         */
        private const val BIND_RETRY_INTERVAL_MS = 5_000L

        fun isShizukuInstalled(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

        /** Open Shizuku；未安装时回退到Down载页。 */
        fun openShizuku(context: Context) {
            val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return
            }
            openDownloadPage(context)
        }

        fun openDownloadPage(context: Context) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Shizuku 授权的**应用级共享Status**。
 *
 * Shizuku 权限按 uid 授予，与具体用户服务None关，因此集Center放在这里供
 * [ShizukuServiceBinding] 的所有实例共享，避免Controller/Vibration/保活各自维护一份缓存导致:
 *
 * - 界面Display“Authorized”但绑定/操作实际被拒（缓存未随服务端变化Refresh）；
 * - 某个功能Display“未授权”而 Shizuku 内部Authorized（各实例Status不同步）；
 * - Display“Authorized”但 Shizuku Center查None此应用（授权被撤销后缓存仍为 true）。
 *
 * 关键修正: 不再“一旦为 true 就永不复核”。[refresh] 会以服务端Back值为准（仅节流，
 * 不做粘滞缓存），绑定前与授权结果回调后强制Refresh；绑定抛权限异常时 [invalidate] 作废
 * 缓存，使界面重新提示授权。
 */
internal object ShizukuPermission {

    @Volatile
    var granted: Boolean = false
        private set

    /** 用户已明确Deny本次授权申请（与“尚未申请”区分）。 */
    @Volatile
    var denied: Boolean = false
        private set

    @Volatile
    var requestInFlight: Boolean = false
        private set

    @Volatile
    var autoPrompted: Boolean = false
        private set

    @Volatile
    private var lastCheckedAt: Long = 0L

    /** 复核授权Status的节流间隔，避免主线程频繁发起 binder 调用。 */
    private const val RECHECK_INTERVAL_MS = 1_000L

    /**
     * 以服务端Back的真实StatusRefresh缓存，BackRefresh后是否Authorized。
     *
     * [force] 忽略节流立即查询（用于绑定前与授权结果回调后）。
     */
    @Synchronized
    fun refresh(force: Boolean = false): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastCheckedAt < RECHECK_INTERVAL_MS) return granted
        lastCheckedAt = now
        val result = try {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        granted = result
        if (result) {
            denied = false
            autoPrompted = false
        }
        return result
    }

    fun onRequestResult(result: Boolean) {
        requestInFlight = false
        granted = result
        denied = !result
        autoPrompted = !result
        lastCheckedAt = android.os.SystemClock.uptimeMillis()
    }

    /** 作废缓存: Down一次 [refresh] 重新向服务端查询，并允许再次Request permission。 */
    @Synchronized
    fun invalidate() {
        granted = false
        denied = false
        lastCheckedAt = 0L
    }

    fun onBinderDead() {
        granted = false
        denied = false
        requestInFlight = false
        autoPrompted = false
        lastCheckedAt = 0L
    }

    fun markAutoPrompted() {
        autoPrompted = true
    }

    fun markRequestInFlight() {
        requestInFlight = true
    }

    fun clearRequestInFlight() {
        requestInFlight = false
    }

    fun clearDenied() {
        denied = false
    }
}
