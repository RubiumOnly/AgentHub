import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "AgentHub · 企业级多 Agent 协作工作台",
  description: "基于 Java 17 + Spring Boot 3 与 Next.js 14 的多智能体研发平台",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" className="dark">
      <body className="antialiased min-h-screen bg-[#090d16] text-slate-100 selection:bg-indigo-500/30">
        {children}
      </body>
    </html>
  );
}
