"""网盘搜索过程 — mounted under 9526 as /pan-search."""
from __future__ import annotations

from .blueprint import bp, register_netdisk

__all__ = ["bp", "register_netdisk"]
