"""配置加载。"""

from pathlib import Path

import yaml

DEFAULT_CONFIG_PATHS = (
    Path("config.yaml"),
    Path("server/config.yaml"),
    Path(__file__).resolve().parent.parent / "config.yaml",
)


def load_config(config_path: str | None = None) -> dict:
    if config_path:
        path = Path(config_path)
    else:
        path = next((p for p in DEFAULT_CONFIG_PATHS if p.is_file()), None)
        if path is None:
            raise FileNotFoundError(
                "未找到 config.yaml，请复制 config.example.yaml 并填写配置"
            )

    with path.open(encoding="utf-8") as f:
        return yaml.safe_load(f)
