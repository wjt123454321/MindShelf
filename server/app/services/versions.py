"""笔记历史版本：自动快照与上限控制。"""

from app.extensions import db
from app.models import Note, NoteVersion
from app.utils.auth import new_id, now_ms

MAX_VERSIONS_PER_NOTE = 10


def version_dict(version: NoteVersion) -> dict:
    return {
        "id": version.id,
        "note_id": version.note_id,
        "title": version.title,
        "content": version.content,
        "created_at": version.created_at,
    }


def snapshot_note(note: Note, user_id: str) -> NoteVersion:
    """保存当前笔记内容为历史版本，超出上限时删除最旧记录。"""
    version = NoteVersion(
        id=new_id(),
        note_id=note.id,
        user_id=user_id,
        title=note.title,
        content=note.content,
        created_at=now_ms(),
    )
    db.session.add(version)
    db.session.flush()

    versions = (
        NoteVersion.query.filter_by(note_id=note.id, user_id=user_id)
        .order_by(NoteVersion.created_at.asc())
        .all()
    )
    overflow = len(versions) - MAX_VERSIONS_PER_NOTE
    if overflow > 0:
        for old in versions[:overflow]:
            db.session.delete(old)
    return version


def prune_versions_for_note(note_id: str, user_id: str) -> None:
    versions = (
        NoteVersion.query.filter_by(note_id=note_id, user_id=user_id)
        .order_by(NoteVersion.created_at.asc())
        .all()
    )
    overflow = len(versions) - MAX_VERSIONS_PER_NOTE
    if overflow > 0:
        for old in versions[:overflow]:
            db.session.delete(old)
