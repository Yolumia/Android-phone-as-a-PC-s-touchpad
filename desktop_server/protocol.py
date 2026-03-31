from __future__ import annotations

import json
import secrets
import socket
import time
from typing import Any, Dict

PROTOCOL_VERSION = 1

PAIR_REQUEST = "pair_request"
PAIR_ACK = "pair_ack"
HEARTBEAT = "heartbeat"
HEARTBEAT_ACK = "heartbeat_ack"
CLICK = "click"
MOVE = "move"
DRAG_START = "drag_start"
DRAG_MOVE = "drag_move"
DRAG_END = "drag_end"
SCROLL = "scroll"
ZOOM = "zoom"
GESTURE = "gesture"
ERROR = "error"
SESSION_RESET = "session_reset"


def generate_token() -> str:
    return secrets.token_urlsafe(18)


def generate_session_id() -> str:
    return secrets.token_urlsafe(16)


def encode_message(payload: Dict[str, Any]) -> bytes:
    return json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def decode_message(raw_payload: bytes) -> Dict[str, Any]:
    return json.loads(raw_payload.decode("utf-8"))


def build_pairing_payload(host_ip: str, port: int, server_name: str, token: str) -> str:
    payload = {
        "version": PROTOCOL_VERSION,
        "server_ip": host_ip,
        "port": port,
        "token": token,
        "server_name": server_name,
        "issued_at": int(time.time() * 1000),
    }
    return json.dumps(payload, ensure_ascii=False)


def detect_primary_ip() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()

