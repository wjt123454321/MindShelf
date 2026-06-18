"""云同步：增量拉取、推送与冲突解决。"""

import json

from flask import Blueprint, g, request

from app.extensions import db
from app.models import Branch, Conversation, CustomPage, KnowledgeBase, Message, Note, NoteKb
from app.routes.conversations import _branch_dict, _conv_dict, _msg_dict
from app.routes.knowledge_bases import _kb_dict
from app.routes.notes import _note_dict, _sync_kb_links
from app.routes.pages import _page_dict
from app.services.versions import snapshot_note
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("sync", __name__, url_prefix="/api/v1/sync")


def _note_kb_links(user_id: str, since: int) -> list[dict]:
    return [
        {"note_id": link.note_id, "kb_id": link.kb_id}
        for link in NoteKb.query.join(Note, Note.id == NoteKb.note_id)
        .filter(Note.user_id == user_id)
        .all()
    ]


def _tombstones(user_id: str, since: int) -> list[dict]:
    items: list[dict] = []
    notes = Note.query.filter(
        Note.user_id == user_id,
        Note.deleted_at.isnot(None),
        Note.deleted_at > since,
    ).all()
    for note in notes:
        items.append({"entity": "note", "id": note.id, "deleted_at": note.deleted_at})

    kbs = KnowledgeBase.query.filter(
        KnowledgeBase.user_id == user_id,
        KnowledgeBase.deleted_at.isnot(None),
        KnowledgeBase.deleted_at > since,
    ).all()
    for kb in kbs:
        items.append({"entity": "knowledge_base", "id": kb.id, "deleted_at": kb.deleted_at})

    pages = CustomPage.query.filter(
        CustomPage.user_id == user_id,
        CustomPage.deleted_at.isnot(None),
        CustomPage.deleted_at > since,
    ).all()
    for page in pages:
        items.append({"entity": "page", "id": page.id, "deleted_at": page.deleted_at})
    return items


def _chat_tombstones(user_id: str, since: int) -> list[dict]:
    # 对话删除通过 REST DELETE 完成，同步层暂不推送会话墓碑
    return []


def _pull_conversations(user_id: str, since: int) -> list[dict]:
    q = Conversation.query.filter_by(user_id=user_id)
    if since > 0:
        q = q.filter(Conversation.updated_at > since)
    return [_conv_dict(c) for c in q.all()]


def _pull_branches(user_id: str, since: int) -> list[dict]:
    q = (
        Branch.query.join(Conversation, Conversation.id == Branch.conversation_id)
        .filter(Conversation.user_id == user_id)
    )
    if since > 0:
        q = q.filter(Branch.created_at > since)
    return [_branch_dict(b) for b in q.all()]


def _pull_messages(user_id: str, since: int) -> list[dict]:
    q = (
        Message.query.join(Conversation, Conversation.id == Message.conversation_id)
        .filter(Conversation.user_id == user_id)
    )
    if since > 0:
        q = q.filter(Message.created_at > since)
    return [_msg_dict(m) for m in q.all()]


def _pull_pages(user_id: str, since: int) -> list[dict]:
    q = CustomPage.query.filter_by(user_id=user_id)
    if since > 0:
        q = q.filter(CustomPage.updated_at > since)
    return [_page_dict(p) for p in q.all()]


@bp.get("/pull")
@auth_required
def pull():
    since = int(request.args.get("since", 0) or 0)
    server_time = now_ms()

    notes_query = Note.query.filter_by(user_id=g.user_id)
    kbs_query = KnowledgeBase.query.filter_by(user_id=g.user_id)
    if since > 0:
        notes_query = notes_query.filter(Note.updated_at > since)
        kbs_query = kbs_query.filter(KnowledgeBase.updated_at > since)

    notes = [_note_dict(n) for n in notes_query.all()]
    kbs = [_kb_dict(k) for k in kbs_query.all()]

    return ok(
        {
            "server_time": server_time,
            "notes": notes,
            "knowledge_bases": kbs,
            "note_kb_links": _note_kb_links(g.user_id, since),
            "conversations": _pull_conversations(g.user_id, since),
            "branches": _pull_branches(g.user_id, since),
            "messages": _pull_messages(g.user_id, since),
            "pages": _pull_pages(g.user_id, since),
            "share_links": [],
            "tombstones": _tombstones(g.user_id, since) + _chat_tombstones(g.user_id, since),
        }
    )


