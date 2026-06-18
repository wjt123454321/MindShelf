"""回收站路由。"""

from flask import Blueprint, g, request

from app.extensions import db
from app.models import KnowledgeBase, Note, NoteKb
from app.routes.knowledge_bases import _kb_dict
from app.routes.notes import _note_dict
from app.services.trash import expires_at, purge_expired_trash
from app.utils.auth import auth_required, now_ms
from app.utils.responses import err, ok

bp = Blueprint("trash", __name__, url_prefix="/api/v1/trash")

TRASH_RETENTION_DAYS = 30


@bp.get("")
@auth_required
def list_trash():
    purge_expired_trash()
    items: list[dict] = []

    notes = (
        Note.query.filter_by(user_id=g.user_id)
        .filter(Note.deleted_at.isnot(None))
        .order_by(Note.deleted_at.desc())
        .all()
    )
    for note in notes:
        items.append(
            {
                "entity_type": "note",
                "entity": _note_dict(note),
                "deleted_at": note.deleted_at,
                "expires_at": expires_at(note.deleted_at),
            }
        )

    kbs = (
        KnowledgeBase.query.filter_by(user_id=g.user_id)
        .filter(KnowledgeBase.deleted_at.isnot(None))
        .order_by(KnowledgeBase.deleted_at.desc())
        .all()
    )
    for kb in kbs:
        items.append(
            {
                "entity_type": "knowledge_base",
                "entity": _kb_dict(kb),
                "deleted_at": kb.deleted_at,
                "expires_at": expires_at(kb.deleted_at),
            }
        )

    items.sort(key=lambda x: x["deleted_at"], reverse=True)
    return ok(items)


@bp.post("/restore")
@auth_required
def restore():
    body = request.get_json(silent=True) or {}
    entity_type = body.get("entity_type")
    entity_id = body.get("id")
    if not entity_type or not entity_id:
        return err("INVALID_REQUEST", "缺少 entity_type 或 id", 400)

    now = now_ms()
    if entity_type == "note":
        note = Note.query.filter_by(id=entity_id, user_id=g.user_id).first()
        if note is None or note.deleted_at is None:
            return err("NOT_FOUND", "回收站中不存在该笔记", 404)
        note.deleted_at = None
        note.updated_at = now
        db.session.commit()
        return ok(_note_dict(note))

    if entity_type == "knowledge_base":
        kb = KnowledgeBase.query.filter_by(id=entity_id, user_id=g.user_id).first()
        if kb is None or kb.deleted_at is None:
            return err("NOT_FOUND", "回收站中不存在该知识库", 404)
        kb.deleted_at = None
        kb.updated_at = now
        db.session.commit()
        return ok(_kb_dict(kb))

    return err("INVALID_ENTITY", "不支持的实体类型", 400)


@bp.delete("/<entity_type>/<entity_id>")
@auth_required
def purge(entity_type: str, entity_id: str):
    if entity_type == "note":
        note = Note.query.filter_by(id=entity_id, user_id=g.user_id).first()
        if note is None or note.deleted_at is None:
            return err("NOT_FOUND", "回收站中不存在该笔记", 404)
        from app.models import NoteVersion, ShareLink

        NoteKb.query.filter_by(note_id=entity_id).delete()
        NoteVersion.query.filter_by(note_id=entity_id).delete()
        ShareLink.query.filter_by(resource_type="note", resource_id=entity_id).delete()
        db.session.delete(note)
        db.session.commit()
        return "", 204

    if entity_type == "knowledge_base":
        kb = KnowledgeBase.query.filter_by(id=entity_id, user_id=g.user_id).first()
        if kb is None or kb.deleted_at is None:
            return err("NOT_FOUND", "回收站中不存在该知识库", 404)
        from app.models import ShareLink

        ShareLink.query.filter_by(resource_type="knowledge_base", resource_id=entity_id).delete()
        db.session.delete(kb)
        db.session.commit()
        return "", 204

    return err("INVALID_ENTITY", "不支持的实体类型", 400)
