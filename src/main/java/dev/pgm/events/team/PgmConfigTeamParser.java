package dev.pgm.events.team;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import tc.oc.pgm.api.Datastore;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.db.CacheDatastore;
import tc.oc.pgm.db.SQLDatastore;
import tc.oc.pgm.util.concurrent.ThreadSafeConnection;

public interface PgmConfigTeamParser extends ConfigTeamParser {

  static PgmConfigTeamParser load() {
    return load(PGM.get().getDatastore());
  }

  static PgmConfigTeamParser load(Datastore datastore) {
    if (datastore instanceof CacheDatastore) {
      try {
        Field declaredField = CacheDatastore.class.getDeclaredField("datastore");
        declaredField.setAccessible(true);
        Object fieldObject = declaredField.get(datastore);
        if (fieldObject instanceof Datastore) return load((Datastore) fieldObject);
      } catch (NoSuchFieldException e) {
        e.printStackTrace();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
    } else if (datastore instanceof SQLDatastore) {
      return new SqlPgmConfigTeamParser((SQLDatastore) datastore);
    }
    return null;
  }

  class SqlPgmConfigTeamParser implements PgmConfigTeamParser {

    private final List<TournamentTeam> teams = new ArrayList<>();
    private final SQLDatastore datastore;

    public SqlPgmConfigTeamParser(SQLDatastore datastore) {
      this.datastore = datastore;
    }

    @Override
    public TournamentTeam getTeam(String name) {
      return this.getTeams().stream()
          .filter(team -> team.getName().equalsIgnoreCase(name))
          .findFirst()
          .orElse(null);
    }

    @Override
    public void reload() {
      this.teams.clear();
      this.getTeams();
    }

    @Override
    public List<TournamentTeam> getTeams() {
      if (teams.isEmpty()) {
        queryTeams();
      }
      return teams;
    }

    private void queryTeams() {
      TeamQuery teamQuery = new TeamQuery();
      this.datastore.submitQuery(teamQuery);
    }

    public class TeamQuery implements ThreadSafeConnection.Query {

      @Override
      public String getFormat() {
        return "SELECT * FROM `teams`;";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          try {
            MembersQuery membersQuery = new MembersQuery(resultSet.getInt("id"));
            SqlPgmConfigTeamParser.this.datastore.submitQuery(membersQuery).get();
            teams.add(
                new DefaultTournamentTeam(resultSet.getString("name"), membersQuery.getMembers()));
          } catch (InterruptedException e) {
            throw new SQLException(e);
          } catch (ExecutionException e) {
            throw new SQLException(e);
          }
        }
      }
    }

    public static class MembersQuery implements ThreadSafeConnection.Query {

      private final List<TournamentPlayer> members = new ArrayList<>();
      private final int team;

      public MembersQuery(int team) {
        this.team = team;
      }

      @Override
      public String getFormat() {
        return "SELECT * FROM `members` WHERE `team`=?";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setInt(1, this.team);
        ResultSet resultSet = statement.executeQuery();
        while (resultSet.next()) {
          TournamentPlayer tournamentPlayer =
              new DefaultTournamentPlayer(UUID.fromString(resultSet.getString("uuid")), true);
          this.members.add(tournamentPlayer);
        }
      }

      public List<TournamentPlayer> getMembers() {
        return this.members;
      }
    }
  }
}