def _apply_note_push(item: dict) -> tuple[dict | None, dict | None]:
    """推送单条笔记，返回 (applied, conflict)。"""
    note_id = item.get("id")
    if not note_id:
        return None, None

    remote = Note.query.filter_by(id=note_id, user_id=g.user_id).first()
    deleted_at = item.get("deleted_at")

    if deleted_at:
        if remote is None:
            remote = Note(
                id=note_id,
                user_id=g.user_id,
                title=item.get("title") or "",
                content=item.get("content") or "",
                deleted_at=deleted_at,
                updated_at=item.get("updated_at") or now_ms(),
            )
            db.session.add(remote)
            db.session.commit()
            return {"entity": "note", "id": note_id, "sync_version": remote.sync_version}, None
        if remote.deleted_at is None:
            remote.deleted_at = deleted_at
            remote.updated_at = item.get("updated_at") or now_ms()
            db.session.commit()
        return {"entity": "note", "id": note_id, "sync_version": remote.sync_version}, None

    client_version = item.get("sync_version")
    if remote is None:
        note = Note(
            id=note_id,
            user_id=g.user_id,
            title=item.get("title") or "",
            content=item.get("content") or "",
            sync_version=1,
            updated_at=item.get("updated_at") or now_ms(),
        )
        db.session.add(note)
        db.session.flush()
        _sync_kb_links(note.id, item.get("knowledge_base_ids", []))
        db.session.commit()
        return {"entity": "note", "id": note_id, "sync_version": note.sync_version}, None

    if remote.deleted_at is not None:
        remote.deleted_at = None

    base_version = item.get("base_sync_version")
    if (
        base_version is not None
        and client_version is not None
        and remote.sync_version != base_version
        and client_version != remote.sync_version
    ):
        conflict = {
            "entity": "note",
            "id": note_id,
            "base": {
                "title": item.get("base_title", ""),
                "content": item.get("base_content", ""),
                "sync_version": base_version,
            },
            "local": {
                "title": item.get("title", ""),
                "content": item.get("content", ""),
                "sync_version": client_version,
            },
            "remote": {
                "title": remote.title,
                "content": remote.content,
                "sync_version": remote.sync_version,
            },
        }
        return None, conflict

    title = item.get("title")
    content = item.get("content")
    changed = False
    if title is not None and title != remote.title:
        snapshot_note(remote, g.user_id)
        remote.title = title
        changed = True
    if content is not None and content != remote.content:
        if not changed:
            snapshot_note(remote, g.user_id)
        remote.content = content
        changed = True
    if changed:
        remote.sync_version += 1
    remote.updated_at = item.get("updated_at") or now_ms()
    if "knowledge_base_ids" in item:
        _sync_kb_links(remote.id, item["knowledge_base_ids"])
    db.session.commit()
    return {"entity": "note", "id": note_id, "sync_version": remote.sync_version}, None


def _apply_kb_push(item: dict) -> tuple[dict | None, dict | None]:
    kb_id = item.get("id")
    if not kb_id:
        return None, None

    remote = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id).first()
    deleted_at = item.get("deleted_at")

    if deleted_at:
        if remote is None:
            remote = KnowledgeBase(
                id=kb_id,
                user_id=g.user_id,
                name=item.get("name") or "",
                description=item.get("description") or "",
                deleted_at=deleted_at,
                updated_at=item.get("updated_at") or now_ms(),
            )
            db.session.add(remote)
            db.session.commit()
            return {"entity": "knowledge_base", "id": kb_id}, None
        if remote.deleted_at is None:
            remote.deleted_at = deleted_at
            remote.updated_at = item.get("updated_at") or now_ms()
            db.session.commit()
        return {"entity": "knowledge_base", "id": kb_id}, None

    if remote is None:
        kb = KnowledgeBase(
            id=kb_id,
            user_id=g.user_id,
            name=item.get("name") or "新知识库",
            description=item.get("description") or "",
            sort_order=int(item.get("sort_order") or 0),
            updated_at=item.get("updated_at") or now_ms(),
        )
        db.session.add(kb)
        db.session.commit()
        return {"entity": "knowledge_base", "id": kb_id}, None

    if remote.deleted_at is not None:
        remote.deleted_at = None

    client_updated = item.get("updated_at") or 0
    if client_updated < remote.updated_at and item.get("force") is not True:
        conflict = {
            "entity": "knowledge_base",
            "id": kb_id,
            "base": item.get("base") or {},
            "local": {
                "name": item.get("name"),
                "description": item.get("description"),
                "sort_order": item.get("sort_order"),
                "updated_at": client_updated,
            },
            "remote": {
                "name": remote.name,
                "description": remote.description,
                "sort_order": remote.sort_order,
                "updated_at": remote.updated_at,
            },
        }
        return None, conflict

    if "name" in item:
        remote.name = item["name"]
    if "description" in item:
        remote.description = item["description"]
    if "sort_order" in item:
        remote.sort_order = int(item["sort_order"])
    remote.updated_at = item.get("updated_at") or now_ms()
    db.session.commit()
    return {"entity": "knowledge_base", "id": kb_id}, None


