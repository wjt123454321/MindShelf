"""认证路由。"""

import re

from flask import Blueprint, g, request

from app.extensions import db
from app.models import RefreshToken, User
from app.services.mail import (
    generate_code,
    last_code_sent_at,
    save_code,
    send_verification_email,
    verify_code,
)
from app.utils.auth import (
    auth_required,
    create_access_token,
    create_refresh_token,
    hash_password,
    new_id,
    now_ms,
    verify_password,
)
from app.utils.responses import err, ok

bp = Blueprint("auth", __name__, url_prefix="/api/v1/auth")

EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def _user_dict(user: User) -> dict:
    return {
        "id": user.id,
        "email": user.email,
        "username": user.username,
        "created_at": user.created_at,
    }


def _issue_tokens(user: User) -> dict:
    access_token, expires_in = create_access_token(user.id)
    refresh_raw, refresh_ttl, token_hash = create_refresh_token(user.id)
    db.session.add(
        RefreshToken(
            user_id=user.id,
            token_hash=token_hash,
            expires_at=now_ms() + refresh_ttl * 1000,
        )
    )
    db.session.commit()
    return {
        "user": _user_dict(user),
        "access_token": access_token,
        "refresh_token": refresh_raw,
        "expires_in": expires_in,
    }


@bp.post("/register")
def register():
    body = request.get_json(silent=True) or {}
    email = (body.get("email") or "").strip().lower()
    password = body.get("password") or ""
    username = (body.get("username") or "").strip() or None
    code = (body.get("code") or "").strip()

    if not EMAIL_RE.match(email):
        return err("INVALID_REQUEST", "邮箱格式无效")
    if not code:
        return err("INVALID_REQUEST", "验证码不能为空")
    if not verify_code(email, code, "register"):
        return err("UNAUTHORIZED", "验证码无效或已过期", 401)
    if len(password) < 8:
        return err("INVALID_REQUEST", "密码至少 8 位")

    if User.query.filter_by(email=email).first():
        return err("EMAIL_EXISTS", "邮箱已注册", 409)
    if username and User.query.filter_by(username=username).first():
        return err("USERNAME_EXISTS", "用户名已占用", 409)

    user = User(
        id=body.get("id") or new_id(),
        email=email,
        username=username,
        password_hash=hash_password(password),
    )
    db.session.add(user)
    db.session.commit()
    return ok(_issue_tokens(user), 201)


@bp.post("/send-code")
def send_code():
    body = request.get_json(silent=True) or {}
    email = (body.get("email") or "").strip().lower()
    purpose = body.get("purpose") or "login"

    if not EMAIL_RE.match(email):
        return err("INVALID_REQUEST", "邮箱格式无效")

    last_sent = last_code_sent_at(email, purpose)
    if last_sent and now_ms() - last_sent < 60_000:
        retry_after = 60 - (now_ms() - last_sent) // 1000
        return err("RATE_LIMITED", f"请 {retry_after} 秒后重试", 429)

    code = generate_code()
    try:
        send_verification_email(email, code, purpose)
    except Exception:
        return err("INTERNAL_ERROR", "验证码发送失败", 500)

    expires_in = save_code(email, code, purpose)
    return ok({"expires_in": expires_in, "retry_after": 60})


@bp.post("/login")
def login():
    body = request.get_json(silent=True) or {}
    account = (body.get("account") or "").strip()
    password = body.get("password") or ""

    if not account or not password:
        return err("INVALID_REQUEST", "账号和密码不能为空")

    user = User.query.filter(
        (User.email == account.lower()) | (User.username == account)
    ).first()
    if user is None or not user.password_hash or not verify_password(password, user.password_hash):
        return err("UNAUTHORIZED", "账号或密码错误", 401)

    return ok(_issue_tokens(user))


@bp.post("/login/code")
def login_with_code():
    body = request.get_json(silent=True) or {}
    email = (body.get("email") or "").strip().lower()
    code = (body.get("code") or "").strip()

    if not EMAIL_RE.match(email) or not code:
        return err("INVALID_REQUEST", "邮箱或验证码无效")
    if not verify_code(email, code, "login"):
        return err("UNAUTHORIZED", "验证码无效或已过期", 401)

    user = User.query.filter_by(email=email).first()
    if user is None:
        user = User(email=email, password_hash=None)
        db.session.add(user)
        db.session.commit()

    return ok(_issue_tokens(user))


@bp.post("/refresh")
def refresh():
    body = request.get_json(silent=True) or {}
    raw = body.get("refresh_token") or ""
    if not raw:
        return err("INVALID_REQUEST", "缺少 refresh_token", 400)

    import hashlib

    token_hash = hashlib.sha256(raw.encode()).hexdigest()
    row = RefreshToken.query.filter_by(token_hash=token_hash, revoked=False).first()
    if row is None or row.expires_at < now_ms():
        return err("UNAUTHORIZED", "未登录或 token 无效", 401)

    row.revoked = True
    user = User.query.get(row.user_id)
    access_token, expires_in = create_access_token(user.id)
    refresh_raw, refresh_ttl, new_hash = create_refresh_token(user.id)
    db.session.add(
        RefreshToken(
            user_id=user.id,
            token_hash=new_hash,
            expires_at=now_ms() + refresh_ttl * 1000,
        )
    )
    db.session.commit()
    return ok(
        {
            "access_token": access_token,
            "refresh_token": refresh_raw,
            "expires_in": expires_in,
        }
    )


@bp.get("/me")
@auth_required
def me():
    user = User.query.get(g.user_id)
    if user is None:
        return err("UNAUTHORIZED", "未登录或 token 无效", 401)
    return ok(_user_dict(user))


@bp.post("/logout")
@auth_required
def logout():
    body = request.get_json(silent=True) or {}
    raw = body.get("refresh_token") or ""
    if raw:
        import hashlib

        token_hash = hashlib.sha256(raw.encode()).hexdigest()
        row = RefreshToken.query.filter_by(token_hash=token_hash, user_id=g.user_id).first()
        if row:
            row.revoked = True
            db.session.commit()
    return "", 204
