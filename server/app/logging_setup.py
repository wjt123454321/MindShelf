"""应用日志：控制台 + 滚动文件。"""

import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path

DEFAULT_FORMAT = "%(asctime)s [%(levelname)s] %(name)s: %(message)s"
DEFAULT_DATEFMT = "%Y-%m-%d %H:%M:%S"


def setup_logging(cfg: dict) -> Path:
    log_cfg = cfg.get("logging") or {}
    level_name = str(log_cfg.get("level", "INFO")).upper()
    level = getattr(logging, level_name, logging.INFO)

    log_dir = Path(__file__).resolve().parent.parent / "logs"
    log_dir.mkdir(exist_ok=True)
    log_file = log_dir / str(log_cfg.get("filename", "mindshelf.log"))

    formatter = logging.Formatter(DEFAULT_FORMAT, datefmt=DEFAULT_DATEFMT)

    root = logging.getLogger()
    root.handlers.clear()
    root.setLevel(level)

    if log_cfg.get("console", True):
        console = logging.StreamHandler()
        console.setFormatter(formatter)
        root.addHandler(console)

    max_bytes = int(log_cfg.get("max_bytes", 5 * 1024 * 1024))
    backup_count = int(log_cfg.get("backup_count", 3))
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=max_bytes,
        backupCount=backup_count,
        encoding="utf-8",
    )
    file_handler.setFormatter(formatter)
    root.addHandler(file_handler)

    for name in ("apscheduler",):
        logging.getLogger(name).setLevel(level)

    # 访问日志由 after_request 统一记录，避免与 werkzeug 重复
    logging.getLogger("werkzeug").setLevel(logging.WARNING)

    return log_file
