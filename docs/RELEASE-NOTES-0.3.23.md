# DiPlay v0.3.23 — 支持跟随系统日夜主题切换与修复 AC83xx 车机解码卡死

## 更新内容

### 1. DiPlay 主题跟随系统日间/夜间模式自动切换
- **主题外观支持浅色/深色切换**：
  - 在「设置 → 显示」卡片中新增「DiPlay 主题」选项，支持「跟随 Android 系统（默认）」、「始终深色」与「始终浅色」，实时生效无需重连；
  - 重构 `SettingsTheme` 为深色/浅色双套完整调色板，`DiPlayActivity` 在渲染时自动解析当前主题，并在车机夜间模式状态变更时自动刷新；
  - CarPlay 投屏等待界面配色重新联动应用主题，实时响应主题切换且不影响底层会话；CarPlay 车机端本身的日夜模式保持独立，仅驱动 iPhone 投屏画面。

### 2. 杰发科技 AutoChips AC83xx 芯片车机优先回退 AVC 软解（Issue #326）
- **硬解死锁与卡顿问题修复**：
  - 在搭载杰发科技（AutoChips）AC83xx 系列芯片（如 AC8317，Android 4.2.2 老款车机）的设备上，系统内置的 `OMX.mtk.video.decoder.avc` 硬件解码器在处理动态 CarPlay 码流时存在严重缺陷（调优参数被拒绝，未调优参数仅渲染首帧后即发生死锁停滞）；
  - 将已知缺陷硬解规避规则扩展至该 MTK 解码器，并精准限定在硬件平台标识包含 `ac83`（`Build.HARDWARE`）的芯片上，优先自动回退至软件解码器（Google 软解），彻底解决首帧后画面卡死问题；
  - 严格的作用域隔离确保其他正常搭载联发科芯片的车机继续保留高性能硬件解码。

## 验证

- 版本 `0.3.23` / `versionCode 143`，包名 `com.shihab.diplay`，最低兼容 Android 4.2（API 17）。
- 全量自动化测试（含 `BrokenHardwareAvcDecoderTest`、`AppThemeModeTest`、`SettingsThemePaletteTest`）、各模块 Lint 静态检查及各应用变体构建验证全部通过。
