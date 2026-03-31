from __future__ import annotations

import socket
import threading
import time
from typing import Callable, Optional, Tuple

from .actions import InputController
from .config import DesktopConfig
from .protocol import (
    CLICK,
    DRAG_END,
    DRAG_MOVE,
    DRAG_START,
    ERROR,
    GESTURE,
    HEARTBEAT,
    HEARTBEAT_ACK,
    MOVE,
    PAIR_ACK,
    PAIR_REQUEST,
    SCROLL,
    SESSION_RESET,
    ZOOM,
    build_pairing_payload,
    decode_message,
    detect_primary_ip,
    encode_message,
    generate_session_id,
    generate_token,
)

StatusCallback = Callable[[str], None]
PairingCallback = Callable[[str], None]


class DesktopServer:
    def __init__(
        self,
        config: DesktopConfig,
        input_controller: InputController,
        on_status: StatusCallback,
        on_pairing_payload: PairingCallback,
    ) -> None:
        self.config = config
        self.input_controller = input_controller
        self._on_status = on_status
        self._on_pairing_payload = on_pairing_payload

        self._socket: Optional[socket.socket] = None
        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self._lock = threading.RLock()

        self._pair_token = ""
        self._session_id: Optional[str] = None
        self._client_addr: Optional[Tuple[str, int]] = None
        self._client_name: str = ""
        self._last_seen_monotonic: float = 0.0
        self._drag_active = False
        self._host_ip = detect_primary_ip()

    def start(self) -> None:
        with self._lock:
            if self._thread and self._thread.is_alive():
                return
            self._host_ip = detect_primary_ip()
            self._stop_event.clear()
            udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            udp_socket.bind(("0.0.0.0", self.config.port))
            udp_socket.settimeout(0.5)
            self._socket = udp_socket
            self.refresh_pairing(notify_status=False)
            self._thread = threading.Thread(target=self._serve_forever, name="MotoMouseServer", daemon=True)
            self._thread.start()
            self._on_status(f"已监听 {self._host_ip}:{self.config.port}，等待手机扫码。")

    def stop(self) -> None:
        with self._lock:
            self._stop_event.set()
            self._release_drag_if_needed()
            udp_socket = self._socket
            self._socket = None
            if udp_socket is not None:
                udp_socket.close()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=1.5)
        self._thread = None

    def restart(self, port: Optional[int] = None) -> None:
        if port is not None:
            self.config.port = int(port)
        self.stop()
        self.start()

    def refresh_pairing(self, notify_status: bool = True) -> None:
        with self._lock:
            self._pair_token = generate_token()
            self._session_id = None
            self._client_addr = None
            self._client_name = ""
            self._last_seen_monotonic = 0.0
            self._release_drag_if_needed()
            payload = build_pairing_payload(
                host_ip=self._host_ip,
                port=self.config.port,
                server_name=socket.gethostname(),
                token=self._pair_token,
            )
            self._on_pairing_payload(payload)
            if notify_status:
                self._on_status(f"二维码已刷新，请重新扫码连接 {self._host_ip}:{self.config.port}。")

    def update_pointer_speed(self, speed: float) -> None:
        self.config.pointer_speed = float(speed)
        self.input_controller.set_pointer_speed(speed)
        self._on_status(f"鼠标速度已调整为 {self.config.pointer_speed:.2f}。")

    def _serve_forever(self) -> None:
        while not self._stop_event.is_set():
            udp_socket = self._socket
            if udp_socket is None:
                return
            try:
                raw_data, client_addr = udp_socket.recvfrom(4096)
            except socket.timeout:
                self._check_session_timeout()
                continue
            except OSError:
                return

            try:
                payload = decode_message(raw_data)
                self._handle_message(payload, client_addr)
            except Exception as error:  # noqa: BLE001 - keep server alive on malformed input
                self._send_message(
                    {
                        "type": ERROR,
                        "message": f"Invalid payload: {error}",
                        "requires_repair": False,
                    },
                    client_addr,
                )
            finally:
                self._check_session_timeout()

    def _handle_message(self, payload: dict, client_addr: Tuple[str, int]) -> None:
        message_type = payload.get("type")
        if message_type == PAIR_REQUEST:
            self._handle_pair_request(payload, client_addr)
            return

        if not self._is_current_session(payload, client_addr):
            self._send_message(
                {
                    "type": ERROR,
                    "message": "会话已失效，请重新扫码配对。",
                    "requires_repair": True,
                },
                client_addr,
            )
            return

        self._last_seen_monotonic = time.monotonic()

        if message_type == HEARTBEAT:
            self._send_message({"type": HEARTBEAT_ACK}, client_addr)
        elif message_type == MOVE:
            self.input_controller.move_relative(float(payload.get("dx", 0.0)), float(payload.get("dy", 0.0)))
        elif message_type == CLICK:
            if payload.get("button") == "right":
                self.input_controller.right_click()
            else:
                self.input_controller.left_click()
        elif message_type == DRAG_START:
            self._drag_active = True
            self.input_controller.drag_start()
        elif message_type == DRAG_MOVE:
            self.input_controller.drag_move(float(payload.get("dx", 0.0)), float(payload.get("dy", 0.0)))
        elif message_type == DRAG_END:
            self._drag_active = False
            self.input_controller.drag_end()
        elif message_type == SCROLL:
            self.input_controller.scroll(int(payload.get("dx", 0)), int(payload.get("dy", 0)))
        elif message_type == ZOOM:
            self.input_controller.zoom(int(payload.get("steps", 0)))
        elif message_type == GESTURE:
            self.input_controller.trigger_gesture(str(payload.get("name", "")))

    def _handle_pair_request(self, payload: dict, client_addr: Tuple[str, int]) -> None:
        token = str(payload.get("token", ""))
        if token != self._pair_token:
            self._send_message(
                {
                    "type": SESSION_RESET,
                    "message": "二维码已过期，请重新扫码。",
                },
                client_addr,
            )
            return

        self._client_addr = client_addr
        self._client_name = str(payload.get("device_name", "Android Phone"))
        self._session_id = generate_session_id()
        self._last_seen_monotonic = time.monotonic()
        self._send_message(
            {
                "type": PAIR_ACK,
                "session_id": self._session_id,
                "server_name": socket.gethostname(),
                "heartbeat_interval_ms": 2000,
            },
            client_addr,
        )
        self._on_status(f"已连接：{self._client_name} ({client_addr[0]}:{client_addr[1]})")

    def _is_current_session(self, payload: dict, client_addr: Tuple[str, int]) -> bool:
        return (
            self._session_id is not None
            and self._client_addr == client_addr
            and str(payload.get("session_id", "")) == self._session_id
        )

    def _check_session_timeout(self) -> None:
        if not self._client_addr or not self._last_seen_monotonic:
            return
        if time.monotonic() - self._last_seen_monotonic < SESSION_TIMEOUT_SECONDS:
            return
        self._on_status("连接已断开，二维码已刷新，请重新扫码。")
        self.refresh_pairing(notify_status=False)

    def _send_message(self, payload: dict, client_addr: Tuple[str, int]) -> None:
        udp_socket = self._socket
        if udp_socket is None:
            return
        try:
            udp_socket.sendto(encode_message(payload), client_addr)
        except OSError:
            return

    def _release_drag_if_needed(self) -> None:
        if self._drag_active:
            self.input_controller.drag_end()
            self._drag_active = False


SESSION_TIMEOUT_SECONDS = 55

