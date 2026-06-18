# MindShelf API 设计

> 本文档定义 Flask 服务端 REST / SSE 接口契约，与 [架构设计](architecture.md)、[需求分析](requirements.md) 对齐。  
> **不在本文档范围：** 自定义 AI 直连第三方 API（客户端本地执行）；`AiProvider` 配置 CRUD（仅客户端 Room）。

---

## 1. 约定

### 1.1 Base URL

| 环境 | Base URL |
|------|----------|
| 开发 | `http://10.0.2.2:5000/api/v1`（模拟器）或 `http://<局域网 IP>:5000/api/v1` |
| 生产 | `https://<domain>/api/v1` |

公开分享只读接口前缀为 `/s/`（无 `/api/v1`），见 §10。

### 1.2 协议与格式

| 项 | 约定 |
|----|------|
| 请求 / 响应体 | `application/json; charset=utf-8` |
| 流式 AI | `text/event-stream`（SSE） |
| 字符编码 | UTF-8 |
| 时间戳 | Unix 毫秒整数（`created_at`、`updated_at`、`deleted_at`） |
| ID | UUID v4 字符串，客户端可预生成（同步场景） |
| 布尔 | JSON `true` / `false` |
| 空值 | 字段可省略时用 `null` |

### 1.3 鉴权

除 §10 公开分享与 §2.2 发送验证码外，请求需携带：

```http
Authorization: Bearer <access_token>
```

| Token | 默认 TTL | 说明 |
|-------|----------|------|
| access_token | 15 分钟 | API 调用 |
| refresh_token | 30 天 | 仅用于刷新 access |

刷新失败返回 `401`，客户端应引导重新登录。

### 1.4 统一响应结构

**成功（单资源）：**

```json
{
  "data": { }
}
```

**成功（列表）：**

```json
{
  "data": [ ],
  "meta": {
    "page": 1,
    "page_size": 20,
    "total": 100
  }
}
```

**失败：**

```json
{
  "error": {
    "code": "NOTE_NOT_FOUND",
    "message": "笔记不存在或无权访问"
  }
}
```

HTTP 状态码与 `error.code` 对应关系见 §12。

### 1.5 分页与排序

列表接口通用 Query：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `page` | int | 1 | 页码，从 1 起 |
| `page_size` | int | 20 | 每页条数，最大 100 |
| `sort` | string | 各接口默认 | 如 `updated_at:desc` |

### 1.6 软删除

含 `deleted_at` 的资源：

- 正常 CRUD 列表**不包含**已删除项（`deleted_at != null`）。
- 回收站见 §9；删除操作为设置 `deleted_at`，非物理删除。

### 1.7 实施阶段标注

