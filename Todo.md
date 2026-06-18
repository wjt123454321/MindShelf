# MindShelf 开发任务

## Phase 1 ✅ 已完成
- 邮箱注册 / 密码与验证码登录
- 笔记、知识库 CRUD + 本地离线
- AI 对话（流式、分支）
- Flask 服务端骨架

## Phase 2 ✅ 已完成
- 内置 AI + 本机自定义 API（共用 `ToolLoopEngine`）
- 工具调用（客户端 `ClientToolDispatcher`，读写 Room）
- 联网搜索（`POST /ai/search` 代理）
- 语音输入（STT）与语音回答（TTS）

## Phase 3 ✅ 已完成
- [x] 服务端：回收站 API + 30 天定时清理
- [x] 服务端：笔记历史版本（最多 10 条）+ 恢复
- [x] 服务端：公开分享链接 + 只读访问
- [x] 服务端：云同步 pull / push / resolve（含对话/分支/消息）
- [x] 客户端：`SyncCoordinator` 云同步默认开启
- [x] 客户端：回收站 / 版本 / 分享 / 冲突 UI
- [x] 客户端：对话 offline-first + 纳入 sync
- [x] 架构重构：服务端 LLM 纯代理；废弃服务端工具执行
- [ ] 版本对比（VER-03，可后续迭代）

## Phase 4 — 待开始
- AI 创建/修改自定义页面
- 页面数据绑定与底栏固定
