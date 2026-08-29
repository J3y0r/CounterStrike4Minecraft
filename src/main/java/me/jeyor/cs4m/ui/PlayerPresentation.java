package me.jeyor.cs4m.ui;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.MatchRoster;
import me.jeyor.cs4m.world.SelectedMap;
import me.jeyor.cs4m.world.SerializedLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

import java.util.Optional;
import java.util.Set;

public final class PlayerPresentation {
    private final Cs4mConfig config;

    public PlayerPresentation(Cs4mConfig config) {
        this.config = config;
    }

    public void sendTitle(ServerPlayer player, Component title, Component subtitle, int fadeInSeconds, int staySeconds, int fadeOutSeconds) {
        if (!config.showGameStatusTitle() || player == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeInSeconds * 20, staySeconds * 20, fadeOutSeconds * 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    public void sendActionBar(ServerPlayer player, Component message) {
        if (player != null) {
            player.sendSystemMessage(message, true);
        }
    }

    public void sendChat(ServerPlayer player, Component message) {
        if (player != null) {
            player.sendSystemMessage(message, false);
        }
    }

    public static MutableComponent colored(String text, ChatFormatting colour) {
        return Component.literal(text).withStyle(colour);
    }

    public void teleport(ServerPlayer player, SerializedLocation location) {
        teleport(player, location, false);
    }

    public void teleport(ServerPlayer player, SerializedLocation location, boolean randomize) {
        Optional<ServerLevel> level = location.resolveLevel(player.level().getServer());
        if (level.isEmpty()) {
            return;
        }
        double x = location.x();
        double z = location.z();
        if (randomize) {
            double sign = Math.random() > 0.5 ? -1.0 : 1.0;
            x += sign * 2.0 * Math.random();
            z += sign * 2.0 * Math.random();
        }
        player.teleportTo(level.get(), x, location.y(), z, Set.of(), location.yaw(), location.pitch(), true);
    }

    public void sendTitleToMatch(MatchRoster roster, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        for (CsPlayer player : roster.players()) {
            if (player.online()) {
                sendTitle(player.player(), title, subtitle, fadeIn, stay, fadeOut);
            }
        }
    }

    public void sendActionBarToMatch(MatchRoster roster, Component message) {
        for (CsPlayer player : roster.players()) {
            if (player.online()) {
                sendActionBar(player.player(), message);
            }
        }
    }

    public void sendActionBarToLobbyWaiters(MinecraftServer server, MatchRoster roster, Optional<SelectedMap> map, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (roster.contains(player) || !inLobby(player, map)) {
                continue;
            }
            sendActionBar(player, message);
        }
    }

    public boolean inLobby(ServerPlayer player, Optional<SelectedMap> map) {
        if (map.isEmpty() || map.get().lobby().isEmpty()) {
            return false;
        }
        SerializedLocation lobby = map.get().lobby().get();
        if (lobby.resolveLevel(player.level().getServer()).filter(level -> level == player.level()).isEmpty()) {
            return false;
        }
        return Math.abs(Math.floor(player.getX()) - Math.floor(lobby.x())) < 20
                && Math.abs(Math.floor(player.getZ()) - Math.floor(lobby.z())) < 20;
    }

    public void clearInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    public void survival(ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
    }

    public void spectator(ServerPlayer player) {
        player.setGameMode(GameType.SPECTATOR);
    }

    public void applyCsHealth(ServerPlayer player) {
        double max = config.modeValorant() || config.modeRealms() ? 20.0 : 40.0;
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(max);
        }
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(8);
        player.fallDistance = 1.0;
        survival(player);
    }

    public void applySurvivalHealth(ServerPlayer player) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(20.0);
        }
        player.setHealth(20.0F);
        player.getFoodData().setFoodLevel(20);
    }
}
