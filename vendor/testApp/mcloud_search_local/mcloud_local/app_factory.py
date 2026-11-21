#!/usr/bin/env python3
"""Flask app factory for local mcloud search."""
from __future__ import annotations

from pathlib import Path

from flask import Flask

from .config import RESULTS, ROOT
from .routes import bp


def create_app() -> Flask:
    RESULTS.mkdir(parents=True, exist_ok=True)
    app = Flask(
        __name__,
        template_folder=str(ROOT / "templates"),
        static_folder=str(ROOT / "static"),
        static_url_path="/static",
    )
    app.config["JSON_AS_ASCII"] = False
    app.register_blueprint(bp)
    return app
