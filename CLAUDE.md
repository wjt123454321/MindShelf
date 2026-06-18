# MindShelf — AI 知识库

## 项目目标

完成一个 AI 知识库 APP，包含 **Android 客户端** 与 **服务端**。

## 文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 项目说明 | [README.md](README.md) | 项目入口、快速开始 |
| 需求分析 | [docs/requirements.md](docs/requirements.md) | 功能需求、非功能需求、优先级（需求阶段已完成） |
| 架构设计 | [docs/architecture.md](docs/architecture.md) | 已完成 |
| API 设计 | [docs/api.md](docs/api.md) | 已完成 |

**开发流程：** 先确定需求 → 再修改代码 → 最后及时完善文档。

## 功能

MindShelf 是一个 AI 知识库应用，核心围绕 **AI 对话** 与 **知识/笔记管理**：

- **AI**：多轮对话、语音交互、联网与工具调用，支持服务端内置 API 或本机自定义 API
- **知识管理**：知识库与笔记的创建、编辑、版本与回收站；离线优先，可选云同步
- **用户与协作**：邮箱注册登录、内容分享、AI 可创建的自定义页面

详细需求与优先级见 [docs/requirements.md](docs/requirements.md)。

## 代码要求

1. 代码简洁、注释清晰
2. 文档完善且简洁清晰
3. 文档和代码禁止“改进版”“修改版”“无bug版”等表述和很多括号补丁
4. 遵循现有项目结构与命名约定

## 项目结构（当前）

```
MindShelf/
├── app/                 # Android 客户端（含本地 SQLite）
├── server/              # Flask 服务端
├── docs/                # 项目文档
│   └── requirements.md  # 需求分析
├── README.md            # 项目说明
├── CLAUDE.md            # 本文件：项目指引
└── ...
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 客户端 | Android (Kotlin)、Gradle、本地 SQLite |
| 服务端 | Flask (Python) |
| 数据库 | SQLite |
| AI | 内置 API（服务端） + 本机自定义 API（不上云） |
| 认证 | 邮箱注册；密码或验证码登录（SMTP / QQ 邮箱） |
| 同步 | 离线编辑默认开启；云同步可选；冲突三路合并 |

## 开发要求

- 动手写代码前，先阅读 [docs/requirements.md](docs/requirements.md) 确认范围与优先级
- 新功能按 Phase 1 → 4 顺序推进，避免跳跃实现低优先级模块
- 需求改变要先更新需求文档，确认后更新技术、api文档，最后代码实现
- 文档与代码同步更新：功能完成后更新对应 docs 条目

## 页面要求

客户端 UI 面向长期阅读与 AI 交互，整体气质为 **清晰、可信、不花哨**（知识工具，非社交 App）。

### 设计风格

当前采用 **「墨蓝智识」+ Calm Productivity**：Material 3 为基础，留白适中、层次靠排版与边框而非重阴影。

| 参考产品 | 借鉴点 |
|----------|--------|
| DeepSeek / ChatGPT | 对话沉浸感、底部固定输入栏、流式输出与 Markdown 渲染 |
| Notion | 列表卡片信息架构、中性背景、长文阅读友好 |
| Obsidian | 深色模式、知识库组织感 |

**品牌色（Compose 主题，见 `app/.../ui/theme/`）：**

| Token | 亮色 | 用途 |
|-------|------|------|
| Primary | `#1E5AA8` | 按钮、选中态、链接 |
| Tertiary | `#2E7D6B` | 笔记色条、成功/知识标签 |
| Background | `#F7F8FA` | 页面背景 |
| Surface | `#FFFFFF` | 卡片、TopBar |

深色模式需与亮色成对维护（`Theme.kt` 中 `LightColorScheme` / `DarkColorScheme`），跟随系统切换。

**形状与排版：** 圆角 8 / 12 / 16dp；卡片默认 0 elevation + 细边框；标题 `titleMedium`，正文 `bodyLarge`，辅助 `labelMedium`。

### 组件与交互规范

- **空状态**：使用 `EmptyState`（图标 + 标题 + 引导文案），禁止仅一行文字
- **加载**：首屏用 `ListSkeleton` 骨架屏；局部操作用 `CircularProgressIndicator`
- **刷新**：列表页支持下拉刷新（`PullToRefreshBox`）
- **删除**：列表项左滑露出删除，弹出 `ConfirmDeleteDialog` 确认，避免行内误触删除按钮
- **AI 消息**：用户气泡右对齐纯色；AI 气泡左对齐 + 头像，内容用 `MarkdownText`（代码块、粗体、行内代码）；流式输出末尾显示光标
- **底部导航**：4 Tab（对话 / 知识库 / 笔记 / 我的）；选中项 Filled 图标 + Primary 色，未选中 Outlined

### 各模块页面要点

| 模块 | 要求 |
|------|------|
| 登录 | Logo + Slogan；表单置于 `ElevatedCard`；SegmentedButton 切换登录方式 |
| 对话 | 空状态引导；输入区圆角容器 + 实心发送按钮；TopBar 显示会话标题 |
| 对话列表 | 显示相对更新时间；选中会话边框高亮 |
| 笔记列表 | 左侧色条、摘要、更新时间；FAB 新建 |
| 笔记编辑 | 无边框大标题 + 正文区；TopBar 保存按钮 |
| 知识库 | 文件夹图标块 + 笔记数量徽章 |
| 我的 | 头像首字母、设置式信息列表、描边退出按钮 |

### 动画与流畅度

动画应 ** subtle、有目的**，提升反馈感而非装饰：

| 场景 | 建议 |
|------|------|
| 列表滚动 | 默认即可；必要时对新增项做 `animateItem` |
| 页面切换 | Navigation 默认过渡；全屏编辑可用共享元素（低优先级） |
| 消息流式 | 自动滚到底部；流式光标闪烁 |
| 骨架屏 | shimmer 呼吸动画（已实现） |
| 按钮 / 卡片 | `Modifier.clickable` 配合 ripple；禁用态降低透明度 |
| 底栏切换 | 指示器颜色过渡（Material 3 默认） |

避免：过长动画、同时多处动效、影响性能的无限复杂动画。

### 实现约定

1. 新页面必须接入 `MindShelfTheme`，禁止硬编码颜色（除主题 Token 定义处）
2. 可复用组件放 `app/.../ui/components/`，页面只组合不重复造轮子
3. 改动范围最小化：先统一 Token / 组件，再逐页打磨
4. 新 UI 能力落地后，若影响整体规范，同步更新本节