各接口标注 **Phase 1–4**，实现顺序与 [requirements.md §6](requirements.md#6-实施优先级) 一致。

---

## 2. 认证

### 2.1 注册（密码） — Phase 1

```http
POST /auth/register
```

**Request**

```json
{
  "email": "user@example.com",
  "password": "secret123",
  "code": "123456",
  "username": "mindshelf_user"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| email | 是 | 合法邮箱 |
| password | 是 | ≥ 8 位 |
| code | 是 | 邮箱验证码（`purpose=register`） |
| username | 否 | 唯一；省略则仅邮箱登录 |

**Response `201`**

```json
{
  "data": {
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "username": "mindshelf_user",
      "created_at": 1718400000000
    },
    "access_token": "...",
    "refresh_token": "...",
    "expires_in": 900
  }
}
```

### 2.2 发送验证码 — Phase 1

```http
POST /auth/send-code
```

**Request**

```json
{
  "email": "user@example.com",
  "purpose": "login"
}
```

| purpose | 说明 |
|---------|------|
| `login` | 登录 / 未注册邮箱自动建号 |
| `register` | 注册时验证邮箱 |
| `reset_password` | 重置密码（后续扩展） |

**Response `200`**

```json
{
  "data": {
    "expires_in": 300,
    "retry_after": 60
  }
}
```

限流：同一邮箱 60 秒内不可重复发送；验证码 6 位数字，5 分钟有效。

### 2.3 登录（密码） — Phase 1

```http
POST /auth/login
```

**Request**

```json
{
  "account": "user@example.com",
  "password": "secret123"
}
```

`account` 可为邮箱或 `username`。

**Response `200`** — 同 §2.1 中 `user` + tokens 结构。

### 2.4 登录（验证码） — Phase 1

```http
POST /auth/login/code
```

**Request**

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

邮箱未注册时自动创建账号（`password_hash` 为空）。**Response** 同 §2.3。

### 2.5 刷新 Token — Phase 1

```http
POST /auth/refresh
```

**Request**

```json
{
  "refresh_token": "..."
}
```

**Response `200`**

```json
{
  "data": {
    "access_token": "...",
    "refresh_token": "...",
    "expires_in": 900
  }
}
```

### 2.6 当前用户 — Phase 1

```http
GET /auth/me
```

**Response `200`**

```json
{
  "data": {
    "id": "uuid",
    "email": "user@example.com",
    "username": "mindshelf_user",
    "created_at": 1718400000000
  }
}
```

### 2.7 登出 — Phase 1

```http
POST /auth/logout
```

**Request**

```json
{
  "refresh_token": "..."
}
```

服务端作废该 refresh_token。**Response `204`** 无 body。

---

## 3. 笔记

### 3.1 笔记对象

```json
{
  "id": "uuid",
  "title": "标题",
  "content": "Markdown 正文",
  "knowledge_base_ids": ["kb-uuid"],
  "sync_version": 3,
  "created_at": 1718400000000,
  "updated_at": 1718401000000,
  "deleted_at": null
}
```

### 3.2 列表 — Phase 1

```http
GET /notes
```

| Query | 说明 |
|-------|------|
| `q` | 标题 / 正文关键词 |
| `kb_id` | 限定某知识库 |
| `updated_after` | 增量拉取（同步用，Phase 3） |

### 3.3 创建 — Phase 1

```http
POST /notes
```

**Request**

```json
{
  "id": "uuid",
  "title": "新笔记",
  "content": "",
  "knowledge_base_ids": []
}
```

`id` 可选；客户端离线创建时传入以便同步对齐。

**Response `201`** — `{ "data": <Note> }`

### 3.4 获取 — Phase 1

```http
GET /notes/{note_id}
```

### 3.5 更新 — Phase 1

```http
PATCH /notes/{note_id}
```

**Request**（部分字段）

```json
{
  "title": "更新标题",
  "content": "更新正文",
  "knowledge_base_ids": ["kb-uuid"],
  "sync_version": 3
}
```

若 `sync_version` 与服务端不一致且启用同步，返回 `409 CONFLICT`（见 §8.3）。

**Response `200`** — `{ "data": <Note> }`（`sync_version` 递增）

### 3.6 删除（进回收站） — Phase 1

```http
DELETE /notes/{note_id}
```

**Response `204`**

---

## 4. 知识库

### 4.1 知识库对象

```json
{
  "id": "uuid",
  "name": "工作笔记",
  "description": "",
  "sort_order": 0,
  "note_count": 12,
  "created_at": 1718400000000,
  "updated_at": 1718401000000,
  "deleted_at": null
}
```

### 4.2 列表 — Phase 1

```http
GET /knowledge-bases
```

### 4.3 创建 — Phase 1

```http
POST /knowledge-bases
```

**Request**

```json
{
  "id": "uuid",
  "name": "新知识库",
  "description": "",
  "sort_order": 0
}
```

### 4.4 获取 / 更新 / 删除 — Phase 1

```http
GET    /knowledge-bases/{kb_id}
PATCH  /knowledge-bases/{kb_id}
DELETE /knowledge-bases/{kb_id}
```

**PATCH Request 示例**

```json
{
  "name": "重命名",
  "description": "描述",
  "sort_order": 1
}
```

### 4.5 知识库内笔记 — Phase 1

```http
GET /knowledge-bases/{kb_id}/notes
```

等价于 `GET /notes?kb_id={kb_id}`；支持 `q` 搜索。

### 4.6 关联笔记 — Phase 1

```http
PUT /knowledge-bases/{kb_id}/notes/{note_id}
```

建立关联（幂等）。**Response `204`**

```http
DELETE /knowledge-bases/{kb_id}/notes/{note_id}
```

解除关联。**Response `204`**

---

## 5. AI 对话

### 5.1 会话对象

```json
{
  "id": "uuid",
  "title": "关于 Kotlin 的问题",
  "created_at": 1718400000000,
  "updated_at": 1718401000000
}
```

### 5.2 分支对象

```json
{
  "id": "uuid",
  "conversation_id": "uuid",
  "label": "分支 2",
  "root_message_id": "msg-uuid",
  "created_at": 1718400000000
}
```

### 5.3 消息对象

```json
{
  "id": "uuid",
  "conversation_id": "uuid",
  "branch_id": "uuid",
  "parent_id": "msg-uuid",
  "role": "user",
  "content": "你好",
  "created_at": 1718400000000
}
```

| role | 说明 |
|------|------|
| `user` | 用户 |
| `assistant` | AI |
| `system` | 系统（一般不返回给客户端展示） |
| `tool` | 工具结果（Phase 2） |

### 5.4 会话 CRUD — Phase 1

```http
GET    /conversations
POST   /conversations
GET    /conversations/{conversation_id}
PATCH  /conversations/{conversation_id}
DELETE /conversations/{conversation_id}
```

**POST Request**

```json
{
  "id": "uuid",
  "title": "新对话"
}
```

**DELETE** 级联删除所有 branches 与 messages。

### 5.5 分支 — Phase 1

```http
GET  /conversations/{conversation_id}/branches
POST /conversations/{conversation_id}/branches
```

**POST Request**（重新提问时创建）

```json
{
  "id": "uuid",
  "label": "分支 2",
  "fork_from_message_id": "msg-uuid"
}
```

服务端根据 `fork_from_message_id` 计算上下文起点，创建 branch 并返回。

```http
GET /conversations/{conversation_id}/branches/{branch_id}
```

### 5.6 消息 — Phase 1

```http
GET /conversations/{conversation_id}/branches/{branch_id}/messages
```

按时间序返回该分支消息链（含从 fork 点回溯的上下文，或客户端自行拼接；**推荐服务端返回完整上下文链**）。

```http
POST /conversations/{conversation_id}/branches/{branch_id}/messages
```

**Request**

```json
{
  "id": "uuid",
  "parent_id": "msg-uuid",
  "role": "user",
  "content": "用户消息"
}
```

用于持久化用户消息（AI 回复由流式接口写入）。**Response `201`**

### 5.7 内置 AI LLM 代理

```http
POST /ai/completions
Authorization: Bearer <access_token>
Accept: text/event-stream   # stream=true 时
Content-Type: application/json
```

**Request**（OpenAI Chat Completions 兼容）

```json
{
  "model": "deepseek-chat",
  "messages": [
    { "role": "system", "content": "…" },
    { "role": "user", "content": "请解释协程" }
  ],
  "tools": [],
  "stream": true
}
```

| 字段 | 说明 |
|------|------|
| model | 须在服务端 `config.yaml` 的 `ai.models` 白名单内 |
| tools | 可选；由客户端 `ToolLoopEngine` 注入 |
| stream | `true` 时响应为 OpenAI SSE 格式原样透传 |

**Response** — `stream=true` 时为上游 SSE；`stream=false` 时为 JSON ChatCompletion。

> **已废弃**：`POST /ai/chat/stream`（服务端内嵌 tool loop 与消息落库）。请改用本接口 + 客户端 tool loop。

### 5.8 联网搜索代理

```http
POST /ai/search
Authorization: Bearer <access_token>
```

**Request**

```json
{
  "query": "Kotlin 协程 最新"
}
```

**说明**：UAPI 等搜索源仅返回标题/链接/摘要；`fetch_pages: true` 时服务端会对结果链接本地抓取正文（见 `server/config.yaml` 中 `max_fetch_pages`、`page_max_chars`）。

**Response `200`**

```json
{
  "data": {
    "query": "Kotlin 协程 最新",
    "results": [
      {
        "title": "…",
        "url": "https://…",
        "snippet": "…",
        "content": "正文摘录（本地抓取，可选）"
      }
    ],
    "result_count": 1,
    "context": "格式化后的完整检索上下文（供 LLM tool 结果使用）",
    "context_preview": "…"
  }
}
```

### 5.9 工具写操作确认（客户端）

写工具（`mutate_note` / `mutate_knowledge_base`）由客户端 `ToolLoopEngine` 生成预览，UI 展示 `ToolActionCard`；用户确认后 `ClientToolDispatcher` 写 Room，并由 `SyncCoordinator` 推送云端。

> **已废弃**：`POST /ai/tools/confirm`、`POST /ai/tools/resume/stream`、`GET /ai/tools/pending`。

---

## 6. 笔记历史版本 — Phase 3

### 6.1 版本对象

```json
{
  "id": "uuid",
  "note_id": "uuid",
  "title": "快照标题",
  "content": "快照正文",
  "created_at": 1718400000000
}
```

每笔记最多 **10** 条；超出时服务端删除最旧版本。

### 6.2 接口

```http
GET  /notes/{note_id}/versions
GET  /notes/{note_id}/versions/{version_id}
POST /notes/{note_id}/versions/{version_id}/restore
```

**restore** 将笔记回滚到该版本（生成新版本快照）。**Response `200`** — `{ "data": <Note> }`

版本对比（VER-03）首版可只做客户端 diff；后续可加：

```http
GET /notes/{note_id}/versions/compare?from={v1}&to={v2}
```

---

## 7. 自定义页面 — Phase 4

### 7.1 页面对象

```json
{
  "id": "uuid",
  "name": "待办",
  "schema_json": { },
  "data_bindings": { },
  "pinned": false,
  "created_at": 1718400000000,
  "updated_at": 1718401000000,
  "deleted_at": null
}
```

### 7.2 接口

```http
GET    /pages
POST   /pages
GET    /pages/{page_id}
PATCH  /pages/{page_id}
DELETE /pages/{page_id}
```

**PATCH** 可更新 `name`、`schema_json`、`data_bindings`、`pinned`。

`schema_json` 结构见 [architecture.md §9](architecture.md#9-自定义页面渲染架构决策)。

---

## 8. 云同步 — Phase 3

### 8.1 拉取增量

```http
GET /sync/pull?since={unix_ms}
```

**Response `200`**

```json
{
  "data": {
    "server_time": 1718402000000,
    "notes": [ ],
    "knowledge_bases": [ ],
    "note_kb_links": [ ],
    "conversations": [ ],
    "branches": [ ],
    "messages": [ ],
    "pages": [ ],
    "share_links": [ ],
    "tombstones": [
      { "entity": "note", "id": "uuid", "deleted_at": 1718400000000 }
    ]
  }
}
```

`since` 为客户端 `last_synced_at`；首次同步省略或 `since=0`。

### 8.2 推送变更

```http
POST /sync/push
```

**Request**

```json
{
  "client_time": 1718401500000,
  "notes": [ ],
  "knowledge_bases": [ ],
  "note_kb_links": [ ],
  "conversations": [ ],
  "branches": [ ],
  "messages": [ ],
  "pages": [ ],
  "deletes": [
    { "entity": "note", "id": "uuid", "deleted_at": 1718401000000 }
  ]
}
```

**Response `200`**

```json
{
  "data": {
    "server_time": 1718402000000,
    "applied": [ { "entity": "note", "id": "uuid", "sync_version": 4 } ],
    "conflicts": [
      {
        "entity": "note",
        "id": "uuid",
        "base": { "title": "A", "content": "..." },
        "local": { "title": "B", "content": "..." },
        "remote": { "title": "C", "content": "..." }
      }
    ]
  }
}
```

客户端对 `conflicts` 展示三路合并 UI，解决后带选定结果再次 `PATCH` 或 `push`。

### 8.3 冲突解决

```http
POST /sync/resolve
```

**Request**

```json
{
  "entity": "note",
  "id": "uuid",
  "resolution": "local",
  "merged": null
}
```

| resolution | 说明 |
|------------|------|
| `local` | 采用本地 |
| `remote` | 采用远端 |
| `merged` | 提供 `merged` 对象作为合并结果 |

---

## 9. 回收站 — Phase 3

```http
GET /trash
```

**Response** — 混合列表，每项含 `entity_type`（`note` | `knowledge_base` | `page`）、`entity` 快照、`deleted_at`、`expires_at`（`deleted_at + 30 天`）。

```http
POST /trash/restore
```

**Request**

```json
{
  "entity_type": "note",
  "id": "uuid"
}
```

```http
DELETE /trash/{entity_type}/{id}
```

永久删除。**Response `204`**

---

## 10. 公开分享

### 10.1 创建分享链接 — Phase 3

```http
POST /share/links
```

**Request**

```json
{
  "resource_type": "note",
  "resource_id": "uuid"
}
```

| resource_type | 说明 |
|---------------|------|
| `note` | 笔记 |
| `knowledge_base` | 知识库 |
| `page` | 自定义页面（Phase 4） |

**Response `201`**

```json
{
  "data": {
    "id": "uuid",
    "token": "random-url-safe-string",
    "url": "https://<domain>/s/random-url-safe-string",
    "resource_type": "note",
    "resource_id": "uuid",
    "revoked": false,
    "created_at": 1718400000000
  }
}
```

### 10.2 管理分享 — Phase 3

```http
GET   /share/links
DELETE /share/links/{link_id}
```

DELETE 即撤销（`revoked=true`）。

### 10.3 公开只读访问 — Phase 3

**无需鉴权**

```http
GET /s/{token}
```

**Response `200`**

```json
{
  "data": {
    "resource_type": "note",
    "snapshot": {
      "title": "标题",
      "content": "正文"
    },
    "shared_at": 1718400000000
  }
}
```

`knowledge_base` 返回笔记列表快照；`page` 返回 `schema_json` + 绑定数据快照。  
已撤销或不存在 → `404`。

---

## 11. SSE 事件格式

`POST /ai/chat/stream` 响应中，每行：

```
event: <type>
data: <json>

