package dev.pgm.events;

import dev.pgm.events.commands.TournamentAdminCommands;
import dev.pgm.events.commands.TournamentUserCommands;
import dev.pgm.events.commands.VetoCommands;
import dev.pgm.events.commands.providers.TournamentProvider;
import dev.pgm.events.config.AppData;
import dev.pgm.events.format.TournamentFormat;
import dev.pgm.events.listeners.MatchLoadListener;
import dev.pgm.events.listeners.PlayerJoinListen;
import dev.pgm.events.ready.ReadyCommands;
import dev.pgm.events.ready.ReadyListener;
import dev.pgm.events.ready.ReadyManager;
import dev.pgm.events.ready.ReadyManagerImpl;
import dev.pgm.events.ready.ReadyParties;
import dev.pgm.events.ready.ReadySystem;
import dev.pgm.events.team.ConfigTeamParser;
import dev.pgm.events.team.DefaultTeamManager;
import dev.pgm.events.team.PgmConfigTeamParser;
import dev.pgm.events.team.TournamentTeamManager;
import dev.pgm.events.team.YmlConfigTeamParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.command.graph.CommandExecutor;
import tc.oc.pgm.command.graph.MatchPlayerProvider;
import tc.oc.pgm.command.graph.MatchProvider;
import tc.oc.pgm.lib.app.ashcon.intake.bukkit.graph.BasicBukkitCommandGraph;
import tc.oc.pgm.lib.app.ashcon.intake.fluent.DispatcherNode;
import tc.oc.pgm.lib.app.ashcon.intake.parametric.AbstractModule;

public class Tournament extends JavaPlugin {

  private TournamentTeamManager teamManager;
  private TournamentManager tournamentManager;

  private static Tournament plugin;
  private static ConfigTeamParser parser;

  @Override
  public void onEnable() {
    plugin = this;
    saveDefaultConfig();

    teamManager = DefaultTeamManager.manager();
    tournamentManager = new TournamentManager();
    // Load teams now
    if (AppData.pgmTeams()) {
      parser = PgmConfigTeamParser.load();
      parser.getTeams();
    }
    if (parser == null) parser = new YmlConfigTeamParser();

    ReadyManager readyManager = new ReadyManagerImpl(new ReadySystem(), new ReadyParties());
    ReadyListener readyListener = new ReadyListener(readyManager);
    ReadyCommands readyCommands = new ReadyCommands(readyManager);

    BasicBukkitCommandGraph graph =
        new BasicBukkitCommandGraph(new CommandModule(tournamentManager, teamManager));

    DispatcherNode node = graph.getRootDispatcherNode();
    node.registerCommands(new VetoCommands());
    node.registerCommands(readyCommands);

    DispatcherNode subNode = node.registerNode("tourney", "tournament", "tm", "events");
    subNode.registerCommands(new TournamentUserCommands());
    subNode.registerCommands(new TournamentAdminCommands());

    Bukkit.getPluginManager().registerEvents(new MatchLoadListener(teamManager), this);
    Bukkit.getPluginManager().registerEvents(new PlayerJoinListen(teamManager), this);
    Bukkit.getPluginManager().registerEvents(readyListener, this);
    new CommandExecutor(this, graph).register();
  }

  @Override
  public void onDisable() {
    plugin = null;
  }

  public TournamentTeamManager getTeamManager() {
    return teamManager;
  }

  public TournamentManager getTournamentManager() {
    return tournamentManager;
  }

  public static Tournament get() {
    return plugin;
  }

  public static ConfigTeamParser getTeamParser() {
    return parser;
  }

  private static class CommandModule extends AbstractModule {

    private final TournamentManager tournamentManager;
    private final TournamentTeamManager teamManager;

    public CommandModule(TournamentManager tournamentManager, TournamentTeamManager teamManager) {
      this.tournamentManager = tournamentManager;
      this.teamManager = teamManager;
    }

    @Override
    protected void configure() {
      configureInstances();
      configureProviders();
    }

    private void configureInstances() {
      bind(PGM.class).toInstance(PGM.get());
    }

    private void configureProviders() {
      bind(MatchPlayer.class).toProvider(new MatchPlayerProvider());
      bind(Match.class).toProvider(new MatchProvider());
      bind(TournamentManager.class).toInstance(tournamentManager);
      bind(TournamentTeamManager.class).toInstance(teamManager);
      bind(TournamentFormat.class).toProvider(new TournamentProvider(tournamentManager));
    }
  }
}
