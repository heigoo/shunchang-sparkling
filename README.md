# 顺场 Android 宿主

用 [Sparkling](https://github.com/bytedance/sparkling) 2.0.1 容器加载 `shunchang` 产出的 Lynx bundle，是顺场的 Android 壳工程。

## 结构

- `app/`：宿主 Activity、返回键模块、文件导入 Activity、启动图标与闪屏资源
- `sparkling-storage/`：官方 `sparkling-storage` 的 Android 实现，供前端持久化使用
- `app/src/main/assets/main.lynx.bundle`：构建时从 `../shunchang/dist` 同步进来的前端产物

## 环境

- JDK 21
- Gradle 8.7
- Android SDK（compileSdk 34）

依赖的 Lynx 版本在 `app/build.gradle` 中强制对齐到 4.2，与前端 ReactLynx 构建保持一致。

## 构建

先构建前端产物：

```bash
# 在 shunchang 工程
npm install
npm run build
```

再构建 Android 包：

```bash
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

`preBuild` 会调用 `syncLynxBundle`，自动把 `../shunchang/dist/main.lynx.bundle` 复制进 `app/src/main/assets`，无需手动拷贝。

安装到设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Release 签名

release 签名从根目录 `keystore.properties` 读取，该文件与 `*.jks` 均不入库：

```properties
storeFile=/absolute/path/to/shunchang.jks
storePassword=******
keyAlias=shunchang
keyPassword=******
```

缺少该文件时 release 回退 debug 签名，仅用于本地验证，不能用于分发。

```bash
gradle assembleRelease
```

## 许可

[MIT](LICENSE)