```

### 11.1 事件类型

| event | 说明 | Phase |
|-------|------|-------|
| `message_delta` |  assistant 文本增量 | 1 |
| `message_done` | 单条 assistant 消息完成，含完整 `message` 对象 | 1 |
| `tool_call` | 开始工具调用 | 2 |
| `tool_result` | 只读工具结果 | 2 |
| `tool_pending` | 写工具待确认，含 `pending_id` 与预览 | 2 |
| `search_result` | 联网搜索结果摘要 | 2 |
| `error` | 流内错误 | 1 |
| `done` | 本轮对话结束 | 1 |

### 11.2 示例

```
event: message_delta
data: {"content":"你"}

event: message_delta
data: {"content":"好"}

event: message_done
data: {"message":{"id":"uuid","role":"assistant","content":"你好","parent_id":"...","branch_id":"..."}}

event: done
data: {"conversation_id":"uuid","branch_id":"uuid"}
```

**tool_pending 示例（Phase 2）**

```
event: tool_pending
data: {
  "pending_id": "uuid",
  "tool": "mutate_note",
  "preview": {
    "action": "update",
    "note_id": "uuid",
    "before": {
      "title": "旧标题",
      "content": "旧正文"
    },
    "after": {
      "title": "新标题",
      "content": "新正文"
    }
  }
}
```

创建操作 `preview.after` 含待创建内容；删除操作 `preview.before` 含待删对象摘要。知识库变更使用 `name` / `description` 字段。

**search_result 示例（Phase 2）**

```
event: search_result
data: {
  "query": "Kotlin 协程",
  "results": [
    {
      "title": "Kotlin 协程官方文档",
      "url": "https://kotlinlang.org/docs/coroutines-overview.html",
      "snippet": "协程是一种轻量级线程…"
    }
  ]
}
```

流内 `error` 不替代 HTTP 4xx；仅在已建立 SSE 连接后的运行时错误使用。

---

## 12. 错误码

| HTTP | code | 说明 |
|------|------|------|
| 400 | `INVALID_REQUEST` | 参数无效 |
| 401 | `UNAUTHORIZED` | 未登录或 token 无效 |
| 403 | `FORBIDDEN` | 无权访问资源 |
| 404 | `NOT_FOUND` | 资源不存在 |
| 404 | `NOTE_NOT_FOUND` | 笔记不存在 |
| 404 | `KB_NOT_FOUND` | 知识库不存在 |
| 404 | `SHARE_NOT_FOUND` | 分享不存在或已撤销 |
| 409 | `CONFLICT` | sync_version 冲突 |
| 409 | `EMAIL_EXISTS` | 邮箱已注册 |
| 409 | `USERNAME_EXISTS` | 用户名已占用 |
| 429 | `RATE_LIMITED` | 验证码等限流 |
| 500 | `INTERNAL_ERROR` | 服务端错误 |

---

## 13. 客户端-only 能力（无服务端 API）

| 能力 | 说明 |
|------|------|
| 自定义 AI 配置 | Room + EncryptedSharedPreferences，见 AI-CFG-* |
| 自定义 AI 对话 | 客户端直连 OpenAI 兼容 API |
| 自定义 AI 工具 | 客户端本地 dispatch，读写本地 Room |
| STT / TTS | Android 系统 API |

---

## 14. 接口与 Phase 对照

| Phase | 接口模块 |
|-------|----------|
| **1** | §2 认证；§3 笔记；§4 知识库；§5 对话 CRUD |
| **2** | §5.7 `/ai/completions`；§5.8 `/ai/search`；客户端 tool loop |
| **3** | §6 版本；§8 同步（含对话）；§9 回收站；§10 分享 |
| **4** | §7 自定义页面；分享 `resource_type=page` |

---

## 15. 相关文档

| 文档 | 说明 |
|------|------|
| [architecture.md](architecture.md) | 架构与数据模型 |
| [requirements.md](requirements.md) | 功能需求 |
| [../CLAUDE.md](../CLAUDE.md) | 开发约定 |

---

## 16. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-06-15 | 初版：REST / SSE 契约，按 Phase 1–4 划分 |
