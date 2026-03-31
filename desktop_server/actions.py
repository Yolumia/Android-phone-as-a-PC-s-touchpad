from __future__ import annotations

import threading
from dataclasses import dataclass

import pyautogui

pyautogui.FAILSAFE = False
pyautogui.PAUSE = 0
if hasattr(pyautogui, "MINIMUM_DURATION"):
    pyautogui.MINIMUM_DURATION = 0


@dataclass
class InputController:
    pointer_speed: float = 1.35

    def __post_init__(self) -> None:
        self._lock = threading.Lock()

    def set_pointer_speed(self, speed: float) -> None:
        self.pointer_speed = max(0.1, min(speed, 4.0))

    def move_relative(self, dx: float, dy: float) -> None:
        scaled_dx = self._scaled_delta(dx)
        scaled_dy = self._scaled_delta(dy)
        if scaled_dx == 0 and scaled_dy == 0:
            return
        with self._lock:
            pyautogui.moveRel(scaled_dx, scaled_dy, duration=0, _pause=False)

    def left_click(self) -> None:
        with self._lock:
            pyautogui.click(button="left")

    def right_click(self) -> None:
        with self._lock:
            pyautogui.click(button="right")

    def drag_start(self) -> None:
        with self._lock:
            pyautogui.mouseDown(button="left")

    def drag_move(self, dx: float, dy: float) -> None:
        self.move_relative(dx, dy)

    def drag_end(self) -> None:
        with self._lock:
            pyautogui.mouseUp(button="left")

    def scroll(self, dx: int, dy: int) -> None:
        with self._lock:
            if dy:
                pyautogui.scroll(dy)
            if dx:
                try:
                    pyautogui.hscroll(dx)
                except Exception:
                    pyautogui.keyDown("shift")
                    try:
                        pyautogui.scroll(dx)
                    finally:
                        pyautogui.keyUp("shift")

    def zoom(self, steps: int) -> None:
        if steps == 0:
            return
        with self._lock:
            pyautogui.keyDown("ctrl")
            try:
                pyautogui.scroll(steps * 120)
            finally:
                pyautogui.keyUp("ctrl")

    def trigger_gesture(self, name: str) -> None:
        with self._lock:
            if name == "app_switch_next":
                pyautogui.hotkey("alt", "tab")
            elif name == "app_switch_previous":
                pyautogui.hotkey("alt", "shift", "tab")
            elif name == "desktop_next":
                pyautogui.hotkey("ctrl", "win", "right")
            elif name == "desktop_previous":
                pyautogui.hotkey("ctrl", "win", "left")

    def _scaled_delta(self, delta: float) -> int:
        scaled = int(round(delta * self.pointer_speed))
        if scaled == 0 and abs(delta) >= 0.2:
            return 1 if delta > 0 else -1
        return scaled

