package me.jeyor.cs4m.ui;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.CsTeam;
import me.jeyor.cs4m.player.MatchRoster;
import me.jeyor.cs4m.player.TeamColor;
import me.jeyor.cs4m.player.TeamEnum;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MatchScoreboard {
    private static final String OBJECTIVE = "cs4m";
    private static final String TEAM_T = "team1";
    private static final String TEAM_CT = "team2";
    private static final int MAX_LINES = 15;

    private final MinecraftServer server;
    private final Cs4mConfig config;
    private final MatchRoster roster;

    public MatchScoreboard(MinecraftServer server, Cs4mConfig config, MatchRoster roster) {
        this.server = server;
        this.config = config;
        this.roster = roster;
    }

    public void ensureTeams() {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam terrorists = team(scoreboard, TEAM_T, roster.terroristsTeam());
        PlayerTeam counters = team(scoreboard, TEAM_CT, roster.counterTerroristsTeam());
        for (CsPlayer player : roster.terrorists()) {
            if (player.online()) {
                scoreboard.addPlayerToTeam(player.player().getScoreboardName(), terrorists);
            }
        }
        for (CsPlayer player : roster.counterTerrorists()) {
            if (player.online()) {
                scoreboard.addPlayerToTeam(player.player().getScoreboardName(), counters);
            }
        }
    }

    public void update(String mapName) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(
                    OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    title(),
                    ObjectiveCriteria.RenderType.INTEGER,
                    true,
                    BlankFormat.INSTANCE
            );
        }
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        List<Component> lines = lines(mapName);
        for (int index = 0; index < MAX_LINES; index++) {
            ScoreHolder holder = ScoreHolder.forNameOnly(entry(index));
            if (index < lines.size()) {
                ScoreAccess score = scoreboard.getOrCreatePlayerScore(holder, objective);
                score.set(MAX_LINES - index);
                score.display(lines.get(index));
                score.numberFormatOverride(BlankFormat.INSTANCE);
            } else {
                scoreboard.resetAllPlayerScores(holder);
            }
        }
        ensureTeams();
    }

    public void clear() {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
        PlayerTeam terrorists = scoreboard.getPlayerTeam(TEAM_T);
        if (terrorists != null) {
            scoreboard.removePlayerTeam(terrorists);
        }
        PlayerTeam counters = scoreboard.getPlayerTeam(TEAM_CT);
        if (counters != null) {
            scoreboard.removePlayerTeam(counters);
        }
    }

    public void remove(ServerPlayer player) {
        server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
        server.getScoreboard().resetAllPlayerScores(player);
    }

    private PlayerTeam team(Scoreboard scoreboard, String name, CsTeam csTeam) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
            team.setNameTagVisibility(Team.Visibility.HIDE_FOR_OTHER_TEAMS);
        }
        team.setColor(Optional.of(scoreColor(csTeam.colour())));
        return team;
    }

    private Component title() {
        String name = config.modeRealms() ? "RealmStrike" : config.modeValorant() ? "ValoCraft" : "MineStrike";
        return Component.literal("----" + name + "----").withStyle(ChatFormatting.BOLD);
    }

    private List<Component> lines(String mapName) {
        List<Component> lines = new ArrayList<>();
        CsTeam own = roster.terroristsTeam();
        if (!roster.players().isEmpty() && roster.players().getFirst().team() == TeamEnum.COUNTER_TERRORISTS) {
            own = roster.counterTerroristsTeam();
        }
        int round = own.wins() + own.losses() + 1;
        lines.add(Component.literal("Map: ").withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD)
                .append(Component.literal(mapName + "  ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("Round: ").withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD))
                .append(Component.literal(round + " of " + config.maxRounds()).withStyle(ChatFormatting.GRAY)));
        lines.add(Component.literal("Teams: ").withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD)
                .append(Component.literal(roster.counterTerroristsTeam().colour() + ": " + roster.counterTerroristsTeam().wins())
                        .withStyle(TeamColor.formatting(roster.counterTerroristsTeam().colour())))
                .append(Component.literal(" vs ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(roster.terroristsTeam().colour() + ": " + roster.terroristsTeam().wins())
                        .withStyle(TeamColor.formatting(roster.terroristsTeam().colour()))));
        boolean compact = config.modeValorant() || config.modeRealms();
        lines.add(header(roster.counterTerrorists().size(), roster.counterTerroristsTeam(), compact ? "Defenders" : "Counters"));
        for (CsPlayer player : roster.counterTerrorists()) {
            lines.add(playerLine(player, compact));
        }
        lines.add(header(roster.terrorists().size(), roster.terroristsTeam(), compact ? "Attackers" : "Terrors"));
        for (CsPlayer player : roster.terrorists()) {
            lines.add(playerLine(player, compact));
        }
        if (lines.size() > MAX_LINES) {
            return lines.subList(0, MAX_LINES);
        }
        return lines;
    }

    private Component header(int size, CsTeam team, String label) {
        return Component.literal("(" + size + ") ").withStyle(ChatFormatting.BOLD)
                .append(Component.literal(label).withStyle(TeamColor.formatting(team.colour())))
                .append(Component.literal(" with " + team.wins() + " wins: ").withStyle(ChatFormatting.WHITE));
    }

    private Component playerLine(CsPlayer player, boolean compact) {
        String name = player.online() ? player.player().getScoreboardName() : "offline";
        boolean eliminated = !player.online() || player.player().gameMode() == GameType.SPECTATOR;
        if (eliminated) {
            return Component.literal(name + ": ").append(
                    Component.literal(" Elim.  $" + player.money() + " K: " + player.kills() + "  D: " + player.deaths())
                            .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE, ChatFormatting.BOLD)
            );
        }
        Component line = Component.literal(name + ": ")
                .append(Component.literal("$" + player.money()).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" K:").withStyle(ChatFormatting.BLACK))
                .append(Component.literal(Integer.toString(player.kills())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" D:").withStyle(ChatFormatting.BLACK))
                .append(Component.literal(Integer.toString(player.deaths())).withStyle(ChatFormatting.GREEN));
        if (!compact) {
            line = line.copy()
                    .append(Component.literal(" MVP:").withStyle(ChatFormatting.BLACK))
                    .append(Component.literal(Integer.toString(player.mvp())).withStyle(ChatFormatting.GREEN));
        }
        return line;
    }

    private static String entry(int index) {
        return index < 10 ? "§" + index : "§" + (char) ('a' + index - 10);
    }

    private static net.minecraft.world.scores.TeamColor scoreColor(String colour) {
        net.minecraft.world.scores.TeamColor mapped = net.minecraft.world.scores.TeamColor.byName(colour.toLowerCase());
        return mapped == null ? net.minecraft.world.scores.TeamColor.WHITE : mapped;
    }
}
