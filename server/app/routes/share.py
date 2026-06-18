"""公开分享链接。"""

import secrets
from urllib.parse import urlparse

from flask import Blueprint, current_app, g, request

from app.extensions import db
from app.models import CustomPage, KnowledgeBase, Note, NoteKb, ShareLink
from app.routes.notes import _note_dict
from app.routes.pages import _page_dict
from app.services.share_page import render_share_not_found, render_share_page
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("share", __name__, url_prefix="/api/v1/share")
public_bp = Blueprint("public_share", __name__)

_LOCALHOST_HOSTS = frozenset({"localhost", "127.0.0.1", "::1"})


def _is_localhost_url(url: str) -> bool:
    if not url:
        return True
    host = urlparse(url).hostname or ""
    return host.lower() in _LOCALHOST_HOSTS


def _share_base_url() -> str:
    """分享链接前缀：优先配置的非 localhost 地址，否则用当前请求的 Host。"""
    cfg = current_app.config.get("MINDSHELF_CONFIG", {})
    configured = cfg.get("share", {}).get("base_url", "").rstrip("/")
    request_base = request.host_url.rstrip("/")

    if configured and not _is_localhost_url(configured):
        return configured
    if not _is_localhost_url(request_base):
        return request_base
    return configured or request_base


def _link_dict(link: ShareLink) -> dict:
    base = _share_base_url()
    return {
        "id": link.id,
        "token": link.token,
        "url": f"{base}/s/{link.token}",
        "resource_type": link.resource_type,
        "resource_id": link.resource_id,
        "revoked": link.revoked,
        "created_at": link.created_at,
    }


def _wants_json() -> bool:
    accept = request.headers.get("Accept", "")
    return "application/json" in accept and "text/html" not in accept.split(",")[0]


def _load_share_payload(token: str) -> dict | None:
    link = ShareLink.query.filter_by(token=token, revoked=False).first()
    if link is None:
        return None

    if link.resource_type == "note":
        note = Note.query.filter_by(
            id=link.resource_id, user_id=link.user_id, deleted_at=None
        ).first()
        if note is None:
            return None
        return {
            "resource_type": "note",
            "snapshot": {"title": note.title, "content": note.content},
            "shared_at": link.created_at,
        }

    if link.resource_type == "knowledge_base":
        kb = KnowledgeBase.query.filter_by(
            id=link.resource_id, user_id=link.user_id, deleted_at=None
        ).first()
        if kb is None:
            return None
        notes = (
            Note.query.join(NoteKb, Note.id == NoteKb.note_id)
            .filter(NoteKb.kb_id == kb.id, Note.deleted_at.is_(None))
            .order_by(Note.updated_at.desc())
            .all()
        )
        return {
            "resource_type": "knowledge_base",
            "snapshot": {
                "name": kb.name,
                "description": kb.description,
                "notes": [_note_dict(n) for n in notes],
            },
            "shared_at": link.created_at,
        }

    if link.resource_type == "page":
        page = CustomPage.query.filter_by(
            id=link.resource_id, user_id=link.user_id, deleted_at=None
        ).first()
        if page is None:
            return None
        snapshot = _page_dict(page)
        bindings = snapshot.get("data_bindings") or {}
        embedded_notes: dict[str, dict] = {}
        for binding in bindings.values():
            if isinstance(binding, dict) and binding.get("kind") == "note":
                note_id = binding.get("note_id")
                if note_id:
                    note = Note.query.filter_by(
                        id=note_id, user_id=link.user_id, deleted_at=None
                    ).first()
                    if note:
                        embedded_notes[note_id] = {
                            "title": note.title,
                            "content": note.content,
                        }
        snapshot["embedded_notes"] = embedded_notes
        return {
            "resource_type": "page",
            "snapshot": snapshot,
            "shared_at": link.created_at,
        }

    return None


def _get_owned_resource(user_id: str, resource_type: str, resource_id: str):
    if resource_type == "note":
        return Note.query.filter_by(id=resource_id, user_id=user_id, deleted_at=None).first()
    if resource_type == "knowledge_base":
        return KnowledgeBase.query.filter_by(
            id=resource_id, user_id=user_id, deleted_at=None
        ).first()
    if resource_type == "page":
        return CustomPage.query.filter_by(
            id=resource_id, user_id=user_id, deleted_at=None
        ).first()
    return None


@bp.post("/links")
@auth_required
def create_link():
    body = request.get_json(silent=True) or {}
    resource_type = body.get("resource_type")
    resource_id = body.get("resource_id")
    if resource_type not in ("note", "knowledge_base", "page"):
        return err("INVALID_TYPE", "仅支持 note、knowledge_base 或 page", 400)
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


@public_bp.get("/s/<token>")
def public_view(token: str):
    payload = _load_share_payload(token)
    if payload is None:
        if _wants_json():
            return err("NOT_FOUND", "链接不存在或已撤销", 404)
        return render_share_not_found(), 404, {"Content-Type": "text/html; charset=utf-8"}

    if _wants_json():
        return ok(payload)
    return render_share_page(payload), 200, {"Content-Type": "text/html; charset=utf-8"}
