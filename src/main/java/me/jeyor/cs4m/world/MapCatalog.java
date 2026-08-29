package me.jeyor.cs4m.world;

import me.jeyor.cs4m.config.Cs4mConfig;
import me.jeyor.cs4m.database.MapLocationField;
import me.jeyor.cs4m.database.MapRecord;
import me.jeyor.cs4m.database.SQLiteDatabase;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class MapCatalog {
    private final SQLiteDatabase database;
    private final Cs4mConfig config;
    private final Logger logger;
    private SelectedMap selected;

    public MapCatalog(SQLiteDatabase database, Cs4mConfig config, Logger logger) {
        this.database = database;
        this.config = config;
        this.logger = logger;
    }

    public Optional<SelectedMap> selected() {
        return Optional.ofNullable(selected);
    }

    public List<MapRecord> maps() {
        return database.loadMaps();
    }

    public Optional<SelectedMap> selectById(int mapId) {
        Optional<MapRecord> record = database.findMapById(mapId);
        if (record.isEmpty()) {
            logger.warn("CS4M map id {} was not found", mapId);
            selected = null;
            return Optional.empty();
        }
        return select(record.get());
    }

    public Optional<SelectedMap> selectByName(String name) {
        Optional<MapRecord> record = database.findMapByName(name);
        if (record.isEmpty()) {
            logger.warn("CS4M map {} was not found", name);
            selected = null;
            return Optional.empty();
        }
        return select(record.get());
    }

    public Optional<SelectedMap> selectStartupMap() {
        if (config.randomMaps()) {
            return selectRandom();
        }
        List<MapRecord> maps = database.loadMaps();
        if (maps.isEmpty()) {
            logger.warn("No CS4M maps loaded");
            selected = null;
            return Optional.empty();
        }
        return select(maps.getFirst());
    }

    public Optional<SelectedMap> selectRandom() {
        Optional<Integer> maxId = database.maxMapId();
        if (maxId.isEmpty()) {
            logger.warn("No CS4M maps loaded");
            selected = null;
            return Optional.empty();
        }
        for (int attempt = 0; attempt < 64; attempt++) {
            int candidate = ThreadLocalRandom.current().nextInt(1, maxId.get() + 1);
            Optional<MapRecord> record = database.findMapById(candidate);
            if (record.isPresent()) {
                return select(record.get());
            }
        }
        List<MapRecord> maps = database.loadMaps();
        if (maps.isEmpty()) {
            selected = null;
            return Optional.empty();
        }
        return select(maps.get(ThreadLocalRandom.current().nextInt(maps.size())));
    }

    public MapRecord createOrLoad(String name) {
        return database.findMapByName(name).orElseGet(() -> database.createMap(name));
    }

    public void saveLocation(String mapName, MapLocationField field, SerializedLocation location) {
        MapRecord record = createOrLoad(mapName);
        database.setWorldCsMode(location.worldName(), true);
        database.updateMapLocation(record.id(), field, location.serialize());
        if (selected != null && selected.id() == record.id()) {
            selectById(record.id());
        }
    }

    public int delete(String mapName) {
        int deleted = database.deleteMap(mapName);
        if (selected != null && selected.name().equals(mapName)) {
            selected = null;
        }
        return deleted;
    }

    public boolean hasPlayableSpawn(MinecraftServer server) {
        if (selected == null || selected.terroristSpawn().isEmpty()) {
            return false;
        }
        return selected.terroristSpawn().get().resolveLevel(server).isPresent();
    }

    private Optional<SelectedMap> select(MapRecord record) {
        SelectedMap map = SelectedMap.from(record);
        if (record.lobby() != null && map.lobby().isEmpty()) {
            logger.warn("Map {} has an invalid lobby location: {}", record.name(), record.lobby());
        }
        if (record.terroristSpawn() != null && map.terroristSpawn().isEmpty()) {
            logger.warn("Map {} has an invalid terrorist spawn: {}", record.name(), record.terroristSpawn());
        }
        if (record.counterSpawn() != null && map.counterSpawn().isEmpty()) {
            logger.warn("Map {} has an invalid CT spawn: {}", record.name(), record.counterSpawn());
        }
        if (record.siteA() != null && map.siteA().isEmpty()) {
            logger.warn("Map {} has an invalid A site: {}", record.name(), record.siteA());
        }
        if (record.siteB() != null && map.siteB().isEmpty()) {
            logger.warn("Map {} has an invalid B site: {}", record.name(), record.siteB());
        }
        selected = map;
        logger.info("Selected CS4M map {} ({})", map.name(), map.id());
        return Optional.of(map);
    }
}
