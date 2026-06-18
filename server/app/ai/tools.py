"""AI 工具定义与执行。"""

import json
from typing import Any

from flask import g

from app.ai import search as search_service
from app.extensions import db
from app.models import KnowledgeBase, Note, NoteKb, PendingToolConfirmation
from app.utils.auth import new_id, now_ms

READ_TOOLS = {"search_knowledge_bases", "search_notes", "web_search"}
WRITE_TOOLS = {"mutate_knowledge_base", "mutate_note"}


def tool_schemas(*, include_web_search: bool = False) -> list[dict]:
    schemas = [
        {
            "type": "function",
            "function": {
                "name": "search_knowledge_bases",
                "description": "搜索或列出用户在本应用内保存的知识库（不含互联网内容）",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "description": "名称关键词，留空则列出全部"},
                    },
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "search_notes",
                "description": "搜索或读取用户在本应用内保存的笔记（不含新闻或网页）",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "description": "标题或正文关键词"},
                        "note_id": {"type": "string", "description": "指定笔记 ID 时返回全文"},
                        "kb_id": {"type": "string", "description": "限定在某个知识库内搜索"},
                    },
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "mutate_knowledge_base",
                "description": "创建、更新或删除知识库（需用户确认后执行）",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "action": {"type": "string", "enum": ["create", "update", "delete"]},
                        "kb_id": {"type": "string"},
                        "name": {"type": "string"},
                        "description": {"type": "string"},
                    },
                    "required": ["action"],
                },
            },
        },
        {
            "type": "function",
            "function": {
                "name": "mutate_note",
                "description": "创建、更新或删除笔记（需用户确认后执行）。更新正文/标题时不要传 knowledge_base_ids，除非要变更所属知识库。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "action": {"type": "string", "enum": ["create", "update", "delete"]},
                        "note_id": {"type": "string"},
                        "title": {"type": "string"},
                        "content": {"type": "string"},
                        "knowledge_base_ids": {
                            "type": "array",
                            "items": {"type": "string"},
                        },
                    },
                    "required": ["action"],
                },
            },
        },
    ]
    if include_web_search:
        schemas.append(
            {
                "type": "function",
                "function": {
                    "name": "web_search",
                    "description": (
                        "搜索互联网获取新闻、时事、百科等外部信息。"
                        "仅在需要最新或网页信息时使用；"
                        "用户本地笔记/知识库内容请用 search_notes / search_knowledge_bases。"
                    ),
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "query": {"type": "string", "description": "搜索关键词，支持中英文"},
                            "sort": {
                                "type": "string",
                                "enum": ["relevance", "date"],
                                "description": "排序方式，默认 relevance",
                            },
                            "time_range": {
                                "type": "string",
                                "enum": ["day", "week", "month", "year"],
                                "description": "限定时间范围",
                            },
                        },
                        "required": ["query"],
                    },
                },
            }
        )
    return schemas


def is_write_tool(name: str) -> bool:
    return name in WRITE_TOOLS


def execute_read_tool(name: str, arguments: dict) -> dict:
    if name == "search_knowledge_bases":
        return _search_knowledge_bases(arguments)
    if name == "search_notes":
        return _search_notes(arguments)
    if name == "web_search":
        return _web_search(arguments)
    return {"error": f"未知工具: {name}"}


def build_write_preview(name: str, arguments: dict) -> dict:
    action = arguments.get("action", "")
    if name == "mutate_note":
        preview: dict[str, Any] = {"action": action}
        note_id = (arguments.get("note_id") or "").strip()
        if note_id:
            preview["note_id"] = note_id
        if action == "create":
            preview["after"] = {
                "title": arguments.get("title") or "",
                "content": (arguments.get("content") or "")[:500],
            }
        elif action == "update" and note_id:
            note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
            if note:
                preview["before"] = {"title": note.title, "content": note.content}
                preview["after"] = {
                    "title": arguments["title"] if "title" in arguments else note.title,
                    "content": arguments["content"] if "content" in arguments else note.content,
                }
        elif action == "delete" and note_id:
            note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
            if note:
                preview["before"] = {"title": note.title, "content": note.content[:500]}
        return preview
    if name == "mutate_knowledge_base":
        preview = {"action": action}
        kb_id = (arguments.get("kb_id") or "").strip()
        if kb_id:
            preview["kb_id"] = kb_id
        if action == "create":
            preview["after"] = {
                "name": arguments.get("name") or "新知识库",
                "description": arguments.get("description") or "",
            }
        elif action == "update" and kb_id:
            kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
            if kb:
                preview["before"] = {"name": kb.name, "description": kb.description}
                preview["after"] = {
                    "name": arguments["name"] if "name" in arguments else kb.name,
                    "description": arguments["description"] if "description" in arguments else kb.description,
                }
        elif action == "delete" and kb_id:
            kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
            if kb:
                preview["before"] = {"name": kb.name, "description": kb.description}
        return preview
    return {"action": action}


