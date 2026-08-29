package me.jeyor.cs4m.world;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.database.SQLiteDatabase;
import me.jeyor.cs4m.database.WorldRecord;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class CsWorldRules {
    private final SQLiteDatabase database;
    private final Cs4mConfig config;
    private final Logger logger;
    private boolean maintenance;

    public CsWorldRules(SQLiteDatabase database, Cs4mConfig config, Logger logger) {
        this.database = database;
        this.config = config;
        this.logger = logger;
    }

    public void seedMissingWorlds(MinecraftServer server) {
        Set<String> existing = new HashSet<>();
        for (WorldRecord world : database.loadWorlds()) {
            existing.add(world.name());
        }
        for (ServerLevel level : server.getAllLevels()) {
            String storedName = WorldNames.storedName(level);
            if (existing.add(storedName)) {
                database.insertWorldIfAbsent(storedName);
                logger.info("Seeded mundo {}", storedName);
            }
        }
    }

    public void apply(MinecraftServer server, Optional<SelectedMap> selectedMap) {
        boolean anyCsWorld = false;
        boolean anyKnownWorld = false;
        for (ServerLevel level : server.getAllLevels()) {
            Optional<Boolean> csMode = csMode(level);
            if (csMode.isPresent()) {
                anyKnownWorld = true;
                anyCsWorld |= csMode.get();
            }
        }
        if (!anyKnownWorld || anyCsWorld) {
            applyCsRules(server, selectedMap);
        } else {
            applySurvivalRules(server);
        }
    }

    public void setMaintenance(boolean maintenance) {
        this.maintenance = maintenance;
    }

    public boolean maintenance() {
        return maintenance;
    }

    public boolean isCsWorld(ServerLevel level) {
        if (maintenance) {
            return false;
        }
        return csMode(level).orElse(true);
    }

    public boolean restrictionsEnabled(ServerLevel level) {
        return !maintenance && isCsWorld(level);
    }

    public boolean isKnownWorld(ServerLevel level) {
        return csMode(level).isPresent();
    }

    private Optional<Boolean> csMode(ServerLevel level) {
        for (String alias : WorldNames.aliases(level)) {
            Optional<WorldRecord> world = database.findWorld(alias);
            if (world.isPresent()) {
                return Optional.of(world.get().csMode());
            }
        }
        return Optional.empty();
    }

    private void applyCsRules(MinecraftServer server, Optional<SelectedMap> selectedMap) {
        GameRules rules = server.getGameRules();
        rules.set(GameRules.NATURAL_HEALTH_REGENERATION, false, server);
        rules.set(GameRules.SPAWN_MOBS, false, server);
        rules.set(GameRules.SPAWN_MONSTERS, false, server);
        rules.set(GameRules.KEEP_INVENTORY, false, server);
        boolean freezeTime = config.alwaysDay() && selectedMap.isPresent();
        rules.set(GameRules.ADVANCE_TIME, !freezeTime, server);
        if (freezeTime) {
            Holder<WorldClock> overworldClock = server.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
            server.clockManager().setTotalTicks(overworldClock, 0L);
        }
        logger.debug("Applied CS world rules");
    }

    private void applySurvivalRules(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        rules.set(GameRules.NATURAL_HEALTH_REGENERATION, true, server);
        rules.set(GameRules.SPAWN_MOBS, true, server);
        rules.set(GameRules.SPAWN_MONSTERS, true, server);
    }
}
