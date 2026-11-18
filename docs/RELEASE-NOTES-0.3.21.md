# DiPlay v0.3.21 — 航盛 NTDA3 车机 ANW 蓝牙协议栈接入与无线 CarPlay 支持

## 更新内容

### 1. 接入航盛 NTDA3 车机 ANW 蓝牙协议栈（Issue IKUZ2R）
- **ANW phone-link 独立蓝牙栈支持**：
  - 在部分航盛 NTDA3 车机上，蓝牙协议栈隐藏于 `AnwPhoneLink` 服务后台，且系统缺失标准 `com.hsae.bluetoothservice` 服务包，此前导致车机内置蓝牙被误判为不可用、无线连线失败并报错「无蓝牙适配器」；
  - 现优化检测策略：当检测到 HSAE 蓝牙管理器包或系统能成功解析 `ANW phone-link` 服务时均判定为支持；对仅有 ANW 服务的车机，只要 ANW 服务存在即直接判定为已启用（避免死等不存在的 HSAE 服务绑定）；
  - 混合蓝牙适配器（`HybridBluetoothAdapter`）名称明确区分 `HSAE` 与 `AnwPhoneLink`，诊断报告中详细记录底层蓝牙协议栈的检测向量。
- **无配对列表时的手动地址引导回退**：
  - 当车机既无系统配对设备列表又无标准 Android 蓝牙适配器（纯 ANW 车机）时，后置引导自动回退至手动填写的 iPhone 蓝牙 MAC 地址，确保寻呼引导能够顺利发起；
  - 在 `AndroidManifest.xml` 中补齐针对 ANW 服务的 `<queries>` 声明，确保在 Android 11+（API 30+）高版本上的包可见性探查不受限制。

## 验证

- 版本 `0.3.21` / `versionCode 141`，包名 `com.shihab.diplay`，最低兼容 Android 4.2（API 17）。
- 全量单元测试（含 `BluetoothAdapterTest` 中 ANW 栈启用、名称展示及负面用例）、各模块 Lint 静态检查及构建验证全部通过。
