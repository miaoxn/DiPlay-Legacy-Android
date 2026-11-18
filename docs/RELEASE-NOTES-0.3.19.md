# DiPlay v0.3.19 — Gitee 镜像自动更新兜底、Telegram 交流入口与通用安装包说明

## 更新内容

### 1. 应用内自动更新新增 Gitee Releases 镜像兜底发现（fix(update)）
- **GitHub 不可达时自动切换至 Gitee 镜像源**：
  - 保持以 GitHub 仓库作为首选更新发现通道；当 GitHub 请求彻底失败（网络异常、HTTP 报错或匿名速率配额耗尽）时，自动回退读取 Gitee 镜像仓库的 Releases 列表；
  - 对 Gitee 镜像源实施与主源同等严格的安全与结构校验：仅接受稳定 `vX.Y.Z` 标签、严格校验规范下载地址，并强制要求同时存在 APK 与 `.sha256` 校验附件；
  - 将 `gitee.com` 及其子域纳入合法更新主机白名单，使自动更新器在读取镜像列表、下载 APK 与校验文件时能够顺利通过重定向安全校验；当 GitHub 正常可达但暂无更新版本时，不会无故回退请求镜像源。

### 2. 文档与反馈渠道完善
- **Telegram 交流群入口**：在英文与简体中文 README 的反馈渠道章节补充 Telegram 交流群链接，方便车友交流适配经验与反馈日志；
- **通用单包架构说明**：在中文 README 中明确说明每次发布的单个通用 APK 已内置全部四种主流车机架构的原生库（ARMv7 / ARM64 / x86 / x86_64），系统将在安装时自动择优匹配，暂不拆分独立 ABI 分包。

## 验证

- 版本 `0.3.19` / `versionCode 139`，包名 `com.shihab.diplay`，最低兼容 Android 4.2（API 17）。
- 全量单元测试（含 `DiPlayUpdateClientTest` 的 Gitee 镜像回退及主机校验用例）、各模块 Lint 静态检查及构建验证全部通过。