def create_pending(
    conversation_id: str,
    branch_id: str,
    tool_name: str,
    arguments: dict,
    *,
    resume_messages_json: str = "[]",
    tool_call_id: str = "",
    partial_content: str = "",
    partial_reasoning: str = "",
    partial_segments_json: str = "[]",
    user_message_id: str = "",
    resume_round: int = 0,
) -> PendingToolConfirmation:
    preview = build_write_preview(tool_name, arguments)
    pending = PendingToolConfirmation(
        id=new_id(),
        user_id=g.user_id,
        conversation_id=conversation_id,
        branch_id=branch_id,
        tool_name=tool_name,
        arguments_json=json.dumps(arguments, ensure_ascii=False),
        preview_json=json.dumps(preview, ensure_ascii=False),
        resume_messages_json=resume_messages_json,
        tool_call_id=tool_call_id,
        partial_content=partial_content,
        partial_reasoning=partial_reasoning,
        partial_segments_json=partial_segments_json,
        user_message_id=user_message_id,
        resume_round=resume_round,
        expires_at=now_ms() + 10 * 60 * 1000,
    )
    db.session.add(pending)
    db.session.commit()
    return pending


def execute_pending(pending: PendingToolConfirmation, approved: bool) -> dict:
    if not approved:
        db.session.delete(pending)
        db.session.commit()
        return {"executed": False, "message": "用户已拒绝"}
    args = json.loads(pending.arguments_json)
    if pending.tool_name == "mutate_note":
        result = _mutate_note(args)
    elif pending.tool_name == "mutate_knowledge_base":
        result = _mutate_knowledge_base(args)
    else:
        result = {"error": "未知写工具"}
    pending.executed = True
    pending.tool_result_json = json.dumps(result, ensure_ascii=False)
    db.session.commit()
    return {"executed": True, "result": result, "pending_id": pending.id}


def delete_pending(pending: PendingToolConfirmation) -> None:
    db.session.delete(pending)
    db.session.commit()


def pending_tool_dict(pending: PendingToolConfirmation) -> dict:
    return {
        "id": pending.id,
        "conversation_id": pending.conversation_id,
        "branch_id": pending.branch_id,
        "tool": pending.tool_name,
        "preview": json.loads(pending.preview_json),
        "created_at": pending.created_at,
    }


def _web_search(args: dict) -> dict:
    if not search_service.is_enabled():
        return {"error": "联网搜索未配置或未启用"}
    query = (args.get("query") or "").strip()
    if not query:
        return {"error": "缺少搜索关键词"}
    results, context = search_service.gather_search_context(
        query,
        sort=(args.get("sort") or "").strip() or None,
        time_range=(args.get("time_range") or "").strip() or None,
    )
    return {
        "query": query,
        "results": results,
        "result_count": len(results),
        "context_preview": context[:1500] if context else "",
    }


def _search_knowledge_bases(args: dict) -> dict:
    query = (args.get("query") or "").strip()
    q = KnowledgeBase.query.filter_by(user_id=g.user_id, deleted_at=None)
    if query:
        like = f"%{query}%"
        q = q.filter(db.or_(KnowledgeBase.name.ilike(like), KnowledgeBase.description.ilike(like)))
    items = q.order_by(KnowledgeBase.sort_order, KnowledgeBase.updated_at.desc()).limit(20).all()
    return {
        "items": [
            {"id": kb.id, "name": kb.name, "description": kb.description}
            for kb in items
        ]
    }


