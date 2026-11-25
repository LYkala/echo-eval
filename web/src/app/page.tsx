"use client";

import { ConsoleClient } from "@/components/console-client";

/**
 * Direct client import (no next/dynamic loading gate).
 * Previously dynamic() left users stuck on “正在加载控制台脚本…” when the
 * chunk failed or hung; /check still works as a no-JS fallback.
 */
export default function HomePage() {
  return <ConsoleClient />;
}
