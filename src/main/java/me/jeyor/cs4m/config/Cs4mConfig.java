package me.jeyor.cs4m.config;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Cs4mConfig {
    private static final String RESOURCE_PATH = "/cs4m/config.yml";

    private final Path configFile;
    private Map<String, Object> values;

    private Cs4mConfig(Path configFile, Map<String, Object> values) {
        this.configFile = configFile;
        this.values = values;
    }

    public static Cs4mConfig load(Path configDirectory, Logger logger) throws IOException {
        Files.createDirectories(configDirectory);
        Path configFile = configDirectory.resolve("config.yml");
        if (Files.notExists(configFile)) {
            copyDefaults(configFile);
            logger.info("Created default configuration at {}", configFile);
        }
        return new Cs4mConfig(configFile, read(configFile));
    }

    public Path file() {
        return configFile;
    }

    public void reload() throws IOException {
        values = read(configFile);
    }

    public void set(String key, Object value) throws IOException {
        values.put(key, value);
        String content = Files.readString(configFile);
        String replacement = key + ": " + formatScalar(value);
        if (content.matches("(?s).*?^" + java.util.regex.Pattern.quote(key) + "\\s*:.*")) {
            content = content.replaceFirst("(?m)^" + java.util.regex.Pattern.quote(key) + "\\s*:.*$", replacement);
        } else {
            if (!content.endsWith("\n") && !content.isEmpty()) {
                content += "\n";
            }
            content += replacement + "\n";
        }
        Files.writeString(configFile, content);
    }

    private static String formatScalar(Object value) {
        if (value instanceof String string) {
            return string;
        }
        return String.valueOf(value);
    }

    public boolean enabled() {
        return booleanValue("enabled", true);
    }

    public boolean debug() {
        return booleanValue("debug", false);
    }

    public int minPlayers() {
        return intValue("min-players", 4);
    }

    public int maxPlayers() {
        return intValue("max-players", 12);
    }

    public int roundsToWin() {
        return intValue("rounds-to-win", 16);
    }

    public int maxRounds() {
        return intValue("max-rounds", 30);
    }

    public int startingMoney() {
        return intValue("starting-money", 800);
    }

    public int moneyOnVictory() {
        return intValue("money-on-win-reward", 3000);
    }

    public int moneyOnLoss() {
        return intValue("money-on-loss-reward", 2000);
    }

    public String bombBlock() {
        return stringValue("bomb-block", "OBSIDIAN");
    }

    public int bombTimer() {
        return intValue("bomb-timer", 45);
    }

    public double bombDefuseTime() {
        return doubleValue("bomb-defuse-time", 5.0);
    }

    public int startCounterDuration() {
        return intValue("start-counter-duration", 40);
    }

    public int shopPhaseDuration() {
        return intValue("shop-phase-duration", 15);
    }

    public int matchDuration() {
        return intValue("match-duration", 120);
    }

    public int knifeSpeed() {
        return intValue("knife-speed", 2);
    }

    public boolean friendlyFireEnabled() {
        return booleanValue("friendly-fire-enabled", false);
    }

    public boolean recoilAnimationEnabled() {
        return booleanValue("recoil-animation-enabled", true);
    }

    public boolean randomMaps() {
        return booleanValue("randomMaps", false);
    }

    public boolean alwaysDay() {
        return booleanValue("alwaysDay", true);
    }

    public boolean quitExitGame() {
        return booleanValue("quitExitGame", false);
    }

    public boolean showGameStatusTitle() {
        return booleanValue("showGameStatusTitle", true);
    }

    public boolean standardTeamColours() {
        return booleanValue("standardTeamColours", false);
    }

    public boolean modeValorant() {
        return booleanValue("modeValorant", false);
    }

    public boolean modeRealms() {
        return booleanValue("modeRealms", false);
    }

    public boolean allowJoinRunningGame() {
        return booleanValue("allowJoinRunningGame", false);
    }

    public Map<String, Object> weapons() {
        Object value = values.get("weapons");
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> weapons = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                weapons.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return weapons;
    }

    private boolean booleanValue(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : fallback;
    }

    private int intValue(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double doubleValue(String key, double fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private String stringValue(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String stringValue ? stringValue : fallback;
    }

    private static Map<String, Object> read(Path configFile) throws IOException {
        try (Reader reader = Files.newBufferedReader(configFile)) {
            Object loaded = new Yaml().load(reader);
            if (loaded == null) {
                return new LinkedHashMap<>();
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IOException("The root of config.yml must be a mapping");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    values.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return values;
        }
    }

    private static void copyDefaults(Path configFile) throws IOException {
        try (InputStream input = Cs4mConfig.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IOException("Missing default configuration resource " + RESOURCE_PATH);
            }
            Files.copy(input, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
