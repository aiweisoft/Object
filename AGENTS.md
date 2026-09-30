# AGENTS.md

本文件为在此仓库工作的智能编码代理提供项目约定与操作指引。

## 项目概述

- **类型**：uni-app（Vue3）项目，使用 HBuilderX 开发（**非** npm/CLI 工程，根目录无 `package.json`、无 npm 脚本）。
- **功能**：Android 端本地离线 YOLOv8 检测，支持双模型切换——通用物体检测（COCO 80 类）+ 人脸检测。
- **核心实现**：本地 UTS 插件 `uni-yolo-detect` 在 Android 原生层完成 CameraX 采集 + ONNX Runtime 推理 + 检测框绘制；前端页面仅做控制与结果展示。通过 `type` 参数（`"detect"`/`"face"`）切换模型与标签。
- **vueVersion**：`3`（见 `manifest.json`）。

## 项目结构与模块组织

```
Object/
├─ App.vue                 # 应用入口（onLaunch/onShow/onHide）
├─ main.js                 # 入口（Vue3 createSSRApp）
├─ index.html              # H5 入口模板
├─ manifest.json           # 应用配置（权限、vueVersion=3、app-plus 分发配置）
├─ pages.json              # 页面注册与导航栏配置
├─ uni.scss                # 全局 SCSS 变量（$uni-* 主题变量）
├─ pages/                  # 页面
│  ├─ index/index.vue      # 首页（两个入口：物体检测 / 人脸检测，带 type 跳转）
│  └─ detect/detect.vue    # 检测页（onLoad 接收 type 参数，调用插件 API）
├─ static/                 # 静态资源（logo.png 等）
├─ uni_modules/            # uni_modules 插件目录
│  └─ uni-yolo-detect/     # 本地 YOLO 检测 UTS 插件
│     ├─ package.json      # 插件清单（id: uni-yolo-detect）
│     └─ utssdk/
│        ├─ interface.uts            # 对外 API 类型声明（DetectResult/Options 等）
│        ├─ unierror.uts             # 错误码定义
│        └─ app-android/
│           ├─ index.uts             # UTS 胶水层（导出 detect/startDetect/stopDetect，传递 type）
│           ├─ YoloDetector.kt       # Kotlin：ONNX 推理核心（双模型加载/预处理/解析）
│           ├─ DetectActivity.kt     # Kotlin：CameraX 实时检测 Activity + 回调桥接
│           ├─ OverlayView.kt        # Kotlin：检测框绘制
│           ├─ AndroidManifest.xml   # 注册 DetectActivity
│           ├─ config.json           # maven 依赖 + abis + minSdkVersion（24）
│           └─ assets/               # yolov8n.onnx+coco.names（通用）、yolov8n-face.onnx+face.names（人脸）
├─ unpackage/              # 编译产物（已 gitignore，勿手动修改）
└─ .hbuilderx/             # HBuilderX 本地配置（launch.json、已 gitignore）
```

- 页面在 `pages.json` 的 `pages` 数组中注册；首项为启动页。
- 插件 API 通过 `import { detect, startDetect, stopDetect } from "@/uni_modules/uni-yolo-detect"` 导入；`detect`/`startDetect` 入参含 `type`（`"detect"` 通用物体 / `"face"` 人脸，默认 `"detect"`）用于切换模型。
- 本项目**没有**独立测试目录与测试框架；验证依赖真机运行 + 控制台日志。

## 构建 / 运行 / 校验命令

所有命令通过 HBuilderX CLI 执行：

- CLI：`D:\HBuilderX.5.01.2026021122-alpha\HBuilderX\cli.exe`
- Node：`D:\HBuilderX.5.01.2026021122-alpha\HBuilderX\plugins\node\node.exe`

以下以 `<PROJ>` 代指项目绝对路径 `D:\Users\Administrator\Documents\HBuilderProjects\Object`，`<CLI>` 代指上面的 cli.exe 路径。

```powershell
# 运行到 Android 真机（标准基座）
& "<CLI>" launch app-android --project "<PROJ>"

# 运行到 Android 真机（自定义基座；插件含三方原生依赖时必须用 custom）
& "<CLI>" launch app-android --project "<PROJ>" --playground custom

# 仅编译不运行（编译模式）
& "<CLI>" launch app-android --project "<PROJ>" --compile true

# 查看 Android 运行日志
& "<CLI>" logcat app-android --project "<PROJ>"

# 单文件静态诊断（lint），UTS/Kotlin 常用
& "<CLI>" lsp lint --file "<绝对文件路径>" --project "<PROJ>" --platform app-android

# 云打包（生成自定义基座；插件含 onnxruntime/CameraX 等原生依赖时必须）
& "<CLI>" pack --project "<PROJ>" --platform android --iscustom true --android.packagename "com.object.detect" --android.androidpacktype 3

# 列出已连接设备
& "<CLI>" devices list
```

