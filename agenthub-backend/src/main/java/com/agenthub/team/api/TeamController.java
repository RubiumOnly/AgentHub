package com.agenthub.team.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.team.application.TeamApplication;
import com.agenthub.team.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamApplication teamApplication;

    public TeamController(TeamApplication teamApplication) {
        this.teamApplication = teamApplication;
    }

    @PostMapping
    public Result<TeamView> createTeam(@RequestBody CreateTeamCommand cmd) {
        return Result.ok(teamApplication.createTeam(cmd));
    }

    @GetMapping
    public Result<List<TeamView>> listTeams(@RequestParam(value = "projectId", required = false) String projectId) {
        return Result.ok(teamApplication.listTeams(projectId));
    }

    @GetMapping("/{id}")
    public Result<TeamView> getTeam(@PathVariable("id") String id) {
        return Result.ok(teamApplication.getTeamById(id));
    }

    @PostMapping("/{id}/members")
    public Result<TeamMemberView> addMember(@PathVariable("id") String id, @RequestBody AddTeamMemberCommand cmd) {
        return Result.ok(teamApplication.addTeamMember(id, cmd));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable("id") String id, @PathVariable("memberId") String memberId) {
        teamApplication.removeTeamMember(id, memberId);
        return Result.ok(null);
    }

    @PostMapping("/{id}/coordinate")
    public Result<TeamCoordinateResultView> coordinate(@PathVariable("id") String id, @RequestBody TeamCoordinateCommand cmd) {
        return Result.ok(teamApplication.coordinateNextTurn(id, cmd));
    }

    @GetMapping("/projects/{projectId}/roles/me")
    public Result<ProjectUserRoleView> getCurrentUserRole(@PathVariable("projectId") String projectId) {
        return Result.ok(teamApplication.getCurrentUserRole(projectId));
    }

    @GetMapping("/projects/{projectId}/members")
    public Result<List<ProjectMemberView>> listProjectMembers(@PathVariable("projectId") String projectId) {
        return Result.ok(teamApplication.listProjectMembers(projectId));
    }

    @PostMapping("/projects/{projectId}/members")
    public Result<ProjectMemberView> addProjectMember(
            @PathVariable("projectId") String projectId,
            @RequestBody AddProjectMemberCommand cmd) {
        return Result.ok(teamApplication.addProjectMember(projectId, cmd));
    }

    @PutMapping("/projects/{projectId}/members/{memberId}/role")
    public Result<ProjectMemberView> updateMemberRole(
            @PathVariable("projectId") String projectId,
            @PathVariable("memberId") String memberId,
            @RequestBody UpdateMemberRoleCommand cmd) {
        return Result.ok(teamApplication.updateMemberRole(projectId, memberId, cmd));
    }

    @DeleteMapping("/projects/{projectId}/members/{memberId}")
    public Result<Void> removeProjectMember(
            @PathVariable("projectId") String projectId,
            @PathVariable("memberId") String memberId) {
        teamApplication.removeProjectMember(projectId, memberId);
        return Result.ok(null);
    }
}
