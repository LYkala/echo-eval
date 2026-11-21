# 移动网盘搜索 · 本地独立项目

USB 直连本机 ADB 跑「移动网盘」E2E，**不依赖 9526 / 跳板 / StartAgent**。

## 给别人用（对方只需 Python）

```bat
python pack_dist.py
```

生成 `dist/mcloud_search_local_*.zip`（内含 **adb** + **ADB Keyboard**）。对方：

1. 解压 → 手机 USB 调试授权 → 双击 `start.bat`
2. 脚本自动装/启用 ADB Keyboard，缺依赖会 `pip install`
3. 打开 http://127.0.0.1:9530/ 刷新设备后开跑

详见 `使用说明.txt`。请用 `pack_dist.py` 打包（会实拷 platform-tools，不是 junction）。

## 本机开发启动

```bat
pip install -r requirements.txt
start.bat
```

## 目录

| 路径 | 说明 |
|---|---|
| `data/` | E2E 用例 + ES 文件名→fid |
| `results/` | 运行结果 |
| `mcloud_local/` | 自动化与 API |
| `pack_dist.py` | 打可分发 zip |

## 对方环境要求

- Windows + Python 3.10+
- `adb` 在 PATH（或自备 `tools/platform-tools/adb.exe`）
- 手机：中国移动云盘；建议 ADB Keyboard
