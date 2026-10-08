package com.agenthub.team.application;

import com.agenthub.team.dto.*;

import java.util.List;

public interface TeamApplication {
    TeamView createTeam(CreateTeamCommand cmd);
    List<TeamView> listTeams(String projectId);
    TeamView getTeamById(String teamId);
    TeamMemberView addTeamMember(String teamId, AddTeamMemberCommand cmd);
    void removeTeamMember(String teamId, String memberId);
    TeamCoordinateResultView coordinateNextTurn(String teamId, TeamCoordinateCommand cmd);
}
