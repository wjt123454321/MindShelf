"""JWT 与密码工具。"""

import hashlib
import secrets
import uuid
from datetime import datetime, timedelta, timezone

import bcrypt
import jwt
from flask import current_app, g, request


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()


def verify_password(password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(password.encode(), password_hash.encode())


def _jwt_cfg():
    return current_app.config["MINDSHELF_CONFIG"]["jwt"]


def create_access_token(user_id: str) -> tuple[str, int]:
    cfg = _jwt_cfg()
    expires_in = int(cfg.get("access_ttl", 900))
    payload = {
        "sub": user_id,
        "type": "access",
        "exp": datetime.now(timezone.utc) + timedelta(seconds=expires_in),
        "iat": datetime.now(timezone.utc),
    }
    token = jwt.encode(payload, cfg["secret"], algorithm="HS256")
    return token, expires_in


def create_refresh_token(user_id: str) -> tuple[str, int, str]:
    cfg = _jwt_cfg()
    ttl = int(cfg.get("refresh_ttl", 2592000))
    raw = secrets.token_urlsafe(48)
    token_hash = hashlib.sha256(raw.encode()).hexdigest()
    return raw, ttl, token_hash


def decode_access_token(token: str) -> dict:
    cfg = _jwt_cfg()
    return jwt.decode(token, cfg["secret"], algorithms=["HS256"])


def auth_required(f):
    from functools import wraps

    @wraps(f)
    def wrapper(*args, **kwargs):
        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            from app.utils.responses import err

            return err("UNAUTHORIZED", "未登录或 token 无效", 401)
        token = header[7:]
        try:
            payload = decode_access_token(token)
            if payload.get("type") != "access":
                raise jwt.InvalidTokenError("invalid type")
            g.user_id = payload["sub"]
        except jwt.PyJWTError:
            from app.utils.responses import err

            return err("UNAUTHORIZED", "未登录或 token 无效", 401)
        return f(*args, **kwargs)

    return wrapper


def new_id() -> str:
    return str(uuid.uuid4())


def now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)
