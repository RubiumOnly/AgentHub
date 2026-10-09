"use client";

import React, { ReactNode } from "react";

interface BentoCardProps {
  title?: string;
  subtitle?: string;
  icon?: ReactNode;
  badge?: ReactNode;
  actions?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
  glow?: boolean;
}

export function BentoCard({
  title,
  subtitle,
  icon,
  badge,
  actions,
  footer,
  children,
  className = "",
  bodyClassName = "",
  glow = false,
}: BentoCardProps) {
  return (
    <div
      className={`group relative flex flex-col rounded-2xl border border-zinc-800/80 bg-zinc-900/50 backdrop-blur-md transition-all duration-200 ease-out hover:border-zinc-700/90 hover:shadow-xl hover:shadow-black/20 ${
        glow ? "ring-1 ring-amber-500/30 shadow-lg shadow-amber-500/5" : ""
      } ${className}`}
    >
      {/* Subtle top edge specular highlight */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-zinc-700/40 to-transparent" />

      {/* Header */}
      {(title || icon || actions || badge) && (
        <div className="flex items-center justify-between border-b border-zinc-800/60 px-4 py-3 shrink-0">
          <div className="flex items-center space-x-2.5 min-w-0">
            {icon && <div className="text-zinc-400 group-hover:text-zinc-200 transition-colors shrink-0">{icon}</div>}
            <div className="truncate">
              <div className="flex items-center space-x-2">
                {title && (
                  <h3 className="text-xs font-semibold tracking-tight text-zinc-100 truncate">
                    {title}
                  </h3>
                )}
                {badge && <div>{badge}</div>}
              </div>
              {subtitle && (
                <p className="text-[11px] text-zinc-400 truncate mt-0.5">{subtitle}</p>
              )}
            </div>
          </div>
          {actions && <div className="flex items-center space-x-1.5 shrink-0 ml-2">{actions}</div>}
        </div>
      )}

      {/* Body */}
      <div className={`flex-1 min-h-0 ${bodyClassName}`}>{children}</div>

      {/* Optional Footer */}
      {footer && (
        <div className="border-t border-zinc-800/60 px-4 py-2.5 text-xs text-zinc-400 shrink-0 bg-zinc-950/30 rounded-b-2xl">
          {footer}
        </div>
      )}
    </div>
  );
}
