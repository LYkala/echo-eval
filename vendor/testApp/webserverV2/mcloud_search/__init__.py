"""移动网盘搜索 — mounted under 9526 as /mcloud-search."""
from __future__ import annotations

from .blueprint import bp, register_mcloud

__all__ = ["bp", "register_mcloud"]
