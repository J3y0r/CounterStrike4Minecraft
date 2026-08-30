package me.jeyor.cs4m.shop;

import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.TeamColor;
import me.jeyor.cs4m.player.TeamEnum;
import me.jeyor.cs4m.weapon.WeaponCatalog;
import me.jeyor.cs4m.weapon.WeaponDefinition;
import me.jeyor.cs4m.weapon.WeaponItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class ShopCatalog {
    public static final String TERRORIST_TITLE = "Buy Menu - Terrorist";
    public static final String COUNTER_TITLE = "Buy Menu - Counter Terrorist";
    private static final int HELMET_PRICE = 500;
    private static final int CHEST_PRICE = 500;

    private ShopCatalog() {
    }

    public static void open(ServerPlayer player, CsPlayer csPlayer, WeaponCatalog weapons, boolean compact) {
        List<ItemStack> entries = displays(csPlayer, weapons);
        SimpleContainer container = new SimpleContainer(18);
        for (int index = 0; index < entries.size(); index++) {
            container.setItem(index, entries.get(index));
        }
        String title = csPlayer.team() == TeamEnum.COUNTER_TERRORISTS ? COUNTER_TITLE : TERRORIST_TITLE;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> new ShopMenu(id, inventory, container, csPlayer, weapons, compact),
                Component.literal(title)
        ));
    }

    public static boolean purchase(ServerPlayer player, CsPlayer csPlayer, ItemStack clicked, WeaponCatalog weapons, boolean compact) {
        if (clicked == null || clicked.isEmpty()) {
            return false;
        }
        if (clicked.is(Items.LEATHER_HELMET) || clicked.is(Items.LEATHER_CHESTPLATE)) {
            return buyArmor(player, csPlayer, clicked, compact);
        }
        return weapons.of(clicked).map(definition -> buyWeapon(player, csPlayer, definition, compact)).orElse(false);
    }

    private static boolean buyArmor(ServerPlayer player, CsPlayer csPlayer, ItemStack clicked, boolean compact) {
        int price = clicked.is(Items.LEATHER_HELMET) ? HELMET_PRICE : CHEST_PRICE;
        if (price > csPlayer.money()) {
            player.sendSystemMessage(Component.literal("Sorry, but you cannot afford this item.").withStyle(ChatFormatting.RED));
            return true;
        }
        ItemStack given = armor(clicked.getItem() == Items.LEATHER_HELMET, csPlayer.colour());
        if (clicked.is(Items.LEATHER_HELMET)) {
            if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                player.sendSystemMessage(Component.literal("Fixing current helmet.").withStyle(ChatFormatting.RED));
            }
            player.setItemSlot(EquipmentSlot.HEAD, given);
        } else {
            if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                player.sendSystemMessage(Component.literal("Fixing current armour.").withStyle(ChatFormatting.RED));
            }
            player.setItemSlot(EquipmentSlot.CHEST, given);
        }
        csPlayer.setMoney(csPlayer.money() - price, compact);
        player.sendSystemMessage(Component.literal("You have purchased " + given.getHoverName().getString()).withStyle(ChatFormatting.GREEN));
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static boolean buyWeapon(ServerPlayer player, CsPlayer csPlayer, WeaponDefinition definition, boolean compact) {
        if (definition.cost() > csPlayer.money()) {
            player.sendSystemMessage(Component.literal("Sorry, but you cannot afford this item.").withStyle(ChatFormatting.RED));
            return true;
        }
        ItemStack current = player.getInventory().getItem(definition.slot());
        if (!current.isEmpty()) {
            player.sendSystemMessage(Component.literal("Sorry, you cannot have two items of this type.").withStyle(ChatFormatting.RED));
            return true;
        }
        ItemStack given = WeaponItems.create(definition);
        player.getInventory().setItem(definition.slot(), given);
        csPlayer.setMoney(csPlayer.money() - definition.cost(), compact);
        player.sendSystemMessage(Component.literal("You have purchased " + given.getHoverName().getString()).withStyle(ChatFormatting.GREEN));
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static List<ItemStack> displays(CsPlayer csPlayer, WeaponCatalog weapons) {
        List<ItemStack> entries = new ArrayList<>();
        entries.add(pricedArmor(true, csPlayer.colour()));
        entries.add(pricedArmor(false, csPlayer.colour()));
        for (WeaponDefinition definition : weapons.shopEntries(csPlayer.team())) {
            ItemStack stack = WeaponItems.create(definition);
            List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
            lore.add(Component.literal("Price: " + definition.cost()).withStyle(ChatFormatting.GREEN));
            lore.add(Component.literal("Click to buy").withStyle(ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            entries.add(stack);
        }
        return entries;
    }

    private static ItemStack pricedArmor(boolean helmet, String colour) {
        ItemStack stack = armor(helmet, colour);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Price: " + (helmet ? HELMET_PRICE : CHEST_PRICE)).withStyle(ChatFormatting.GREEN),
                Component.literal("Click to buy").withStyle(ChatFormatting.GRAY)
        )));
        return stack;
    }

    private static ItemStack armor(boolean helmet, String colour) {
        ItemStack stack = new ItemStack(helmet ? Items.LEATHER_HELMET : Items.LEATHER_CHESTPLATE);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(TeamColor.leatherRgb(colour)));
        return stack;
    }
}