def _search_notes(args: dict) -> dict:
    note_id = (args.get("note_id") or "").strip()
    if note_id:
        note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
        if note is None:
            return {"error": "笔记不存在"}
        kb_ids = [link.kb_id for link in NoteKb.query.filter_by(note_id=note.id)]
        return {
            "note": {
                "id": note.id,
                "title": note.title,
                "content": note.content,
                "knowledge_base_ids": kb_ids,
            }
        }

    query = (args.get("query") or "").strip()
    kb_id = (args.get("kb_id") or "").strip()
    q = Note.query.filter_by(user_id=g.user_id, deleted_at=None)
    if kb_id:
        q = q.join(NoteKb, Note.id == NoteKb.note_id).filter(NoteKb.kb_id == kb_id)
    if query:
        like = f"%{query}%"
        q = q.filter(db.or_(Note.title.ilike(like), Note.content.ilike(like)))
    notes = q.order_by(Note.updated_at.desc()).limit(20).all()
    return {
        "items": [
            {
                "id": n.id,
                "title": n.title,
                "snippet": n.content[:200],
            }
            for n in notes
        ]
    }


def _mutate_note(args: dict) -> dict:
    action = args.get("action")
    if action == "create":
        note = Note(
            id=args.get("note_id") or new_id(),
            user_id=g.user_id,
            title=args.get("title") or "",
            content=args.get("content") or "",
        )
        db.session.add(note)
        db.session.flush()
        for kb_id in args.get("knowledge_base_ids") or []:
            kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
            if kb:
                db.session.add(NoteKb(note_id=note.id, kb_id=kb_id))
        db.session.commit()
        kb_ids = [link.kb_id for link in NoteKb.query.filter_by(note_id=note.id)]
        return {
            "note_id": note.id,
            "title": note.title,
            "content": note.content,
            "knowledge_base_ids": kb_ids,
            "sync_version": note.sync_version,
            "updated_at": note.updated_at,
        }
    if action == "update":
        note_id = (args.get("note_id") or "").strip()
        note = None
        if note_id:
            note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
        if note is None:
            title = (args.get("title") or "").strip()
            if title:
                note = (
                    Note.query.filter_by(user_id=g.user_id, deleted_at=None, title=title)
                    .order_by(Note.updated_at.desc())
                    .first()
                )
        if note is None:
            return {"error": "笔记不存在，请确认笔记已同步到服务器"}
        if "title" in args:
            note.title = args["title"]
        if "content" in args:
            note.content = args["content"]
        note.sync_version += 1
        note.updated_at = now_ms()
        kb_ids_arg = args.get("knowledge_base_ids")
        if kb_ids_arg:
            NoteKb.query.filter_by(note_id=note.id).delete()
            for kb_id in kb_ids_arg:
                kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
                if kb:
                    db.session.add(NoteKb(note_id=note.id, kb_id=kb_id))
        db.session.commit()
        kb_ids = [link.kb_id for link in NoteKb.query.filter_by(note_id=note.id)]
        return {
            "note_id": note.id,
            "title": note.title,
            "content": note.content,
            "knowledge_base_ids": kb_ids,
            "sync_version": note.sync_version,
            "updated_at": note.updated_at,
        }
    if action == "delete":
        note_id = args.get("note_id")
        note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
        if note is None:
            return {"error": "笔记不存在"}
        note.deleted_at = now_ms()
        note.updated_at = now_ms()
        db.session.commit()
        return {"note_id": note.id, "deleted": True}
    return {"error": "无效 action"}


def _mutate_knowledge_base(args: dict) -> dict:
    action = args.get("action")
    if action == "create":
        kb = KnowledgeBase(
            id=args.get("kb_id") or new_id(),
            user_id=g.user_id,
            name=args.get("name") or "新知识库",
            description=args.get("description") or "",
        )
        db.session.add(kb)
        db.session.commit()
        return {
            "kb_id": kb.id,
            "name": kb.name,
            "description": kb.description,
            "sort_order": kb.sort_order,
            "updated_at": kb.updated_at,
        }
    if action == "update":
        kb_id = args.get("kb_id")
        kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
        if kb is None:
            return {"error": "知识库不存在"}
        if "name" in args:
            kb.name = args["name"]
        if "description" in args:
            kb.description = args["description"]
        kb.updated_at = now_ms()
        db.session.commit()
        return {
            "kb_id": kb.id,
            "name": kb.name,
            "description": kb.description,
            "sort_order": kb.sort_order,
            "updated_at": kb.updated_at,
        }
    if action == "delete":
        kb_id = args.get("kb_id")
        kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
        if kb is None:
            return {"error": "知识库不存在"}
        kb.deleted_at = now_ms()
        kb.updated_at = now_ms()
        db.session.commit()
        return {"kb_id": kb.id, "deleted": True}
    return {"error": "无效 action"}
