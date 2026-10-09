"use client";

import React, { ReactNode } from "react";

interface MetricCardProps {
  title: string;
  value: string | number;
  subtext?: string;
  icon: ReactNode;
  tag?: string;
  trend?: "up" | "down" | "neutral";
  trendText?: string;
  statusDotColor?: string;
  className?: string;
  onClick?: () => void;
}

export function MetricCard({
  title,
  value,
  subtext,
  icon,
  tag,
  trend,
  trendText,
  statusDotColor,
  className = "",
  onClick,
}: MetricCardProps) {
  return (
    <div
      onClick={onClick}
      className={`group relative overflow-hidden rounded-xl border border-zinc-800/80 bg-zinc-900/60 p-3.5 backdrop-blur-md transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-zinc-700/80 hover:shadow-lg hover:shadow-black/25 ${
        onClick ? "cursor-pointer" : ""
      } ${className}`}
    >
      {/* Top subtle highlight */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-zinc-700/30 to-transparent" />

      <div className="flex items-center justify-between text-xs text-zinc-400">
        <span className="font-medium tracking-tight truncate">{title}</span>
        <div className="flex items-center space-x-1.5 shrink-0">
          {tag && (
            <span className="rounded bg-zinc-800/80 px-1.5 py-0.5 text-[10px] font-mono text-zinc-400 border border-zinc-700/40">
              {tag}
            </span>
          )}
          <div className="text-zinc-500 group-hover:text-zinc-300 transition-colors">
            {icon}
          </div>
        </div>
      </div>

      <div className="mt-2 flex items-baseline justify-between">
        <div className="text-xl font-bold font-mono tracking-tight text-zinc-100 flex items-center space-x-2">
          {statusDotColor && (
            <span className={`w-2 h-2 rounded-full ${statusDotColor} animate-pulse`} />
          )}
          <span>{value}</span>
        </div>

        {trendText && (
          <span
            className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono font-medium ${
              trend === "up"
                ? "bg-emerald-950/40 text-emerald-400 border border-emerald-500/20"
                : trend === "down"
                ? "bg-rose-950/40 text-rose-400 border border-rose-500/20"
                : "bg-zinc-800/60 text-zinc-400 border border-zinc-700/30"
            }`}
          >
            {trendText}
          </span>
        )}
      </div>

      {subtext && (
        <div className="mt-1 text-[11px] text-zinc-400 truncate">
          {subtext}
        </div>
      )}
    </div>
  );
}
