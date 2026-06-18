"""知识库路由。"""

from flask import Blueprint, g, request

from app.extensions import db
from app.models import KnowledgeBase, Note, NoteKb
from app.routes.notes import _note_dict
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("knowledge_bases", __name__, url_prefix="/api/v1/knowledge-bases")


def _kb_dict(kb: KnowledgeBase) -> dict:
    note_count = (
        NoteKb.query.join(Note, Note.id == NoteKb.note_id)
        .filter(NoteKb.kb_id == kb.id, Note.deleted_at.is_(None))
        .count()
    )
    return {
        "id": kb.id,
        "name": kb.name,
        "description": kb.description,
        "sort_order": kb.sort_order,
        "note_count": note_count,
        "created_at": kb.updated_at,
        "updated_at": kb.updated_at,
        "deleted_at": kb.deleted_at,
    }


@bp.get("")
@auth_required
def list_kbs():
    page = max(int(request.args.get("page", 1)), 1)
    page_size = min(max(int(request.args.get("page_size", 20)), 1), 100)
    query = KnowledgeBase.query.filter_by(user_id=g.user_id, deleted_at=None)
    total = query.count()
    items = (
        query.order_by(KnowledgeBase.sort_order, KnowledgeBase.updated_at.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
        .all()
    )
    return ok([_kb_dict(k) for k in items], meta={"page": page, "page_size": page_size, "total": total})


@bp.post("")
@auth_required
def create_kb():
    body = request.get_json(silent=True) or {}
    kb = KnowledgeBase(
        id=body.get("id") or new_id(),
        user_id=g.user_id,
        name=body.get("name") or "新知识库",
        description=body.get("description") or "",
        sort_order=int(body.get("sort_order") or 0),
    )
    db.session.add(kb)
    db.session.commit()
    return ok(_kb_dict(kb), 201)


@bp.get("/<kb_id>")
@auth_required
def get_kb(kb_id: str):
    kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
    if kb is None:
        return err("KB_NOT_FOUND", "知识库不存在", 404)
    return ok(_kb_dict(kb))


@bp.patch("/<kb_id>")
@auth_required
def update_kb(kb_id: str):
    kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
    if kb is None:
        return err("KB_NOT_FOUND", "知识库不存在", 404)
    body = request.get_json(silent=True) or {}
    if "name" in body:
        kb.name = body["name"]
    if "description" in body:
        kb.description = body["description"]
    if "sort_order" in body:
        kb.sort_order = int(body["sort_order"])
    kb.updated_at = now_ms()
    db.session.commit()
    return ok(_kb_dict(kb))


@bp.delete("/<kb_id>")
@auth_required
def delete_kb(kb_id: str):
    kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
    if kb is None:
        return err("KB_NOT_FOUND", "知识库不存在", 404)
    kb.deleted_at = now_ms()
    kb.updated_at = now_ms()
    db.session.commit()
    return "", 204


@bp.get("/<kb_id>/notes")
@auth_required
def kb_notes(kb_id: str):
    kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
    if kb is None:
        return err("KB_NOT_FOUND", "知识库不存在", 404)
    q = (request.args.get("q") or "").strip()
    query = (
        Note.query.join(NoteKb, Note.id == NoteKb.note_id)
        .filter(NoteKb.kb_id == kb_id, Note.user_id == g.user_id, Note.deleted_at.is_(None))
    )
    if q:
        like = f"%{q}%"
        query = query.filter(db.or_(Note.title.ilike(like), Note.content.ilike(like)))
    notes = query.order_by(Note.updated_at.desc()).all()
    return ok([_note_dict(n) for n in notes])


@bp.put("/<kb_id>/notes/<note_id>")
@auth_required
def link_note(kb_id: str, note_id: str):
    kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
    note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
    if kb is None:
        return err("KB_NOT_FOUND", "知识库不存在", 404)
    if note is None:
        return err("NOTE_NOT_FOUND", "笔记不存在或无权访问", 404)
    if not NoteKb.query.filter_by(note_id=note_id, kb_id=kb_id).first():
        db.session.add(NoteKb(note_id=note_id, kb_id=kb_id))
        db.session.commit()
    return "", 204


@bp.delete("/<kb_id>/notes/<note_id>")
@auth_required
def unlink_note(kb_id: str, note_id: str):
    NoteKb.query.filter_by(note_id=note_id, kb_id=kb_id).delete()
    db.session.commit()
    return "", 204
