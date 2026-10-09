"use client";

import React from "react";

export type BadgeStatus =
  | "PENDING"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "SKIPPED"
  | "WAITING_APPROVAL"
  | "APPROVED"
  | "REJECTED"
  | "ACTIVE"
  | "STANDBY"
  | "CLOSED"
  | "HALF_OPEN"
  | "OPEN"
  | "BUILDING"
  | "STOPPED"
  | "UP"
  | "CONNECTED"
  | "OFFLINE";

interface StatusBadgeProps {
  status: BadgeStatus | string;
  size?: "sm" | "md";
  pulse?: boolean;
  className?: string;
}

export function StatusBadge({
  status,
  size = "sm",
  pulse = false,
  className = "",
}: StatusBadgeProps) {
  const norm = (status || "").toUpperCase();

  let colorClasses = "bg-zinc-800 text-zinc-400 border-zinc-700/50";
  let dotColor = "bg-zinc-400";
  let label = status;

  switch (norm) {
    case "SUCCEEDED":
    case "APPROVED":
    case "ACTIVE":
    case "CLOSED": // Circuit breaker healthy
    case "UP":
      colorClasses = "bg-emerald-950/40 text-emerald-300 border-emerald-500/30";
      dotColor = "bg-emerald-400";
      break;

    case "RUNNING":
    case "BUILDING":
    case "CONNECTED":
      colorClasses = "bg-sky-950/40 text-sky-300 border-sky-500/30";
      dotColor = "bg-sky-400";
      break;

    case "WAITING_APPROVAL":
    case "HALF_OPEN":
    case "STANDBY":
      colorClasses = "bg-amber-950/50 text-amber-300 border-amber-500/40 shadow-sm shadow-amber-500/10";
      dotColor = "bg-amber-400";
      break;

    case "FAILED":
    case "REJECTED":
    case "OPEN": // Circuit breaker tripped
    case "OFFLINE":
      colorClasses = "bg-rose-950/40 text-rose-300 border-rose-500/30";
      dotColor = "bg-rose-400";
      break;

    case "PENDING":
    case "STOPPED":
    case "SKIPPED":
    default:
      colorClasses = "bg-zinc-800/60 text-zinc-400 border-zinc-700/40";
      dotColor = "bg-zinc-400";
      break;
  }

  const isPulsing =
    pulse ||
    norm === "RUNNING" ||
    norm === "BUILDING" ||
    norm === "WAITING_APPROVAL";

  const sizeClasses =
    size === "sm"
      ? "px-2 py-0.5 text-[10px]"
      : "px-2.5 py-1 text-xs";

  return (
    <span
      className={`inline-flex items-center space-x-1.5 rounded-full border font-mono font-medium tracking-tight ${colorClasses} ${sizeClasses} ${className}`}
    >
      <span
        className={`w-1.5 h-1.5 rounded-full ${dotColor} ${
          isPulsing ? "animate-pulse" : ""
        }`}
      />
      <span>{label}</span>
    </span>
  );
}
