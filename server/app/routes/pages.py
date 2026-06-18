"""自定义页面路由。"""

import json

from flask import Blueprint, g, request

from app.extensions import db
from app.models import CustomPage
from app.services.page_schema import (
    bindings_to_storage,
    schema_to_storage,
    validate_data_bindings,
    validate_schema_json,
)
from app.services.page_normalizer import normalize_page
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("pages", __name__, url_prefix="/api/v1/pages")

DEFAULT_SCHEMA = {
    "version": 1,
    "root": {"type": "Column", "children": []},
}


def _parse_stored_json(text: str) -> dict:
    if not text:
        return {}
    try:
        parsed = json.loads(text)
        return parsed if isinstance(parsed, dict) else {}
    except json.JSONDecodeError:
        return {}


def _page_dict(page: CustomPage) -> dict:
    return {
        "id": page.id,
        "name": page.name,
        "schema_json": _parse_stored_json(page.schema_json),
        "data_bindings": _parse_stored_json(page.data_bindings),
        "pinned": page.pinned,
        "created_at": page.created_at,
        "updated_at": page.updated_at,
        "deleted_at": page.deleted_at,
    }


def _clear_other_pins(user_id: str, keep_id: str) -> None:
    CustomPage.query.filter(
        CustomPage.user_id == user_id,
        CustomPage.id != keep_id,
        CustomPage.pinned.is_(True),
        CustomPage.deleted_at.is_(None),
    ).update({"pinned": False}, synchronize_session=False)


@bp.get("")
@auth_required
def list_pages():
    pages = (
        CustomPage.query.filter_by(user_id=g.user_id, deleted_at=None)
        .order_by(CustomPage.updated_at.desc())
        .all()
    )
    return ok([_page_dict(p) for p in pages])


@bp.post("")
@auth_required
def create_page():
    body = request.get_json(silent=True) or {}
    schema_raw, bindings_raw, _ = normalize_page(body.get("schema_json"), body.get("data_bindings"))
    schema, schema_err = validate_schema_json(schema_raw)
    if schema_err:
        return err("INVALID_SCHEMA", schema_err, 400)
    bindings, bind_err = validate_data_bindings(bindings_raw)
    if bind_err:
        return err("INVALID_BINDINGS", bind_err, 400)

    page_id = body.get("id") or new_id()
    pinned = bool(body.get("pinned", False))
    now = now_ms()
    page = CustomPage(
        id=page_id,
        user_id=g.user_id,
        name=body.get("name") or "新页面",
        schema_json=schema_to_storage(schema or DEFAULT_SCHEMA),
        data_bindings=bindings_to_storage(bindings or {}),
        pinned=pinned,
        created_at=now,
        updated_at=now,
    )
    db.session.add(page)
    if pinned:
        db.session.flush()
        _clear_other_pins(g.user_id, page_id)
    db.session.commit()
    return ok(_page_dict(page), 201)


@bp.get("/<page_id>")
@auth_required
def get_page(page_id: str):
    page = CustomPage.query.filter_by(
        id=page_id, user_id=g.user_id, deleted_at=None
    ).first()
    if page is None:
        return err("PAGE_NOT_FOUND", "页面不存在", 404)
    return ok(_page_dict(page))


@bp.patch("/<page_id>")
@auth_required
def update_page(page_id: str):
    page = CustomPage.query.filter_by(
        id=page_id, user_id=g.user_id, deleted_at=None
    ).first()
    if page is None:
        return err("PAGE_NOT_FOUND", "页面不存在", 404)

    body = request.get_json(silent=True) or {}
    if "schema_json" in body or "data_bindings" in body:
        schema_raw, bindings_raw, _ = normalize_page(
            body.get("schema_json") if "schema_json" in body else json.loads(page.schema_json or "{}"),
            body.get("data_bindings") if "data_bindings" in body else json.loads(page.data_bindings or "{}"),
        )
    if "schema_json" in body:
        schema, schema_err = validate_schema_json(schema_raw)
        if schema_err:
            return err("INVALID_SCHEMA", schema_err, 400)
        page.schema_json = schema_to_storage(schema or DEFAULT_SCHEMA)
    if "data_bindings" in body:
        bindings, bind_err = validate_data_bindings(bindings_raw)
        if bind_err:
            return err("INVALID_BINDINGS", bind_err, 400)
        page.data_bindings = bindings_to_storage(bindings or {})
    elif "schema_json" in body:
        bindings, bind_err = validate_data_bindings(bindings_raw)
        if bind_err:
            return err("INVALID_BINDINGS", bind_err, 400)
        page.data_bindings = bindings_to_storage(bindings or {})
    if "name" in body:
        page.name = body["name"] or page.name
    if "pinned" in body:
        page.pinned = bool(body["pinned"])
        if page.pinned:
            _clear_other_pins(g.user_id, page_id)
    page.updated_at = now_ms()
    db.session.commit()
    return ok(_page_dict(page))


@bp.delete("/<page_id>")
@auth_required
def delete_page(page_id: str):
    page = CustomPage.query.filter_by(id=page_id, user_id=g.user_id).first()
    if page is None or page.deleted_at is not None:
        return err("PAGE_NOT_FOUND", "页面不存在", 404)
    now = now_ms()
    page.deleted_at = now
    page.updated_at = now
    page.pinned = False
    db.session.commit()
    return "", 204
