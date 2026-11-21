"""本地移动网盘搜索（USB 直连）。"""
from __future__ import annotations

__all__ = ["create_app"]


def create_app():
    from .app_factory import create_app as _create

    return _create()
