package me.jeyor.cs4m.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class WorldNames {
    private WorldNames() {
    }

    public static String storedName(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        if (dimension == Level.OVERWORLD) {
            return "world";
        }
        if (dimension == Level.NETHER) {
            return "world_nether";
        }
        if (dimension == Level.END) {
            return "world_the_end";
        }
        return dimension.identifier().toString();
    }

    public static List<String> aliases(ServerLevel level) {
        List<String> names = new ArrayList<>();
        add(names, storedName(level));
        add(names, level.dimension().identifier().toString());
        add(names, level.dimension().identifier().getPath());
        if (level.getLevelData() instanceof ServerLevelData serverLevelData) {
            add(names, serverLevelData.getLevelName());
        }
        ResourceKey<Level> dimension = level.dimension();
        if (dimension == Level.OVERWORLD) {
            add(names, "overworld");
            add(names, "minecraft:overworld");
        } else if (dimension == Level.NETHER) {
            add(names, "world_nether");
            add(names, "DIM-1");
            add(names, "the_nether");
            add(names, "minecraft:the_nether");
        } else if (dimension == Level.END) {
            add(names, "world_the_end");
            add(names, "DIM1");
            add(names, "the_end");
            add(names, "minecraft:the_end");
        }
        return names;
    }

    public static Optional<ServerLevel> resolve(MinecraftServer server, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String requested = name.trim();
        for (ServerLevel level : server.getAllLevels()) {
            for (String alias : aliases(level)) {
                if (alias.equalsIgnoreCase(requested)) {
                    return Optional.of(level);
                }
            }
        }
        Identifier identifier = Identifier.tryParse(requested.contains(":") ? requested : "minecraft:" + requested.toLowerCase(Locale.ROOT));
        if (identifier != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, identifier));
            if (level != null) {
                return Optional.of(level);
            }
        }
        return Optional.empty();
    }

    private static void add(List<String> names, String value) {
        if (value != null && !value.isBlank() && names.stream().noneMatch(existing -> existing.equalsIgnoreCase(value))) {
            names.add(value);
        }
    }
}
