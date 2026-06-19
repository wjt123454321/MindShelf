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
- 服务端：回收站 API + 30 天定时清理
- 服务端：笔记历史版本（最多 10 条）+ 恢复
- 服务端：公开分享链接 + 只读访问
- 服务端：云同步 pull / push / resolve（含对话/分支/消息）
- 客户端：`SyncCoordinator` 云同步（可开关）
- 客户端：回收站 / 版本列表与回滚 / 分享 / 冲突 UI
- 客户端：对话 offline-first + 纳入 sync
- 架构重构：服务端 LLM 纯代理；废弃服务端工具执行

## Phase 4 ✅ 已完成
- 文档：architecture §9 / api §7 schema 契约
- 服务端：CustomPage 模型 + `/pages` CRUD + pinned 互斥
- 服务端：sync pull/push pages + 回收站 + 分享 page HTML
- 客户端：Room + PageRepository + SyncEngine
- 客户端：PageSchemaValidator + PageRenderer + 5 种组件
- 客户端：dynamic_pinned 底栏 + PagesListScreen + 用户编辑
- 客户端：`search_custom_pages` / `mutate_custom_page` 工具链

---

## 后续计划

当前 Phase 1～4 核心功能已收尾。以下为需求文档中**未实现**或**仅简化实现**的项，可按优先级迭代。

### P1 — 功能缺口

| 编号 | 项 | 说明 |
|------|-----|------|
| VER-03 | 笔记版本对比 | 版本历史页仅支持列表与回滚；缺两版 diff 视图。首版可客户端对比 title/content，后续可加 `GET .../versions/compare` |
| SYNC-03 | 三路合并增强 | 冲突 UI 现为「保留本地 / 保留云端」；缺字段级自动合并与同屏手动编辑合并稿（服务端 resolve 已支持 `merged`） |

### P2 — 体验增强

| 编号 | 项 | 说明 |
|------|-----|------|
| NET-03 | 来源标注体验 | 联网结果依赖 Markdown 链接与简要提示；可加强为可折叠来源卡片、引用块高亮等 |
| AUTH-04 | 修改用户名 | 注册时可填用户名；个人中心暂不可修改 |
| PAGE-* | 自定义页面扩展 | 复杂布局 `WebViewBlock`（architecture §9）；看板/图表等高级组件与模板（需求示例场景） |

### P3 — 可选优化

- **同步**：对话/页面等冲突的可视化与批量处理
- **分享**：笔记/知识库/页面导出（Markdown、PDF 等）
- **AI**：工具写操作审计日志（architecture §11 audit_log）
- **UI**：全屏编辑共享元素过渡、更细列表动效（CLAUDE.md 低优先级）
- **部署**：生产环境证书固定、监控与日志聚合
