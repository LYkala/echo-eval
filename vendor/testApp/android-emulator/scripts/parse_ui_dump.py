#!/usr/bin/env python3
import re
import sys
from pathlib import Path

xml = Path(sys.argv[1]).read_text(encoding="utf-8")
pkg = sys.argv[2] if len(sys.argv) > 2 else "com.testappgui.voiceassistant"
for rid in ("status_text", "asr_text", "llm_text", "record_button", "demo_button"):
    m = re.search(
        rf'resource-id="{re.escape(pkg)}:id/{rid}"[^>]*text="([^"]*)"',
        xml,
    )
    if not m:
        m = re.search(
            rf'text="([^"]*)"[^>]*resource-id="{re.escape(pkg)}:id/{rid}"',
            xml,
        )
    print(f"{rid}: {m.group(1) if m else 'NOT FOUND'}")
