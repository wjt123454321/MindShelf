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

在 Android Studio 中打开项目，运行 `app` 模块。

**API 地址（本地配置，勿提交真实公网 IP）：**

| 场景 | 客户端 `app/build.gradle.kts` → `API_BASE_URL` | 服务端 `config.yaml` → `share.base_url` |
|------|--------------------------------------------------|-------------------------------------------|
| 模拟器连本机 Flask | `http://10.0.2.2:5000/api/v1/` | `http://10.0.2.2:5000`（仅本机预览） |
| 真机连局域网服务端 | `http://<你的局域网IP>:5000/api/v1/` | `http://<你的局域网IP>:5000` |
| 公网部署 | `http://<你的域名或公网IP>/api/v1/` | `https://<你的域名>`（推荐 HTTPS） |

若使用 HTTP 非标准域名，在 `app/src/main/res/xml/network_security_config.xml` 中追加对应 `<domain>`。上述地址仅写在本地配置文件中，不要写入 README 或提交到版本库。

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
- 公开分享链接（笔记、知识库、页面，只读，可撤销）

## Phase 4 已实现

- AI 创建/修改自定义页面（`mutate_custom_page`）
- Schema 渲染（文本、待办、表格等）与用户编辑
- 底栏 dynamic_pinned、页面列表与分享

未做项见 [Todo.md](Todo.md)「后续计划」（如版本对比 VER-03）。

## 开发阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1 | 账号、笔记/知识库、AI 基础对话、Flask 骨架 | ✅ |
| Phase 2 | 自定义 API、工具调用、联网、语音 | ✅ |
| Phase 3 | 云同步、回收站、版本历史、分享 | ✅ |
| Phase 4 | 自定义页面 | ✅ |

## 许可证

待定。
