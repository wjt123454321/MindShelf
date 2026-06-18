"""内置 AI 提供方：流式对话与工具循环。"""

import json
from collections.abc import Generator
from typing import Any

import requests
from flask import current_app

from app.ai import search as search_service
from app.ai import tools as tool_service
from app.models import Message


def _ai_cfg() -> dict:
    return current_app.config["MINDSHELF_CONFIG"]["ai"]


def _resolve_model(requested: str | None = None) -> str:
    cfg = _ai_cfg()
    default = cfg.get("model", "deepseek-chat")
    allowed = cfg.get("models") or [default]
    if requested and requested in allowed:
        return requested
    return default if default in allowed else allowed[0]


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


REASONING_ROUND_SEP = "\n\n---\n\n"


def _append_segment(segments: list[dict], seg_type: str, text: str) -> None:
    if not text:
        return
    if segments and segments[-1]["type"] == seg_type:
        segments[-1]["text"] += text
    else:
        segments.append({"type": seg_type, "text": text})


def build_chat_messages(
    history: list[Message],
    user_content: str,
    *,
    enable_search: bool = False,
    search_context: str = "",
    search_no_results: bool = False,
) -> list[dict]:
    system_parts = [
        "你是 MindShelf 知识库助手，简洁准确地回答用户问题。",
        "工具 search_knowledge_bases、search_notes 用于检索用户在本应用内保存的知识库与笔记。",
        "删除或修改本地内容前需用户明确意图。",
    ]
    if enable_search:
        system_parts.append(
            "用户已开启联网搜索能力。需要新闻、时事、百科等外部信息时，"
            "请调用 web_search 工具，不要凭空编造来源。"
            "本地笔记/知识库问题请用 search_notes / search_knowledge_bases。"
            "引用外部来源时使用 Markdown 链接 [标题](url)。"
        )
    elif search_context:
        system_parts.append(
            "若提供了联网搜索资料，请优先依据这些外部资料作答；"
            "引用外部来源时使用 Markdown 链接 [标题](url)。"
        )
    else:
        system_parts.append("引用外部资料时使用 Markdown 链接格式。")
    messages = [{"role": "system", "content": "".join(system_parts)}]
    for m in history:
        if m.role in ("user", "assistant"):
            messages.append({"role": m.role, "content": m.content})
    if not history or history[-1].role != "user" or history[-1].content != user_content:
        messages.append({"role": "user", "content": user_content})
    if search_context:
        messages.insert(
            -1,
            {
                "role": "user",
                "content": f"【联网搜索资料】\n{search_context}",
            },
        )
    return messages


def prepare_search_events(query: str, enable_search: bool) -> tuple[list[str], str]:
    """执行联网搜索，返回 (SSE 事件列表, 注入上下文)。"""
    if not enable_search or not search_service.is_enabled():
        return [], ""
    results, context = search_service.gather_search_context(query)
    events: list[str] = []
    if results:
        events.append(
            _sse(
                "search_result",
                {
                    "query": query.strip(),
                    "results": results,
                },
            )
        )
    return events, context


def _tool_schemas(enable_tools: bool, *, enable_search: bool = False) -> list[dict] | None:
    if not enable_tools:
        return None
    schemas = tool_service.tool_schemas(include_web_search=enable_search)
    return schemas or None


