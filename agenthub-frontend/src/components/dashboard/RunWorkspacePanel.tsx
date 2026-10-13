"use client";

import React from "react";
import { StepRunView, TeamView } from "@/types";
import { DagTopologyVisualizer } from "@/components/dashboard/DagTopologyVisualizer";
import { LiveExecutionTerminal } from "@/components/dashboard/LiveExecutionTerminal";
import { MultiAgentSwarmChat } from "@/components/dashboard/MultiAgentSwarmChat";
import WorkspaceExplorer from "@/components/WorkspaceExplorer";

interface RunWorkspacePanelProps {
  mode: "overview-top" | "dag" | "swarm" | "files";
  runId: string;
  workspaceId: string;
  steps: StepRunView[];
  team: TeamView | null;
  isRunningWorkflow: boolean;
  onRunWorkflow: () => void;
  onRefresh: () => void;
  onCardAction: (action: string) => void;
}

export function RunWorkspacePanel({
  mode,
  runId,
  workspaceId,
  steps,
  team,
  isRunningWorkflow,
  onRunWorkflow,
  onRefresh,
  onCardAction,
}: RunWorkspacePanelProps) {
  if (mode === "overview-top") {
    return (
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[380px]">
        <div className="lg:col-span-7 h-[380px]">
          <DagTopologyVisualizer
            steps={steps}
            onRunWorkflow={onRunWorkflow}
            isRunning={isRunningWorkflow}
          />
        </div>
        <div className="lg:col-span-5 h-[380px]">
          <LiveExecutionTerminal runId={runId} onRefresh={onRefresh} />
        </div>
      </div>
    );
  }

  if (mode === "dag") {
    return (
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[640px]">
        <div className="lg:col-span-8 h-[640px]">
          <DagTopologyVisualizer
            steps={steps}
            onRunWorkflow={onRunWorkflow}
            isRunning={isRunningWorkflow}
          />
        </div>
        <div className="lg:col-span-4 h-[640px]">
          <LiveExecutionTerminal runId={runId} onRefresh={onRefresh} />
        </div>
      </div>
    );
  }

  if (mode === "swarm") {
    return (
      <div className="flex-1 h-[680px]">
        <MultiAgentSwarmChat team={team} onCardAction={onCardAction} />
      </div>
    );
  }

  return (
    <div className="flex-1 h-[680px]">
      <WorkspaceExplorer workspaceId={workspaceId} />
    </div>
  );
}