def _apply_conv_push(item: dict) -> tuple[dict | None, dict | None]:
    conv_id = item.get("id")
    if not conv_id:
        return None, None
    remote = Conversation.query.filter_by(id=conv_id, user_id=g.user_id).first()
    if remote is None:
        remote = Conversation(
            id=conv_id,
            user_id=g.user_id,
            title=item.get("title") or "新对话",
            created_at=item.get("created_at") or now_ms(),
            updated_at=item.get("updated_at") or now_ms(),
        )
        db.session.add(remote)
    else:
        if "title" in item:
            remote.title = item["title"]
        remote.updated_at = item.get("updated_at") or now_ms()
    db.session.commit()
    return {"entity": "conversation", "id": conv_id}, None


def _apply_branch_push(item: dict) -> tuple[dict | None, dict | None]:
    branch_id = item.get("id")
    conv_id = item.get("conversation_id")
    if not branch_id or not conv_id:
        return None, None
    conv = Conversation.query.filter_by(id=conv_id, user_id=g.user_id).first()
    if conv is None:
        return None, None
    remote = Branch.query.filter_by(id=branch_id, conversation_id=conv_id).first()
    if remote is None:
        remote = Branch(
            id=branch_id,
            conversation_id=conv_id,
            label=item.get("label") or "新分支",
            root_message_id=item.get("root_message_id"),
            created_at=item.get("created_at") or now_ms(),
        )
        db.session.add(remote)
    else:
        if "label" in item:
            remote.label = item["label"]
        if "root_message_id" in item:
            remote.root_message_id = item.get("root_message_id")
    db.session.commit()
    return {"entity": "branch", "id": branch_id}, None


def _apply_message_push(item: dict) -> tuple[dict | None, dict | None]:
    msg_id = item.get("id")
    conv_id = item.get("conversation_id")
    branch_id = item.get("branch_id")
    if not msg_id or not conv_id or not branch_id:
        return None, None
    conv = Conversation.query.filter_by(id=conv_id, user_id=g.user_id).first()
    if conv is None:
        return None, None
    branch = Branch.query.filter_by(id=branch_id, conversation_id=conv_id).first()
    if branch is None:
        return None, None
    remote = Message.query.filter_by(id=msg_id, conversation_id=conv_id).first()
    segments = item.get("segments") or []
    if remote is None:
        remote = Message(
            id=msg_id,
            conversation_id=conv_id,
            branch_id=branch_id,
            parent_id=item.get("parent_id"),
            role=item.get("role") or "user",
            content=item.get("content") or "",
            reasoning=item.get("reasoning") or "",
            segments_json=json.dumps(segments, ensure_ascii=False),
            created_at=item.get("created_at") or now_ms(),
        )
        db.session.add(remote)
    else:
        remote.content = item.get("content") or remote.content
        remote.reasoning = item.get("reasoning") or remote.reasoning
        if segments:
            remote.segments_json = json.dumps(segments, ensure_ascii=False)
    conv.updated_at = now_ms()
    db.session.commit()
    return {"entity": "message", "id": msg_id}, None


