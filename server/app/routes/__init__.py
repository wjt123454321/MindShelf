"""注册 Blueprint。"""

from flask import Flask

from app.routes.ai import bp as ai_bp
from app.routes.auth import bp as auth_bp
from app.routes.conversations import bp as conversations_bp
from app.routes.knowledge_bases import bp as kb_bp
from app.routes.notes import bp as notes_bp
from app.routes.share import bp as share_bp
from app.routes.share import public_bp as public_share_bp
from app.routes.sync import bp as sync_bp
from app.routes.trash import bp as trash_bp


def register_blueprints(app: Flask) -> None:
    for bp in (
        auth_bp,
        notes_bp,
        kb_bp,
        conversations_bp,
        ai_bp,
        trash_bp,
        share_bp,
        sync_bp,
        public_share_bp,
    ):
        app.register_blueprint(bp)
