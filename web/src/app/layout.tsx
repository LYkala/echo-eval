import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "Echo Eval Console",
  description: "语音助手无线评测控制台 — 上传数据集、环境检查、开测与结果回看",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body className="font-sans">
        <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-4 pb-16 pt-6 sm:px-6">
          <header className="animate-rise mb-10 flex flex-wrap items-end justify-between gap-4 border-b border-border/70 pb-5">
            <div>
              <p
                className="text-3xl font-semibold tracking-tight text-foreground sm:text-4xl"
                style={{ fontFamily: "Georgia, 'Songti SC', 'Noto Serif SC', serif" }}
              >
                Echo Eval
              </p>
              <p className="mt-1 max-w-xl text-sm text-muted-foreground">
                PC1 现场控制台：上传用例 → 检查环境 → 播报开测 → 回看截帧与评判
              </p>
            </div>
            <nav className="flex gap-4 text-sm font-medium text-muted-foreground">
              <Link className="hover:text-foreground" href="/">
                工作台
              </Link>
              <Link className="hover:text-foreground" href="/runs">
                历史任务
              </Link>
            </nav>
          </header>
          <main className="flex-1">{children}</main>
        </div>
      </body>
    </html>
  );
}
