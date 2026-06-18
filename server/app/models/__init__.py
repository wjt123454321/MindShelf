"""SQLAlchemy 模型。"""

import uuid
from datetime import datetime, timezone

from app.extensions import db


def _uuid() -> str:
    return str(uuid.uuid4())


def _now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


class User(db.Model):
    __tablename__ = "users"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    email = db.Column(db.String(255), unique=True, nullable=False, index=True)
    username = db.Column(db.String(64), unique=True, nullable=True, index=True)
    password_hash = db.Column(db.String(128), nullable=True)
    created_at = db.Column(db.BigInteger, nullable=False, default=_now_ms)


class RefreshToken(db.Model):
    __tablename__ = "refresh_tokens"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    user_id = db.Column(db.String(36), db.ForeignKey("users.id"), nullable=False, index=True)
    token_hash = db.Column(db.String(64), nullable=False, unique=True, index=True)
    expires_at = db.Column(db.BigInteger, nullable=False)
    revoked = db.Column(db.Boolean, nullable=False, default=False)


class VerificationCode(db.Model):
    __tablename__ = "verification_codes"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    email = db.Column(db.String(255), nullable=False, index=True)
    code = db.Column(db.String(6), nullable=False)
    purpose = db.Column(db.String(32), nullable=False)
    expires_at = db.Column(db.BigInteger, nullable=False)
    created_at = db.Column(db.BigInteger, nullable=False, default=_now_ms)


class KnowledgeBase(db.Model):
    __tablename__ = "knowledge_bases"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    user_id = db.Column(db.String(36), db.ForeignKey("users.id"), nullable=False, index=True)
    name = db.Column(db.String(255), nullable=False)
    description = db.Column(db.Text, nullable=False, default="")
    sort_order = db.Column(db.Integer, nullable=False, default=0)
    deleted_at = db.Column(db.BigInteger, nullable=True)
    updated_at = db.Column(db.BigInteger, nullable=False, default=_now_ms, onupdate=_now_ms)


class Note(db.Model):
    __tablename__ = "notes"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    user_id = db.Column(db.String(36), db.ForeignKey("users.id"), nullable=False, index=True)
    title = db.Column(db.String(512), nullable=False, default="")
    content = db.Column(db.Text, nullable=False, default="")
    deleted_at = db.Column(db.BigInteger, nullable=True)
    updated_at = db.Column(db.BigInteger, nullable=False, default=_now_ms, onupdate=_now_ms)
    sync_version = db.Column(db.Integer, nullable=False, default=1)


class NoteKb(db.Model):
    __tablename__ = "note_kb"

    note_id = db.Column(db.String(36), db.ForeignKey("notes.id"), primary_key=True)
    kb_id = db.Column(db.String(36), db.ForeignKey("knowledge_bases.id"), primary_key=True)


class Conversation(db.Model):
    __tablename__ = "conversations"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    user_id = db.Column(db.String(36), db.ForeignKey("users.id"), nullable=False, index=True)
    title = db.Column(db.String(255), nullable=False, default="新对话")
    created_at = db.Column(db.BigInteger, nullable=False, default=_now_ms)
    updated_at = db.Column(db.BigInteger, nullable=False, default=_now_ms, onupdate=_now_ms)


class Branch(db.Model):
    __tablename__ = "branches"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    conversation_id = db.Column(
        db.String(36), db.ForeignKey("conversations.id"), nullable=False, index=True
    )
    label = db.Column(db.String(128), nullable=False, default="主分支")
    root_message_id = db.Column(db.String(36), nullable=True)
    created_at = db.Column(db.BigInteger, nullable=False, default=_now_ms)


class Message(db.Model):
    __tablename__ = "messages"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    conversation_id = db.Column(
        db.String(36), db.ForeignKey("conversations.id"), nullable=False, index=True
    )
    branch_id = db.Column(db.String(36), db.ForeignKey("branches.id"), nullable=False, index=True)
    parent_id = db.Column(db.String(36), nullable=True, index=True)
    role = db.Column(db.String(16), nullable=False)
    content = db.Column(db.Text, nullable=False, default="")
    reasoning = db.Column(db.Text, nullable=False, default="")
    segments_json = db.Column(db.Text, nullable=False, default="[]")
    created_at = db.Column(db.BigInteger, nullable=False, default=_now_ms)


class PendingToolConfirmation(db.Model):
    __tablename__ = "pending_tool_confirmations"

    id = db.Column(db.String(36), primary_key=True, default=_uuid)
    user_id = db.Column(db.String(36), db.ForeignKey("users.id"), nullable=False, index=True)
    conversation_id = db.Column(db.String(36), nullable=False, index=True)
    branch_id = db.Column(db.String(36), nullable=False)
    tool_name = db.Column(db.String(64), nullable=False)
    arguments_json = db.Column(db.Text, nullable=False)
    preview_json = db.Column(db.Text, nullable=False)
    resume_messages_json = db.Column(db.Text, nullable=False, default="[]")
    tool_call_id = db.Column(db.String(64), nullable=False, default="")
    partial_content = db.Column(db.Text, nullable=False, default="")
    partial_reasoning = db.Column(db.Text, nullable=False, default="")
    partial_segments_json = db.Column(db.Text, nullable=False, default="[]")
    user_message_id = db.Column(db.String(36), nullable=False, default="")
    resume_round = db.Column(db.Integer, nullable=False, default=0)
    executed = db.Column(db.Boolean, nullable=False, default=False)
    tool_result_json = db.Column(db.Text, nullable=True)
    created_at = db.Column(db.BigInteger, nullable=False, default=_now_ms)
    expires_at = db.Column(db.BigInteger, nullable=False)
