"""注册 Blueprint。"""

from flask import Flask

from app.routes.ai import bp as ai_bp
from app.routes.auth import bp as auth_bp
from app.routes.conversations import bp as conversations_bp
from app.routes.knowledge_bases import bp as kb_bp
from app.routes.notes import bp as notes_bp


def register_blueprints(app: Flask) -> None:
    for bp in (auth_bp, notes_bp, kb_bp, conversations_bp, ai_bp):
        app.register_blueprint(bp)
