# 凌汐弹幕网 电视版 (LingXiTV)

凌汐弹幕网（https://lingxi.xiguastudio.top/）的 Android TV 客户端，专为电视盒子和智能电视设计，支持遥控器操作与弹幕观看。

## 功能特性

- 🎬 **视频浏览**：左侧分区导航 + 右侧视频卡片网格，13 个分区全覆盖
- 🔥 **推荐与最新**：首页直接展示热门推荐和最新投稿
- 🔍 **搜索**：Leanback 原生搜索界面，支持关键词搜索
- ▶️ **视频播放**：基于 Media3 ExoPlayer，自动选择 720p 画质
- ⚙️ **播放设置**：菜单键切换清晰度（480p/540p/720p）、画面比例、弹幕开关
- 💬 **弹幕**：Canvas 渲染弹幕系统，支持滚动 / 顶部 / 底部三种模式
- 🎮 **遥控器适配**：方向键导航、OK 播放、左右键快进快退 10 秒
- 💾 **低内存占用**：仅 3.5 MB 安装包，弹幕对象池复用，封面按需加载

## 仅使用的 API

为减少内存与网络开销，电视端仅调用以下 5 个公开接口，不包含任何后台管理接口：

| 接口 | 用途 |
|------|------|
| `GET /api/v1/videos` | 视频列表（sort=hot/new、partition 分区过滤） |
| `GET /api/v1/videos/{id}` | 视频详情（含多画质 qualityOptions） |
| `GET /api/v1/partitions` | 分区列表 |
| `GET /api/v1/search` | 关键词搜索 |
| `GET /api/v1/danmaku` | 弹幕列表 |

## 系统要求

- Android 6.0 (API 23) 及以上
- 电视盒子 / 智能电视（Android TV 或普通 Android 均可）
- 网络连接

## 安装方式

### 方式一：ADB 安装（推荐）

```bash
# 电视开启 ADB 调试后，连接并安装
adb connect <电视IP>:5555
adb install -r app-release.apk
```

### 方式二：U盘安装

将 `app-release.apk` 拷贝到 U 盘，插入电视，通过文件管理器安装。

## 遥控器操作

| 按键 | 功能 |
|------|------|
| 方向键 | 导航选择 |
| OK / 确认 | 播放视频 |
| 左键 | 快退 10 秒 |
| 右键 | 快进 10 秒 |
| 播放/暂停键 | 播放或暂停 |
| 菜单键 / 上键 | 打开播放设置菜单（清晰度、画面比例、弹幕开关） |

## 内存优化

- 弹幕使用 **Canvas 直接绘制**，不创建 TextView，同屏限制 60 条
- **弹幕对象池**复用 `DanmakuItem`，降低 GC 频率
- Glide 加载封面时 override 到卡片尺寸，避免大图占用内存
- 分区列表内存缓存 5 分钟，减少重复网络请求
- 视频列表分页加载（每页 30 条），滚动到行尾自动加载下一页
- 播放页 `onDestroy` 释放 ExoPlayer，协程随生命周期自动取消
- 不申请大堆内存（`largeHeap=false`）

## 构建

使用 Android Studio（Hedgehog 及以上，JDK 17）打开项目根目录，等待 Gradle 同步完成后：

```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

产物路径：`app/build/outputs/apk/release/app-release.apk`

## 技术栈

- Kotlin
- AndroidX Leanback（TV UI 框架）
- Media3 ExoPlayer（视频播放）
- Retrofit + OkHttp（网络请求）
- Gson（JSON 解析）
- Glide（图片加载）
- 协程（异步任务）

## License

本项目仅供学习交流使用。
