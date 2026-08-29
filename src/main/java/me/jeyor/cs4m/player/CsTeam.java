package me.jeyor.cs4m.player;

import java.util.List;

public final class CsTeam {
    private TeamEnum role;
    private final List<CsPlayer> players;
    private int wins;
    private int losses;
    private String colour = TeamColor.WHITE;

    public CsTeam(TeamEnum role, List<CsPlayer> players) {
        this.role = role;
        this.players = players;
    }

    public TeamEnum role() {
        return role;
    }

    public void setRole(TeamEnum role) {
        this.role = role;
    }

    public List<CsPlayer> players() {
        return players;
    }

    public int wins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public void addVictory() {
        wins++;
    }

    public int losses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public void addLoss() {
        losses++;
    }

    public String colour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public int size() {
        return players.size();
    }

    public void resetScores() {
        wins = 0;
        losses = 0;
        colour = TeamColor.WHITE;
    }
}
