package me.jeyor.cs4m.shop;

import me.jeyor.cs4m.player.CsPlayer;
import me.jeyor.cs4m.weapon.WeaponCatalog;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

public final class ShopMenu extends ChestMenu {
    private final CsPlayer buyer;
    private final WeaponCatalog weapons;
    private final boolean compact;

    public ShopMenu(int containerId, Inventory inventory, Container container, CsPlayer buyer, WeaponCatalog weapons, boolean compact) {
        super(MenuType.GENERIC_9x2, containerId, inventory, container, 2);
        this.buyer = buyer;
        this.weapons = weapons;
        this.compact = compact;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack clicked = slotId >= 0 && slotId < getContainer().getContainerSize()
                ? getContainer().getItem(slotId)
                : ItemStack.EMPTY;
        ShopCatalog.purchase(serverPlayer, buyer, clicked, weapons, compact);
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }
}
