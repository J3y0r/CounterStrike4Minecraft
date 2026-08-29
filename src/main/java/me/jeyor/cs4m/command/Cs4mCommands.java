package me.jeyor.cs4m.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.jeyor.cs4m.database.MapLocationField;
import me.jeyor.cs4m.database.MapRecord;
import me.jeyor.cs4m.game.GameState;
import me.jeyor.cs4m.runtime.Cs4mServer;
import me.jeyor.cs4m.world.SerializedLocation;
import me.jeyor.cs4m.world.WorldNames;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class Cs4mCommands {
    private static final SuggestionProvider<CommandSourceStack> BLOCK_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    BuiltInRegistries.BLOCK.keySet().stream().map(Identifier::getPath),
                    builder
            );

    private final Supplier<Cs4mServer> runtime;
    private final SuggestionProvider<CommandSourceStack> mapSuggestions;

    public Cs4mCommands(Supplier<Cs4mServer> runtime) {
        this.runtime = runtime;
        this.mapSuggestions = (context, builder) -> {
            Cs4mServer current = this.runtime.get();
            if (current == null) {
                return builder.buildFuture();
            }
            return SharedSuggestionProvider.suggest(
                    current.maps().maps().stream().map(MapRecord::name),
                    builder
            );
        };
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("csmc")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .executes(context -> usage(context.getSource()))
                .then(Commands.literal("setMap")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(mapSuggestions)
                                .executes(context -> setMap(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("delMap")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(mapSuggestions)
                                .executes(context -> delMap(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("setlobby")
                        .executes(context -> setLobby(context.getSource())))
                .then(Commands.literal("setspawn")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("ct", "counterterrorist", "t", "terrorist"),
                                        builder
                                ))
                                .executes(context -> setSpawn(context.getSource(), StringArgumentType.getString(context, "team")))))
                .then(Commands.literal("setbombsite")
                        .then(Commands.argument("site", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("A", "B"), builder))
                                .executes(context -> setBombSite(context.getSource(), StringArgumentType.getString(context, "site")))))
                .then(Commands.literal("setMinPlayers")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(context -> setMinPlayers(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("setMaxPlayers")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(context -> setMaxPlayers(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("setBombBlock")
                        .then(Commands.argument("block", StringArgumentType.word())
                                .suggests(BLOCK_SUGGESTIONS)
                                .executes(context -> setBombBlock(context.getSource(), StringArgumentType.getString(context, "block")))))
                .then(Commands.literal("stop")
                        .executes(context -> stop(context.getSource())))
                .then(Commands.literal("setRandMap")
                        .executes(context -> setRandMap(context.getSource())))
                .then(Commands.literal("maintenance")
                        .executes(context -> maintenance(context.getSource())))
                .then(Commands.literal("reloadConfig")
                        .executes(context -> reloadConfig(context.getSource())));
        var node = dispatcher.register(root);
        dispatcher.register(Commands.literal("cs").redirect(node));
        dispatcher.register(Commands.literal("counterstrike").redirect(node));
    }

    private int setMap(CommandSourceStack source, String name) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        runtime.setSetupMap(name);
        gold(source, "Assuming map " + name + ", continue with lobby and spawn configs or run again to change name");
        if (runtime.systemSet() == 1) {
            green(source, "Great, now lets set the lobby type /csmc setlobby");
            runtime.setSystemSet(2);
        }
        return 1;
    }

    private int delMap(CommandSourceStack source, String name) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        int deleted = runtime.maps().delete(name);
        gold(source, "Deleting map " + name + " was completed with " + deleted + " lines afetcted");
        if (runtime.systemSet() < 8) {
            gold(source, "You deleted map " + name + ", do you want to try again with /csmc setMap  with the map name?");
            runtime.setSystemSet(1);
        }
        runtime.clearSetupMap();
        return 1;
    }

    private int setLobby(CommandSourceStack source) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        String map = requireSetupMap(source, runtime);
        if (map == null) {
            return 0;
        }
        if (!lobbyOrWaiting(runtime)) {
            gold(source, "Game is active so no config done");
            return 0;
        }
        runtime.maps().saveLocation(map, MapLocationField.LOBBY, locationOf(player));
        gold(source, "Lobby location has been successfully set for map " + map);
        if (runtime.systemSet() == 2) {
            green(source, "Good, now lets set spawns with /csmc setspawn (ct or terrorist)");
            runtime.setSystemSet(3);
        }
        return 1;
    }

    private int setSpawn(CommandSourceStack source, String team) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        String map = requireSetupMap(source, runtime);
        if (map == null) {
            return 0;
        }
        if (!lobbyOrWaiting(runtime)) {
            gold(source, "Game is active so no config done");
            return 0;
        }
        String key = team.toLowerCase(Locale.ROOT);
        if (key.equals("ct") || key.equals("counterterrorist")) {
            runtime.maps().saveLocation(map, MapLocationField.COUNTER, locationOf(player));
            gold(source, "Counter Terrorist spawn has been successfully set for map " + map);
            runtime.markTerroristSpawnSet();
            runtime.advanceSystemSet();
        } else if (key.equals("t") || key.equals("terrorist")) {
            runtime.maps().saveLocation(map, MapLocationField.TERRORISTS, locationOf(player));
            gold(source, "Terrorist spawn has been successfully set for map " + map);
            runtime.markCounterSpawnSet();
            runtime.advanceSystemSet();
        } else {
            source.sendFailure(Component.literal("/counterstrike setspawn <counterterrorist/terrorist>").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (runtime.systemSet() >= 5 && runtime.bothSpawnsSet()) {
            green(source, "Congrats! Basic tutorial is now finished, you can also add different maps to this world! Enjoy!");
            runtime.selectRandomMap();
            runtime.setSystemSet(8);
        }
        return 1;
    }

    private int setBombSite(CommandSourceStack source, String site) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        String map = requireSetupMap(source, runtime);
        if (map == null) {
            return 0;
        }
        if (!lobbyOrWaiting(runtime)) {
            gold(source, "Game is active so no config done");
            return 0;
        }
        if (!site.equals("A") && !site.equals("B")) {
            source.sendFailure(Component.literal("Bom site name needs to be A or B."));
            return 0;
        }
        runtime.maps().saveLocation(map, site.equals("A") ? MapLocationField.A : MapLocationField.B, locationOf(player));
        gold(source, site + " bomb site has been successfully set for map " + map);
        return 1;
    }

    private int setMinPlayers(CommandSourceStack source, int count) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        if (!lobbyOrWaiting(runtime)) {
            source.sendSuccess(() -> Component.literal("Too late to change player min count ").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return 0;
        }
        try {
            runtime.config().set("min-players", count);
            gold(source, "MinPlayers was set to " + count);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Unable to save min-players"));
            return 0;
        }
    }

    private int setMaxPlayers(CommandSourceStack source, int count) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        if (!lobbyOrWaiting(runtime)) {
            source.sendSuccess(() -> Component.literal("Too late to change player max count ").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return 0;
        }
        try {
            runtime.config().set("max-players", count);
            gold(source, "MaxPlayers was set to " + count);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Unable to save max-players"));
            return 0;
        }
    }

    private int setBombBlock(CommandSourceStack source, String block) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        if (!lobbyOrWaiting(runtime)) {
            source.sendSuccess(() -> Component.literal("Too late to change player max count ").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return 0;
        }
        if (!isBlock(block)) {
            gold(source, "Material " + block + " can't be used to plant bombs");
            return 0;
        }
        try {
            runtime.config().set("bomb-block", block.toUpperCase(Locale.ROOT));
            gold(source, "Material was set to " + block);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Unable to save bomb-block"));
            return 0;
        }
    }

    private int stop(CommandSourceStack source) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        GameState state = runtime.match().state();
        if (state == GameState.LOBBY || state == GameState.WAITING || state == GameState.STARTING || state == GameState.SHOP) {
            source.sendSuccess(() -> Component.literal("Game hasn't start yet").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return 0;
        }
        runtime.match().forceFinish();
        return 1;
    }

    private int setRandMap(CommandSourceStack source) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        GameState state = runtime.match().state();
        if (state == GameState.LOBBY || state == GameState.WAITING || state == GameState.STARTING) {
            runtime.selectRandomMap();
            return 1;
        }
        source.sendSuccess(
                () -> Component.literal("Too late to change map " + state).withStyle(ChatFormatting.LIGHT_PURPLE),
                false
        );
        return 0;
    }

    private int maintenance(CommandSourceStack source) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        if (runtime.match().state() != GameState.LOBBY) {
            source.sendSuccess(() -> Component.literal("Must be stopped on Lobby game state").withStyle(ChatFormatting.LIGHT_PURPLE), false);
            return 0;
        }
        runtime.startMaintenance(player);
        source.sendSuccess(() -> Component.literal("You have now 200s to do your Map maintenance").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private int reloadConfig(CommandSourceStack source) {
        Cs4mServer runtime = requireRuntime(source);
        if (runtime == null) {
            return 0;
        }
        ServerPlayer player = playerOrMessage(source);
        if (player == null) {
            return 0;
        }
        if (runtime.match().state() != GameState.LOBBY) {
            source.sendSuccess(
                    () -> Component.literal("You can only reload configs on lobby state, now is " + runtime.match().state())
                            .withStyle(ChatFormatting.LIGHT_PURPLE),
                    false
            );
            return 0;
        }
        try {
            runtime.reloadConfig();
            source.sendSuccess(() -> Component.literal("CSMC Configs reloaded").withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Unable to reload config.yml"));
            return 0;
        }
    }

    private int usage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("/counterstrike setMap - ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Sets the current map Name been setup with next commands.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/counterstrike setlobby - ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Sets the spawn point for when the game is in lobby state.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/counterstrike setspawn <counterterrorist/terrorist> - ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Sets the spawn point for each team for when the game has started.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/counterstrike setbombsite <A/B> - ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Sets the bomb site for A or B (Required for AI).").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/counterstrike delMap - ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("To delete Map configuration from BD (You don't need this if you just want to fix a spawn).").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("/counterstrike maintenance - ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Set Maps on maintenance mode, so that you can change them.").withStyle(ChatFormatting.GRAY)), false);
        source.sendSuccess(() -> Component.literal("Other commands setMinPlayers, setMaxPlayers, setRandMap, stop, setBombBlock, reloadConfig.")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private Cs4mServer requireRuntime(CommandSourceStack source) {
        Cs4mServer current = runtime.get();
        if (current == null) {
            source.sendFailure(Component.literal("CS4M is not loaded"));
        }
        return current;
    }

    private ServerPlayer playerOrMessage(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("You need to be a player to execute this command."));
        }
        return player;
    }

    private String requireSetupMap(CommandSourceStack source, Cs4mServer runtime) {
        String map = runtime.setupMap();
        if (map == null) {
            gold(source, "Map not set, set it with setMap command");
        }
        return map;
    }

    private boolean lobbyOrWaiting(Cs4mServer runtime) {
        GameState state = runtime.match().state();
        return state == GameState.LOBBY || state == GameState.WAITING;
    }

    private SerializedLocation locationOf(ServerPlayer player) {
        Vec3 pos = player.position();
        return new SerializedLocation(WorldNames.storedName(player.level()), pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
    }

    private boolean isBlock(String name) {
        Identifier identifier = Identifier.tryParse(name.contains(":") ? name.toLowerCase(Locale.ROOT) : "minecraft:" + name.toLowerCase(Locale.ROOT));
        if (identifier == null) {
            return false;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        return block != null && BuiltInRegistries.BLOCK.getKey(block).equals(identifier);
    }

    private void gold(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GOLD), false);
    }

    private void green(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }
}
