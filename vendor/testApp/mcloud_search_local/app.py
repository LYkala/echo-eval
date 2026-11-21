#!/usr/bin/env python3
"""本地启动入口：移动网盘搜索（USB 直连）。

用法:
  python app.py
  python app.py --port 9530
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from mcloud_local import create_app  # noqa: E402
from mcloud_local.config import ADB_DEFAULT, DATA, RESULTS  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser(description="本地移动网盘搜索（USB ADB）")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=9530)
    ap.add_argument("--debug", action="store_true")
    args = ap.parse_args()

    DATA.mkdir(parents=True, exist_ok=True)
    RESULTS.mkdir(parents=True, exist_ok=True)
    app = create_app()
    print(f"ADB={ADB_DEFAULT}")
    print(f"DATA={DATA}")
    print(f"RESULTS={RESULTS}")
    print(f"Open http://{args.host}:{args.port}/")
    app.run(host=args.host, port=args.port, debug=args.debug, threaded=True)


if __name__ == "__main__":
    main()
