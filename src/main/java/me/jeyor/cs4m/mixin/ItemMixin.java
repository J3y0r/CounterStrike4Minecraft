package me.jeyor.cs4m.mixin;

import me.jeyor.cs4m.runtime.Cs4mAccess;
import me.jeyor.cs4m.runtime.Cs4mServer;
import me.jeyor.cs4m.weapon.WeaponItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class ItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void cs4mUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer serverPlayer) || hand != InteractionHand.MAIN_HAND) {
            return;
        }
        Cs4mServer runtime = Cs4mAccess.runtime();
        if (runtime != null && runtime.match().onUseItem(serverPlayer, hand) && WeaponItems.isWeapon(serverPlayer.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void cs4mUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || context.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!WeaponItems.isWeapon(context.getItemInHand())) {
            return;
        }
        Cs4mServer runtime = Cs4mAccess.runtime();
        if (runtime != null && runtime.match().onUseItem(player, context.getHand())) {
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}
