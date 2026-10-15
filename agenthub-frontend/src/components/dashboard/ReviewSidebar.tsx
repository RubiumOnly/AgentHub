"use client";

import React from "react";
import { FileDiffEntry, TeamView } from "@/types";
import { MultiAgentSwarmChat } from "@/components/dashboard/MultiAgentSwarmChat";
import { DiffArtifactReviewer } from "@/components/dashboard/DiffArtifactReviewer";

interface ReviewSidebarProps {
  mode: "overview-middle" | "diff";
  diffs: FileDiffEntry[];
  workspaceId: string;
  runId?: string;
  artifactId?: string;
  team: TeamView | null;
  onRefresh: () => void;
  onCardAction: (action: string) => void;
}

export function ReviewSidebar({
  mode,
  diffs,
  workspaceId,
  runId,
  artifactId,
  team,
  onRefresh,
  onCardAction,
}: ReviewSidebarProps) {
  if (mode === "overview-middle") {
    return (
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 min-h-[460px]">
        <div className="lg:col-span-6 h-[460px]">
          <MultiAgentSwarmChat team={team} onCardAction={onCardAction} />
        </div>
        <div className="lg:col-span-6 h-[460px]">
          <DiffArtifactReviewer
            diffs={diffs}
            workspaceId={workspaceId}
            runId={runId}
            artifactId={artifactId}
            onRefresh={onRefresh}
          />
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 h-[680px]">
      <DiffArtifactReviewer
        diffs={diffs}
        workspaceId={workspaceId}
        runId={runId}
        artifactId={artifactId}
        onRefresh={onRefresh}
      />
    </div>
  );
}
