# MenuApp 🍳

一个基于 Kotlin 的 Android 菜谱管理应用，支持手动录入、OCR 图像识别自动填充、食材用量智能换算和烹饪步骤倒计时。

## ✨ 功能特性

- **菜谱管理** — 创建、编辑、删除菜谱，包含主菜、配菜、调料和烹饪步骤
- **OCR 智能识别** — 拍照或从相册选图，通过 ML Kit 中文 OCR 自动识别菜谱文字并填充表单
- **剪贴板解析** — 粘贴剪贴板中的菜谱文本，自动解析为结构化数据
- **食材用量换算** — 输入实际主菜重量，自动按比例缩放所有配菜和调料的用量
- **烹饪计时器** — 每个步骤支持独立倒计时，烹饪过程不手忙脚乱
- **离线可用** — 数据本地存储（SharedPreferences + JSON），无需网络即可使用
- **内置示例** — 首次启动预置红烧肉、清炒西兰花、番茄炒蛋三道菜谱

## 📸 截图

> 截图位于 `img/` 目录下

## 🛠 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| 最低 SDK | Android 7.0 (API 24) |
| 目标 SDK | Android 14 (API 34) |
| UI 框架 | ViewBinding + Material Design 3 |
| OCR | Google ML Kit 中文文本识别（离线） |
| 数据存储 | SharedPreferences + Gson JSON |
| 构建工具 | Gradle 8.3 (Kotlin DSL) |

## 🚀 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1) 或更高版本
- JDK 17
- Android SDK 34

### 构建运行

```bash
# 克隆仓库
git clone git@github.com:cwh1234/menu_app.git
cd menu_app

# 使用 Android Studio 打开项目
# File → Open → 选择 menu_app 目录

# 或在命令行构建
./gradlew assembleDebug
```

构建产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 📁 项目结构

```
menu_app/
├── app/
│   ├── build.gradle.kts          # 应用模块构建配置
│   ├── proguard-rules.pro        # 混淆规则
│   └── src/main/
│       ├── AndroidManifest.xml   # 应用清单
│       ├── java/com/menuapp/
│       │   ├── MainActivity.kt          # 主页面（菜谱列表）
│       │   ├── DetailActivity.kt        # 菜谱详情（含计时器、用量换算）
│       │   ├── AddRecipeActivity.kt     # 添加/编辑菜谱（含 OCR、剪贴板）
│       │   ├── api/
│       │   │   ├── OcrClient.kt         # ML Kit OCR 封装
│       │   │   └── RecipeTextParser.kt  # 菜谱文本解析器
│       │   ├── adapter/
│       │   │   └── RecipeAdapter.kt     # 菜谱列表适配器
│       │   ├── data/
│       │   │   └── RecipeData.kt        # 数据持久化管理
│       │   └── model/
│       │       └── Recipe.kt            # 数据模型
│       └── res/                         # 布局、图片、字符串等资源
├── build.gradle.kts              # 顶层构建配置
├── settings.gradle.kts           # 项目设置
└── gradle/                       # Gradle Wrapper
```

## 📦 依赖项

- **AndroidX** — Core KTX, AppCompat, ConstraintLayout, RecyclerView, CardView, SwipeRefreshLayout
- **Material Design 3** — Material Components
- **ML Kit** — `text-recognition-chinese`（离线中文 OCR）
- **Gson** — JSON 序列化/反序列化

## 📄 License

MIT License

---

Made with ❤️ for cooking enthusiasts
