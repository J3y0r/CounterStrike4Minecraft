package me.jeyor.cs4m.database;

public enum MapLocationField {
    LOBBY("SpawnLobby"),
    TERRORISTS("SpawnTerrorists"),
    COUNTER("SpawnCounter"),
    A("A"),
    B("B");

    private final String column;

    MapLocationField(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }
}
