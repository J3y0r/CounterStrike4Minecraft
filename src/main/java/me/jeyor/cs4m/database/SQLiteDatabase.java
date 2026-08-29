package me.jeyor.cs4m.database;

import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SQLiteDatabase implements AutoCloseable {
    private static final String DATABASE_NAME = "CSMC.db";

    private final Path databaseFile;
    private final Logger logger;
    private Connection connection;

    private SQLiteDatabase(Path databaseFile, Logger logger) {
        this.databaseFile = databaseFile;
        this.logger = logger;
    }

    public static SQLiteDatabase open(Path configDirectory, Logger logger) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is missing", exception);
        }
        Path databaseFile = configDirectory.resolve(DATABASE_NAME);
        boolean created = Files.notExists(databaseFile);
        SQLiteDatabase database = new SQLiteDatabase(databaseFile, logger);
        database.connect();
        database.initializeSchema();
        if (created) {
            logger.info("Created CS4M database at {}", databaseFile);
        } else {
            logger.info("Opened CS4M database at {}", databaseFile);
        }
        return database;
    }

    public Path file() {
        return databaseFile;
    }

    public List<WorldRecord> loadWorlds() {
        List<WorldRecord> worlds = new ArrayList<>();
        String sql = "SELECT id, nome, IFNULL(modoCs, 'false') AS modoCs FROM mundos";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                worlds.add(new WorldRecord(
                        results.getInt("id"),
                        results.getString("nome"),
                        parseBoolean(results.getString("modoCs"))
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load mundos", exception);
        }
        return worlds;
    }

    public Optional<WorldRecord> findWorld(String name) {
        String sql = "SELECT id, nome, IFNULL(modoCs, 'false') AS modoCs FROM mundos WHERE nome = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new WorldRecord(
                        results.getInt("id"),
                        results.getString("nome"),
                        parseBoolean(results.getString("modoCs"))
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query mundos", exception);
        }
    }

    public void insertWorldIfAbsent(String name) {
        if (findWorld(name).isPresent()) {
            return;
        }
        String sql = "INSERT INTO mundos (nome, modoCs) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, "false");
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to insert mundo " + name, exception);
        }
    }

    public void setWorldCsMode(String name, boolean csMode) {
        String sql = "UPDATE mundos SET modoCs = ? WHERE nome = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Boolean.toString(csMode));
            statement.setString(2, name);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update mundo " + name, exception);
        }
    }

    public List<MapRecord> loadMaps() {
        List<MapRecord> maps = new ArrayList<>();
        String sql = "SELECT id, Descr, SpawnLobby, SpawnTerrorists, SpawnCounter, A, B FROM CSMaps ORDER BY id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                maps.add(readMap(results));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to load CSMaps", exception);
        }
        return maps;
    }

    public Optional<MapRecord> findMapById(int id) {
        String sql = "SELECT id, Descr, SpawnLobby, SpawnTerrorists, SpawnCounter, A, B FROM CSMaps WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(readMap(results));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query CSMaps by id", exception);
        }
    }

    public Optional<MapRecord> findMapByName(String name) {
        String sql = "SELECT id, Descr, SpawnLobby, SpawnTerrorists, SpawnCounter, A, B FROM CSMaps WHERE Descr = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(readMap(results));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query CSMaps by name", exception);
        }
    }

    public Optional<Integer> maxMapId() {
        String sql = "SELECT MAX(id) AS maxId FROM CSMaps";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                return Optional.empty();
            }
            int maxId = results.getInt("maxId");
            if (results.wasNull() || maxId <= 0) {
                return Optional.empty();
            }
            return Optional.of(maxId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to query CSMaps max id", exception);
        }
    }

    public MapRecord createMap(String name) {
        String sql = "INSERT INTO CSMaps (Descr) VALUES (?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return findMapById(keys.getInt(1)).orElseThrow();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to insert CSMaps row", exception);
        }
        return findMapByName(name).orElseThrow(() -> new IllegalStateException("Inserted map was not found: " + name));
    }

    public void updateMapLocation(int id, MapLocationField field, String location) {
        if (!isSafeIdentifier(field.column())) {
            throw new IllegalArgumentException("Invalid CSMaps column " + field.column());
        }
        String sql = "UPDATE CSMaps SET " + field.column() + " = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to update CSMaps." + field.column(), exception);
        }
    }

    public int deleteMap(String name) {
        String sql = "DELETE FROM CSMaps WHERE Descr = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to delete CSMaps row", exception);
        }
    }

    private MapRecord readMap(ResultSet results) throws SQLException {
        return new MapRecord(
                results.getInt("id"),
                results.getString("Descr"),
                nullable(results.getString("SpawnLobby")),
                nullable(results.getString("SpawnTerrorists")),
                nullable(results.getString("SpawnCounter")),
                nullable(results.getString("A")),
                nullable(results.getString("B"))
        );
    }

    private void connect() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to open " + databaseFile, exception);
        }
    }

    private void initializeSchema() {
        execute("""
                CREATE TABLE IF NOT EXISTS mundos (
                    id integer PRIMARY KEY,
                    nome VARCHAR(255) NOT NULL,
                    modoCs TINYINT
                )
                """);
        execute("""
                CREATE TABLE IF NOT EXISTS CSMaps (
                    id INTEGER NOT NULL,
                    Descr varchar(50) NOT NULL,
                    SpawnLobby TEXT,
                    SpawnTerrorists TEXT,
                    SpawnCounter TEXT,
                    PRIMARY KEY(id AUTOINCREMENT)
                )
                """);
        addColumnIfMissing("CSMaps", "A", "ALTER TABLE CSMaps ADD A TEXT");
        addColumnIfMissing("CSMaps", "B", "ALTER TABLE CSMaps ADD B TEXT");
        if (!tableExists("Skins")) {
            execute("""
                    CREATE TABLE Skins (
                        id INTEGER NOT NULL,
                        Descr varchar(100) NOT NULL,
                        signature TEXT,
                        texture TEXT,
                        PRIMARY KEY(id AUTOINCREMENT)
                    )
                    """);
        }
    }

    private void addColumnIfMissing(String table, String column, String alterSql) {
        if (!columnExists(table, column)) {
            execute(alterSql);
        }
    }

    private boolean tableExists(String table) {
        String sql = "SELECT COUNT(*) AS cnt FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() && results.getInt("cnt") > 0;
            }
        } catch (SQLException exception) {
            logger.warn("Unable to inspect table {}", table, exception);
            return false;
        }
    }

    private boolean columnExists(String table, String column) {
        if (!isSafeIdentifier(table) || !isSafeIdentifier(column)) {
            return false;
        }
        String sql = "SELECT COUNT(*) AS cnt FROM pragma_table_info('" + table + "') WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, column);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() && results.getInt("cnt") > 0;
            }
        } catch (SQLException exception) {
            logger.warn("Unable to inspect column {}.{}", table, column, exception);
            return false;
        }
    }

    private static boolean isSafeIdentifier(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private void execute(String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to execute schema SQL", exception);
        }
    }

    private static boolean parseBoolean(String value) {
        return Boolean.parseBoolean(value);
    }

    private static String nullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                logger.warn("Unable to close CS4M database", exception);
            }
            connection = null;
        }
    }
}
