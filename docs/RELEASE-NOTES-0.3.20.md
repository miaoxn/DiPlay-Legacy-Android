# DiPlay v0.3.20 — 现有 Wi-Fi 模式兼容 USB 共享网络与有线以太网同局域网连接

## 更新内容

### 1. 现有 Wi-Fi 模式支持接入 USB 共享网络与有线以太网（Issue IKU53B）
- **同局域网 USB 共享网络兼容**：
  - 当车机未连接常规 Wi-Fi 时，「现有 Wi-Fi」连接模式现在能够自动识别并附着到同一局域网下的 USB 共享网络（例如通过 RNDIS / CDC ECM 接入车机的随身 Wi-Fi 棒或有线 USB 网卡）；
  - 始终保持**原生 Wi-Fi 优先**：只要存在活跃的 Wi-Fi 连接，就优先使用 Wi-Fi，确保已有 Wi-Fi 关联方案不会发生传输切换或中断。
- **高版本与 KitKat 双路径稳健实现**：
  - 现代 Android 路径（API 21+）通过系统 `NetworkCallback` 监听 `TRANSPORT_ETHERNET` 传输，在接口断开或 IP 地址变更时自动重启会话；USB 路径不校验站点 SSID 与信道（记为 `0/Auto`），已保存的 Wi-Fi 凭据仍可通过蓝牙同步给 iPhone；
  - 老旧 Android 4.4（KitKat）路径自动筛选唯一处于启用状态且具备可用 IPv4 地址的非回环 `eth` / `usb` / `rndis` 接口；
  - 两条路径均对多网卡歧义情况执行安全拒绝，避免错误绑定未知网络接口。

### 2. 测试覆盖与可靠性修复
- 新增覆盖现代网络路径（无 Wi-Fi 信息接入、Wi-Fi 优先、多网卡歧义拒绝、断网重启、无地址超时）与 USB 接口筛选辅助逻辑的单元测试；
- 修复 `ExistingWifiManagerTest.wifiNetworkWinsOverUsbSharedNetwork` 中的 Mockito 嵌套打桩异常，确保全量单元测试稳定通过。

## 验证

- 版本 `0.3.20` / `versionCode 140`，包名 `com.shihab.diplay`，最低兼容 Android 4.2（API 17）。
- 全量单元测试、各模块 Lint 静态检查及构建验证全部通过。
