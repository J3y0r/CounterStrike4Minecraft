package me.jeyor.cs4m.weapon;

import me.jeyor.cs4m.item.MatchItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class WeaponItems {
    public static final String WEAPON = "weapon";
    private static final String ID_KEY = "cs4mId";
    private static final String RESERVE_KEY = "cs4mReserve";

    private WeaponItems() {
    }

    public static ItemStack create(WeaponDefinition definition) {
        ItemStack stack = new ItemStack(definition.item());
        stack.set(DataComponents.CUSTOM_NAME, definition.displayName());
        stack.set(DataComponents.MAX_DAMAGE, definition.durabilityMax());
        stack.set(DataComponents.DAMAGE, 0);
        stack.set(DataComponents.BLOCKS_ATTACKS, holdToUse());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("cs4m", WEAPON);
            tag.putString(ID_KEY, definition.id());
            tag.putInt(RESERVE_KEY, definition.reserveCapacity());
        });
        refreshLore(stack, definition);
        return stack;
    }

    public static boolean isWeapon(ItemStack stack) {
        return MatchItems.is(stack, WEAPON);
    }

    public static Optional<String> id(ItemStack stack) {
        if (!isWeapon(stack)) {
            return Optional.empty();
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }
        String value = data.copyTag().getStringOr(ID_KEY, "");
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    public static int ammo(ItemStack stack, WeaponDefinition definition) {
        return Mth.clamp(definition.magazineCapacity() - stack.getDamageValue(), 0, definition.magazineCapacity());
    }

    public static void setAmmo(ItemStack stack, WeaponDefinition definition, int ammo) {
        int clamped = Mth.clamp(ammo, 0, definition.magazineCapacity());
        stack.setDamageValue(definition.magazineCapacity() - clamped);
        refreshLore(stack, definition);
    }

    public static int reserve(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 0;
        }
        return Math.max(0, data.copyTag().getIntOr(RESERVE_KEY, 0));
    }

    public static void setReserve(ItemStack stack, WeaponDefinition definition, int reserve) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(RESERVE_KEY, Math.max(0, reserve)));
        refreshLore(stack, definition);
    }

    public static void refreshLore(ItemStack stack, WeaponDefinition definition) {
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Ammo: " + ammo(stack, definition) + "/" + reserve(stack)).withStyle(ChatFormatting.YELLOW),
                Component.literal("Damage: " + trim(definition.damage())).withStyle(ChatFormatting.GRAY)
        )));
    }

    public static Component fromLegacy(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if ((character == '&' || character == '§') && index + 1 < text.length()) {
                if (!current.isEmpty()) {
                    root.append(Component.literal(current.toString()).withStyle(style));
                    current.setLength(0);
                }
                ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(++index)));
                if (formatting == ChatFormatting.RESET) {
                    style = Style.EMPTY;
                } else if (formatting != null) {
                    style = style.applyFormat(formatting);
                }
                continue;
            }
            current.append(character);
        }
        if (!current.isEmpty()) {
            root.append(Component.literal(current.toString()).withStyle(style));
        }
        return root;
    }

    private static BlocksAttacks holdToUse() {
        return new BlocksAttacks(
                0.0F,
                0.0F,
                List.of(new BlocksAttacks.DamageReduction(0.0F, Optional.empty(), 0.0F, 0.0F)),
                new BlocksAttacks.ItemDamageFunction(Float.MAX_VALUE, 0.0F, 0.0F),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    static String normalizeMaterial(String material) {
        if (material == null || material.isBlank()) {
            return "";
        }
        String value = material.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (value.startsWith("minecraft:")) {
            return value;
        }
        return "minecraft:" + value;
    }

    private static String trim(float value) {
        if (value == (int) value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
