package me.jeyor.cs4m.weapon;

import me.jeyor.cs4m.player.TeamEnum;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WeaponCatalog {
    private Map<String, WeaponDefinition> byId;
    private final Logger logger;

    public WeaponCatalog(Map<String, Object> weapons, Logger logger) {
        this.logger = logger;
        this.byId = parse(weapons);
    }

    public void reload(Map<String, Object> weapons) {
        byId = parse(weapons);
    }

    public Optional<WeaponDefinition> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<WeaponDefinition> of(ItemStack stack) {
        return WeaponItems.id(stack).flatMap(this::byId);
    }

    public List<WeaponDefinition> shopEntries(TeamEnum team) {
        List<WeaponDefinition> entries = new ArrayList<>();
        for (WeaponDefinition definition : byId.values()) {
            if (definition.type() == WeaponType.GRENADE || definition.defaultPistol() || !definition.availableTo(team)) {
                continue;
            }
            entries.add(definition);
        }
        return entries;
    }

    public Optional<WeaponDefinition> defaultPistol(TeamEnum team) {
        String id = team == TeamEnum.TERRORISTS ? "t-pistol-default" : "ct-pistol-default";
        Optional<WeaponDefinition> named = byId(id);
        if (named.isPresent()) {
            return named;
        }
        for (WeaponDefinition definition : byId.values()) {
            if (definition.type() == WeaponType.PISTOL && definition.availableTo(team)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    private Map<String, WeaponDefinition> parse(Map<String, Object> weapons) {
        Map<String, WeaponDefinition> parsed = new LinkedHashMap<>();
        if (weapons == null) {
            return parsed;
        }
        for (Map.Entry<String, Object> entry : weapons.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> nested : raw.entrySet()) {
                if (nested.getKey() != null) {
                    values.put(String.valueOf(nested.getKey()), nested.getValue());
                }
            }
            WeaponDefinition definition = definition(entry.getKey(), values);
            if (definition != null) {
                parsed.put(definition.id(), definition);
            }
        }
        logger.info("Loaded {} CS4M weapons", parsed.size());
        return parsed;
    }

    private WeaponDefinition definition(String id, Map<String, Object> values) {
        WeaponType type = WeaponType.parse(string(values, "weapon-type", "RIFLE"));
        if (type == WeaponType.GRENADE) {
            return null;
        }
        TeamEnum team = team(string(values, "team", "TERRORISTS"));
        Item item = item(id, type, string(values, "material", ""));
        if (item == Items.AIR) {
            logger.warn("Skipping weapon {} with unknown material", id);
            return null;
        }
        String display = string(values, "display-name", id);
        String key = id.toLowerCase(Locale.ROOT);
        boolean sniper = key.contains("awp");
        boolean pistol = type == WeaponType.PISTOL;
        boolean smg = key.contains("mp5");
        float armorPenetration = (float) number(values, "armor-penetration", sniper ? 0.97 : smg ? 0.65 : pistol ? 0.50 : 0.75);
        return new WeaponDefinition(
                id,
                string(values, "name", id),
                WeaponItems.fromLegacy(display),
                item,
                (float) number(values, "damage", pistol ? 6.0 : 7.0),
                (int) number(values, "cost", 0),
                Math.max(1, (int) number(values, "magazine-capacity", pistol ? 12 : 30)),
                Math.max(0, (int) number(values, "magazines", 3)),
                (float) number(values, "reload-speed", 2.2),
                type,
                team,
                pistol || sniper,
                sniper ? 30 : pistol ? 6 : 2,
                sniper ? 128.0 : pistol ? 64.0 : 96.0,
                sniper ? 0.15F : pistol ? 1.2F : 0.8F,
                Math.max(0.0F, Math.min(1.0F, armorPenetration))
        );
    }

    private Item item(String id, WeaponType type, String material) {
        String normalized = WeaponItems.normalizeMaterial(material);
        if (!normalized.isEmpty()) {
            try {
                Identifier identifier = Identifier.parse(normalized);
                if (BuiltInRegistries.ITEM.containsKey(identifier)) {
                    return BuiltInRegistries.ITEM.getValue(identifier);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return fallbackItem(id, type);
    }

    private static Item fallbackItem(String id, WeaponType type) {
        String key = id.toLowerCase(Locale.ROOT);
        if (type == WeaponType.PISTOL) {
            if (key.contains("deagle")) {
                return Items.GOLDEN_PICKAXE;
            }
            if (key.contains("tec")) {
                return Items.STONE_PICKAXE;
            }
            if (key.contains("p30") || key.contains("five")) {
                return Items.COPPER_PICKAXE;
            }
            if (key.contains("ct") || key.contains("usp")) {
                return Items.IRON_PICKAXE;
            }
            return Items.WOODEN_PICKAXE;
        }
        if (key.contains("awp")) {
            return Items.NETHERITE_PICKAXE;
        }
        if (key.contains("ak")) {
            return Items.IRON_HOE;
        }
        if (key.contains("galil") || key.contains("vz58")) {
            return Items.COPPER_HOE;
        }
        if (key.contains("m4")) {
            return Items.DIAMOND_HOE;
        }
        if (key.contains("famas")) {
            return Items.GOLDEN_HOE;
        }
        if (key.contains("sg")) {
            return Items.NETHERITE_HOE;
        }
        if (key.contains("mp5")) {
            return Items.STONE_HOE;
        }
        return type == WeaponType.PISTOL ? Items.WOODEN_PICKAXE : Items.IRON_HOE;
    }

    private static @Nullable TeamEnum team(String raw) {
        if (raw == null) {
            return TeamEnum.TERRORISTS;
        }
        String value = raw.toUpperCase(Locale.ROOT);
        if (value.equals("ALL") || value.equals("BOTH") || value.equals("ANY")) {
            return null;
        }
        if (value.contains("COUNTER")) {
            return TeamEnum.COUNTER_TERRORISTS;
        }
        return TeamEnum.TERRORISTS;
    }

    private static String string(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String string ? string : fallback;
    }

    private static double number(Map<String, Object> values, String key, double fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
