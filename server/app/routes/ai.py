"""内置 AI：OpenAI 兼容 LLM 代理与联网搜索代理（无服务端工具循环）。"""

import json

import requests
from flask import Blueprint, Response, request, stream_with_context

from app.ai import search as search_service
from app.ai.provider import _ai_cfg, _resolve_model
from app.utils.auth import auth_required
from app.utils.responses import err, ok

bp = Blueprint("ai", __name__, url_prefix="/api/v1/ai")


@bp.post("/completions")
@auth_required
def completions():
    """JWT 鉴权下转发至内置 LLM，注入服务端 api_key，SSE 原样透传。"""
    body = request.get_json(silent=True) or {}
    if not body.get("messages"):
        return err("INVALID", "缺少 messages", 400)

    cfg = _ai_cfg()
    url = f"{cfg['base_url'].rstrip('/')}/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {cfg['api_key']}",
        "Content-Type": "application/json",
    }
    payload = dict(body)
    payload["model"] = _resolve_model(body.get("model"))

    if payload.get("stream"):
        def generate():
            try:
                with requests.post(
                    url, headers=headers, json=payload, stream=True, timeout=120
                ) as resp:
                    if resp.status_code != 200:
                        detail = resp.text[:500]
                        yield f"data: {json.dumps({'error': detail}, ensure_ascii=False)}\n\n"
                        return
                    for line in resp.iter_lines(decode_unicode=True):
                        if line is None:
                            continue
                        yield f"{line}\n"
                    yield "\n"
            except requests.RequestException as exc:
                yield f"data: {json.dumps({'error': str(exc)}, ensure_ascii=False)}\n\n"

        return Response(
            stream_with_context(generate()),
            mimetype="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no",
                "Connection": "keep-alive",
            },
        )

    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=120)
    except requests.RequestException as exc:
        return err("UPSTREAM_ERROR", str(exc), 502)
    return Response(resp.content, status=resp.status_code, mimetype="application/json")


@bp.post("/search")
@auth_required
def ai_search():
    """联网搜索代理，供客户端 web_search 工具调用。"""
    body = request.get_json(silent=True) or {}
    query = (body.get("query") or "").strip()
    if not query:
        return err("INVALID", "缺少 query", 400)
    if not search_service.is_enabled():
        return err("UNAVAILABLE", "联网搜索未配置或未启用", 503)

    max_results = body.get("max_results")
    if max_results is not None:
        try:
            max_results = int(max_results)
        except (TypeError, ValueError):
            max_results = None

    results, context = search_service.gather_search_context(
        query,
        max_results=max_results,
        sort=(body.get("sort") or "").strip() or None,
        time_range=(body.get("time_range") or "").strip() or None,
    )
    return ok(
        {
            "query": query,
            "results": results,
            "result_count": len(results),
            "context": context,
            "context_preview": context[:1500] if context else "",
        }
    )
