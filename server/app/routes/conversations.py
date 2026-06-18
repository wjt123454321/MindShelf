"""对话、分支、消息路由。"""

import json

from flask import Blueprint, g, request

from app.extensions import db
from app.models import Branch, Conversation, Message
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("conversations", __name__, url_prefix="/api/v1/conversations")


def _conv_dict(c: Conversation) -> dict:
    title = c.title
    if title == "新对话":
        first_user = (
            Message.query.filter_by(conversation_id=c.id, role="user")
            .order_by(Message.created_at)
            .first()
        )
        if first_user and first_user.content.strip():
            title = first_user.content.strip()[:40]
    return {
        "id": c.id,
        "title": title,
        "created_at": c.created_at,
        "updated_at": c.updated_at,
    }


def _branch_dict(b: Branch) -> dict:
    return {
        "id": b.id,
        "conversation_id": b.conversation_id,
        "label": b.label,
        "root_message_id": b.root_message_id,
        "created_at": b.created_at,
    }


def _msg_dict(m: Message) -> dict:
    segments = []
    if m.segments_json:
        try:
            segments = json.loads(m.segments_json)
        except json.JSONDecodeError:
            segments = []
    return {
        "id": m.id,
        "conversation_id": m.conversation_id,
        "branch_id": m.branch_id,
        "parent_id": m.parent_id,
        "role": m.role,
        "content": m.content,
        "reasoning": m.reasoning or "",
        "segments": segments,
        "created_at": m.created_at,
    }


def _get_conv(conversation_id: str) -> Conversation | None:
    return Conversation.query.filter_by(id=conversation_id, user_id=g.user_id).first()


@bp.get("")
@auth_required
def list_conversations():
    items = (
        Conversation.query.filter_by(user_id=g.user_id)
        .order_by(Conversation.updated_at.desc())
        .all()
    )
    return ok([_conv_dict(c) for c in items])


@bp.post("")
@auth_required
def create_conversation():
    body = request.get_json(silent=True) or {}
    conv = Conversation(
        id=body.get("id") or new_id(),
        user_id=g.user_id,
        title=body.get("title") or "新对话",
    )
    db.session.add(conv)
    db.session.flush()

    branch = Branch(
        conversation_id=conv.id,
        label="主分支",
    )
    db.session.add(branch)
    db.session.commit()
    return ok(_conv_dict(conv), 201)


@bp.get("/<conversation_id>")
@auth_required
def get_conversation(conversation_id: str):
    conv = _get_conv(conversation_id)
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)
    return ok(_conv_dict(conv))


@bp.patch("/<conversation_id>")
@auth_required
def update_conversation(conversation_id: str):
    conv = _get_conv(conversation_id)
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)
    body = request.get_json(silent=True) or {}
    if "title" in body:
        conv.title = body["title"]
    conv.updated_at = now_ms()
    db.session.commit()
    return ok(_conv_dict(conv))


@bp.delete("/<conversation_id>")
@auth_required
def delete_conversation(conversation_id: str):
    conv = _get_conv(conversation_id)
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)
    Message.query.filter_by(conversation_id=conversation_id).delete()
    Branch.query.filter_by(conversation_id=conversation_id).delete()
    db.session.delete(conv)
    db.session.commit()
    return "", 204


@bp.get("/<conversation_id>/branches")
@auth_required
def list_branches(conversation_id: str):
    if _get_conv(conversation_id) is None:
        return err("NOT_FOUND", "会话不存在", 404)
    branches = Branch.query.filter_by(conversation_id=conversation_id).order_by(Branch.created_at).all()
    return ok([_branch_dict(b) for b in branches])


@bp.post("/<conversation_id>/branches")
@auth_required
def create_branch(conversation_id: str):
    conv = _get_conv(conversation_id)
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)

    body = request.get_json(silent=True) or {}
    fork_id = body.get("fork_from_message_id")
    fork_msg = Message.query.filter_by(id=fork_id, conversation_id=conversation_id).first()
    if fork_msg is None:
        return err("INVALID_REQUEST", "fork 消息无效")

    branch = Branch(
        id=body.get("id") or new_id(),
        conversation_id=conversation_id,
        label=body.get("label") or "新分支",
        root_message_id=fork_msg.parent_id,
    )
    db.session.add(branch)
    db.session.commit()
    return ok(_branch_dict(branch), 201)


@bp.get("/<conversation_id>/branches/<branch_id>")
@auth_required
def get_branch(conversation_id: str, branch_id: str):
    if _get_conv(conversation_id) is None:
        return err("NOT_FOUND", "会话不存在", 404)
    branch = Branch.query.filter_by(id=branch_id, conversation_id=conversation_id).first()
    if branch is None:
        return err("NOT_FOUND", "分支不存在", 404)
    return ok(_branch_dict(branch))


def _branch_context_messages(conversation_id: str, branch_id: str) -> list[Message]:
    """返回分支完整上下文链（从 fork 点回溯 + 本分支消息）。"""
    branch = Branch.query.filter_by(id=branch_id, conversation_id=conversation_id).first()
    if branch is None:
        return []

    branch_msgs = (
        Message.query.filter_by(conversation_id=conversation_id, branch_id=branch_id)
        .order_by(Message.created_at)
        .all()
    )

    if branch.root_message_id is None:
        return branch_msgs

    # 回溯 fork 点之前的祖先链
    ancestors: list[Message] = []
    current_id = branch.root_message_id
    while current_id:
        msg = Message.query.filter_by(id=current_id, conversation_id=conversation_id).first()
        if msg is None:
            break
        ancestors.insert(0, msg)
        current_id = msg.parent_id

    return ancestors + branch_msgs


@bp.get("/<conversation_id>/messages")
@auth_required
def list_all_messages(conversation_id: str):
    """返回会话下全部消息，用于客户端计算兄弟节点与分支导航。"""
    if _get_conv(conversation_id) is None:
        return err("NOT_FOUND", "会话不存在", 404)
    messages = (
        Message.query.filter_by(conversation_id=conversation_id)
        .order_by(Message.created_at)
        .all()
    )
    return ok([_msg_dict(m) for m in messages])


@bp.get("/<conversation_id>/branches/<branch_id>/messages")
@auth_required
def list_messages(conversation_id: str, branch_id: str):
    if _get_conv(conversation_id) is None:
        return err("NOT_FOUND", "会话不存在", 404)
    messages = _branch_context_messages(conversation_id, branch_id)
    return ok([_msg_dict(m) for m in messages])


@bp.post("/<conversation_id>/branches/<branch_id>/messages")
@auth_required
def create_message(conversation_id: str, branch_id: str):
    conv = _get_conv(conversation_id)
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)
    branch = Branch.query.filter_by(id=branch_id, conversation_id=conversation_id).first()
    if branch is None:
        return err("NOT_FOUND", "分支不存在", 404)

    body = request.get_json(silent=True) or {}
    msg = Message(
        id=body.get("id") or new_id(),
        conversation_id=conversation_id,
        branch_id=branch_id,
        parent_id=body.get("parent_id"),
        role=body.get("role") or "user",
        content=body.get("content") or "",
    )
    db.session.add(msg)
    conv.updated_at = now_ms()
    db.session.commit()
    return ok(_msg_dict(msg), 201)