def stream_completion(
    messages: list[dict],
    *,
    enable_tools: bool = False,
    enable_search: bool = False,
    model: str | None = None,
    conversation_id: str | None = None,
    branch_id: str | None = None,
    user_message_id: str | None = None,
    result: dict | None = None,
    initial_content: str = "",
    initial_reasoning: str = "",
    initial_segments: list[dict] | None = None,
    start_round: int = 0,
) -> Generator[str, None, None]:
    """流式生成 assistant 回复；最终文本写入 result['content'] / result['reasoning'] / result['segments']。"""
    if result is None:
        result = {}
    cfg = _ai_cfg()
    url = f"{cfg['base_url'].rstrip('/')}/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {cfg['api_key']}",
        "Content-Type": "application/json",
    }

    working_messages = list(messages)
    full_content = initial_content
    full_reasoning = initial_reasoning
    segments: list[dict] = list(initial_segments or [])
    max_rounds = start_round + 6

    for round_idx in range(start_round, max_rounds):
        if round_idx > 0:
            if full_reasoning:
                full_reasoning += REASONING_ROUND_SEP
            yield _sse("reasoning_round_start", {"round": round_idx + 1})
        payload: dict[str, Any] = {
            "model": _resolve_model(model),
            "messages": working_messages,
            "stream": True,
        }
        tools = _tool_schemas(enable_tools, enable_search=enable_search)
        if tools:
            payload["tools"] = tools

        round_content = ""
        round_reasoning = ""
        tool_calls: dict[int, dict] = {}

        with requests.post(url, headers=headers, json=payload, stream=True, timeout=120) as resp:
            if resp.status_code != 200:
                yield _sse("error", {"message": f"AI 请求失败: {resp.status_code}"})
                result["content"] = full_content
                result["reasoning"] = full_reasoning
                result["segments"] = segments
                return

            for line in resp.iter_lines(decode_unicode=True):
                if not line or not line.startswith("data: "):
                    continue
                data_str = line[6:]
                if data_str.strip() == "[DONE]":
                    break
                try:
                    chunk = json.loads(data_str)
                except json.JSONDecodeError:
                    continue

                choice = chunk.get("choices", [{}])[0]
                delta = choice.get("delta", {})

                reasoning_delta = delta.get("reasoning_content") or ""
                content_delta = delta.get("content") or ""
                if reasoning_delta:
                    round_reasoning += reasoning_delta
                    full_reasoning += reasoning_delta
                    _append_segment(segments, "reasoning", reasoning_delta)
                    yield _sse("reasoning_delta", {"content": reasoning_delta})
                if content_delta:
                    round_content += content_delta
                    full_content += content_delta
                    _append_segment(segments, "content", content_delta)
                    yield _sse("message_delta", {"content": content_delta})

                for tc in delta.get("tool_calls") or []:
                    idx = tc.get("index", 0)
                    entry = tool_calls.setdefault(
                        idx,
                        {"id": "", "name": "", "arguments": ""},
                    )
                    if tc.get("id"):
                        entry["id"] = tc["id"]
                    fn = tc.get("function") or {}
                    if fn.get("name"):
                        entry["name"] = fn["name"]
                    if fn.get("arguments"):
                        entry["arguments"] += fn["arguments"]

        if not tool_calls:
            result["content"] = full_content
            result["reasoning"] = full_reasoning
            result["segments"] = segments
            return

        assistant_msg: dict[str, Any] = {
            "role": "assistant",
            "content": round_content or None,
            "tool_calls": [
                {
                    "id": tc["id"] or f"call_{i}",
                    "type": "function",
                    "function": {
                        "name": tc["name"],
                        "arguments": tc["arguments"] or "{}",
                    },
                }
                for i, tc in sorted(tool_calls.items())
            ],
        }
        working_messages.append(assistant_msg)

        for i, tc in sorted(tool_calls.items()):
            name = tc["name"]
            try:
                args = json.loads(tc["arguments"] or "{}")
            except json.JSONDecodeError:
                args = {}

            yield _sse("status", {"phase": "tool", "tool": name})
            yield _sse("tool_call", {"tool": name, "arguments": args})

            if tool_service.is_write_tool(name):
                if not (conversation_id and branch_id):
                    yield _sse("error", {"message": "无法执行写操作：缺少会话上下文"})
                    result["content"] = full_content
                    result["reasoning"] = full_reasoning
                    result["segments"] = segments
                    return
                pending = tool_service.create_pending(
                    conversation_id,
                    branch_id,
                    name,
                    args,
                    resume_messages_json=json.dumps(working_messages, ensure_ascii=False),
                    tool_call_id=tc["id"] or f"call_{i}",
                    partial_content=full_content,
                    partial_reasoning=full_reasoning,
                    partial_segments_json=json.dumps(segments, ensure_ascii=False),
                    user_message_id=user_message_id or "",
                    resume_round=round_idx + 1,
                )
                preview = json.loads(pending.preview_json)
                yield _sse(
                    "tool_pending",
                    {
                        "pending_id": pending.id,
                        "tool": name,
                        "preview": preview,
                    },
                )
                result["content"] = full_content
                result["reasoning"] = full_reasoning
                result["segments"] = segments
                return

            tool_result = tool_service.execute_read_tool(name, args)
            if name == "web_search" and tool_result.get("results"):
                yield _sse(
                    "search_result",
                    {
                        "query": tool_result.get("query", args.get("query", "")),
                        "results": tool_result["results"],
                    },
                )
            yield _sse("tool_result", {"tool": name, "result": tool_result})
            working_messages.append(
                {
                    "role": "tool",
                    "tool_call_id": tc["id"] or f"call_{i}",
                    "content": json.dumps(tool_result, ensure_ascii=False),
                }
            )

    result["content"] = full_content
    result["reasoning"] = full_reasoning
    result["segments"] = segments


def resume_after_tool(
    pending,
    *,
    enable_tools: bool = True,
    enable_search: bool = False,
    model: str | None = None,
    result: dict | None = None,
) -> Generator[str, None, None]:
    """用户确认写工具后继续流式生成。"""
    if result is None:
        result = {}
    working_messages = json.loads(pending.resume_messages_json or "[]")
    tool_result = json.loads(pending.tool_result_json or "{}")
    working_messages.append(
        {
            "role": "tool",
            "tool_call_id": pending.tool_call_id,
            "content": json.dumps(tool_result, ensure_ascii=False),
        }
    )
    segments = json.loads(pending.partial_segments_json or "[]")
    yield from stream_completion(
        working_messages,
        enable_tools=enable_tools,
        enable_search=enable_search,
        model=model,
        conversation_id=pending.conversation_id,
        branch_id=pending.branch_id,
        user_message_id=pending.user_message_id or None,
        result=result,
        initial_content=pending.partial_content or "",
        initial_reasoning=pending.partial_reasoning or "",
        initial_segments=segments,
        start_round=pending.resume_round or 0,
    )
