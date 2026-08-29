package me.jeyor.cs4m.player;

import me.jeyor.cs4m.config.Cs4mConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MatchRoster {
    private final Cs4mConfig config;
    private final List<CsPlayer> players = new ArrayList<>();
    private final List<CsPlayer> terrorists = new ArrayList<>();
    private final List<CsPlayer> counterTerrorists = new ArrayList<>();
    private final CsTeam terroristsTeam = new CsTeam(TeamEnum.TERRORISTS, terrorists);
    private final CsTeam counterTerroristsTeam = new CsTeam(TeamEnum.COUNTER_TERRORISTS, counterTerrorists);

    public MatchRoster(Cs4mConfig config) {
        this.config = config;
    }

    public List<CsPlayer> players() {
        return players;
    }

    public List<CsPlayer> terrorists() {
        return terrorists;
    }

    public List<CsPlayer> counterTerrorists() {
        return counterTerrorists;
    }

    public CsTeam terroristsTeam() {
        return terroristsTeam;
    }

    public CsTeam counterTerroristsTeam() {
        return counterTerroristsTeam;
    }

    public Optional<CsPlayer> find(UUID uuid) {
        for (CsPlayer player : players) {
            if (player.uuid().equals(uuid)) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    public Optional<CsPlayer> find(ServerPlayer player) {
        return find(player.getUUID());
    }

    public boolean contains(ServerPlayer player) {
        return find(player).isPresent();
    }

    public int size() {
        return players.size();
    }

    public JoinResult join(ServerPlayer player, String colour) {
        Optional<CsPlayer> existing = find(player);
        if (existing.isPresent()) {
            return JoinResult.alreadyJoined(existing.get());
        }
        CsPlayer csPlayer = new CsPlayer(player, config.startingMoney());
        Optional<TeamEnum> team = assignTeam(colour);
        if (team.isEmpty()) {
            return JoinResult.rejected(colour);
        }
        csPlayer.setTeam(team.get());
        if (team.get() == TeamEnum.TERRORISTS) {
            csPlayer.setColour(terroristsTeam.colour());
            terrorists.add(csPlayer);
            csPlayer.setOpponentColour(counterTerroristsTeam.colour());
        } else {
            csPlayer.setColour(counterTerroristsTeam.colour());
            counterTerrorists.add(csPlayer);
            csPlayer.setOpponentColour(terroristsTeam.colour());
        }
        players.add(csPlayer);
        return JoinResult.accepted(csPlayer);
    }

    public void remove(CsPlayer player) {
        players.remove(player);
        terrorists.remove(player);
        counterTerrorists.remove(player);
        player.resetMatchStats();
    }

    public void clear() {
        players.clear();
        terrorists.clear();
        counterTerrorists.clear();
        terroristsTeam.resetScores();
        counterTerroristsTeam.resetScores();
    }

    public List<CsPlayer> balance() {
        List<CsPlayer> moved = new ArrayList<>();
        int ct = counterTerrorists.size();
        int terr = terrorists.size();
        String terrColour = terroristsTeam.colour();
        String ctColour = counterTerroristsTeam.colour();
        if (ct > terr + 1) {
            for (CsPlayer csPlayer : new ArrayList<>(counterTerrorists)) {
                move(csPlayer, TeamEnum.TERRORISTS, terrColour, ctColour);
                moved.add(csPlayer);
                ct--;
                terr++;
                if (terr == ct || terr == ct + 1) {
                    break;
                }
            }
        } else if (terr > ct + 1) {
            for (CsPlayer csPlayer : new ArrayList<>(terrorists)) {
                move(csPlayer, TeamEnum.COUNTER_TERRORISTS, ctColour, terrColour);
                moved.add(csPlayer);
                ct++;
                terr--;
                if (ct == terr || ct == terr + 1) {
                    break;
                }
            }
        }
        return moved;
    }

    public void swapSides() {
        List<CsPlayer> previousTerrorists = new ArrayList<>(terrorists);
        terrorists.clear();
        terrorists.addAll(counterTerrorists);
        counterTerrorists.clear();
        counterTerrorists.addAll(previousTerrorists);
        int wins = terroristsTeam.wins();
        int losses = terroristsTeam.losses();
        String colour = terroristsTeam.colour();
        terroristsTeam.setWins(counterTerroristsTeam.wins());
        terroristsTeam.setLosses(counterTerroristsTeam.losses());
        terroristsTeam.setColour(counterTerroristsTeam.colour());
        counterTerroristsTeam.setWins(wins);
        counterTerroristsTeam.setLosses(losses);
        counterTerroristsTeam.setColour(colour);
        for (CsPlayer player : terrorists) {
            player.setTeam(TeamEnum.TERRORISTS);
            player.setColour(terroristsTeam.colour());
            player.setOpponentColour(counterTerroristsTeam.colour());
        }
        for (CsPlayer player : counterTerrorists) {
            player.setTeam(TeamEnum.COUNTER_TERRORISTS);
            player.setColour(counterTerroristsTeam.colour());
            player.setOpponentColour(terroristsTeam.colour());
        }
    }

    public boolean allEliminated(List<CsPlayer> team) {
        if (team.isEmpty()) {
            return true;
        }
        for (CsPlayer player : team) {
            if (player.online() && player.player().gameMode() != GameType.SPECTATOR) {
                return false;
            }
        }
        return true;
    }

    private void move(CsPlayer player, TeamEnum team, String colour, String opponent) {
        terrorists.remove(player);
        counterTerrorists.remove(player);
        player.setTeam(team);
        player.setColour(colour);
        player.setOpponentColour(opponent);
        if (team == TeamEnum.TERRORISTS) {
            terrorists.add(player);
        } else {
            counterTerrorists.add(player);
        }
    }

    private Optional<TeamEnum> assignTeam(String colour) {
        if (config.standardTeamColours()) {
            if (TeamColor.GOLD.equals(colour) || TeamColor.RED.equals(colour)) {
                terroristsTeam.setColour(colour);
                return Optional.of(TeamEnum.TERRORISTS);
            }
            counterTerroristsTeam.setColour(TeamColor.AQUA);
            return Optional.of(TeamEnum.COUNTER_TERRORISTS);
        }
        if (colour.equals(terroristsTeam.colour())) {
            return Optional.of(TeamEnum.TERRORISTS);
        }
        if (colour.equals(counterTerroristsTeam.colour())) {
            return Optional.of(TeamEnum.COUNTER_TERRORISTS);
        }
        if (TeamColor.WHITE.equals(terroristsTeam.colour())) {
            String claimed = colour.equals(counterTerroristsTeam.colour()) ? TeamColor.GOLD : colour;
            terroristsTeam.setColour(claimed);
            return Optional.of(TeamEnum.TERRORISTS);
        }
        if (TeamColor.WHITE.equals(counterTerroristsTeam.colour())) {
            String claimed = colour.equals(counterTerroristsTeam.colour()) ? TeamColor.AQUA : colour;
            counterTerroristsTeam.setColour(claimed);
            return Optional.of(TeamEnum.COUNTER_TERRORISTS);
        }
        return Optional.empty();
    }

    public record JoinResult(boolean accepted, boolean alreadyJoined, CsPlayer player, String colour) {
        static JoinResult accepted(CsPlayer player) {
            return new JoinResult(true, false, player, player.colour());
        }

        static JoinResult alreadyJoined(CsPlayer player) {
            return new JoinResult(false, true, player, player.colour());
        }

        static JoinResult rejected(String colour) {
            return new JoinResult(false, false, null, colour);
        }
    }
}
