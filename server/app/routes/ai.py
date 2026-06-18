"""内置 AI 流式对话。"""

import json

from flask import Blueprint, Response, g, request, stream_with_context

from app.ai import search as search_service
from app.ai import tools as tool_service
from app.ai.provider import build_chat_messages, prepare_search_events, resume_after_tool, stream_completion
from app.extensions import db
from app.models import Conversation, Message, PendingToolConfirmation
from app.routes.conversations import _branch_context_messages, _msg_dict
from app.utils.auth import auth_required, new_id, now_ms
from app.utils.responses import err, ok

bp = Blueprint("ai", __name__, url_prefix="/api/v1/ai")


def _save_assistant(
    conv: Conversation,
    conversation_id: str,
    branch_id: str,
    user_msg_id: str,
    content: str,
    reasoning: str = "",
    segments: list | None = None,
    *,
    allow_empty: bool = False,
    message_id: str | None = None,
) -> Message | None:
    text = content.strip()
    has_reasoning = bool(reasoning.strip())
    has_segments = bool(segments)
    if not text and not allow_empty and not has_reasoning and not has_segments:
        return None

    existing_id = message_id
    assistant = None
    if existing_id:
        assistant = Message.query.filter_by(id=existing_id).first()

    if assistant is None:
        assistant = Message(
            id=new_id(),
            conversation_id=conversation_id,
            branch_id=branch_id,
            parent_id=user_msg_id,
            role="assistant",
        )
        db.session.add(assistant)
    assistant.content = text
    assistant.reasoning = reasoning.strip()
    assistant.segments_json = json.dumps(segments or [], ensure_ascii=False)
    conv.updated_at = now_ms()
    db.session.commit()
    return assistant


@bp.post("/chat/stream")
@auth_required
def chat_stream():
    body = request.get_json(silent=True) or {}
    conversation_id = body.get("conversation_id")
    branch_id = body.get("branch_id")
    parent_message_id = body.get("parent_message_id")
    message = body.get("message") or {}
    options = body.get("options") or {}
    enable_tools = bool(options.get("enable_tools"))
    enable_search = bool(options.get("enable_search"))
    model = options.get("model")

    conv = Conversation.query.filter_by(id=conversation_id, user_id=g.user_id).first()
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)

    user_msg = Message(
        id=message.get("id") or new_id(),
        conversation_id=conversation_id,
        branch_id=branch_id,
        parent_id=parent_message_id,
        role="user",
        content=message.get("content") or "",
    )
    user_content = user_msg.content.strip()
    if conv.title == "新对话" and user_content:
        conv.title = user_content[:40]
    db.session.add(user_msg)
    conv.updated_at = now_ms()
    db.session.commit()

    history = _branch_context_messages(conversation_id, branch_id)

    def generate():
        stream_result: dict = {"content": "", "reasoning": "", "segments": []}
        assistant: Message | None = None
        partial_assistant_id: str | None = None
        yield ": connected\n\n"
        try:
            ai_messages = build_chat_messages(
                history,
                user_msg.content,
                enable_search=enable_search and search_service.is_enabled(),
            )
            yield _sse("status", {"phase": "thinking"})
            for event in stream_completion(
                ai_messages,
                enable_tools=enable_tools,
                enable_search=enable_search and search_service.is_enabled(),
                model=model,
                conversation_id=conversation_id,
                branch_id=branch_id,
                user_message_id=user_msg.id,
                result=stream_result,
            ):
                if event.startswith("event: tool_pending"):
                    assistant = _save_assistant(
                        conv,
                        conversation_id,
                        branch_id,
                        user_msg.id,
                        stream_result.get("content", ""),
                        stream_result.get("reasoning", ""),
                        stream_result.get("segments"),
                        allow_empty=True,
                        message_id=partial_assistant_id,
                    )
                    if assistant is not None:
                        partial_assistant_id = assistant.id
                    yield event
                    if assistant is not None:
                        yield _sse("message_done", {"message": _msg_dict(assistant)})
                    return
                yield event

            full_content = stream_result.get("content", "")
            full_reasoning = stream_result.get("reasoning", "")
            segments = stream_result.get("segments", [])
            assistant = _save_assistant(
                conv,
                conversation_id,
                branch_id,
                user_msg.id,
                full_content,
                full_reasoning,
                segments,
                allow_empty=False,
                message_id=partial_assistant_id,
            )
            if assistant is None:
                yield _sse("error", {"message": "AI 未返回有效内容"})
                return

            yield _sse("message_done", {"message": _msg_dict(assistant)})
            yield _sse("done", {"conversation_id": conversation_id, "branch_id": branch_id})
        except GeneratorExit:
            pass
        except Exception as exc:
            db.session.rollback()
            yield _sse("error", {"message": str(exc)})
        finally:
            full_content = stream_result.get("content", "")
            full_reasoning = stream_result.get("reasoning", "")
            segments = stream_result.get("segments", [])
            if assistant is None and (full_content.strip() or full_reasoning.strip() or segments):
                try:
                    assistant = _save_assistant(
                        conv,
                        conversation_id,
                        branch_id,
                        user_msg.id,
                        full_content,
                        full_reasoning,
                        segments,
                        allow_empty=True,
                    )
                except Exception:
                    db.session.rollback()

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


