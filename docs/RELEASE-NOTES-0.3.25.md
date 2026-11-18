# DiPlay v0.3.25 — 开机自启动优化添加置顶标志避免被车机桌面遮挡

## 更新内容

### 1. 开机自启动 Intent 新增置顶标志（FLAG_ACTIVITY_REORDER_TO_FRONT）
- **解决车机开机后界面被第三方/原车桌面覆盖问题**：
  - 在部分车机定制系统（如特定车厂或后装 Android 导航车机）上，车机启动广播 `BOOT_COMPLETED` 触发时，系统虽已执行 `startActivity` 打开 DiPlay，但随后车载桌面启动并将已打开的 Activity 覆盖至后台；
  - 在 `BootReceiver.kt` 的启动 Intent 标志中追加 `Intent.FLAG_ACTIVITY_REORDER_TO_FRONT`；
  - 若系统任务栈中已存在 `DiPlayActivity` 实例，系统会将其重新拉取置顶至前台，避免用户开机后面对车载桌面而未能直接进入 DiPlay 界面；若栈中尚无实例，则保持正常的创建与置顶逻辑；
  - 原有的 `FLAG_ACTIVITY_NEW_TASK`、`FLAG_ACTIVITY_CLEAR_TOP` 与 `FLAG_ACTIVITY_SINGLE_TOP` 逻辑保持不变。

## 验证

- 版本 `0.3.25` / `versionCode 145`，包名 `com.shihab.diplay`，最低兼容 Android 4.2（API 17）。
- 全量自动化测试、各模块 Lint 静态检查及各应用变体构建验证全部通过。
