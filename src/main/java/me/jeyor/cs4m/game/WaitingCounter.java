package me.jeyor.cs4m.game;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.MatchRoster;
import me.jeyor.cs4m.ui.PlayerPresentation;
import me.jeyor.cs4m.world.SelectedMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class WaitingCounter {
    private final Cs4mConfig config;
    private final MatchRoster roster;
    private final PlayerPresentation presentation;
    private boolean active;
    private int cooldownTicks;
    private int seconds;

    public WaitingCounter(Cs4mConfig config, MatchRoster roster, PlayerPresentation presentation) {
        this.config = config;
        this.roster = roster;
        this.presentation = presentation;
    }

    public boolean active() {
        return active;
    }

    public void start() {
        if (active) {
            return;
        }
        active = true;
        cooldownTicks = 20;
        seconds = 0;
    }

    public void stop() {
        active = false;
        cooldownTicks = 0;
        seconds = 0;
    }

    public TickResult tick(MinecraftServer server, Optional<SelectedMap> map, boolean quitExitGame) {
        if (!active) {
            return TickResult.none();
        }
        if (!config.enabled()) {
            stop();
            return TickResult.lobby();
        }
        if ((roster.size() == 0 || server.getPlayerList().getPlayers().isEmpty()) && quitExitGame) {
            stop();
            return TickResult.lobby();
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return TickResult.none();
        }
        cooldownTicks = 20;
        if (roster.size() >= config.minPlayers()) {
            stop();
            return TickResult.starting();
        }
        if (seconds % 5 == 0) {
            broadcastNeedPlayers(server, map);
        }
        seconds++;
        return TickResult.waiting();
    }

    private void broadcastNeedPlayers(MinecraftServer server, Optional<SelectedMap> map) {
        int remaining = Math.max(0, config.minPlayers() - roster.size());
        String noun = remaining <= 1 ? "player" : "players";
        Component message = Component.literal("The game needs ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(Integer.toString(remaining)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" more " + noun + " to start!").withStyle(ChatFormatting.GOLD));
        for (CsPlayer player : roster.players()) {
            if (player.online()) {
                presentation.sendChat(player.player(), message);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (roster.contains(player) || !inLobby(player, map)) {
                continue;
            }
            presentation.sendActionBar(player, message);
        }
    }

    private boolean inLobby(ServerPlayer player, Optional<SelectedMap> map) {
        if (map.isEmpty() || map.get().lobby().isEmpty()) {
            return false;
        }
        var lobby = map.get().lobby().get();
        if (lobby.resolveLevel(server(player)).filter(level -> level == player.level()).isEmpty()) {
            return false;
        }
        Vec3 pos = player.position();
        return Math.abs(Math.floor(pos.x) - Math.floor(lobby.x())) < 20
                && Math.abs(Math.floor(pos.z) - Math.floor(lobby.z())) < 20;
    }

    private MinecraftServer server(ServerPlayer player) {
        return player.level().getServer();
    }

    public record TickResult(GameState state, boolean changed) {
        static TickResult none() {
            return new TickResult(null, false);
        }

        static TickResult lobby() {
            return new TickResult(GameState.LOBBY, true);
        }

        static TickResult waiting() {
            return new TickResult(GameState.WAITING, true);
        }

        static TickResult starting() {
            return new TickResult(GameState.STARTING, true);
        }
    }
}
