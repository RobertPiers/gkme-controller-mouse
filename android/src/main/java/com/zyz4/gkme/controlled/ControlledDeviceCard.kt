package com.zyz4.gkme.controlled

/**
 * 扫描结果卡片: 把同一物理设备（相同 MAC）在多个 IP Up发现的端点合并为一张卡。
 * 当存在多个可用 IP 且均Not connected时展开，逐条列出每个 IP 并各自提供ConnectButton；
 * 一旦其Center一个 IP 被使用（或只剩单个 IP）则折叠为普通单卡。
 * 行为对齐 GKME-Windows 的 DeviceCardViewModel。
 */
data class ControlledDeviceCard(
    val groupKey: String,
    val name: String,
    val mac: String,
    val endpoints: List<ControlledDevice>,
) {
    val title: String get() = name.ifBlank { endpoints.firstOrNull()?.ip.orEmpty() }

    val hasMultipleEndpoints: Boolean get() = endpoints.size > 1
}