def _clear_page_pins(user_id: str, keep_id: str) -> None:
    CustomPage.query.filter(
        CustomPage.user_id == user_id,
        CustomPage.id != keep_id,
        CustomPage.pinned.is_(True),
        CustomPage.deleted_at.is_(None),
    ).update({"pinned": False}, synchronize_session=False)


def _apply_page_push(item: dict) -> tuple[dict | None, dict | None]:
    page_id = item.get("id")
    if not page_id:
        return None, None

    remote = CustomPage.query.filter_by(id=page_id, user_id=g.user_id).first()
    deleted_at = item.get("deleted_at")

    if deleted_at:
        if remote is None:
            remote = CustomPage(
                id=page_id,
                user_id=g.user_id,
                name=item.get("name") or "新页面",
                schema_json=json.dumps(item.get("schema_json") or {}, ensure_ascii=False),
                data_bindings=json.dumps(item.get("data_bindings") or {}, ensure_ascii=False),
                pinned=False,
                deleted_at=deleted_at,
                created_at=item.get("created_at") or now_ms(),
                updated_at=item.get("updated_at") or now_ms(),
            )
            db.session.add(remote)
            db.session.commit()
            return {"entity": "page", "id": page_id}, None
        if remote.deleted_at is None:
            remote.deleted_at = deleted_at
            remote.pinned = False
            remote.updated_at = item.get("updated_at") or now_ms()
            db.session.commit()
        return {"entity": "page", "id": page_id}, None

    client_updated = item.get("updated_at") or 0
    if remote is None:
        page = CustomPage(
            id=page_id,
            user_id=g.user_id,
            name=item.get("name") or "新页面",
            schema_json=json.dumps(item.get("schema_json") or {}, ensure_ascii=False),
            data_bindings=json.dumps(item.get("data_bindings") or {}, ensure_ascii=False),
            pinned=bool(item.get("pinned", False)),
            created_at=item.get("created_at") or now_ms(),
            updated_at=client_updated or now_ms(),
        )
        db.session.add(page)
        if page.pinned:
            db.session.flush()
            _clear_page_pins(g.user_id, page_id)
        db.session.commit()
        return {"entity": "page", "id": page_id}, None

    if remote.deleted_at is not None:
        remote.deleted_at = None

    if client_updated < remote.updated_at and item.get("force") is not True:
        conflict = {
            "entity": "page",
            "id": page_id,
            "base": item.get("base") or {},
            "local": {
                "name": item.get("name"),
                "schema_json": item.get("schema_json"),
                "data_bindings": item.get("data_bindings"),
                "pinned": item.get("pinned"),
                "updated_at": client_updated,
            },
            "remote": {
                "name": remote.name,
                "schema_json": json.loads(remote.schema_json or "{}"),
                "data_bindings": json.loads(remote.data_bindings or "{}"),
                "pinned": remote.pinned,
                "updated_at": remote.updated_at,
            },
        }
        return None, conflict

    if "name" in item:
        remote.name = item["name"]
    if "schema_json" in item:
        remote.schema_json = json.dumps(item["schema_json"], ensure_ascii=False)
    if "data_bindings" in item:
        remote.data_bindings = json.dumps(item["data_bindings"], ensure_ascii=False)
    if "pinned" in item:
        remote.pinned = bool(item["pinned"])
        if remote.pinned:
            _clear_page_pins(g.user_id, page_id)
    remote.updated_at = item.get("updated_at") or now_ms()
    db.session.commit()
    return {"entity": "page", "id": page_id}, None


