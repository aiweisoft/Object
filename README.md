# Object — YOLO 物体检测 & 人脸检测

基于 **uni-app（Vue3）** 的 Android 本地离线 AI 检测应用，支持**双模型**实时检测：通用物体检测（COCO 80 类）+ 人脸检测。全部推理在设备本地完成，无需联网。

## 功能特性

- 🎯 **通用物体检测**：识别 COCO 80 类常见物体（人、车、动物、家具等）
- 😀 **人脸检测**：实时框出画面中的人脸
- 📷 **实时摄像头检测**：CameraX 采集 + 原生界面叠加检测框，低延迟
- 🖼️ **图片检测**：从相册选图识别
- 🔌 **本地离线推理**：ONNX Runtime 端侧推理，无网络依赖

## 技术架构

```
uni-app Vue3 页面（控制/展示）
        │  UTS 插件 API（detect / startDetect / stopDetect，type 切换模型）
        ▼
UTS 插件 uni-yolo-detect（Kotlin 原生层）
   ├─ CameraX           摄像头实时采集
   ├─ ONNX Runtime      端侧推理引擎
   ├─ YOLOv8n           COCO 80 类检测模型
   ├─ YOLOv8n-face      人脸检测模型
   └─ OverlayView       检测框绘制（letterbox + FILL_CENTER 对齐）
```

## 目录结构

```
Object/
├─ pages/
│  ├─ index/index.vue      # 首页（两个入口：物体检测 / 人脸检测）
│  └─ detect/detect.vue    # 检测页（接收 type 参数，调用插件）
├─ static/                 # 静态资源
├─ uni_modules/
│  └─ uni-yolo-detect/     # 本地 UTS 检测插件
│     └─ utssdk/
│        ├─ interface.uts      # 对外 API 声明
│        ├─ unierror.uts       # 错误码
│        └─ app-android/
│           ├─ index.uts        # UTS 胶水层
│           ├─ YoloDetector.kt  # ONNX 推理核心（双模型）
│           ├─ DetectActivity.kt# CameraX 实时检测界面
│           ├─ OverlayView.kt   # 检测框绘制
│           ├─ config.json      # maven 依赖 + abis
│           └─ assets/          # 模型 + 标签
│              ├─ yolov8n.onnx / coco.names           # 通用物体
│              └─ yolov8n-face.onnx / face.names      # 人脸
├─ manifest.json           # 应用配置（CAMERA 权限等）
└─ pages.json              # 页面注册
```

## 环境要求

- **HBuilderX**（5.x，含 UTS 插件开发能力）
- **Android 7.0+（API 24）** 设备（受 onnxruntime minSdk 24 限制）

## 构建与运行

本插件依赖 `onnxruntime-android`、`androidx.camera` 等三方原生库，**标准基座不含这些依赖**，必须使用**云端打包的自定义基座**。

### 方式一：HBuilderX 图形界面

1. 用 HBuilderX 打开本项目
2. 菜单 `发行` → `原生App-云打包` → 勾选「自定义基座」
3. 打包完成后，`运行` → `运行到手机或模拟器` → 选择自定义基座运行

### 方式二：CLI 命令

```powershell
# 云打包自定义基座（生成含三方依赖的调试基座）
& "HBuilderX\cli.exe" pack --project "<项目路径>" --platform android --iscustom true --android.packagename "com.object.detect" --android.androidpacktype 3

# 用自定义基座运行到真机
& "HBuilderX\cli.exe" launch app-android --project "<项目路径>" --playground custom
```

## 使用说明

1. 首页点击「物体检测」或「人脸检测」进入对应模式
2. **启动摄像头检测**：打开原生检测界面，画面实时叠加检测框，点左上角「✕ 关闭」退出
3. **选择图片检测**：从相册选图，页面下方展示识别结果（类别 + 置信度）

## 模型说明

模型均为**带 NMS 端到端** ONNX 格式：

- 输入：`[1, 3, 640, 640]`
- 输出：`[1, 300, 6]`（`x1, y1, x2, y2, score, classId`）

### 更换 / 训练模型

```bash
pip install ultralytics

# 换更大的通用模型（s/m/l 精度更高）
yolo export model=yolov8s.pt format=onnx imgsz=640 nms=True

# 训练自定义数据集
yolo train model=yolov8n.pt data=your_dataset.yaml epochs=100 imgsz=640
yolo export model=runs/detect/train/weights/best.pt format=onnx imgsz=640 nms=True
```

导出后将 `.onnx` 覆盖到 `uni_modules/uni-yolo-detect/utssdk/app-android/assets/` 对应文件，并同步更新标签文件（`coco.names` / `face.names`），最后重新云打包。

> 注意：输出须为 `[1, 300, 6]` 才能复用现有解析逻辑（`nms=True` 导出）。

## 权限

应用需要相机权限（已在 `manifest.json` 配置）：

```xml
<uses-permission android:name="android.permission.CAMERA"/>
```

## License

模型与代码仅供学习使用。YOLO 模型版权归 Ultralytics 所有，请遵循其 [AGPL-3.0](https://github.com/ultralytics/ultralytics/blob/main/LICENSE) 许可。
