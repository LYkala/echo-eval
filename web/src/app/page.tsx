"use client";

import dynamic from "next/dynamic";

const ConsoleClient = dynamic(
  () => import("@/components/console-client").then((m) => m.ConsoleClient),
  {
    ssr: false,
    loading: () => (
      <div className="space-y-4 rounded-lg border bg-card p-6 text-sm">
        <p className="font-medium">正在加载控制台脚本…</p>
        <p className="text-muted-foreground">
          若超过 5 秒仍无界面，请打开不依赖前端脚本的检查页：
          <a className="mx-1 text-primary underline" href="/check">
            /check
          </a>
          ，并确认地址是 <code>http://127.0.0.1:47391</code>
        </p>
        <p className="text-muted-foreground">
          也可在 Bash 测试：
          <code className="ml-1">curl http://127.0.0.1:47391/api/ping</code>
        </p>
      </div>
    ),
  }
);

export default function HomePage() {
  return <ConsoleClient />;
}
