package me.jeyor.cs4m.weapon;

import me.jeyor.cs4m.player.TeamEnum;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

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
        @Nullable TeamEnum team,
        boolean semiAutomatic,
        int fireIntervalTicks,
        double range,
        float baseSpread,
        float armorPenetration
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

    public boolean defaultPistol() {
        return "t-pistol-default".equals(id) || "ct-pistol-default".equals(id);
    }

    public boolean availableTo(TeamEnum team) {
        return this.team == null || this.team == team;
    }
}
