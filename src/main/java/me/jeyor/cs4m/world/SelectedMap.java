package me.jeyor.cs4m.world;

import me.jeyor.cs4m.database.MapRecord;

import java.util.Optional;

public final class SelectedMap {
    private final MapRecord record;
    private final Optional<SerializedLocation> lobby;
    private final Optional<SerializedLocation> terroristSpawn;
    private final Optional<SerializedLocation> counterSpawn;
    private final Optional<SerializedLocation> siteA;
    private final Optional<SerializedLocation> siteB;

    private SelectedMap(
            MapRecord record,
            Optional<SerializedLocation> lobby,
            Optional<SerializedLocation> terroristSpawn,
            Optional<SerializedLocation> counterSpawn,
            Optional<SerializedLocation> siteA,
            Optional<SerializedLocation> siteB
    ) {
        this.record = record;
        this.lobby = lobby;
        this.terroristSpawn = terroristSpawn;
        this.counterSpawn = counterSpawn;
        this.siteA = siteA;
        this.siteB = siteB;
    }

    public static SelectedMap from(MapRecord record) {
        return new SelectedMap(
                record,
                SerializedLocation.parse(record.lobby()),
                SerializedLocation.parse(record.terroristSpawn()),
                SerializedLocation.parse(record.counterSpawn()),
                SerializedLocation.parse(record.siteA()),
                SerializedLocation.parse(record.siteB())
        );
    }

    public MapRecord record() {
        return record;
    }

    public int id() {
        return record.id();
    }

    public String name() {
        return record.name();
    }

    public Optional<SerializedLocation> lobby() {
        return lobby;
    }

    public Optional<SerializedLocation> terroristSpawn() {
        return terroristSpawn;
    }

    public Optional<SerializedLocation> counterSpawn() {
        return counterSpawn;
    }

    public Optional<SerializedLocation> siteA() {
        return siteA;
    }

    public Optional<SerializedLocation> siteB() {
        return siteB;
    }

    public boolean hasTerroristSpawn() {
        return terroristSpawn.isPresent();
    }
}