@bp.post("/tools/resume/stream")
@auth_required
def resume_tool_stream():
    body = request.get_json(silent=True) or {}
    pending_id = body.get("pending_id")
    options = body.get("options") or {}
    enable_tools = bool(options.get("enable_tools", True))
    enable_search = bool(options.get("enable_search"))
    model = options.get("model")

    pending = PendingToolConfirmation.query.filter_by(id=pending_id, user_id=g.user_id).first()
    if pending is None:
        return err("NOT_FOUND", "待确认操作不存在或已过期", 404)
    if not pending.executed:
        return err("INVALID", "请先确认工具操作", 400)
    if pending.expires_at < now_ms():
        tool_service.delete_pending(pending)
        return err("NOT_FOUND", "待确认操作已过期", 404)

    conv = Conversation.query.filter_by(id=pending.conversation_id, user_id=g.user_id).first()
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)

    existing = (
        Message.query.filter_by(
            conversation_id=pending.conversation_id,
            branch_id=pending.branch_id,
            parent_id=pending.user_message_id,
            role="assistant",
        )
        .order_by(Message.created_at.desc())
        .first()
    )
    partial_assistant_id = existing.id if existing else None
    resume_state = pending

    def generate():
        stream_result: dict = {"content": "", "reasoning": "", "segments": []}
        assistant: Message | None = None
        partial_id = partial_assistant_id
        yield ": connected\n\n"
        try:
            yield _sse("status", {"phase": "thinking"})
            for event in resume_after_tool(
                resume_state,
                enable_tools=enable_tools,
                enable_search=enable_search,
                model=model,
                result=stream_result,
            ):
                if event.startswith("event: tool_pending"):
                    assistant = _save_assistant(
                        conv,
                        resume_state.conversation_id,
                        resume_state.branch_id,
                        resume_state.user_message_id,
                        stream_result.get("content", ""),
                        stream_result.get("reasoning", ""),
                        stream_result.get("segments"),
                        allow_empty=True,
                        message_id=partial_id,
                    )
                    if assistant is not None:
                        partial_id = assistant.id
                    yield event
                    if assistant is not None:
                        yield _sse("message_done", {"message": _msg_dict(assistant)})
                    return
                yield event

            full_content = stream_result.get("content", "")
            full_reasoning = stream_result.get("reasoning", "")
            segments = stream_result.get("segments", [])
            assistant = _save_assistant(
                conv,
                resume_state.conversation_id,
                resume_state.branch_id,
                resume_state.user_message_id,
                full_content,
                full_reasoning,
                segments,
                allow_empty=True,
                message_id=partial_id,
            )
            if assistant is None:
                yield _sse("error", {"message": "AI 未返回有效内容"})
                return

            yield _sse("message_done", {"message": _msg_dict(assistant)})
            yield _sse(
                "done",
                {
                    "conversation_id": resume_state.conversation_id,
                    "branch_id": resume_state.branch_id,
                },
            )
        except GeneratorExit:
            pass
        except Exception as exc:
            db.session.rollback()
            yield _sse("error", {"message": str(exc)})
        finally:
            tool_service.delete_pending(resume_state)

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@bp.get("/tools/pending")
@auth_required
def list_pending_tools():
    conversation_id = request.args.get("conversation_id")
    branch_id = request.args.get("branch_id")
    if not conversation_id:
        return err("INVALID", "缺少 conversation_id", 400)

    conv = Conversation.query.filter_by(id=conversation_id, user_id=g.user_id).first()
    if conv is None:
        return err("NOT_FOUND", "会话不存在", 404)

    q = PendingToolConfirmation.query.filter_by(
        user_id=g.user_id,
        conversation_id=conversation_id,
        executed=False,
    )
    if branch_id:
        q = q.filter_by(branch_id=branch_id)
    items = q.order_by(PendingToolConfirmation.created_at).all()
    now = now_ms()
    active = []
    for pending in items:
        if pending.expires_at < now:
            tool_service.delete_pending(pending)
            continue
        active.append(tool_service.pending_tool_dict(pending))
    return ok(active)


@bp.post("/tools/confirm")
@auth_required
def confirm_tool():
    body = request.get_json(silent=True) or {}
    pending_id = body.get("pending_id")
    approved = bool(body.get("approved"))

    pending = PendingToolConfirmation.query.filter_by(id=pending_id, user_id=g.user_id).first()
    if pending is None:
        return err("NOT_FOUND", "待确认操作不存在或已过期", 404)
    if pending.expires_at < now_ms():
        tool_service.delete_pending(pending)
        return err("NOT_FOUND", "待确认操作已过期", 404)

    result = tool_service.execute_pending(pending, approved)
    return ok(result)
