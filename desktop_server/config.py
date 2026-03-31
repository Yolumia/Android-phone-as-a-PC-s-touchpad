from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path

CONFIG_PATH = Path.home() / ".motomouse_desktop.json"


@dataclass
class DesktopConfig:
    port: int = 50555
    pointer_speed: float = 1.35

    @classmethod
    def load(cls, path: Path = CONFIG_PATH) -> "DesktopConfig":
        if not path.exists():
            return cls()
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return cls()
        return cls(
            port=int(payload.get("port", 50555)),
            pointer_speed=float(payload.get("pointer_speed", 1.35)),
        )

    def save(self, path: Path = CONFIG_PATH) -> None:
        path.write_text(
            json.dumps(asdict(self), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )

