"""自定义页面 schema / data_bindings 校验（v1）。"""

from __future__ import annotations

import json
from typing import Any

ALLOWED_NODE_TYPES = frozenset(
    {"Column", "TextBlock", "TodoList", "Checklist", "SimpleTable", "NoteEmbed"}
)
BINDING_COMPONENTS = frozenset({"TodoList", "Checklist", "SimpleTable", "NoteEmbed"})
ALLOWED_BINDING_KINDS = frozenset({"checklist", "note", "table", "text"})


def _parse_json(value: Any, field: str) -> tuple[dict | None, str | None]:
    if value is None:
        return {}, None
    if isinstance(value, dict):
        return value, None
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return None, f"{field} 不是合法 JSON"
        if not isinstance(parsed, dict):
            return None, f"{field} 必须是 JSON 对象"
        return parsed, None
    return None, f"{field} 类型无效"


def _validate_node(node: Any, path: str) -> str | None:
    if not isinstance(node, dict):
        return f"{path} 必须是对象"
    node_type = node.get("type")
    if node_type not in ALLOWED_NODE_TYPES:
        return f"{path}.type 未知: {node_type}"
    props = node.get("props")
    if props is not None and not isinstance(props, dict):
        return f"{path}.props 必须是对象"

    if node_type == "Column":
        children = node.get("children")
        if children is None:
            return None
        if not isinstance(children, list):
            return f"{path}.children 必须是数组"
        for i, child in enumerate(children):
            err = _validate_node(child, f"{path}.children[{i}]")
            if err:
                return err
        return None

    props = props or {}
    if node_type == "TextBlock":
        if not props.get("text") and not props.get("binding"):
            return f"{path} TextBlock 需要 text 或 binding"
        return None

    binding = props.get("binding")
    if node_type in BINDING_COMPONENTS and not binding:
        return f"{path} {node_type} 需要 props.binding"
    return None


def validate_schema_json(value: Any) -> tuple[dict | None, str | None]:
    schema, err = _parse_json(value, "schema_json")
    if err:
        return None, err
    if not schema:
        return {"version": 1, "root": {"type": "Column", "children": []}}, None
    if schema.get("version") != 1:
        return None, "schema_json.version 必须为 1"
    root = schema.get("root")
    if root is None:
        return None, "schema_json 缺少 root"
    node_err = _validate_node(root, "root")
    if node_err:
        return None, node_err
    return schema, None


def validate_data_bindings(value: Any) -> tuple[dict | None, str | None]:
    bindings, err = _parse_json(value, "data_bindings")
    if err:
        return None, err
    if not bindings:
        return {}, None
    for key, binding in bindings.items():
        if not isinstance(binding, dict):
            return None, f"data_bindings.{key} 必须是对象"
        kind = binding.get("kind")
        if kind not in ALLOWED_BINDING_KINDS:
            return None, f"data_bindings.{key}.kind 无效: {kind}"
        if kind == "checklist":
            items = binding.get("items")
            if items is not None and not isinstance(items, list):
                return None, f"data_bindings.{key}.items 必须是数组"
        elif kind == "note":
            if not binding.get("note_id"):
                return None, f"data_bindings.{key} 缺少 note_id"
        elif kind == "table":
            headers = binding.get("headers")
            rows = binding.get("rows")
            if headers is not None and not isinstance(headers, list):
                return None, f"data_bindings.{key}.headers 必须是数组"
            if rows is not None and not isinstance(rows, list):
                return None, f"data_bindings.{key}.rows 必须是数组"
        elif kind == "text":
            if binding.get("text") is None and binding.get("value") is None:
                return None, f"data_bindings.{key} 缺少 text"
    return bindings, None


def schema_to_storage(schema: dict) -> str:
    return json.dumps(schema, ensure_ascii=False)


def bindings_to_storage(bindings: dict) -> str:
    return json.dumps(bindings, ensure_ascii=False)
