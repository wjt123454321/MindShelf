"""Flask 应用工厂。"""

import logging
from pathlib import Path

from flask import Flask, request
from flask_cors import CORS

from app.config import load_config
from app.extensions import db
from app.logging_setup import setup_logging
from app.routes import register_blueprints

logger = logging.getLogger(__name__)


def create_app(config_path: str | None = None) -> Flask:
    app = Flask(__name__)
    cfg = load_config(config_path)
    log_file = setup_logging(cfg)
    logger.info("日志已初始化，文件: %s", log_file)

    data_dir = Path(__file__).resolve().parent.parent / "data"
    data_dir.mkdir(exist_ok=True)

    db_url = cfg["database"]["url"]
    if db_url.startswith("sqlite:///") and not db_url.startswith("sqlite:////"):
        rel = db_url.replace("sqlite:///", "")
        db_path = Path(__file__).resolve().parent.parent / rel
        db_path.parent.mkdir(parents=True, exist_ok=True)
        db_url = f"sqlite:///{db_path.as_posix()}"

    app.config.update(
        SQLALCHEMY_DATABASE_URI=db_url,
        SQLALCHEMY_TRACK_MODIFICATIONS=False,
        MINDSHELF_CONFIG=cfg,
    )

    CORS(app, resources={r"/api/*": {"origins": "*"}})
    db.init_app(app)
    register_blueprints(app)
    _register_request_logging(app)

    with app.app_context():
        from app import models  # noqa: F401

        db.create_all()
        _ensure_schema(db)
        _start_scheduler(app)

    return app


def _register_request_logging(app: Flask) -> None:
    @app.after_request
    def _log_request(response):
        if request.path.startswith("/api/"):
            logger.info("%s %s %s", request.method, request.path, response.status_code)
        return response


def _start_scheduler(app: Flask) -> None:
    """每日清理过期回收站条目。"""
    try:
        from apscheduler.schedulers.background import BackgroundScheduler

        from app.services.trash import purge_expired_trash

        scheduler = BackgroundScheduler(daemon=True)

        def _purge():
            with app.app_context():
                purge_expired_trash()

        scheduler.add_job(_purge, "interval", hours=24, id="purge_trash")
        scheduler.start()
        app.config["TRASH_SCHEDULER"] = scheduler
    except ImportError:
        pass


def _ensure_schema(database) -> None:
    """为已有 SQLite 库补充新列（create_all 不会 alter 旧表）。"""
    from sqlalchemy import inspect, text

    inspector = inspect(database.engine)
    if "messages" not in inspector.get_table_names():
        return
    columns = {col["name"] for col in inspector.get_columns("messages")}
    if "reasoning" not in columns:
        with database.engine.connect() as conn:
            conn.execute(text("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''"))
            conn.commit()
    if "segments_json" not in columns:
        with database.engine.connect() as conn:
            conn.execute(text("ALTER TABLE messages ADD COLUMN segments_json TEXT NOT NULL DEFAULT '[]'"))
            conn.commit()
    if "pending_tool_confirmations" in inspector.get_table_names():
        pending_cols = {col["name"] for col in inspector.get_columns("pending_tool_confirmations")}
        pending_migrations = [
            ("resume_messages_json", "TEXT NOT NULL DEFAULT '[]'"),
            ("tool_call_id", "VARCHAR(64) NOT NULL DEFAULT ''"),
            ("partial_content", "TEXT NOT NULL DEFAULT ''"),
            ("partial_reasoning", "TEXT NOT NULL DEFAULT ''"),
            ("partial_segments_json", "TEXT NOT NULL DEFAULT '[]'"),
            ("user_message_id", "VARCHAR(36) NOT NULL DEFAULT ''"),
            ("resume_round", "INTEGER NOT NULL DEFAULT 0"),
            ("executed", "BOOLEAN NOT NULL DEFAULT 0"),
            ("tool_result_json", "TEXT"),
        ]
        for col_name, col_def in pending_migrations:
            if col_name not in pending_cols:
                with database.engine.connect() as conn:
                    conn.execute(
                        text(f"ALTER TABLE pending_tool_confirmations ADD COLUMN {col_name} {col_def}")
                    )
                    conn.commit()
