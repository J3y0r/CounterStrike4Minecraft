package me.jeyor.cs4m.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public final class MatchItems {
    public static final String KNIFE = "knife";
    public static final String BOMB = "bomb";
    public static final String SHOP = "shop";

    private MatchItems() {
    }

    public static ItemStack knife() {
        ItemStack stack = new ItemStack(Items.IRON_AXE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Standard Knife").withStyle(ChatFormatting.GRAY));
        mark(stack, KNIFE);
        return stack;
    }

    public static ItemStack bomb() {
        ItemStack stack = new ItemStack(Items.TNT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("C4-Explosive Bomb").withStyle(ChatFormatting.RED));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Plant the bomb on a bomb site.").withStyle(ChatFormatting.YELLOW),
                Component.literal("After planting, the bomb will").withStyle(ChatFormatting.YELLOW),
                Component.literal("start ticking for about a minute").withStyle(ChatFormatting.YELLOW),
                Component.literal("before it explodes for a victory.").withStyle(ChatFormatting.YELLOW)
        )));
        mark(stack, BOMB);
        return stack;
    }

    public static ItemStack shop() {
        ItemStack stack = new ItemStack(Items.CHEST);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("(Right click to open shop)").withStyle(ChatFormatting.YELLOW));
        mark(stack, SHOP);
        return stack;
    }

    public static boolean is(ItemStack stack, String identity) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        return identity.equals(data.copyTag().getString("cs4m").orElse(""));
    }

    public static boolean bomb(ItemStack stack) {
        return is(stack, BOMB) || (!stack.isEmpty() && stack.is(Items.TNT));
    }

    private static void mark(ItemStack stack, String identity) {
        CompoundTag tag = new CompoundTag();
        tag.putString("cs4m", identity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
