"""回收站：过期清理。"""

from datetime import datetime, timedelta, timezone

from flask import current_app

from app.extensions import db
from app.models import KnowledgeBase, Note, NoteVersion, ShareLink, CustomPage


def retention_ms() -> int:
    cfg = current_app.config.get("MINDSHELF_CONFIG", {})
    days = int(cfg.get("trash", {}).get("retention_days", 30))
    return days * 24 * 60 * 60 * 1000


def expires_at(deleted_at: int) -> int:
    return deleted_at + retention_ms()


def purge_expired_trash() -> int:
    """永久删除超过保留期的软删除记录。返回清理条数。"""
    cutoff = int(
        (datetime.now(timezone.utc) - timedelta(milliseconds=retention_ms())).timestamp() * 1000
    )
    removed = 0

    expired_notes = Note.query.filter(Note.deleted_at.isnot(None), Note.deleted_at < cutoff).all()
    for note in expired_notes:
        NoteVersion.query.filter_by(note_id=note.id).delete()
        ShareLink.query.filter_by(resource_type="note", resource_id=note.id).delete()
        db.session.delete(note)
        removed += 1

    expired_kbs = KnowledgeBase.query.filter(
        KnowledgeBase.deleted_at.isnot(None), KnowledgeBase.deleted_at < cutoff
    ).all()
    for kb in expired_kbs:
        ShareLink.query.filter_by(resource_type="knowledge_base", resource_id=kb.id).delete()
        db.session.delete(kb)
        removed += 1

    expired_pages = CustomPage.query.filter(
        CustomPage.deleted_at.isnot(None), CustomPage.deleted_at < cutoff
    ).all()
    for page in expired_pages:
        ShareLink.query.filter_by(resource_type="page", resource_id=page.id).delete()
        db.session.delete(page)
        removed += 1

    if removed:
        db.session.commit()
    return removed
