"use client";

import React from "react";
import { DeploymentResponse, ProviderView, TokenSummaryView } from "@/types";
import { ProviderCostDashboard } from "@/components/dashboard/ProviderCostDashboard";
import { SandboxPreviewPanel } from "@/components/dashboard/SandboxPreviewPanel";

interface DeploymentPanelProps {
  mode: "overview-bottom" | "providers" | "sandbox";
  providers: ProviderView[];
  tokenSummary: TokenSummaryView | null;
  deployment: DeploymentResponse | null;
  projectId: string;
  onRefresh: () => void;
}

export function DeploymentPanel({
  mode,
  providers,
  tokenSummary,
  deployment,
  projectId,
  onRefresh,
}: DeploymentPanelProps) {
  if (mode === "overview-bottom") {
    return (
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[440px]">
        <div className="lg:col-span-6 h-[440px]">
          <ProviderCostDashboard
            providers={providers}
            tokenSummary={tokenSummary}
            onRefresh={onRefresh}
          />
        </div>
        <div className="lg:col-span-6 h-[440px]">
          <SandboxPreviewPanel
            deployment={deployment}
            projectId={projectId}
            onRefresh={onRefresh}
          />
        </div>
      </div>
    );
  }

  if (mode === "providers") {
    return (
      <div className="flex-1 h-[680px]">
        <ProviderCostDashboard
          providers={providers}
          tokenSummary={tokenSummary}
          onRefresh={onRefresh}
        />
      </div>
    );
  }

  return (
    <div className="flex-1 h-[680px]">
      <SandboxPreviewPanel
        deployment={deployment}
        projectId={projectId}
        onRefresh={onRefresh}
      />
    </div>
  );
}
