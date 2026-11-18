# DiPlay v0.3.27 — 低版本车机设置开关适配主题色彩与禁用状态样式

## 更新内容

### 1. Android 6.0 以下系统设置开关自绘制与主题色彩对齐
- **旧系统原生开关外观冲突消除**：
  - 在 Android 6.0（API 23）以下的老款车机上，系统原生 `Switch` 不支持通过 Tint 色彩列表着色，此前回退使用 ROM 系统默认开关样式，导致与 DiPlay 深色/浅色应用主题格格不入，且滑轨内常伴有突兀的 Holo 原生「ON/OFF」英文文字；
  - 针对 API 23 以下环境重构开关渲染逻辑，复用设置菜单的自定义高质感滑块与滑轨 Drawable，各 API 级别统一清空 `textOn`/`textOff`，确保视觉风格高度统一；
  - 为自定义开关状态补齐半透明置灰（Dimmed）的禁用样式（Disabled States），在开关被禁用时具备清晰明确的不可点击视觉反馈；
  - Android 6.0+（API 23+）系统保持原生硬件着色渲染不变。

## 验证

- 版本 `0.3.27` / `versionCode 147`，包名 `com.shihab.diplay`，最低兼容 Android 4.2（API 17）。
- 全量自动化测试（含 `SettingsWidgetsTest` pre-Marshmallow 自绘开关测试）、各模块 Lint 静态检查及各应用变体构建验证全部通过。
