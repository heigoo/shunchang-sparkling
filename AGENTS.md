# AGENTS.md

Android 壳工程：用 Sparkling 2.0.1 容器加载 `../shunchang` 产出的 Lynx bundle。

## 构建

- 先构建前端产物：在 `../shunchang` 执行 `npm run build`。
- `preBuild` 会自动把 `../shunchang/dist/main.lynx.bundle` 同步进 `app/src/main/assets`。
- 本机 Gradle 8.7、JDK 21（`C:\Program Files\Java\jdk-21.0.12.1`），Android SDK 在 `D:\Program Files\Android\sdk`。
- 打 debug 包：`gradle assembleDebug`，产物在 `app/build/outputs/apk/debug/app-debug.apk`。
- 安装到设备：`adb install -r app/build/outputs/apk/debug/app-debug.apk`。

## Git

- 每次完成一个可交付的改动后必须提交 git，禁止把修改留在工作区。
- 使用约定式提交：`type(scope): 中文描述`，一次提交只做一件事。
