"""公开分享链接。"""

import secrets

from flask import Blueprint, current_app, g, request

from app.extensions import db
from app.models import KnowledgeBase, Note, NoteKb, ShareLink
from app.routes.notes import _note_dict
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("share", __name__, url_prefix="/api/v1/share")
public_bp = Blueprint("public_share", __name__)


def _share_base_url() -> str:
    cfg = current_app.config.get("MINDSHELF_CONFIG", {})
    base = cfg.get("share", {}).get("base_url", "").rstrip("/")
    if base:
        return base
    return request.host_url.rstrip("/")


def _link_dict(link: ShareLink) -> dict:
    base = _share_base_url()
    return {
        "id": link.id,
        "token": link.token,
        "url": f"{base}/api/v1/s/{link.token}",
        "resource_type": link.resource_type,
        "resource_id": link.resource_id,
        "revoked": link.revoked,
        "created_at": link.created_at,
    }


def _get_owned_resource(user_id: str, resource_type: str, resource_id: str):
    if resource_type == "note":
        return Note.query.filter_by(id=resource_id, user_id=user_id, deleted_at=None).first()
    if resource_type == "knowledge_base":
        return KnowledgeBase.query.filter_by(
            id=resource_id, user_id=user_id, deleted_at=None
        ).first()
    return None


@bp.post("/links")
@auth_required
def create_link():
    body = request.get_json(silent=True) or {}
    resource_type = body.get("resource_type")
    resource_id = body.get("resource_id")
    if resource_type not in ("note", "knowledge_base"):
        return err("INVALID_TYPE", "仅支持 note 或 knowledge_base", 400)
    if not resource_id:
        return err("INVALID_REQUEST", "缺少 resource_id", 400)

    resource = _get_owned_resource(g.user_id, resource_type, resource_id)
    if resource is None:
        return err("NOT_FOUND", "资源不存在或无权访问", 404)

    existing = ShareLink.query.filter_by(
        user_id=g.user_id,
        resource_type=resource_type,
        resource_id=resource_id,
        revoked=False,
    ).first()
    if existing:
        return ok(_link_dict(existing), 201)

    link = ShareLink(
        id=new_id(),
        user_id=g.user_id,
        resource_type=resource_type,
        resource_id=resource_id,
        token=secrets.token_urlsafe(24),
        revoked=False,
        created_at=now_ms(),
    )
    db.session.add(link)
    db.session.commit()
    return ok(_link_dict(link), 201)


@bp.get("/links")
@auth_required
def list_links():
    links = (
        ShareLink.query.filter_by(user_id=g.user_id, revoked=False)
        .order_by(ShareLink.created_at.desc())
        .all()
    )
    return ok([_link_dict(link) for link in links])


@bp.delete("/links/<link_id>")
@auth_required
def revoke_link(link_id: str):
    link = ShareLink.query.filter_by(id=link_id, user_id=g.user_id).first()
    if link is None:
        return err("NOT_FOUND", "分享链接不存在", 404)
    link.revoked = True
    db.session.commit()
    return "", 204


@public_bp.get("/api/v1/s/<token>")
def public_view(token: str):
    link = ShareLink.query.filter_by(token=token, revoked=False).first()
    if link is None:
        return err("NOT_FOUND", "链接不存在或已撤销", 404)

    if link.resource_type == "note":
        note = Note.query.filter_by(
            id=link.resource_id, user_id=link.user_id, deleted_at=None
        ).first()
        if note is None:
            return err("NOT_FOUND", "内容不存在", 404)
        return ok(
            {
                "resource_type": "note",
                "snapshot": {"title": note.title, "content": note.content},
                "shared_at": link.created_at,
            }
        )

    if link.resource_type == "knowledge_base":
        kb = KnowledgeBase.query.filter_by(
            id=link.resource_id, user_id=link.user_id, deleted_at=None
        ).first()
        if kb is None:
            return err("NOT_FOUND", "内容不存在", 404)
        notes = (
            Note.query.join(NoteKb, Note.id == NoteKb.note_id)
            .filter(NoteKb.kb_id == kb.id, Note.deleted_at.is_(None))
            .order_by(Note.updated_at.desc())
            .all()
        )
        return ok(
            {
                "resource_type": "knowledge_base",
                "snapshot": {
                    "name": kb.name,
                    "description": kb.description,
                    "notes": [_note_dict(n) for n in notes],
                },
                "shared_at": link.created_at,
            }
        )

    return err("NOT_FOUND", "链接不存在或已撤销", 404)
