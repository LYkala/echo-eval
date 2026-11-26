"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { cn } from "@/lib/utils";

type NavChild = { href: string; label: string };
type NavItem =
  | { href: string; label: string; children?: undefined }
  | { href?: undefined; label: string; children: NavChild[] };

const NAV: NavItem[] = [
  { href: "/env", label: "环境检查" },
  {
    label: "数据集管理",
    children: [
      { href: "/datasets", label: "数据集" },
      { href: "/datasets/tts", label: "预合成语音" },
      { href: "/datasets/calibrate", label: "预置校准" },
    ],
  },
  { href: "/run", label: "开始测试" },
  { href: "/runs", label: "任务管理" },
];

function isActive(pathname: string, href: string): boolean {
  if (href === "/runs") return pathname === "/runs" || pathname.startsWith("/runs/");
  return pathname === href || pathname.startsWith(href + "/");
}

function groupOpen(pathname: string, children: NavChild[]): boolean {
  return children.some((c) => isActive(pathname, c.href));
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname() || "/";
  const [mobileOpen, setMobileOpen] = useState(false);
  const [datasetsOpen, setDatasetsOpen] = useState(true);

  return (
    <div className="flex min-h-screen bg-background text-foreground">
      {/* Mobile overlay */}
      {mobileOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-30 bg-black/30 lg:hidden"
          aria-label="关闭菜单"
          onClick={() => setMobileOpen(false)}
        />
      ) : null}

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 flex w-56 flex-col border-r border-border bg-card transition-transform lg:static lg:translate-x-0",
          mobileOpen ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="flex h-14 items-center border-b border-border px-4">
          <Link
            href="/env"
            className="text-[15px] font-semibold tracking-tight text-foreground"
            onClick={() => setMobileOpen(false)}
          >
            秒控测试平台
          </Link>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-2 py-3 text-sm">
          {NAV.map((item) => {
            if (item.children) {
              const open = datasetsOpen || groupOpen(pathname, item.children);
              return (
                <div key={item.label} className="pt-1">
                  <button
                    type="button"
                    className="flex w-full items-center justify-between rounded-md px-2.5 py-2 text-left text-muted-foreground hover:bg-secondary/60 hover:text-foreground"
                    onClick={() => setDatasetsOpen((v) => !v)}
                  >
                    <span className="font-medium text-foreground/80">{item.label}</span>
                    <span className="text-xs text-muted-foreground">{open ? "▾" : "▸"}</span>
                  </button>
                  {open ? (
                    <ul className="ml-1 space-y-0.5 border-l border-border pl-2">
                      {item.children.map((child) => {
                        const active = isActive(pathname, child.href);
                        return (
                          <li key={child.href}>
                            <Link
                              href={child.href}
                              onClick={() => setMobileOpen(false)}
                              className={cn(
                                "block rounded-md px-2.5 py-1.5 transition-colors",
                                active
                                  ? "bg-primary/10 font-medium text-primary"
                                  : "text-muted-foreground hover:bg-secondary/60 hover:text-foreground"
                              )}
                            >
                              {child.label}
                            </Link>
                          </li>
                        );
                      })}
                    </ul>
                  ) : null}
                </div>
              );
            }
            const active = isActive(pathname, item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={() => setMobileOpen(false)}
                className={cn(
                  "block rounded-md px-2.5 py-2 transition-colors",
                  active
                    ? "bg-primary/10 font-medium text-primary"
                    : "text-muted-foreground hover:bg-secondary/60 hover:text-foreground"
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="border-t border-border px-3 py-3 text-[11px] leading-relaxed text-muted-foreground">
          本机控制台 · 端口 59371
          <br />
          <Link href="/check" className="underline-offset-2 hover:underline" onClick={() => setMobileOpen(false)}>
            无脚本检查
          </Link>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center gap-3 border-b border-border bg-card px-4 lg:px-6">
          <button
            type="button"
            className="rounded-md border border-border px-2 py-1 text-sm lg:hidden"
            onClick={() => setMobileOpen(true)}
          >
            菜单
          </button>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm text-muted-foreground">语音助手无线评测</p>
          </div>
        </header>
        <main className="flex-1 overflow-auto px-4 py-5 lg:px-8 lg:py-6">{children}</main>
      </div>
    </div>
  );
}
