package me.jeyor.cs4m.shop;

import me.jeyor.cs4m.item.InventorySlots;
import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.player.TeamColor;
import me.jeyor.cs4m.player.TeamEnum;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class ShopCatalog {
    public static final String TERRORIST_TITLE = "Buy Menu - Terrorist";
    public static final String COUNTER_TITLE = "Buy Menu - Counter Terrorist";

    private ShopCatalog() {
    }

    public static void open(ServerPlayer player, CsPlayer csPlayer, boolean compact) {
        SimpleContainer container = new SimpleContainer(9);
        for (int index = 0; index < entries().length; index++) {
            container.setItem(index, display(entries()[index], csPlayer.colour()));
        }
        String title = csPlayer.team() == TeamEnum.COUNTER_TERRORISTS ? COUNTER_TITLE : TERRORIST_TITLE;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> new ShopMenu(id, inventory, container, csPlayer, compact),
                Component.literal(title)
        ));
    }

    public static boolean purchase(ServerPlayer player, CsPlayer csPlayer, ItemStack clicked, boolean compact) {
        ShopEntry entry = of(clicked);
        if (entry == null) {
            return false;
        }
        if (entry.price() > csPlayer.money()) {
            player.sendSystemMessage(Component.literal("Sorry, but you cannot afford this item.").withStyle(ChatFormatting.RED));
            return true;
        }
        ItemStack given = display(entry, csPlayer.colour());
        if (entry.item() == Items.LEATHER_HELMET) {
            if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                player.sendSystemMessage(Component.literal("Fixing current helmet.").withStyle(ChatFormatting.RED));
            }
            player.setItemSlot(EquipmentSlot.HEAD, given);
        } else if (entry.item() == Items.LEATHER_CHESTPLATE) {
            if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                player.sendSystemMessage(Component.literal("Fixing current armour.").withStyle(ChatFormatting.RED));
            }
            player.setItemSlot(EquipmentSlot.CHEST, given);
        } else {
            if (entry.slot() >= 0 && !player.getInventory().getItem(entry.slot()).isEmpty()) {
                player.sendSystemMessage(Component.literal("Sorry, you cannot have two items of this type.").withStyle(ChatFormatting.RED));
                return true;
            }
            player.getInventory().setItem(entry.slot(), given);
        }
        csPlayer.setMoney(csPlayer.money() - entry.price(), compact);
        player.sendSystemMessage(Component.literal("You have purchased " + given.getHoverName().getString()).withStyle(ChatFormatting.GREEN));
        player.containerMenu.broadcastChanges();
        return true;
    }

    private static ItemStack display(ShopEntry entry, String colour) {
        ItemStack stack = new ItemStack(entry.item());
        if (entry.item() == Items.LEATHER_HELMET || entry.item() == Items.LEATHER_CHESTPLATE) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(TeamColor.leatherRgb(colour)));
        }
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Price: " + entry.price()).withStyle(ChatFormatting.GREEN),
                Component.literal("Click to buy").withStyle(ChatFormatting.GRAY)
        )));
        return stack;
    }

    private static ShopEntry of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (ShopEntry entry : entries()) {
            if (stack.is(entry.item())) {
                return entry;
            }
        }
        return null;
    }

    private static ShopEntry[] entries() {
        return new ShopEntry[]{
                new ShopEntry(Items.LEATHER_HELMET, 500, -1),
                new ShopEntry(Items.LEATHER_CHESTPLATE, 500, -1),
                new ShopEntry(Items.CROSSBOW, 700, InventorySlots.UTILITY),
                new ShopEntry(Items.BOW, 600, InventorySlots.UTILITY),
                new ShopEntry(Items.MACE, 1000, InventorySlots.RIFLE),
                new ShopEntry(Items.TRIDENT, 1500, InventorySlots.RIFLE),
                new ShopEntry(Items.IRON_SWORD, 1100, InventorySlots.PISTOL),
                new ShopEntry(Items.DIAMOND_SWORD, 1800, InventorySlots.PISTOL),
                new ShopEntry(Items.NETHERITE_SWORD, 2500, InventorySlots.PISTOL)
        };
    }

    private record ShopEntry(Item item, int price, int slot) {
    }
}
