from __future__ import annotations

import tkinter as tk
from tkinter import messagebox, ttk

import qrcode
from PIL import Image, ImageTk

from .actions import InputController
from .config import DesktopConfig
from .protocol import detect_primary_ip
from .server import DesktopServer


class MotoMouseDesktopApp:
    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("MotoMouse Desktop")
        self.root.geometry("560x760")
        self.root.minsize(520, 720)

        self.config_model = DesktopConfig.load()
        self.input_controller = InputController(pointer_speed=self.config_model.pointer_speed)
        self._qr_photo: ImageTk.PhotoImage | None = None

        self.status_var = tk.StringVar(value="准备启动桌面端服务…")
        self.port_var = tk.StringVar(value=str(self.config_model.port))
        self.speed_var = tk.DoubleVar(value=self.config_model.pointer_speed)
        self.speed_text_var = tk.StringVar(value=f"{self.config_model.pointer_speed:.2f}")
        self.local_ip_var = tk.StringVar(value=detect_primary_ip())

        self.server = DesktopServer(
            config=self.config_model,
            input_controller=self.input_controller,
            on_status=self._schedule_status_update,
            on_pairing_payload=self._schedule_qr_update,
        )

        self._build_ui()
        self.root.protocol("WM_DELETE_WINDOW", self.on_exit)

    def run(self) -> None:
        self.server.start()
        self.root.mainloop()

    def _build_ui(self) -> None:
        container = ttk.Frame(self.root, padding=18)
        container.pack(fill=tk.BOTH, expand=True)

        title = ttk.Label(container, text="MotoMouse Desktop", font=("Segoe UI", 19, "bold"))
        title.pack(anchor=tk.W)
        subtitle = ttk.Label(
            container,
            text="运行后会生成二维码，安卓端扫码即可通过 UDP 配对并将手机当作 Windows 触摸板使用。",
            wraplength=500,
        )
        subtitle.pack(anchor=tk.W, pady=(6, 14))

        status_frame = ttk.LabelFrame(container, text="运行状态", padding=12)
        status_frame.pack(fill=tk.X)
        ttk.Label(status_frame, textvariable=self.status_var, wraplength=500).pack(anchor=tk.W)
        ttk.Label(status_frame, textvariable=self.local_ip_var).pack(anchor=tk.W, pady=(8, 0))

        config_frame = ttk.LabelFrame(container, text="连接与灵敏度", padding=12)
        config_frame.pack(fill=tk.X, pady=(14, 0))

        port_row = ttk.Frame(config_frame)
        port_row.pack(fill=tk.X)
        ttk.Label(port_row, text="UDP 端口：", width=12).pack(side=tk.LEFT)
        ttk.Entry(port_row, textvariable=self.port_var, width=12).pack(side=tk.LEFT)
        ttk.Button(port_row, text="应用端口", command=self.apply_port).pack(side=tk.LEFT, padx=(12, 0))

        speed_row = ttk.Frame(config_frame)
        speed_row.pack(fill=tk.X, pady=(14, 0))
        ttk.Label(speed_row, text="鼠标速率：", width=12).pack(side=tk.LEFT)
        ttk.Scale(
            speed_row,
            from_=0.4,
            to=3.0,
            variable=self.speed_var,
            orient=tk.HORIZONTAL,
            command=self.on_speed_change,
        ).pack(side=tk.LEFT, fill=tk.X, expand=True)
        ttk.Label(speed_row, textvariable=self.speed_text_var, width=8).pack(side=tk.LEFT, padx=(10, 0))

        qr_frame = ttk.LabelFrame(container, text="扫码二维码", padding=12)
        qr_frame.pack(fill=tk.BOTH, expand=True, pady=(14, 0))
        self.qr_label = ttk.Label(qr_frame)
        self.qr_label.pack(pady=(4, 8))
        ttk.Label(
            qr_frame,
            text="如果断连较久，桌面端会自动刷新二维码。安卓端重连失败 10 次后，也会回到扫码界面。",
            wraplength=500,
        ).pack(anchor=tk.W)

        button_row = ttk.Frame(container)
        button_row.pack(fill=tk.X, pady=(14, 0))
        ttk.Button(button_row, text="刷新二维码", command=self.refresh_pairing).pack(side=tk.LEFT)
        ttk.Button(button_row, text="退出程序", command=self.on_exit).pack(side=tk.RIGHT)

    def apply_port(self) -> None:
        try:
            port = int(self.port_var.get().strip())
        except ValueError:
            messagebox.showerror("端口错误", "请输入有效的数字端口。")
            return
        if not (1024 <= port <= 65535):
            messagebox.showerror("端口错误", "端口范围应在 1024 - 65535 之间。")
            return

        self.config_model.port = port
        self.config_model.save()
        try:
            self.server.restart(port=port)
        except OSError as error:
            messagebox.showerror("启动失败", f"无法绑定端口 {port}: {error}")
            return
        self.status_var.set(f"UDP 端口已切换为 {port}。")

    def on_speed_change(self, _value: str) -> None:
        speed = round(float(self.speed_var.get()), 2)
        self.speed_text_var.set(f"{speed:.2f}")
        self.server.update_pointer_speed(speed)
        self.config_model.pointer_speed = speed
        self.config_model.save()

    def refresh_pairing(self) -> None:
        self.server.refresh_pairing()

    def on_exit(self) -> None:
        self.server.stop()
        self.root.destroy()

    def _schedule_status_update(self, text: str) -> None:
        self.root.after(0, lambda: self._set_status(text))

    def _schedule_qr_update(self, payload: str) -> None:
        self.root.after(0, lambda: self._set_qr_image(payload))

    def _set_status(self, text: str) -> None:
        self.status_var.set(text)
        self.local_ip_var.set(f"当前局域网地址：{detect_primary_ip()}  ·  端口：{self.config_model.port}")

    def _set_qr_image(self, payload: str) -> None:
        qr_image = qrcode.make(payload).convert("RGB")
        qr_image = qr_image.resize((360, 360), Image.Resampling.NEAREST)
        self._qr_photo = ImageTk.PhotoImage(qr_image)
        self.qr_label.configure(image=self._qr_photo)

