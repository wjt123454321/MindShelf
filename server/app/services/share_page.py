"""公开分享页的 HTML 渲染。"""

from __future__ import annotations

import html
import re
from typing import Any

import bleach
import markdown

_MD_EXTENSIONS = ("fenced_code", "tables", "nl2br", "sane_lists")
_MD_ALLOWED_TAGS = frozenset(
    {
        "p",
        "br",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6",
        "ul",
        "ol",
        "li",
        "blockquote",
        "pre",
        "code",
        "strong",
        "em",
        "del",
        "a",
        "hr",
        "table",
        "thead",
        "tbody",
        "tr",
        "th",
        "td",
    }
)
_MD_ALLOWED_ATTRS = {
    "a": ["href", "title", "rel"],
    "code": ["class"],
    "pre": ["class"],
    "th": ["align"],
    "td": ["align"],
}


def _normalize_markdown(text: str) -> str:
    """表格前补空行，便于 GFM 解析。"""
    if not text:
        return ""
    lines = text.splitlines()
    result: list[str] = []
    for line in lines:
        trimmed = line.strip()
        if trimmed.startswith("|") and result:
            prev = result[-1].strip()
            if prev and not prev.startswith("|"):
                result.append("")
        result.append(line)
    return "\n".join(result)


def _render_markdown_html(text: str) -> str:
    if not text.strip():
        return "<p></p>"
    source = _normalize_markdown(text)
    rendered = markdown.markdown(source, extensions=_MD_EXTENSIONS, output_format="html5")
    cleaned = bleach.clean(
        rendered,
        tags=_MD_ALLOWED_TAGS,
        attributes=_MD_ALLOWED_ATTRS,
        strip=True,
    )
    # 外链新窗口打开，并限制 javascript: 协议（bleach 已过滤，此处双保险）
    return re.sub(
        r'<a href="(https?://[^"]+)"',
        r'<a href="\1" target="_blank" rel="noopener noreferrer"',
        cleaned,
    )


def _page_shell(title: str, body: str) -> str:
    safe_title = html.escape(title)
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{safe_title} · MindShelf</title>
  <style>
    :root {{
      --bg: #f7f8fa;
      --surface: #ffffff;
      --text: #1a1c1e;
      --muted: #5f6368;
      --primary: #1e5aa8;
      --border: #e3e5e8;
      --code-bg: #eef0f3;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC",
        "Microsoft YaHei", sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.65;
    }}
    .wrap {{
      max-width: 720px;
      margin: 0 auto;
      padding: 24px 16px 48px;
    }}
    .badge {{
      display: inline-block;
      font-size: 12px;
      color: var(--muted);
      margin-bottom: 12px;
    }}
    .card {{
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 20px 18px;
      margin-bottom: 16px;
    }}
    .page-title {{
      margin: 0 0 8px;
      font-size: 1.5rem;
      line-height: 1.35;
    }}
    .note-title {{
      margin: 0 0 12px;
      font-size: 1.05rem;
    }}
    .desc {{
      color: var(--muted);
      margin: 0 0 16px;
      font-size: 0.95rem;
    }}
    .md {{
      font-size: 1rem;
      line-height: 1.7;
      word-break: break-word;
    }}
    .md > :first-child {{ margin-top: 0; }}
    .md > :last-child {{ margin-bottom: 0; }}
    .md h1, .md h2, .md h3, .md h4 {{
      margin: 1.2em 0 0.5em;
      line-height: 1.35;
    }}
    .md h1 {{ font-size: 1.35rem; }}
    .md h2 {{ font-size: 1.2rem; }}
    .md h3 {{ font-size: 1.08rem; }}
    .md p {{ margin: 0.65em 0; }}
    .md ul, .md ol {{ margin: 0.65em 0; padding-left: 1.4em; }}
    .md li {{ margin: 0.25em 0; }}
    .md blockquote {{
      margin: 0.8em 0;
      padding: 0.4em 0.9em;
      border-left: 3px solid var(--primary);
      color: var(--muted);
      background: var(--code-bg);
      border-radius: 0 8px 8px 0;
    }}
    .md code {{
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.9em;
      background: var(--code-bg);
      padding: 0.1em 0.35em;
      border-radius: 4px;
    }}
    .md pre {{
      margin: 0.8em 0;
      padding: 12px 14px;
      background: var(--code-bg);
      border-radius: 8px;
      overflow-x: auto;
      line-height: 1.5;
    }}
    .md pre code {{
      background: none;
      padding: 0;
    }}
    .md a {{ color: var(--primary); text-decoration: underline; }}
    .md table {{
      width: 100%;
      border-collapse: collapse;
      margin: 0.8em 0;
      display: block;
      overflow-x: auto;
    }}
    .md th, .md td {{
      border: 1px solid var(--border);
      padding: 8px 10px;
      text-align: left;
    }}
    .md th {{ background: var(--code-bg); }}
    .md hr {{
      border: none;
      border-top: 1px solid var(--border);
      margin: 1.2em 0;
    }}
    .footer {{
      margin-top: 24px;
      font-size: 12px;
      color: var(--muted);
      text-align: center;
    }}
    .error-title {{
      color: #b3261e;
    }}
  </style>
</head>
<body>
  <div class="wrap">
    {body}
    <p class="footer">由 MindShelf 分享 · 只读</p>
  </div>
</body>
</html>"""


def render_share_not_found() -> str:
    body = """
    <div class="card">
      <p class="badge">MindShelf 分享</p>
      <h1 class="page-title error-title">链接无效</h1>
      <p class="desc">该分享不存在、已撤销或内容已被删除。</p>
    </div>
    """
    return _page_shell("链接无效", body)


def render_share_page(payload: dict[str, Any]) -> str:
    resource_type = payload.get("resource_type")
    snapshot = payload.get("snapshot") or {}
    if resource_type == "note":
        title = snapshot.get("title") or "笔记"
        content = snapshot.get("content") or ""
        body = f"""
    <div class="card">
      <p class="badge">MindShelf 笔记分享</p>
      <h1 class="page-title">{html.escape(title)}</h1>
      <div class="md">{_render_markdown_html(content)}</div>
    </div>
        """
        return _page_shell(title, body)

    if resource_type == "knowledge_base":
        name = snapshot.get("name") or "知识库"
        description = snapshot.get("description") or ""
        notes = snapshot.get("notes") or []
        note_blocks = []
        for note in notes:
            note_title = html.escape(note.get("title") or "无标题")
            note_html = _render_markdown_html(note.get("content") or "")
            note_blocks.append(
                f"""
    <div class="card">
      <h2 class="note-title">{note_title}</h2>
      <div class="md">{note_html}</div>
    </div>
                """
            )
        desc_html = (
            f'<div class="desc md">{_render_markdown_html(description)}</div>'
            if description
            else ""
        )
        body = f"""
    <div class="card">
      <p class="badge">MindShelf 知识库分享</p>
      <h1 class="page-title">{html.escape(name)}</h1>
      {desc_html}
      <p class="desc">共 {len(notes)} 篇笔记</p>
    </div>
    {''.join(note_blocks)}
        """
        return _page_shell(name, body)

    return render_share_not_found()
