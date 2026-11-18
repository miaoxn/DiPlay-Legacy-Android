# DiPlay v0.3.22 — 插入 USB 蓝牙适配器或共享网络设备时自动启动应用

## 更新内容

### 1. 插入 USB 蓝牙棒或网络设备时自动打开 DiPlay（Issue IKUZ2R）
- **USB 硬件热插拔唤起能力**：
  - 新增设置项「插入 USB 设备时自动打开 DiPlay」（位于「设置 → 连接 → 自动连接」中，默认关闭）；
  - 注册无特定设备过滤器的全局 `USB_DEVICE_ATTACHED` 广播接收器，在任意 USB 设备插入时响应，且不会抢占系统的 USB 默认应用关联判定；
  - 严密的代码级类型判定门禁：
    - 排除 iPhone / iPad（有线 CarPlay 走原生独占路径，避免冲突）；
    - 支持 USB 蓝牙适配器：借助 `UsbBtDongleDetector` 精确匹配各类常见外置蓝牙适配器（绿联、Realtek、炬芯等）；
    - 支持 USB 共享网络设备：依据 USB-IF 标准类代码匹配 RNDIS 设备三元组以及 CDC ECM/NCM/EEM 接口；
    - 厂商私有驱动的以太网卡不带标准网络类代码，保持严格隔离不予响应；
  - 当满足触发条件且开关开启时，自动唤起 DiPlay 连接主界面，大幅提升无感连线体验。

## 验证

- 版本 `0.3.22` / `versionCode 142`，包名 `com.shihab.diplay`，最低兼容 Android 4.2（API 17）。
- 全量自动化测试（含 `UsbDeviceAutoLaunchReceiverTest` 与 `UsbNetworkDeviceTest`）、各模块 Lint 静态检查及各应用变体构建验证全部通过。
