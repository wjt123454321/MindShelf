# MindShelf

AI 驱动的个人知识库应用，包含 **Android 客户端** 与 **Flask 服务端**。

## 文档

| 文档 | 说明 |
|------|------|
| [docs/requirements.md](docs/requirements.md) | 需求分析 |
| [docs/architecture.md](docs/architecture.md) | 架构设计 |
| [docs/api.md](docs/api.md) | API 契约 |
| [CLAUDE.md](CLAUDE.md) | 开发约定 |

## 项目结构

```
MindShelf/
├── app/                  # Android 客户端（Compose + Hilt + Retrofit）
├── server/               # Flask 服务端
├── docs/
├── config.txt            # 本地配置源（勿提交，见 .gitignore）
└── README.md
```

## 快速开始

### 1. 配置

将根目录 `config.txt` 中的信息写入 `server/config.yaml`（或复制 `server/config.example.yaml` 后填写）：

- 内置 AI：`base_url`、`api_key`、`model`
- SMTP：QQ 邮箱主机、账号、授权码
- JWT：`secret`（生产环境请使用随机字符串）

`config.yaml`、`config.txt` 已加入 `.gitignore`，不会提交到版本库。

### 2. 启动服务端

```bash
cd server
python -m pip install -r requirements.txt
python run.py
```

服务默认监听 `http://0.0.0.0:5000`。模拟器访问 `http://10.0.2.2:5000/api/v1/`。

### 3. 启动客户端

**环境：** Android Studio、JDK 11+、minSdk 24

```bash
gradlew.bat assembleDebug    # Windows
./gradlew assembleDebug      # Linux / macOS
```

在 Android Studio 中打开项目，运行 `app` 模块。Debug 构建已允许对 `10.0.2.2` 的明文 HTTP。

真机调试时，将 `app/build.gradle.kts` 中 debug 的 `API_BASE_URL` 改为电脑局域网 IP。

## Phase 1 已实现

- 邮箱注册 / 密码登录 / 验证码登录
- 笔记、知识库 CRUD
- AI 对话（内置 DeepSeek，SSE 流式）
- 会话与分支

## Phase 2 已实现

- 内置 AI + 本机自定义 API（加密存储）
- AI 工具调用（知识库/笔记读写，写操作确认）
- 联网搜索与来源标注
- 语音输入（STT）与语音回答（TTS）

## Phase 3 已实现

- 可选云同步（Pull / Push，冲突时择一合并）
- 回收站（恢复、永久删除，服务端 30 天自动清理）
- 笔记历史版本（自动快照，最多 10 条，可恢复）
- 公开分享链接（笔记、知识库，只读，可撤销）

## 开发阶段

| 阶段 | 内容 |
|------|------|
| Phase 1 | 账号、笔记/知识库、AI 基础对话、Flask 骨架 |
| Phase 2 | 自定义 API、工具调用、联网、语音 |
| Phase 3 | 云同步、回收站、版本历史、分享 |
| Phase 4 | 自定义页面 |

## 许可证

待定。
