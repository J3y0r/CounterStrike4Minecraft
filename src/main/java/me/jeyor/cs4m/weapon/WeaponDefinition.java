package me.jeyor.cs4m.weapon;

import me.jeyor.cs4m.player.TeamEnum;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

public record WeaponDefinition(
        String id,
        String name,
        Component displayName,
        Item item,
        float damage,
        int cost,
        int magazineCapacity,
        int magazines,
        float reloadSeconds,
        WeaponType type,
        TeamEnum team,
        boolean semiAutomatic,
        int fireIntervalTicks,
        double range,
        float baseSpread
) {
    public int slot() {
        return type.slot();
    }

    public int reserveCapacity() {
        return magazines * magazineCapacity;
    }

    public int durabilityMax() {
        return magazineCapacity + 1;
    }
}
