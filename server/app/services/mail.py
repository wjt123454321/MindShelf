"""SMTP 验证码发送。"""

import random
import smtplib
from email.mime.text import MIMEText

from flask import current_app

from app.extensions import db
from app.models import VerificationCode
from app.utils.auth import now_ms


def _smtp_cfg() -> dict:
    return current_app.config["MINDSHELF_CONFIG"]["smtp"]


def generate_code() -> str:
    return f"{random.randint(0, 999999):06d}"


def send_verification_email(email: str, code: str, purpose: str) -> None:
    cfg = _smtp_cfg()
    subject = "MindShelf 验证码"
    if purpose == "login":
        body = f"您的登录验证码为：{code}，5 分钟内有效。"
    elif purpose == "register":
        body = f"您的注册验证码为：{code}，5 分钟内有效。"
    else:
        body = f"您的验证码为：{code}，5 分钟内有效。"

    msg = MIMEText(body, "plain", "utf-8")
    msg["Subject"] = subject
    msg["From"] = cfg["user"]
    msg["To"] = email

    port = int(cfg.get("port", 465))
    with smtplib.SMTP_SSL(cfg["host"], port) as server:
        server.login(cfg["user"], cfg["password"])
        server.sendmail(cfg["user"], [email], msg.as_string())


def save_code(email: str, code: str, purpose: str) -> int:
    expires_in = 300
    expires_at = now_ms() + expires_in * 1000
    db.session.add(
        VerificationCode(
            email=email.lower(),
            code=code,
            purpose=purpose,
            expires_at=expires_at,
        )
    )
    db.session.commit()
    return expires_in


def verify_code(email: str, code: str, purpose: str) -> bool:
    row = (
        VerificationCode.query.filter_by(
            email=email.lower(), code=code, purpose=purpose
        )
        .order_by(VerificationCode.created_at.desc())
        .first()
    )
    if row is None or row.expires_at < now_ms():
        return False
    db.session.delete(row)
    db.session.commit()
    return True


def last_code_sent_at(email: str, purpose: str) -> int | None:
    row = (
        VerificationCode.query.filter_by(email=email.lower(), purpose=purpose)
        .order_by(VerificationCode.created_at.desc())
        .first()
    )
    return row.created_at if row else None
