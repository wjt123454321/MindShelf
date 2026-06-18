"""笔记路由。"""

from flask import Blueprint, g, request

from app.extensions import db
from app.models import KnowledgeBase, Note, NoteKb, NoteVersion
from app.services.versions import snapshot_note, version_dict
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("notes", __name__, url_prefix="/api/v1/notes")


def _note_dict(note: Note) -> dict:
    kb_ids = [link.kb_id for link in NoteKb.query.filter_by(note_id=note.id)]
    return {
        "id": note.id,
        "title": note.title,
        "content": note.content,
        "knowledge_base_ids": kb_ids,
        "sync_version": note.sync_version,
        "created_at": note.updated_at,
        "updated_at": note.updated_at,
        "deleted_at": note.deleted_at,
    }


def _sync_kb_links(note_id: str, kb_ids: list[str] | None) -> None:
    if kb_ids is None:
        return
    NoteKb.query.filter_by(note_id=note_id).delete()
    for kb_id in kb_ids:
        kb = KnowledgeBase.query.filter_by(id=kb_id, user_id=g.user_id, deleted_at=None).first()
        if kb:
            db.session.add(NoteKb(note_id=note_id, kb_id=kb_id))


@bp.get("")
@auth_required
def list_notes():
    q = (request.args.get("q") or "").strip()
    kb_id = request.args.get("kb_id")
    page = max(int(request.args.get("page", 1)), 1)
    page_size = min(max(int(request.args.get("page_size", 20)), 1), 100)

    query = Note.query.filter_by(user_id=g.user_id, deleted_at=None)
    if kb_id:
        query = query.join(NoteKb, Note.id == NoteKb.note_id).filter(NoteKb.kb_id == kb_id)
    if q:
        like = f"%{q}%"
        query = query.filter(db.or_(Note.title.ilike(like), Note.content.ilike(like)))

    total = query.count()
    notes = (
        query.order_by(Note.updated_at.desc())
        .offset((page - 1) * page_size)
        .limit(page_size)
        .all()
    )
    return ok([_note_dict(n) for n in notes], meta={"page": page, "page_size": page_size, "total": total})


@bp.post("")
@auth_required
def create_note():
    body = request.get_json(silent=True) or {}
    note = Note(
        id=body.get("id") or new_id(),
        user_id=g.user_id,
        title=body.get("title") or "",
        content=body.get("content") or "",
    )
    db.session.add(note)
    db.session.flush()
    _sync_kb_links(note.id, body.get("knowledge_base_ids", []))
    db.session.commit()
    return ok(_note_dict(note), 201)


@bp.get("/<note_id>")
@auth_required
def get_note(note_id: str):
    note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
    if note is None:
        return err("NOTE_NOT_FOUND", "笔记不存在或无权访问", 404)
    return ok(_note_dict(note))


@bp.patch("/<note_id>")
@auth_required
def update_note(note_id: str):
    note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
    if note is None:
        return err("NOTE_NOT_FOUND", "笔记不存在或无权访问", 404)

    body = request.get_json(silent=True) or {}
    if "sync_version" in body and body["sync_version"] != note.sync_version:
        return err("CONFLICT", "sync_version 冲突", 409)

    if "title" in body:
        note.title = body["title"]
    if "content" in body:
        note.content = body["content"]
    if "title" in body or "content" in body:
        snapshot_note(note, g.user_id)
    note.sync_version += 1
    note.updated_at = now_ms()
    _sync_kb_links(note.id, body.get("knowledge_base_ids"))
    db.session.commit()
    return ok(_note_dict(note))


@bp.delete("/<note_id>")
@auth_required
def delete_note(note_id: str):
    note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
    if note is None:
        return err("NOTE_NOT_FOUND", "笔记不存在或无权访问", 404)
    note.deleted_at = now_ms()
    note.updated_at = now_ms()
    db.session.commit()
    return "", 204


@bp.get("/<note_id>/versions")
@auth_required
def list_versions(note_id: str):
    note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
    if note is None:
        return err("NOTE_NOT_FOUND", "笔记不存在或无权访问", 404)
    versions = (
        NoteVersion.query.filter_by(note_id=note_id, user_id=g.user_id)
        .order_by(NoteVersion.created_at.desc())
        .all()
    )
    return ok([version_dict(v) for v in versions])


@bp.get("/<note_id>/versions/<version_id>")
@auth_required
def get_version(note_id: str, version_id: str):
    version = NoteVersion.query.filter_by(
        id=version_id, note_id=note_id, user_id=g.user_id
    ).first()
    if version is None:
        return err("VERSION_NOT_FOUND", "版本不存在", 404)
    return ok(version_dict(version))


@bp.post("/<note_id>/versions/<version_id>/restore")
@auth_required
def restore_version(note_id: str, version_id: str):
    note = Note.query.filter_by(id=note_id, user_id=g.user_id, deleted_at=None).first()
    if note is None:
        return err("NOTE_NOT_FOUND", "笔记不存在或无权访问", 404)
    version = NoteVersion.query.filter_by(
        id=version_id, note_id=note_id, user_id=g.user_id
    ).first()
    if version is None:
        return err("VERSION_NOT_FOUND", "版本不存在", 404)
    snapshot_note(note, g.user_id)
    note.title = version.title
    note.content = version.content
    note.sync_version += 1
    note.updated_at = now_ms()
    db.session.commit()
    return ok(_note_dict(note))
