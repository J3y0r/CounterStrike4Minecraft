package me.jeyor.cs4m.weapon;

import me.jeyor.cs4m.item.InventorySlots;

import java.util.Locale;

public enum WeaponType {
    RIFLE,
    PISTOL,
    GRENADE;

    public int slot() {
        return this == PISTOL ? InventorySlots.PISTOL : InventorySlots.RIFLE;
    }

    public static WeaponType parse(String raw) {
        if (raw == null) {
            return RIFLE;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.contains("PISTOL")) {
            return PISTOL;
        }
        if (value.contains("GRENADE")) {
            return GRENADE;
        }
        return RIFLE;
    }
}
