# MotoMouse

MotoMouse 是一个局域网内的手机触摸板方案：

- **Android App**：用摄像头扫描电脑端二维码，完成 UDP 配对，并将手机变成 Windows 触摸板。
- **Python Desktop Server**：在 Windows 上弹出二维码、监听 UDP 控制消息、把手势映射成鼠标/滚轮/快捷键操作。

## 当前实现

### Android 端
- CameraX + ML Kit 扫描二维码
- 保存配对信息，下次启动自动重连
- UDP 心跳保活与自动重连
- 重连失败超过 10 次后清除配对并回到扫码页
- 单指移动 / 左键点击
- 单击后再次按住并移动触发拖动
- 双指轻点右键
- 双指滑动滚轮 / 横向滚动
- 双指捏合缩放（映射为 `Ctrl + 滚轮`）
- 三指左右滑切换应用
- 四指左右滑切换虚拟桌面

### Windows Python 端
- Tkinter 图形界面
- 启动后生成二维码
- 可修改 UDP 端口
- 可调整鼠标移动速率
- 自动刷新断连后的二维码
- 退出按钮

## 项目结构

- `app/`：Android 客户端
- `desktop_server/`：Windows Python 桌面端

## Windows 桌面端运行

先进入项目根目录，然后执行：

```powershell
cd D:\Android_project\MotoMouse
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r .\desktop_server\requirements.txt
python -m desktop_server.main
```

> 如果系统拦截 UDP 或鼠标控制，请允许 Windows 防火墙和辅助功能相关权限。

## Android 端构建

```powershell
cd D:\Android_project\MotoMouse
.\gradlew.bat :app:assembleDebug
```

## 手势映射

- 单指移动 → 鼠标移动
- 单指轻点 → 左键单击
- 轻点一次后再次按住移动 → 左键拖动
- 双指轻点 → 右键
- 双指拖动 → 滚轮 / 横向滚动
- 双指捏合 → `Ctrl + 滚轮`
- 三指左右滑 → `Alt + Tab` / `Alt + Shift + Tab`
- 四指左右滑 → `Ctrl + Win + ←/→`

## 协议说明

二维码中保存的是 JSON：

```json
{
  "version": 1,
  "server_ip": "192.168.1.10",
  "port": 50555,
  "token": "...",
  "server_name": "My PC",
  "issued_at": 1710000000000
}
```

Android 端扫描后向该地址发送 `pair_request`，成功后通过 UDP 持续发送 `move`、`click`、`scroll`、`zoom`、`gesture` 与 `heartbeat`。

## 建议联调步骤

1. 在 Windows 上运行 `python -m desktop_server.main`
2. 打开 Android App，允许摄像头权限
3. 扫描桌面端二维码
4. 在手机触摸板区域测试：移动、单击、拖动、双指滚动、三指/四指切换
5. 断开 Wi‑Fi 或关闭桌面端，观察自动重连和重新配对逻辑

