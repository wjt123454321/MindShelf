"""通用响应与错误处理。"""

from flask import jsonify


def ok(data, status=200, meta=None):
    body = {"data": data}
    if meta is not None:
        body["meta"] = meta
    return jsonify(body), status


def err(code: str, message: str, status: int = 400):
    return jsonify({"error": {"code": code, "message": message}}), status