@bp.post("/push")
@auth_required
def push():
    body = request.get_json(silent=True) or {}
    applied: list[dict] = []
    conflicts: list[dict] = []

    for item in body.get("notes") or []:
        a, c = _apply_note_push(item)
        if a:
            applied.append(a)
        if c:
            conflicts.append(c)

    for item in body.get("knowledge_bases") or []:
        a, c = _apply_kb_push(item)
        if a:
            applied.append(a)
        if c:
            conflicts.append(c)

    for item in body.get("conversations") or []:
        a, c = _apply_conv_push(item)
        if a:
            applied.append(a)
        if c:
            conflicts.append(c)

    for item in body.get("branches") or []:
        a, c = _apply_branch_push(item)
        if a:
            applied.append(a)
        if c:
            conflicts.append(c)

    for item in body.get("messages") or []:
        a, c = _apply_message_push(item)
        if a:
            applied.append(a)
        if c:
            conflicts.append(c)

    for item in body.get("pages") or []:
        a, c = _apply_page_push(item)
        if a:
            applied.append(a)
        if c:
            conflicts.append(c)

    for tomb in body.get("deletes") or []:
        entity = tomb.get("entity")
        entity_id = tomb.get("id")
        deleted_at = tomb.get("deleted_at") or now_ms()
        if entity == "note" and entity_id:
            note = Note.query.filter_by(id=entity_id, user_id=g.user_id).first()
            if note and note.deleted_at is None:
                note.deleted_at = deleted_at
                note.updated_at = deleted_at
        elif entity == "knowledge_base" and entity_id:
            kb = KnowledgeBase.query.filter_by(id=entity_id, user_id=g.user_id).first()
            if kb and kb.deleted_at is None:
                kb.deleted_at = deleted_at
                kb.updated_at = deleted_at
        elif entity == "page" and entity_id:
            page = CustomPage.query.filter_by(id=entity_id, user_id=g.user_id).first()
            if page and page.deleted_at is None:
                page.deleted_at = deleted_at
                page.pinned = False
                page.updated_at = deleted_at

    db.session.commit()
    return ok({"server_time": now_ms(), "applied": applied, "conflicts": conflicts})


@bp.post("/resolve")
@auth_required
def resolve():
    body = request.get_json(silent=True) or {}
    entity = body.get("entity")
    entity_id = body.get("id")
    resolution = body.get("resolution")
    if not entity or not entity_id or resolution not in ("local", "remote", "merged"):
        return err("INVALID_REQUEST", "参数无效", 400)

    if entity == "note":
        note = Note.query.filter_by(id=entity_id, user_id=g.user_id, deleted_at=None).first()
        if note is None:
            return err("NOTE_NOT_FOUND", "笔记不存在", 404)

        if resolution == "remote":
            return ok(_note_dict(note))

        merged = body.get("merged") or {}
        if resolution == "local":
            merged = {"title": body.get("local", {}).get("title", note.title),
                      "content": body.get("local", {}).get("content", note.content)}
        snapshot_note(note, g.user_id)
        if "title" in merged:
            note.title = merged["title"]
        if "content" in merged:
            note.content = merged["content"]
        note.sync_version += 1
        note.updated_at = now_ms()
        if "knowledge_base_ids" in merged:
            _sync_kb_links(note.id, merged["knowledge_base_ids"])
        db.session.commit()
        return ok(_note_dict(note))

    if entity == "knowledge_base":
        kb = KnowledgeBase.query.filter_by(id=entity_id, user_id=g.user_id, deleted_at=None).first()
        if kb is None:
            return err("KB_NOT_FOUND", "知识库不存在", 404)
        if resolution == "remote":
            return ok(_kb_dict(kb))
        merged = body.get("merged") or body.get("local") or {}
        if "name" in merged:
            kb.name = merged["name"]
        if "description" in merged:
            kb.description = merged["description"]
        if "sort_order" in merged:
            kb.sort_order = int(merged["sort_order"])
        kb.updated_at = now_ms()
        db.session.commit()
        return ok(_kb_dict(kb))

    if entity == "page":
        page = CustomPage.query.filter_by(id=entity_id, user_id=g.user_id, deleted_at=None).first()
        if page is None:
            return err("PAGE_NOT_FOUND", "页面不存在", 404)
        if resolution == "remote":
            return ok(_page_dict(page))
        merged = body.get("merged") or body.get("local") or {}
        if "name" in merged:
            page.name = merged["name"]
        if "schema_json" in merged:
            page.schema_json = json.dumps(merged["schema_json"], ensure_ascii=False)
        if "data_bindings" in merged:
            page.data_bindings = json.dumps(merged["data_bindings"], ensure_ascii=False)
        if "pinned" in merged:
            page.pinned = bool(merged["pinned"])
            if page.pinned:
                _clear_page_pins(g.user_id, entity_id)
        page.updated_at = now_ms()
        db.session.commit()
        return ok(_page_dict(page))

    return err("INVALID_ENTITY", "不支持的实体类型", 400)
