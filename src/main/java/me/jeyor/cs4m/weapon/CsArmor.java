package me.jeyor.cs4m.weapon;

import me.jeyor.cs4m.player.CsPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CsArmor {
    private static final float ARMOR_BONUS = 0.5F;

    private CsArmor() {
    }

    public static float apply(ServerPlayer victim, CsPlayer csPlayer, WeaponDefinition weapon, float damage, boolean headshot) {
        boolean helmet = victim.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET);
        boolean kevlar = victim.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE) && csPlayer.armor() > 0;
        if (headshot && !helmet) {
            return damage;
        }
        if (!kevlar) {
            return damage;
        }
        float pen = Math.max(0.0F, Math.min(1.0F, weapon.armorPenetration()));
        float health = damage * pen;
        float armorLost = (damage - health) * ARMOR_BONUS;
        int armor = csPlayer.armor();
        if (armorLost >= armor) {
            health = damage - armor / ARMOR_BONUS;
            csPlayer.setArmor(0);
            breakArmor(victim);
        } else {
            csPlayer.setArmor(Math.round(armor - armorLost));
        }
        return Math.max(0.0F, health);
    }

    public static void breakArmor(ServerPlayer victim) {
        victim.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        victim.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        victim.containerMenu.broadcastChanges();
    }
}
