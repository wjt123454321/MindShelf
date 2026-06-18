"""LLM 输出规范化，与客户端 PageSchemaNormalizer 对齐。"""

from __future__ import annotations

import json
from typing import Any


def _deep_str_map(raw: dict) -> dict:
    out = {}
    for k, v in raw.items():
        key = str(k)
        if isinstance(v, dict):
            out[key] = _deep_str_map(v)
        elif isinstance(v, list):
            out[key] = [_deep_str_map(i) if isinstance(i, dict) else i for i in v]
        else:
            out[key] = v
    return out


def _normalize_type(raw: str | None) -> str:
    t = (raw or "").strip()
    lower = t.lower()
    mapping = {
        "div": "Column",
        "container": "Column",
        "box": "Column",
        "view": "Column",
        "page": "Column",
        "stack": "Column",
        "layout": "Column",
        "column": "Column",
        "vstack": "Column",
        "p": "TextBlock",
        "h1": "TextBlock",
        "h2": "TextBlock",
        "h3": "TextBlock",
        "span": "TextBlock",
        "label": "TextBlock",
        "text": "TextBlock",
        "textblock": "TextBlock",
        "heading": "TextBlock",
        "title": "TextBlock",
        "todo": "TodoList",
        "todolist": "TodoList",
        "todo_list": "TodoList",
        "tasks": "TodoList",
        "tasklist": "TodoList",
        "checklist": "Checklist",
        "check_list": "Checklist",
        "table": "SimpleTable",
        "simpletable": "SimpleTable",
        "grid": "SimpleTable",
        "note": "NoteEmbed",
        "noteembed": "NoteEmbed",
    }
    if lower in mapping:
        return mapping[lower]
    if t in {"Column", "TextBlock", "TodoList", "Checklist", "SimpleTable", "NoteEmbed"}:
        return t
    return t[:1].upper() + t[1:] if t else "Column"


def _extract_checklist_items(node: dict) -> list[dict]:
    raw = node.get("items") or node.get("children") or node.get("tasks") or node.get("todos")
    if not isinstance(raw, list):
        return []
    items = []
    for index, item in enumerate(raw):
        if isinstance(item, str):
            items.append({"id": str(index + 1), "text": item, "done": False})
        elif isinstance(item, dict):
            m = _deep_str_map(item)
            text = m.get("text") or m.get("title") or m.get("label") or m.get("name")
            if not text:
                continue
            done = bool(m.get("done") or m.get("checked") or m.get("completed") or False)
            item_id = str(m.get("id") or index + 1)
            items.append({"id": item_id, "text": str(text), "done": done})
    return items


def _normalize_node(node: dict, path: str, extracted: dict, fixes: list) -> dict:
    n = _deep_str_map(node)
    n["type"] = _normalize_type(n.get("type"))

    for wrong in ("childeren", "childs", "child", "elements"):
        if wrong in n and "children" not in n:
            n["children"] = n.pop(wrong)
            fixes.append(f"{path}: {wrong} → children")

    t = n.get("type", "")
    props = dict(n.get("props") or {})

    if t == "TextBlock":
        for key in ("text", "title", "content", "label", "value"):
            if key in n and str(n[key]).strip():
                if not props.get("text") and not props.get("binding"):
                    props["text"] = str(n[key])
                    fixes.append(f"{path} TextBlock: 顶层 {key} → props.text")
                n.pop(key, None)
        static_text = str(props.get("text") or "").strip()
        if static_text and not props.get("binding"):
            binding_key = "intro" if "children[0]" in path or path == "root.children[0]" else f"text_{path.replace('.', '_')}"
            extracted[binding_key] = {"kind": "text", "text": static_text}
            props.pop("text", None)
            props["binding"] = binding_key
            fixes.append(f"{path} TextBlock: 静态 text → data_bindings.{binding_key}")
        if props:
            n["props"] = props

    elif t in ("TodoList", "Checklist"):
        if not props.get("binding"):
            binding_key = "todos"
            items = _extract_checklist_items(n)
            if items:
                extracted[binding_key] = {"kind": "checklist", "items": items}
                props["binding"] = binding_key
                fixes.append(f"{path} {t}: 内联待办 → data_bindings.{binding_key}")
            else:
                props["binding"] = binding_key
            n.clear()
            n.update({"type": t, "props": props})
        for k in ("items", "children", "tasks", "todos", "headers", "rows"):
            n.pop(k, None)

    elif t == "Column":
        children = n.get("children") or []
        if isinstance(children, list):
            n["children"] = [
                _normalize_node(c, f"{path}.children[{i}]", extracted, fixes)
                for i, c in enumerate(children)
                if isinstance(c, dict)
            ]

    return n


def normalize_page(schema_input: Any, bindings_input: Any | None = None) -> tuple[dict, dict, list[str]]:
    fixes: list[str] = []
    extracted: dict = {}

    if isinstance(schema_input, str):
        schema = json.loads(schema_input)
    elif isinstance(schema_input, dict):
        schema = _deep_str_map(schema_input)
    else:
        schema = {"version": 1, "root": {"type": "Column", "children": []}}

    if bindings_input is None:
        bindings = {}
    elif isinstance(bindings_input, str):
        bindings = json.loads(bindings_input) if bindings_input.strip() else {}
    else:
        bindings = _deep_str_map(bindings_input)

    if "root" not in schema:
        fixes.append("补全 schema_json.root")
        if schema.get("type"):
            root = schema
        elif schema.get("children") is not None:
            root = {"type": "Column", "children": schema["children"]}
        else:
            root = {"type": "Column", "children": []}
        schema = {"version": 1, "root": root}
    else:
        schema["version"] = 1

    schema["root"] = _normalize_node(schema["root"], "root", extracted, fixes)
    bindings = {**bindings, **extracted}
    return schema, bindings, fixes
