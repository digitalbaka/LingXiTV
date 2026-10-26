# 更新日志

## v1.5.0 (2026-10-27)

播放器交互增强版本。

### 新增功能

- ⬇️ **下键呼出播放控制面板**：播放时按下键显示 PlayerView 控制条（暂停/播放、进度条拖动）
- 🔁 **单曲循环**：设置菜单新增「循环」选项，开启后视频播放结束自动从头播放

### 遥控器操作（更新）

| 按键 | 功能 |
|------|------|
| 左 / 右 | 快退 / 快进 10 秒 |
| OK | 播放 / 暂停 |
| 上键 / 菜单键 | 打开播放设置菜单（清晰度、画面比例、弹幕、循环） |
| 下键 | 呼出播放控制面板 |

## v1.4.0 (2026-10-27)

分页加载根本修复版本。

### 修复

- 🐛 **分页加载始终不生效（根因修复）**：`OnItemViewSelectedListener` 的 lambda 参数顺序写错——`onItemSelected(itemViewHolder, item, rowViewHolder, row)` 第 3 个参数是 `rowViewHolder`（`RowPresenter.ViewHolder` 类型），第 4 个才是 `row`（`Row` 类型）。代码误将第 3 个参数命名为 `row`，导致 `row as? ListRow` 永远返回 null，分页永不触发。已修正为 `{ _, item, _, row ->`
- 🐛 **搜索结果分页同样不生效**：与主界面相同的参数顺序错误，一并修复

### 验证

- ✅ 电视端实测：滚动到推荐行末尾自动加载下一页，`loadPage: loading page=5, added=6, total=94`

## v1.3.0 (2026-10-27)

分页加载修复与构建兼容性版本。

### 修复

- 🐛 **分页加载不生效**：改用 `lifecycleScope` 替代 `viewLifecycleOwner.lifecycleScope`，避免 View 销毁时协程被取消导致 `loading` 状态卡死；加载完成后调用 `notifyItemRangeChanged` 强制刷新 Leanback 行视图
- 🐛 **`is` 类型检查编译错误**：Leanback 接口平台类型推断导致 `item is Video` 报 "instance is always false"，改用 `as?` 安全转换
- 🐛 **单次网络失败终止分页**：失败不再将 `hasMore` 设为 `false`，允许下次重试；`try/finally` 确保 `loading` 状态正确重置
- 🐛 **构建依赖冲突**：强制 okio 2.10.0 并排除 okio-jvm，解决 Kotlin 元数据版本不匹配

### 改进

- 📄 分页触发阈值优化为倒数第 3 个，更灵敏
- 📄 hasMore 双重判断：API 字段 + 已加载数量 vs 总数

## v1.2.0 (2026-10-27)

Bug 修复与功能完善版本。

### 修复

- 🐛 **菜单键闪退（最终修复）**：根因是 Leanback 主题未定义 `alertDialogTheme`，导致 `Resources$NotFoundException: Resource ID #0x0`。已在 `Theme.LingXiTV.Player` 中显式指定 `alertDialogTheme`，并新增 `Theme.LingXiTV.Dialog` 主题
- 🐛 **菜单键被 PlayerView 控制器拦截**：改用 `dispatchKeyEvent` 在 View 层之前捕获菜单键/上键，确保播放设置菜单能正常弹出
- 🐛 **分区/搜索结果显示不全**：实现分页加载，选中行内倒数第 5 个视频时自动加载下一页；搜索结果同样支持分页
- 🐛 **视频播放 502 错误**：添加 `onPlayerError` 监听，先重试当前清晰度（最多 3 次），失败后自动降级到其他可用清晰度
- 🐛 **构建错误 okio 不兼容**：强制 okio 2.10.0 并排除 okio-jvm，解决 Kotlin 元数据版本不匹配导致的编译失败

### 功能

- ⚙️ **播放设置菜单**：按菜单键或上键弹出，包含：
  - 清晰度切换（480p / 540p / 720p，保持播放进度）
  - 画面比例（适应屏幕 / 全屏裁剪 / 填满宽度 / 填满高度）
  - 弹幕开关
- 📄 **防重复弹窗**：已有对话框显示时不再重复弹出

## v1.1.0 (2026-10-27)

Bug 修复与功能增强版本。

### 修复

- 🐛 **弹幕不显示**：`DanmakuView.updatePosition` 派发弹幕后未触发重绘，已添加 `invalidate()`；PlayerView 改用 `texture_view` 确保弹幕层叠加在视频上方
- 🐛 **菜单键闪退**：原使用 `android.app.AlertDialog` 与 Leanback 主题不兼容导致崩溃，改用 `androidx.appcompat.app.AlertDialog`
- 🐛 **分区/搜索结果显示不全**：视频卡片信息改为始终显示（`CARD_REGION_VISIBLE_ALWAYS`），不再仅在选中时显示标题；每行加载量从 20 提升到 30

### 新增功能

- 🎚️ **清晰度切换**：播放设置中可切换 480p / 540p / 720p，切换时保持当前播放进度
- 🖼️ **画面比例调节**：适应屏幕 / 全屏裁剪 / 填满宽度 / 填满高度 四档循环
- 💬 **弹幕开关**：播放设置中可一键开关弹幕
- ⚙️ **播放设置菜单**：按 `菜单键` 或 `上键` 打开，统一管理清晰度、画面比例、弹幕

### 优化

- 弹幕字号放大至 1.4 倍，同屏弹幕上限提升到 80 条
- 视频卡片尺寸调整为 313×176（标准 TV 比例）

### 遥控器操作

| 按键 | 功能 |
|------|------|
| 左 / 右 | 快退 / 快进 10 秒 |
| OK | 播放 / 暂停 |
| 上键 / 菜单键 | 打开播放设置菜单 |

## v1.0.0 (2026-10-27)

首个正式版本发布。

### 新增功能

- 🎬 **视频浏览**：Leanback Browse 界面，左侧分区导航、右侧视频卡片网格
- 📂 **分区支持**：动画、番剧、游戏、知识、科技、音乐、舞蹈、影视、生活、鬼畜、时尚、娱乐、运动 共 13 个分区
- 🔥 **推荐与最新**：首页展示热门推荐（sort=hot）和最新投稿（sort=new）
- 🔍 **搜索**：Leanback 原生搜索，支持关键词搜索视频
- ▶️ **视频播放**：Media3 ExoPlayer 播放，自动选择 720p 画质，支持多清晰度切换数据
- 💬 **弹幕系统**：Canvas 渲染，支持滚动、顶部、底部三种弹幕模式，黑色描边增强可读性
- 🎮 **遥控器适配**：方向键导航、OK 播放、左右键快进快退 10 秒、播放/暂停键支持
- 🖼️ **应用图标**：使用官方提供的图标，适配各密度 mipmap + TV Banner + Adaptive Icon

### 内存优化

- 弹幕 Canvas 绘制 + 对象池复用，同屏上限 60 条
- Glide 封面 override 到卡片尺寸（320×180）
- 分区列表 5 分钟内存缓存
- 列表分页加载，每页 20 条
- 播放页资源随生命周期释放

### 兼容性

- 最低支持 Android 6.0 (API 23)
- 安装包大小约 3.5 MB

### API 调用（仅 5 个公开接口）

- `GET /api/v1/videos` — 视频列表
- `GET /api/v1/videos/{id}` — 视频详情
- `GET /api/v1/partitions` — 分区列表
- `GET /api/v1/search` — 搜索
- `GET /api/v1/danmaku` — 弹幕

不包含任何后台管理接口。