**重要约定**：

- 本项目**没有** `npm install`/`npm run lint`/`npm test` 等命令，不要尝试运行 npm 脚本。
- **没有**自动化测试命令；「运行单个测试」不适用，改完代码用 `lsp lint` 校验 + 真机运行验证。
- 插件引入了 `onnxruntime-android`、`androidx.camera` 等 maven 三方依赖，**标准基座不含这些依赖**，真机运行实时检测必须使用云端打包的**自定义基座**（`pack --iscustom true` 或 `launch --playground custom`）。
- PowerShell 环境下运行 CLI 需在命令前加 `&`；涉及中文路径请用引号包裹。

## 代码风格与协作规则

### 通用格式（见 `.editorconfig`）

- 缩进：**Tab**（`indent_style = tab`，`indent_size = 4`）。
- 编码 UTF-8，换行 LF，行尾去空格，文件末尾保留一个空行。
- 前端样式尺寸用 `rpx` 单位。

### 前端（`.vue` / `.js`）

- Vue3 使用 **Options API**（`data()` / `methods` / `onLoad` / `onUnload`），与现有页面保持一致。
- 命名：页面目录与文件小写；方法/变量用 camelCase。
- 插件导入路径统一为 `@/uni_modules/<插件id>`。
- 用户交互错误提示用 `uni.showToast({ title, icon: 'none' })`。

### 条件编译（重要，易踩坑）

- 传统 uni-app 的 `.vue`/`.js` 文件中，App 平台用 **`APP-PLUS`**；**`APP-ANDROID` / `APP-IOS` 仅 `uts` 文件支持**，在 `.vue`/JS 里不生效（会导致 `#ifdef` 代码被丢弃、`#ifndef` 代码反而保留）。
- 在 `.vue`/JS 中区分 Android/iOS，用运行时判断：`uni.getSystemInfoSync().platform === 'android'`。
- 条件编译必须保证「编译前/后」语法均正确：json 中不能有多余逗号，js 中不能重复 import。
- 示例（前端正确写法）：
  ```js
  // #ifdef APP-PLUS
  import { detect } from "@/uni_modules/uni-yolo-detect"
  // #endif
  ```

### UTS / Kotlin（插件 `uni-yolo-detect`）

- `interface.uts` 只放**类型/API 声明**（`export type`/`export interface`），实现放 `app-android/index.uts`。
- 错误处理：对外 API 用 `fail` 回调传 `IUniError`（含 `errCode`），见 `unierror.uts` 与 `YoloFailImpl`。
- 获取原生上下文：`UTSAndroid.getAppContext()`；获取 Activity：`UTSAndroid.getUniActivity()`（`io.dcloud.uts`）。
- 结果回调经 `DetectBridge.onResult` 传 JSON 字符串，前端 `parseJson` 解析为 `DetectResult[]`。
- 新增原生依赖/so/aar、调整 abis，须同步修改 `utssdk/app-android/config.json`；改动后需重新云端打包自定义基座。

### 模型与资源

- 模型均为**带 NMS 端到端** ONNX：输入 `[1,3,640,640]`，输出 `[1,300,6]`（`x1,y1,x2,y2,score,classId`）。
- 模型/标签文件位于 `utssdk/app-android/assets/`：
  - 通用：`yolov8n.onnx` + `coco.names`（80 类）
  - 人脸：`yolov8n-face.onnx` + `face.names`（单类 `face`）
- 换/训练模型用 `yolo export model=xxx.pt format=onnx imgsz=640 nms=True` 导出，输出须为 `[1,300,6]` 才能复用 `parseOutput`；标签文件内容需与类别一一对应。
- 预处理用 letterbox（等比例缩放 + 灰边填充），`parseOutput` 依据 `Letterbox(scale,padX,padY)` 还原坐标；`OverlayView` 按 `FILL_CENTER`（裁剪填满）映射到预览画面。

### 兼容性注意事项

- **不要**对 `input`/`textarea` 设置 `box-sizing: border-box`（H5 端会导致无法聚焦/输入）。
- 新增 Android 权限写到 `manifest.json` 的 `app-plus.distribute.android.permissions`。
- 不要按进程名 kill Node 进程（避免误杀 HBuilderX 内置 Node），需要时按 PID 操作。
