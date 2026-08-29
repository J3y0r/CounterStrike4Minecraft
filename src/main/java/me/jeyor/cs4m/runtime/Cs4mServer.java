package me.jeyor.cs4m.runtime;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.database.SQLiteDatabase;
import me.jeyor.cs4m.game.GameState;
import me.jeyor.cs4m.game.MatchController;
import me.jeyor.cs4m.world.CsWorldRules;
import me.jeyor.cs4m.world.MapCatalog;
import me.jeyor.cs4m.world.MapVote;
import me.jeyor.cs4m.world.SelectedMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public final class Cs4mServer implements AutoCloseable {
    private static final int MAINTENANCE_TICKS = 200 * 20;

    private final MinecraftServer server;
    private final Cs4mConfig config;
    private final SQLiteDatabase database;
    private final MapCatalog maps;
    private final CsWorldRules worldRules;
    private final MatchController match;
    private final MapVote votes;
    private final Logger logger;
    private long ticks;
    private String setupMap;
    private int systemSet;
    private boolean terroristSpawnSet;
    private boolean counterSpawnSet;
    private boolean maintenance;
    private int maintenanceTicks;
    private UUID maintenancePlayer;
    private String defaultMotd;

    private Cs4mServer(
            MinecraftServer server,
            Cs4mConfig config,
            SQLiteDatabase database,
            MapCatalog maps,
            CsWorldRules worldRules,
            MatchController match,
            MapVote votes,
            Logger logger
    ) {
        this.server = server;
        this.config = config;
        this.database = database;
        this.maps = maps;
        this.worldRules = worldRules;
        this.match = match;
        this.votes = votes;
        this.logger = logger;
        this.defaultMotd = server.getMotd();
        this.systemSet = maps.maps().isEmpty() ? 0 : 8;
    }

    public static Cs4mServer start(MinecraftServer server, Logger logger) {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("cs4m");
        SQLiteDatabase database = null;
        try {
            Cs4mConfig config = Cs4mConfig.load(configDirectory, logger);
            database = SQLiteDatabase.open(configDirectory, logger);
            MapCatalog maps = new MapCatalog(database, config, logger);
            CsWorldRules worldRules = new CsWorldRules(database, config, logger);
            worldRules.seedMissingWorlds(server);
            Optional<SelectedMap> selected = maps.selectStartupMap();
            worldRules.apply(server, selected);
            MapVote votes = new MapVote();
            MatchController match = new MatchController(server, config, maps, worldRules, votes, logger);
            Cs4mServer runtime = new Cs4mServer(server, config, database, maps, worldRules, match, votes, logger);
            logger.info("CS4M server runtime started with {} player slots", config.maxPlayers());
            return runtime;
        } catch (IOException exception) {
            if (database != null) {
                database.close();
            }
            throw new IllegalStateException("Unable to load CS4M configuration", exception);
        } catch (RuntimeException exception) {
            if (database != null) {
                database.close();
            }
            throw exception;
        }
    }

    public MinecraftServer server() {
        return server;
    }

    public Cs4mConfig config() {
        return config;
    }

    public SQLiteDatabase database() {
        return database;
    }

    public MapCatalog maps() {
        return maps;
    }

    public CsWorldRules worldRules() {
        return worldRules;
    }

    public MatchController match() {
        return match;
    }

    public MapVote votes() {
        return votes;
    }

    public boolean maintenance() {
        return maintenance;
    }

    public String setupMap() {
        return setupMap;
    }

    public void setSetupMap(String name) {
        setupMap = name;
    }

    public void clearSetupMap() {
        setupMap = null;
    }

    public int systemSet() {
        return systemSet;
    }

    public void setSystemSet(int value) {
        systemSet = value;
    }

    public void advanceSystemSet() {
        systemSet++;
    }

    public void markTerroristSpawnSet() {
        terroristSpawnSet = true;
    }

    public void markCounterSpawnSet() {
        counterSpawnSet = true;
    }

    public boolean bothSpawnsSet() {
        return terroristSpawnSet && counterSpawnSet;
    }

    public void selectRandomMap() {
        Optional<SelectedMap> selected = maps.selectRandom();
        if (selected.isEmpty()) {
            broadcast(Component.literal("No maps loaded").withStyle(ChatFormatting.RED));
            return;
        }
        systemSet = 8;
        worldRules.apply(server, selected);
        broadcast(Component.literal("Map " + selected.get().name() + " was randomly chosen").withStyle(ChatFormatting.WHITE));
    }

    public void startMaintenance(ServerPlayer player) {
        maintenance = true;
        maintenanceTicks = MAINTENANCE_TICKS;
        maintenancePlayer = player.getUUID();
        worldRules.setMaintenance(true);
    }

    public void maybePromptSetup(ServerPlayer player) {
        if (systemSet != 0 || !player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_OWNER)) {
            return;
        }
        if (worldRules.isCsWorld(player.level())) {
            return;
        }
        player.sendSystemMessage(Component.literal("Game is still not set with maps, a dedicated World is required for this!"));
        player.sendSystemMessage(Component.literal("If you would like to start setting up, go to the world you have for it and type /csmc setMap  with the map name"));
        systemSet = 1;
    }

    public void reloadConfig() throws IOException {
        config.reload();
        worldRules.apply(server, maps.selected());
    }

    public void tick() {
        ticks++;
        match.tick();
        tickMaintenance();
        updateMotd();
        if (config.debug() && ticks % 20 == 0) {
            logger.debug("CS4M server tick {} state {}", ticks, match.state());
        }
    }

    @Override
    public void close() {
        match.stopWaiting();
        worldRules.setMaintenance(false);
        server.setMotd(defaultMotd);
        database.close();
        logger.info("CS4M server runtime stopped");
    }

    private void tickMaintenance() {
        if (!maintenance) {
            return;
        }
        maintenanceTicks--;
        if (maintenanceTicks > 0) {
            return;
        }
        maintenance = false;
        worldRules.setMaintenance(false);
        ServerPlayer player = maintenancePlayer == null ? null : server.getPlayerList().getPlayer(maintenancePlayer);
        if (player != null) {
            player.sendSystemMessage(Component.literal("Map maintenance is now OFF").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        maintenancePlayer = null;
    }

    private void updateMotd() {
        GameState state = match.state();
        if (state == GameState.LOBBY) {
            if (!votes.empty() && !server.getPlayerList().getPlayers().isEmpty()) {
                server.setMotd(ChatFormatting.AQUA + "CSMC Game has map been voted..");
            } else {
                server.setMotd(defaultMotd);
            }
        } else if (state == GameState.WAITING) {
            if (!votes.empty()) {
                server.setMotd(ChatFormatting.AQUA + "CSMC Game has map been voted...");
            } else {
                server.setMotd(ChatFormatting.AQUA + "CSMC Game is waiting for more players...");
            }
        } else if (state == GameState.STARTING) {
            server.setMotd(ChatFormatting.YELLOW + "CSMC Game is starting...");
        } else if (state == GameState.RUN || state == GameState.PLANTED) {
            server.setMotd(ChatFormatting.GREEN + "CSMC Game is running!");
        } else {
            server.setMotd(defaultMotd);
        }
    }

    private void broadcast(Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
        logger.info(message.getString());
    }
}
