package me.jeyor.cs4m.player;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class CsPlayer {
    private final UUID uuid;
    private ServerPlayer player;
    private int money;
    private int kills;
    private int chickenKills;
    private int deaths;
    private int assists;
    private int mvp;
    private int tempmvp;
    private TeamEnum team;
    private String colour;
    private String opponentColour;
    private UUID lastKiller;

    public CsPlayer(ServerPlayer player, int startingMoney) {
        this.uuid = player.getUUID();
        this.player = player;
        this.money = startingMoney;
    }

    public UUID uuid() {
        return uuid;
    }

    public ServerPlayer player() {
        return player;
    }

    public void setPlayer(ServerPlayer player) {
        this.player = player;
    }

    public boolean online() {
        return player != null && !player.hasDisconnected();
    }

    public int money() {
        return money;
    }

    public void setMoney(int money, boolean compactEconomy) {
        this.money = money;
        int cap = compactEconomy ? 9000 : 16000;
        if (this.money > cap) {
            this.money = cap;
        }
    }

    public int kills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public int chickenKills() {
        return chickenKills;
    }

    public void setChickenKills(int chickenKills) {
        this.chickenKills = chickenKills;
    }

    public int deaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public int assists() {
        return assists;
    }

    public void setAssists(int assists) {
        this.assists = assists;
    }

    public int mvp() {
        return mvp;
    }

    public void setMvp(int mvp) {
        this.mvp = mvp;
    }

    public int tempmvp() {
        return tempmvp;
    }

    public void setTempmvp(int tempmvp) {
        this.tempmvp = tempmvp;
    }

    public TeamEnum team() {
        return team;
    }

    public void setTeam(TeamEnum team) {
        this.team = team;
    }

    public boolean terrorist() {
        return team == TeamEnum.TERRORISTS;
    }

    public String colour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public String opponentColour() {
        return opponentColour;
    }

    public void setOpponentColour(String opponentColour) {
        this.opponentColour = opponentColour;
    }

    public UUID lastKiller() {
        return lastKiller;
    }

    public void setLastKiller(UUID lastKiller) {
        this.lastKiller = lastKiller;
    }

    public void resetMatchStats() {
        money = 0;
        kills = 0;
        deaths = 0;
        team = null;
        lastKiller = null;
    }
}
