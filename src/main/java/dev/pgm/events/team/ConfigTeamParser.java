package dev.pgm.events.team;

import java.util.List;

public interface ConfigTeamParser {

  TournamentTeam getTeam(String name);

  List<TournamentTeam> getTeams();
}
